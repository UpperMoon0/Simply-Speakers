package com.nstut.simplyspeakers.audio;

import java.nio.FloatBuffer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ZonePreviewMatricesTest {
    @Test void projectionMultipliesViewInColumnMajorOrderWithoutAdvancingBuffers() {
        FloatBuffer projection=FloatBuffer.wrap(new float[]{3,0,0,0,0,2,0,0,0,0,1,0,0,0,0,1});
        FloatBuffer view=FloatBuffer.wrap(new float[]{1,0,0,0,0,1,0,0,0,0,1,0,4,5,6,1});
        FloatBuffer result=FloatBuffer.allocate(16);
        ZonePreviewMatrices.multiply(projection,view,result);
        assertEquals(12,result.get(12));assertEquals(10,result.get(13));assertEquals(6,result.get(14));
        assertEquals(3,result.get(0));assertEquals(2,result.get(5));assertEquals(1,result.get(10));
        assertEquals(0,projection.position());assertEquals(0,view.position());assertEquals(0,result.position());
        assertThrows(IllegalArgumentException.class,()->ZonePreviewMatrices.multiply(projection,view,view));
    }
}
