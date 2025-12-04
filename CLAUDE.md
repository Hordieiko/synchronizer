# Synchronizer Project

A Java synchronization utility library for executing actions under locks with consistent lock acquisition and exception handling.

---

## 📝 Documentation Maintenance (Instructions for Claude Code)

**IMPORTANT**: When making changes to this project, automatically update this CLAUDE.md file if any of the following occur:

### Always Update When:
- Adding/removing/upgrading dependencies in `pom.xml`
- Changing build commands or Maven configuration
- Modifying module structure (`module-info.java` exports/requires)
- Introducing new code conventions or patterns
- Adding new test patterns or helper utilities
- Changing project structure (new packages, file reorganization)
- Updating Java version or tooling paths

### How to Update:
1. After making code changes that affect the above, immediately update the relevant section(s) in CLAUDE.md
2. Keep the existing structure and formatting
3. Be concise - add only what's necessary

### Custom Commands:
- Custom Claude Code commands are defined as `.md` files in `.claude/commands/` directory
- Each command file contains the prompt/instructions for that command

### Git Commit Messages:
- **DO NOT** include Claude Code mentions in commit messages
- **DO NOT** add "🤖 Generated with [Claude Code]" footer
- **DO NOT** add "Co-Authored-By: Claude <noreply@anthropic.com>"
- Keep commit messages professional and focused on the changes made

### Sections to Monitor:
- **Build & Test** → Maven commands, Java version, build configuration
- **Project Structure** → Module system, package layout
- **Code Conventions** → Null-safety, Lombok usage, test patterns
- **Key Dependencies** → Production and testing dependencies with versions
- **API Design Principles** → Core design decisions
- **Common Patterns** → Usage examples

---

## Build & Test

### Commands
- `mvn clean compile` - Compile with module system support
- `mvn test` - Run JUnit 6 and JUnit 4 tests (92 tests total)
- `mvn clean package` - Build distributable JAR
- `mvn clean install` - Install to local Maven repository

### Java Version
- **Target**: Java 25
- **Requirement**: Set `JAVA_HOME` environment variable to JDK 25 installation path

### Maven Configuration Notes
- **Byte Buddy compatibility**: Surefire configured with `-Dnet.bytebuddy.experimental=true` for Mockito support in Java 25
- **Module path**: Surefire uses `<useModulePath>false</useModulePath>` to run tests on classpath (required for JUnit 4 via junit-vintage-engine)
- **Test discovery**: Default Surefire patterns apply (`**/*Test.java`, `**/*Tests.java`)
- **JUnit 4 support**: Included for backward compatibility with older projects

## Project Structure

### Module System
- **Module name**: `io.github.hordieiko.synchronizer`
- **Exports**:
  - `io.github.hordieiko.synchronizer` - Main API (Synchronizer interface)
  - `io.github.hordieiko.synchronizer.function` - Functional interfaces (Action, Command)
- **Requires**:
  - `lombok` (static) - Annotation processor
  - `org.jspecify` (static) - Null-safety annotations

### Package Layout
- `src/main/java/io/github/hordieiko/synchronizer/` - Main API
  - `Synchronizer.java` - Core interface with factory methods
  - `module-info.java` - Module descriptor with @NullMarked
- `src/main/java/io/github/hordieiko/synchronizer/function/` - Functional interfaces
  - `Action.java` - Functional interface for actions that throw exceptions
  - `Command.java` - Functional interface for void operations with exceptions
- `src/main/java/io/github/hordieiko/synchronizer/internal/` - Internal implementation (not exported)
  - `BaseSynchronizer.java` - Default implementation of Synchronizer interface
  - `LockAcquirers.java` - Factory for cached LockAcquirer instances (performance optimization)
- `src/test/java/hordieiko/` - Test suite
  - `SynchronizerTest.java` - Comprehensive test coverage
  - `package-info.java` - Package-level @NullMarked for tests

## Code Conventions

