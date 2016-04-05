package com.vg.live.worker;

import static com.vg.live.video.TSPkt.PAT_PID;
import static com.vg.util.ADTSHeader.decoderSpecific;
import static js.util.Arrays.asList;
import static org.jcodec.codecs.h264.mp4.AvcCBox.createAvcCBox;
import static org.jcodec.codecs.mpeg4.mp4.EsdsBox.createEsdsBox;
import static org.jcodec.common.io.NIOUtils.writableFileChannel;
import static org.jcodec.containers.mp4.MP4Packet.createMP4Packet;
import static org.stjs.javascript.Global.console;
import static org.stjs.javascript.JSObjectAdapter.$get;

import js.io.File;
import js.io.FileNotFoundException;
import js.io.IOException;
import js.lang.System;
import js.nio.ByteBuffer;
import js.util.List;

import org.jcodec.codecs.h264.H264Utils;
import org.jcodec.codecs.h264.io.model.SeqParameterSet;
import org.jcodec.codecs.h264.mp4.AvcCBox;
import org.jcodec.codecs.mpeg4.mp4.EsdsBox;
import org.jcodec.common.io.FileChannelWrapper;
import org.jcodec.common.io.NIOUtils;
import org.jcodec.containers.mp4.MP4Packet;
import org.jcodec.containers.mp4.TrackType;
import org.jcodec.containers.mp4.boxes.Box;
import org.jcodec.containers.mp4.boxes.SampleEntry;
import org.jcodec.containers.mp4.muxer.AbstractMP4MuxerTrack;
import org.jcodec.containers.mp4.muxer.FramesMP4MuxerTrack;
import org.jcodec.containers.mp4.muxer.MP4Muxer;
import org.jcodec.containers.mps.psi.PATSection;
import org.jcodec.containers.mps.psi.PMTSection;
import org.junit.Test;
import org.stjs.javascript.Global;
import org.stjs.javascript.JSObjectAdapter;

import com.vg.js.bridge.Rx.Observable;
import com.vg.live.video.AVFrame;
import com.vg.live.video.TSPkt;
import com.vg.util.ADTSHeader;

public class TsWorkerTest {

    public static class TSStream {
        public PATSection pat;
        public PMTSection pmt;
        public int[] pmtPIDs;
        public long startPts = -1;

        public boolean isPMT(int pid) {
            if (pmtPIDs != null) {
                for (int pmtPID : pmtPIDs) {
                    if (pid == pmtPID)
                        return true;
                }
            }
            return false;
        }

        /**
         * https://en.wikipedia.org/wiki/Program-specific_information
         * 
         * @param pkt
         */
        public void parsePSI(TSPkt pkt) {
            TSStream stream = this;
            if (pkt.pid == PAT_PID) {
                ByteBuffer payload = pkt.payload();
                if (pkt.payloadStart) {
                    int pointerField = payload.get() & 0xff;
                    if (pointerField != 0) {
                        payload.setPosition(payload.position() + pointerField);
                    }
                }
                PATSection pat = PATSection.parsePAT(payload);
                stream.pat = pat;
                stream.pmtPIDs = stream.pat.getPrograms().values();
            }
            if (stream.isPMT(pkt.pid)) {
                ByteBuffer payload = pkt.payload();
                if (pkt.payloadStart) {
                    int pointerField = payload.get() & 0xff;
                    if (pointerField != 0) {
                        payload.setPosition(payload.position() + pointerField);
                    }
                }
                PMTSection pmt = PMTSection.parsePMT(payload);
                stream.pmt = pmt;
            }
        }
    }

    static MP4Packet mp4(AVFrame f, long startPts) {
        MP4Packet pkt = createMP4Packet(f.data(), f.pts - startPts, 90000, Math.max(0, f.duration), 0, f
                .isIFrame(), null, 0, f.pts - startPts, 0);
        return pkt;
    }

    public void testPipeline1() throws Exception {
        File file2 = new File("tmp/out.mp4");
        FileChannelWrapper w = NIOUtils.writableChannel(file2);
        MP4Muxer muxer = MP4Muxer.createMP4MuxerToChannel(w);

        FramesMP4MuxerTrack vTrack = muxer.addTrack(TrackType.VIDEO, 90000);

        AvcCBox avcc = createAvcCBox(0, 0, 0, 4, asList(ByteBuffer.allocate(42)), asList(ByteBuffer.allocate(4)));
        SampleEntry se = H264Utils.createMOVSampleEntryFromAvcC(avcc);
        vTrack.addSampleEntry(se);
        muxer.writeHeader();
        w.close();

    }

