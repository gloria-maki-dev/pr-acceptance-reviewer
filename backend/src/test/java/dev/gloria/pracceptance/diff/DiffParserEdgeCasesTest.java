package dev.gloria.pracceptance.diff;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The diffs that are not the happy path, which is where false "Met" verdicts hide. */
class DiffParserEdgeCasesTest {

    private final DiffParser parser = new DiffParser();

    @Test
    @DisplayName("a removed line starting with \"-- \" is not mistaken for a header")
    void sqlCommentThatLooksLikeAHeader() {
        String raw = """
                diff --git a/schema.sql b/schema.sql
                --- a/schema.sql
                +++ b/schema.sql
                @@ -1,4 +1,4 @@
                 CREATE TABLE t (
                --- order number
                +-- order id
                   id INT
                """;

        DiffFile file = parser.parse(raw).file("schema.sql").orElseThrow();

        assertThat(file.lines()).hasSize(4);
        assertThat(file.lines().stream().filter(l -> l.side() == DiffSide.REMOVED))
                .singleElement()
                .satisfies(l -> assertThat(l.content()).isEqualTo("-- order number"));
        assertThat(file.lines().stream().filter(l -> l.side() == DiffSide.ADDED))
                .singleElement()
                .satisfies(l -> assertThat(l.content()).isEqualTo("-- order id"));
    }

    @Test
    @DisplayName("a deleted file after another one is not merged into it")
    void deletedFileWithoutGitHeader() {
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
    @DisplayName("a new file is numbered against the new side")
    void newFile() {
        String raw = """
                diff --git a/new.ts b/new.ts
                new file mode 100644
                --- /dev/null
                +++ b/new.ts
                @@ -0,0 +1,2 @@
                +export const a = 1;
                +export const b = 2;
                """;

        DiffFile file = parser.parse(raw).file("new.ts").orElseThrow();

        assertThat(file.lines()).extracting(DiffLine::lineNumber).containsExactly(1, 2);
    }

    @Test
    @DisplayName("the same path in two blocks is merged into one file")
    void samePathInTwoBlocks() {
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
                -coffee
                +tea
                """;

        ParsedDiff diff = parser.parse(raw);

        assertThat(diff.files()).hasSize(1);
        List<DiffLine> added =
                diff.file("x.ts").orElseThrow().lines().stream().filter(l -> l.side() == DiffSide.ADDED).toList();
        assertThat(added).extracting(DiffLine::lineNumber).containsExactly(1, 50);
    }

    @Test
    @DisplayName("a blank separator line between files is not taken as context")
    void blankLineBetweenFiles() {
        String raw = """
                --- a/one.txt
                +++ b/one.txt
                @@ -1,1 +1,1 @@
                -a
                +b

                --- a/two.txt
                +++ b/two.txt
                @@ -1,1 +1,1 @@
                -c
                +d
                """;

        ParsedDiff diff = parser.parse(raw);

        assertThat(diff.file("one.txt").orElseThrow().lines()).hasSize(2);
        assertThat(diff.file("two.txt").orElseThrow().lines()).hasSize(2);
    }

    @Test
    @DisplayName("tolerates CRLF and \"\\ No newline at end of file\"")
    void crlfAndNoTrailingNewline() {
        String raw = "--- a/a.txt\r\n+++ b/a.txt\r\n@@ -1,1 +1,1 @@\r\n-old\r\n\\ No newline at end of file\r\n+new\r\n";

        DiffFile file = parser.parse(raw).file("a.txt").orElseThrow();

        assertThat(file.lines()).extracting(DiffLine::content).containsExactly("old", "new");
    }

    @Test
    @DisplayName("unquotes the paths git escapes for non-ASCII names")
    void quotedPath() {
        String raw = """
                diff --git "a/caf\\303\\251.txt" "b/caf\\303\\251.txt"
                --- "a/caf\\303\\251.txt"
                +++ "b/caf\\303\\251.txt"
                @@ -1,1 +1,1 @@
                -one
                +two
                """;

        assertThat(parser.parse(raw).files()).extracting(DiffFile::path).containsExactly("café.txt");
    }

    @Test
    @DisplayName("a rename with changes is stored under the new path")
    void renameWithChanges() {
        String raw = """
                diff --git a/old.ts b/new.ts
                similarity index 80%
                rename from old.ts
                rename to new.ts
                --- a/old.ts
                +++ b/new.ts
                @@ -1,1 +1,1 @@
                -a
                +b
                """;

        assertThat(parser.parse(raw).files()).extracting(DiffFile::path).containsExactly("new.ts");
    }
}
