package dev.gloria.pracceptance.review;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gloria.pracceptance.diff.DiffParser;
import dev.gloria.pracceptance.diff.DiffSide;
import dev.gloria.pracceptance.diff.ParsedDiff;
import dev.gloria.pracceptance.model.ModelVerdict;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Las citas que antes se colaban, y las correctas que antes se rechazaban. */
class EvidenceVerifierHardeningTest {

    private final EvidenceVerifier verifier = new EvidenceVerifier();
    private final DiffParser parser = new DiffParser();

    private static ModelVerdict met(Evidence evidence) {
        return new ModelVerdict(ReviewStatus.MET, "La explicación.", evidence);
    }

    @Test
    @DisplayName("un fragmento de un carácter no puede sostener un veredicto")
    void fragmentoTrivial() {
        ParsedDiff diff = parser.parse("--- a/a.ts\n+++ b/a.ts\n@@ -1,2 +1,2 @@\n x\n+const x = 2;\n");

        CriterionResult result =
                verifier.verify("Criterio", met(new Evidence("a.ts", DiffSide.ADDED, 2, ";")), diff);

        assertThat(result.status()).isEqualTo(ReviewStatus.UNVERIFIED);
        assertThat(result.verificationNote()).contains("demasiado corto");
    }

    @Test
    @DisplayName("una cita a la segunda aparición de la misma ruta se verifica")
    void segundaAparicionDeLaMismaRuta() {
        ParsedDiff diff = parser.parse("""
                diff --git a/x.ts b/x.ts
                --- a/x.ts
                +++ b/x.ts
                @@ -1,1 +1,1 @@
                -cafe con leche
                +te con limon
                diff --git a/x.ts b/x.ts
                --- a/x.ts
                +++ b/x.ts
                @@ -50,1 +50,1 @@
                -zumo de naranja
                +agua con gas
                """);

        CriterionResult result = verifier.verify(
                "Criterio", met(new Evidence("x.ts", DiffSide.ADDED, 50, "agua con gas")), diff);

        assertThat(result.status()).isEqualTo(ReviewStatus.MET);
        assertThat(result.verificationNote()).isNull();
    }

    @Test
    @DisplayName("tolera que el modelo copie la línea con la anotación del prompt")
    void citaConLaAnotacionDelPrompt() {
        ParsedDiff diff = parser.parse("--- a/a.ts\n+++ b/a.ts\n@@ -1,1 +1,1 @@\n-viejo valor\n+nuevo valor\n");

        CriterionResult result = verifier.verify(
                "Criterio", met(new Evidence("a.ts", DiffSide.ADDED, 1, "[ADDED 1] nuevo valor")), diff);

        assertThat(result.status()).isEqualTo(ReviewStatus.MET);
    }

    @Test
    @DisplayName("el espacio duro no invalida una cita literal")
    void espacioDuro() {
        ParsedDiff diff =
                parser.parse("--- a/a.tsx\n+++ b/a.tsx\n@@ -1,1 +1,1 @@\n-<span>Total</span>\n+<span>Total estimado</span>\n");

        CriterionResult result = verifier.verify(
                "Criterio", met(new Evidence("a.tsx", DiffSide.ADDED, 1, "<span>Total estimado</span>")), diff);

        assertThat(result.status()).isEqualTo(ReviewStatus.MET);
    }

    @Test
    @DisplayName("una cita al lado CONTEXT también se verifica")
    void ladoContexto() {
        ParsedDiff diff =
                parser.parse("--- a/a.ts\n+++ b/a.ts\n@@ -1,2 +1,2 @@\n export const config = {\n-  modo: 'viejo'\n+  modo: 'nuevo'\n");

        CriterionResult result = verifier.verify(
                "Criterio",
                new ModelVerdict(ReviewStatus.NOT_MET, "No cambia.", new Evidence("a.ts", DiffSide.CONTEXT, 1, "export const config")),
                diff);

        assertThat(result.status()).isEqualTo(ReviewStatus.NOT_MET);
    }
}
