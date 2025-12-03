package io.github.hordieiko.synchronizer.function;

import org.jspecify.annotations.Nullable;

import java.util.concurrent.Callable;
import java.util.function.Supplier;

@FunctionalInterface
public interface Action<V extends @Nullable Object> {

    V apply() throws Exception;


    default Callable<V> toCallable() {return this::apply;}

    static <V> Action<V> of(final Action<V> action) {return action;}

    static <V> Action<V> from(final Supplier<V> supplier) {return supplier::get;}

    static Action<Void> from(final Runnable runnable) {return () -> {runnable.run(); return null;};}

    static Action<Void> from(final Command command) {return () -> {command.execute(); return null;};}

    static <V> Action<V> from(final Callable<V> callable) {return callable::call;}
}