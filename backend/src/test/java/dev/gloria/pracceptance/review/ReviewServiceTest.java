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

    /** Modelo de mentira: devuelve siempre la misma respuesta y guarda lo que se le pidió. */
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
            return "modelo-de-prueba";
        }
    }

    private ReviewService serviceWith(String modelResponse) {
        return serviceWith(new StubModelClient(modelResponse));
    }

    private ReviewService serviceWith(StubModelClient client) {
        return new ReviewService(
                client, new ModelResponseParser(new ObjectMapper()), new DiffParser(), new EvidenceVerifier());
    }

    private static final String RESPUESTA_DIFF_CON_FALLO =
            """
            {"results":[
              {"criterionId":"C1","status":"MET",
               "explanation":"El resumen del carrito ahora muestra «Total estimado».",
               "evidence":{"file":"src/components/CartSummary.tsx","side":"ADDED","lineNumber":20,
                           "snippet":"<span className=\\"cart-summary__label\\">Total estimado</span>"}},
              {"criterionId":"C2","status":"NOT_MET",
               "explanation":"El historial de pedidos también cambia de «Total» a «Total estimado».",
               "evidence":{"file":"src/components/OrderHistory.tsx","side":"ADDED","lineNumber":32,
                           "snippet":"Total estimado: {formatPrice(order.total)}"}}
            ]}
            """;

    private static final String RESPUESTA_DIFF_CORREGIDO =
            """
            {"results":[
              {"criterionId":"C1","status":"MET",
               "explanation":"El resumen del carrito ahora muestra «Total estimado».",
               "evidence":{"file":"src/components/CartSummary.tsx","side":"ADDED","lineNumber":20,
                           "snippet":"Total estimado"}},
              {"criterionId":"C2","status":"MET",
               "explanation":"El diff solo toca CartSummary.tsx, así que el historial sigue igual.",
               "evidence":{"file":"src/components/CartSummary.tsx","side":"REMOVED","lineNumber":20,
                           "snippet":"<span className=\\"cart-summary__label\\">Total</span>"}}
            ]}
            """;

    @Test
    @DisplayName("el diff con fallo da Cumplido y No cumplido, citando el historial de pedidos")
    void diffConFallo() {
        ReviewResult result =
                serviceWith(RESPUESTA_DIFF_CON_FALLO).review(ExampleFixtures.criteria(), ExampleFixtures.brokenDiff());

        assertThat(result.results()).hasSize(2);

        CriterionResult primero = result.results().get(0);
        assertThat(primero.status()).isEqualTo(ReviewStatus.MET);
        assertThat(primero.evidence().file()).isEqualTo("src/components/CartSummary.tsx");

        CriterionResult segundo = result.results().get(1);
        assertThat(segundo.status()).isEqualTo(ReviewStatus.NOT_MET);
        assertThat(segundo.evidence().file()).isEqualTo("src/components/OrderHistory.tsx");
        assertThat(segundo.evidence().lineNumber()).isEqualTo(32);
        assertThat(segundo.evidence().side()).isEqualTo(DiffSide.ADDED);
        assertThat(segundo.verificationNote()).isNull();
    }

    @Test
    @DisplayName("el diff corregido da los dos criterios como Cumplidos")
    void diffCorregido() {
        ReviewResult result =
                serviceWith(RESPUESTA_DIFF_CORREGIDO).review(ExampleFixtures.criteria(), ExampleFixtures.fixedDiff());

        assertThat(result.results()).extracting(CriterionResult::status).containsExactly(ReviewStatus.MET, ReviewStatus.MET);
    }

    @Test
    @DisplayName("devuelve el modelo y quién atendió de verdad la petición")
    void devuelveElModeloYElProveedor() {
        ReviewResult result =
                serviceWith(RESPUESTA_DIFF_CORREGIDO).review(ExampleFixtures.criteria(), ExampleFixtures.fixedDiff());

        assertThat(result.model()).isEqualTo("modelo-de-prueba");
        assertThat(result.provider()).isEqualTo("API de Claude");
        assertThat(result.officialProvider()).isTrue();
    }

    @Test
    @DisplayName("una cita inventada por el modelo acaba como No verificado")
    void citaInventada() {
        String respuesta =
                """
                {"results":[
                  {"criterionId":"C1","status":"MET","explanation":"Cambiado.",
                   "evidence":{"file":"src/components/CartSummary.tsx","side":"ADDED","lineNumber":417,
                               "snippet":"Total estimado"}},
                  {"criterionId":"C2","status":"MET","explanation":"Nada más cambia.","evidence":null}
                ]}
                """;

        ReviewResult result = serviceWith(respuesta).review(ExampleFixtures.criteria(), ExampleFixtures.fixedDiff());

        assertThat(result.results()).extracting(CriterionResult::status)
                .containsExactly(ReviewStatus.UNVERIFIED, ReviewStatus.UNVERIFIED);
        assertThat(result.results().get(0).verificationNote()).contains("417");
    }

    @Test
    @DisplayName("una respuesta inválida del modelo se propaga como error del modelo")
    void respuestaInvalida() {
        ReviewService service = serviceWith("Claro, aquí tienes mi opinión sobre el diff.");

        assertThatThrownBy(() -> service.review(ExampleFixtures.criteria(), ExampleFixtures.fixedDiff()))
                .isInstanceOf(ModelResponseException.class);
    }

    @Test
    @DisplayName("un diff que no se puede interpretar se rechaza antes de llamar al modelo")
    void diffIlegible() {
        StubModelClient client = new StubModelClient(RESPUESTA_DIFF_CORREGIDO);
        ReviewService service = serviceWith(client);

        assertThatThrownBy(() -> service.review(List.of("Un criterio"), "esto no es un diff"))
                .isInstanceOf(InvalidDiffException.class);
        assertThat(client.userPrompt).isNull();
    }

    @Test
    @DisplayName("el prompt lleva los criterios numerados y el diff anotado con números de línea")
    void contenidoDelPrompt() {
        StubModelClient client = new StubModelClient(RESPUESTA_DIFF_CORREGIDO);
        serviceWith(client).review(ExampleFixtures.criteria(), ExampleFixtures.fixedDiff());

        assertThat(client.systemPrompt).contains("MET", "NOT_MET", "UNVERIFIED");
        assertThat(client.userPrompt).contains("C1. Cambiar el texto «Total»");
        assertThat(client.userPrompt).contains("C1, C2");
        assertThat(client.userPrompt).contains("ARCHIVO: src/components/CartSummary.tsx");
        assertThat(client.userPrompt).contains("[ADDED 20]");
    }
}
