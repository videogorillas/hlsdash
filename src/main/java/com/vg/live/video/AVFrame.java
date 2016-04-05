package com.vg.live.video;

import js.nio.ByteBuffer;

import org.jcodec.codecs.h264.io.model.NALUnitType;
import org.jcodec.codecs.h264.io.model.PictureParameterSet;
import org.jcodec.codecs.h264.io.model.SeqParameterSet;

import com.vg.util.ADTSHeader;

public class AVFrame {
    public static final AVFrame EOF = new AVFrame(null, -1, -1, null);
    public final String type;
    public long pts;
    public Long dts;
    public long duration;
    public final long streamOffset;
    public final int streamSize;
    public final NALUnitType nalType;
    public transient ByteBuffer _data;
    public int dataSize;
    public int dataOffset;
    public transient SeqParameterSet sps;
    public transient PictureParameterSet pps;
    public transient ADTSHeader adtsHeader;
    public transient ByteBuffer spsBuf;
    public transient ByteBuffer ppsBuf;

    public AVFrame(String type, long streamOffset, int size, NALUnitType nalType) {
        this.type = type;
        this.streamOffset = streamOffset;
        this.dataSize = size;
        this.streamSize = size;
        this.nalType = nalType;
    }

    public boolean isIFrame() {
        return nalType == NALUnitType.IDR_SLICE;
    }

    @Override
    public String toString() {
        return "{\"type\":\"" + type + "\",\"pts\":\"" + pts + "\",\"dts\":\"" + dts + "\",\"duration\":\"" + duration
                + "\",\"streamOffset\":\"" + streamOffset + "\",\"streamSize\":\"" + streamSize + "\",\"nalType\":\""
                + nalType + "\",\"dataSize\":\"" + dataSize + "\",\"dataOffset\":\"" + dataOffset + "\"}";
    }

    public static AVFrame audio(long offset, int size) {
        return new AVFrame("A", offset, size, null);
    }

    public static AVFrame video(long offset, int size, NALUnitType nalType) {
        return new AVFrame("V", offset, size, nalType);
    }

    public boolean isVideo() {
        return nalType != null;
    }

    public ByteBuffer data() {
        _data.setLimit(dataOffset + dataSize);
        _data.setPosition(dataOffset);
        return _data;
    }

    public boolean isAudio() {
        return !isVideo();
    }

}