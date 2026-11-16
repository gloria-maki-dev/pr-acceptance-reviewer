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
 * Regresión: una revisión no puede arrastrar nada de la anterior.
 *
 * <p>El síntoma que la motiva —revisar un diff de login y recibir citas de
 * {@code CartSummary.tsx}— venía de un proveedor de pruebas que devolvía siempre la misma
 * respuesta, no del flujo. Estas pruebas fijan las dos mitades: que cada revisión use el diff que
 * se le manda, y que una cita a un archivo ajeno al diff nunca se dé por buena.
 */
class LoginDiffRegressionTest {

    private static final String DIFF_LOGIN =
            """
            diff --git a/src/login/messages.ts b/src/login/messages.ts
            --- a/src/login/messages.ts
            +++ b/src/login/messages.ts
            @@ -1,4 +1,4 @@
             export const loginMessages = {
            -  wrongPassword: 'Error de inicio de sesión',
            -  accountLocked: 'Tu cuenta está bloqueada',
            +  wrongPassword: 'La contraseña es incorrecta',
            +  accountLocked: 'Inténtalo más tarde',
             };
            """;

    private static final List<String> CRITERIOS_LOGIN = List.of(
            "El mensaje de contraseña incorrecta debe decir «La contraseña es incorrecta».",
            "El mensaje de cuenta bloqueada debe seguir diciendo «Tu cuenta está bloqueada».");

    private static final String RESPUESTA_LOGIN =
            """
            {"results":[
              {"criterionId":"C1","status":"MET",
               "explanation":"El mensaje pasa a decir «La contraseña es incorrecta».",
               "evidence":{"file":"src/login/messages.ts","side":"ADDED","lineNumber":2,
                           "snippet":"wrongPassword: 'La contraseña es incorrecta'"}},
              {"criterionId":"C2","status":"NOT_MET",
               "explanation":"El mensaje de cuenta bloqueada pasa a decir «Inténtalo más tarde».",
               "evidence":{"file":"src/login/messages.ts","side":"ADDED","lineNumber":3,
                           "snippet":"accountLocked: 'Inténtalo más tarde'"}}
            ]}
            """;

    /** Respuesta del ejemplo TIENDA-412: cita archivos que el diff de login no contiene. */
    private static final String RESPUESTA_DEL_CARRITO =
            """
            {"results":[
              {"criterionId":"C1","status":"MET",
               "explanation":"El resumen del carrito pasa a mostrar «Total estimado».",
               "evidence":{"file":"src/components/CartSummary.tsx","side":"ADDED","lineNumber":20,
                           "snippet":"<span className=\\"cart-summary__label\\">Total estimado</span>"}},
              {"criterionId":"C2","status":"NOT_MET",
               "explanation":"El historial de pedidos también cambia.",
               "evidence":{"file":"src/components/OrderHistory.tsx","side":"ADDED","lineNumber":32,
                           "snippet":"Total estimado:"}}
            ]}
            """;

    /** Devuelve una respuesta distinta en cada llamada, y recuerda qué se le pidió. */
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
            return "modelo-de-prueba";
        }
    }

    private static ReviewService serviceWith(ModelClient client) {
        return new ReviewService(
                client, new ModelResponseParser(new ObjectMapper()), new DiffParser(), new EvidenceVerifier());
    }

    @Test
    @DisplayName("el diff de login da Cumplido en messages.ts:2 y No cumplido en messages.ts:3")
    void revisionDelDiffDeLogin() {
        ReviewResult result =
                serviceWith(new ScriptedModelClient(RESPUESTA_LOGIN)).review(CRITERIOS_LOGIN, DIFF_LOGIN);

        CriterionResult primero = result.results().get(0);
        assertThat(primero.status()).isEqualTo(ReviewStatus.MET);
        assertThat(primero.evidence().file()).isEqualTo("src/login/messages.ts");
        assertThat(primero.evidence().lineNumber()).isEqualTo(2);
        assertThat(primero.evidence().side()).isEqualTo(DiffSide.ADDED);
        assertThat(primero.verificationNote()).isNull();

        CriterionResult segundo = result.results().get(1);
        assertThat(segundo.status()).isEqualTo(ReviewStatus.NOT_MET);
        assertThat(segundo.evidence().file()).isEqualTo("src/login/messages.ts");
        assertThat(segundo.evidence().lineNumber()).isEqualTo(3);
        assertThat(segundo.verificationNote()).isNull();
    }

    @Test
    @DisplayName("revisar login justo después de TIENDA-412 no arrastra nada del carrito")
    void revisarLoginDespuesDelCarrito() {
        ScriptedModelClient client = new ScriptedModelClient(
                """
                {"results":[
                  {"criterionId":"C1","status":"MET","explanation":"Carrito.",
                   "evidence":{"file":"src/components/CartSummary.tsx","side":"ADDED","lineNumber":20,
                               "snippet":"Total estimado"}},
                  {"criterionId":"C2","status":"NOT_MET","explanation":"Historial.",
                   "evidence":{"file":"src/components/OrderHistory.tsx","side":"ADDED","lineNumber":32,
                               "snippet":"Total estimado:"}}
                ]}
                """,
                RESPUESTA_LOGIN);
        ReviewService service = serviceWith(client);

        service.review(ExampleFixtures.criteria(), ExampleFixtures.brokenDiff());
        ReviewResult login = service.review(CRITERIOS_LOGIN, DIFF_LOGIN);

        assertThat(login.results()).extracting(r -> r.evidence().file())
                .containsOnly("src/login/messages.ts");
        assertThat(login.results()).extracting(CriterionResult::criterion)
                .containsExactlyElementsOf(CRITERIOS_LOGIN);

        // El prompt de la segunda revisión no puede llevar rastro del diff de la primera.
        String segundoPrompt = client.prompts.pollLast();
        assertThat(segundoPrompt).contains("src/login/messages.ts");
        assertThat(segundoPrompt).doesNotContain("CartSummary.tsx", "OrderHistory.tsx");
    }

    @Test
    @DisplayName("si el modelo responde con las citas del carrito para el diff de login, se rechazan")
    void citasDelCarritoSobreElDiffDeLogin() {
        ReviewResult result = serviceWith(new ScriptedModelClient(RESPUESTA_DEL_CARRITO))
                .review(CRITERIOS_LOGIN, DIFF_LOGIN);

        assertThat(result.results()).extracting(CriterionResult::status)
                .containsExactly(ReviewStatus.UNVERIFIED, ReviewStatus.UNVERIFIED);
        assertThat(result.results()).allSatisfy(r ->
                assertThat(r.verificationNote()).contains("no contiene el archivo"));
    }
}
