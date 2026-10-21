package com.nstut.simplyspeakers.compat;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.io.*;
import static org.junit.jupiter.api.Assertions.*;
class Java8Test {
    @Test void immutableCollectionsCopyAndRejectNulls() {
        List<String> original=new ArrayList<>(Arrays.asList("a"));
        List<String> copy=Java8.listCopy(original);original.clear();assertEquals(Arrays.asList("a"),copy);
        assertThrows(UnsupportedOperationException.class,()->copy.add("b"));
        assertThrows(NullPointerException.class,()->Java8.list("a",null));
        assertThrows(IllegalArgumentException.class,()->Java8.set("a","a"));
        assertEquals(Collections.singleton("a"),Java8.setCopy(Arrays.asList("a","a")));
    }
    @Test void mapsPreserveFactoryContracts() {
        assertEquals("v",Java8.map("k","v").get("k"));
        assertThrows(NullPointerException.class,()->Java8.map("k",null));
        assertThrows(IllegalArgumentException.class,()->Java8.map("k","v","k","v2"));
        assertThrows(UnsupportedOperationException.class,()->Java8.map("k","v").clear());
    }
    @Test void whitespaceIsUnicodeAwareAndTrailingOnly() {
        assertTrue(Java8.isBlank(" \t\u2003"));assertFalse(Java8.isBlank("\u00a0"));
        assertEquals(" \ud83d\ude00",Java8.stripTrailing(" \ud83d\ude00\u2003\t"));
    }
    @Test void repetitionRejectsNegativeCountAndHandlesEmpty() {
        assertEquals("",Java8.repeat("a",0));assertEquals("界界",Java8.repeat("界",2));
        assertThrows(IllegalArgumentException.class,()->Java8.repeat("a",-1));
    }
    @Test void streamCopyPreservesBytesWithoutClosingCallerStream() throws Exception {
        byte[] data=new byte[17001];new Random(14).nextBytes(data);
        assertArrayEquals(data,Java8.readAllBytes(new ByteArrayInputStream(data)));
    }
}
