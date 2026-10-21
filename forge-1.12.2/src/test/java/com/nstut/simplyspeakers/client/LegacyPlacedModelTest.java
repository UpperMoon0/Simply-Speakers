package com.nstut.simplyspeakers.client;

import java.lang.reflect.Proxy;
import java.util.Collections;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.EnumFacing;
import org.junit.Test;
import static org.junit.Assert.*;

public class LegacyPlacedModelTest {
    private static class Sprite extends TextureAtlasSprite { Sprite(String name) { super(name); } }
    private static IBakedModel model(TextureAtlasSprite sprite) {
        return (IBakedModel)Proxy.newProxyInstance(IBakedModel.class.getClassLoader(), new Class<?>[]{IBakedModel.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getQuads")) return sprite == null || args[1] != null
                            ? Collections.emptyList() : Collections.singletonList(new BakedQuad(new int[28], -1, EnumFacing.NORTH, sprite));
                    throw new AssertionError("Unexpected native model call: " + method);
                });
    }
    @Test public void genuineOwnedTexturedQuadQualifies() {
        assertEquals(1, LegacyClientRuntimeAcceptance.validatePlacedModel(model(new Sprite("simplyspeakers:block/speaker_front")), model(null), new Sprite("missingno"), null));
    }
    @Test public void nativeMissingModelCannotQualify() {
        IBakedModel missing = model(new Sprite("simplyspeakers:block/speaker_front"));
        rejected(missing, missing, new Sprite("missingno"));
    }
    @Test public void emptyAndMissingOrForeignTexturesCannotQualify() {
        TextureAtlasSprite missing = new Sprite("missingno");
        rejected(model(null), model(null), missing);
        rejected(model(missing), model(null), missing);
        rejected(model(new Sprite("minecraft:missingno")), model(null), missing);
    }
    private static void rejected(IBakedModel model, IBakedModel missing, TextureAtlasSprite sprite) {
        try { LegacyClientRuntimeAcceptance.validatePlacedModel(model, missing, sprite, null); fail("Missing geometry must fail"); }
        catch (IllegalStateException expected) { assertTrue(expected.getMessage().contains("SIMPLYSPEAKERS_VERIFY_FAIL")); }
    }
}
