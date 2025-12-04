package io.github.hordieiko.synchronizer.internal;

import io.github.hordieiko.synchronizer.Synchronizer;
import io.github.hordieiko.synchronizer.function.Action;
import io.github.hordieiko.synchronizer.function.Command;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.Callable;
import java.util.concurrent.locks.Lock;
import java.util.function.Function;
import java.util.function.Supplier;

/// Base implementation of [Synchronizer].
///
/// This class is internal and should not be used directly.
@RequiredArgsConstructor
public final class BaseSynchronizer<L extends Lock> implements Synchronizer {

    private final L lock;
    private final LockAcquirer<L> lockAcquirer;
    private final Function<L, ? extends LockAcquisitionException> lockAcquisitionExceptionFactory;

    @Override
    public <R> R execute(final Action<R> action) throws LockAcquisitionException, ExecutionException, ExecutionInterruptedException {
        try {
            if (!lockAcquirer.acquire(lock))
                throw lockAcquisitionExceptionFactory.apply(lock);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExecutionInterruptedException(e);
        }

        try {
            return action.apply();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExecutionInterruptedException(e);
        } catch (ExecutionInterruptedException e) {
            throw e;
        } catch (Exception e) {
            throw new ExecutionException(e);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void execute(final Runnable runnable) throws LockAcquisitionException, ExecutionException, ExecutionInterruptedException {
        execute(Action.from(runnable));
    }

    @Override
    public <R> R execute(final Supplier<R> supplier) throws LockAcquisitionException, ExecutionException, ExecutionInterruptedException {
        return execute(Action.from(supplier));
    }

    @Override
    public void execute(final Command command) throws LockAcquisitionException, ExecutionException, ExecutionInterruptedException {
        execute(Action.from(command));
    }

    @Override
    public <R> R execute(final Callable<R> callable) throws LockAcquisitionException, ExecutionException, ExecutionInterruptedException {
        return execute(Action.from(callable));
    }

    @Override
    public <R> R execute(final Action<R> action, final Supplier<R> fallback) throws ExecutionInterruptedException {
        try {
            return execute(action);
        } catch (ExecutionInterruptedException e) {
            throw e;
        } catch (Exception e) {
            return fallback.get();
        }
    }

    @Override
    public <R, X1 extends Exception> R execute(final Action<R> action, final Class<X1> x1Type)
            throws LockAcquisitionException, ExecutionException, ExecutionInterruptedException, X1 {
        return executeWithExpectedException(action, x1Type, null, null);
    }

    @Override
    public <R, X1 extends Exception, X2 extends Exception>
    R execute(final Action<R> action, final Class<X1> x1Type, final Class<X2> x2Type)
            throws LockAcquisitionException, ExecutionException, ExecutionInterruptedException, X1, X2 {
        return executeWithExpectedException(action, x1Type, x2Type, null);
    }

    @Override
    public <R, X1 extends Exception, X2 extends Exception, X3 extends Exception>
    R execute(final Action<R> action, final Class<X1> x1Type, final Class<X2> x2Type, final Class<X3> x3Type)
            throws LockAcquisitionException, ExecutionException, ExecutionInterruptedException, X1, X2, X3 {
        return executeWithExpectedException(action, x1Type, x2Type, x3Type);
    }

    <R, X1 extends Exception, X2 extends Exception, X3 extends Exception>
    R executeWithExpectedException(final Action<R> action,
                                   @Nullable final Class<X1> x1Type,
                                   @Nullable final Class<X2> x2Type,
                                   @Nullable final Class<X3> x3Type)
            throws LockAcquisitionException, ExecutionException, ExecutionInterruptedException, X1, X2, X3 {
        try {
            return execute(action);
        } catch (ExecutionException e) {
            switch (e.getCause()) {
                case Exception cause when isInstance(x1Type, cause) -> throw x1Type.cast(cause);
                case Exception cause when isInstance(x2Type, cause) -> throw x2Type.cast(cause);
                case Exception cause when isInstance(x3Type, cause) -> throw x3Type.cast(cause);
                default -> throw e;
            }
        }
    }

    static <X extends Exception> boolean isInstance(final @Nullable Class<X> expected, final Exception actual) {
        return expected != null && expected.isInstance(actual);
    }
}
