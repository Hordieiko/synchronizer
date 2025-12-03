package io.github.hordieiko.synchronizer;

import io.github.hordieiko.synchronizer.Synchronizer.LockAcquirer;
import io.github.hordieiko.synchronizer.function.Action;
import io.github.hordieiko.synchronizer.function.Command;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.awaitility.Awaitility;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Awaitility.waitAtMost;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;
import static org.mockito.Mockito.*;

class SynchronizerTest {

    private static final Duration COMMON_TIMEOUT = Duration.ofSeconds(1);

    ReentrantLock commonLock;
    Synchronizer commonSynchronizer;

    @BeforeEach
    void setUp() {
        commonLock = new ReentrantLock();
        commonSynchronizer = Synchronizer.of(commonLock, commonTimedTryLockAcquirer());
    }

    @AfterEach
    void tearDown() {
        if (commonLock.isHeldByCurrentThread()) {
            commonLock.unlock();
        }
    }

    private static <L extends Lock> LockAcquirer<L> commonTimedTryLockAcquirer() {
        return LockAcquirer.usingTimedTryLock(COMMON_TIMEOUT.toNanos(), TimeUnit.NANOSECONDS);
    }

    @Nested
    class NormalExecutionTests {

        static final Synchronizer synchronizer = Synchronizer.of(new ReentrantLock(), commonTimedTryLockAcquirer());

        @ParameterizedTest
        @MethodSource("parameters")
        void testNormalExecution(Incrementor incrementor) {
            assertEquals(1, incrementor.apply(new Counter()));
        }

        static Stream<Arguments> parameters() {
            return Stream.of(
                    argumentSet("Runnable",
                            (Incrementor) counter -> {
                                synchronizer.execute((Runnable) counter::increment);
                                return counter.get();
                            }),
                    argumentSet("Supplier",
                            (Incrementor) counter -> synchronizer.execute((Supplier<@NonNull Integer>) counter::increment)
                    ),
                    argumentSet("Command",
                            (Incrementor) counter -> {
                                synchronizer.execute((Command) counter::increment);
                                return counter.get();
                            }),
                    argumentSet("Callable",
                            (Incrementor) counter -> synchronizer.execute((Callable<@NonNull Integer>) counter::increment)),
                    argumentSet("Action",
                            (Incrementor) counter -> synchronizer.execute((Action<@NonNull Integer>) counter::increment)),
                    argumentSet("Action with fallback",
                            (Incrementor) counter -> requireNonNull(synchronizer.execute(counter::increment, () -> -1)))
            );
        }

        static class Counter {
            int value;

            int increment() {return ++value;}

            int get() {return value;}
        }

        interface Incrementor {
            int apply(Counter counter);
        }
    }

    @Nested
    class ExceptionalExecutionTests {

        static final ReentrantLock lock = new ReentrantLock();
        static final Synchronizer synchronizer = Synchronizer.of(lock, commonTimedTryLockAcquirer());

        @ParameterizedTest
        @MethodSource("parameters")
        void testExceptionalExecution(final Class<? extends Exception> exceptionType, final Executable testAction) {
            final var e = assertThrows(Synchronizer.ExecutionException.class, testAction);
            assertInstanceOf(exceptionType, e.getCause());
            assertFalse(lock.isLocked());
        }

        static Stream<Arguments> parameters() {
            return Stream.of(
                    argumentSet("Runnable",
                            TestUncheckedException.class,
                            (Executable) () -> synchronizer.execute(
                                    (Runnable) () -> {throw new TestUncheckedException();})),
                    argumentSet("Supplier",
                            TestUncheckedException.class,
                            (Executable) () -> synchronizer.execute(
                                    (Supplier<?>) () -> {throw new TestUncheckedException();})),
                    argumentSet("Command",
                            TestCheckedException.class,
                            (Executable) () -> synchronizer.execute(
                                    (Command) () -> {throw new TestCheckedException();})),
                    argumentSet("Callable",
                            TestCheckedException.class,
                            (Executable) () -> synchronizer.execute(
                                    (Callable<?>) () -> {throw new TestCheckedException();})),
                    argumentSet("Action",
                            TestCheckedException.class,
                            (Executable) () -> synchronizer.execute(
                                    (Action<?>) () -> {throw new TestCheckedException();})),
                    argumentSet("Action without expected exception",
                            TestUncheckedException.class,
                            (Executable) () -> synchronizer.execute(
                                    () -> {throw new TestUncheckedException();}, TestCheckedException.class)),
                    argumentSet("Action without two expected exceptions",
                            IOException.class,
                            (Executable) () -> synchronizer.execute(() -> {throw new IOException();},
                                    TestCheckedException.class, TestUncheckedException.class)),
                    argumentSet("Action without three expected exceptions",
                            IllegalStateException.class,
                            (Executable) () -> synchronizer.execute(
                                    () -> {throw new IllegalStateException();},
                                    TestCheckedException.class, TestUncheckedException.class, IOException.class))
            );
        }
    }

