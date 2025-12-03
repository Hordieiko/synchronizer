package io.github.hordieiko.synchronizer;

import io.github.hordieiko.synchronizer.function.Action;
import io.github.hordieiko.synchronizer.function.Command;
import io.github.hordieiko.synchronizer.internal.BaseSynchronizer;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A synchronization utility for executing various actions under a lock,
 * ensuring consistent lock acquisition and proper exception handling.
 * <p>
 * <h6>Exception policy:</h6>
 * <ul>
 *     <li>{@link LockAcquisitionException} - thrown if <strong>this synchronizer's</strong> lock cannot be acquired.
 *     If the action itself throws {@code LockAcquisitionException} (e.g., from a nested synchronizer),
 *     it will be wrapped in {@link ExecutionException}.</li>
 *     <li>{@link ExecutionException} - thrown if the action execution throws an exception.
 *     All exceptions from the action are wrapped in {@code ExecutionException}, except for
 *     {@code ExecutionInterruptedException} and {@code InterruptedException} that wrapped to
 *     {@code ExecutionInterruptedException}. Check {@link ExecutionException#getCause()} to
 *     retrieve the original exception.</li>
 *     <li>{@link ExecutionInterruptedException} - thrown if the current thread is interrupted
 *     either while acquiring the lock or during action execution. This exception propagates
 *     <strong>without wrapping</strong>, even if thrown by the action, as it represents an interrupt signal.</li>
 * </ul>
 */
public interface Synchronizer {

    /**
     * Executes an {@link Action} under the lock.
     *
     * @param action the action to execute
     * @param <R>    the type of the result
     * @return the result of the action
     * @throws LockAcquisitionException      if the lock cannot be acquired
     * @throws ExecutionException            if the action execution throws an exception
     * @throws ExecutionInterruptedException if the action execution or lock acquisition was interrupted
     */
    <R extends @Nullable Object>
    R execute(final Action<R> action) throws LockAcquisitionException, ExecutionException, ExecutionInterruptedException;

    /**
     * Executes a {@link Runnable} under the lock.
     *
     * @param runnable the action to execute
     * @throws LockAcquisitionException      if the lock cannot be acquired
     * @throws ExecutionException            if the action execution throws an exception
     * @throws ExecutionInterruptedException if the action execution or lock acquisition was interrupted
     */
    void execute(final Runnable runnable) throws LockAcquisitionException, ExecutionException, ExecutionInterruptedException;

    /**
     * Executes a {@link Supplier} under the lock.
     *
     * @param supplier the action to execute
     * @param <R>      the type of the result
     * @return the result of the supplier
     * @throws LockAcquisitionException      if the lock cannot be acquired
     * @throws ExecutionException            if the action execution throws an exception
     * @throws ExecutionInterruptedException if the action execution or lock acquisition was interrupted
     */
    <R extends @Nullable Object>
    R execute(final Supplier<R> supplier) throws LockAcquisitionException, ExecutionException, ExecutionInterruptedException;

    /**
     * Executes a {@link Command} under the lock.
     *
     * @param command the action to execute
     * @throws LockAcquisitionException      if the lock cannot be acquired
     * @throws ExecutionException            if the action execution throws an exception
     * @throws ExecutionInterruptedException if the action execution or lock acquisition was interrupted
     */
    void execute(final Command command) throws LockAcquisitionException, ExecutionException, ExecutionInterruptedException;

    /**
     * Executes a {@link Callable} under the lock.
     *
     * @param callable the action to execute
     * @param <R>      the type of the result
     * @return the result of the callable
     * @throws LockAcquisitionException      if the lock cannot be acquired
     * @throws ExecutionException            if the action execution throws an exception
     * @throws ExecutionInterruptedException if the action execution or lock acquisition was interrupted
     */
    <R extends @Nullable Object>
    R execute(final Callable<R> callable) throws LockAcquisitionException, ExecutionException, ExecutionInterruptedException;

    /**
     * Executes an {@link Action} under the lock, providing a fallback in case of failure.
     *
     * @param action   the primary action
     * @param fallback the fallback supplier to call if execution fails (excluding interruption)
     * @param <R>      the type of the result
     * @return the result of the primary action, or the fallback if execution failed
     * @throws ExecutionInterruptedException if the action execution or lock acquisition was interrupted
     */
    <R extends @Nullable Object>
    R execute(final Action<R> action, final Supplier<R> fallback) throws ExecutionInterruptedException;

    /**
     * Executes an {@link Action} under the lock.
     * <p>
     * If the action fails with a {@link ExecutionException} whose cause is of type {@code X},
     * then the exception of that type will be thrown instead.
     *
     * @param action the action to execute
     * @param x1Type the expected exception
     * @param <R>    the type of the result
     * @param <X1>   the type of the expected exception
     * @return the result of the action
     * @throws LockAcquisitionException      if the lock cannot be acquired
     * @throws ExecutionException            if the action execution throws an exception that is not of type {@code X}
     * @throws ExecutionInterruptedException if the action execution or lock acquisition was interrupted
     * @throws X1                            if the action execution throws an exception of type {@code X}
     */
    <R extends @Nullable Object, X1 extends Exception>
    R execute(final Action<R> action, final Class<X1> x1Type)
            throws LockAcquisitionException, ExecutionException, ExecutionInterruptedException, X1;

    /**
     * Executes an {@link Action} under the lock.
     * <p>
     * If the action fails with a {@link ExecutionException} whose cause is of type {@code X1} or {@code X2},
     * then the exception of that type will be thrown instead.
     *
     * @param action the action to execute
     * @param x1Type the expected exception
     * @param x2Type the expected exception
     * @param <R>    the type of the result
     * @param <X1>   the type of the expected exception
     * @param <X2>   the type of the expected exception
     * @return the result of the action
     * @throws LockAcquisitionException      if the lock cannot be acquired
     * @throws ExecutionException            if the action execution throws an exception
     *                                       that is not of type {@code X1} or {@code X2}
     * @throws ExecutionInterruptedException if the action execution or lock acquisition was interrupted
     * @throws X1                            if the action execution throws an exception of type {@code X1}
     * @throws X2                            if the action execution throws an exception of type {@code X2}
     */
    <R extends @Nullable Object, X1 extends Exception, X2 extends Exception>
    R execute(final Action<R> action, final Class<X1> x1Type, final Class<X2> x2Type)
            throws LockAcquisitionException, ExecutionException, ExecutionInterruptedException, X1, X2;

    /**
     * Executes an {@link Action} under the lock.
     * <p>
     * If the action fails with a {@link ExecutionException} whose cause is of type {@code X1, X2} or {@code X3},
     * then the exception of that type will be thrown instead.
     *
     * @param action the action to execute
     * @param x1Type the expected exception
     * @param x2Type the expected exception
     * @param x3Type the expected exception
     * @param <R>    the type of the result
     * @param <X1>   the type of the expected exception
     * @param <X2>   the type of the expected exception
     * @param <X3>   the type of the expected exception
     * @return the result of the action
     * @throws LockAcquisitionException      if the lock cannot be acquired
     * @throws ExecutionException            if the action execution throws an exception
     *                                       that is not of type {@code X1}, {@code X2}, or {@code X3}
     * @throws ExecutionInterruptedException if the action execution or lock acquisition was interrupted
     * @throws X1                            if the action execution throws an exception of type {@code X1}
     * @throws X2                            if the action execution throws an exception of type {@code X2}
     * @throws X3                            if the action execution throws an exception of type {@code X3}
     */
    <R extends @Nullable Object, X1 extends Exception, X2 extends Exception, X3 extends Exception>
    R execute(final Action<R> action, final Class<X1> x1Type, final Class<X2> x2Type, final Class<X3> x3Type)
            throws LockAcquisitionException, ExecutionException, ExecutionInterruptedException, X1, X2, X3;

    /**
     * Creates a new {@link Synchronizer} for the given lock and lock acquirer.
     *
     * @param lock         the lock to acquire before executing actions
     * @param lockAcquirer the strategy for acquiring the lock
     * @param <L>          type of lock
     * @return a new synchronizer
     */
    static <L extends Lock> Synchronizer of(final L lock, final LockAcquirer<L> lockAcquirer) {
        return new BaseSynchronizer<>(lock, lockAcquirer, _ -> new LockAcquisitionException());
    }

    /**
     * Creates a new {@link Synchronizer} for the given lock and lock acquirer with a custom
     * exception factory.
     *
     * @param lock                            the lock to acquire
     * @param lockAcquirer                    the strategy for acquiring the lock
     * @param lockAcquisitionExceptionFactory function to create an exception if the lock cannot be acquired
     * @param <L>                             type of lock
     * @return a new synchronizer
     */
    static <L extends Lock, X extends LockAcquisitionException>
    Synchronizer of(final L lock, final LockAcquirer<L> lockAcquirer, final Function<L, X> lockAcquisitionExceptionFactory) {
        return new BaseSynchronizer<>(lock, lockAcquirer, lockAcquisitionExceptionFactory);
    }


    /**
     * Represents a strategy for acquiring a lock.
     *
     * @param <L> type of lock
     */
    @FunctionalInterface
    interface LockAcquirer<L extends Lock> {

        /**
         * Attempts to acquire the lock.
         *
         * @param lock the lock to acquire
         * @return true if acquired, false otherwise
         * @throws InterruptedException if the current thread is interrupted
         */
        boolean acquire(L lock) throws InterruptedException;


        /**
         * Creates a {@link LockAcquirer} that acquires the lock using {@link Lock#lock()},
         * which is a blocking, non-interruptible call.
         *
         * @param <L> the type of lock
         * @return a blocking, non-interruptible lock acquirer
         */
        static <L extends Lock> LockAcquirer<L> usingLock() {
            return l -> {l.lock(); return true;};
        }

        /**
         * Creates a {@link LockAcquirer} that acquires the lock using {@link Lock#lockInterruptibly()},
         * which is a blocking, interruptible call.
         *
         * @param <L> the type of lock
         * @return a blocking, interruptible lock acquirer
         */
        static <L extends Lock> LockAcquirer<L> usingLockInterruptibly() {
            return l -> {l.lockInterruptibly(); return true;};
        }

        /**
         * Creates a {@link LockAcquirer} that acquires the lock using {@link Lock#tryLock()},
         * which attempts to acquire the lock immediately and returns false if the lock
         * is not available.
         *
         * @param <L> the type of lock
         * @return a non-blocking, non-interruptible, immediate lock acquirer
         */
        static <L extends Lock> LockAcquirer<L> usingTryLock() {
            return Lock::tryLock;
        }

        /**
         * Creates a {@link LockAcquirer} that attempts to acquire the lock for a limited time
         * using {@link Lock#tryLock(long, TimeUnit)}, which is a timed, interruptible lock attempt.
         *
         * @param time the maximum time to wait for the lock
         * @param unit the time unit of the {@code time} argument
         * @param <L>  the type of lock
         * @return a timed, interruptible lock acquirer
         */
        static <L extends Lock> LockAcquirer<L> usingTimedTryLock(final long time, final TimeUnit unit) {
            return l -> l.tryLock(time, unit);
        }
    }

    /**
     * Designed to be thrown when a lock cannot be acquired.
     */
    final class LockAcquisitionException extends RuntimeException {
        public LockAcquisitionException() {super();}

        public LockAcquisitionException(final Throwable cause) {super(cause);}

        public LockAcquisitionException(final String message) {super(message);}

        public LockAcquisitionException(final String message, final Throwable cause) {super(message, cause);}
    }

    /**
     * Designed to be thrown when an action execution fails.
     */
    final class ExecutionException extends RuntimeException {
        public ExecutionException(final Throwable cause) {super(cause);}
    }

    /**
     * Designed to be thrown when an action execution or lock acquisition is interrupted.
     */
    final class ExecutionInterruptedException extends RuntimeException {
        public ExecutionInterruptedException(final InterruptedException cause) {super(cause);}

        @Override
        public InterruptedException getCause() {
            return (InterruptedException) super.getCause();
        }
    }
}
