package com.vg.live.worker;

import static com.vg.gopro.PESPacket.isAudio;
import static com.vg.gopro.PESPacket.isVideo;
import static com.vg.util.RxUtil.split;
import static com.vg.util.Utils.lastElement;
import static org.jcodec.codecs.h264.io.model.NALUnitType.IDR_SLICE;
import static org.jcodec.codecs.h264.io.model.NALUnitType.NON_IDR_SLICE;
import static org.jcodec.common.NIOUtils.skip;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.jcodec.codecs.h264.H264Utils;
import org.jcodec.codecs.h264.io.model.NALUnitType;
import org.jcodec.codecs.h264.io.model.PictureParameterSet;
import org.jcodec.codecs.h264.io.model.SeqParameterSet;

import com.vg.gopro.PESPacket;
import com.vg.live.video.AVFrame;
import com.vg.live.video.TSPkt;
import com.vg.util.ADTSHeader;
import com.vg.util.BufferUtil;
import com.vg.util.MutableLong;

import rx.Observable;
import rx.functions.Action1;
import rx.observables.GroupedObservable;

public class TsWorker {
    public static Observable<TSPkt> tsPackets(Observable<ByteBuffer> tsBufs, final long initialPosition) {
        MutableLong streamPosition = new MutableLong(initialPosition);
        Observable<TSPkt> tsPackets = tsBufs.concatMap(ts -> {
            return Observable.create(sub -> {
                try {
                    int limit = ts.limit();
                    for (int pos = ts.position(); pos < limit; pos += 188) {
                        ts.clear();
                        ts.position(pos);
                        ts.limit(Math.min(pos + 188, ts.capacity()));
                        TSPkt pkt = new TSPkt();
                        TSPkt.parsePacket(pkt, ts);
                        pkt.dataOffset = pos;
                        pkt.streamOffset = streamPosition.longValue();
                        streamPosition.add(188);
                        sub.onNext(pkt);
                    }
                    sub.onCompleted();
                } catch (Exception e) {
                    sub.onError(e);
                }
            });
        });
        return tsPackets.onBackpressureBuffer();
    }

    public static Observable<PESPacket> pesPackets(Observable<List<TSPkt>> s, Log2 log) {
        Observable<PESPacket> pesPackets = s.concatMap(pktList -> {
            int payloadLen = pktList.stream().mapToInt(p -> p.payloadLength).sum();
            ByteBuffer pes = ByteBuffer.allocate(payloadLen);
            pktList.forEach(pkt -> pes.put(pkt.payload()));
            pes.flip();
            int pesLimit = pes.limit();
            TSPkt firstPacket = pktList.get(0);
            List<PESPacket> output = new ArrayList<>();
            PESPacket pespkt = null;
            for (int i = pes.position(); i < pesLimit - 4;) {
                if (pespkt != null) {
                    log.w("pesPackets", "@" + firstPacket.streamOffset + "more than one frame per tspacket list");
                }
                int marker = pes.getInt(i);
                if (PESPacket.psMarker(marker)) {
                    try {
                        pespkt = PESPacket.readPESHeader(pes, firstPacket.streamOffset);
                        int newLimit;
                        if (pespkt.payloadSize == 0) {
                            //If the PES packet length is set to zero, the PES packet can be of any length.
                            //A value of zero for the PES packet length can be used only when the PES packet payload is a video elementary stream.
                            newLimit = PESPacket.nextPsMarkerPosition(pes);
                        } else {
                            newLimit = i + PESPacket.PES_HEADER_SIZE + pespkt.payloadSize;
                        }
                        pes.limit(newLimit);

                        pespkt.payload = pes.slice();
                        pespkt.payloadSize = pespkt.payload.limit();
                        pespkt.payloadOffset = pespkt.payload.position();
                        TSPkt lastPacket = lastElement(pktList);
                        int sz = pktList.size() * TSPkt.TSPKT_SIZE;
                        pespkt.streamSize = sz; //(int) (lastPacket.streamOffset - firstPacket.streamOffset + TSPkt.TSPKT_SIZE);
                        output.add(pespkt);
                        pes.position(pes.limit());
                        pes.limit(pesLimit);
                    } catch (Exception e) {
                        log.w("pesPackets", "@" + firstPacket.streamOffset + " cant parse pes packet " + e);
                    }
                    int nextMarkerPosition = PESPacket.nextPsMarkerPosition(pes);
                    i = nextMarkerPosition;
                    pes.position(i);
                    pes.limit(pesLimit);
                } else {
                    i++;
                }
            }
            return Observable.from(output);
        });
        return pesPackets;
    }