### General Coding Style
- **Prefer modern Java features**: Use Java 21+ features (pattern matching, switch expressions, records, sealed classes) over legacy approaches
- **Self-documenting code**: Use descriptive names for classes, methods, variables, and parameters
- **Avoid unnecessary comments**:
  - ❌ Don't write comments that repeat what the code does
  - ❌ Don't add comments to explain poorly named code - rename it instead
  - ✅ Only add comments when explaining *why* something is done (business logic, non-obvious decisions)
  - ✅ Documentation comments for public APIs are acceptable
- **Modern over legacy**: Prefer `var`/`final var` over explicit types when type is obvious, pattern matching over `instanceof` chains, switch expressions over if-else chains

### Documentation Comments (JEP 467)
Use **Markdown documentation comments** (`///`) instead of traditional HTML JavaDoc (`/** */`):

- **Syntax**: Each line begins with `///` instead of `/** ... */`
- **Paragraphs**: Blank lines indicate paragraph breaks (no `<p>` needed)
- **Lists**: Use `-` for bullet lists (replaces `<ul>` and `<li>`)
- **Emphasis**: Use `_text_` for emphasis (replaces `<em>`)
- **Code**: Use backticks `` `code` `` for inline code (replaces `{@code ...}`)
- **Links to elements**: Use `[element]` syntax (replaces `{@link ...}`)
  - Simple link: `[List]` or `[java.util.List]` (text derived from element, monospace font)
  - Custom text: `[a list][List]` (custom text in current font, replaces `{@linkplain ...}`)
  - Module: `[java.base/]`
  - Package: `[java.util]`
  - Class: `[String]`
  - Field: `[String#CASE_INSENSITIVE_ORDER]`
  - Method: `[String#chars()]`
- **Block tags**: Standard tags like `@param`, `@return`, `@see`, `@implSpec` still work (content is Markdown)
- **Inline tags**: Tags like `{@inheritDoc}` still work

Example:
```java
/// Factory for cached [LockAcquirer] instances.
///
/// This class is internal and should not be used directly.
public final class LockAcquirers { }
```

### Null-Safety (JSpecify)
- **Module-level @NullMarked**: All main code is non-null by default (see `module-info.java`)
- **Package-level @NullMarked**: All test code is non-null by default (see `hordieiko/package-info.java`)
- **Use @Nullable explicitly**: For method return types or parameters that can be null
- **Generic types**: Use `<R extends @Nullable Object>` for nullable generic returns

### Lombok Usage
- `@SneakyThrows` - For checked exceptions in tests
- `@RequiredArgsConstructor` - For dependency injection (BaseSynchronizer, test helpers)
- **Avoid `@val` in main code** - Prefer Java `final var` or `var` if it could/should be changed

### Modern Java Patterns
- **Switch pattern matching (Java 21+)**: Prefer switch expressions with guards for exception type checking
  ```java
  switch (e.getCause()) {
      case Exception ex when condition(ex) -> handleException(ex);
      default -> rethrow(e);
  }
  ```
- **Pattern matching benefits**: Built-in type safety, null handling, cleaner control flow

### Performance Optimizations
- **LockAcquirer caching**: Factory methods (`usingLock()`, `usingLockInterruptibly()`, `usingTryLock()`) return cached instances from `LockAcquirers` to avoid repeated lambda allocation
- **Internal package hiding**: Cache implementations hidden in non-exported `internal` package

