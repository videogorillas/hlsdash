//
//  ========================================================================
//  Copyright (c) 1995-2015 Mort Bay Consulting Pty. Ltd.
//  ------------------------------------------------------------------------
//  All rights reserved. This program and the accompanying materials
//  are made available under the terms of the Eclipse Public License v1.0
//  and Apache License v2.0 which accompanies this distribution.
//
//      The Eclipse Public License is available at
//      http://www.eclipse.org/legal/epl-v10.html
//
//      The Apache License v2.0 is available at
//      http://www.opensource.org/licenses/apache2.0.php
//
//  You may elect to redistribute this code under either of these licenses.
//  ========================================================================
//

package com.vg.util;

import js.nio.ByteBuffer;
import js.util.Queue;
import js.util.concurrent.ConcurrentHashMap;
import js.util.concurrent.ConcurrentLinkedQueue;
import js.util.concurrent.ConcurrentMap;

public class MappedByteBufferPool {
    private final ConcurrentMap<Integer, Queue<ByteBuffer>> directBuffers;
    private final ConcurrentMap<Integer, Queue<ByteBuffer>> heapBuffers;
    private final int factor;

    public MappedByteBufferPool(int factor) {
        this.factor = factor;
        this.directBuffers = new ConcurrentHashMap<>();
        this.heapBuffers = new ConcurrentHashMap<>();
    }

    public ByteBuffer acquire(int size, boolean direct) {
        int bucket = bucketFor(size);
        ConcurrentMap<Integer, Queue<ByteBuffer>> buffers = buffersFor(direct);

        ByteBuffer result = null;
        Queue<ByteBuffer> byteBuffers = buffers.get(bucket);
        if (byteBuffers != null)
            result = byteBuffers.poll();

        if (result == null) {
            int capacity = bucket * factor;
            result = newByteBuffer(capacity, direct);
        }

        BufferUtil.clear(result);
        if(result.capacity() < size) {
            throw new RuntimeException("FramePool BUG");
        }
        return result;
    }

    protected ByteBuffer newByteBuffer(int capacity, boolean direct) {
        return direct ? BufferUtil.allocateDirect(capacity) : BufferUtil.allocate(capacity);
    }

    public void release(ByteBuffer buffer) {
        if (buffer == null)
            return; // nothing to do

        // validate that this buffer is from this pool
//        assert ((buffer.capacity() % factor) == 0);

        int bucket = bucketFor(buffer.capacity());
        ConcurrentMap<Integer, Queue<ByteBuffer>> buffers = buffersFor(buffer.isDirect());

        // Avoid to create a new queue every time, just to be discarded immediately
        Queue<ByteBuffer> byteBuffers = buffers.get(bucket);
        if (byteBuffers == null) {
            byteBuffers = new ConcurrentLinkedQueue<>();
            Queue<ByteBuffer> existing = buffers.putIfAbsent(bucket, byteBuffers);
            if (existing != null)
                byteBuffers = existing;
        }

        BufferUtil.clear(buffer);
        byteBuffers.offer(buffer);
    }

    public void clear() {
        directBuffers.clear();
        heapBuffers.clear();
    }

    private int bucketFor(int size) {
        int bucket = size / factor;
        if (size % factor > 0)
            ++bucket;
        return bucket;
    }

    // Package local for testing
    ConcurrentMap<Integer, Queue<ByteBuffer>> buffersFor(boolean direct) {
        return direct ? directBuffers : heapBuffers;
    }

}
