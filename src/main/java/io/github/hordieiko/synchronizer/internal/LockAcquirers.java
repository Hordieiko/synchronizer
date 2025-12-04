package io.github.hordieiko.synchronizer.internal;

import io.github.hordieiko.synchronizer.Synchronizer.LockAcquirer;

import java.util.concurrent.locks.Lock;

/// Factory for cached [LockAcquirer] instances.
///
/// This class is internal and should not be used directly.
public final class LockAcquirers {

    private LockAcquirers() {}

    public static final LockAcquirer<?> USING_LOCK = l -> {l.lock(); return true;};

    public static final LockAcquirer<?> USING_LOCK_INTERRUPTIBLY = l -> {l.lockInterruptibly(); return true;};

    public static final LockAcquirer<?> USING_TRY_LOCK = Lock::tryLock;
}
