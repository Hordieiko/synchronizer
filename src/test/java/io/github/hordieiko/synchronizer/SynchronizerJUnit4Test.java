package io.github.hordieiko.synchronizer;

import io.github.hordieiko.synchronizer.Synchronizer.LockAcquirer;
import io.github.hordieiko.synchronizer.function.Action;
import io.github.hordieiko.synchronizer.function.Command;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.awaitility.Awaitility;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.function.ThrowingRunnable;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Awaitility.waitAtMost;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(Enclosed.class)
public class SynchronizerJUnit4Test {

    private static final Duration COMMON_TIMEOUT = Duration.ofSeconds(1);

    private static <L extends Lock> LockAcquirer<L> commonTimedTryLockAcquirer() {
        return LockAcquirer.usingTimedTryLock(COMMON_TIMEOUT.toNanos(), TimeUnit.NANOSECONDS);
    }


    @RunWith(Parameterized.class)
    public static class NormalExecutionTests {
        static final Synchronizer synchronizer = Synchronizer.of(new ReentrantLock(), commonTimedTryLockAcquirer());

        @Parameterized.Parameter(0)
        public String testName;

        @Parameterized.Parameter(1)
        public Incrementor incrementor;

        @Parameterized.Parameters(name = "{0}")
        public static Collection<Object[]> parameters() {
            return Arrays.asList(
                    new Object[]{"Runnable", (Incrementor) counter -> {
                        synchronizer.execute((Runnable) counter::increment);
                        return counter.get();
                    }},
                    new Object[]{"Supplier", (Incrementor) counter ->
                            synchronizer.execute((Supplier<@NonNull Integer>) counter::increment)},
                    new Object[]{"Command", (Incrementor) counter -> {
                        synchronizer.execute((Command) counter::increment);
                        return counter.get();
                    }},
                    new Object[]{"Callable", (Incrementor) counter ->
                            synchronizer.execute((Callable<@NonNull Integer>) counter::increment)},
                    new Object[]{"Action", (Incrementor) counter ->
                            synchronizer.execute((Action<@NonNull Integer>) counter::increment)},
                    new Object[]{"Action with fallback", (Incrementor) counter ->
                            requireNonNull(synchronizer.execute(counter::increment, () -> -1))}
            );
        }

        @Test
        public void testNormalExecution() {
            assertEquals(1, incrementor.apply(new Counter()));
        }

        static class Counter {
            int value;

            int increment() {return ++value;}

            int get() {return value;}
        }

        public interface Incrementor {
            int apply(Counter counter);
        }
    }

    @RunWith(Parameterized.class)
    public static class ExceptionalExecutionTests {
        static final ReentrantLock lock = new ReentrantLock();
        static final Synchronizer synchronizer = Synchronizer.of(lock, commonTimedTryLockAcquirer());

        @Parameterized.Parameter(0)
        public String testName;

        @Parameterized.Parameter(1)
        public Class<? extends Exception> exceptionType;

        @Parameterized.Parameter(2)
        public ThrowingRunnable executable;

