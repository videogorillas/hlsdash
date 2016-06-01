package com.vg.live.player;

import com.vg.js.bridge.Rx;
import com.vg.js.bridge.Rx.Disposable;
import com.vg.js.bridge.Rx.Observable;
import com.vg.live.video.AVFrame;
import com.vg.live.worker.M4sWorker;
import com.vg.util.MutableLong;
import com.vg.util.SimpleAjax;
import js.nio.ByteBuffer;
import org.stjs.javascript.Array;
import org.stjs.javascript.Map;
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

public class HLSPlayer {
    private Video video;
    private MediaSource mediaSource;
    private int sequenceNo;
    private long videoPts;
    private long audioPts;
    private Map<String, Boolean> knownUrls;
    private boolean live;
    private SourceBuffer videoBuffer;
    private SourceBuffer audioBuffer;
    private Observable<DOMEvent> videoUpdateRx;
    private Observable<DOMEvent> audioUpdateRx;
    private Disposable disposable;

    private static final int BUFFER_TIME = 15;

    private static final String SOURCEOPEN = "sourceopen";
    private static final String READYSTATE_CLOSED = "closed";
    private static final String READYSTATE_OPEN = "open";
    private static final String READYSTATE_ENDED = "ended";
    private static final String BUFFER_ERROR = "error";
    private static final String BUFFER_UPDATEEND = "updateend";
    private static final String TIMEUPDATE = "timeupdate";

    public HLSPlayer(Video video) {
        this.video = video;
    }

    public void load(String m3u8Url, Callback1 onDone) {
        sequenceNo = 1;
        audioPts = -1;
        videoPts = -1;
        knownUrls = $map();

        disposable = init()
                .doOnNext(b -> onDone.$invoke(null))
                .doOnError(err -> onDone.$invoke(err))
                .flatMap(b -> parseTsUrls(m3u8Url))
//                .take(1)
//                .flatMap(b -> Observable.just("http://localhost/code/hlsdash/testdata/zoomoo/76fab9ea8d4dc3941bd0872b7bef2c9c_31321.ts"))
                .doOnNext(tsUrl -> knownUrls.$put(tsUrl, true))
                .concatMap(tsUrl -> appendNextSegment(tsUrl).pausableBuffered(bufferUnderflow()))
                .flatMap(x -> cleanup(videoBuffer, videoUpdateRx))
                .flatMap(x -> cleanup(audioBuffer, audioUpdateRx))
                .doOnError(err -> console.log("something went wrong", err))
                .subscribe();
    }

