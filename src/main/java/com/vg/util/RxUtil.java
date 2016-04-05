package com.vg.util;

import org.stjs.javascript.functions.Function2;

import com.vg.js.bridge.Rx.Observable;

import js.util.ArrayList;
import js.util.List;


public class RxUtil {
    public static <T> Observable<List<T>> split(Observable<T> from, Function2<List<T>, T, Boolean> predicate) {
        Observable<List<T>> scan = from.scan((list, item) -> {
            if (!list.isEmpty() && predicate.$invoke(list, item)) {
                list = new ArrayList<>();
            }
            list.add(item);
            return list;
        }, new ArrayList<T>());

        Observable<List<T>> tokens = scan.bufferWithCount(2, 1).flatMap(ll -> {
            if (ll.$length() == 1 || ll.$get(0) != ll.$get(1)) {
                return Observable.just(ll.$get(0));
            } else {
                return Observable.empty();
            }
        });
        return tokens;
    }
}