        @Parameterized.Parameters(name = "{0}")
        public static Collection<Object[]> parameters() {
            return Arrays.asList(
                    new Object[]{"Runnable", TestUncheckedException.class,
                            (ThrowingRunnable) () -> synchronizer.execute(
                                    (Runnable) () -> {throw new TestUncheckedException();})},
                    new Object[]{"Supplier", TestUncheckedException.class,
                            (ThrowingRunnable) () -> synchronizer.execute(
                                    (Supplier<?>) () -> {throw new TestUncheckedException();})},
                    new Object[]{"Command", TestCheckedException.class,
                            (ThrowingRunnable) () -> synchronizer.execute(
                                    (Command) () -> {throw new TestCheckedException();})},
                    new Object[]{"Callable", TestCheckedException.class,
                            (ThrowingRunnable) () -> synchronizer.execute(
                                    (Callable<?>) () -> {throw new TestCheckedException();})},
                    new Object[]{"Action", TestCheckedException.class,
                            (ThrowingRunnable) () -> synchronizer.execute(
                                    (Action<?>) () -> {throw new TestCheckedException();})},
                    new Object[]{"Action without expected exception", TestUncheckedException.class,
                            (ThrowingRunnable) () -> synchronizer.execute(
                                    () -> {throw new TestUncheckedException();},
                                    TestCheckedException.class)},
                    new Object[]{"Action without two expected exceptions", IOException.class,
                            (ThrowingRunnable) () -> synchronizer.execute(
                                    () -> {throw new IOException();},
                                    TestCheckedException.class, TestUncheckedException.class)},
                    new Object[]{"Action without three expected exceptions", IllegalStateException.class,
                            (ThrowingRunnable) () -> synchronizer.execute(
                                    () -> {throw new IllegalStateException();},
                                    TestCheckedException.class, TestUncheckedException.class, IOException.class)}
            );
        }

        @Test
        public void testExceptionalExecution() {
            Exception e = assertThrows(Synchronizer.ExecutionException.class, executable);
            Throwable cause = e.getCause();
            assertNotNull(cause);
            assertTrue("Expected " + exceptionType.getName() + " but got " + cause.getClass().getName(),
                    exceptionType.isInstance(e.getCause()));
            assertFalse(lock.isLocked());
        }
    }

    @RunWith(Parameterized.class)
    public static class ExpectedExceptionTests {
        static final Synchronizer synchronizer = Synchronizer.of(new ReentrantLock(), commonTimedTryLockAcquirer());

        @Parameterized.Parameter(0)
        public String testName;

        @Parameterized.Parameter(1)
        public Class<? extends Exception> expectedType;

        @Parameterized.Parameter(2)
        public ThrowingRunnable executable;

        @Parameterized.Parameters(name = "{0}")
        public static Collection<Object[]> parameters() {
            return Arrays.asList(
                    new Object[]{"One expected exception", TestCheckedException.class,
                            (ThrowingRunnable) () -> synchronizer.execute(() -> {throw new TestCheckedException();},
                                    TestCheckedException.class)},
                    new Object[]{"Two expected exceptions", TestUncheckedException.class,
                            (ThrowingRunnable) () -> synchronizer.execute(() -> {throw new TestUncheckedException();},
                                    TestCheckedException.class, TestUncheckedException.class)},
                    new Object[]{"Three expected exceptions", IOException.class,
                            (ThrowingRunnable) () -> synchronizer.execute(() -> {throw new IOException();},
                                    TestCheckedException.class, TestUncheckedException.class, IOException.class)}
            );
        }

        @Test
        public void testExpectedExceptions() {
            Exception e = assertThrows(Exception.class, () -> executable.run());
            assertTrue("Expected " + expectedType.getName() + " but got " + e.getClass().getName(),
                    expectedType.isInstance(e));
        }
    }

    @RunWith(Parameterized.class)
    public static class LockAcquirerInterruptibilityTests {

        @Parameterized.Parameter(0)
        public String testName;

        @Parameterized.Parameter(1)
        public LockAcquirer<ReentrantLock> lockAcquirer;

        @Parameterized.Parameter(2)
        public Assertion assertion;

        @Parameterized.Parameters(name = "{0}")
        public static Collection<Object[]> parameters() {
            return Arrays.asList(
                    new Object[]{"usingLock",
                            LockAcquirer.usingLock(), (Assertion) Assertion::doesNotThrow},
                    new Object[]{"usingLockInterruptibly",
                            LockAcquirer.usingLockInterruptibly(),
                            (Assertion) Assertion::doesThrowInterruptedException},
                    new Object[]{"usingTryLock",
                            LockAcquirer.usingTryLock(), (Assertion) Assertion::doesNotThrow},
                    new Object[]{"usingTimedTryLock",
                            LockAcquirer.usingTimedTryLock(1, TimeUnit.SECONDS),
                            (Assertion) Assertion::doesThrowInterruptedException}
            );
        }

