package com.vg.util;

import js.nio.ByteBuffer;
import js.util.List;
import org.stjs.javascript.typed.DataView;
import org.stjs.javascript.typed.Int8Array;

public class Utils {
    public static <T> T lastElement(List<T> list) {
        if (list != null && list.size() > 0) {
            return list.get(list.size() - 1);
        }
        return null;
    }

    public static String toAbsoluteUri(String base, String relative) {
        boolean absoluteUrl = relative.startsWith("http://") || relative.startsWith("https://")
                || relative.startsWith("/");
        if (!absoluteUrl) {
            int idx = base.lastIndexOf("/");
            if (idx >= 0) {
                relative = base.substring(0, idx) + "/" + relative;
            }
        }
        return relative;
    }

    public static DataView dataView(ByteBuffer bb) {
        Int8Array array = (Int8Array) (Object) bb.array();
        DataView view = new DataView(array.buffer, bb.position(), bb.remaining());
        return view;
    }
}
