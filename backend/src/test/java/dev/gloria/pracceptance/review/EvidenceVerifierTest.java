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
        diff = new DiffParser().parse(ExampleFixtures.brokenDiff());
    }

    private ModelVerdict verdict(ReviewStatus status, Evidence evidence) {
        return new ModelVerdict(status, "La explicación del modelo.", evidence);
    }

    @Test
    @DisplayName("una cita válida conserva el estado que dio el modelo")
    void citaValida() {
        Evidence cita = new Evidence(
                "src/components/CartSummary.tsx", DiffSide.ADDED, 20, "<span className=\"cart-summary__label\">Total estimado</span>");

        CriterionResult result = verifier.verify("Criterio", verdict(ReviewStatus.MET, cita), diff);

        assertThat(result.status()).isEqualTo(ReviewStatus.MET);
        assertThat(result.evidence()).isEqualTo(cita);
        assertThat(result.verificationNote()).isNull();
    }

    @Test
    @DisplayName("acepta un fragmento parcial de la línea citada")
    void fragmentoParcial() {
        Evidence cita = new Evidence("src/components/OrderHistory.tsx", DiffSide.ADDED, 32, "Total estimado:");

        CriterionResult result = verifier.verify("Criterio", verdict(ReviewStatus.NOT_MET, cita), diff);

        assertThat(result.status()).isEqualTo(ReviewStatus.NOT_MET);
        assertThat(result.verificationNote()).isNull();
    }

    @Test
    @DisplayName("una línea inventada degrada el resultado a No verificado")
    void lineaInventada() {
        Evidence cita = new Evidence("src/components/CartSummary.tsx", DiffSide.ADDED, 999, "Total estimado");

        CriterionResult result = verifier.verify("Criterio", verdict(ReviewStatus.MET, cita), diff);

        assertThat(result.status()).isEqualTo(ReviewStatus.UNVERIFIED);
        assertThat(result.evidence()).isEqualTo(cita);
        assertThat(result.verificationNote()).contains("999");
    }

    @Test
    @DisplayName("un archivo que no está en el diff degrada el resultado a No verificado")
    void archivoInventado() {
        Evidence cita = new Evidence("src/components/Checkout.tsx", DiffSide.ADDED, 20, "Total estimado");

        CriterionResult result = verifier.verify("Criterio", verdict(ReviewStatus.NOT_MET, cita), diff);

        assertThat(result.status()).isEqualTo(ReviewStatus.UNVERIFIED);
        assertThat(result.verificationNote()).contains("Checkout.tsx");
    }

    @Test
    @DisplayName("un fragmento que no coincide con la línea degrada el resultado a No verificado")
    void fragmentoQueNoCoincide() {
        Evidence cita = new Evidence("src/components/CartSummary.tsx", DiffSide.ADDED, 20, "Importe aproximado");

        CriterionResult result = verifier.verify("Criterio", verdict(ReviewStatus.MET, cita), diff);

        assertThat(result.status()).isEqualTo(ReviewStatus.UNVERIFIED);
        assertThat(result.verificationNote()).contains("no coincide");
    }

    @Test
    @DisplayName("citar el lado equivocado del diff degrada el resultado a No verificado")
    void ladoEquivocado() {
        Evidence cita = new Evidence("src/components/CartSummary.tsx", DiffSide.REMOVED, 20, "Total estimado");

        CriterionResult result = verifier.verify("Criterio", verdict(ReviewStatus.MET, cita), diff);

        assertThat(result.status()).isEqualTo(ReviewStatus.UNVERIFIED);
    }

    @Test
    @DisplayName("un veredicto concluyente sin cita no puede verificarse")
    void sinCita() {
        CriterionResult result = verifier.verify("Criterio", verdict(ReviewStatus.MET, null), diff);

        assertThat(result.status()).isEqualTo(ReviewStatus.UNVERIFIED);
        assertThat(result.verificationNote()).contains("sin citar");
    }

    @Test
    @DisplayName("No verificado sin cita se queda como está, sin nota de error")
    void noVerificadoSinCita() {
        CriterionResult result = verifier.verify("Criterio", verdict(ReviewStatus.UNVERIFIED, null), diff);

        assertThat(result.status()).isEqualTo(ReviewStatus.UNVERIFIED);
        assertThat(result.evidence()).isNull();
        assertThat(result.verificationNote()).isNull();
    }

    @Test
    @DisplayName("tolera que el modelo cite la ruta con el prefijo b/ del diff")
    void toleraPrefijoDeRuta() {
        Evidence cita = new Evidence("b/src/components/CartSummary.tsx", DiffSide.ADDED, 20, "Total estimado");

        CriterionResult result = verifier.verify("Criterio", verdict(ReviewStatus.MET, cita), diff);

        assertThat(result.status()).isEqualTo(ReviewStatus.MET);
    }

    @Test
    @DisplayName("conserva siempre la explicación del modelo junto a la cita")
    void conservaLaExplicacion() {
        Evidence cita = new Evidence("src/components/CartSummary.tsx", DiffSide.ADDED, 20, "Total estimado");

        CriterionResult result = verifier.verify("Mi criterio", verdict(ReviewStatus.MET, cita), diff);

        assertThat(result.criterion()).isEqualTo("Mi criterio");
        assertThat(result.explanation()).isEqualTo("La explicación del modelo.");
    }
}