    @Nested
    class ExpectedExceptionTests {

        static final Synchronizer synchronizer = Synchronizer.of(new ReentrantLock(), commonTimedTryLockAcquirer());

        @ParameterizedTest
        @MethodSource("parameters")
        void testExpectedExceptions(final Class<? extends Exception> expectedType, final Executable action) {
            assertThrows(expectedType, action);
        }

        static Stream<Arguments> parameters() {
            return Stream.of(
                    argumentSet("One expected exception",
                            TestCheckedException.class,
                            (Executable) () -> synchronizer.execute(
                                    () -> {throw new TestCheckedException();}, TestCheckedException.class)),
                    argumentSet("Two expected exceptions",
                            TestUncheckedException.class,
                            (Executable) () -> synchronizer.execute(
                                    () -> {throw new TestUncheckedException();},
                                    TestCheckedException.class, TestUncheckedException.class)),
                    argumentSet("Three expected exceptions",
                            IOException.class,
                            (Executable) () -> synchronizer.execute(
                                    () -> {throw new IOException();},
                                    TestCheckedException.class, TestUncheckedException.class, IOException.class))
            );
        }
    }

    @Nested
    class ActionThrowsSynchronizerExceptionTests {

        static final Synchronizer synchronizer = Synchronizer.of(new ReentrantLock(), commonTimedTryLockAcquirer());

        @Test
        void testActionThrowsLockAcquisitionExceptionIsWrapped() {
            final var innerException = new Synchronizer.LockAcquisitionException("Inner lock failed");
            final var action = Action.of(() -> {throw innerException;});

            final var thrown = assertThrows(Synchronizer.ExecutionException.class, () -> synchronizer.execute(action));
            assertSame(innerException, thrown.getCause(),
                    "LockAcquisitionException from action should be wrapped in ExecutionException");
        }

        @Test
        void testActionThrowsExecutionExceptionIsWrapped() {
            final var innerCause = new IOException("Original failure");
            final var innerException = new Synchronizer.ExecutionException(innerCause);
            final var action = Action.of(() -> {throw innerException;});

            final var thrown = assertThrows(Synchronizer.ExecutionException.class, () -> synchronizer.execute(action));
            assertSame(innerException, thrown.getCause(),
                    "ExecutionException from action should be wrapped in ExecutionException");
            assertSame(innerCause, innerException.getCause(),
                    "Original cause should be preserved in the inner ExecutionException");
        }

        @Test
        void testActionThrowsExecutionInterruptedExceptionIsNotWrapped() {
            final var innerException = new Synchronizer.ExecutionInterruptedException(new InterruptedException());
            final var action = Action.of(() -> {throw innerException;});

            final var thrown = assertThrows(Synchronizer.ExecutionInterruptedException.class,
                    () -> synchronizer.execute(action));
            assertSame(innerException, thrown,
                    "ExecutionInterruptedException should propagate without wrapping");
        }

        @Test
        @SneakyThrows
        void testNestedSynchronizerInnerLockFailureIsWrapped() {
            try (final var executor = Executors.newSingleThreadExecutor()) {
                final var outerSync = Synchronizer.of(new ReentrantLock(), LockAcquirer.usingTryLock());
                final var innerLock = new ReentrantLock();
                final var innerSync = Synchronizer.of(innerLock, LockAcquirer.usingTryLock());

                final var lockTask = new Task<>(() -> {innerLock.lock(); return null;});
                executor.submit(lockTask);
                await().untilTrue(lockTask.completed);

                try {
                    final var thrown = assertThrows(Synchronizer.ExecutionException.class,
                            () -> outerSync.execute((Runnable) () -> innerSync.execute((Action<?>) () -> "inner work")));

                    assertInstanceOf(Synchronizer.LockAcquisitionException.class, thrown.getCause(),
                            "Inner lock failure should be wrapped, preventing confusion with outer lock failure");
                } finally {
                    executor.execute(innerLock::unlock);
                }
            }
        }

