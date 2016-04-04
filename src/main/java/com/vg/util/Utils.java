package com.vg.util;

import java.util.List;

public class Utils {
    public static <T> T lastElement(List<T> list) {
        if (list != null && list.size() > 0) {
            return list.get(list.size() - 1);
        }
        return null;
    }
}
