package dev.gloria.pracceptance.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.gloria.pracceptance.ExampleFixtures;
import dev.gloria.pracceptance.diff.DiffParser;
import dev.gloria.pracceptance.diff.DiffSide;
import dev.gloria.pracceptance.diff.InvalidDiffException;
import dev.gloria.pracceptance.model.ModelClient;
import dev.gloria.pracceptance.model.ModelResponseException;
import dev.gloria.pracceptance.model.ModelResponseParser;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReviewServiceTest {

    /** A pretend model: always the same answer, and it remembers what it was asked. */
    private static final class StubModelClient implements ModelClient {
        private final String response;
        private String systemPrompt;
        private String userPrompt;

        StubModelClient(String response) {
            this.response = response;
        }

        @Override
        public String completeJson(String systemPrompt, String userPrompt) {
            this.systemPrompt = systemPrompt;
            this.userPrompt = userPrompt;
            return response;
        }

        @Override
        public String modelName() {
            return "test-model";
        }
    }

    private ReviewService serviceWith(String modelResponse) {
        return serviceWith(new StubModelClient(modelResponse));
    }

    private ReviewService serviceWith(StubModelClient client) {
        return new ReviewService(
                client, new ModelResponseParser(new ObjectMapper()), new DiffParser(), new EvidenceVerifier());
    }

    private static final String ANSWER_FOR_DIFF_WITH_BUG =
            """
            {"results":[
              {"criterionId":"C1","status":"MET",
               "explanation":"The cart summary now shows \\"Estimated total\\".",
               "evidence":{"file":"src/components/CartSummary.tsx","side":"ADDED","lineNumber":20,
                           "snippet":"<span className=\\"cart-summary__label\\">Estimated total</span>"}},
              {"criterionId":"C2","status":"NOT_MET",
               "explanation":"Order history changes from \\"Total\\" to \\"Estimated total\\" as well.",
               "evidence":{"file":"src/components/OrderHistory.tsx","side":"ADDED","lineNumber":32,
                           "snippet":"Estimated total: {formatPrice(order.total)}"}}
            ]}
            """;

    private static final String ANSWER_FOR_FIXED_DIFF =
            """
            {"results":[
              {"criterionId":"C1","status":"MET",
               "explanation":"The cart summary now shows \\"Estimated total\\".",
               "evidence":{"file":"src/components/CartSummary.tsx","side":"ADDED","lineNumber":20,
                           "snippet":"Estimated total"}},
              {"criterionId":"C2","status":"MET",
               "explanation":"The diff only touches CartSummary.tsx, so order history is unchanged.",
               "evidence":{"file":"src/components/CartSummary.tsx","side":"REMOVED","lineNumber":20,
                           "snippet":"<span className=\\"cart-summary__label\\">Total</span>"}}
            ]}
            """;

    @Test
    @DisplayName("the buggy diff gives Met and Not met, citing order history")
    void diffWithBug() {
        ReviewResult result = serviceWith(ANSWER_FOR_DIFF_WITH_BUG)
                .review(ExampleFixtures.criteria(), ExampleFixtures.diffWithBug());

        assertThat(result.results()).hasSize(2);

        CriterionResult first = result.results().get(0);
        assertThat(first.status()).isEqualTo(ReviewStatus.MET);
        assertThat(first.evidence().file()).isEqualTo("src/components/CartSummary.tsx");

        CriterionResult second = result.results().get(1);
        assertThat(second.status()).isEqualTo(ReviewStatus.NOT_MET);
        assertThat(second.evidence().file()).isEqualTo("src/components/OrderHistory.tsx");
        assertThat(second.evidence().lineNumber()).isEqualTo(32);
        assertThat(second.evidence().side()).isEqualTo(DiffSide.ADDED);
        assertThat(second.verificationNote()).isNull();
    }

    @Test
    @DisplayName("the fixed diff gives both criteria as Met")
    void fixedDiff() {
        ReviewResult result =
                serviceWith(ANSWER_FOR_FIXED_DIFF).review(ExampleFixtures.criteria(), ExampleFixtures.diffFixed());

        assertThat(result.results())
                .extracting(CriterionResult::status)
                .containsExactly(ReviewStatus.MET, ReviewStatus.MET);
    }

    @Test
    @DisplayName("returns the model and who actually answered the request")
    void returnsModelAndProvider() {
        ReviewResult result =
                serviceWith(ANSWER_FOR_FIXED_DIFF).review(ExampleFixtures.criteria(), ExampleFixtures.diffFixed());

        assertThat(result.model()).isEqualTo("test-model");
        assertThat(result.provider()).isEqualTo("Anthropic API");
        assertThat(result.officialProvider()).isTrue();
    }

    @Test
    @DisplayName("a citation the model invented ends up Not verified")
    void inventedCitation() {
        String answer =
                """
                {"results":[
                  {"criterionId":"C1","status":"MET","explanation":"Changed.",
                   "evidence":{"file":"src/components/CartSummary.tsx","side":"ADDED","lineNumber":417,
                               "snippet":"Estimated total"}},
                  {"criterionId":"C2","status":"MET","explanation":"Nothing else changes.","evidence":null}
                ]}
                """;

        ReviewResult result = serviceWith(answer).review(ExampleFixtures.criteria(), ExampleFixtures.diffFixed());

        assertThat(result.results())
                .extracting(CriterionResult::status)
                .containsExactly(ReviewStatus.NOT_VERIFIED, ReviewStatus.NOT_VERIFIED);
        assertThat(result.results().get(0).verificationNote()).contains("417");
    }

    @Test
    @DisplayName("an unusable answer from the model is raised as a model error")
    void unusableAnswer() {
        ReviewService service = serviceWith("Sure, here is my take on the diff.");

        assertThatThrownBy(() -> service.review(ExampleFixtures.criteria(), ExampleFixtures.diffFixed()))
                .isInstanceOf(ModelResponseException.class);
    }

    @Test
    @DisplayName("an unreadable diff is rejected before the model is called")
    void unreadableDiff() {
        StubModelClient client = new StubModelClient(ANSWER_FOR_FIXED_DIFF);
        ReviewService service = serviceWith(client);

        assertThatThrownBy(() -> service.review(List.of("A criterion"), "this is not a diff"))
                .isInstanceOf(InvalidDiffException.class);
        assertThat(client.userPrompt).isNull();
    }

    @Test
    @DisplayName("the prompt carries the criteria with identifiers and the annotated diff")
    void promptContents() {
        StubModelClient client = new StubModelClient(ANSWER_FOR_FIXED_DIFF);
        serviceWith(client).review(ExampleFixtures.criteria(), ExampleFixtures.diffFixed());

        assertThat(client.systemPrompt).contains("MET", "NOT_MET", "NOT_VERIFIED");
        assertThat(client.userPrompt).contains("C1. Change the text");
        assertThat(client.userPrompt).contains("C1, C2");
        assertThat(client.userPrompt).contains("FILE: src/components/CartSummary.tsx");
        assertThat(client.userPrompt).contains("[ADDED 20]");
    }
}
