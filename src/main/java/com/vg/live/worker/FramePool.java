package com.vg.live.worker;

import java.nio.ByteBuffer;

import com.vg.util.MappedByteBufferPool;

public class FramePool {
    static MappedByteBufferPool pool = new MappedByteBufferPool();

    public static ByteBuffer acquire(int size) {
        ByteBuffer acquire = pool.acquire(size, false);
        acquire.position(0);
        acquire.limit(size);
        return acquire;
    }

    public static void release(ByteBuffer buf) {
        pool.release(buf);
    }

    public static ByteBuffer copy(ByteBuffer payload) {
        ByteBuffer acquire = FramePool.acquire(payload.remaining());
        acquire.put(payload);
        return acquire;
    }

}
