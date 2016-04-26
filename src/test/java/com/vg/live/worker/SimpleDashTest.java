package com.vg.live.worker;

import static org.stjs.javascript.Global.console;
import static org.stjs.javascript.Global.window;

import org.junit.Test;
import org.stjs.javascript.Array;
import org.stjs.javascript.dom.Video;
import org.stjs.javascript.dom.media.MediaSource;
import org.stjs.javascript.dom.media.SourceBuffer;
import org.stjs.javascript.dom.media.URL;

import com.vg.js.bridge.Rx;
import com.vg.js.bridge.Rx.Observable;
import com.vg.util.MutableLong;
import com.vg.util.SimpleAjax;

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
    public void testName() throws Exception {
        String mimeType = "video/mp4";
        String vcodecs = "avc1.4d4028";
        mediaSource = new MediaSource();
        Video video = (Video) window.document.createElement("video");
        video.setAttribute("controls", "true");
        window.document.body.appendChild(video);
        video.src = URL.createObjectURL(mediaSource);
        MutableLong _mseq = new MutableLong(1);
        Rx.Observable.fromEvent(mediaSource, SOURCEOPEN).doOnNext(e -> {
            videoBuffer = mediaSource.addSourceBuffer(mimeType + "; codecs=\"" + vcodecs + "\"");
        }).flatMap(e -> {
            return SimpleAjax.getArrayBuffer("tmp/dash/init-stream0.m4s");
        }).doOnNext(arrayBuffer -> {
            videoBuffer.appendBuffer(arrayBuffer);
            console.log("init segment added");
        }).flatMap(e -> {
            return Rx.Observable.fromEvent(videoBuffer, BUFFER_UPDATEEND);
        }).flatMap(e -> {
            long mseq = _mseq.longValue();
            if (mseq < 3) {
                _mseq.add(1);
                String url = "tmp/dash/chunk-stream0-" + zeroPad((int) mseq, 5) + ".m4s";
                console.log("loading", url);
                return SimpleAjax.getArrayBuffer(url).doOnNext(arrayBuffer -> {
                    videoBuffer.appendBuffer(arrayBuffer);
                    console.log("segment added", mseq, url);
                });
            } else {
                return Observable.empty();
            }
        }).subscribe(x -> {
            console.log("event", x);
        });

    }

    public static String zeroPad(int n, int width) {
        String z = "0";
        String _n = n + "";
        return _n.length() >= width ? _n : new Array(width - _n.length() + 1).join(z) + _n;
    }
}