        @Test
        void testNestedSynchronizerInnerExecutionFailureIsDoubleWrapped() {
            final var outerSync = Synchronizer.of(new ReentrantLock(), LockAcquirer.usingTryLock());
            final var innerSync = Synchronizer.of(new ReentrantLock(), LockAcquirer.usingTryLock());
            final var originalCause = new IllegalStateException("Actual failure");

            final var thrown = assertThrows(Synchronizer.ExecutionException.class,
                    () -> outerSync.execute((Runnable) () -> innerSync.execute((Action<?>) () -> {
                        throw originalCause;
                    })));

            assertInstanceOf(Synchronizer.ExecutionException.class, thrown.getCause(),
                    "Inner ExecutionException should be wrapped by outer");
            final var innerException = (Synchronizer.ExecutionException) thrown.getCause();
            assertSame(originalCause, innerException.getCause(),
                    "Original cause should be accessible via double unwrapping");
        }
    }

    @Test
    void testSynchronizerFailExecuteActionWithFallback() {
        final var fallback = -1;
        assertEquals(fallback, commonSynchronizer.execute(() -> {throw new TestCheckedException();}, () -> fallback));
    }

    @Test
    void testSynchronizerExecuteActionWithFallbackInterruption() {
        try (final var executor = Executors.newSingleThreadExecutor()) {
            final var actionDuration = COMMON_TIMEOUT;
            final var interruptibleAction = Action.from((Command) () -> Thread.sleep(actionDuration.toMillis()));
            final var task = new Task<>(() -> commonSynchronizer.execute(interruptibleAction,
                    () -> {throw new IllegalStateException("unexpected");}));
            final var future = executor.submit(task);

            await().untilTrue(task.started);
            waitAtMost(actionDuration.dividedBy(2)).until(commonLock::isLocked);
            task.executionThread().interrupt();
            assertThrows(Synchronizer.ExecutionInterruptedException.class, getExceptionalResult(future));
            assertFalse(commonLock.isLocked());
        }
    }

    @Test
    void testSynchronizerInterruption() {
        try (final var executor = Executors.newSingleThreadExecutor()) {
            commonLock.lock();
            try {
                final var task = new Task<>(() -> commonSynchronizer.execute((Action<?>) () -> null));
                final var future = executor.submit(task);

                await().untilTrue(task.started);
                task.executionThread().interrupt();
                assertThrows(Synchronizer.ExecutionInterruptedException.class, getExceptionalResult(future));
            } finally {
                commonLock.unlock();
            }
        }
    }

    @Test
    @SneakyThrows
    void testSynchronizerCustomTimeoutError() {
        try (final var executor = Executors.newSingleThreadExecutor()) {
            final var task1Started = new CountDownLatch(1);
            final var extraTimeout = COMMON_TIMEOUT.plusMillis(200);
            final var synchronizer = Synchronizer.of(new ReentrantLock(), commonTimedTryLockAcquirer(),
                    _ -> new Synchronizer.LockAcquisitionException(new TestUncheckedException()));

            final var task1ExceedsSynchronizerTimeout = (Runnable) () -> synchronizer.execute((Runnable) () -> {
                task1Started.countDown();
                sleep(extraTimeout);
            });

            executor.execute(task1ExceedsSynchronizerTimeout);
            awaitLatch(task1Started);
            Awaitility.waitAtMost(extraTimeout)
                    .untilAsserted(() -> {
                        final var lockAcquisitionException = assertThrows(Synchronizer.LockAcquisitionException.class,
                                () -> synchronizer.execute((Runnable) () -> {}));
                        assertInstanceOf(TestUncheckedException.class, lockAcquisitionException.getCause());
                    });
        }
    }

    @Test
    @SneakyThrows
    void testSynchronizerExecuteOneActionAtTime() {
        try (final var executor = Executors.newFixedThreadPool(2)) {
            final var task1Started = new CountDownLatch(1);
            final var task1Release = new CountDownLatch(1);
            final var extraTimeout = COMMON_TIMEOUT.plusMillis(200);

            final var task1ExceedsSynchronizerTimeout = (Runnable) () -> commonSynchronizer.execute((Runnable) () -> {
                task1Started.countDown();
                awaitLatch(task1Release, extraTimeout);
            });
            final var task2UnableToExecuteDueToTimeout = (Runnable) () -> {
                awaitLatch(task1Started);
                Awaitility.waitAtMost(extraTimeout)
                        .untilAsserted(() -> assertThrows(Synchronizer.LockAcquisitionException.class,
                                () -> commonSynchronizer.execute((Runnable) () -> {})));
            };

            executor.execute(task1ExceedsSynchronizerTimeout);
            executor.submit(task2UnableToExecuteDueToTimeout).get(3, TimeUnit.SECONDS);
            task1Release.countDown();
        }
    }

