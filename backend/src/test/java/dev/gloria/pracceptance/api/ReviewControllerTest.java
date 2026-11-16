package dev.gloria.pracceptance.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.gloria.pracceptance.diff.DiffSide;
import dev.gloria.pracceptance.diff.InvalidDiffException;
import dev.gloria.pracceptance.model.ModelResponseException;
import dev.gloria.pracceptance.model.ModelUnavailableException;
import dev.gloria.pracceptance.review.CriterionResult;
import dev.gloria.pracceptance.review.Evidence;
import dev.gloria.pracceptance.review.ReviewResult;
import dev.gloria.pracceptance.review.ReviewService;
import dev.gloria.pracceptance.review.ReviewStatus;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ReviewController.class)
class ReviewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReviewService reviewService;

    private static final String VALID_BODY =
            """
            {"acceptanceCriteria":["Change \\"Total\\" to \\"Estimated total\\"."],
             "diff":"--- a/a.tsx\\n+++ b/a.tsx\\n@@ -1,1 +1,1 @@\\n-Total\\n+Estimated total\\n"}
            """;

    @Test
    @DisplayName("returns 200 with one result per criterion")
    void returnsResults() throws Exception {
        given(reviewService.review(any(), any()))
                .willReturn(new ReviewResult(
                        List.of(new CriterionResult(
                                "Change \"Total\" to \"Estimated total\".",
                                ReviewStatus.MET,
                                "Changed on line 1.",
                                new Evidence("a.tsx", DiffSide.ADDED, 1, "Estimated total"),
                                null)),
                        "claude-opus-5-5",
                        "Anthropic API",
                        true));

        mockMvc.perform(post("/api/reviews").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.model").value("claude-opus-5-5"))
                .andExpect(jsonPath("$.provider").value("Anthropic API"))
                .andExpect(jsonPath("$.officialProvider").value(true))
                .andExpect(jsonPath("$.results[0].status").value("MET"))
                .andExpect(jsonPath("$.results[0].evidence.file").value("a.tsx"))
                .andExpect(jsonPath("$.results[0].evidence.side").value("ADDED"))
                .andExpect(jsonPath("$.results[0].evidence.lineNumber").value(1))
                .andExpect(jsonPath("$.results[0].explanation").value("Changed on line 1."));
    }

    @Test
    @DisplayName("rejects a body with no criteria with 400")
    void rejectsWithoutCriteria() throws Exception {
        String body = """
                {"acceptanceCriteria":[],"diff":"--- a/a\\n+++ b/a\\n@@ -1 +1 @@\\n-a\\n+b\\n"}
                """;

        mockMvc.perform(post("/api/reviews").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    @DisplayName("rejects a body with no diff with 400")
    void rejectsWithoutDiff() throws Exception {
        String body = """
                {"acceptanceCriteria":["Something"],"diff":"   "}
                """;

        mockMvc.perform(post("/api/reviews").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("rejects a blank criterion with 400")
    void rejectsBlankCriterion() throws Exception {
        String body = """
                {"acceptanceCriteria":["  "],"diff":"--- a/a\\n+++ b/a\\n@@ -1 +1 @@\\n-a\\n+b\\n"}
                """;

        mockMvc.perform(post("/api/reviews").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("turns an unreadable diff into a 400 with a message for the screen")
    void unreadableDiff() throws Exception {
        willThrow(new InvalidDiffException("No changed files found in the diff."))
                .given(reviewService)
                .review(any(), any());

        mockMvc.perform(post("/api/reviews").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("No changed files found in the diff."));
    }

    @Test
    @DisplayName("turns an invalid model answer into a 502")
    void invalidModelAnswer() throws Exception {
        willThrow(new ModelResponseException("The model did not return JSON."))
                .given(reviewService)
                .review(any(), any());

        mockMvc.perform(post("/api/reviews").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("The model did not return JSON."));
    }

    @Test
    @DisplayName("turns a missing credential into a 503 with a configuration hint")
    void missingCredential() throws Exception {
        willThrow(new ModelUnavailableException("The ANTHROPIC_API_KEY environment variable is missing."))
                .given(reviewService)
                .review(any(), any());

        mockMvc.perform(post("/api/reviews").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("The ANTHROPIC_API_KEY environment variable is missing."));
    }

    @Test
    @DisplayName("a GET returns 405, not a 500")
    void methodNotAllowed() throws Exception {
        mockMvc.perform(get("/api/reviews"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.error").value("This resource only accepts POST."));
    }

    @Test
    @DisplayName("a non-JSON body returns 415, not a 500")
    void unsupportedMediaType() throws Exception {
        mockMvc.perform(post("/api/reviews").contentType(MediaType.TEXT_PLAIN).content("hola"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.error").value("The body must be sent as application/json."));
    }
}