### Exception Handling
- `LockAcquisitionException` - When THIS synchronizer's lock cannot be acquired
- `ExecutionException` - When action execution fails (wraps cause)
  - **Wrapping behavior**: All action exceptions are wrapped in `ExecutionException`, including `LockAcquisitionException` and `ExecutionException` from nested synchronizers
  - **Exception**: `ExecutionInterruptedException` propagates unwrapped (it's an interrupt signal)
- `ExecutionInterruptedException` - When interrupted during execution or lock acquisition
  - Always propagates without wrapping, even if thrown by action code

### Test Patterns
- **Nested test classes**: Group related tests (e.g., `NormalExecutionTests`, `ExceptionalExecutionTests`, `ActionThrowsSynchronizerExceptionTests`)
- **Parameterized tests**: Use `@ParameterizedTest` with `@MethodSource` for testing multiple scenarios
- **Test isolation**: Each nested class has its own static final `Synchronizer`
- **Async testing**: Use Awaitility for concurrent behavior verification
- **Multi-threaded lock testing**: Use executor services to lock from different threads (prevents reentrant lock success)

## Key Dependencies

### Production
- **Lombok 1.18.36** (provided) - Code generation via annotation processing
- **JSpecify 1.0.0** (provided) - Null-safety annotations (@NullMarked, @Nullable)

### Testing
- **JUnit Jupiter 6.0.0** - JUnit 6 test framework
- **JUnit 4.13.2** - JUnit 4 for backward compatibility with older projects
- **JUnit Vintage Engine 6.0.0** - Runs JUnit 4 tests on JUnit Platform
- **Mockito 5.14.2** - Mocking framework (experimental Java 25 support)
- **Awaitility 4.2.2** - Async/concurrent test utilities

## API Design Principles

1. **Lock abstraction**: Support any `java.util.concurrent.locks.Lock` implementation
2. **Flexible lock acquisition**: `LockAcquirer` strategy pattern (blocking, timed, interruptible)
3. **Multiple action types**: Support Runnable, Supplier, Callable, Command, Action
4. **Exception transparency**: Typed exception handling with `execute(action, X1.class, X2.class, ...)`
5. **Exception wrapping clarity**: Infrastructure exceptions from actions are wrapped to distinguish from synchronizer failures
   - `LockAcquisitionException` at top level = THIS synchronizer's lock failed
   - `ExecutionException` wrapping `LockAcquisitionException` = action (e.g., nested sync) threw it
   - `ExecutionInterruptedException` never wrapped = interrupt signal propagates immediately
6. **Fallback support**: `execute(action, fallback)` for graceful degradation
7. **Thread-safety**: Proper lock cleanup in finally blocks
8. **Interrupt handling**: Restore interrupt flag and throw ExecutionInterruptedException

## Common Patterns

### Basic usage
```java
void main() {
    var sync = Synchronizer.of(new ReentrantLock(), LockAcquirer.usingTimedTryLock(1, TimeUnit.SECONDS));
    // Execute with automatic lock management
    sync.execute(() -> {
        // Your synchronized code
        return 42;
    });
}
```

### With typed exception handling
```java
void main() throws IOException, SQLException {
    var sync = Synchronizer.of(new ReentrantLock(), LockAcquirer.usingTimedTryLock(1, TimeUnit.SECONDS));
    sync.execute(() -> {
        // May throw IOException or SQLException
        return performOperation();
    }, IOException.class, SQLException.class);
}
```

### With fallback

```java
void main() {
    var sync = Synchronizer.of(new ReentrantLock(), LockAcquirer.usingTimedTryLock(1, TimeUnit.SECONDS));
    var action = (Action<Integer>) () -> riskyOperation();
    // Called on any failure except interruption
    var fallback = (Supplier<Integer>) () -> defaultValue();
    sync.execute(action, fallback);
}
```

### Nested synchronizers with clear exception semantics
```java
void main() {
    var outer = Synchronizer.of(new ReentrantLock(), LockAcquirer.usingTryLock());
    var inner = Synchronizer.of(new ReentrantLock(), LockAcquirer.usingTryLock());
    try {
        outer.execute(() -> inner.execute(() -> work()));
    } catch (LockAcquisitionException e) {
        // OUTER lock failed
    } catch (ExecutionException e) {
        if (e.getCause() instanceof LockAcquisitionException) {
            // INNER lock failed (wrapped to prevent ambiguity)
        }
    } catch (ExecutionInterruptedException e) {
        // Interrupted (could be outer or inner, but propagates immediately)
    }    
}       
```

## Notes
- **Reentrant locks**: Synchronizer supports re-entrant calls (nested execute())
- **Lock cleanup**: Always unlocks in finally block, even on exception
- **Custom exceptions**: Use factory pattern for custom LockAcquisitionException messages
- **Performance**: Minimal overhead - direct delegation to lock and action