    @Test
    @SneakyThrows
    void testSynchronizerExecuteMultipleActionsSequentially() {
        try (final var executor = Executors.newFixedThreadPool(2)) {
            final var testLock = new TwoThreadsTestLock();
            final var synchronizer = Synchronizer.of(testLock, commonTimedTryLockAcquirer());

            final var durationKeeper = new DurationKeeper();
            final var taskDuration = COMMON_TIMEOUT.dividedBy(2);
            final var task = Executors.callable(() ->
                    synchronizer.execute((Runnable) () -> {
                        durationKeeper.toggle();
                        sleep(taskDuration);
                    }));
            final var tasks = List.of(task, task);
            final var futures = executor.invokeAll(tasks, 3, TimeUnit.SECONDS);

            checkForExecutionException(futures);
            assertEquals(2, testLock.getTimedTryLockAcquisitionCount(),
                    "Two Threads have to acquire the lock via tryLock with timeout");
            assertEquals(2, testLock.getReleaseLockCount(),
                    "Two threads have to release the lock");
            assertEquals(1, testLock.getLockedThreadsCount(),
                    "One thread has to be blocked during the synchronised execution of two tasks");
            final var durationBetweenTasks = durationKeeper.get();
            assertTrue(durationBetweenTasks.compareTo(taskDuration) >= 0,
                    "The time between the execution of tasks has not to be less than the task duration.");
        }
    }

    @Test
    void testSynchronizerExecuteActionThrowsInterruptedException() {
        final var lock = new ReentrantLock();
        final var sync = Synchronizer.of(lock, LockAcquirer.usingTryLock());
        final var action = Action.of(() -> {throw new InterruptedException();});

        assertThrows(Synchronizer.ExecutionInterruptedException.class, () -> sync.execute(action));
        assertFalse(lock.isLocked());
        assertTrue(Thread.currentThread().isInterrupted());
    }

    @Test
    void testSynchronizerExecuteActionThrowsExecutionInterruptedException() {
        final var lock = new ReentrantLock();
        final var sync = Synchronizer.of(lock, LockAcquirer.usingTryLock());
        final var action = Action.of(
                () -> {throw new Synchronizer.ExecutionInterruptedException(new InterruptedException());});

        assertThrows(Synchronizer.ExecutionInterruptedException.class, () -> sync.execute(action));
        assertFalse(lock.isLocked());
        assertFalse(Thread.currentThread().isInterrupted());
    }

    @Test
    void testInterruptedFlagAfterNestedActionInterruption() {
        final var interruptedAction = Action.of(() -> {throw new InterruptedException();});

        assertThrows(Synchronizer.ExecutionInterruptedException.class,
                () -> commonSynchronizer.execute((Command) () -> {
                    throw assertThrows(Synchronizer.ExecutionInterruptedException.class,
                            () -> commonSynchronizer.execute(interruptedAction));
                }));
        assertTrue(Thread.currentThread().isInterrupted());
    }

    @Test
    void testSynchronizerExecuteActionReturnsNull() {
        assertNull(commonSynchronizer.execute((Action<?>) () -> null));
    }

    @Test
    void testSynchronizerExecuteActionUsingLockAcquirer() {
        var sync = Synchronizer.of(new ReentrantLock(), LockAcquirer.usingLock());
        var result = sync.execute((Action<?>) () -> 5);
        assertEquals(5, result);
    }

    @Test
    void testSynchronizerExecuteActionWithCustomAcquirerThrowsException() {
        final var lock = new ReentrantLock();
        final var synchronizer = Synchronizer.of(lock, _ -> {throw new TestUncheckedException();});
        assertThrows(TestUncheckedException.class, () -> synchronizer.execute((Runnable) () -> {}));
        assertFalse(lock.isLocked());
    }

    @Nested
    class LockAcquirerInterruptibilityTests {

