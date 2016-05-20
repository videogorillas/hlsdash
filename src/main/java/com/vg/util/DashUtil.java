package com.vg.util;

import static com.vg.util.ADTSHeader.decoderSpecific;
import static js.util.Arrays.asList;
import static org.jcodec.codecs.h264.mp4.AvcCBox.createAvcCBox;
import static org.jcodec.codecs.mpeg4.mp4.EsdsBox.createEsdsBox;
import static org.jcodec.containers.mp4.boxes.ChunkOffsetsBox.createChunkOffsetsBox;
import static org.jcodec.containers.mp4.boxes.MediaHeaderBox.createMediaHeaderBox;
import static org.jcodec.containers.mp4.boxes.MovieHeaderBox.createMovieHeaderBox;
import static org.jcodec.containers.mp4.boxes.SampleDescriptionBox.createSampleDescriptionBox;
import static org.jcodec.containers.mp4.boxes.SampleSizesBox.createSampleSizesBox2;
import static org.jcodec.containers.mp4.boxes.SampleToChunkBox.createSampleToChunkBox;
import static org.jcodec.containers.mp4.boxes.SoundMediaHeaderBox.createSoundMediaHeaderBox;
import static org.jcodec.containers.mp4.boxes.TimeToSampleBox.createTimeToSampleBox;
import static org.jcodec.containers.mp4.boxes.TrackHeaderBox.createTrackHeaderBox;
import static org.jcodec.containers.mp4.boxes.VideoMediaHeaderBox.createVideoMediaHeaderBox;
import static org.jcodec.containers.mp4.muxer.MP4Muxer.terminatorAtom;
import static org.stjs.javascript.Global.console;

import org.jcodec.codecs.h264.H264Utils;
import org.jcodec.codecs.h264.io.model.SeqParameterSet;
import org.jcodec.codecs.h264.mp4.AvcCBox;
import org.jcodec.codecs.mpeg4.mp4.EsdsBox;
import org.jcodec.common.model.Rational;
import org.jcodec.containers.mp4.TrackType;
import org.jcodec.containers.mp4.boxes.AudioSampleEntry;
import org.jcodec.containers.mp4.boxes.ChunkOffsetsBox;
import org.jcodec.containers.mp4.boxes.DataInfoBox;
import org.jcodec.containers.mp4.boxes.DataRefBox;
import org.jcodec.containers.mp4.boxes.Edit;
import org.jcodec.containers.mp4.boxes.EditListBox;
import org.jcodec.containers.mp4.boxes.HandlerBox;
import org.jcodec.containers.mp4.boxes.Header;
import org.jcodec.containers.mp4.boxes.MediaBox;
import org.jcodec.containers.mp4.boxes.MediaHeaderBox;
import org.jcodec.containers.mp4.boxes.MediaInfoBox;
import org.jcodec.containers.mp4.boxes.MovieBox;
import org.jcodec.containers.mp4.boxes.MovieExtendsBox;
import org.jcodec.containers.mp4.boxes.MovieHeaderBox;
import org.jcodec.containers.mp4.boxes.NodeBox;
import org.jcodec.containers.mp4.boxes.SampleDescriptionBox;
import org.jcodec.containers.mp4.boxes.SampleEntry;
import org.jcodec.containers.mp4.boxes.SampleSizesBox;
import org.jcodec.containers.mp4.boxes.SampleToChunkBox;
import org.jcodec.containers.mp4.boxes.SoundMediaHeaderBox;
import org.jcodec.containers.mp4.boxes.SyncSamplesBox;
import org.jcodec.containers.mp4.boxes.TimeToSampleBox;
import org.jcodec.containers.mp4.boxes.TrackExtendsBox;
import org.jcodec.containers.mp4.boxes.TrackHeaderBox;
import org.jcodec.containers.mp4.boxes.TrakBox;
import org.jcodec.containers.mp4.boxes.VideoMediaHeaderBox;

import com.vg.live.video.AVFrame;

import js.lang.System;
import js.util.Arrays;

