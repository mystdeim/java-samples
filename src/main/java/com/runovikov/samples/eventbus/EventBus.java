package com.runovikov.samples.eventbus;

import java.util.function.Consumer;

/**
 * The simplest eventbus ever
 *
 * @author romanovi
 * @since 1/25/17.
 */
public interface EventBus {

    /**
     * Publish a message.
     *
     * @param address
     * @param value
     * @param <T>
     */
    <T> void publish(String address, T value);

    /**
     * Publish message and run callback with result
     *
     * @param address
     * @param value
     * @param callback
     * @param <T> input message
     * @param <R> output message
     */
    <T, R> void publish(String address, T value, Consumer<R> callback);

    void addHandler(Handler builder);

    /**
     * Consume messages
     *
     * @param address
     * @return
     */
    HandlerBuilder consume(String address);

    /**
     *
     * @param str
     * @return
     */
    EventBus IOPoolPrefix(String str);

    /**
     * Calls after IO-pool shutdown
     *
     * @param callback
     */
    void shutdown(Runnable callback);

    /**
     * Wait until all tasks finished
     *
     * Use with method with caution.
     */
    void waitTermination();
}