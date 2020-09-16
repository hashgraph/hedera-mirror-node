package com.hedera.mirror.grpc.util;

import org.reactivestreams.Subscription;
import reactor.core.publisher.BaseSubscriber;

public class BackpressureSubscriber<T> extends BaseSubscriber<T> {


    @Override
    protected void hookOnSubscribe(Subscription subscription) {
        subscription.request(Long.MAX_VALUE);
    }

    @Override
    protected void hookOnNext(T value) {
    }
}
