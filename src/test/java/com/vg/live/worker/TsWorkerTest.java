package com.vg.live.worker;

import static com.vg.live.video.MP4Segment.createMP4Segment;
import static com.vg.live.video.TSPkt.PAT_PID;
import static com.vg.util.ADTSHeader.decoderSpecific;
import static com.vg.util.DashUtil.dashinitVideo;
import static js.util.Arrays.asList;
import static org.jcodec.codecs.h264.mp4.AvcCBox.createAvcCBox;
import static org.jcodec.codecs.mpeg4.mp4.EsdsBox.createEsdsBox;
import static org.jcodec.containers.mp4.MP4Packet.createMP4Packet;
import static org.jcodec.containers.mp4.boxes.FileTypeBox.createFileTypeBox;
import static org.stjs.javascript.Global.console;
import static org.stjs.javascript.Global.window;
import static org.stjs.javascript.JSCollections.$array;
import static org.stjs.javascript.JSCollections.$map;
import static org.stjs.javascript.JSObjectAdapter.$get;

import org.jcodec.codecs.h264.H264Utils;
import org.jcodec.codecs.h264.mp4.AvcCBox;
import org.jcodec.codecs.mpeg4.mp4.EsdsBox;
import org.jcodec.common.io.ByteBufferSeekableByteChannel;
import org.jcodec.common.io.FileChannelWrapper;
import org.jcodec.common.io.NIOUtils;
import org.jcodec.common.io.SeekableByteChannel;
import org.jcodec.containers.mp4.MP4Packet;
import org.jcodec.containers.mp4.TrackType;
import org.jcodec.containers.mp4.boxes.Box;
import org.jcodec.containers.mp4.boxes.FileTypeBox;
import org.jcodec.containers.mp4.boxes.Header;
import org.jcodec.containers.mp4.boxes.MovieBox;
import org.jcodec.containers.mp4.boxes.SampleEntry;
import org.jcodec.containers.mp4.muxer.AbstractMP4MuxerTrack;
import org.jcodec.containers.mp4.muxer.FramesMP4MuxerTrack;
import org.jcodec.containers.mp4.muxer.MP4Muxer;
import org.jcodec.containers.mps.psi.PATSection;
import org.jcodec.containers.mps.psi.PMTSection;
import org.junit.Test;
import org.stjs.javascript.JSObjectAdapter;
import org.stjs.javascript.dom.Video;
import org.stjs.javascript.dom.media.URL;
import org.stjs.javascript.file.Blob;
import org.stjs.javascript.typed.Int8Array;

import com.vg.js.bridge.Rx.Observable;
import com.vg.live.video.AVFrame;
import com.vg.live.video.MP4Segment;
import com.vg.live.video.TSPkt;
import com.vg.util.ADTSHeader;
import com.vg.util.DashUtil;
import com.vg.util.MutableBoolean;
import com.vg.util.SimpleAjax;

import js.io.File;
import js.io.FileNotFoundException;
import js.io.IOException;
import js.lang.System;
import js.nio.ByteBuffer;
import js.util.List;

public class TsWorkerTest {

    static MP4Packet mp4(AVFrame f) {
        MP4Packet pkt = createMP4Packet(f.data(), f.pts, 90000, Math.max(0, f.duration), 0, f.isIFrame(), null, 0, f.pts, 0);
        return pkt;
    }

    public void testPipeline1() throws Exception {
        File file2 = new File("tmp/out.mp4");
        FileChannelWrapper w = NIOUtils.writableChannel(file2);
        MP4Muxer muxer = MP4Muxer.createMP4MuxerToChannel(w);

        FramesMP4MuxerTrack vTrack = muxer.addTrack(TrackType.VIDEO, 90000);

        AvcCBox avcc = createAvcCBox(0, 0, 0, 4, asList(ByteBuffer.allocate(42)), asList(ByteBuffer.allocate(4)));
        SampleEntry se = H264Utils.createMOVSampleEntryFromAvcC(avcc);
        vTrack.addSampleEntry(se);
        muxer.writeHeader();
        w.close();

    }

    public void testPipelineInBrowser() throws Exception {
        SimpleAjax.getArrayBuffer("testdata/zoomoo/76fab9ea8d4dc3941bd0872b7bef2c9c_31321.ts")
                  .subscribe(arrayBuf -> {
                      ByteBuffer buf = ByteBuffer.wrap(new Int8Array(arrayBuf));
                      Int8Array outArr = new Int8Array(arrayBuf.byteLength + 4242);
                      JSObjectAdapter.$put(window, "OUTARR", outArr);
                      ByteBufferSeekableByteChannel out = new ByteBufferSeekableByteChannel(ByteBuffer.wrap(outArr));
                      long start = System.currentTimeMillis();

                      Observable<MP4Muxer> _runPipeline = _runPipeline(buf, out);
                      _runPipeline.subscribe(x -> {
                          console.log("next");
                      }, err -> {
                          console.error(err);
                      }, () -> {
                          long time = System.currentTimeMillis() - start;
                          Video video = (Video) window.document.createElement("video");
                          String url = URL.createObjectURL(new Blob($array(outArr), $map("type", "video/mp4")));
                          video.src = url;
                          video.controls = true;
                          window.document.body.appendChild(video);
                          console.log("done in " + time + " msec");
                      });
                  });
    }

