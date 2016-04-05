package com.vg.live.worker;

import static com.vg.gopro.PESPacket.isAudio;
import static com.vg.gopro.PESPacket.isVideo;
import static com.vg.util.RxUtil.split;
import static com.vg.util.Utils.lastElement;
import static org.jcodec.codecs.h264.io.model.NALUnitType.IDR_SLICE;
import static org.jcodec.codecs.h264.io.model.NALUnitType.NON_IDR_SLICE;
import static org.jcodec.common.io.NIOUtils.skip;

import org.jcodec.codecs.h264.H264Utils;
import org.jcodec.codecs.h264.io.model.NALUnitType;
import org.jcodec.codecs.h264.io.model.PictureParameterSet;
import org.jcodec.codecs.h264.io.model.SeqParameterSet;

import com.vg.gopro.PESPacket;
import com.vg.js.bridge.Rx.GroupedObservable;
import com.vg.js.bridge.Rx.Observable;
import com.vg.live.video.AVFrame;
import com.vg.live.video.TSPkt;
import com.vg.util.ADTSHeader;
import com.vg.util.BufferUtil;
import com.vg.util.MutableLong;

import js.lang.System;
import js.nio.ByteBuffer;
import js.util.ArrayList;
import js.util.List;

public class TsWorker {
    public static final String UNKNOWN_PES = "unknown";
    public static final String VIDEO_PES = "video";
    public static final String AUDIO_PES = "audio";

    public static Observable<TSPkt> tsPackets(Observable<ByteBuffer> tsBufs, final long initialPosition) {
        MutableLong streamPosition = new MutableLong(initialPosition);
        Observable<TSPkt> tsPackets = tsBufs.concatMap(ts -> {
            List<TSPkt> list = new ArrayList<>();
            int limit = ts.limit();
            for (int pos = ts.position(); pos < limit; pos += 188) {
                ts.clear();
                ts.setPosition(pos);
                ts.setLimit(Math.min(pos + 188, ts.capacity()));
                TSPkt pkt = new TSPkt();
                TSPkt.parsePacket(pkt, ts);
                System.out.println(pkt.toString());
                pkt.dataOffset = pos;
                pkt.streamOffset = streamPosition.longValue();
                streamPosition.add(188);
                list.add(pkt);
            }
            return Observable.from(list.toArray(new TSPkt[0]));
        });
        return tsPackets;
        //                return tsPackets.onBackpressureBuffer();
    }

    static Log2 log = new Log2();

    static int payloadLen(List<TSPkt> pktList) {
        int sum = 0;
        for (TSPkt p : pktList) {
            sum += p.payloadLength;
        }
        return sum;
    }

