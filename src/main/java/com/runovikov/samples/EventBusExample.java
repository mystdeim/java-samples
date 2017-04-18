package com.runovikov.samples;

import com.runovikov.samples.eventbus.EventBus;
import com.runovikov.samples.eventbus.EventBusImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.impl.SimpleLogger;

import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static java.lang.String.format;

/**
 *
 * Example of using EventBus
 *
 * Sample output of example:
 * [workers-1] INFO com.runovikov.samples.EventBusExample - Generate number=34.09
 * [pool-1-thread-2] INFO com.runovikov.samples.EventBusExample - Get number=34.09
 * [workers-1] INFO com.runovikov.samples.EventBusExample - Generate number=40.10
 * [pool-1-thread-3] INFO com.runovikov.samples.EventBusExample - Get number=40.10
 * [main] INFO com.runovikov.samples.EventBusExample - Finished
 *
 * @author romanovi
 * @since 4/17/17.
 */
public class EventBusExample {

    private static final Logger log;

    static final String ADDRESS_START       = "start";
    static final String ADDRESS_RANDOM      = "random";
    static final String ADDRESS_PRINT_OUT   = "print_out";
    static final String IO_POOL_PREFIX      = "workers";
    static final float MAX_RANDOM_VALUE     = 100f;
    static final int IO_POOL_SIZE           = 1;

    static {
        System.setProperty(SimpleLogger.DEFAULT_LOG_LEVEL_KEY, "INFO");
        log = LoggerFactory.getLogger(EventBusExample.class);
    }

    EventBus eventBus;
    Random random;

    public static void main(String[] args) throws Exception {
        new EventBusExample().iterateExample();
    }

    public void iterateExample() {

        // init
        // ExecutorService eventLoop = Executors.newWorkStealingPool(); // for performance purpose
        // To be sure that each task will be executed in one thread exclusively we would use fixed pool
        ExecutorService eventLoop = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        random = new Random();
        eventBus = new EventBusImpl(eventLoop, IO_POOL_SIZE, IO_POOL_PREFIX);

        // connect handlers
        eventBus.consume(ADDRESS_START).callback(this::iterate);
        eventBus.consume(ADDRESS_RANDOM).io(true).throttle(1, TimeUnit.SECONDS).callback(this::random);
        eventBus.consume(ADDRESS_PRINT_OUT).callback(this::printOut);

        // fire init mesasge
        eventBus.publish(ADDRESS_START, 2);

        eventBus.waitTermination();
        eventLoop.shutdown();

        log.info("Finished");
    }

    // Callbacks
    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Just iterate in range [1..n]
     *
     * @param n
     * @return
     */
    boolean iterate(Integer n) {
        if (n > 0) {
            IntStream.rangeClosed(1, n).forEach(i -> eventBus
                // Handle response asynchronously in callback
                .publish(ADDRESS_RANDOM, MAX_RANDOM_VALUE,  outValue -> eventBus.publish(ADDRESS_PRINT_OUT, outValue))
            );
            return true;
        } else {
            log.error("Value '{}' shouldn't be negative", n);
            return false;
        }
    }

    /**
     * "Slow" task
     *
     * @param maxValue
     * @return
     */
    float random(float maxValue) {
        float num = random.nextFloat() * maxValue;
        log.info("Generate number={}", format("%.2f", num));
        return num;
    }

    /**
     * Finished task
     *
     * @param num
     * @return
     */
    Void printOut(float num) {
        log.info("Get number={}", format("%.2f", num));
        return null;
    }

}
