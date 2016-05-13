package com.vg.live.worker;

import com.vg.js.bridge.Rx;
import com.vg.live.video.AVFrame;
import com.vg.live.video.MP4Segment;
import com.vg.live.video.TSPkt;
import com.vg.live.video.TSStream;
import com.vg.util.MutableBoolean;
import js.nio.ByteBuffer;
import org.jcodec.containers.mp4.boxes.FileTypeBox;
import org.jcodec.containers.mp4.boxes.Header;
import org.jcodec.containers.mp4.boxes.MovieBox;

import static com.vg.live.video.MP4Segment.createMP4Segment;
import static com.vg.util.DashUtil.dashinit;
import static js.util.Arrays.asList;
import static org.jcodec.containers.mp4.boxes.FileTypeBox.createFileTypeBox;

public class M4sWorker {
    public static Rx.Observable<AVFrame> frames(ByteBuffer bufIn) {
        TSStream stream = new TSStream();
        Rx.Observable<TSPkt> tsPackets = TsWorker.tsPackets(Rx.Observable.just(bufIn), 0);
        tsPackets = tsPackets.doOnNext(pkt -> stream.parsePSI(pkt));
        tsPackets = tsPackets.filter(pkt -> TSPkt.isElementaryStreamPID(pkt.pid) && !stream.isPMT(pkt.pid));

        Rx.Observable<AVFrame> frames = TsWorker.frameStream(tsPackets);

        frames = frames.doOnNext(f -> {
            if (stream.startPts == -1) {
                stream.startPts = f.pts;
            }
            f.pts = f.pts - stream.startPts;
            if (f.dts != null && f.dts != -1) {
                f.dts = f.dts - stream.startPts;
            }
        });
        return frames;
    }

    public static Rx.Observable<MP4Segment> m4s(ByteBuffer inputBuf, long startTime, int sequenceNumber) {

        Rx.Observable<AVFrame> frames = frames(inputBuf);

//        на этом кадре происходит кирдык. браузер не может проиграть видео. почему - хз

        frames = frames.filter(f -> f.isVideo());
        MutableBoolean hasInit = new MutableBoolean(false);
        long timescale = 90000;

        MP4Segment segment = createMP4Segment(timescale, startTime, sequenceNumber);
        ByteBuffer init = FramePool.acquire(2048);
        ByteBuffer data = FramePool.acquire(inputBuf.capacity());

        frames = frames.doOnNext(frame -> {
            if (!hasInit.value) {
                hasInit.value = true;
                FileTypeBox ftyp = createFileTypeBox("iso5", 1, asList("avc1", "iso5", "dash"));
                MovieBox moov = dashinit(frame);
                ftyp.write(init);
                moov.write(init);
                init.flip();
            }
        });

        ByteBuffer _mdat = FramePool.acquire(inputBuf.capacity());

        Rx.Observable<MP4Segment> m4srx = frames.reduce((m4s, frame) -> {
            int compOffset = frame.dts != null ? (int) (frame.pts - frame.dts) : 0;
            //            console.log(frame.pts, frame.dts, compOffset);
            m4s.sidx.references[0].subsegment_duration += frame.duration;
            m4s.trackRun.sampleCompositionOffset.add(compOffset);
            m4s.trackRun.sampleDuration.add((int) frame.duration);
            int flags = frame.isIFrame() ? 0x02000000 : 0x01010000;
            m4s.trackRun.sampleFlags.add(flags);
            m4s.trackRun.sampleSize.add(frame.dataSize);
            _mdat.putBuf(frame.data());
            FramePool.release(frame._data);
            return m4s;
        }, segment);

        Rx.Observable<MP4Segment> outputrx = m4srx.map(m4s -> {
            m4s.trun = MP4Segment.createTrunBox(m4s.trackRun);
            m4s.moof.getTracks()[0].add(m4s.trun);

            _mdat.flip();
            long dataSize = _mdat.remaining();
            long mdatSize = dataSize + 8;
            Header mdat = Header.createHeader("mdat", mdatSize);
            int frameCount = segment.trackRun.sampleSize.size();
            int headerSize = (frameCount * 16) * 2 + 68 + 12;
            ByteBuffer hdr = FramePool.acquire(headerSize);
            m4s.styp.write(hdr);
            m4s.sidx.write(hdr);
            int sidxEndPosition = hdr.position();
            m4s.moof.write(hdr);
            mdat.write(hdr);

            int videoDataOffset = hdr.position();
            m4s.trun.setDataOffset(videoDataOffset - 68);
            m4s.sidx.references[0].referenced_size = videoDataOffset + dataSize - sidxEndPosition;

            hdr.clear();
            m4s.styp.write(hdr);
            m4s.sidx.write(hdr);
            m4s.moof.write(hdr);
            mdat.write(hdr);
            hdr.flip();
            while (hdr.hasRemaining()) {
                data.putBuf(hdr);
            }
            while (_mdat.hasRemaining()) {
                data.putBuf(_mdat);
            }
            data.flip();
            FramePool.release(hdr);
            FramePool.release(_mdat);

            segment.init = init;
            segment.data = data;
            return segment;
        });
        return outputrx;
    }
}
