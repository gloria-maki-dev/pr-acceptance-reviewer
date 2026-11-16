package dev.gloria.pracceptance;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * Reads the demo examples from {@code examples/}, at the repository root.
 *
 * <p>The screen and the tests share the same files on purpose: if someone changes an example
 * diff, these tests notice.
 */
public final class ExampleFixtures {

    private static final Path EXAMPLES = Path.of("..", "examples");

    private ExampleFixtures() {
    }

    public static List<String> criteria() {
        return Arrays.stream(read("acceptance-criteria.txt").split("\n"))
                .map(String::strip)
                .filter(line -> !line.isEmpty())
                .toList();
    }

    public static String diffWithBug() {
        return read("diff-with-bug.diff");
    }

    public static String diffFixed() {
        return read("diff-fixed.diff");
    }

    private static String read(String name) {
        Path path = EXAMPLES.resolve(name);
        if (!Files.exists(path)) {
            throw new IllegalStateException(
                    "Cannot find the example " + path.toAbsolutePath()
                            + ". The backend tests run from the backend/ directory.");
        }
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