        @Test
        public void testLockAcquirerInterruptibility() {
            try (final ExecutorService executor = Executors.newSingleThreadExecutor()) {
                final LockAcquirerWrapper<ReentrantLock> lockAcquirerWrapper = new LockAcquirerWrapper<>(lockAcquirer);
                final ReentrantLock lock = new ReentrantLock();
                lock.lock();

                final Task<Boolean> task = new Task<>(() -> {
                    await().pollInSameThread().until(Thread.currentThread()::isInterrupted);
                    return lockAcquirerWrapper.acquire(lock);
                });
                final Future<Boolean> future = executor.submit(task);

                await().untilTrue(task.started);
                task.executionThread().interrupt();
                lock.unlock();

                assertion.apply(getExceptionalResult(future));
                assertTrue(lockAcquirerWrapper.invoked);
            }
        }

        public interface Assertion {
            void apply(ThrowingRunnable executable);

            static void doesThrowInterruptedException(final ThrowingRunnable executable) {
                assertThrows(InterruptedException.class, executable);
            }

            static void doesNotThrow(final ThrowingRunnable executable) {
                try {
                    executable.run();
                } catch (Throwable e) {
                    fail("Unexpected exception " + e);
                }
            }
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

    @RunWith(Parameterized.class)
    public static class LockAcquirerBlockabilityTests {
        static final Duration taskDuration = Duration.ofMillis(500);
        static final Duration deviation = Duration.ofMillis(150);
        static final Duration taskEvaluation = taskDuration.minus(deviation);
        static final Duration pollInterval = Duration.ofMillis(10);

        @Parameterized.Parameter(0)
        public String testName;

        @Parameterized.Parameter(1)
        public LockAcquirer<ReentrantLock> lockAcquirer;

        @Parameterized.Parameters(name = "{0}")
        public static Collection<Object[]> parameters() {
            return Arrays.asList(
                    new Object[]{"usingLock is blocked", LockAcquirer.usingLock()},
                    new Object[]{"usingLockInterruptibly is blocked", LockAcquirer.usingLockInterruptibly()},
                    new Object[]{"usingTimedLock is blocked (temporary)",
                            LockAcquirer.usingTimedTryLock(taskDuration.toNanos(), TimeUnit.NANOSECONDS)}
            );
        }