        @ParameterizedTest
        @MethodSource("parameters")
        void testLockAcquirerInterruptibility(final LockAcquirer<ReentrantLock> lockAcquirer, final Assertion assertion) {
            try (final var executor = Executors.newSingleThreadExecutor()) {
                final var lockAcquirerWrapper = new LockAcquirerWrapper<>(lockAcquirer);
                final var lock = new ReentrantLock();
                lock.lock();

                final var task = new Task<>(() -> {
                    await().pollInSameThread().until(Thread.currentThread()::isInterrupted);
                    return lockAcquirerWrapper.acquire(lock);
                });
                final var future = executor.submit(task);

                await().untilTrue(task.started);
                task.executionThread().interrupt();
                lock.unlock();

                assertion.apply(getExceptionalResult(future));
                assertTrue(lockAcquirerWrapper.invoked);
            }
        }

        public static Stream<Arguments> parameters() {
            return Stream.of(
                    argumentSet("usingLock", LockAcquirer.usingLock(),
                            (Assertion) Assertions::assertDoesNotThrow),
                    argumentSet("usingLockInterruptibly", LockAcquirer.usingLockInterruptibly(),
                            (Assertion) e -> assertThrows(InterruptedException.class, e)),
                    argumentSet("usingTryLock",
                            LockAcquirer.usingTryLock(), (Assertion) Assertions::assertDoesNotThrow),
                    argumentSet("usingTimedTryLock", LockAcquirer.usingTimedTryLock(1, TimeUnit.SECONDS),
                            (Assertion) e -> assertThrows(InterruptedException.class, e))
            );
        }

        interface Assertion {
            void apply(Executable executable);
        }

        @RequiredArgsConstructor
        static class LockAcquirerWrapper<L extends Lock> implements LockAcquirer<L> {
            final LockAcquirer<L> delegate;
            volatile boolean invoked = false;

            @Override
            public boolean acquire(final L lock) throws InterruptedException {
                invoked = true;
                return delegate.acquire(lock);
            }
        }
    }

    @Nested
    class LockAcquirerBlockabilityTests {
        static final Duration taskDuration = Duration.ofMillis(500);
        static final Duration deviation = Duration.ofMillis(150);
        static final Duration taskEvaluation = taskDuration.minus(deviation);
        static final Duration pollInterval = Duration.ofMillis(10);

        @ParameterizedTest
        @MethodSource("parameters")
        void testLockAcquirerBlockability(final LockAcquirer<ReentrantLock> lockAcquirer) {
            try (final var executor = Executors.newSingleThreadExecutor()) {
                final var lock = new ReentrantLock();
                lock.lock();

                final var task = new Task<>(() -> lockAcquirer.acquire(lock));
                executor.submit(task);

                await().untilTrue(task.started);
                waitAtMost(taskDuration)
                        .pollInterval(pollInterval)
                        .during(taskEvaluation)
                        .untilFalse(task.completed);

                lock.unlock();
                waitAtMost(deviation).untilTrue(task.completed);
            }

        }

        static Stream<Arguments> parameters() {
            return Stream.of(
                    argumentSet("usingLock is blocked", LockAcquirer.usingLock()),
                    argumentSet("usingLockInterruptibly is blocked", LockAcquirer.usingLockInterruptibly()),
                    argumentSet("usingTimedLock is blocked (temporary)",
                            LockAcquirer.usingTimedTryLock(taskDuration.toNanos(), TimeUnit.NANOSECONDS))
            );
        }
    }

    @RequiredArgsConstructor
    static class Task<R extends @Nullable Object> implements Callable<R> {
        final Action<R> action;

        final AtomicBoolean started = new AtomicBoolean(false);
        final AtomicBoolean completed = new AtomicBoolean(false);
        final AtomicReference<@Nullable Thread> executionThread = new AtomicReference<>();

        @Override
        @SneakyThrows
        public R call() {
            try {
                executionThread.set(Thread.currentThread());
                started.set(true);
                return action.apply();
            } finally {
                completed.set(true);
            }
        }

        boolean started() {return started.get();}

        boolean completed() {return completed.get();}

        Thread executionThread() {return requireNonNull(executionThread.get());}
    }

    @Test
    @SneakyThrows
    void testLockAcquirerUsingTimedTryLockIsBlockedTemporarily() {
        final var taskDuration = Duration.ofMillis(500);
        final var lockAcquirer = LockAcquirer.usingTimedTryLock(taskDuration.toNanos(), TimeUnit.NANOSECONDS);
        try (final var executor = Executors.newSingleThreadExecutor()) {
            final var lock = new ReentrantLock();
            lock.lock();

            final var task = new Task<>(() -> lockAcquirer.acquire(lock));
            final var future = executor.submit(task);

            await().untilTrue(task.started);
            waitAtMost(taskDuration).untilTrue(task.completed);
            assertFalse(future.get());
        }
    }

