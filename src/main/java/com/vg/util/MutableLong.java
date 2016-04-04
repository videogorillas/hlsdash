package com.vg.util;

public class MutableLong {

    private long val;

    public MutableLong(long val) {
        this.val = val;
    }

    public long longValue() {
        return val;
    }

    public void add(long v) {
        val += v;
    }

}
