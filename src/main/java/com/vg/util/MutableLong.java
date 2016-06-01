package com.vg.util;

public class MutableLong {

    public long value;

    public MutableLong(long value) {
        this.value = value;
    }

    public void add(long v) {
        value += v;
    }

}
