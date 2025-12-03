# Synchronizer

A Java utility library for executing actions under locks with consistent lock acquisition and exception handling.

## Features

- **Flexible Lock Acquisition**: Support for any `java.util.concurrent.locks.Lock` implementation
- **Customizable Acquisition Strategies**: Built-in blocking, timed, and interruptible strategies via `LockAcquirer` functional interface - or create your own custom strategy
- **Rich Action Types**: Execute `Runnable`, `Supplier`, `Callable`, `Command`, and `Action` with automatic lock management
- **Typed Exception Handling**: Declare expected exceptions with compile-time safety
- **Fallback Support**: Graceful degradation with fallback suppliers
- **Interrupt-Safe**: Proper interrupt handling and flag restoration

## Requirements

- Java 25+
- Maven 3.8+

## Installation

Add to your `pom.xml`:

```xml
<dependency>
    <groupId>io.github.hordieiko</groupId>
    <artifactId>synchronizer</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

## Usage

### Basic Example

```java
import io.github.hordieiko.synchronizer.Synchronizer;

import static io.github.hordieiko.synchronizer.Synchronizer.LockAcquirer.usingTimedTryLock;

void main() {
    var synchronizer = Synchronizer.of(new ReentrantLock(), usingTimedTryLock(1, TimeUnit.SECONDS));
    // Execute with automatic lock management
    var result = synchronizer.execute(() -> {
        // Your synchronized code here
        return 42;
    });
}
```

### With Typed Exception Handling

```java
import io.github.hordieiko.synchronizer.Synchronizer;
import io.github.hordieiko.synchronizer.Synchronizer.LockAcquirer;

import java.sql.SQLException;

void main() throws IOException, SQLException {
    var synchronizer = Synchronizer.of(new ReentrantLock(), LockAcquirer.usingLock());
    synchronizer.execute(() -> {
        // May throw IOException or SQLException
        return performRiskyOperation();
    }, IOException.class, SQLException.class);
}
```

### With Fallback

```java
import io.github.hordieiko.synchronizer.Synchronizer;
import io.github.hordieiko.synchronizer.function.Action;

void main() {
    var synchronizer = Synchronizer.of(new ReentrantLock(), Synchronizer.LockAcquirer.usingLock());
    
    var action = (Action<Integer>) () -> riskyOperation();
    var fallback = (Supplier<Integer>) () -> -1;

    // Fallback called on any failure except interruption
    var result = synchronizer.execute(action, fallback);
}
```

### Nested Synchronizers

```java
import io.github.hordieiko.synchronizer.Synchronizer;
import io.github.hordieiko.synchronizer.Synchronizer.LockAcquirer;

void main() {
    var outer = Synchronizer.of(new ReentrantLock(), LockAcquirer.usingTryLock());
    var inner = Synchronizer.of(new ReentrantLock(), LockAcquirer.usingTryLock());

    try {
        outer.execute(() -> inner.execute(() -> work()));
    } catch (Synchronizer.LockAcquisitionException e) {
        // Outer lock failed
        throw new RuntimeException("Execution failed: Cannot acquire outer lock");
    } catch (Synchronizer.ExecutionException e) {
        if (e.getCause() instanceof Synchronizer.LockAcquisitionException) {
            // Inner lock failed (wrapped to prevent ambiguity)
            throw new RuntimeException("Execution failed: Cannot acquire inner lock");
        }
        throw new RuntimeException("Execution failed", e);
    } catch (Synchronizer.ExecutionInterruptedException e) {
        Thread.currentThread().interrupt();
        throw new RuntimeException("Execution interrupted", e);
    }
}
```

## Exception Handling

- **`LockAcquisitionException`**: Thrown when this synchronizer's lock cannot be acquired
- **`ExecutionException`**: Wraps exceptions from action execution
- **`ExecutionInterruptedException`**: Propagates unwrapped as an interrupt signal

## License

This project is available under the MIT License.
