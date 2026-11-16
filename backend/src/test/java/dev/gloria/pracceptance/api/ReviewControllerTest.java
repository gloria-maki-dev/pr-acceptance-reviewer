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

    private static final String CUERPO_VALIDO =
            """
            {"acceptanceCriteria":["Cambiar «Total» por «Total estimado»."],
             "diff":"--- a/a.tsx\\n+++ b/a.tsx\\n@@ -1,1 +1,1 @@\\n-Total\\n+Total estimado\\n"}
            """;

    @Test
    @DisplayName("devuelve 200 con un resultado por criterio")
    void devuelveResultados() throws Exception {
        given(reviewService.review(any(), any()))
                .willReturn(new ReviewResult(
                        List.of(new CriterionResult(
                                "Cambiar «Total» por «Total estimado».",
                                ReviewStatus.MET,
                                "Cambiado en la línea 1.",
                                new Evidence("a.tsx", DiffSide.ADDED, 1, "Total estimado"),
                                null)),
                        "claude-opus-5-5",
                        "API de Claude",
                        true));

        mockMvc.perform(post("/api/reviews").contentType(MediaType.APPLICATION_JSON).content(CUERPO_VALIDO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.model").value("claude-opus-5-5"))
                .andExpect(jsonPath("$.provider").value("API de Claude"))
                .andExpect(jsonPath("$.officialProvider").value(true))
                .andExpect(jsonPath("$.results[0].status").value("MET"))
                .andExpect(jsonPath("$.results[0].evidence.file").value("a.tsx"))
                .andExpect(jsonPath("$.results[0].evidence.side").value("ADDED"))
                .andExpect(jsonPath("$.results[0].evidence.lineNumber").value(1))
                .andExpect(jsonPath("$.results[0].explanation").value("Cambiado en la línea 1."));
    }

    @Test
    @DisplayName("rechaza con 400 un cuerpo sin criterios")
    void rechazaSinCriterios() throws Exception {
        String cuerpo = """
                {"acceptanceCriteria":[],"diff":"--- a/a\\n+++ b/a\\n@@ -1 +1 @@\\n-a\\n+b\\n"}
                """;

        mockMvc.perform(post("/api/reviews").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    @DisplayName("rechaza con 400 un cuerpo sin diff")
    void rechazaSinDiff() throws Exception {
        String cuerpo = """
                {"acceptanceCriteria":["Algo"],"diff":"   "}
                """;

        mockMvc.perform(post("/api/reviews").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("rechaza con 400 un criterio en blanco")
    void rechazaCriterioEnBlanco() throws Exception {
        String cuerpo = """
                {"acceptanceCriteria":["  "],"diff":"--- a/a\\n+++ b/a\\n@@ -1 +1 @@\\n-a\\n+b\\n"}
                """;

        mockMvc.perform(post("/api/reviews").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("traduce un diff ilegible a 400 con un mensaje para la pantalla")
    void diffIlegible() throws Exception {
        willThrow(new InvalidDiffException("No reconozco ningún archivo en el diff."))
                .given(reviewService)
                .review(any(), any());

        mockMvc.perform(post("/api/reviews").contentType(MediaType.APPLICATION_JSON).content(CUERPO_VALIDO))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("No reconozco ningún archivo en el diff."));
    }

    @Test
    @DisplayName("traduce una respuesta inválida del modelo a 502")
    void respuestaInvalidaDelModelo() throws Exception {
        willThrow(new ModelResponseException("El modelo no devolvió JSON."))
                .given(reviewService)
                .review(any(), any());

        mockMvc.perform(post("/api/reviews").contentType(MediaType.APPLICATION_JSON).content(CUERPO_VALIDO))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("El modelo no devolvió JSON."));
    }

    @Test
    @DisplayName("traduce la falta de credenciales a 503 con una pista de configuración")
    void sinCredenciales() throws Exception {
        willThrow(new ModelUnavailableException("Falta la variable de entorno ANTHROPIC_API_KEY."))
                .given(reviewService)
                .review(any(), any());

        mockMvc.perform(post("/api/reviews").contentType(MediaType.APPLICATION_JSON).content(CUERPO_VALIDO))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("Falta la variable de entorno ANTHROPIC_API_KEY."));
    }

    @Test
    @DisplayName("un GET devuelve 405, no un 500")
    void metodoNoPermitido() throws Exception {
        mockMvc.perform(get("/api/reviews"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.error").value("Este recurso solo acepta POST."));
    }

    @Test
    @DisplayName("un cuerpo que no es JSON devuelve 415, no un 500")
    void tipoDeContenidoNoSoportado() throws Exception {
        mockMvc.perform(post("/api/reviews").contentType(MediaType.TEXT_PLAIN).content("hola"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.error").value("El cuerpo tiene que ir como application/json."));
    }
}
