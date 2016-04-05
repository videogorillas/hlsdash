package com.vg.live.worker;

import js.nio.ByteBuffer;

import com.vg.util.MappedByteBufferPool;

public class FramePool {
    static MappedByteBufferPool pool = new MappedByteBufferPool(1024);

    public static ByteBuffer acquire(int size) {
        ByteBuffer acquire = pool.acquire(size, false);
        acquire.setPosition(0);
        acquire.setLimit(size);
        return acquire;
    }

    public static void release(ByteBuffer buf) {
        pool.release(buf);
    }

    public static ByteBuffer copy(ByteBuffer payload) {
        ByteBuffer acquire = FramePool.acquire(payload.remaining());
        acquire.putBuf(payload);
        return acquire;
    }

}
