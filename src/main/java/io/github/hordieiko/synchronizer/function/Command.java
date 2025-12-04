package io.github.hordieiko.synchronizer.function;

/// A functional interface that replaces [Runnable] for operations that may throw exceptions.
///
/// This interface is useful for capturing lambdas that throw checked exceptions,
/// which cannot be used directly with [Runnable].
///
/// Example:
/// ```java
/// Command cmd = () -> {
///     // May throw IOException
///     Files.readString(Path.of("file.txt"));
/// };
/// ```
@FunctionalInterface
public interface Command {

    /// Executes the command operation.
    ///
    /// @throws Exception if the command execution fails
    /// @see Runnable#run()
    void execute() throws Exception;


    /// Returns the given command unchanged.
    ///
    /// This identity function is useful for method references or explicit type conversion.
    ///
    /// @param command the command to return
    /// @return the same command instance
    static Command of(final Command command) {return command;}

    /// Adapts a [Runnable] to a [Command].
    ///
    /// The resulting command delegates to the runnable's `run()` method
    /// and never throws checked exceptions.
    ///
    /// @param runnable the runnable to adapt
    /// @return a command that executes the runnable
    static Command from(final Runnable runnable) {return runnable::run;}
}
