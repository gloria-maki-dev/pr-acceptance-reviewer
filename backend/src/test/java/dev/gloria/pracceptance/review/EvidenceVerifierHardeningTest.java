package dev.gloria.pracceptance.review;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gloria.pracceptance.diff.DiffParser;
import dev.gloria.pracceptance.diff.DiffSide;
import dev.gloria.pracceptance.diff.ParsedDiff;
import dev.gloria.pracceptance.model.ModelVerdict;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The citations that used to slip through, and the correct ones that used to be rejected. */
class EvidenceVerifierHardeningTest {

    private final EvidenceVerifier verifier = new EvidenceVerifier();
    private final DiffParser parser = new DiffParser();

    private static ModelVerdict met(Evidence evidence) {
        return new ModelVerdict(ReviewStatus.MET, "The explanation.", evidence);
    }

    @Test
    @DisplayName("a one-character fragment cannot support a verdict")
    void trivialFragment() {
        ParsedDiff diff = parser.parse("--- a/a.ts\n+++ b/a.ts\n@@ -1,2 +1,2 @@\n x\n+const x = 2;\n");

        CriterionResult result =
                verifier.verify("Criterion", met(new Evidence("a.ts", DiffSide.ADDED, 2, ";")), diff);

        assertThat(result.status()).isEqualTo(ReviewStatus.NOT_VERIFIED);
        assertThat(result.verificationNote()).contains("too short");
    }

    @Test
    @DisplayName("a citation to the second appearance of the same path is verified")
    void secondAppearanceOfTheSamePath() {
        ParsedDiff diff = parser.parse("""
                diff --git a/x.ts b/x.ts
                --- a/x.ts
                +++ b/x.ts
                @@ -1,1 +1,1 @@
                -coffee with milk
                +tea with lemon
                diff --git a/x.ts b/x.ts
                --- a/x.ts
                +++ b/x.ts
                @@ -50,1 +50,1 @@
                -orange juice
                +sparkling water
                """);

        CriterionResult result = verifier.verify(
                "Criterion", met(new Evidence("x.ts", DiffSide.ADDED, 50, "sparkling water")), diff);

        assertThat(result.status()).isEqualTo(ReviewStatus.MET);
        assertThat(result.verificationNote()).isNull();
    }

    @Test
    @DisplayName("tolerates the model copying the line with the prompt's annotation")
    void citationWithThePromptAnnotation() {
        ParsedDiff diff = parser.parse("--- a/a.ts\n+++ b/a.ts\n@@ -1,1 +1,1 @@\n-old value\n+new value\n");

        CriterionResult result = verifier.verify(
                "Criterion", met(new Evidence("a.ts", DiffSide.ADDED, 1, "[ADDED 1] new value")), diff);

        assertThat(result.status()).isEqualTo(ReviewStatus.MET);
    }

    @Test
    @DisplayName("a no-break space does not invalidate a literal citation")
    void noBreakSpace() {
        ParsedDiff diff = parser.parse(
                "--- a/a.tsx\n+++ b/a.tsx\n@@ -1,1 +1,1 @@\n-<span>Total</span>\n+<span>Estimated total</span>\n");

        CriterionResult result = verifier.verify(
                "Criterion", met(new Evidence("a.tsx", DiffSide.ADDED, 1, "<span>Estimated total</span>")), diff);

        assertThat(result.status()).isEqualTo(ReviewStatus.MET);
    }

    @Test
    @DisplayName("a citation to the CONTEXT side is verified too")
    void contextSide() {
        ParsedDiff diff = parser.parse(
                "--- a/a.ts\n+++ b/a.ts\n@@ -1,2 +1,2 @@\n export const config = {\n-  mode: 'old'\n+  mode: 'new'\n");

        CriterionResult result = verifier.verify(
                "Criterion",
                new ModelVerdict(
                        ReviewStatus.NOT_MET,
                        "Unchanged.",
                        new Evidence("a.ts", DiffSide.CONTEXT, 1, "export const config")),
                diff);

        assertThat(result.status()).isEqualTo(ReviewStatus.NOT_MET);
    }
}