    @Test
    public void testPipeline() throws Exception {
        //        File file = new File("testdata/apple/06402.ts");
        File file = new File("testdata/zoomoo/76fab9ea8d4dc3941bd0872b7bef2c9c_31321.ts");
        File outputDir = emptyDir(new File("tmp/hlsjs"));
        ByteBuffer buf = readFileToByteBuffer(file);
        File file2 = new File(outputDir, "out.mp4");
        FileChannelWrapper w = NIOUtils.writableChannel(file2);
        runPipeline(buf, w);
        w.close();
    }

    @Test
    public void testDashPipeline() throws Exception {
        //        File file = new File("testdata/apple/06402.ts");
        File file = new File("testdata/zoomoo/76fab9ea8d4dc3941bd0872b7bef2c9c_31321.ts");
        File outputDir = emptyDir(new File("tmp/hlsjs"));
        ByteBuffer inputBuf = readFileToByteBuffer(file);
        FileChannelWrapper w = NIOUtils.writableChannel(new File(outputDir, "init.m4s"));
        FileChannelWrapper output = NIOUtils.writableChannel(new File(outputDir, "chunk.m4s"));

        Observable<AVFrame> frames = M4sWorker.frames(inputBuf);

        MutableBoolean hasInit = new MutableBoolean(false);
        long timescale = 90000;
        long startTime = 0;
        int sequenceNumber = 1;

        MP4Segment segment = createMP4Segment(timescale, startTime, sequenceNumber);
        ByteBuffer data = FramePool.acquire(inputBuf.capacity());
        frames.filter(f -> f.isVideo())
              .doOnNext(frame -> {
                  if (!hasInit.value) {
                      hasInit.value = true;
                      MovieBox moov = dashinitVideo(frame);
                      FileTypeBox ftyp = createFileTypeBox("iso5", 1, asList("avc1", "iso5", "dash"));
                      ByteBuffer _buf = FramePool.acquire(2048);
                      ftyp.write(_buf);
                      moov.write(_buf);
                      _buf.flip();
                      console.log("_buf", _buf.toString());
                      while (_buf.hasRemaining()) {
                          w.write(_buf);
                          console.log("_buf", _buf.toString());
                      }
                      FramePool.release(_buf);
                      w.close();
                  }
              })
              .reduce((m4s, frame) -> {
                  int compOffset = frame.dts != null ? (int) (frame.pts - frame.dts) : 0;
                  console.log(frame.pts, frame.dts, compOffset);
                  m4s.sidx.references[0].subsegment_duration += frame.duration;
                  m4s.trackRun.sampleCompositionOffset.add(compOffset);
                  m4s.trackRun.sampleDuration.add((int) frame.duration);
                  int flags = frame.isIFrame() ? 0x02000000 : 0x01010000;
                  m4s.trackRun.sampleFlags.add(flags);
                  m4s.trackRun.sampleSize.add(frame.dataSize);
                  data.putBuf(frame.data());
                  FramePool.release(frame._data);
                  return m4s;
              }, segment)
              .doOnNext(m4s -> {
                  m4s.trun = MP4Segment.createTrunBox(m4s.trackRun);
                  m4s.moof.getTracks()[0].add(m4s.trun);
              })
              .subscribe(m4s -> {
                  data.flip();
                  long dataSize = data.remaining();
                  long mdatSize = dataSize + 8;
                  Header mdat = Header.createHeader("mdat", mdatSize);
                  int frameCount = segment.trackRun.sampleSize.size();
                  int headerSize = (frameCount * 16) * 2;
                  console.log("headerSize", headerSize);
                  ByteBuffer buf = FramePool.acquire(headerSize);
                  m4s.styp.write(buf);
                  m4s.sidx.write(buf);
                  int sidxEndPosition = buf.position();
                  m4s.moof.write(buf);
                  mdat.write(buf);

                  int videoDataOffset = buf.position();
                  m4s.trun.setDataOffset(videoDataOffset - 68);
                  m4s.sidx.references[0].referenced_size = videoDataOffset + dataSize - sidxEndPosition;

                  buf.clear();
                  m4s.styp.write(buf);
                  m4s.sidx.write(buf);
                  m4s.moof.write(buf);
                  mdat.write(buf);
                  buf.flip();
                  while (buf.hasRemaining()) {
                      output.write(buf);
                  }
                  while (data.hasRemaining()) {
                      output.write(data);
                  }
                  FramePool.release(buf);
              }, err -> {
                  console.log("err", err);
                  console.log("stack", JSObjectAdapter.$get(err, "stack"));
              }, () -> {

                  console.log("done");
                  FramePool.release(data);
              });
    }

