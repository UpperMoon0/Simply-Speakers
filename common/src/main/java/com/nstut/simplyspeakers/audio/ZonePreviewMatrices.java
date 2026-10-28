package com.nstut.simplyspeakers.audio;

import java.nio.FloatBuffer;

/** Column-major projection/view multiplication for pre-JOML Minecraft adapters. */
public final class ZonePreviewMatrices {
    private ZonePreviewMatrices() {}
    public static void multiply(FloatBuffer projection,FloatBuffer view,FloatBuffer result) {
        if(result==projection || result==view) throw new IllegalArgumentException("Preview matrix output must be distinct");
        for(int column=0;column<4;column++)for(int row=0;row<4;row++) {
            float value=0;
            for(int k=0;k<4;k++) value+=projection.get(k*4+row)*view.get(column*4+k);
            result.put(column*4+row,value);
        }
    }
}
