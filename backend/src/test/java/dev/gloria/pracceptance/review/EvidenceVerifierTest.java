package dev.gloria.pracceptance.review;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gloria.pracceptance.ExampleFixtures;
import dev.gloria.pracceptance.diff.DiffParser;
import dev.gloria.pracceptance.diff.DiffSide;
import dev.gloria.pracceptance.diff.ParsedDiff;
import dev.gloria.pracceptance.model.ModelVerdict;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EvidenceVerifierTest {

    private final EvidenceVerifier verifier = new EvidenceVerifier();
    private ParsedDiff diff;

    @BeforeEach
    void setUp() {
        diff = new DiffParser().parse(ExampleFixtures.diffWithBug());
    }

    private ModelVerdict verdict(ReviewStatus status, Evidence evidence) {
        return new ModelVerdict(status, "The model's explanation.", evidence);
    }

    @Test
    @DisplayName("a valid citation keeps the status the model gave")
    void validCitation() {
        Evidence citation = new Evidence(
                "src/components/CartSummary.tsx",
                DiffSide.ADDED,
                20,
                "<span className=\"cart-summary__label\">Estimated total</span>");

        CriterionResult result = verifier.verify("Criterion", verdict(ReviewStatus.MET, citation), diff);

        assertThat(result.status()).isEqualTo(ReviewStatus.MET);
        assertThat(result.evidence()).isEqualTo(citation);
        assertThat(result.verificationNote()).isNull();
    }

    @Test
    @DisplayName("accepts a partial fragment of the cited line")
    void partialFragment() {
        Evidence citation = new Evidence("src/components/OrderHistory.tsx", DiffSide.ADDED, 32, "Estimated total:");

        CriterionResult result = verifier.verify("Criterion", verdict(ReviewStatus.NOT_MET, citation), diff);

        assertThat(result.status()).isEqualTo(ReviewStatus.NOT_MET);
        assertThat(result.verificationNote()).isNull();
    }

    @Test
    @DisplayName("an invented line number drops the result to Not verified")
    void inventedLine() {
        Evidence citation = new Evidence("src/components/CartSummary.tsx", DiffSide.ADDED, 999, "Estimated total");

        CriterionResult result = verifier.verify("Criterion", verdict(ReviewStatus.MET, citation), diff);

        assertThat(result.status()).isEqualTo(ReviewStatus.NOT_VERIFIED);
        assertThat(result.evidence()).isEqualTo(citation);
        assertThat(result.verificationNote()).contains("999");
    }

    @Test
    @DisplayName("a file that is not in the diff drops the result to Not verified")
    void inventedFile() {
        Evidence citation = new Evidence("src/components/Checkout.tsx", DiffSide.ADDED, 20, "Estimated total");

        CriterionResult result = verifier.verify("Criterion", verdict(ReviewStatus.NOT_MET, citation), diff);

        assertThat(result.status()).isEqualTo(ReviewStatus.NOT_VERIFIED);
        assertThat(result.verificationNote()).contains("Checkout.tsx");
    }

    @Test
    @DisplayName("a fragment that does not match the line drops the result to Not verified")
    void fragmentDoesNotMatch() {
        Evidence citation = new Evidence("src/components/CartSummary.tsx", DiffSide.ADDED, 20, "Approximate amount");

        CriterionResult result = verifier.verify("Criterion", verdict(ReviewStatus.MET, citation), diff);

        assertThat(result.status()).isEqualTo(ReviewStatus.NOT_VERIFIED);
        assertThat(result.verificationNote()).contains("does not match");
    }

    @Test
    @DisplayName("citing the wrong side of the diff drops the result to Not verified")
    void wrongSide() {
        Evidence citation = new Evidence("src/components/CartSummary.tsx", DiffSide.REMOVED, 20, "Estimated total");

        CriterionResult result = verifier.verify("Criterion", verdict(ReviewStatus.MET, citation), diff);

        assertThat(result.status()).isEqualTo(ReviewStatus.NOT_VERIFIED);
    }

    @Test
    @DisplayName("a conclusive verdict with no citation cannot be verified")
    void noCitation() {
        CriterionResult result = verifier.verify("Criterion", verdict(ReviewStatus.MET, null), diff);

        assertThat(result.status()).isEqualTo(ReviewStatus.NOT_VERIFIED);
        assertThat(result.verificationNote()).contains("without citing");
    }

    @Test
    @DisplayName("Not verified with no citation stays as it is, with no error note")
    void notVerifiedWithoutCitation() {
        CriterionResult result = verifier.verify("Criterion", verdict(ReviewStatus.NOT_VERIFIED, null), diff);

        assertThat(result.status()).isEqualTo(ReviewStatus.NOT_VERIFIED);
        assertThat(result.evidence()).isNull();
        assertThat(result.verificationNote()).isNull();
    }

    @Test
    @DisplayName("tolerates a path cited with the diff's b/ prefix")
    void toleratesPathPrefix() {
        Evidence citation = new Evidence("b/src/components/CartSummary.tsx", DiffSide.ADDED, 20, "Estimated total");

        CriterionResult result = verifier.verify("Criterion", verdict(ReviewStatus.MET, citation), diff);

        assertThat(result.status()).isEqualTo(ReviewStatus.MET);
    }

    @Test
    @DisplayName("always keeps the model's explanation next to the citation")
    void keepsTheExplanation() {
        Evidence citation = new Evidence("src/components/CartSummary.tsx", DiffSide.ADDED, 20, "Estimated total");

        CriterionResult result = verifier.verify("My criterion", verdict(ReviewStatus.MET, citation), diff);

        assertThat(result.criterion()).isEqualTo("My criterion");
        assertThat(result.explanation()).isEqualTo("The model's explanation.");
    }
}
