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
 * Tres criterios, tres estados, y lo que pasa cuando el modelo se deja uno.
 *
 * <p>Antes, un veredicto de menos tiraba la revisión entera con «el modelo devolvió 2 veredictos
 * y se esperaban 3». Ahora cada criterio tiene su identificador y su resultado, pase lo que pase
 * con los demás.
 */
class ExportCsvThreeCriteriaTest {

    private static final List<String> CRITERIOS = List.of(
            "La constante exportButtonLabel debe tener el valor «Descargar CSV».",
            "El separador del CSV debe seguir siendo el punto y coma.",
            "El endpoint de exportación debe exigir autenticación.");

    private static final String DIFF_CSV =
            """
            diff --git a/src/orders/exportCsv.ts b/src/orders/exportCsv.ts
            --- a/src/orders/exportCsv.ts
            +++ b/src/orders/exportCsv.ts
            @@ -1,5 +1,5 @@
            -export const exportButtonLabel = 'Exportar';
            -const SEPARATOR = ';';
            +export const exportButtonLabel = 'Descargar CSV';
            +const SEPARATOR = ',';

             export function exportCsv(orders: Order[]): string {
               const header = ['id', 'cliente', 'total'].join(SEPARATOR);
            """;

    /** Respuesta completa: un veredicto por criterio, con los tres estados. */
    private static final String RESPUESTA_COMPLETA =
            """
            {"results":[
              {"criterionId":"C1","status":"MET",
               "explanation":"exportButtonLabel pasa a valer «Descargar CSV».",
               "evidence":{"file":"src/orders/exportCsv.ts","side":"ADDED","lineNumber":1,
                           "snippet":"export const exportButtonLabel = 'Descargar CSV';"}},
              {"criterionId":"C2","status":"NOT_MET",
               "explanation":"El separador pasa de punto y coma a coma.",
               "evidence":{"file":"src/orders/exportCsv.ts","side":"ADDED","lineNumber":2,
                           "snippet":"const SEPARATOR = ','"}},
              {"criterionId":"C3","status":"UNVERIFIED",
               "explanation":"El diff no contiene el endpoint de exportación, así que no se ve su autenticación.",
               "evidence":null}
            ]}
            """;