public class DashUtil {
    //    public static MP4Segment convertToDashBoxes(MovieBox realMovie, long startTime, int sequenceNumber) {
    //        TrakBox track = realMovie.getVideoTrack();
    //        if (track == null) {
    //            track = realMovie.getAudioTracks()
    //                             .get(0);
    //        }
    //
    //        MediaHeaderBox mdhd = NodeBox.findFirst(track, MediaHeaderBox.class, "mdia", "mdhd");
    //        TimeToSampleBox vstts = getStts(track);
    //
    //        int timescale = mdhd.getTimescale();
    //        long duration = mdhd.getDuration();
    //        int vsampleCount = (int) track.getSampleCount();
    //
    //        SegmentIndexBox sidx = SegmentIndexBox.createSegmentIndexBox();
    //        sidx.reference_ID = 1;
    //        sidx.timescale = timescale;
    //        sidx.earliest_presentation_time = startTime;
    //        sidx.first_offset = 0;
    //        sidx.reserved = 0;
    //        sidx.reference_count = 1;
    //        SegmentIndexBox.Reference ref = new SegmentIndexBox.Reference();
    //        ref.reference_type = false;
    //        ref.referenced_size = 100500; //TODO: resulting file size -0x44;
    //        ref.subsegment_duration = duration;
    //        ref.starts_with_SAP = true;
    //        ref.SAP_type = 1;
    //        ref.SAP_delta_time = 0;
    //        sidx.references = new SegmentIndexBox.Reference[] { ref };
    //
    //        MovieFragmentBox moof = new MovieFragmentBox(new Header(MovieFragmentBox.fourcc()));
    //
    //        MovieFragmentHeaderBox mfhd = new MovieFragmentHeaderBox(new Header(MovieFragmentHeaderBox.fourcc()));
    //        mfhd.setSequenceNumber(sequenceNumber);
    //        moof.add(mfhd);
    //
    //        TrackFragmentBox vtraf = new TrackFragmentBox(new Header(TrackFragmentBox.fourcc()));
    //        TrackFragmentHeaderBox vtfhd = new TrackFragmentHeaderBox(new Header(TrackFragmentHeaderBox.fourcc()));
    //        vtfhd.setFlags(0x020000);
    //        vtfhd.setTrackId(1);
    //        vtraf.add(vtfhd);
    //        vtraf.add(createTrackFragmentBaseMediaDecodeTimeBox(startTime));
    //        SyncSamplesBox stss = MP4Helper.getStss(track);
    //        SampleSizesBox vstsz = MP4Helper.getStsz(track);
    //        int[] vsampleSizes = vstsz.getSizes();
    //        if (vsampleSizes == null) {
    //            vsampleSizes = new int[vstsz.getCount()];
    //            Arrays.fill(vsampleSizes, vstsz.getDefaultSize());
    //        }
    //        int[] sampleFlags = getSampleFlags(vsampleCount, stss);
    //        int[] sampleDurations = MP4Helper.getSampleDurations(vstts);
    //        TrunBox.Factory factory = TrunBox.create(vsampleCount)
    //                                         .dataOffset(100500)
    //                                         .sampleSize(vsampleSizes)
    //                                         .sampleFlags(sampleFlags)
    //                                         .sampleDuration(sampleDurations);
    //        TrunBox vtrun = factory.create();
    //        vtraf.add(vtrun);
    //        moof.add(vtraf);
    //
    //        MP4Segment m4s = new MP4Segment();
    //        m4s.styp = SegmentTypeBox.createSegmentTypeBox("msdh", 0, Arrays.asList("msdh", "msix"));
    //        m4s.sidx = sidx;
    //        m4s.moof = moof;
    //        m4s.trun = vtrun;
    //        return m4s;
    //    }

