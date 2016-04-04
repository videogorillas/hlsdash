package com.vg.util;

import java.util.ArrayList;
import java.util.List;

import rx.Observable;
import rx.functions.Func2;

public class RxUtil {
    public static <T> Observable<List<T>> split(Observable<T> from, Func2<List<T>, T, Boolean> predicate) {
        Observable<List<T>> scan = from.scan(new ArrayList<T>(), (list, item) -> {
            if (!list.isEmpty() && predicate.call(list, item)) {
                list = new ArrayList<>();
            }
            list.add(item);
            return list;
        });

        Observable<List<T>> tokens = scan.buffer(2, 1).flatMap(ll -> {
            if (ll.size() == 1 || ll.get(0) != ll.get(1)) {
                return Observable.just(ll.get(0));
            } else {
                return Observable.empty();
            }
        });
        return tokens;
    }
}
