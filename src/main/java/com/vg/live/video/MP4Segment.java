package com.vg.live.video;

import static js.util.Arrays.asList;
import static org.jcodec.containers.mp4.boxes.MovieFragmentBox.createMovieFragmentBox;
import static org.jcodec.containers.mp4.boxes.MovieFragmentHeaderBox.createMovieFragmentHeaderBox;
import static org.jcodec.containers.mp4.boxes.TrackFragmentBaseMediaDecodeTimeBox.createTrackFragmentBaseMediaDecodeTimeBox;
import static org.jcodec.containers.mp4.boxes.TrackFragmentBox.createTrackFragmentBox;
import static org.jcodec.containers.mp4.boxes.TrackFragmentHeaderBox.createTrackFragmentHeaderBoxWithId;

import org.jcodec.containers.mp4.boxes.MovieFragmentBox;
import org.jcodec.containers.mp4.boxes.MovieFragmentHeaderBox;
import org.jcodec.containers.mp4.boxes.SegmentIndexBox;
import org.jcodec.containers.mp4.boxes.SegmentIndexBox.Reference;

import js.nio.ByteBuffer;

import org.jcodec.containers.mp4.boxes.SegmentTypeBox;
import org.jcodec.containers.mp4.boxes.TrackFragmentBaseMediaDecodeTimeBox;
import org.jcodec.containers.mp4.boxes.TrackFragmentBox;
import org.jcodec.containers.mp4.boxes.TrackFragmentHeaderBox;
import org.jcodec.containers.mp4.boxes.TrunBox;

public class MP4Segment {
    public String mimeType;
    public String codecs;

    public SegmentTypeBox styp;
    public SegmentIndexBox sidx;
    public MovieFragmentBox moof;
    public TrunBox trun;

    public TrackRun trackRun;
    
    public ByteBuffer init;
    public ByteBuffer data;

    public long startTime;

    public MP4Segment() {
        this.trackRun = new TrackRun();
    }

    public static MP4Segment createMP4Segment(long timescale, long startTime, int sequenceNumber) {
        SegmentTypeBox styp = SegmentTypeBox.createSegmentTypeBox("msdh", 0, asList("msdh", "msix"));

        SegmentIndexBox sidx = SegmentIndexBox.createSegmentIndexBox();
        sidx.reference_ID = 1;
        sidx.timescale = timescale;
        sidx.earliest_presentation_time = startTime;
        sidx.first_offset = 0;
        sidx.reserved = 0;
        sidx.reference_count = 1;
        sidx.references = new SegmentIndexBox.Reference[] { new SegmentIndexBox.Reference() };
        SegmentIndexBox.Reference ref = sidx.references[0];
        ref.reference_type = false;
        ref.referenced_size = 100500; //TODO: resulting file size -0x44;
        ref.subsegment_duration = 0;
        ref.starts_with_SAP = true;
        ref.SAP_type = 1;
        ref.SAP_delta_time = 0;
        MovieFragmentBox moof = createMovieFragmentBox();

        MovieFragmentHeaderBox mfhd = createMovieFragmentHeaderBox(sequenceNumber);
        TrackFragmentBox traf = createTrackFragmentBox();
        TrackFragmentHeaderBox tfhd = createTrackFragmentHeaderBoxWithId(1);
        tfhd.setFlags(0x020000);

        TrackFragmentBaseMediaDecodeTimeBox tfdt = createTrackFragmentBaseMediaDecodeTimeBox(startTime);

        moof.add(mfhd)
            .add(traf.add(tfhd)
                     .add(tfdt));

        MP4Segment m4s = new MP4Segment();

        m4s.styp = styp;
        m4s.moof = moof;
        m4s.sidx = sidx;
        m4s.startTime = startTime;

        return m4s;
    }

    public static TrunBox createTrunBox(TrackRun trackRun) {
        TrunBox trun = TrunBox.create(trackRun.sampleSize.size())
                              .dataOffset(100500)
                              .sampleSize(trackRun.sampleSize.toArray())
                              .sampleFlags(trackRun.sampleFlags.toArray())
                              .sampleDuration(trackRun.sampleDuration.toArray())
                              .sampleCompositionOffset(trackRun.sampleCompositionOffset.toArray())
                              .create();
        return trun;
    }

    public boolean isVideo() {
        return mimeType.startsWith("video");
    }
}