    @Test
    public void testPipeline() throws Exception {
        File file = new File("testdata/zoomoo/76fab9ea8d4dc3941bd0872b7bef2c9c_31321.ts");
        //        File file = new File("testdata/apple/06402.ts");
        File outputDir = emptyDir(new File("tmp/hlsjs"));
        ByteBuffer buf = readFileToByteBuffer(file);

        TSStream stream = new TSStream();
        Observable<TSPkt> tsPackets = TsWorker.tsPackets(Observable.just(buf), 0);
        tsPackets = tsPackets.doOnNext(pkt -> stream.parsePSI(pkt));
        tsPackets = tsPackets.filter(pkt -> TSPkt.isElementaryStreamPID(pkt.pid) && !stream.isPMT(pkt.pid));

        Observable<AVFrame> frames = TsWorker.frameStream(tsPackets);

        frames = frames.doOnNext(f -> {
            if (stream.startPts == -1) {
                stream.startPts = f.pts;
            }
        });

        File file2 = new File(outputDir, "out.mp4");
        FileChannelWrapper w = NIOUtils.writableChannel(file2);
        MP4Muxer muxer = MP4Muxer.createMP4MuxerToChannel(w);
        Observable<MP4Muxer> reduce = frames.reduce((m, f) -> {
            console.log("frame", f.toString());
            try {
                if (f.isVideo()) {
                    FramesMP4MuxerTrack vTrack = (FramesMP4MuxerTrack) muxer.getVideoTrack();
                    if (vTrack == null) {
                        vTrack = addVideoTrack(m, f);
                    }
                    vTrack.addFrame(mp4(f, stream.startPts));
                } else if (f.adtsHeader != null) {
                    List<AbstractMP4MuxerTrack> audioTracks = muxer.getAudioTracks();
                    FramesMP4MuxerTrack aTrack = (FramesMP4MuxerTrack) (audioTracks.isEmpty() ? null
                            : audioTracks.get(0));
                    if (aTrack == null) {
                        aTrack = addAudioTrack(m, f);
                    }
                    aTrack.addFrame(mp4(f, stream.startPts));
                }
                FramePool.release(f._data);
            } catch (Exception e) {
                console.error(e);
                throw new RuntimeException(e);
            }
            return m;
        }, muxer);
        reduce = reduce.doOnNext(m -> {
            try {
                m.writeHeader();
                w.close();
            } catch (Exception e) {
                console.log("e", e);
                console.log("stack", $get(e, "stack"));
                throw new RuntimeException(e);
            }
        });
        reduce.subscribe(x -> {
            System.out.println(x);
        }, err -> {
            console.log("err", err);
            console.log("stack", JSObjectAdapter.$get(err, "stack"));
        }, () -> {
            console.log("done.");
            console.log("FramePool", FramePool.pool);
            console.log("FramePool", FramePool.pool.toString());
        });

    }

    private ByteBuffer readFileToByteBuffer(File file) throws FileNotFoundException, IOException {
        FileChannelWrapper channel = NIOUtils.readableChannel(file);
        int size = (int) channel.size();
        ByteBuffer buf = ByteBuffer.allocate(size);
        while (buf.hasRemaining()) {
            channel.read(buf);
        }
        buf.clear();
        return buf;
    }

    private static File emptyDir(File dir) {
        dir.mkdirs();
        File[] listFiles = dir.listFiles();
        if (listFiles != null) {
            for (int i = 0; i < listFiles.length; i++) {
                File f = listFiles[i];
                System.out.println("rm " + f);
                f.$delete();
            }
        }
        return dir;
    }

    private FramesMP4MuxerTrack addVideoTrack(MP4Muxer muxer, AVFrame videoFrame) {
        FramesMP4MuxerTrack vTrack = muxer.addTrack(TrackType.VIDEO, 90000);
        SampleEntry sampleEntry = videoSampleEntry(videoFrame);
        vTrack.addSampleEntry(sampleEntry);
        return vTrack;
    }

    private FramesMP4MuxerTrack addAudioTrack(MP4Muxer muxer, AVFrame audioFrame) {
        ADTSHeader hdr = audioFrame.adtsHeader;
        EsdsBox esds = createEsdsBox(decoderSpecific(hdr), (hdr.getObjectType() + 1) << 5, 0, 256 * 1024, 128
                * 1024, 2);
        FramesMP4MuxerTrack aTrack = muxer.addCompressedAudioTrack("mp4a", 90000, hdr.getChanConfig(), hdr
                .getSampleRate(), 0, new Box[] { esds });
        return aTrack;
    }

    private SampleEntry videoSampleEntry(AVFrame videoFrame) {
        SeqParameterSet sps = videoFrame.sps;
        int nalLenSize = 4;
        AvcCBox avcC = createAvcCBox(sps.profile_idc, 0, sps.level_idc, nalLenSize, asList(videoFrame.spsBuf), asList(videoFrame.ppsBuf));

        SampleEntry sampleEntry = H264Utils.createMOVSampleEntryFromAvcC(avcC);
        return sampleEntry;
    }

}
