package com.nstut.simplyspeakers;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Narrow loader-boundary contracts; behavior is covered by gate/adapter/live tests. */
class AudioContextLifecycleWiringTest {
    private Path root() {
        Path path = Path.of("").toAbsolutePath();
        while (path != null && !Files.isDirectory(path.resolve("common-1.20.1"))) path = path.getParent();
        assertNotNull(path);
        return path;
    }

    @Test void everyRuntimeRegistersLifecycleMixinOnlyOnTheClientSide() throws Exception {
        for (String module : List.of("common-1.20.1", "common-1.21.1", "neoforge-26.1.2")) {
            Path base = root().resolve(module).resolve("src/main");
            var config = JsonParser.parseString(Files.readString(base.resolve("resources/simplyspeakers.mixins.json"))).getAsJsonObject();
            assertTrue(config.getAsJsonArray("client").asList().stream().anyMatch(name -> name.getAsString().equals("SoundEngineMixin")), module);
            assertTrue(config.getAsJsonArray("mixins").isEmpty(), module + " must not load client audio hooks on dedicated servers");
            String code = Files.readString(base.resolve("java/com/nstut/mixin/SoundEngineMixin.java"));
            assertTrue(code.contains("@Inject(method = \"destroy\", at = @At(\"HEAD\"))"), module);
            assertTrue(code.contains("@Inject(method = \"loadLibrary\", at = @At(\"RETURN\"))"), module);
            assertTrue(code.contains("if (loaded) ClientAudioPlayer.soundContextReady()"), module + " must not resume after failed initialization");
            assertEquals(!module.equals("common-1.20.1"), code.contains("method = \"emergencyShutdown\""), module);
        }
    }

    @Test void everyLoaderPackagesTheLifecycleConfig() throws Exception {
        for (String module : List.of("fabric-1.20.1", "fabric-1.21.1")) {
            String metadata = Files.readString(root().resolve(module + "/src/main/resources/fabric.mod.json"));
            assertTrue(metadata.contains("simplyspeakers.mixins.json"), module);
        }
        assertTrue(Files.readString(root().resolve("forge-1.20.1/build.gradle")).contains("mixinConfig \"simplyspeakers.mixins.json\""));
        for (String config : List.of("neoforge-1.21.1/src/main/resources/META-INF/neoforge.mods.toml",
                "neoforge-26.1.2/src/main/templates/META-INF/neoforge.mods.toml")) {
            assertTrue(Files.readString(root().resolve(config)).contains("[[mixins]]\nconfig = \"simplyspeakers.mixins.json\""), config);
        }
    }
}
