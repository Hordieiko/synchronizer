import org.jspecify.annotations.NullMarked;

@NullMarked
module io.github.hordieiko.synchronizer {
    requires static lombok;
    requires static org.jspecify;

    exports io.github.hordieiko.synchronizer;
    exports io.github.hordieiko.synchronizer.function;
}