    @Test
    @SneakyThrows
    void testLockAcquirerUsingTryLockIsNonBlocking() {
        final var lockAcquirer = LockAcquirer.usingTryLock();
        final var lock = new ReentrantLock();

        try (final var executor = Executors.newSingleThreadExecutor()) {
            lock.lock();

            final var task = new Task<>(() -> lockAcquirer.acquire(lock));
            final var future = executor.submit(task);

            await().untilTrue(task.started);
            assertTrue(task::completed);

            assertFalse(future.get());
        }
    }

    @Test
    void testUnlockNotCalledWhenLockAcquisitionFails() {
        final var lockSpy = spy(new ReentrantLock());
        final var synchronizer = Synchronizer.of(lockSpy, LockAcquirer.usingTryLock());

        try (final var executor = Executors.newSingleThreadExecutor()) {
            final var task = new Task<>(lockSpy::tryLock);
            executor.submit(task);
            try {
                await().untilTrue(task.started);
                assertThrows(Synchronizer.LockAcquisitionException.class, () -> synchronizer.execute((Runnable) () -> {}));
            } finally {
                executor.execute(lockSpy::unlock);
            }

            verify(lockSpy, times(2)).tryLock();
            verify(lockSpy, times(1)).unlock();
        }
    }

    @Test
    void testFallbackThrowsException() {
        class FallbackException extends RuntimeException {}

        final var exceptionalAction = (Action<Void>) () -> {throw new TestCheckedException();};
        final var exceptionalFallback = (Supplier<Void>) () -> {throw new FallbackException();};

        assertThrows(FallbackException.class,
                () -> commonSynchronizer.execute(exceptionalAction, exceptionalFallback));
    }

    @Test
    void testReentrantSynchronizerCallsSucceed() {
        var box = new Object() {
            boolean nestedCall;
        };
        commonSynchronizer.execute((Runnable) () ->
                commonSynchronizer.execute((Runnable) () -> box.nestedCall = true));
        assertTrue(box.nestedCall);
    }


    private static Executable getExceptionalResult(final Future<?> future) {
        return () -> {
            try {
                future.get(COMMON_TIMEOUT.toNanos(), TimeUnit.NANOSECONDS);
            } catch (ExecutionException e) {
                throw e.getCause();
            }
        };
    }

    private static void checkForExecutionException(final List<? extends Future<?>> futures) throws Exception {
        for (final var future : futures) future.get();
    }

    @SneakyThrows
    private static void sleep(Duration timeout) {
        TimeUnit.NANOSECONDS.sleep(timeout.toNanos());
    }

    private static void awaitLatch(CountDownLatch latch) {
        awaitLatch(latch, Duration.ofSeconds(1));
    }

    @SneakyThrows
    private static void awaitLatch(CountDownLatch latch, Duration timeout) {
        if (!latch.await(timeout.toNanos(), TimeUnit.NANOSECONDS))
            throw new IllegalStateException("Latch timeout exceeded");
    }

    private static class TwoThreadsTestLock extends ReentrantLock {
        int lockedThreads = 0;
        final AtomicInteger tryLockWithTimeoutCounter = new AtomicInteger();
        final AtomicInteger unlockCounter = new AtomicInteger();

        @Override
        public boolean tryLock(final long timeout, final TimeUnit unit) throws InterruptedException {
            tryLockWithTimeoutCounter.incrementAndGet();
            if (tryLock()) return true;

            lockedThreads++;
            return super.tryLock(timeout, unit);
        }

        @Override
        public void unlock() {
            unlockCounter.incrementAndGet();
            super.unlock();
        }

        int getTimedTryLockAcquisitionCount() {
            return tryLockWithTimeoutCounter.get();
        }

        int getReleaseLockCount() {
            return unlockCounter.get();
        }

        int getLockedThreadsCount() {
            return lockedThreads;
        }
    }

    private static class DurationKeeper {
        @Nullable Instant start;
        @Nullable Duration duration;

        void toggle() {
            final var time = Instant.now();
            if (start == null) start = time;
            else if (duration == null) duration = Duration.between(start, time);
        }

        Duration get() {return requireNonNull(duration, "Duration has not been computed yet");}
    }

    private static class TestUncheckedException extends RuntimeException {}

    private static class TestCheckedException extends Exception {}
}
