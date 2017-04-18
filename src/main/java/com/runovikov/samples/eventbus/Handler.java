package com.runovikov.samples.eventbus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static java.lang.String.format;

/**
 * @author romanovi
 * @since 1/25/17.
 */
public class Handler<T, R> {

    private static final Logger log = LoggerFactory.getLogger(Handler.class);

    public Handler(String address, int permissions, TimeUnit timeUnit, boolean isIO, Function<T, R> callback) {
        this.address = address;
        this.permissions = permissions;
        this.timeUnit = timeUnit;
        this.isIO = isIO;
        this.callback = callback;

        if (this.permissions > 0 && null != timeUnit) {
            this.buffer = new ConcurrentLinkedDeque<Long>() {
                @Override
                public boolean add(Long aLong) {
                    if (size() == Handler.this.permissions) {
                        throw new IllegalStateException(format("Max size was reached: %d", Handler.this.permissions));
                    }
                    return super.add(aLong);
                }
                @Override
                public void addLast(Long aLong) {
                    add(aLong);
                }
            };
            this.period = timeUnit.toMillis(1);
            this.waitings = new ConcurrentLinkedDeque<>();
        } else {
            this.buffer = null;
            this.period = 0;
            this.waitings = null;
        }
    }

    /**
     * It's impossible to set up executor in constructor, because executor can be reinit.
     * ExecutorService is closed every time when tasks are over
     *
     * @param executor
     * @param runnable
     * @return waiting time
     */
    public void run(ExecutorService executor, Runnable runnable) {
        if (isThrottling()) {
            waitings.addLast(runnable);
            tryNext(executor);
        } else {
            runImmediately(executor, runnable);
        }
    }

    // Implementation
    // -----------------------------------------------------------------------------------------------------------------

    void runImmediately(ExecutorService executor, Runnable runnable) {
        executor.submit(runnableImmediate(executor, runnable));
    }

    Runnable runnableImmediate(ExecutorService executor, Runnable runnable) {
        return () -> {
            try {
                runnable.run();
                if (null != waitings) {
                    tryNext(executor);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        };
    }

    void runWaiting(ExecutorService executor) {
        executor.submit(runnableWaiting(executor));
    }

    Runnable runnableWaiting(ExecutorService executor) {
        return () -> {
            long ms = getThrottlingTime();
            log.trace("Wait: {}", format("%.2f", ms / 1_000.0));
            if (ms > 0) {
                try {
                    Thread.currentThread().sleep(ms);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            isScheduled = false;
            tryNext(executor);
        };
    }

    void tryNext(ExecutorService executor) {
        synchronized (lock) {
            if (waitings.size() > 0) {
                if (can()) {
                    Runnable run = waitings.removeFirst();
                    buffer.add(System.currentTimeMillis());
                    runImmediately(executor, run);
                } else {
                    if (!isScheduled) {
                        runWaiting(executor);
                        isScheduled = true;
                    }
                }
            }
        }
    }

    boolean isThrottling() {
        return buffer != null;
    }

    boolean can() {
        removeExpired();
        return buffer.size() < permissions;
    }

    long getThrottlingTime() {
        long ms = period - (System.currentTimeMillis() - getMinTime());
        return ms > 0 ? ms : 0;
    }

    long getMinTime() {
        if (buffer.size() > 0) return buffer.stream().min(Long::compare).get();
        else return 0;
    }

    void removeExpired() {
        long max_time = System.currentTimeMillis() - period;
        buffer.removeIf(time -> time < max_time);
    }

    // Fields
    // -----------------------------------------------------------------------------------------------------------------

    final String address;
    final Function<T, R> callback;
    final int permissions;
    final long period;
    final TimeUnit timeUnit;
    final boolean isIO;

    /**
     * Timestamp storing place
     */
    final Deque<Long> buffer;
    final Deque<Runnable> waitings;

    volatile boolean isScheduled = false;

    private final Object lock = new Object();

}