    static List<PESPacket> fromTsPackets(List<TSPkt> pktList) {
        int payloadLen = payloadLen(pktList);
        ByteBuffer pes = ByteBuffer.allocate(payloadLen);
        pktList.forEach(pkt -> pes.putBuf(pkt.payload()));
        pes.flip();
        int pesLimit = pes.limit();
        TSPkt firstPacket = pktList.get(0);
        List<PESPacket> output = new ArrayList<>();
        PESPacket pespkt = null;
        for (int i = pes.position(); i < pesLimit - 4;) {
            if (pespkt != null) {
                log.w("pesPackets", "@" + firstPacket.streamOffset + "more than one frame per tspacket list");
            }
            int marker = pes.getIntAt(i);
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
                    pes.setLimit(newLimit);

                    pespkt._payload = pes.slice();
                    pespkt.payloadSize = pespkt._payload.limit();
                    pespkt.payloadOffset = pespkt._payload.position();
                    TSPkt lastPacket = lastElement(pktList);
                    int sz = pktList.size() * TSPkt.TSPKT_SIZE;
                    pespkt.streamSize = sz; //(int) (lastPacket.streamOffset - firstPacket.streamOffset + TSPkt.TSPKT_SIZE);
                    output.add(pespkt);
                    pes.setPosition(pes.limit());
                    pes.setLimit(pesLimit);
                } catch (Exception e) {
                    log.w("pesPackets", "@" + firstPacket.streamOffset + " cant parse pes packet " + e);
                }
                int nextMarkerPosition = PESPacket.nextPsMarkerPosition(pes);
                i = nextMarkerPosition;
                pes.setPosition(i);
                pes.setLimit(pesLimit);
            } else {
                i++;
            }
        }
        return output;
    }

    public static Observable<PESPacket> pesPackets(Observable<List<TSPkt>> s, Log2 log) {

        Observable<PESPacket> pesPackets = s.concatMap(pktList -> {
            List<PESPacket> pesList = fromTsPackets(pktList);
            PESPacket[] array = pesList.toArray(new PESPacket[0]);
            return Observable.from(array);
        });
        return pesPackets;
    }

    public static Observable<AVFrame> skipUntilKeyFrame(Observable<AVFrame> frames) {
        return frames.skipWhile(f -> !f.isIFrame());
    }

    public static Observable<AVFrame> audio(Observable<PESPacket> packets) {
        Observable<AVFrame> frames = packets.map(pespkt -> {
            AVFrame frame = AVFrame.audio(pespkt.streamOffset, pespkt.streamSize);
            frame.dataOffset = 0;
            frame.dataSize = pespkt.payload().remaining();
            frame._data = FramePool.copy(pespkt.payload());
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
            payload.setPosition(startPos);
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
                    payload.setLimit(payloadLimit);
                } else if (naltype == NALUnitType.PPS) {
                    ppsBuf = nalData.slice();
                    ByteBuffer escaped = BufferUtil.copy(ppsBuf);
                    H264Utils.unescapeNAL(escaped);
                    pps = PictureParameterSet.read(escaped);
                    ppsBuf.clear();
                    payload.setLimit(payloadLimit);
                }
                nalData.reset();
            }
            int dataSize = dataSize(splitFrame);
            dataSize += (splitFrame.size() * 4);

            ByteBuffer acquire = FramePool.acquire(dataSize);
            for (ByteBuffer nalData : splitFrame) {
                acquire.putInt(nalData.remaining());
                acquire.putBuf(nalData);
            }
            acquire.clear();

            AVFrame frame = AVFrame.video(pespkt.streamOffset, pespkt.streamSize, iframe ? IDR_SLICE : NON_IDR_SLICE);
            frame.spsBuf = spsBuf;
            frame.ppsBuf = ppsBuf;
            frame.sps = sps;
            frame.pps = pps;
            frame.dataOffset = 0;
            frame.dataSize = dataSize;
            frame._data = acquire;
            frame.pts = pespkt.pts;
            frame.dts = pespkt.dts == -1 ? null : pespkt.dts;
            frame.duration = pespkt.duration;
            return frame;
        });

        return frames;
    }

    private static int dataSize(List<ByteBuffer> buffers) {
        int sum = 0;
        for (ByteBuffer b : buffers) {
            sum += b.remaining();
        }
        return sum;
    }

    public static NALUnitType readNal(int nalu) {
        int nal_ref_idc = (nalu >> 5) & 0x3;
        int nb = nalu & 0x1f;
        NALUnitType type = NALUnitType.fromValue(nb);
        return type;
    }

    public static Observable<PESPacket> setPESPacketDuration(Observable<PESPacket> pkts) {
        pkts = pkts.bufferWithCount(2, 1).map(list -> {
            if (list.$length() == 2) {
                PESPacket f1 = list.$get(0);
                PESPacket f2 = list.$get(1);
                f1.duration = Math.max(0, f2.pts - f1.pts);
                f2.duration = f1.duration;
            }
            return list.$get(0);
        });
        return pkts;
    }

    static List<AVFrame> adts2asc(AVFrame frame) {
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
            payload.setLimit(payload.position() + hdr.getDataSize());
            ByteBuffer asc = FramePool.copy(payload);
            asc.flip();
            payload.setLimit(lim);

            AVFrame f = AVFrame.audio(frame.streamOffset + pos, asc.remaining());
            f.adtsHeader = hdr;
            f._data = asc;
            f.dataOffset = 0;
            f.dataSize = asc.limit();
            f.pts = -1;
            f.duration = -1;
            output.add(f);
        }
        FramePool.release(frame._data);
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
        return output;

    }

    public static Observable<AVFrame> adtstoasc(Observable<AVFrame> frames) {
        Observable<AVFrame> o = frames.concatMap(frame -> {
            List<AVFrame> adts2asc = adts2asc(frame);
            AVFrame[] array = adts2asc.toArray(new AVFrame[0]);
            return Observable.from(array);
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
                return AUDIO_PES;
            } else if (isVideo(pes.streamId)) {
                return VIDEO_PES;
            } else {
                return UNKNOWN_PES;
            }
        });

        Observable<AVFrame> frames = audioVideoPes.flatMap(_pes -> {
            String pestype = _pes.key;
            Observable<PESPacket> pes = setPESPacketDuration(_pes);
            if (AUDIO_PES.equals(pestype)) {
                Observable<AVFrame> aframes = audio(pes);
                aframes = adtstoasc(aframes);
                aframes = aframes.doOnNext(frame -> {
                    if (frame.adtsHeader == null) {
                        log.w("adtstoasc", "@" + frame.streamOffset + " cant parse aac header");
                    }
                });
                return aframes;
            } else if (VIDEO_PES.equals(pestype)) {
                return populateSpsPps(video(pes));
            }
            return Observable.empty();
        });

        frames = frames.filter(f -> f.isVideo() || f.adtsHeader != null);
        frames = skipUntilKeyFrame(frames);
        return frames;
    }

    static Observable<AVFrame> populateSpsPps(Observable<AVFrame> video) {
        return video.scan((prevFrame, curFrame) -> {
            if (prevFrame != null && curFrame.sps == null) {
                curFrame.sps = prevFrame.sps;
                curFrame.spsBuf = prevFrame.spsBuf;
            }
            if (prevFrame != null && curFrame.pps == null) {
                curFrame.pps = prevFrame.pps;
                curFrame.ppsBuf = prevFrame.ppsBuf;
            }
            return curFrame;
        });
    }
}
