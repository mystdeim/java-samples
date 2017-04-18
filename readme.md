# Samples

## Eventbus
The simplest implementation of EventBus design pattern.

To connection handler:
```java
eventBus.consume("any address").callback(Function<I, O> handler);
```

Fire messages:
```java
eventBus.publish("any address", new Object());
```

Look at `EventBusExample` class inside `com.runovikov.samples` package for more details
