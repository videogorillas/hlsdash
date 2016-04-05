package com.vg.util;

import js.nio.ByteBuffer;

import org.jcodec.common.io.BitReader;
import org.jcodec.common.io.BitWriter;

public class ADTSHeader {
    public static final int HEADER_SIZE = 7;
    public final static int sample_rates[] = new int[] { 96000, 88200, 64000, 48000, 44100, 32000, 24000, 22050, 16000,
            12000, 11025, 8000, 7350 };

    private int id;
    private int layer;
    public int crc_abs;
    private int aot;
    private int sr;
    private int pb;
    private int ch;
    private int origCopy;
    private int home;
    private int copy;
    private int copyStart;
    private int size;
    private int buffer;
    private int rdb;

    public static ADTSHeader read(ByteBuffer data) {
        ByteBuffer dup = data.duplicate();
        BitReader br = BitReader.createBitReader(dup);
        // int size, rdb, ch, sr;
        // int aot, crc_abs;

        if (br.readNBit(12) != 0xfff) {
            return null;
        }

        ADTSHeader hdr = new ADTSHeader();

        hdr.id = br.read1Bit(); /* id */
        hdr.layer = br.readNBit(2); /* layer */
        hdr.crc_abs = br.read1Bit(); /* protection_absent */
        hdr.aot = br.readNBit(2); /* profile_objecttype */
        hdr.sr = br.readNBit(4); /* sample_frequency_index */
        hdr.pb = br.read1Bit(); /* private_bit */
        hdr.ch = br.readNBit(3); /* channel_configuration */

        hdr.origCopy = br.read1Bit(); /* original/copy */
        hdr.home = br.read1Bit(); /* home */

        /* adts_variable_header */
        hdr.copy = br.read1Bit(); /* copyright_identification_bit */
        hdr.copyStart = br.read1Bit(); /* copyright_identification_start */
        hdr.size = br.readNBit(13); /* aac_frame_length */
        if (hdr.size < HEADER_SIZE)
            return null;

        hdr.buffer = br.readNBit(11); /* adts_buffer_fullness */
        hdr.rdb = br.readNBit(2); /* number_of_raw_data_blocks_in_frame */
        br.stop();

        data.setPosition(dup.position());

        return hdr;
    }

    public int getSize() {
        return size;
    }

    public static ByteBuffer write(ADTSHeader hdr, ByteBuffer buf, int frameSize) {
        ByteBuffer data = buf.duplicate();
        BitWriter br = new BitWriter(data);
        // int size, rdb, ch, sr;
        // int aot, crc_abs;

        br.writeNBit(0xfff, 12);

        br.write1Bit(hdr.id); /* id */
        br.writeNBit(hdr.layer, 2); /* layer */
        br.write1Bit(hdr.crc_abs); /* protection_absent */
        br.writeNBit(hdr.aot, 2); /* profile_objecttype */
        br.writeNBit(hdr.sr, 4); /* sample_frequency_index */
        br.write1Bit(hdr.pb); /* private_bit */
        br.writeNBit(hdr.ch, 3); /* channel_configuration */

        br.write1Bit(hdr.origCopy); /* original/copy */
        br.write1Bit(hdr.home); /* home */

        /* adts_variable_header */
        br.write1Bit(hdr.copy); /* copyright_identification_bit */
        br.write1Bit(hdr.copyStart); /* copyright_identification_start */
        br.writeNBit(frameSize + HEADER_SIZE, 13); /* aac_frame_length */

        br.writeNBit(hdr.buffer, 11); /* adts_buffer_fullness */
        br.writeNBit(hdr.rdb, 2); /* number_of_raw_data_blocks_in_frame */
        br.flush();

        data.flip();
        return data;
    }

    public int getObjectType() {
        return aot;
    }

    public int getSamplingIndex() {
        return sr;
    }

    public int getChanConfig() {
        return ch;
    }

    public int getSampleRate() {
        return sample_rates[sr];
    }

    public static ByteBuffer decoderSpecific(ADTSHeader hdr) {
        ByteBuffer si = ByteBuffer.allocate(2);
        BitWriter wr = new BitWriter(si);
        wr.writeNBit(hdr.getObjectType() + 1, 5);
        wr.writeNBit(hdr.getSamplingIndex(), 4);
        wr.writeNBit(hdr.getChanConfig(), 4);
        wr.flush();
        si.clear();
        return si;
    }

    public int getDataSize() {
        return size - HEADER_SIZE;
    }
}
