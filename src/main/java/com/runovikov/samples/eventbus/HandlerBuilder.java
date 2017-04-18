package com.runovikov.samples.eventbus;

import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * @author romanovi
 * @since 1/25/17.
 */
public class HandlerBuilder {

    public HandlerBuilder(String address, EventBus eventBus) {
        this.address = address;
        this.eventBus = eventBus;
    }

    public <T, R> Handler callback(Function<T, R> callback) {
        this.callback = callback;
        Handler handler = build();
        eventBus.addHandler(handler);
        return handler;
    }

    public HandlerBuilder io(boolean flag) {
        this.isIO = flag;
        return this;
    }

    public HandlerBuilder throttle(int count, TimeUnit timeUnit) {
        this.count = count;
        this.timeUnit = timeUnit;
        return this;
    }

    public Handler build() {
        Handler handler = new Handler(address, count, timeUnit, isIO, callback);
        return handler;
    }

    private String address;
    private EventBus eventBus;
    private Function callback;
    private boolean isIO;
    private int count;
    private TimeUnit timeUnit;

}
