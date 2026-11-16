package dev.gloria.pracceptance.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.gloria.pracceptance.diff.DiffParser;
import dev.gloria.pracceptance.diff.DiffSide;
import dev.gloria.pracceptance.model.ModelClient;
import dev.gloria.pracceptance.model.ModelResponseException;
import dev.gloria.pracceptance.model.ModelResponseParser;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Three criteria, three verdicts, and what happens when the model leaves one out.
 *
 * <p>A missing verdict used to sink the whole review with "the model returned 2 verdicts and 3
 * were expected". Now every criterion has its own identifier and its own result, whatever
 * happens to the others.
 */
class ExportCsvThreeCriteriaTest {

    private static final List<String> CRITERIA = List.of(
            "The exportButtonLabel constant must have the value “Download CSV”.",
            "The CSV separator must still be the semicolon.",
            "The export endpoint must require authentication.");

    private static final String CSV_DIFF =
            """
            diff --git a/src/orders/exportCsv.ts b/src/orders/exportCsv.ts
            --- a/src/orders/exportCsv.ts
            +++ b/src/orders/exportCsv.ts
            @@ -1,5 +1,5 @@
            -export const exportButtonLabel = 'Export';
            -const separator = ';';
            +export const exportButtonLabel = 'Download CSV';
            +const separator = ',';

             export function exportCsv(orders: Order[]): string {
               const header = ['id', 'customer', 'total'].join(separator);
            """;

    /** A complete answer: one verdict per criterion, covering all three statuses. */
    private static final String COMPLETE_ANSWER =
            """
            {"results":[
              {"criterionId":"C1","status":"MET",
               "explanation":"exportButtonLabel now reads \\"Download CSV\\".",
               "evidence":{"file":"src/orders/exportCsv.ts","side":"ADDED","lineNumber":1,
                           "snippet":"export const exportButtonLabel = 'Download CSV';"}},
              {"criterionId":"C2","status":"NOT_MET",
               "explanation":"The separator changes from a semicolon to a comma.",
               "evidence":{"file":"src/orders/exportCsv.ts","side":"ADDED","lineNumber":2,
                           "snippet":"const separator = ','"}},
              {"criterionId":"C3","status":"NOT_VERIFIED",
               "explanation":"The diff does not contain the export endpoint, so its auth is not visible.",
               "evidence":null}
            ]}
            """;

    /** A half-answer: the model leaves the third criterion out. */
    private static final String PARTIAL_ANSWER =
            """
            {"results":[
              {"criterionId":"C1","status":"MET",
               "explanation":"exportButtonLabel now reads \\"Download CSV\\".",
               "evidence":{"file":"src/orders/exportCsv.ts","side":"ADDED","lineNumber":1,
                           "snippet":"exportButtonLabel = 'Download CSV'"}},
              {"criterionId":"C2","status":"NOT_MET",
               "explanation":"The separator changes from a semicolon to a comma.",
               "evidence":{"file":"src/orders/exportCsv.ts","side":"ADDED","lineNumber":2,
                           "snippet":"const separator = ','"}}
            ]}
            """;

    private static ReviewService serviceWith(String response) {
        ModelClient client = new ModelClient() {
            @Override
            public String completeJson(String systemPrompt, String userPrompt) {
                return response;
            }

            @Override
            public String modelName() {
                return "test-model";
            }
        };
        return new ReviewService(
                client, new ModelResponseParser(new ObjectMapper()), new DiffParser(), new EvidenceVerifier());
    }

    @Test
    @DisplayName("the CSV case returns all three verdicts, each with its citation checked")
    void threeCriteriaThreeVerdicts() {
        ReviewResult result = serviceWith(COMPLETE_ANSWER).review(CRITERIA, CSV_DIFF);

        assertThat(result.results()).hasSize(3);
        assertThat(result.results())
                .extracting(CriterionResult::status)
                .containsExactly(ReviewStatus.MET, ReviewStatus.NOT_MET, ReviewStatus.NOT_VERIFIED);

        CriterionResult label = result.results().get(0);
        assertThat(label.evidence().file()).isEqualTo("src/orders/exportCsv.ts");
        assertThat(label.evidence().lineNumber()).isEqualTo(1);
        assertThat(label.evidence().side()).isEqualTo(DiffSide.ADDED);
        assertThat(label.verificationNote()).isNull();

        CriterionResult separator = result.results().get(1);
        assertThat(separator.evidence().file()).isEqualTo("src/orders/exportCsv.ts");
        assertThat(separator.evidence().lineNumber()).isEqualTo(2);
        assertThat(separator.evidence().snippet()).isEqualTo("const separator = ','");
        assertThat(separator.verificationNote()).isNull();

        CriterionResult auth = result.results().get(2);
        assertThat(auth.evidence()).isNull();
        assertThat(auth.verificationNote()).isNull();
        assertThat(auth.explanation()).contains("endpoint");
    }

