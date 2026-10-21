package com.nstut.simplyspeakers.compat;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import net.minecraft.client.renderer.block.model.ModelBlockDefinition;
import net.minecraft.client.renderer.block.model.Variant;
import net.minecraft.util.ResourceLocation;
import org.junit.Test;
import static org.junit.Assert.*;

/** Parse the actual generated resource pack with Minecraft 1.12's native loader. */
public class NativeBlockstateModelsTest {
    @Test public void allTwentyFourNativeVariantsResolveToPackagedModels() throws Exception {
        for(String block:new String[]{"speaker","proxy_speaker","redstone_controller"}) {
            try(InputStream input=getClass().getResourceAsStream("/assets/simplyspeakers/blockstates/"+block+".json")) {
                assertNotNull("Generated main resources must be on the test runtime classpath",input);
                ModelBlockDefinition model=ModelBlockDefinition.parseFromReader(new InputStreamReader(input,StandardCharsets.UTF_8));
                for(String facing:new String[]{"north","south","west","east"}) for(boolean powered:new boolean[]{false,true}) {
                    for(Variant variant:model.getVariant("facing="+facing+",powered="+powered).getVariantList()) {
                        ResourceLocation location=variant.getModelLocation();
                        assertEquals("block/"+block,location.getPath());
                        String resource="/assets/"+location.getNamespace()+"/models/"+location.getPath()+".json";
                        try(InputStream packaged=getClass().getResourceAsStream(resource)) {
                            assertNotNull("Native model must exist in the generated pack: "+resource,packaged);
                        }
                    }
                }
            }
        }
    }
    @Test public void previousModernPrefixProducesMissingNativeResource() {
        ModelBlockDefinition model=ModelBlockDefinition.parseFromReader(new StringReader("{\"variants\":{\"facing=north,powered=false\":{\"model\":\"simplyspeakers:block/speaker\"}}}"));
        ResourceLocation location=model.getVariant("facing=north,powered=false").getVariantList().get(0).getModelLocation();
        assertEquals("block/block/speaker",location.getPath());
        assertNull(getClass().getResource("/assets/simplyspeakers/models/"+location.getPath()+".json"));
    }
}
