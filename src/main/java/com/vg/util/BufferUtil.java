package com.vg.util;

import js.nio.ByteBuffer;

public class BufferUtil {
    /**
     * Clear the buffer to be empty in flush mode. The position and limit are
     * set to 0;
     * 
     * @param buffer
     *            The buffer to clear.
     */
    public static void clear(ByteBuffer buffer) {
        if (buffer != null) {
            buffer.setPosition(0);
            buffer.setLimit(0);
        }
    }

    /**
     * Allocate ByteBuffer in flush mode. The position and limit will both be
     * zero, indicating that the buffer is empty and must be flipped before any
     * data is put to it.
     * 
     * @param capacity
     *            capacity of the allocated ByteBuffer
     * @return Buffer
     */
    public static ByteBuffer allocate(int capacity) {
        ByteBuffer buf = ByteBuffer.allocate(capacity);
        buf.setLimit(0);
        return buf;
    }

    public static ByteBuffer allocateDirect(int capacity) {
        ByteBuffer buf = ByteBuffer.allocateDirect(capacity);
        buf.setLimit(0);
        return buf;
    }

    public static ByteBuffer copy(ByteBuffer buf) {
        buf.mark();
        ByteBuffer allocate = ByteBuffer.allocate(buf.remaining());
        allocate.putBuf(buf);
        allocate.clear();
        buf.reset();
        return allocate;
    }

}