    @Test
    @DisplayName("an answer with only two verdicts keeps both and does not block the review")
    void partialAnswerDoesNotBlockTheReview() {
        ReviewResult result = serviceWith(PARTIAL_ANSWER).review(CRITERIA, CSV_DIFF);

        assertThat(result.results()).hasSize(3);
        assertThat(result.results().get(0).status()).isEqualTo(ReviewStatus.MET);
        assertThat(result.results().get(0).evidence().file()).isEqualTo("src/orders/exportCsv.ts");
        assertThat(result.results().get(1).status()).isEqualTo(ReviewStatus.NOT_MET);

        CriterionResult skipped = result.results().get(2);
        assertThat(skipped.criterion()).isEqualTo(CRITERIA.get(2));
        assertThat(skipped.status()).isEqualTo(ReviewStatus.NOT_VERIFIED);
        assertThat(skipped.evidence()).isNull();
        assertThat(skipped.explanation()).isNull();
        assertThat(skipped.verificationNote())
                .isEqualTo("The model did not evaluate this criterion: it returned no verdict for it.");
    }

    @Test
    @DisplayName("a citation to a file outside this diff is rejected, and the rest carry on")
    void citationOutsideThisRequestsDiff() {
        String answer =
                """
                {"results":[
                  {"criterionId":"C1","status":"MET","explanation":"From the cart.",
                   "evidence":{"file":"src/components/CartSummary.tsx","side":"ADDED","lineNumber":20,
                               "snippet":"Estimated total"}},
                  {"criterionId":"C2","status":"NOT_MET","explanation":"Separator changed.",
                   "evidence":{"file":"src/orders/exportCsv.ts","side":"ADDED","lineNumber":2,
                               "snippet":"const separator = ','"}},
                  {"criterionId":"C3","status":"NOT_VERIFIED","explanation":"Not visible.","evidence":null}
                ]}
                """;

        ReviewResult result = serviceWith(answer).review(CRITERIA, CSV_DIFF);

        assertThat(result.results().get(0).status()).isEqualTo(ReviewStatus.NOT_VERIFIED);
        assertThat(result.results().get(0).verificationNote()).contains("CartSummary.tsx");
        assertThat(result.results().get(1).status()).isEqualTo(ReviewStatus.NOT_MET);
        assertThat(result.results().get(2).status()).isEqualTo(ReviewStatus.NOT_VERIFIED);
    }

    @Test
    @DisplayName("a malformed verdict leaves its criterion unchecked and says why")
    void malformedVerdict() {
        String answer =
                """
                {"results":[
                  {"criterionId":"C1","status":"MET","explanation":"Label changed.",
                   "evidence":{"file":"src/orders/exportCsv.ts","side":"ADDED","lineNumber":1,
                               "snippet":"exportButtonLabel = 'Download CSV'"}},
                  {"criterionId":"C2","status":"I_MADE_IT_UP","explanation":"Who knows."},
                  {"criterionId":"C3","status":"NOT_VERIFIED","explanation":"Not visible.","evidence":null}
                ]}
                """;

        ReviewResult result = serviceWith(answer).review(CRITERIA, CSV_DIFF);

        assertThat(result.results().get(0).status()).isEqualTo(ReviewStatus.MET);
        assertThat(result.results().get(1).status()).isEqualTo(ReviewStatus.NOT_VERIFIED);
        assertThat(result.results().get(1).verificationNote()).contains("I_MADE_IT_UP");
        assertThat(result.results().get(2).status()).isEqualTo(ReviewStatus.NOT_VERIFIED);
    }

    @Test
    @DisplayName("with no verdict left standing it is still a model error")
    void noVerdictLeftStanding() {
        ReviewService service = serviceWith("I am not sure what to tell you about this diff.");

        assertThatThrownBy(() -> service.review(CRITERIA, CSV_DIFF))
                .isInstanceOf(ModelResponseException.class);
    }
}