    private static int[] getSampleFlags(int sampleCount, SyncSamplesBox stss) {
        int[] sampleFlags = new int[sampleCount];
        if (stss != null) {
            Arrays.fill(sampleFlags, 0x00010000);
            int[] syncSamples = stss.getSyncSamples();
            for (int i = 0; i < syncSamples.length; i++) {
                int idx = syncSamples[i] - 1;
                sampleFlags[idx] = 0x02000000; //I-frame here
            }
        } else {
            Arrays.fill(sampleFlags, 0x02000000);
        }
        return sampleFlags;
    }

    public static Rational getSAR(SeqParameterSet sps) {
        Rational sar = null;
        if (sps != null && sps.vuiParams != null && sps.vuiParams.aspect_ratio_info_present_flag
                && sps.vuiParams.aspect_ratio != null) {
            sar = sps.vuiParams.aspect_ratio.toRational();
        }
        return sar != null ? sar : Rational.ONE;
    }

    public static MovieBox dashinitVideo(AVFrame videoFrame) {
        int timescale = 90000;
        long created = System.currentTimeMillis();
        long modified = created;
        SeqParameterSet sps = videoFrame.sps;
        Rational sar = getSAR(sps);
        float width = (sps.pic_width_in_mbs_minus1 + 1) << 4;
        float height = SeqParameterSet.getPicHeightInMbs(sps) << 4;
        width = (width * sar.getNum() / sar.getDen());
        height = (height * sar.getNum() / sar.getDen());

        MovieBox moov = MovieBox.createMovieBox();
        int[] matrix = new int[] { 0x10000, 0, 0, 0, 0x10000, 0, 0, 0, 0x40000000 };
        MovieHeaderBox mvhd = createMovieHeaderBox(timescale, 0, 1.0f, 1.0f, created, modified, matrix, 3);
        MovieExtendsBox mvex = MovieExtendsBox.createMovieExtendsBox();
        TrackExtendsBox trex = TrackExtendsBox.createTrackExtendsBox(1, 1, 0x00010000);

        TrakBox trak = TrakBox.createTrakBox();
        TrackHeaderBox tkhd = createTrackHeaderBox(1, 0, width, height, created, modified, 1f, (short) 0, 0, matrix);

        NodeBox edts = new NodeBox(new Header("edts"));
        EditListBox elst = EditListBox.createEditListBox(asList(new Edit(0, 0, 1)));

        MediaBox mdia = MediaBox.createMediaBox();
        MediaHeaderBox mdhd = createMediaHeaderBox(timescale, 0, ENG, created, modified, 0);
        HandlerBox hdlr = HandlerBox.createHandlerBox("mhlr", TrackType.VIDEO.getHandler(), "appl", 0, 0);
        MediaInfoBox minf = MediaInfoBox.createMediaInfoBox();
        VideoMediaHeaderBox vmhd = createVideoMediaHeaderBox(0, 0, 0, 0);
        DataInfoBox dinf = DataInfoBox.createDataInfoBox();
        DataRefBox dref = DataRefBox.createDataRefBox();

        NodeBox stbl = new NodeBox(new Header("stbl"));
        SampleDescriptionBox stsd = createSampleDescriptionBox(new SampleEntry[] {
                DashUtil.videoSampleEntry(videoFrame) });
        TimeToSampleBox stts = createTimeToSampleBox(new TimeToSampleBox.TimeToSampleEntry[0]);
        SampleToChunkBox stsc = createSampleToChunkBox(new SampleToChunkBox.SampleToChunkEntry[0]);
        SampleSizesBox stsz = createSampleSizesBox2(new int[0]);
        ChunkOffsetsBox stco = createChunkOffsetsBox(new long[0]);

        moov.add(mvhd)
            .add(mvex.add(trex))
            .add(trak.add(tkhd)
                     .add(edts.add(elst))
                     .add(mdia.add(mdhd)
                              .add(hdlr)
                              .add(minf.add(vmhd)
                                       .add(dinf.add(dref))
                                       .add(stbl.add(stsd)
                                                .add(stts)
                                                .add(stsc)
                                                .add(stsz)
                                                .add(stco)))));

        return moov;
    }

