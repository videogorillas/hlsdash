package com.vg.live.video;

import org.jcodec.common.IntArrayList;

public class TrackRun {
    public IntArrayList sampleDuration;
    public IntArrayList sampleSize;
    public IntArrayList sampleFlags;
    public IntArrayList sampleCompositionOffset;

    public TrackRun() {
        this.sampleDuration = new IntArrayList(64);
        this.sampleSize = new IntArrayList(64);
        this.sampleFlags = new IntArrayList(64);
        this.sampleCompositionOffset = new IntArrayList(64);
    }

}
