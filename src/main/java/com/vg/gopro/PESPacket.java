package com.vg.gopro;

import java.nio.ByteBuffer;

/**
 * https://en.wikipedia.org/wiki/Packetized_elementary_stream
 * 
 * @author zhukov
 *
 */
public class PESPacket {
    public transient ByteBuffer payload;
    public int streamId;
    public long streamOffset;
    public int streamSize;
    public int payloadOffset;
    public int payloadSize;
    public long pts;
    public long dts;
    public long duration;
    public static final int PES_HEADER_SIZE = 6;
    public static final int PRIVATE_1 = 0x1bd;
    public static final int VIDEO_MAX = 0x1EF;

    public ByteBuffer payload() {
        payload.limit(payloadOffset + payloadSize);
        payload.position(payloadOffset);
        return payload;
    }

    //TODO: move this method to a utility class
    public static boolean isVideo(int streamId) {
        streamId &= 0xff;
        return streamId >= 0xe0 && streamId <= 0xef;
    }

    public static boolean isAudio(int streamId) {
        streamId &= 0xff;
        return streamId >= 0xbf && streamId <= 0xdf;
    }

    static void mpeg1Pes(int b0, int len, int streamId, ByteBuffer is) {
        int c = b0;
        while (c == 0xff) {
            c = is.get() & 0xff;
        }

        if ((c & 0xc0) == 0x40) {
            is.get();
            c = is.get() & 0xff;
        }
        long pts = -1, dts = -1;
        if ((c & 0xf0) == 0x20) {
            pts = readTs(is, c);
        } else if ((c & 0xf0) == 0x30) {
            pts = readTs(is, c);
            dts = readTs(is);
        } else {
            if (c != 0x0f)
                throw new RuntimeException("Invalid data");
        }
    }

    static void mpeg2Pes(int b0, int len, int streamId, ByteBuffer is) {
        int flags1 = b0;
        int flags2 = is.get() & 0xff;
        int header_len = is.get() & 0xff;

        long pts = -1, dts = -1;
        if ((flags2 & 0xc0) == 0x80) {
            pts = readTs(is);
            PESPacket.skip(is, header_len - 5);
        } else if ((flags2 & 0xc0) == 0xc0) {
            pts = readTs(is);
            dts = readTs(is);
            PESPacket.skip(is, header_len - 10);
        } else {
            PESPacket.skip(is, header_len);
        }
    }

    static void skipPESHeader(ByteBuffer iss) {
        int streamId = iss.getInt() & 0xff;
        int len = iss.getShort();
        if (streamId != 0xbf) {
            int b0 = iss.get() & 0xff;
            if ((b0 & 0xc0) == 0x80)
                PESPacket.mpeg2Pes(b0, len, streamId, iss);
            else
                PESPacket.mpeg1Pes(b0, len, streamId, iss);
        }
    }

    public static final boolean psMarker(int marker) {
        return marker >= PESPacket.PRIVATE_1 && marker <= PESPacket.VIDEO_MAX;
    }

    public static int nextPsMarkerPosition(ByteBuffer buf) {
        for (int i = buf.position() + 4; i < buf.limit() - 4; i++) {
            int int1 = buf.getInt(i);
            if (PESPacket.psMarker(int1)) {
                return i;
            }
        }
        return buf.limit();
    }

    public PESPacket(ByteBuffer data, long pts, int streamId, int length, long pos, long dts) {
        this.payload = data;
        this.pts = pts;
        this.streamId = streamId;
        this.payloadSize = length;
        this.streamOffset = pos;
        this.dts = dts;
    }

    public static PESPacket readPESHeader(ByteBuffer bb, long pos) {
        int streamId = bb.getInt();
        if ((streamId >> 8) != 1) {
            return null;
            //            continue; // non PES payload, probably PSI
        }
        while (bb.hasRemaining() && !(streamId >= 0x1bf && streamId < 0x1ef)) {
            streamId <<= 8;
            streamId |= bb.get() & 0xff;
        }
        if (streamId >= 0x1bf && streamId < 0x1ef) {
            int len = bb.getShort() & 0xffff;
            int b0 = bb.get() & 0xff;
            if ((b0 & 0xc0) == 0x80)
                return PESPacket.mpeg2Pes(b0, len, streamId, bb, pos);
            else
                return PESPacket.mpeg1Pes(b0, len, streamId, bb, pos);
        }
        return new PESPacket(null, -1, streamId, -1, pos, -1);
    }

    public static PESPacket _readPESHeader(ByteBuffer iss, long pos) {
        int streamId = iss.getInt() & 0xff;
        int len = iss.getShort();
        if (streamId != 0xbf) {
            int b0 = iss.get() & 0xff;
            if ((b0 & 0xc0) == 0x80)
                return PESPacket.mpeg2Pes(b0, len, streamId, iss, pos);
            else
                return PESPacket.mpeg1Pes(b0, len, streamId, iss, pos);
        }
        return new PESPacket(null, -1, streamId, len, pos, -1);
    }

    public static int skip(ByteBuffer buffer, int count) {
        int toSkip = Math.min(buffer.remaining(), count);
        buffer.position(buffer.position() + toSkip);
        return toSkip;
    }

    public static PESPacket mpeg2Pes(int b0, int len, int streamId, ByteBuffer is, long pos) {
        int flags1 = b0;
        int flags2 = is.get() & 0xff;
        int header_len = is.get() & 0xff;

        long pts = -1, dts = -1;
        if ((flags2 & 0xc0) == 0x80) {
            pts = PESPacket.readTs(is);
            skip(is, header_len - 5);
        } else if ((flags2 & 0xc0) == 0xc0) {
            pts = PESPacket.readTs(is);
            dts = PESPacket.readTs(is);
            skip(is, header_len - 10);
        } else {
            skip(is, header_len);
        }
        return new PESPacket(null, pts, streamId, len, pos, dts);
    }

    public static long readTs(ByteBuffer is) {
        return (((long) is.get() & 0x0e) << 29) | ((is.get() & 0xff) << 22) | (((is.get() & 0xff) >> 1) << 15)
                | ((is.get() & 0xff) << 7) | ((is.get() & 0xff) >> 1);
    }

    public static PESPacket mpeg1Pes(int b0, int len, int streamId, ByteBuffer is, long pos) {
        int c = b0;
        while (c == 0xff) {
            c = is.get() & 0xff;
        }

        if ((c & 0xc0) == 0x40) {
            is.get();
            c = is.get() & 0xff;
        }
        long pts = -1, dts = -1;
        if ((c & 0xf0) == 0x20) {
            pts = PESPacket.readTs(is, c);
        } else if ((c & 0xf0) == 0x30) {
            pts = PESPacket.readTs(is, c);
            dts = readTs(is);
        } else {
            if (c != 0x0f)
                throw new RuntimeException("Invalid data");
        }

        return new PESPacket(null, pts, streamId, len, pos, dts);
    }

    public static long readTs(ByteBuffer is, int c) {
        return (((long) c & 0x0e) << 29) | ((is.get() & 0xff) << 22) | (((is.get() & 0xff) >> 1) << 15)
                | ((is.get() & 0xff) << 7) | ((is.get() & 0xff) >> 1);
    }
}