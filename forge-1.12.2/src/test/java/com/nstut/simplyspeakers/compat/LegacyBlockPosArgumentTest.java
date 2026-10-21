package com.nstut.simplyspeakers.compat;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import org.junit.Test;
import static org.junit.Assert.*;
public class LegacyBlockPosArgumentTest {
    @Test public void absoluteAndRelativeCoordinates() throws Exception {
        LegacyBlockPosArgument.Coordinates coordinate = LegacyBlockPosArgument.blockPos().parse(new StringReader("12 ~-3 ~"));
        assertArrayEquals(new double[]{12,-3,0}, coordinate.v, 0);
        assertArrayEquals(new boolean[]{false,true,true}, coordinate.r);
        assertFalse(coordinate.local);
    }
    @Test public void localCoordinates() throws Exception {
        LegacyBlockPosArgument.Coordinates coordinate = LegacyBlockPosArgument.blockPos().parse(new StringReader("^ ^2 ^-4"));
        assertArrayEquals(new double[]{0,2,-4}, coordinate.v, 0);
        assertTrue(coordinate.local);
    }
    @Test public void malformedAndMixedCoordinatesAreRejected() throws Exception {
        for (String text : new String[]{"", "1", "1 2", "1 2 ", "1  2 3", "^ ~ ^", "~ ^ ~"}) {
            try { LegacyBlockPosArgument.blockPos().parse(new StringReader(text)); fail(text); }
            catch (CommandSyntaxException expected) { }
        }
    }
    @Test public void leavesFollowingSubcommandUnconsumed() throws Exception {
        StringReader reader = new StringReader("~ ~ ~ info");
        LegacyBlockPosArgument.blockPos().parse(reader);
        assertEquals(" info", reader.getRemaining());
    }
}
