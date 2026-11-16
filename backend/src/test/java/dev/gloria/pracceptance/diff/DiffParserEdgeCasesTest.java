package dev.gloria.pracceptance.diff;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Los diffs que no son el caso feliz, que es donde se esconden los falsos «Cumplido». */
class DiffParserEdgeCasesTest {

    private final DiffParser parser = new DiffParser();

    @Test
    @DisplayName("una línea eliminada que empieza por «-- » no se confunde con una cabecera")
    void comentarioSqlQueParececCabecera() {
        String raw = """
                diff --git a/schema.sql b/schema.sql
                --- a/schema.sql
                +++ b/schema.sql
                @@ -1,4 +1,4 @@
                 CREATE TABLE t (
                --- numero de pedido
                +-- identificador de pedido
                   id INT
                """;

        DiffFile file = parser.parse(raw).file("schema.sql").orElseThrow();

        assertThat(file.lines()).hasSize(4);
        assertThat(file.lines().stream().filter(l -> l.side() == DiffSide.REMOVED))
                .singleElement()
                .satisfies(l -> assertThat(l.content()).isEqualTo("-- numero de pedido"));
        assertThat(file.lines().stream().filter(l -> l.side() == DiffSide.ADDED))
                .singleElement()
                .satisfies(l -> assertThat(l.content()).isEqualTo("-- identificador de pedido"));
    }

    @Test
    @DisplayName("un archivo borrado detrás de otro no se mezcla con el anterior")
    void archivoBorradoSinCabeceraGit() {
        String raw = """
                --- a/one.txt
                +++ b/one.txt
                @@ -1,1 +1,1 @@
                -a
                +b
                --- a/gone.txt
                +++ /dev/null
                @@ -1,2 +0,0 @@
                -x
                -y
                """;

        ParsedDiff diff = parser.parse(raw);

        assertThat(diff.files()).extracting(DiffFile::path).containsExactly("one.txt", "gone.txt");
        assertThat(diff.file("one.txt").orElseThrow().lines()).hasSize(2);
        assertThat(diff.file("gone.txt").orElseThrow().lines())
                .extracting(DiffLine::content)
                .containsExactly("x", "y");
    }

    @Test
    @DisplayName("un archivo nuevo se numera desde el lado nuevo")
    void archivoNuevo() {
        String raw = """
                diff --git a/nuevo.ts b/nuevo.ts
                new file mode 100644
                --- /dev/null
                +++ b/nuevo.ts
                @@ -0,0 +1,2 @@
                +export const a = 1;
                +export const b = 2;
                """;

        DiffFile file = parser.parse(raw).file("nuevo.ts").orElseThrow();

        assertThat(file.lines()).extracting(DiffLine::lineNumber).containsExactly(1, 2);
    }

    @Test
    @DisplayName("la misma ruta en dos bloques se junta en un solo archivo")
    void mismaRutaEnDosBloques() {
        String raw = """
                diff --git a/x.ts b/x.ts
                --- a/x.ts
                +++ b/x.ts
                @@ -1,1 +1,1 @@
                -a
                +b
                diff --git a/x.ts b/x.ts
                --- a/x.ts
                +++ b/x.ts
                @@ -50,1 +50,1 @@
                -cafe
                +te
                """;

        ParsedDiff diff = parser.parse(raw);

        assertThat(diff.files()).hasSize(1);
        List<DiffLine> anadidas =
                diff.file("x.ts").orElseThrow().lines().stream().filter(l -> l.side() == DiffSide.ADDED).toList();
        assertThat(anadidas).extracting(DiffLine::lineNumber).containsExactly(1, 50);
    }

    @Test
    @DisplayName("una línea en blanco entre archivos no se cuela como línea de contexto")
    void lineaEnBlancoEntreArchivos() {
        String raw = """
                --- a/uno.txt
                +++ b/uno.txt
                @@ -1,1 +1,1 @@
                -a
                +b

                --- a/dos.txt
                +++ b/dos.txt
                @@ -1,1 +1,1 @@
                -c
                +d
                """;

        ParsedDiff diff = parser.parse(raw);

        assertThat(diff.file("uno.txt").orElseThrow().lines()).hasSize(2);
        assertThat(diff.file("dos.txt").orElseThrow().lines()).hasSize(2);
    }

    @Test
    @DisplayName("tolera CRLF y «\\ No newline at end of file»")
    void crlfYSinSaltoFinal() {
        String raw = "--- a/a.txt\r\n+++ b/a.txt\r\n@@ -1,1 +1,1 @@\r\n-viejo\r\n\\ No newline at end of file\r\n+nuevo\r\n";

        DiffFile file = parser.parse(raw).file("a.txt").orElseThrow();

        assertThat(file.lines()).extracting(DiffLine::content).containsExactly("viejo", "nuevo");
    }

    @Test
    @DisplayName("descomilla las rutas que git escapa por tener acentos")
    void rutaEntrecomillada() {
        String raw = """
                diff --git "a/caf\\303\\251.txt" "b/caf\\303\\251.txt"
                --- "a/caf\\303\\251.txt"
                +++ "b/caf\\303\\251.txt"
                @@ -1,1 +1,1 @@
                -uno
                +dos
                """;

        assertThat(parser.parse(raw).files()).extracting(DiffFile::path).containsExactly("café.txt");
    }

    @Test
    @DisplayName("un renombrado con cambios se guarda con la ruta nueva")
    void renombradoConCambios() {
        String raw = """
                diff --git a/viejo.ts b/nuevo.ts
                similarity index 80%
                rename from viejo.ts
                rename to nuevo.ts
                --- a/viejo.ts
                +++ b/nuevo.ts
                @@ -1,1 +1,1 @@
                -a
                +b
                """;

        assertThat(parser.parse(raw).files()).extracting(DiffFile::path).containsExactly("nuevo.ts");
    }
}
