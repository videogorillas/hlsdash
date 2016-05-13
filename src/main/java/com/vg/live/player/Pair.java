package com.vg.live.player;

public class Pair<K, V> {
    private K k;
    private V v;

    private Pair(K k, V v) {
        this.k = k;
        this.v = v;
    }

    public static <K, V> Pair<K, V> of(K k, V v) {
        return new Pair<K, V>(k, v);
    }

    public K getKey() {
        return k;
    }

    public V getValue() {
        return v;
    }

}
