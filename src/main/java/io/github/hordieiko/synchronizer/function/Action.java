package io.github.hordieiko.synchronizer.function;

import org.jspecify.annotations.Nullable;

import java.util.concurrent.Callable;
import java.util.function.Supplier;

/// A functional interface that replaces [Supplier] and [Callable] for operations that may throw exceptions.
///
/// This interface is useful for capturing lambdas that throw checked exceptions and return a result,
/// which cannot be used directly with [Supplier].
///
/// Example:
/// ```java
/// Action<String> action = () -> {
///     // May throw IOException
///     return Files.readString(Path.of("file.txt"));
/// };
/// ```
///
/// @param <V> the type of result returned by the action
@FunctionalInterface
public interface Action<V extends @Nullable Object> {

    /// Executes the action and returns its result.
    ///
    /// @return the result of the action execution (may be null)
    /// @throws Exception if the action execution fails
    V apply() throws Exception;


    /// Converts this action to a [Callable].
    ///
    /// The resulting callable delegates to this action's [#apply()] method.
    ///
    /// @return a callable that executes this action
    default Callable<V> toCallable() {return this::apply;}

    /// Returns the given action unchanged.
    ///
    /// This identity function is useful for method references or explicit type conversion.
    ///
    /// @param <V> the type of result returned by the action
    /// @param action the action to return
    /// @return the same action instance
    static <V> Action<V> of(final Action<V> action) {return action;}

    /// Adapts a [Supplier] to an [Action].
    ///
    /// The resulting action delegates to the supplier's `get()` method
    /// and never throws checked exceptions.
    ///
    /// @param <V> the type of result returned by the supplier
    /// @param supplier the supplier to adapt
    /// @return an action that executes the supplier
    static <V> Action<V> from(final Supplier<V> supplier) {return supplier::get;}

    /// Adapts a [Runnable] to an [Action] that returns null.
    ///
    /// The resulting action executes the runnable and always returns null.
    ///
    /// @param runnable the runnable to adapt
    /// @return an action that executes the runnable and returns null
    static Action<Void> from(final Runnable runnable) {return () -> {runnable.run(); return null;};}

    /// Adapts a [Command] to an [Action] that returns null.
    ///
    /// The resulting action executes the command and always returns null.
    ///
    /// @param command the command to adapt
    /// @return an action that executes the command and returns null
    static Action<Void> from(final Command command) {return () -> {command.execute(); return null;};}

    /// Adapts a [Callable] to an [Action].
    ///
    /// The resulting action delegates to the callable's `call()` method.
    ///
    /// @param <V> the type of result returned by the callable
    /// @param callable the callable to adapt
    /// @return an action that executes the callable
    static <V> Action<V> from(final Callable<V> callable) {return callable::call;}
}