        @Test
        public void testLockAcquirerBlockability() {
            try (final ExecutorService executor = Executors.newSingleThreadExecutor()) {
                final ReentrantLock lock = new ReentrantLock();
                lock.lock();

                final Task<Boolean> task = new Task<>(() -> lockAcquirer.acquire(lock));
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
    }

    public static class IndividualTests {
        ReentrantLock commonLock;
        Synchronizer commonSynchronizer;

        @Before
        public void setUp() {
            commonLock = new ReentrantLock();
            commonSynchronizer = Synchronizer.of(commonLock, commonTimedTryLockAcquirer());
        }

        @After
        public void tearDown() {
            if (commonLock.isHeldByCurrentThread()) {
                commonLock.unlock();
            }
        }

        @Test
        public void testSynchronizerFailExecuteActionWithFallback() {
            final Action<Integer> exceptionalAction = () -> {throw new TestCheckedException();};
            final int fallbackValue = -1;
            final Supplier<Integer> fallback = () -> fallbackValue;
            assertEquals(fallbackValue, commonSynchronizer.execute(exceptionalAction, fallback).intValue());
        }

        @Test
        public void testSynchronizerExecuteActionWithFallbackInterruption() {
            try (final ExecutorService executor = Executors.newSingleThreadExecutor()) {
                final Duration actionDuration = COMMON_TIMEOUT;
                final Action<Void> interruptibleAction = Action.from((Command) () -> Thread.sleep(actionDuration.toMillis()));
                final Task<Void> task = new Task<>(() -> commonSynchronizer.execute(interruptibleAction,
                        () -> {throw new IllegalStateException("unexpected");}));
                final Future<Void> future = executor.submit(task);

                await().untilTrue(task.started);
                waitAtMost(actionDuration.dividedBy(2)).until(commonLock::isLocked);
                task.executionThread().interrupt();

                assertThrows(Synchronizer.ExecutionInterruptedException.class, getExceptionalResult(future));
                assertFalse(commonLock.isLocked());
            }
        }

        @Test
        public void testSynchronizerInterruption() {
            try (final ExecutorService executor = Executors.newSingleThreadExecutor()) {
                commonLock.lock();
                try {
                    final Task<Void> task = new Task<>(() -> commonSynchronizer.execute((Action<Void>) () -> null));
                    final Future<Void> future = executor.submit(task);

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
        public void testSynchronizerCustomTimeoutError() {
            try (final ExecutorService executor = Executors.newSingleThreadExecutor()) {
                final CountDownLatch task1Started = new CountDownLatch(1);
                final Duration extraTimeout = COMMON_TIMEOUT.plusMillis(200);
                final Synchronizer synchronizer = Synchronizer.of(new ReentrantLock(), commonTimedTryLockAcquirer(),
                        l -> new Synchronizer.LockAcquisitionException("Cannot acquire a lock", new TestUncheckedException()));

                final Runnable task1ExceedsSynchronizerTimeout = () -> synchronizer.execute((Runnable) () -> {
                    task1Started.countDown();
                    sleep(extraTimeout);
                });

                executor.execute(task1ExceedsSynchronizerTimeout);
                awaitLatch(task1Started);
                Awaitility.waitAtMost(extraTimeout)
                        .untilAsserted(() -> {
                            final Exception e = assertThrows(Synchronizer.LockAcquisitionException.class,
                                    () -> synchronizer.execute((Runnable) () -> {}));
                            final Throwable cause = e.getCause();
                            assertNotNull(cause);
                            assertTrue("Expected TestUncheckedException but got " + cause.getClass().getName(),
                                    cause instanceof TestUncheckedException);
                        });
            }
        }

        @Test
        @SneakyThrows
        public void testSynchronizerExecuteOneActionAtTime() {
            try (final ExecutorService executor = Executors.newFixedThreadPool(2)) {
                final CountDownLatch task1Started = new CountDownLatch(1);
                final CountDownLatch task1Release = new CountDownLatch(1);
                final Duration extraTimeout = COMMON_TIMEOUT.plusMillis(200);

                final Runnable task1ExceedsSynchronizerTimeout = () -> commonSynchronizer.execute((Runnable) () -> {
                    task1Started.countDown();
                    awaitLatch(task1Release, extraTimeout);
                });
                final Runnable task2UnableToExecuteDueToTimeout = () -> {
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
        public void testSynchronizerExecuteMultipleActionsSequentially() {
            try (final ExecutorService executor = Executors.newFixedThreadPool(2)) {
                final TwoThreadsTestLock testLock = new TwoThreadsTestLock();
                final Synchronizer synchronizer = Synchronizer.of(testLock, commonTimedTryLockAcquirer());

                final DurationKeeper durationKeeper = new DurationKeeper();
                final Duration taskDuration = COMMON_TIMEOUT.dividedBy(2);
                final Callable<Object> task = Executors.callable(() ->
                        synchronizer.execute((Runnable) () -> {
                            durationKeeper.toggle();
                            sleep(taskDuration);
                        }));
                final List<Callable<Object>> tasks = Arrays.asList(task, task);
                final List<Future<Object>> futures = executor.invokeAll(tasks, 3, TimeUnit.SECONDS);

                checkForExecutionException(futures);
                assertEquals("Two Threads have to acquire the lock via tryLock with timeout",
                        2, testLock.getTimedTryLockAcquisitionCount());
                assertEquals("Two threads have to release the lock",
                        2, testLock.getReleaseLockCount());
                assertEquals("One thread has to be blocked during the synchronised execution of two tasks",
                        1, testLock.getLockedThreadsCount());
                final Duration durationBetweenTasks = durationKeeper.get();
                assertTrue("The time between the execution of tasks has not to be less than the task duration.",
                        durationBetweenTasks.compareTo(taskDuration) >= 0);
            }
        }

        @Test
        public void testSynchronizerExecuteActionThrowsInterruptedException() {
            final ReentrantLock lock = new ReentrantLock();
            final Synchronizer sync = Synchronizer.of(lock, LockAcquirer.usingTryLock());
            final Action<Void> action = Action.of(() -> {
                throw new InterruptedException();
            });

            assertThrows(Synchronizer.ExecutionInterruptedException.class, () -> sync.execute(action));
            assertFalse(lock.isLocked());
            assertTrue(Thread.currentThread().isInterrupted());
        }

        @Test
        public void testSynchronizerExecuteActionThrowsExecutionInterruptedException() {
            final ReentrantLock lock = new ReentrantLock();
            final Synchronizer sync = Synchronizer.of(lock, LockAcquirer.usingTryLock());
            final Action<Void> action = Action.of(
                    () -> {
                        throw new Synchronizer.ExecutionInterruptedException(new InterruptedException());
                    });

            assertThrows(Synchronizer.ExecutionInterruptedException.class, () -> sync.execute(action));
            assertFalse(lock.isLocked());
            assertFalse(Thread.currentThread().isInterrupted());
        }

        @Test
        public void testActionThrowsLockAcquisitionExceptionIsWrapped() {
            final Synchronizer synchronizer = Synchronizer.of(new ReentrantLock(), commonTimedTryLockAcquirer());
            final Synchronizer.LockAcquisitionException innerException =
                    new Synchronizer.LockAcquisitionException("Inner lock failed");
            final Action<Void> action = Action.of(() -> {
                throw innerException;
            });

            Synchronizer.ExecutionException thrown = assertThrows(Synchronizer.ExecutionException.class,
                    () -> synchronizer.execute(action));
            assertSame("LockAcquisitionException from action should be wrapped in ExecutionException",
                    innerException, thrown.getCause());
        }

        @Test
        public void testActionThrowsExecutionExceptionIsWrapped() {
            final Synchronizer synchronizer = Synchronizer.of(new ReentrantLock(), commonTimedTryLockAcquirer());
            final IOException innerCause = new IOException("Original failure");
            final Synchronizer.ExecutionException innerException = new Synchronizer.ExecutionException(innerCause);
            final Action<Void> action = Action.of(() -> {
                throw innerException;
            });

            Synchronizer.ExecutionException thrown = assertThrows(Synchronizer.ExecutionException.class,
                    () -> synchronizer.execute(action));
            assertSame("ExecutionException from action should be wrapped in ExecutionException",
                    innerException, thrown.getCause());
            assertSame("Original cause should be preserved in the inner ExecutionException",
                    innerCause, innerException.getCause());
        }

        @Test
        public void testActionThrowsExecutionInterruptedExceptionIsNotWrapped() {
            final Synchronizer synchronizer = Synchronizer.of(new ReentrantLock(), commonTimedTryLockAcquirer());
            final Synchronizer.ExecutionInterruptedException innerException =
                    new Synchronizer.ExecutionInterruptedException(new InterruptedException());
            final Action<Void> action = Action.of(() -> {
                throw innerException;
            });

            Synchronizer.ExecutionInterruptedException thrown = assertThrows(Synchronizer.ExecutionInterruptedException.class,
                    () -> synchronizer.execute(action));
            assertSame("ExecutionInterruptedException should propagate without wrapping",
                    innerException, thrown);
        }

        @Test
        @SneakyThrows
        public void testNestedSynchronizerInnerLockFailureIsWrapped() {
            try (final ExecutorService executor = Executors.newSingleThreadExecutor()) {
                final Synchronizer outerSync = Synchronizer.of(new ReentrantLock(), LockAcquirer.usingTryLock());
                final ReentrantLock innerLock = new ReentrantLock();
                final Synchronizer innerSync = Synchronizer.of(innerLock, LockAcquirer.usingTryLock());

                final Task<Void> lockTask = new Task<>(() -> {
                    innerLock.lock();
                    return null;
                });
                executor.submit(lockTask);
                await().untilTrue(lockTask.completed);

                try {
                    Synchronizer.ExecutionException thrown = assertThrows(Synchronizer.ExecutionException.class,
                            () -> outerSync.execute((Runnable) () -> innerSync.execute((Action<?>) () -> "inner work")));
                    assertTrue("Inner lock failure should be wrapped, preventing confusion with outer lock failure",
                            thrown.getCause() instanceof Synchronizer.LockAcquisitionException);
                } finally {
                    executor.execute(innerLock::unlock);
                }
            }
        }

        @Test
        public void testNestedSynchronizerInnerExecutionFailureIsDoubleWrapped() {
            final Synchronizer outerSync = Synchronizer.of(new ReentrantLock(), LockAcquirer.usingTryLock());
            final Synchronizer innerSync = Synchronizer.of(new ReentrantLock(), LockAcquirer.usingTryLock());
            final IllegalStateException originalCause = new IllegalStateException("Actual failure");

            Synchronizer.ExecutionException thrown = assertThrows(Synchronizer.ExecutionException.class,
                    () -> outerSync.execute((Runnable) () -> innerSync.execute((Action<?>) () -> {
                        throw originalCause;
                    })));
            assertTrue("Inner ExecutionException should be wrapped by outer",
                    thrown.getCause() instanceof Synchronizer.ExecutionException);
            final Synchronizer.ExecutionException innerException = (Synchronizer.ExecutionException) thrown.getCause();
            assertSame("Original cause should be accessible via double unwrapping",
                    originalCause, innerException.getCause());
        }

        @Test
        public void testInterruptedFlagAfterNestedActionInterruption() {
            final Action<Void> interruptedAction = Action.of(() -> {throw new InterruptedException();});

            assertThrows(Synchronizer.ExecutionInterruptedException.class,
                    () -> commonSynchronizer.execute((Command) () -> {
                        throw assertThrows(Synchronizer.ExecutionInterruptedException.class,
                                () -> commonSynchronizer.execute(interruptedAction));
                    }));
            assertTrue(Thread.currentThread().isInterrupted());
        }

        @Test
        public void testSynchronizerExecuteActionReturnsNull() {
            Object result = commonSynchronizer.execute((Action<?>) () -> null);
            assertNull(result);
        }

        @Test
        public void testSynchronizerExecuteActionUsingLockAcquirer() {
            Synchronizer sync = Synchronizer.of(new ReentrantLock(), LockAcquirer.usingLock());
            Object result = sync.execute((Action<?>) () -> 5);
            assertEquals(5, result);
        }

        @Test
        public void testSynchronizerExecuteActionWithCustomAcquirerThrowsException() {
            final ReentrantLock lock = new ReentrantLock();
            final Synchronizer synchronizer = Synchronizer.of(lock, l -> {throw new TestUncheckedException();});

            assertThrows(TestUncheckedException.class, () -> synchronizer.execute((Runnable) () -> {}));
            assertFalse(lock.isLocked());
        }

        @Test
        @SneakyThrows
        public void testLockAcquirerUsingTimedTryLockIsBlockedTemporarily() {
            final Duration taskDuration = Duration.ofMillis(500);
            final LockAcquirer<ReentrantLock> lockAcquirer =
                    LockAcquirer.usingTimedTryLock(taskDuration.toNanos(), TimeUnit.NANOSECONDS);
            try (final ExecutorService executor = Executors.newSingleThreadExecutor()) {
                final ReentrantLock lock = new ReentrantLock();
                lock.lock();

                final Task<Boolean> task = new Task<>(() -> lockAcquirer.acquire(lock));
                final Future<Boolean> future = executor.submit(task);

                await().untilTrue(task.started);
                waitAtMost(taskDuration).untilTrue(task.completed);
                assertFalse(future.get());
            }
        }

        @Test
        @SneakyThrows
        public void testLockAcquirerUsingTryLockIsNonBlocking() {
            final LockAcquirer<ReentrantLock> lockAcquirer = LockAcquirer.usingTryLock();
            final ReentrantLock lock = new ReentrantLock();

            try (final ExecutorService executor = Executors.newSingleThreadExecutor()) {
                lock.lock();

                final Task<Boolean> task = new Task<>(() -> lockAcquirer.acquire(lock));
                final Future<Boolean> future = executor.submit(task);

                await().untilTrue(task.started);
                assertTrue(task.completed());

                assertFalse(future.get());
            }
        }

        @Test
        public void testUnlockNotCalledWhenLockAcquisitionFails() {
            final ReentrantLock lockSpy = spy(new ReentrantLock());
            final Synchronizer synchronizer = Synchronizer.of(lockSpy, LockAcquirer.usingTryLock());

            try (final ExecutorService executor = Executors.newSingleThreadExecutor()) {
                final Task<Boolean> task = new Task<>(lockSpy::tryLock);
                executor.submit(task);
                try {
                    await().untilTrue(task.started);
                    try {
                        synchronizer.execute((Runnable) () -> {
                        });
                        fail("Expected LockAcquisitionException");
                    } catch (Synchronizer.LockAcquisitionException expected) {
                    }
                } finally {
                    executor.execute(lockSpy::unlock);
                }

                verify(lockSpy, times(2)).tryLock();
                verify(lockSpy, times(1)).unlock();
            }
        }

        @Test
        public void testFallbackThrowsException() {
            class FallbackException extends RuntimeException {}

            final Action<Void> exceptionalAction = () -> {throw new TestCheckedException();};
            final Supplier<Void> exceptionalFallback = () -> {throw new FallbackException();};

            assertThrows(FallbackException.class,
                    () -> commonSynchronizer.execute(exceptionalAction, exceptionalFallback));
        }

        @Test
        public void testReentrantSynchronizerCallsSucceed() {
            final AtomicBoolean nestedCall = new AtomicBoolean(false);
            commonSynchronizer.execute((Runnable) () ->
                    commonSynchronizer.execute((Runnable) () -> nestedCall.set(true)));
            assertTrue(nestedCall.get());
        }
    }


    @RequiredArgsConstructor
    private static class Task<R extends @Nullable Object> implements Callable<R> {
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

        boolean started() {
            return started.get();
        }

        boolean completed() {
            return completed.get();
        }

        Thread executionThread() {
            return requireNonNull(executionThread.get());
        }
    }

    private static ThrowingRunnable getExceptionalResult(final Future<?> future) {
        return () -> {
            try {
                future.get(COMMON_TIMEOUT.toNanos(), TimeUnit.NANOSECONDS);
            } catch (ExecutionException e) {
                throw e.getCause();
            }
        };
    }

    private static void checkForExecutionException(final List<? extends Future<?>> futures) throws Exception {
        for (final Future<?> future : futures) future.get();
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
        @Nullable
        Instant start;
        @Nullable
        Duration duration;

        void toggle() {
            final Instant time = Instant.now();
            if (start == null) start = time;
            else if (duration == null) duration = Duration.between(start, time);
        }

        Duration get() {
            return requireNonNull(duration, "Duration has not been computed yet");
        }
    }

    private static class TestUncheckedException extends RuntimeException {
    }

    private static class TestCheckedException extends Exception {
    }
}