    private Observable<String> parseTsUrls(String url) {
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
        }).share();

        Observable<HLSPlaylist> updates = m3u8.flatMap(hls -> {
            return Observable
                    .interval(5000)
//                    .flatMapFirst(...) TODO
                    .flatMap(x -> {
                        if (live) {
                            return loadHLSPlaylist(hls.uri) ;
                        } else {
                            return Observable.empty();
                        }
                    });
        });

        Observable<HLSPlaylist> playlists = m3u8.merge(updates);

        return playlists.flatMap(hls -> {
            live = !hls.hasEndList();

            return Observable.from(hls.getMediaList())
                    .map(media -> toAbsoluteUri(url, media.url))
                    .filter(tsUrl -> !knownUrls.$get(tsUrl));
        });
    }

    private Observable<Boolean> appendNextSegment(String url) {
        return SimpleAjax.getArrayBuffer(url)
                .flatMap(ts -> {
                    ByteBuffer inputBuf = ByteBuffer.wrap(new Int8Array(ts));
                    Observable<AVFrame> frames = M4sWorker.frames(inputBuf).shareReplay();

                    if (sequenceNo == 1) {
                        return frames
                                .takeWhile(f -> audioPts == -1 || videoPts == -1)
                                .reduce((x, f) -> {
                                    if (f.isVideo() && videoPts == -1) videoPts = f.pts;
                                    if (f.isAudio() && audioPts == -1) audioPts = f.pts;
                                    return "pewpew";
                                }, "gagaga")
                                .concatMap(x -> {
                                    console.log("video-audio desync is", videoPts - audioPts);
                                    if (videoPts < audioPts) {
                                        audioPts -= videoPts;
                                        videoPts = 0;
                                    } else {
                                        videoPts -= audioPts;
                                        audioPts = 0;
                                    }
                                    return M4sWorker.framesToM4sDemux(frames, inputBuf.capacity(), sequenceNo, videoPts, audioPts);
                                });
                    } else {
                        return M4sWorker.framesToM4sDemux(frames, inputBuf.capacity(), sequenceNo, videoPts, audioPts);
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
                .doOnNext(m4s -> {
                    if (sequenceNo == 1) {
                        ByteBuffer initSeg = m4s.init;
                        console.log("init", initSeg);
                        DataView dataView = dataView(initSeg);
                        console.log("dataView", dataView, dataView.buffer, dataView.byteOffset, dataView.byteLength);
                        SourceBuffer buffer = m4s.isVideo() ? videoBuffer : audioBuffer;
                        buffer.appendBuffer(dataView);
                        console.log("init segment added video=" + m4s.isVideo());

                        video.currentTime = (double) Math.max(videoPts, audioPts) / m4s.sidx.timescale;
                        console.log("currentTime", video.currentTime);
                    }
                })
                .doOnError(err -> console.log("something wrong", err))
                .flatMap(m4s -> {
                    Observable<Boolean> bufferReady = m4s.isVideo() ? videoBufferReady() : audioBufferReady();
                    return bufferReady.doOnNext(e -> {
                        console.log("add data video=" + m4s.isVideo(), "seq", sequenceNo, "pts", m4s.startTime);
                        SourceBuffer buffer = m4s.isVideo() ? videoBuffer : audioBuffer;
                        buffer.appendBuffer(dataView(m4s.data));

                        for (int i = 0; i < m4s.trun.getSampleCount(); i++) {
                            if (m4s.isVideo()) {
                                videoPts += m4s.trun.getSampleDuration(i);
                            } else {
                                audioPts += m4s.trun.getSampleDuration(i);
                            }
                        }
                    });
                })
                .doOnCompleted(() -> sequenceNo++);
    }

    private Observable<Boolean> init() {
        String vMimeType = "video/mp4";
        String vcodecs = "avc1.4d4028";

        String aMimeType = "audio/mp4";
        String acodecs = "mp4a.40.2";

        mediaSource = new MediaSource();
        video.src = URL.createObjectURL(mediaSource);

        Observable<DOMEvent> open = Rx.Observable.fromEvent(mediaSource, SOURCEOPEN);
        return open.doOnNext(e -> {
            console.log("source open");

            videoBuffer = mediaSource.addSourceBuffer(vMimeType + "; codecs=\"" + vcodecs + "\"");
            videoBuffer.addEventListener(BUFFER_ERROR, (_e) -> {
                console.log("videoBuffer error", _e);
            });
            videoUpdateRx = Observable.fromEvent(videoBuffer, BUFFER_UPDATEEND).share();

            audioBuffer = mediaSource.addSourceBuffer(aMimeType + "; codecs=\"" + acodecs + "\"");
            audioBuffer.addEventListener(BUFFER_ERROR, (_e) -> {
                console.log("audioBuffer error", _e);
            });
            audioUpdateRx = Observable.fromEvent(audioBuffer, BUFFER_UPDATEEND).share();
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
                .merge(
                        Observable.fromEvent(videoBuffer, BUFFER_UPDATEEND),
                        Observable.fromEvent(audioBuffer, BUFFER_UPDATEEND),
                        Observable.fromEvent(video, TIMEUPDATE))
                .map(e -> !bufferedEnough(videoBuffer) || !
                        bufferedEnough(videoBuffer))
                .doOnNext(ub -> console.log("underbuffered", ub));
    }

    private boolean bufferedEnough(SourceBuffer buffer) {
        int len = buffer.buffered.length;
        return len > 0 && buffer.buffered.end(len - 1) - video.currentTime >= BUFFER_TIME;
    }

    private Observable<Boolean> videoBufferReady() {
        return bufferReady(videoBuffer, videoUpdateRx);
    }

    private Observable<Boolean> audioBufferReady() {
        return bufferReady(audioBuffer, audioUpdateRx);
    }

    private Observable<Boolean> bufferReady(SourceBuffer buffer, Observable bufferUpdateRx) {
        Observable<Boolean> ready = Observable.create(observer -> {
            if (buffer.updating) {
                observer.onError("buffer updating");
            } else {
                observer.onNext(true);
                observer.onCompleted();
            }
        });
        return ready.retryWhen(errors -> errors.flatMap(err -> bufferUpdateRx.take(1)));
    }

    private Observable<Boolean> cleanup(SourceBuffer buffer, Observable bufferUpdateRx) {
        return bufferReady(buffer, bufferUpdateRx).doOnNext(x -> {
            if (buffer.buffered.length > 0 && video.currentTime > 10) {
                console.log("buffered", buffer.buffered.start(0), buffer.buffered.end(0));
                buffer.remove(0, video.currentTime - 10);
            }
        });
    }

    public void close() {
        disposable.dispose();
        video.src = "";
    }
}