    /** Respuesta a medias: el modelo se deja el tercer criterio. */
    private static final String RESPUESTA_PARCIAL =
            """
            {"results":[
              {"criterionId":"C1","status":"MET",
               "explanation":"exportButtonLabel pasa a valer «Descargar CSV».",
               "evidence":{"file":"src/orders/exportCsv.ts","side":"ADDED","lineNumber":1,
                           "snippet":"exportButtonLabel = 'Descargar CSV'"}},
              {"criterionId":"C2","status":"NOT_MET",
               "explanation":"El separador pasa de punto y coma a coma.",
               "evidence":{"file":"src/orders/exportCsv.ts","side":"ADDED","lineNumber":2,
                           "snippet":"const SEPARATOR = ','"}}
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
                return "modelo-de-prueba";
            }
        };
        return new ReviewService(
                client, new ModelResponseParser(new ObjectMapper()), new DiffParser(), new EvidenceVerifier());
    }

    @Test
    @DisplayName("el caso CSV devuelve los tres estados, cada uno con su evidencia comprobada")
    void tresCriteriosTresEstados() {
        ReviewResult result = serviceWith(RESPUESTA_COMPLETA).review(CRITERIOS, DIFF_CSV);

        assertThat(result.results()).hasSize(3);
        assertThat(result.results()).extracting(CriterionResult::status)
                .containsExactly(ReviewStatus.MET, ReviewStatus.NOT_MET, ReviewStatus.UNVERIFIED);

        CriterionResult etiqueta = result.results().get(0);
        assertThat(etiqueta.evidence().file()).isEqualTo("src/orders/exportCsv.ts");
        assertThat(etiqueta.evidence().lineNumber()).isEqualTo(1);
        assertThat(etiqueta.evidence().side()).isEqualTo(DiffSide.ADDED);
        assertThat(etiqueta.verificationNote()).isNull();

        CriterionResult separador = result.results().get(1);
        assertThat(separador.evidence().file()).isEqualTo("src/orders/exportCsv.ts");
        assertThat(separador.evidence().lineNumber()).isEqualTo(2);
        assertThat(separador.verificationNote()).isNull();

        CriterionResult autenticacion = result.results().get(2);
        assertThat(autenticacion.evidence()).isNull();
        assertThat(autenticacion.verificationNote()).isNull();
        assertThat(autenticacion.explanation()).contains("endpoint");
    }

    @Test
    @DisplayName("una respuesta con solo dos veredictos conserva los dos y no bloquea la revisión")
    void respuestaParcialNoBloqueaLaRevision() {
        ReviewResult result = serviceWith(RESPUESTA_PARCIAL).review(CRITERIOS, DIFF_CSV);

        assertThat(result.results()).hasSize(3);

        assertThat(result.results().get(0).status()).isEqualTo(ReviewStatus.MET);
        assertThat(result.results().get(0).evidence().file()).isEqualTo("src/orders/exportCsv.ts");
        assertThat(result.results().get(1).status()).isEqualTo(ReviewStatus.NOT_MET);

        CriterionResult omitido = result.results().get(2);
        assertThat(omitido.criterion()).isEqualTo(CRITERIOS.get(2));
        assertThat(omitido.status()).isEqualTo(ReviewStatus.UNVERIFIED);
        assertThat(omitido.evidence()).isNull();
        assertThat(omitido.explanation()).isNull();
        assertThat(omitido.verificationNote()).isEqualTo(
                "El modelo no evaluó este criterio: no devolvió ningún veredicto para él.");
    }

    @Test
    @DisplayName("una cita a un archivo que no está en este diff se rechaza, y los demás siguen")
    void citaAjenaAlDiffDeEstaPeticion() {
        String respuesta =
                """
                {"results":[
                  {"criterionId":"C1","status":"MET","explanation":"Del carrito.",
                   "evidence":{"file":"src/components/CartSummary.tsx","side":"ADDED","lineNumber":20,
                               "snippet":"Total estimado"}},
                  {"criterionId":"C2","status":"NOT_MET","explanation":"Separador cambiado.",
                   "evidence":{"file":"src/orders/exportCsv.ts","side":"ADDED","lineNumber":2,
                               "snippet":"const SEPARATOR = ','"}},
                  {"criterionId":"C3","status":"UNVERIFIED","explanation":"No se ve.","evidence":null}
                ]}
                """;

        ReviewResult result = serviceWith(respuesta).review(CRITERIOS, DIFF_CSV);

        assertThat(result.results().get(0).status()).isEqualTo(ReviewStatus.UNVERIFIED);
        assertThat(result.results().get(0).verificationNote()).contains("CartSummary.tsx");
        assertThat(result.results().get(1).status()).isEqualTo(ReviewStatus.NOT_MET);
        assertThat(result.results().get(2).status()).isEqualTo(ReviewStatus.UNVERIFIED);
    }

    @Test
    @DisplayName("un veredicto mal formado deja ese criterio sin comprobar y explica por qué")
    void veredictoMalFormado() {
        String respuesta =
                """
                {"results":[
                  {"criterionId":"C1","status":"MET","explanation":"Etiqueta cambiada.",
                   "evidence":{"file":"src/orders/exportCsv.ts","side":"ADDED","lineNumber":1,
                               "snippet":"exportButtonLabel = 'Descargar CSV'"}},
                  {"criterionId":"C2","status":"ME_LO_INVENTO","explanation":"Vete a saber."},
                  {"criterionId":"C3","status":"UNVERIFIED","explanation":"No se ve.","evidence":null}
                ]}
                """;

        ReviewResult result = serviceWith(respuesta).review(CRITERIOS, DIFF_CSV);

        assertThat(result.results().get(0).status()).isEqualTo(ReviewStatus.MET);
        assertThat(result.results().get(1).status()).isEqualTo(ReviewStatus.UNVERIFIED);
        assertThat(result.results().get(1).verificationNote()).contains("ME_LO_INVENTO");
        assertThat(result.results().get(2).status()).isEqualTo(ReviewStatus.UNVERIFIED);
    }

    @Test
    @DisplayName("si no queda ni un veredicto en pie, sigue siendo un error del modelo")
    void ningunVeredictoEnPie() {
        ReviewService service = serviceWith("Pues no sé qué decirte de este diff.");

        assertThatThrownBy(() -> service.review(CRITERIOS, DIFF_CSV))
                .isInstanceOf(ModelResponseException.class);
    }
}
