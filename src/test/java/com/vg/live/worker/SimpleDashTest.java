package com.vg.live.worker;

import com.vg.js.bridge.Rx;
import com.vg.js.bridge.Rx.Observable;
import com.vg.js.bridge.Rx.Subject;
import com.vg.live.video.MP4Segment;
import com.vg.util.MutableLong;
import com.vg.util.SimpleAjax;
import js.nio.ByteBuffer;
import org.junit.Test;
import org.stjs.javascript.Array;
import org.stjs.javascript.dom.Anchor;
import org.stjs.javascript.dom.DOMEvent;
import org.stjs.javascript.dom.Element;
import org.stjs.javascript.dom.Video;
import org.stjs.javascript.dom.media.MediaSource;
import org.stjs.javascript.dom.media.SourceBuffer;
import org.stjs.javascript.dom.media.URL;
import org.stjs.javascript.file.Blob;
import org.stjs.javascript.typed.ArrayBuffer;
import org.stjs.javascript.typed.DataView;
import org.stjs.javascript.typed.Int8Array;

import static org.stjs.javascript.Global.console;
import static org.stjs.javascript.Global.window;
import static org.stjs.javascript.JSCollections.$array;
import static org.stjs.javascript.JSCollections.$map;
import static org.stjs.javascript.JSObjectAdapter.$put;

public class SimpleDashTest {
    private static final String SOURCEOPEN = "sourceopen";
    private static final String READYSTATE_CLOSED = "closed";
    private static final String READYSTATE_OPEN = "open";
    private static final String READYSTATE_ENDED = "ended";
    private static final String BUFFER_ERROR = "error";
    private static final String BUFFER_UPDATEEND = "updateend";
    private SourceBuffer videoBuffer;
    private MediaSource mediaSource;

    @Test
    public void testSeries() throws Exception {
        MutableLong mseq = new MutableLong(0);
        MutableLong duration = new MutableLong(0);

        Array<String> urls = $array(
                "testdata/zoomoo/4760a09c958138d875af68fd53f4a9a8_80917.ts",
                "testdata/zoomoo/4760a09c958138d875af68fd53f4a9a8_80918.ts",
                "testdata/zoomoo/4760a09c958138d875af68fd53f4a9a8_80919.ts",
                "testdata/zoomoo/4760a09c958138d875af68fd53f4a9a8_80920.ts",
                "testdata/zoomoo/4760a09c958138d875af68fd53f4a9a8_80921.ts",
                "testdata/zoomoo/4760a09c958138d875af68fd53f4a9a8_80922.ts");

        Subject<String> urlrx = new Subject<>();
        urlrx
                .flatMap(SimpleAjax::getArrayBuffer)
                .flatMap(ts -> {
                    ByteBuffer inputBuf = ByteBuffer.wrap(new Int8Array(ts));
                    Observable<MP4Segment> m4s = TsWorkerTest.m4s(inputBuf, duration.longValue(), (int) mseq.longValue() + 1);

                    if (mseq.longValue() == 0) {
                        return m4s.doOnNext(m -> {
                            ByteBuffer init = m.init;
                            console.log("init", init);
                            DataView dataView = dataView(init);
                            console.log("dataView", dataView, dataView.buffer, dataView.byteOffset, dataView.byteLength);
                            videoBuffer.appendBuffer(dataView);
                            console.log("init segment added");
                        });
                    } else {
                        return m4s;
                    }
                })
                .doOnNext(m4s -> {
                    Blob blob = new Blob($array(dataView(m4s.init), dataView(m4s.data)), $map("type", "video/mpeg"));
                    Anchor a = (Anchor) window.document.createElement("a");
                    a.href = URL.createObjectURL(blob);
                    a.target = "_blank";
                    a.download = "generated.mp4";
                    a.innerHTML = "download mp4";
                    window.document.body.appendChild(a);
                })
                .flatMap(m4s -> {
                    console.log("videoBuffer.updating", videoBuffer.updating);
                    Observable<Boolean> bufferReady = Observable.just(true);
                    if (videoBuffer.updating) {
                        bufferReady = Rx.Observable.fromEvent(videoBuffer, BUFFER_UPDATEEND).take(1).map(ee -> true);
                    }
                    return bufferReady.doOnNext(e -> {
                        console.log("add data", mseq.longValue(), duration.longValue());
                        videoBuffer.appendBuffer(dataView(m4s.data));

                        for (int i = 0; i < m4s.trun.getSampleCount(); i++) {
                            duration.add(m4s.trun.getSampleDuration(i));
                        }
                    });
                })
                .doOnNext(b -> {
                    mseq.add(1);
//                    if (mseq.longValue() < urls.$length()) {
//                        urlrx.onNext(urls.$get(mseq.longValue()));
//                    }
                })
                .subscribe();

        String mimeType = "video/mp4";
        String vcodecs = "avc1.4d4028";
        mediaSource = new MediaSource();
        Video video = (Video) window.document.createElement("video");
        video.setAttribute("controls", "true");
        window.document.body.appendChild(video);
        video.src = URL.createObjectURL(mediaSource);
        Observable<DOMEvent> open = Rx.Observable.fromEvent(mediaSource, SOURCEOPEN);
        open = open.doOnNext(e -> {
            console.log("source open");
            videoBuffer = mediaSource.addSourceBuffer(mimeType + "; codecs=\"" + vcodecs + "\"");
            videoBuffer.addEventListener(BUFFER_ERROR, (_e) -> {
                console.log("videoBuffer error", _e);
            });
            $put(window, "mediaSource", mediaSource);
            $put(window, "videoBuffer", videoBuffer);
        });
        open.subscribe(ee -> {
            urlrx.onNext(urls.$get(0));
        });
    }

