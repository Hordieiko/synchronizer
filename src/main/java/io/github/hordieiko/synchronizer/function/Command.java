package io.github.hordieiko.synchronizer.function;

/**
 * This interface replaces {@link Runnable} in cases when
 * execution may throw an exception.
 * <p/>
 * Useful for capturing lambdas that throw exceptions.
 */
@FunctionalInterface
public interface Command {

    /**
     * @throws Exception The exception that may be thrown
     * @see Runnable#run()
     */
    void execute() throws Exception;


    static Command of(final Command command) {return command;}

    static Command from(final Runnable runnable) {return runnable::run;}
}
