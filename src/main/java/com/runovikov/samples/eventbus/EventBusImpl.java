package com.runovikov.samples.eventbus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Eventbus implementation
 *
 * @author romanovi
 * @since 1/25/17.
 */
public class EventBusImpl implements EventBus {

    private static final Logger log = LoggerFactory.getLogger(EventBus.class);

    // Implementation
    // -----------------------------------------------------------------------------------------------------------------

    @Override
    public <T> void publish(final String address, final T value) {
        beforeSend();
        if (handlers.containsKey(address)) {
            handlers.get(address).stream().forEach(handler -> {
                if (handler.isIO) {

                    // Double check
                    if (ioService.isShutdown()) {
                        synchronized (this.lock) {
                            if (ioService.isShutdown()) startIOPool();
                        }
                    }
                    handler.run(ioService, wrap(handler.callback, value));
                } else {
                    handler.run(eventLoopService, wrap(handler.callback, value));
                }
            });
        } else {
            throw new IllegalArgumentException(String.format("Address '%s' doesn't exist", address));
        }
    }

    @Override
    public <T, R> void publish(String address, T value, Consumer<R> callback) {
        beforeSend();
        if (handlers.containsKey(address)) {
            handlers.get(address).stream().forEach(handler -> {
                if (handler.isIO) {
                    handler.run(ioService, wrap(handler.callback, value, callback));
                } else {
                    handler.run(eventLoopService, wrap(handler.callback, value, callback));
                }
            });
        } else {
            throw new IllegalArgumentException(String.format("Address '%s' doesn't exist", address));
        }
    }

    @Override
    public void addHandler(Handler handler) {
        Set<Handler> set;
        if (handlers.containsKey(handler.address)) {
            set = handlers.get(handler.address);
        } else {
            set = new HashSet<>();
            handlers.put(handler.address, set);
        }
        set.add(handler);
    }

    @Override
    public HandlerBuilder consume(String address) {
        return new HandlerBuilder(address, this);
    }

    @Override
    public void shutdown(Runnable callback) {
        shutdownCallback = callback;
    }

    @Override
    public EventBus IOPoolPrefix(String str) {
        this.IOPoolPrefix = str;
        return this;
    }

    /**
     * Use with method with caution
     *
     * You can call this method only in the same thread where eventbus was constructed
     */
    @Override
    public void waitTermination() {
        while (count.longValue() > 0) {
            LockSupport.park();
        }
    }

    // Constructor
    // -----------------------------------------------------------------------------------------------------------------

    public EventBusImpl(ExecutorService eventLoopService,
                        int ioPoolSize,
                        String ioPoolPrefix) {
        this.main = Thread.currentThread();
        this.eventLoopService = eventLoopService;
        this.ioPoolSize = ioPoolSize;
        this.IOPoolPrefix = ioPoolPrefix;
        this.handlers = new HashMap<>();
        this.count = new LongAdder();
        startIOPool();
        this.lock = new Object();
    }

    // Properties
    // -----------------------------------------------------------------------------------------------------------------

    public ExecutorService getEventLoopService() {
        return eventLoopService;
    }

    public void setEventLoopService(ExecutorService eventLoopService) {
        this.eventLoopService = eventLoopService;
    }

    public ExecutorService getIoService() {
        return ioService;
    }

    public void setIoService(ExecutorService ioService) {
        this.ioService = ioService;
    }
    
    // Private section
    // -----------------------------------------------------------------------------------------------------------------

    void startIOPool() {
        ioService = Executors.newFixedThreadPool(ioPoolSize, new ThreadFactory() {
            final AtomicLong count =  new AtomicLong(0);
            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r, String.format("%s-%d", IOPoolPrefix, count.incrementAndGet()));
            }
        });
    }

    void countIncrement() {
        log.debug("Increment [count={}]", count.longValue());
        count.increment();
    }

    void countDecrement() {
        count.decrement();
        log.debug("Decrement [count={}]", count.longValue());
        if (0 == count.longValue()) {
            ioService.shutdown();
            if (null != shutdownCallback) shutdownCallback.run();
            if (0 == count.longValue()) {
                LockSupport.unpark(main);
            }
        }
    }

    <T> Runnable wrap(Function func, T data) {
        return () -> {
            func.apply(data);
            countDecrement();
        };
    }

    <T, R> Runnable wrap(Function<T, R> func, T data, Consumer<R> callback) {
        return () -> {
            R obj = func.apply(data);
            callback.accept(obj);
            countDecrement();
        };
    }

    void beforeSend() {
        countIncrement();
    }

    // Fields
    // -----------------------------------------------------------------------------------------------------------------

    ExecutorService eventLoopService;
    ExecutorService ioService;
    int ioPoolSize;
    Map<String, Set<Handler>> handlers;
    LongAdder count;
    Runnable shutdownCallback;
    String IOPoolPrefix;

    private final Object lock;
    private final Thread main;
}
