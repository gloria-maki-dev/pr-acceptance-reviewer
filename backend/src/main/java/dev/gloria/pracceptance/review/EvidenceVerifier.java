package dev.gloria.pracceptance.review;

import dev.gloria.pracceptance.diff.DiffFile;
import dev.gloria.pracceptance.diff.DiffLine;
import dev.gloria.pracceptance.diff.ParsedDiff;
import dev.gloria.pracceptance.model.ModelVerdict;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Comprueba la cita del modelo contra el diff de verdad.
 *
 * <p>Esta clase es la que hace que el resultado sea comprobable. Un veredicto concluyente —Cumplido
 * o No cumplido— solo sobrevive si su cita apunta a una línea que existe, en el archivo y el lado
 * que dice, y cuyo texto contiene el fragmento citado. Si algo no cuadra, el veredicto baja a
 * {@link ReviewStatus#UNVERIFIED} con una nota que explica qué falló.
 *
 * <p>La explicación y la cita se conservan en el resultado para que la interfaz pueda decidir qué
 * enseñar; cuando hay nota, lo correcto es ocultarlas, porque la explicación se apoyaba en una
 * cita que no se sostiene.
 */
@Component
public class EvidenceVerifier {

    /**
     * Un fragmento más corto que esto no distingue nada: citar «;» sostendría cualquier veredicto
     * sobre casi cualquier línea de código.
     */
    private static final int MIN_SNIPPET_LENGTH = 4;

    /** La anotación con la que el prompt le enseña el diff al modelo: «[ADDED 20] ». */
    private static final Pattern ANNOTATION = Pattern.compile("^\\[(?:ADDED|REMOVED|CONTEXT) \\d+]\\s?");

    /** Espacios de todo tipo, incluido el espacio duro U+00A0 que aparece en JSX. */
    private static final Pattern WHITESPACE = Pattern.compile("[\\s\\p{Z}]+");

    public CriterionResult verify(String criterion, ModelVerdict verdict, ParsedDiff diff) {
        Evidence evidence = verdict.evidence();

        if (evidence == null) {
            if (verdict.status() == ReviewStatus.UNVERIFIED) {
                return new CriterionResult(criterion, ReviewStatus.UNVERIFIED, verdict.explanation(), null, null);
            }
            return unverified(
                    criterion,
                    verdict,
                    "El modelo concluyó sin citar ninguna línea del diff, así que no hay nada que comprobar.");
        }

        Optional<DiffFile> file = diff.file(evidence.file());
        if (file.isEmpty()) {
            return unverified(
                    criterion,
                    verdict,
                    "El diff no contiene el archivo «" + evidence.file() + "» que cita el modelo.");
        }

        List<DiffLine> candidates = file.get().lines().stream()
                .filter(l -> l.side() == evidence.side() && l.lineNumber() == evidence.lineNumber())
                .toList();
        if (candidates.isEmpty()) {
            return unverified(
                    criterion,
                    verdict,
                    "En «" + file.get().path() + "» no hay ninguna línea " + evidence.lineNumber()
                            + " del lado " + evidence.side() + " en este diff.");
        }

        String snippet = normalise(stripMarkers(evidence.snippet()));
        if (snippet.length() < MIN_SNIPPET_LENGTH) {
            return unverified(
                    criterion,
                    verdict,
                    "El fragmento citado («" + evidence.snippet().strip()
                            + "») es demasiado corto para demostrar nada.");
        }

        boolean matches = candidates.stream().anyMatch(l -> normalise(l.content()).contains(snippet));
        if (!matches) {
            return unverified(
                    criterion,
                    verdict,
                    "El fragmento citado no coincide con la línea " + evidence.lineNumber() + " de «"
                            + file.get().path() + "», que dice: " + candidates.get(0).content().strip());
        }

        return new CriterionResult(criterion, verdict.status(), verdict.explanation(), evidence, null);
    }

    private static CriterionResult unverified(String criterion, ModelVerdict verdict, String note) {
        return new CriterionResult(
                criterion, ReviewStatus.UNVERIFIED, verdict.explanation(), verdict.evidence(), note);
    }

    /**
     * El modelo puede citar la línea entera o solo un trozo, con otra sangría, con el prefijo
     * {@code +}/{@code -} del diff, o con la anotación {@code [ADDED 20]} con la que se la
     * enseñamos. Nada de eso debería invalidar una cita por lo demás correcta.
     */
    private static String stripMarkers(String snippet) {
        String trimmed = ANNOTATION.matcher(snippet.strip()).replaceFirst("").strip();
        if (trimmed.startsWith("+") || trimmed.startsWith("-")) {
            return trimmed.substring(1);
        }
        return trimmed;
    }

    private static String normalise(String text) {
        return WHITESPACE.matcher(text).replaceAll(" ").strip();
    }
}
