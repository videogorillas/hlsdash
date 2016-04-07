package com.vg.util;

import com.vg.js.bridge.Rx;
import org.stjs.javascript.Array;
import org.stjs.javascript.XMLHttpRequest;
import org.stjs.javascript.functions.Callback1;
import org.stjs.javascript.functions.Callback2;
import org.stjs.javascript.typed.ArrayBuffer;

import static com.vg.util.SimpleAjax.Method.GET;
import static com.vg.util.SimpleAjax.Method.POST;
import static com.vg.util.SimpleAjax.Method.PUT;
import static com.vg.util.SimpleAjax.Method.DELETE;
import static org.stjs.javascript.Global.console;
import static org.stjs.javascript.JSCollections.$array;

public class SimpleAjax {

    public final static class XHRHeader {
        public static final String CONTENT_TYPE = "Content-type";

        public final String header;
        public final String value;

        public XHRHeader(String header, String value) {
            this.header = header;
            this.value = value;
        }

        @Override
        public String toString() {
            return header + " " + value;
        }
    }

    public final static class Method {
        public static final Method GET = new Method("GET");
        public static final Method PUT = new Method("PUT");
        public static final Method POST = new Method("POST");
        public static final Method DELETE = new Method("DELETE");

        private final String m;

        public Method(String method) {
            this.m = method;
        }

        public String asString() {
            return m;
        }
    }

    public static void req(Method method, String url, String data, Array<XHRHeader> headers, Callback1<String> onLoad,
                           Callback1<XMLHttpRequest> onError) {
        XMLHttpRequest http = new XMLHttpRequest();
        http.open(method.asString(), url, true);
        if (headers != null) {
            for (int i = 0; i < headers.$length(); i++) {
                XHRHeader h = headers.$get(i);
                http.setRequestHeader(h.header, h.value);
            }
        }

        http.onreadystatechange = () -> {
            if (http.readyState == 4) {
                // 1xx Informational
                // 2xx Success
                if (http.status >= 200 && http.status < 300) {
                    if (onLoad != null)
                        onLoad.$invoke(http.responseText);
                }

                // 3xx Redirection
                if (http.status >= 300 && http.status < 400) {
                    if (onLoad != null)
                        onLoad.$invoke(http.responseText);
                }

                //4xx Client Error
                //5xx Server Error
                if (http.status >= 400) {
                    if (onError != null)
                        onError.$invoke(http);
                }

                //CORS errors, connection errors etc
                if (http.status == 0) {
                    if (onError != null)
                        onError.$invoke(http);
                }
            }
        };
        http.send(data);
    }

    public static void load(String url, Callback1<String> callback, Callback2<String, XMLHttpRequest> error) {
        req(GET, url, null, null, callback, xmlHttpRequest -> {
            error.$invoke(null, xmlHttpRequest);
        });
    }

    public static XMLHttpRequest loadBinary(String url, Callback1<ArrayBuffer> callback,
                                            Callback2<String, XMLHttpRequest> error) {
        XMLHttpRequest http = new XMLHttpRequest();
        http.open("GET", url, true);
        http.responseType = "arraybuffer";
        http.onreadystatechange = () -> {
            if (http.readyState == 4) {
                if (http.status >= 200 && http.status < 300) {
                    if (callback != null)
                        callback.$invoke(http.response);
                }
                if (http.status >= 300 && http.status < 300) {
                    if (callback != null)
                        callback.$invoke(http.response);
                }
                if (http.status >= 400) {
                    if (error != null)
                        error.$invoke(null, http);
                }
                if (http.status == 0) {
                    if (error != null)
                        error.$invoke(null, http);
                }
            }
        };
        http.send(null);
        return http;
    }

    public static void post(String url, String data, Callback1<String> onDone, Callback1<XMLHttpRequest> onError) {
        req(POST, url, data, $array(new XHRHeader(XHRHeader.CONTENT_TYPE, "application/x-www-form-urlencoded")), onDone, onError);
    }

    public static void put(String url, String data, Callback1<String> onDone, Callback1<XMLHttpRequest> onError) {
        req(PUT, url, data, $array(new XHRHeader(XHRHeader.CONTENT_TYPE, "application/x-www-form-urlencoded")), onDone, onError);
    }

    public static void deleteReq(String url, Callback1<String> onDone, Callback1<XMLHttpRequest> onError) {
        req(DELETE, url, null, null, onDone, onError);
    }

    public static Rx.Observable<String> postRx(String url, String data) {
        return request(url, data, "POST");
    }

    public static Rx.Observable<String> putRx(String url, String data) {
        return request(url, data, "PUT");
    }

    public static Rx.Observable<String> get(String url) {
        return request(url, null, "GET");
    }

    public static Rx.Observable<String> request(String url, String data, String method) {
        Rx.Observable<String> o = Rx.Observable.$create(observer -> {
            XMLHttpRequest http = new XMLHttpRequest();
            http.open(method, url, true);
            http.setRequestHeader("Content-type", "application/x-www-form-urlencoded");
            http.onload = () -> {
                if (http.status == 200) {
                    observer.onNext(http.responseText);
                    observer.onCompleted();
                } else {
                    dbg("error " + http.status + " " + method + " " + url);
                    observer.onError(http);
                }
            };
            http.onerror = e -> {
                dbg("error " + http.status + " " + method + " " + url);
                observer.onError(e);
            };
            http.send(data);

            return () -> {
                if (http.readyState != 4) {
                    dbg("abort " + method + " " + url);
                    http.abort();
                }
            };
        });
        return o;
    }

    public static boolean debug = true;

    private static void dbg(String msg) {
        if (debug) {
            console.log(msg);
        }
    }

    public static Rx.Observable<ArrayBuffer> getArrayBuffer(String url) {
        Rx.Observable<ArrayBuffer> o = Rx.Observable.create(observer -> {
            XMLHttpRequest http = new XMLHttpRequest();
            http.open("GET", url, true);
            http.responseType = "arraybuffer";
            http.onreadystatechange = () -> {
                if (http.readyState == 4) {
                    if (http.status == 200) {
                        observer.onNext(http.response);
                        observer.onCompleted();
                    } else {
                        observer.onError(http);
                    }
                }
            };
            http.send(null);
        });
        return o;
    }

}
