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
    @DisplayName("separates the files of the diff and keeps their paths")
    void separatesFiles() {
        ParsedDiff diff = parser.parse(ExampleFixtures.diffWithBug());

        assertThat(diff.files())
                .extracting(DiffFile::path)
                .containsExactly("src/components/CartSummary.tsx", "src/components/OrderHistory.tsx");
    }

    @Test
    @DisplayName("numbers added lines against the new file")
    void numbersAddedLines() {
        ParsedDiff diff = parser.parse(ExampleFixtures.diffWithBug());

        DiffFile cart = diff.file("src/components/CartSummary.tsx").orElseThrow();
        List<DiffLine> added = cart.lines().stream().filter(l -> l.side() == DiffSide.ADDED).toList();

        assertThat(added).hasSize(1);
        assertThat(added.get(0).lineNumber()).isEqualTo(20);
        assertThat(added.get(0).content()).contains("Estimated total");
    }

    @Test
    @DisplayName("numbers removed lines against the old file")
    void numbersRemovedLines() {
        ParsedDiff diff = parser.parse(ExampleFixtures.diffWithBug());

        DiffFile cart = diff.file("src/components/CartSummary.tsx").orElseThrow();
        List<DiffLine> removed = cart.lines().stream().filter(l -> l.side() == DiffSide.REMOVED).toList();

        assertThat(removed).hasSize(1);
        assertThat(removed.get(0).lineNumber()).isEqualTo(20);
        assertThat(removed.get(0).content()).contains(">Total<");
    }

    @Test
    @DisplayName("advances both sides on context lines")
    void numbersContext() {
        ParsedDiff diff = parser.parse(ExampleFixtures.diffWithBug());

        DiffFile history = diff.file("src/components/OrderHistory.tsx").orElseThrow();
        List<DiffLine> context = history.lines().stream().filter(l -> l.side() == DiffSide.CONTEXT).toList();

        assertThat(context).hasSize(4);
        assertThat(context).extracting(DiffLine::lineNumber).containsExactly(31, 33, 34, 35);
    }

    @Test
    @DisplayName("the added line in order history is line 32")
    void addedLineInOrderHistory() {
        ParsedDiff diff = parser.parse(ExampleFixtures.diffWithBug());

        DiffLine added = diff.file("src/components/OrderHistory.tsx").orElseThrow().lines().stream()
                .filter(l -> l.side() == DiffSide.ADDED)
                .findFirst()
                .orElseThrow();

        assertThat(added.lineNumber()).isEqualTo(32);
        assertThat(added.content()).contains("Estimated total:");
    }

    @Test
    @DisplayName("the fixed diff only touches the cart summary")
    void fixedDiffTouchesOnlyTheCart() {
        ParsedDiff diff = parser.parse(ExampleFixtures.diffFixed());

        assertThat(diff.files()).extracting(DiffFile::path).containsExactly("src/components/CartSummary.tsx");
    }

    @Test
    @DisplayName("handles several hunks in the same file")
    void handlesSeveralHunks() {
        String raw = """
                --- a/app.ts
                +++ b/app.ts
                @@ -1,2 +1,2 @@
                 one
                -two
                +TWO
                @@ -20,2 +20,3 @@
                 twenty
                +new
                 twentyone
                """;

        ParsedDiff diff = parser.parse(raw);
        DiffFile file = diff.file("app.ts").orElseThrow();

        assertThat(file.lines().stream().filter(l -> l.side() == DiffSide.ADDED))
                .extracting(DiffLine::lineNumber)
                .containsExactly(2, 21);
    }

    @Test
    @DisplayName("rejects text that is not a unified diff")
    void rejectsTextThatIsNotADiff() {
        assertThatThrownBy(() -> parser.parse("this is not a diff\njust loose text"))
                .isInstanceOf(InvalidDiffException.class);
    }

    @Test
    @DisplayName("the annotated text carries file, side and line number")
    void annotatedText() {
        ParsedDiff diff = parser.parse(ExampleFixtures.diffFixed());

        String annotated = diff.toAnnotatedText();

        assertThat(annotated).contains("FILE: src/components/CartSummary.tsx");
        assertThat(annotated).contains("[ADDED 20]");
        assertThat(annotated).contains("[REMOVED 20]");
        assertThat(annotated).contains("[CONTEXT 18]");
    }
}