    public static Action1<AVFrame> populateSps() {
        return new Action1<AVFrame>() {
            AVFrame prevFrame = null;

            @Override
            public void call(AVFrame curFrame) {
                if (curFrame.isVideo()) {
                    if (prevFrame != null && curFrame.getSps() == null) {
                        curFrame.setSps(prevFrame.getSps());
                        curFrame.spsBuf = prevFrame.spsBuf;
                    }
                    if (prevFrame != null && curFrame.pps == null) {
                        curFrame.pps = prevFrame.pps;
                        curFrame.ppsBuf = prevFrame.ppsBuf;
                    }
                    prevFrame = curFrame;
                }
            }
        };
    }

    public static Observable<AVFrame> skipUntilKeyFrame(Observable<AVFrame> frames) {
        return frames.skipWhile(f -> !f.isIFrame());
    }

    public static Observable<AVFrame> audio(Observable<PESPacket> packets) {
        Observable<AVFrame> frames = packets.map(pespkt -> {
            AVFrame frame = AVFrame.audio(pespkt.streamOffset, pespkt.streamSize);
            frame.dataOffset = 0;
            frame.dataSize = pespkt.payload().remaining();
            frame.data = FramePool.copy(pespkt.payload());
            frame.pts = pespkt.pts;
            frame.dts = pespkt.dts == -1 ? null : pespkt.dts;
            frame.duration = pespkt.duration;
            return frame;
        });
        return frames;
    }

    public static Observable<AVFrame> video(Observable<PESPacket> packets) {
        Observable<AVFrame> frames = packets.map(pespkt -> {
            ByteBuffer payload = pespkt.payload();
            int dataOffset = pespkt.payloadOffset;
            int startPos = payload.position();
            payload.position(startPos);
            int payloadLimit = payload.limit();
            boolean iframe = false;
            SeqParameterSet sps = null;
            PictureParameterSet pps = null;
            ByteBuffer spsBuf = null;
            ByteBuffer ppsBuf = null;
            List<ByteBuffer> splitFrame = H264Utils.splitFrame(payload);
            for (ByteBuffer nalData : splitFrame) {
                nalData.mark();
                NALUnitType naltype = TsWorker.readNal(nalData.get() & 0xff);
                iframe |= naltype == NALUnitType.IDR_SLICE;
                if (naltype == NALUnitType.SPS) {
                    spsBuf = nalData.slice();
                    ByteBuffer escaped = BufferUtil.copy(spsBuf);
                    H264Utils.unescapeNAL(escaped);
                    sps = SeqParameterSet.read(escaped);
                    spsBuf.clear();
                    payload.limit(payloadLimit);
                } else if (naltype == NALUnitType.PPS) {
                    ppsBuf = nalData.slice();
                    ByteBuffer escaped = BufferUtil.copy(ppsBuf);
                    H264Utils.unescapeNAL(escaped);
                    pps = PictureParameterSet.read(escaped);
                    ppsBuf.clear();
                    payload.limit(payloadLimit);
                }
                nalData.reset();
            }
            int dataSize = splitFrame.stream().mapToInt(b -> b.remaining()).sum();
            dataSize += (splitFrame.size() * 4);
    
            ByteBuffer acquire = FramePool.acquire(dataSize);
            for (ByteBuffer nalData : splitFrame) {
                acquire.putInt(nalData.remaining());
                acquire.put(nalData);
            }
            acquire.clear();
    
            AVFrame frame = AVFrame.video(pespkt.streamOffset, pespkt.streamSize, iframe ? IDR_SLICE : NON_IDR_SLICE);
            frame.spsBuf = spsBuf;
            frame.ppsBuf = ppsBuf;
            frame.setSps(sps);
            frame.pps = pps;
            frame.dataOffset = 0;
            frame.dataSize = dataSize;
            frame.data = acquire;
            frame.pts = pespkt.pts;
            frame.dts = pespkt.dts == -1 ? null : pespkt.dts;
            frame.duration = pespkt.duration;
            return frame;
        });
    
        return frames;
    }

