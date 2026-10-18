package com.nstut.simplyspeakers.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class ListeningPreferencesStoreTest {
    @TempDir Path directory;
    @Test void defaultsDoNotCreateServerConfiguration() {
        assertEquals(ListeningPreferencesStore.DEFAULTS, ListeningPreferencesStore.load(directory));
        assertFalse(Files.exists(directory.resolve("simplyspeakers-common.toml")));
        assertFalse(Files.exists(directory.resolve("simplyspeakers.properties")));
    }
    @Test void personalChoicesPersistAndPreserveThemeAndFutureKeys() throws Exception {
        Path file=directory.resolve("simplyspeakers-ui.properties");
        Files.writeString(file,"ui.theme=light\nfuture.option=keep\n");
        var value=new ListeningPreferencesStore.Preferences(true,0,875,1024);
        assertTrue(ListeningPreferencesStore.save(directory,value));
        assertEquals(value,ListeningPreferencesStore.load(directory));
        String saved=Files.readString(file);
        assertTrue(saved.contains("ui.theme=light"));assertTrue(saved.contains("future.option=keep"));
        try(var files=Files.list(directory)) { assertEquals(1,files.count()); }
    }
    @Test void importsForgeValuesBeforeSpecCleanupAndOnlyOnce() throws Exception {
        Path legacy=directory.resolve("simplyspeakers-common.toml");
        String source="# previous settings\nstereoPlayback = true\nrangeFadeInMillis = 0\nrangeFadeOutMillis = 725\nspeakerRange = 123\n";
        Files.writeString(legacy,source);
        var value=new ListeningPreferencesStore.Preferences(true,0,725,500);
        assertEquals(value,ListeningPreferencesStore.load(directory));
        assertEquals(source,Files.readString(legacy));
        Files.writeString(legacy,"stereoPlayback=false\nrangeFadeOutMillis=250\n");
        assertEquals(value,ListeningPreferencesStore.load(directory));
    }
    @Test void importsFabricWithoutOverridingExistingPersonalValues() throws Exception {
        Files.writeString(directory.resolve("simplyspeakers.properties"),"stereoPlayback=true\nrangeFadeInMillis=400\nrangeFadeOutMillis=900\n");
        Files.writeString(directory.resolve("simplyspeakers-ui.properties"),"ui.theme=dark\nlistening.fadeInMillis=125\n");
        assertEquals(new ListeningPreferencesStore.Preferences(true,125,900,500),ListeningPreferencesStore.load(directory));
    }
    @Test void malformedAndOutOfBoundsValuesStaySafe() throws Exception {
        Files.writeString(directory.resolve("simplyspeakers-ui.properties"),"listening.version=1\nlistening.stereo=bad\nlistening.fadeInMillis=-100\nlistening.fadeOutMillis=99999\nlistening.cacheMiB=oops\n");
        assertEquals(new ListeningPreferencesStore.Preferences(false,0,5000,500),ListeningPreferencesStore.load(directory));
        assertTrue(ListeningPreferencesStore.save(directory,new ListeningPreferencesStore.Preferences(false,250,250,99999)));
        assertEquals(4096,ListeningPreferencesStore.load(directory).cacheMiB());
    }
}