    /**
     * 
     * @param srcTv
     *            audio time value
     * @param srcTs
     *            audio time scale
     * @param dstTs
     *            video time scale
     * @return
     */
    public static long convertTimescale(long srcTv, long srcTs, long dstTs) {
        return srcTv * dstTs / srcTs;
    }

    private Observable<MP4Muxer> _runPipeline(ByteBuffer bufIn, SeekableByteChannel out) {

        Observable<AVFrame> frames = M4sWorker.frames(bufIn);

        MP4Muxer muxer = MP4Muxer.createMP4MuxerToChannel(out);
        Observable<MP4Muxer> reduce = frames.reduce((m, f) -> {
            //console.log("frame", f.toString());
            try {
                if (f.isVideo()) {
                    FramesMP4MuxerTrack vTrack = (FramesMP4MuxerTrack) muxer.getVideoTrack();
                    if (vTrack == null) {
                        vTrack = addVideoTrack(m, f);
                    }
                    vTrack.addFrame(mp4(f));
                } else if (f.adtsHeader != null) {
                    List<AbstractMP4MuxerTrack> audioTracks = muxer.getAudioTracks();
                    FramesMP4MuxerTrack aTrack = (FramesMP4MuxerTrack) (audioTracks.isEmpty() ? null
                            : audioTracks.get(0));
                    if (aTrack == null) {
                        aTrack = addAudioTrack(m, f);
                    }
                    aTrack.addFrame(mp4(f));
                }
                FramePool.release(f._data);
            } catch (Exception e) {
                console.error(e);
                throw new RuntimeException(e);
            }
            return m;
        }, muxer);
        reduce = reduce.doOnNext(m -> {
            try {
                m.writeHeader();
            } catch (Exception e) {
                console.log("e", e);
                console.log("stack", $get(e, "stack"));
                throw new RuntimeException(e);
            }
        });
        return reduce;
    }

    private void runPipeline(ByteBuffer bufIn, SeekableByteChannel out) throws Exception {
        long start = System.currentTimeMillis();
        Observable<MP4Muxer> reduce = _runPipeline(bufIn, out);
        reduce.subscribe(x -> {
            System.out.println(x);
        }, err -> {
            console.log("err", err);
            console.log("stack", JSObjectAdapter.$get(err, "stack"));
        }, () -> {
            long time = System.currentTimeMillis() - start;
            console.log("done in " + time + "msec");
            console.log("FramePool", FramePool.pool);
            //            console.log("FramePool", FramePool.pool.toString());
        });
        //        console.log("done");
        //        System.exit(1);

    }

    private ByteBuffer readFileToByteBuffer(File file) throws FileNotFoundException, IOException {
        FileChannelWrapper channel = NIOUtils.readableChannel(file);
        int size = (int) channel.size();
        ByteBuffer buf = ByteBuffer.allocate(size);
        while (buf.hasRemaining()) {
            channel.read(buf);
        }
        buf.clear();
        return buf;
    }

    private static File emptyDir(File dir) {
        dir.mkdirs();
        File[] listFiles = dir.listFiles();
        if (listFiles != null) {
            for (int i = 0; i < listFiles.length; i++) {
                File f = listFiles[i];
                System.out.println("rm " + f);
                f.$delete();
            }
        }
        return dir;
    }

    private FramesMP4MuxerTrack addVideoTrack(MP4Muxer muxer, AVFrame videoFrame) {
        FramesMP4MuxerTrack vTrack = muxer.addTrack(TrackType.VIDEO, 90000);
        SampleEntry sampleEntry = DashUtil.videoSampleEntry(videoFrame);
        vTrack.addSampleEntry(sampleEntry);
        return vTrack;
    }

    private FramesMP4MuxerTrack addAudioTrack(MP4Muxer muxer, AVFrame audioFrame) {
        ADTSHeader hdr = audioFrame.adtsHeader;
        EsdsBox esds = createEsdsBox(decoderSpecific(hdr), (hdr.getObjectType() + 1) << 5, 0, 256 * 1024, 128
                * 1024, 2);
        FramesMP4MuxerTrack aTrack = muxer.addCompressedAudioTrack("mp4a", 90000, hdr.getChanConfig(), hdr.getSampleRate(), 0, new Box[] {
                esds });
        return aTrack;
    }

}