    public static MovieBox dashinitAudio(AVFrame audioFrame) {
        int timescale = 90000;
        long created = System.currentTimeMillis();
        long modified = created;

        MovieBox moov = MovieBox.createMovieBox();
        int[] matrix = new int[] { 0x10000, 0, 0, 0, 0x10000, 0, 0, 0, 0x40000000 };
        MovieHeaderBox mvhd = createMovieHeaderBox(timescale, 0, 1.0f, 1.0f, created, modified, matrix, 3);
        MovieExtendsBox mvex = MovieExtendsBox.createMovieExtendsBox();
        TrackExtendsBox trex = TrackExtendsBox.createTrackExtendsBox(1, 1, 0x00010000);

        TrakBox trak = TrakBox.createTrakBox();
        TrackHeaderBox tkhd = createTrackHeaderBox(1, 0, 0, 0, created, modified, 1f, (short) 0, 0, matrix);

        NodeBox edts = new NodeBox(new Header("edts"));
        EditListBox elst = EditListBox.createEditListBox(asList(new Edit(0, 0, 1)));

        MediaBox mdia = MediaBox.createMediaBox();
        MediaHeaderBox mdhd = createMediaHeaderBox(timescale, 0, ENG, created, modified, 0);
        HandlerBox hdlr = HandlerBox.createHandlerBox("mhlr", TrackType.SOUND.getHandler(), "appl", 0, 0);
        MediaInfoBox minf = MediaInfoBox.createMediaInfoBox();
        SoundMediaHeaderBox smhd = createSoundMediaHeaderBox();
        DataInfoBox dinf = DataInfoBox.createDataInfoBox();
        DataRefBox dref = DataRefBox.createDataRefBox();

        NodeBox stbl = new NodeBox(new Header("stbl"));
        SampleDescriptionBox stsd = createSampleDescriptionBox(new SampleEntry[] {
                DashUtil.audioSampleEntry(audioFrame) });
        TimeToSampleBox stts = createTimeToSampleBox(new TimeToSampleBox.TimeToSampleEntry[0]);
        SampleToChunkBox stsc = createSampleToChunkBox(new SampleToChunkBox.SampleToChunkEntry[0]);
        SampleSizesBox stsz = createSampleSizesBox2(new int[0]);
        ChunkOffsetsBox stco = createChunkOffsetsBox(new long[0]);

        moov.add(mvhd)
            .add(mvex.add(trex))
            .add(trak.add(tkhd)
                     .add(edts.add(elst))
                     .add(mdia.add(mdhd)
                              .add(hdlr)
                              .add(minf.add(smhd)
                                       .add(dinf.add(dref))
                                       .add(stbl.add(stsd)
                                                .add(stts)
                                                .add(stsc)
                                                .add(stsz)
                                                .add(stco)))));

        return moov;
    }

    private static SampleEntry audioSampleEntry(AVFrame audioFrame) {
        ADTSHeader hdr = audioFrame.adtsHeader;
        EsdsBox esds = createEsdsBox(decoderSpecific(hdr), (hdr.getObjectType() + 1) << 5, 0, 256 * 1024, 128 * 1024, 1);
        AudioSampleEntry ase = AudioSampleEntry.createAudioSampleEntry(Header.createHeader("mp4a", 0L),
                (short)1, (short)hdr.getChanConfig(), (short)16, hdr.getSampleRate(), (short)0, 0, '\ufffe', 0,
                0, 0, 0, 2, (short)0);
        ase.add(esds);
        ase.add(terminatorAtom());
        return ase;
    }

    public static SampleEntry videoSampleEntry(AVFrame videoFrame) {
        SeqParameterSet sps = videoFrame.sps;
        int nalLenSize = 4;
        AvcCBox avcC = createAvcCBox(sps.profile_idc, 0, sps.level_idc, nalLenSize, asList(videoFrame.spsBuf), asList(videoFrame.ppsBuf));

        SampleEntry sampleEntry = H264Utils.createMOVSampleEntryFromAvcC(avcC);
        return sampleEntry;
    }

    static final int ENG = 0x55c4;

}
