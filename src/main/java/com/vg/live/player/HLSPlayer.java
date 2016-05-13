package com.vg.live.player;

import com.vg.js.bridge.Rx;
import com.vg.js.bridge.Rx.Disposable;
import com.vg.js.bridge.Rx.Observable;
import com.vg.live.video.MP4Segment;
import com.vg.live.worker.M4sWorker;
import com.vg.util.SimpleAjax;
import js.nio.ByteBuffer;
import org.stjs.javascript.Array;
import org.stjs.javascript.dom.Anchor;
import org.stjs.javascript.dom.DOMEvent;
import org.stjs.javascript.dom.Video;
import org.stjs.javascript.dom.media.MediaSource;
import org.stjs.javascript.dom.media.SourceBuffer;
import org.stjs.javascript.dom.media.URL;
import org.stjs.javascript.file.Blob;
import org.stjs.javascript.functions.Callback1;
import org.stjs.javascript.typed.DataView;
import org.stjs.javascript.typed.Int8Array;

import static com.vg.util.Utils.dataView;
import static com.vg.util.Utils.toAbsoluteUri;
import static org.stjs.javascript.Global.console;
import static org.stjs.javascript.Global.window;
import static org.stjs.javascript.JSCollections.$array;
import static org.stjs.javascript.JSCollections.$map;
import static org.stjs.javascript.JSObjectAdapter.$put;

public class HLSPlayer {
    private Video video;
    private MediaSource mediaSource;
    private int sequenceNo;
    private long duration;
    private Disposable disposable;

    private static final int BUFFER_TIME = 15;

    private static final String SOURCEOPEN = "sourceopen";
    private static final String READYSTATE_CLOSED = "closed";
    private static final String READYSTATE_OPEN = "open";
    private static final String READYSTATE_ENDED = "ended";
    private static final String BUFFER_ERROR = "error";
    private static final String BUFFER_UPDATEEND = "updateend";
    private static final String TIMEUPDATE = "timeupdate";
    private SourceBuffer videoBuffer;

    public HLSPlayer(Video video) {
        this.video = video;
    }

    public void load(String m3u8Url, Callback1 onDone) {
        sequenceNo = 0;
        duration = 0;

        disposable = init()
                .doOnNext(b -> onDone.$invoke(null))
                .doOnError(err -> onDone.$invoke(err))
                .flatMap(b -> tsUrls(m3u8Url))
                .concatMap(tsUrl -> appendNextSegment(tsUrl).pausableBuffered(bufferUnderflow()))
                .subscribe();
    }

    private Observable<String> tsUrls(String url) {
        Observable<HLSPlaylist> m3u8 = loadHLSPlaylist(url).flatMap(hls -> {
            Array<HLSPlaylist.Variant> variants = hls.getVariantList();
            if (hls.getMediaList().$length() == 0 && variants.$length() == 0) {
                return Observable.$throw("Empty HLS. Neither media nor stream variants are present.");
            }
            if (variants.$length() > 0) {
                String variantUri = variants.$get(0).uri;
                variantUri = toAbsoluteUri(url, variantUri);
                return loadHLSPlaylist(variantUri);
            }
            return Observable.just(hls);
        });

        return m3u8.flatMap(hls -> {
            return Observable.from(hls.getMediaList())
                    .map(media -> toAbsoluteUri(url, media.url));
        });
    }

    private Observable<Boolean> appendNextSegment(String url) {
        return SimpleAjax.getArrayBuffer(url)
                .flatMap(ts -> {
                    ByteBuffer inputBuf = ByteBuffer.wrap(new Int8Array(ts));
                    Observable<MP4Segment> m4s = M4sWorker.m4s(inputBuf, duration, sequenceNo + 1);

                    if (sequenceNo == 0) {
                        return m4s.doOnNext(m -> {
                            ByteBuffer initSeg = m.init;
                            console.log("init", initSeg);
                            DataView dataView = dataView(initSeg);
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
//                    console.log("videoBuffer.updating", videoBuffer.updating);
                    Observable<Boolean> bufferReady = Observable.just(true);
                    if (videoBuffer.updating) {
                        bufferReady = Rx.Observable.fromEvent(videoBuffer, BUFFER_UPDATEEND).take(1).map(ee -> true);
                    }
                    return bufferReady.doOnNext(e -> {
                        console.log("add data", sequenceNo, "pts", duration);
                        videoBuffer.appendBuffer(dataView(m4s.data));

                        for (int i = 0; i < m4s.trun.getSampleCount(); i++) {
                            duration += m4s.trun.getSampleDuration(i);
                        }

                        sequenceNo++;
                    });
                });
    }

    private Observable<Boolean> init() {
        String mimeType = "video/mp4";
        String vcodecs = "avc1.4d4028";

        mediaSource = new MediaSource();
        video.src = URL.createObjectURL(mediaSource);

        Observable<DOMEvent> open = Rx.Observable.fromEvent(mediaSource, SOURCEOPEN);
        return open.doOnNext(e -> {
            console.log("source open");
            videoBuffer = mediaSource.addSourceBuffer(mimeType + "; codecs=\"" + vcodecs + "\"");
            videoBuffer.addEventListener(BUFFER_ERROR, (_e) -> {
                console.log("videoBuffer error", _e);
            });
            $put(window, "mediaSource", mediaSource);
            $put(window, "videoBuffer", videoBuffer);
        }).map(e -> true);
    }

    private Observable<HLSPlaylist> loadHLSPlaylist(String url) {
        return SimpleAjax.get(url).map(str -> {
            HLSPlaylist parsed = HLSPlaylist.parseString(str);
            parsed.uri = url;
            return parsed;
        });
    }

    private Observable<Boolean> bufferUnderflow() {
        return Observable
                .merge(Observable.fromEvent(videoBuffer, BUFFER_UPDATEEND),
                       Observable.fromEvent(video, TIMEUPDATE))
                .map(e -> !bufferedEnough())
                .doOnNext(ub -> console.log("underbuffered", ub));
    }

    private boolean bufferedEnough() {
        int len = videoBuffer.buffered.length;
        return len > 0 && videoBuffer.buffered.end(len - 1) - video.currentTime >= BUFFER_TIME;
    }

    public void close() {
        disposable.dispose();
        video.src = "";
    }
}
