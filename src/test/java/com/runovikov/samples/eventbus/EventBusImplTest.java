package com.runovikov.samples.eventbus;

import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.Executors;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * @author romanovi
 * @since 4/18/17.
 */
public class EventBusImplTest {

    EventBusImpl eventBus;

    @Before
    public void setUp() {
        eventBus = spy(new EventBusImpl(Executors.newWorkStealingPool(), 1, "worker"));
    }

    @Test
    public void testEmpty() throws Exception {
        assertNotNull(eventBus.eventLoopService);
        assertNotNull(eventBus.ioService);
    }

    @Test
    public void testCountControl() throws Exception {
        eventBus.consume("a").callback(handler -> null);
        eventBus.publish("a", "a");
        eventBus.waitTermination();

        verify(eventBus, times(1)).countIncrement();
        verify(eventBus, times(1)).countDecrement();
    }

}