    public static NALUnitType readNal(int nalu) {
        int nal_ref_idc = (nalu >> 5) & 0x3;
        int nb = nalu & 0x1f;
        NALUnitType type = NALUnitType.fromValue(nb);
        return type;
    }

    public static Observable<PESPacket> setPESPacketDuration(Observable<PESPacket> pkts) {
        pkts = pkts.buffer(2, 1).map(list -> {
            if (list.size() == 2) {
                PESPacket f1 = list.get(0);
                PESPacket f2 = list.get(1);
                f1.duration = Math.max(0, f2.pts - f1.pts);
                f2.duration = f1.duration;
            }
            return list.get(0);
        });
        return pkts;
    }

    public static Observable<AVFrame> adtstoasc(Observable<AVFrame> frames) {
        Observable<AVFrame> o = frames.concatMap(frame -> {
            ByteBuffer payload = frame.data();
            List<AVFrame> output = new ArrayList<>();
            while (payload.hasRemaining()) {
                ADTSHeader hdr = ADTSHeader.read(payload);
                if (hdr == null) {
                    break;
                }
                int skipCrc = 2 * (0 == hdr.crc_abs ? 1 : 0);
                skip(payload, skipCrc);
                int pos = payload.position();
                int lim = payload.limit();
                payload.limit(payload.position() + hdr.getDataSize());
                ByteBuffer asc = FramePool.copy(payload);
                asc.flip();
                payload.limit(lim);
    
                AVFrame f = AVFrame.audio(frame.streamOffset + pos, asc.remaining());
                f.adtsHeader = hdr;
                f.data = asc;
                f.dataOffset = 0;
                f.dataSize = asc.limit();
                f.pts = -1;
                f.duration = -1;
                output.add(f);
            }
            FramePool.release(frame.data);
            if (!output.isEmpty()) {
                long frameDuration = frame.duration / output.size();
                long pts = frame.pts;
                for (int fn = 0; fn < output.size(); fn++) {
                    AVFrame f = output.get(fn);
                    f.pts = pts;
                    f.duration = frameDuration;
                    pts += frameDuration;
                }
            }
            return Observable.from(output);
        });
        return o;
    
    }

    public static Observable<AVFrame> frameStream(Observable<TSPkt> tsPackets) {
    
        Observable<GroupedObservable<Integer, TSPkt>> tsByPid = tsPackets.groupBy(pkt -> pkt.pid);
        Observable<List<TSPkt>> tsPacketLists = tsByPid.flatMap(g -> split(g, (list, pkt) -> pkt.payloadStart));
        Log2 log = new Log2();
        tsPacketLists = tsPacketLists.filter(list -> list.get(0).payloadStart);
        Observable<PESPacket> pesPackets = pesPackets(tsPacketLists, log);
        Observable<GroupedObservable<String, PESPacket>> audioVideoPes = pesPackets.groupBy(pes -> {
            if (isAudio(pes.streamId)) {
                return TsWorkerTest.AUDIO_PES;
            } else if (isVideo(pes.streamId)) {
                return TsWorkerTest.VIDEO_PES;
            } else {
                return TsWorkerTest.UNKNOWN_PES;
            }
        });
    
        Observable<AVFrame> frames = audioVideoPes.flatMap(_pes -> {
            String pestype = _pes.getKey();
            Observable<PESPacket> pes = setPESPacketDuration(_pes);
    
            if (TsWorkerTest.AUDIO_PES.equals(pestype)) {
                Observable<AVFrame> aframes = audio(pes);
                aframes = adtstoasc(aframes);
                aframes = aframes.doOnNext(frame -> {
                    if (frame.adtsHeader == null) {
                        log.w("adtstoasc", "@" + frame.streamOffset + " cant parse aac header");
                    }
                });
                return aframes;
            } else if (TsWorkerTest.VIDEO_PES.equals(pestype)) {
                return video(pes).doOnNext(populateSps());
            }
            return Observable.empty();
        });
    
        frames = frames.filter(f -> f.isVideo() || f.adtsHeader != null);
        frames = skipUntilKeyFrame(frames);
        return frames;
    }
}
