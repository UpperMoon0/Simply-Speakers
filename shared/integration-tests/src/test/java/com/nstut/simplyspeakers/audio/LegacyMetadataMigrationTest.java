package com.nstut.simplyspeakers.audio;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class LegacyMetadataMigrationTest {
    @TempDir Path world;

    @Test void nameLimitsProtectNewAndPreviouslyPersistedMetadataOnTheWire() {
        var original = new AudioFileMetadata("clip", "clip.wav", "owner", 1);
        assertEquals(256, original.withDisplayName("a".repeat(256)).getDisplayName().length());
        assertThrows(IllegalArgumentException.class, () -> original.withDisplayName("a".repeat(257)));
        assertThrows(IllegalArgumentException.class, () -> original.withDisplayName("界".repeat(257)));
        var legacy = new Gson().fromJson("{\"uuid\":\"clip\",\"originalFilename\":\"clip.wav\",\"durationSeconds\":1,\"library\":{\"displayName\":\"" +
                "a".repeat(255) + "😀" + "\",\"category\":\"" + "c".repeat(100) +
                "\",\"uploaderName\":\"" + "u".repeat(100) + "\"}}", AudioFileMetadata.class);
        var buffer = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try {
            legacy.encode(buffer);
            var decoded = AudioFileMetadata.decode(buffer);
            assertEquals("a".repeat(255), decoded.getDisplayName());
            assertEquals(64, decoded.getCategory().length()); assertEquals(64, decoded.getUploaderName().length());
            assertEquals(0, buffer.readableBytes());
        } finally { buffer.release(); }
    }

    @Test void durationReplacementHandlesMissingAndNullLibraryAndPreservesOrganization() {
        for (String extra : new String[]{"", ",\"library\":null"}) {
            var legacy=new Gson().fromJson("{\"uuid\":\"old\",\"originalFilename\":\"old.wav\",\"ownerUUID\":\"owner\""+extra+"}",AudioFileMetadata.class);
            var updated=legacy.withDuration(2.5f);
            assertEquals("old",updated.getUuid());assertEquals("owner",updated.getOwnerUUID());
            assertEquals("old.wav",updated.effectiveDisplayName());assertEquals(2.5f,updated.getDurationSeconds());
            assertTrue(updated.getTags().isEmpty());
        }
        var original=new AudioFileMetadata("new","new.wav","owner",1).withDisplayName("Voice").withCategory("Clips").withTags(java.util.List.of("speech"));
        var updated=original.withDuration(3);
        assertEquals("Voice",updated.getDisplayName());assertEquals("Clips",updated.getCategory());assertEquals(original.getTags(),updated.getTags());
        assertEquals(1,original.getDurationSeconds());
    }

    @Test void actualLegacyManifestMigrationKeepsAllEntriesAndSurvivesReload() throws Exception {
        var dir=Files.createDirectories(world.resolve("simply_speakers_audios"));
        int samples=8000,data=samples*2;
        var wav=ByteBuffer.allocate(44+data).order(ByteOrder.LITTLE_ENDIAN);
        wav.put("RIFF".getBytes(java.nio.charset.StandardCharsets.US_ASCII)).putInt(36+data);
        wav.put("WAVEfmt ".getBytes(java.nio.charset.StandardCharsets.US_ASCII)).putInt(16).putShort((short)1).putShort((short)1);
        wav.putInt(8000).putInt(16000).putShort((short)2).putShort((short)16);
        wav.put("data".getBytes(java.nio.charset.StandardCharsets.US_ASCII)).putInt(data);
        Files.write(dir.resolve("first.wav"),wav.array());Files.write(dir.resolve("second.wav"),wav.array());
        Files.writeString(dir.resolve("audio_manifest.json"),"{\"first\":{\"uuid\":\"first\",\"originalFilename\":\"first.wav\",\"ownerUUID\":\"owner\"},\"second\":{\"uuid\":\"second\",\"originalFilename\":\"second.wav\",\"durationSeconds\":0,\"library\":null}}");
        var manager=new AudioFileManager(world);
        assertEquals(2,manager.getManifest().size());
        assertEquals(1f,manager.getManifest().get("first").getDurationSeconds(),.01f);
        assertEquals(1f,manager.getManifest().get("second").getDurationSeconds(),.01f);
        assertEquals("owner",manager.getManifest().get("first").getOwnerUUID());
        assertEquals(2,new AudioFileManager(world).getManifest().size());
        try(var entries=Files.list(dir)){assertFalse(entries.anyMatch(file -> file.getFileName().toString().contains(".corrupt.")));}
    }
}