    @Test
    public void testName() throws Exception {
        String mimeType = "video/mp4";
        String vcodecs = "avc1.4d4028";
        mediaSource = new MediaSource();
        Video video = (Video) window.document.createElement("video");
        video.setAttribute("controls", "true");
        window.document.body.appendChild(video);
        video.src = URL.createObjectURL(mediaSource);
        MutableLong _mseq = new MutableLong(1);
        Observable<DOMEvent> open = Rx.Observable.fromEvent(mediaSource, SOURCEOPEN);
        open = open.doOnNext(e -> {
            videoBuffer = mediaSource.addSourceBuffer(mimeType + "; codecs=\"" + vcodecs + "\"");
            videoBuffer.addEventListener(BUFFER_ERROR, (_e) -> {
                console.log("videoBuffer error", _e);
            });
        });

        Observable<ArrayBuffer> downloadTs = open.flatMap(e -> {
            return SimpleAjax.getArrayBuffer("testdata/zoomoo/76fab9ea8d4dc3941bd0872b7bef2c9c_31321.ts");
        });

        Observable<DOMEvent> updateEndRx = downloadTs.flatMap(ts -> {
            ByteBuffer inputBuf = ByteBuffer.wrap(new Int8Array(ts));
            Observable<MP4Segment> m4s = TsWorkerTest.m4s(inputBuf, 0, 1);
            m4s = m4s.doOnNext(m -> {
                ByteBuffer init = m.init;
                console.log("init", init);
                DataView dataView = dataView(init);
                console.log("dataView", dataView, dataView.buffer, dataView.byteOffset, dataView.byteLength);
                videoBuffer.appendBuffer(dataView);
                console.log("init segment added");
            });
            Observable<DOMEvent> flatMap = m4s.flatMap(m -> {
                return Rx.Observable.fromEvent(videoBuffer, BUFFER_UPDATEEND)
                                    .take(1)
                                    .doOnNext(e -> {
                                        console.log("add data");
                                        videoBuffer.appendBuffer(dataView(m.data));
                                    });
            });
            return flatMap;
        });
        updateEndRx.subscribe(x -> {
            console.log("event", x);
        });

    }

    private static DataView dataView(ByteBuffer bb) {
        Int8Array array = (Int8Array) (Object) bb.array();
        DataView view = new DataView(array.buffer, bb.position(), bb.remaining());
        return view;
    }

