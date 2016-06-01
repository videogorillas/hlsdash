package com.vg.live.worker;

import com.vg.js.bridge.Rx;
import com.vg.js.bridge.Rx.GroupedObservable;
import com.vg.js.bridge.Rx.Observable;
import com.vg.live.video.AVFrame;
import com.vg.live.video.MP4Segment;
import com.vg.live.video.TSPkt;
import com.vg.live.video.TSStream;
import com.vg.util.MutableBoolean;
import com.vg.util.MutableLong;
import js.nio.ByteBuffer;
import org.jcodec.containers.mp4.boxes.FileTypeBox;
import org.jcodec.containers.mp4.boxes.Header;
import org.jcodec.containers.mp4.boxes.MovieBox;
import org.stjs.javascript.Global;

import static com.vg.live.video.MP4Segment.createMP4Segment;
import static com.vg.util.DashUtil.dashinitAudio;
import static com.vg.util.DashUtil.dashinitVideo;
import static js.util.Arrays.asList;
import static org.jcodec.containers.mp4.boxes.FileTypeBox.createFileTypeBox;
import static org.stjs.javascript.Global.console;

public class M4sWorker {
    public static Observable<AVFrame> frames(ByteBuffer bufIn) {
        TSStream stream = new TSStream();
        Observable<TSPkt> tsPackets = TsWorker.tsPackets(Observable.just(bufIn), 0)
                .doOnNext(stream::parsePSI)
                .filter(pkt -> TSPkt.isElementaryStreamPID(pkt.pid) && !stream.isPMT(pkt.pid));
        return TsWorker.frameStream(tsPackets);
    }

    public static Observable<MP4Segment> framesToM4sDemux(Observable<AVFrame> frames, int size, int sequenceNumber,
                                                          long videoStartTv, long audioStartTv) {
        return frames
                .groupBy(AVFrame::isVideo)
                .flatMap(frames1 ->
                        framesToM4s(frames1, size, sequenceNumber, frames1.key ? videoStartTv : audioStartTv));
    }

    public static Observable<MP4Segment> framesToM4s(Observable<AVFrame> frames, int size, int sequenceNumber,
                                                     long startPts) {
        long timescale = 90000;
        MutableBoolean hasInit = new MutableBoolean(false);
        MutableLong originalStartPts = new MutableLong(-1);

        MP4Segment segment = createMP4Segment(timescale, startPts, sequenceNumber);
        ByteBuffer init = FramePool.acquire(2048);
        ByteBuffer data = FramePool.acquire(size);

        frames = frames
                .doOnNext(frame -> {
                    if (!hasInit.value) {
                        hasInit.value = true;

                        console.log(frame.isVideo() ? "video" : "audio", "first pts", frame.pts, "dts", frame.dts);

                        FileTypeBox ftyp = createFileTypeBox("iso5", 1, asList("avc1", "iso5", "dash"));
                        MovieBox moov = frame.isVideo() ? dashinitVideo(frame) : dashinitAudio(frame);
                        ftyp.write(init);
                        moov.write(init);
                        init.flip();

                        segment.mimeType = frame.isVideo() ? "video/mp4" : "audio/mp4";
                        segment.codecs = frame.isVideo() ? "avc1.4d4028" : "mp4a.40.2";   // TODO

                        originalStartPts.value = frame.pts;
                    }
                })
                .doOnNext(f -> {
                    f.pts = f.pts - originalStartPts.value + startPts;
                    if (f.dts != null && f.dts != -1) {
                        f.dts = f.dts - originalStartPts.value + startPts;
                    }
                });

        ByteBuffer _mdat = FramePool.acquire(size);

        Observable<MP4Segment> m4srx = frames.reduce((m4s, frame) -> {
            int compOffset = frame.dts != null ? (int) (frame.pts - frame.dts) : 0;
                        if (frame.isVideo()) console.log(frame.pts, frame.dts, compOffset, frame.isIFrame() ? "I" : "");
            m4s.sidx.references[0].subsegment_duration += frame.duration;
            m4s.trackRun.sampleCompositionOffset.add(compOffset);
            m4s.trackRun.sampleDuration.add((int) frame.duration);
            int flags = (frame.isIFrame() || frame.isAudio()) ? 0x02000000 : 0x01010000;
            m4s.trackRun.sampleFlags.add(flags);
            m4s.trackRun.sampleSize.add(frame.dataSize);
            _mdat.putBuf(frame.data());
            FramePool.release(frame._data);
            return m4s;
        }, segment);

        Observable<MP4Segment> outputrx = m4srx.map(m4s -> {
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
