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
 * Checks the model's citation against the real diff.
 *
 * <p>This class is what makes the result checkable. A conclusive verdict - Met or Not met - only
 * survives if its citation points at a line that exists, on the file and side it claims, whose
 * text contains the quoted snippet. When something does not line up the verdict drops to
 * {@link ReviewStatus#NOT_VERIFIED} with a note explaining what failed.
 *
 * <p>The explanation and the citation are kept on the result so the interface can decide what to
 * show; when there is a note, the right move is to hide both, because the explanation rested on
 * a citation that does not hold up.
 */
@Component
public class EvidenceVerifier {

    /**
     * A snippet shorter than this distinguishes nothing: citing ";" would support any verdict at
     * all over almost any line of code.
     */
    private static final int MIN_SNIPPET_LENGTH = 4;

    /** The annotation the prompt uses to show the diff to the model: "[ADDED 20] ". */
    private static final Pattern ANNOTATION = Pattern.compile("^\\[(?:ADDED|REMOVED|CONTEXT) \\d+]\\s?");

    /** Whitespace of every kind, including the no-break space that turns up in JSX. */
    private static final Pattern WHITESPACE = Pattern.compile("[\\s\\p{Z}]+");

    public CriterionResult verify(String criterion, ModelVerdict verdict, ParsedDiff diff) {
        Evidence evidence = verdict.evidence();

        if (evidence == null) {
            if (verdict.status() == ReviewStatus.NOT_VERIFIED) {
                return new CriterionResult(criterion, ReviewStatus.NOT_VERIFIED, verdict.explanation(), null, null);
            }
            return unverified(
                    criterion,
                    verdict,
                    "The model concluded without citing any line of the diff, so there is nothing to check.");
        }

        Optional<DiffFile> file = diff.file(evidence.file());
        if (file.isEmpty()) {
            return unverified(
                    criterion,
                    verdict,
                    "The diff does not contain the file `" + evidence.file() + "` the model cites.");
        }

        List<DiffLine> candidates = file.get().lines().stream()
                .filter(l -> l.side() == evidence.side() && l.lineNumber() == evidence.lineNumber())
                .toList();
        if (candidates.isEmpty()) {
            return unverified(
                    criterion,
                    verdict,
                    "`" + file.get().path() + "` has no " + evidence.side() + " line "
                            + evidence.lineNumber() + " in this diff.");
        }

        String snippet = normalise(stripMarkers(evidence.snippet()));
        if (snippet.length() < MIN_SNIPPET_LENGTH) {
            return unverified(
                    criterion,
                    verdict,
                    "The snippet cited (`" + evidence.snippet().strip()
                            + "`) is too short to prove anything.");
        }

        boolean matches = candidates.stream().anyMatch(l -> normalise(l.content()).contains(snippet));
        if (!matches) {
            return unverified(
                    criterion,
                    verdict,
                    "The snippet does not match line " + evidence.lineNumber() + " of `"
                            + file.get().path() + "`, which reads: " + candidates.get(0).content().strip());
        }

        return new CriterionResult(criterion, verdict.status(), verdict.explanation(), evidence, null);
    }

    private static CriterionResult unverified(String criterion, ModelVerdict verdict, String note) {
        return new CriterionResult(
                criterion, ReviewStatus.NOT_VERIFIED, verdict.explanation(), verdict.evidence(), note);
    }

    /**
     * The model may quote a whole line or a fragment of one, with different indentation, with the
     * diff's {@code +}/{@code -} prefix, or with the {@code [ADDED 20]} annotation it was shown.
     * None of that should invalidate an otherwise correct citation.
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