    @Test
    public void testReadFromDisk() throws Exception {
        String mimeType = "video/mp4";
        String vcodecs = "avc1.4d4028";
        mediaSource = new MediaSource();
        Video video = (Video) window.document.createElement("video");
        video.setAttribute("controls", "true");
        window.document.body.appendChild(video);
        video.src = URL.createObjectURL(mediaSource);
        MutableLong _mseq = new MutableLong(1);
        Rx.Observable.fromEvent(mediaSource, SOURCEOPEN)
                     .doOnNext(e -> {
                         videoBuffer = mediaSource.addSourceBuffer(mimeType + "; codecs=\"" + vcodecs + "\"");
                     })
                     .flatMap(e -> {
//                         return SimpleAjax.getArrayBuffer("tmp/hlsjs/init-stream0.m4s");
                         return SimpleAjax.getArrayBuffer("tmp/hlsjs/init.m4s");
                     })
                     .doOnNext(arrayBuffer -> {
                         videoBuffer.appendBuffer(arrayBuffer);
                         console.log("init segment added");
                     })
                     .flatMap(e -> {
                         return Rx.Observable.fromEvent(videoBuffer, BUFFER_UPDATEEND);
                     })
                     .flatMap(e -> {
                         long mseq = _mseq.longValue();
                         if (mseq < 3) {
                             _mseq.add(1);
                             String url = "tmp/hlsjs/chunk.m4s";
//                             String url = "tmp/hlsjs/chunk-stream0-" + zeroPad((int) mseq, 5) + ".m4s";
                             console.log("loading", url);
                             return SimpleAjax.getArrayBuffer(url)
                                              .doOnNext(arrayBuffer -> {
                                                  videoBuffer.appendBuffer(arrayBuffer);
                                                  console.log("segment added", mseq, url);
                                              });
                         } else {
                             return Observable.empty();
                         }
                     })
                     .subscribe(x -> {
                         console.log("event", x);
                     });

    }

    @Test
    public void testName2() throws Exception {
        String mimeType = "video/mp4";
        String vcodecs = "avc1.4d4028";
        mediaSource = new MediaSource();
        Video video = (Video) window.document.createElement("video");
        video.setAttribute("controls", "true");
        window.document.body.appendChild(video);
        video.src = URL.createObjectURL(mediaSource);
        MutableLong _mseq = new MutableLong(1);
        Rx.Observable.fromEvent(mediaSource, SOURCEOPEN)
                     .doOnNext(e -> {
                         videoBuffer = mediaSource.addSourceBuffer(mimeType + "; codecs=\"" + vcodecs + "\"");
                     })
                     .flatMap(e -> {
                         return SimpleAjax.getArrayBuffer("tmp/dash/init-stream0.m4s");
                     })
                     .doOnNext(arrayBuffer -> {
                         videoBuffer.appendBuffer(arrayBuffer);
                         console.log("init segment added");
                     })
                     .flatMap(e -> {
                         return Rx.Observable.fromEvent(videoBuffer, BUFFER_UPDATEEND);
                     })
                     .flatMap(e -> {
                         long mseq = _mseq.longValue();
                         if (mseq < 3) {
                             _mseq.add(1);
                             String url = "tmp/dash/chunk-stream0-" + zeroPad((int) mseq, 5) + ".m4s";
                             console.log("loading", url);
                             return SimpleAjax.getArrayBuffer(url)
                                              .doOnNext(arrayBuffer -> {
                                                  videoBuffer.appendBuffer(arrayBuffer);
                                                  console.log("segment added", mseq, url);
                                              });
                         } else {
                             return Observable.empty();
                         }
                     })
                     .subscribe(x -> {
                         console.log("event", x);
                     });

    }

    public static String zeroPad(int n, int width) {
        String z = "0";
        String _n = n + "";
        return _n.length() >= width ? _n : new Array(width - _n.length() + 1).join(z) + _n;
    }
}
