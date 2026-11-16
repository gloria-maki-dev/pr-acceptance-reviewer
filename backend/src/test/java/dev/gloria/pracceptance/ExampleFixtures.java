package dev.gloria.pracceptance;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * Lee los ejemplos de la demo desde {@code examples/}, en la raíz del repositorio.
 *
 * <p>La pantalla y las pruebas comparten los mismos archivos a propósito: si alguien
 * cambia un diff de ejemplo, estas pruebas lo notan.
 */
public final class ExampleFixtures {

    private static final Path EXAMPLES = Path.of("..", "examples");

    private ExampleFixtures() {
    }

    public static List<String> criteria() {
        return Arrays.stream(read("criterios.txt").split("\n"))
                .map(String::strip)
                .filter(line -> !line.isEmpty())
                .toList();
    }

    public static String brokenDiff() {
        return read("diff-con-fallo.diff");
    }

    public static String fixedDiff() {
        return read("diff-corregido.diff");
    }

    private static String read(String name) {
        Path path = EXAMPLES.resolve(name);
        if (!Files.exists(path)) {
            throw new IllegalStateException(
                    "No encuentro el ejemplo " + path.toAbsolutePath()
                            + ". Las pruebas del backend se ejecutan desde el directorio backend/.");
        }
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
