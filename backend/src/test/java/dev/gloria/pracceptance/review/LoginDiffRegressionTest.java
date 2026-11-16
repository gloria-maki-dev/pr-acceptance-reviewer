package dev.gloria.pracceptance.review;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.gloria.pracceptance.ExampleFixtures;
import dev.gloria.pracceptance.diff.DiffParser;
import dev.gloria.pracceptance.diff.DiffSide;
import dev.gloria.pracceptance.model.ModelClient;
import dev.gloria.pracceptance.model.ModelResponseParser;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Regression: a review must carry nothing over from the previous one.
 *
 * <p>The symptom behind it - reviewing a login diff and getting citations to
 * {@code CartSummary.tsx} - came from a test provider that always returned the same answer, not
 * from the flow. These tests pin both halves: that each review uses the diff it was sent, and
 * that a citation to a file outside that diff is never taken at face value.
 */
class LoginDiffRegressionTest {

    private static final String LOGIN_DIFF =
            """
            diff --git a/src/login/messages.ts b/src/login/messages.ts
            --- a/src/login/messages.ts
            +++ b/src/login/messages.ts
            @@ -1,4 +1,4 @@
             export const loginMessages = {
            -  wrongPassword: 'Sign-in error',
            -  accountLocked: 'Your account is locked',
            +  wrongPassword: 'The password is incorrect',
            +  accountLocked: 'Try again later',
             };
            """;

    private static final List<String> LOGIN_CRITERIA = List.of(
            "The wrong-password message must read \"The password is incorrect\".",
            "The locked-account message must still read \"Your account is locked\".");

    private static final String LOGIN_ANSWER =
            """
            {"results":[
              {"criterionId":"C1","status":"MET",
               "explanation":"The message now reads \\"The password is incorrect\\".",
               "evidence":{"file":"src/login/messages.ts","side":"ADDED","lineNumber":2,
                           "snippet":"wrongPassword: 'The password is incorrect'"}},
              {"criterionId":"C2","status":"NOT_MET",
               "explanation":"The locked-account message now reads \\"Try again later\\".",
               "evidence":{"file":"src/login/messages.ts","side":"ADDED","lineNumber":3,
                           "snippet":"accountLocked: 'Try again later'"}}
            ]}
            """;

    /** The SHOP-412 answer: it cites files the login diff does not contain. */
    private static final String CART_ANSWER =
            """
            {"results":[
              {"criterionId":"C1","status":"MET",
               "explanation":"The cart summary now shows \\"Estimated total\\".",
               "evidence":{"file":"src/components/CartSummary.tsx","side":"ADDED","lineNumber":20,
                           "snippet":"<span className=\\"cart-summary__label\\">Estimated total</span>"}},
              {"criterionId":"C2","status":"NOT_MET",
               "explanation":"Order history changes as well.",
               "evidence":{"file":"src/components/OrderHistory.tsx","side":"ADDED","lineNumber":32,
                           "snippet":"Estimated total:"}}
            ]}
            """;

    /** Returns a different answer on each call, and remembers what it was asked. */
    private static final class ScriptedModelClient implements ModelClient {
        private final Deque<String> responses = new ArrayDeque<>();
        private final Deque<String> prompts = new ArrayDeque<>();

        ScriptedModelClient(String... scripted) {
            for (String response : scripted) {
                responses.addLast(response);
            }
        }

        @Override
        public String completeJson(String systemPrompt, String userPrompt) {
            prompts.addLast(userPrompt);
            return responses.pollFirst();
        }

        @Override
        public String modelName() {
            return "test-model";
        }
    }

    private static ReviewService serviceWith(ModelClient client) {
        return new ReviewService(
                client, new ModelResponseParser(new ObjectMapper()), new DiffParser(), new EvidenceVerifier());
    }

    @Test
    @DisplayName("the login diff gives Met at messages.ts:2 and Not met at messages.ts:3")
    void reviewOfTheLoginDiff() {
        ReviewResult result =
                serviceWith(new ScriptedModelClient(LOGIN_ANSWER)).review(LOGIN_CRITERIA, LOGIN_DIFF);

        CriterionResult first = result.results().get(0);
        assertThat(first.status()).isEqualTo(ReviewStatus.MET);
        assertThat(first.evidence().file()).isEqualTo("src/login/messages.ts");
        assertThat(first.evidence().lineNumber()).isEqualTo(2);
        assertThat(first.evidence().side()).isEqualTo(DiffSide.ADDED);
        assertThat(first.verificationNote()).isNull();

        CriterionResult second = result.results().get(1);
        assertThat(second.status()).isEqualTo(ReviewStatus.NOT_MET);
        assertThat(second.evidence().file()).isEqualTo("src/login/messages.ts");
        assertThat(second.evidence().lineNumber()).isEqualTo(3);
        assertThat(second.verificationNote()).isNull();
    }

    @Test
    @DisplayName("reviewing login right after SHOP-412 carries nothing over from the cart")
    void reviewLoginAfterTheCart() {
        ScriptedModelClient client = new ScriptedModelClient(CART_ANSWER, LOGIN_ANSWER);
        ReviewService service = serviceWith(client);

        service.review(ExampleFixtures.criteria(), ExampleFixtures.diffWithBug());
        ReviewResult login = service.review(LOGIN_CRITERIA, LOGIN_DIFF);

        assertThat(login.results())
                .extracting(r -> r.evidence().file())
                .containsOnly("src/login/messages.ts");
        assertThat(login.results())
                .extracting(CriterionResult::criterion)
                .containsExactlyElementsOf(LOGIN_CRITERIA);

        // The second review's prompt cannot carry a trace of the first diff.
        String secondPrompt = client.prompts.pollLast();
        assertThat(secondPrompt).contains("src/login/messages.ts");
        assertThat(secondPrompt).doesNotContain("CartSummary.tsx", "OrderHistory.tsx");
    }

    @Test
    @DisplayName("cart citations answered for the login diff are rejected")
    void cartCitationsOverTheLoginDiff() {
        ReviewResult result =
                serviceWith(new ScriptedModelClient(CART_ANSWER)).review(LOGIN_CRITERIA, LOGIN_DIFF);

        assertThat(result.results())
                .extracting(CriterionResult::status)
                .containsExactly(ReviewStatus.NOT_VERIFIED, ReviewStatus.NOT_VERIFIED);
        assertThat(result.results())
                .allSatisfy(r -> assertThat(r.verificationNote()).contains("does not contain the file"));
    }
}
