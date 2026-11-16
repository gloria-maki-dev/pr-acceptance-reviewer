package dev.gloria.pracceptance.diff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.gloria.pracceptance.ExampleFixtures;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DiffParserTest {

    private final DiffParser parser = new DiffParser();

    @Test
    @DisplayName("separa los archivos del diff y conserva su ruta")
    void separaLosArchivos() {
        ParsedDiff diff = parser.parse(ExampleFixtures.brokenDiff());

        assertThat(diff.files())
                .extracting(DiffFile::path)
                .containsExactly("src/components/CartSummary.tsx", "src/components/OrderHistory.tsx");
    }

    @Test
    @DisplayName("numera las líneas añadidas con la numeración del archivo nuevo")
    void numeraLasLineasAnadidas() {
        ParsedDiff diff = parser.parse(ExampleFixtures.brokenDiff());

        DiffFile cart = diff.file("src/components/CartSummary.tsx").orElseThrow();
        List<DiffLine> anadidas = cart.lines().stream().filter(l -> l.side() == DiffSide.ADDED).toList();

        assertThat(anadidas).hasSize(1);
        assertThat(anadidas.get(0).lineNumber()).isEqualTo(20);
        assertThat(anadidas.get(0).content()).contains("Total estimado");
    }

    @Test
    @DisplayName("numera las líneas eliminadas con la numeración del archivo antiguo")
    void numeraLasLineasEliminadas() {
        ParsedDiff diff = parser.parse(ExampleFixtures.brokenDiff());

        DiffFile cart = diff.file("src/components/CartSummary.tsx").orElseThrow();
        List<DiffLine> eliminadas = cart.lines().stream().filter(l -> l.side() == DiffSide.REMOVED).toList();

        assertThat(eliminadas).hasSize(1);
        assertThat(eliminadas.get(0).lineNumber()).isEqualTo(20);
        assertThat(eliminadas.get(0).content()).contains(">Total<");
    }

    @Test
    @DisplayName("avanza la numeración de ambos lados en las líneas de contexto")
    void numeraElContexto() {
        ParsedDiff diff = parser.parse(ExampleFixtures.brokenDiff());

        DiffFile historial = diff.file("src/components/OrderHistory.tsx").orElseThrow();
        List<DiffLine> contexto = historial.lines().stream().filter(l -> l.side() == DiffSide.CONTEXT).toList();

        assertThat(contexto).hasSize(4);
        assertThat(contexto.get(0).lineNumber()).isEqualTo(31);
        assertThat(contexto.get(1).lineNumber()).isEqualTo(33);
        assertThat(contexto.get(2).lineNumber()).isEqualTo(34);
        assertThat(contexto.get(3).lineNumber()).isEqualTo(35);
    }

    @Test
    @DisplayName("la línea añadida del historial de pedidos es la 32")
    void lineaAnadidaDelHistorial() {
        ParsedDiff diff = parser.parse(ExampleFixtures.brokenDiff());

        DiffFile historial = diff.file("src/components/OrderHistory.tsx").orElseThrow();
        DiffLine anadida = historial.lines().stream()
                .filter(l -> l.side() == DiffSide.ADDED)
                .findFirst()
                .orElseThrow();

        assertThat(anadida.lineNumber()).isEqualTo(32);
        assertThat(anadida.content()).contains("Total estimado:");
    }

    @Test
    @DisplayName("el diff corregido solo toca el resumen del carrito")
    void elDiffCorregidoSoloTocaElCarrito() {
        ParsedDiff diff = parser.parse(ExampleFixtures.fixedDiff());

        assertThat(diff.files()).extracting(DiffFile::path).containsExactly("src/components/CartSummary.tsx");
    }

    @Test
    @DisplayName("admite varios fragmentos dentro del mismo archivo")
    void admiteVariosFragmentos() {
        String raw = """
                --- a/app.ts
                +++ b/app.ts
                @@ -1,2 +1,2 @@
                 uno
                -dos
                +DOS
                @@ -20,2 +20,3 @@
                 veinte
                +nuevo
                 veintiuno
                """;

        ParsedDiff diff = parser.parse(raw);
        DiffFile file = diff.file("app.ts").orElseThrow();

        assertThat(file.lines().stream().filter(l -> l.side() == DiffSide.ADDED))
                .extracting(DiffLine::lineNumber)
                .containsExactly(2, 21);
    }

    @Test
    @DisplayName("rechaza un texto que no es un diff unificado")
    void rechazaTextoQueNoEsDiff() {
        assertThatThrownBy(() -> parser.parse("esto no es un diff\nsolo texto suelto"))
                .isInstanceOf(InvalidDiffException.class);
    }

    @Test
    @DisplayName("el texto anotado lleva archivo, lado y número de línea")
    void textoAnotado() {
        ParsedDiff diff = parser.parse(ExampleFixtures.fixedDiff());

        String anotado = diff.toAnnotatedText();

        assertThat(anotado).contains("ARCHIVO: src/components/CartSummary.tsx");
        assertThat(anotado).contains("[ADDED 20]");
        assertThat(anotado).contains("[REMOVED 20]");
        assertThat(anotado).contains("[CONTEXT 18]");
    }
}
