package dev.gloria.pracceptance.review;

import dev.gloria.pracceptance.diff.ParsedDiff;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** Builds the two prompts sent to the model. */
@Component
public class PromptBuilder {

    private static final String SYSTEM_PROMPT =
            """
            You review pull requests. You are given the acceptance criteria of a ticket and an
            annotated diff, and you decide whether the diff satisfies each criterion.

            Each criterion comes with an identifier (C1, C2, C3...). The diff comes with one line
            per change, in this shape:

                FILE: path/to/file.tsx
                [ADDED 20] contents of line 20 of the new file
                [REMOVED 20] contents of line 20 of the old file
                [CONTEXT 18] unchanged line, numbered as in the new file

            Rules, in order of importance:

            1. Return one verdict per identifier, skipping none, even when you have nothing to say
               about it: in that case the status is NOT_VERIFIED. The statuses are:
               - MET: the diff satisfies the criterion, and you can point at the line that proves it.
               - NOT_MET: the diff violates the criterion, and you can point at the line that proves it.
               - NOT_VERIFIED: the diff does not give enough context to decide.
            2. MET and NOT_MET require a citation. If you cannot point at a specific line of the
               diff, the status is NOT_VERIFIED.
            3. Citations are copied from the annotated diff, never invented: "file" is the path
               after FILE, "side" is ADDED, REMOVED or CONTEXT, "lineNumber" is the number in
               brackets, and "snippet" is text copied literally from that line. A citation that
               does not match the diff is thrown out and the criterion ends up Not verified, so do
               not gamble: when in doubt, use NOT_VERIFIED.
            4. The diff only shows what changed. Assume nothing about the rest of a file or about
               files that do not appear. If a criterion says something must NOT change and that
               something is nowhere in the diff, it is met: the diff is everything that changed.
            5. "explanation" is one short, concrete sentence saying what you saw. Do not restate
               the criterion and do not talk about yourself.

            Answer with a single JSON object, with no text around it and no code fence:

            {"results":[{"criterionId":"C1","status":"MET","explanation":"...",
              "evidence":{"file":"...","side":"ADDED","lineNumber":1,"snippet":"..."}}]}

            "evidence" is null when the status is NOT_VERIFIED.
            """;

    public String systemPrompt() {
        return SYSTEM_PROMPT;
    }

    /** The identifiers sent to the model, in the order of the criteria. */
    public static List<String> criterionIds(int criteriaCount) {
        List<String> ids = new ArrayList<>(criteriaCount);
        for (int i = 1; i <= criteriaCount; i++) {
            ids.add("C" + i);
        }
        return List.copyOf(ids);
    }

    public String userPrompt(List<String> criteria, ParsedDiff diff) {
        List<String> ids = criterionIds(criteria.size());

        StringBuilder out = new StringBuilder("ACCEPTANCE CRITERIA\n\n");
        for (int i = 0; i < criteria.size(); i++) {
            out.append(ids.get(i)).append(". ").append(criteria.get(i).strip()).append('\n');
        }
        out.append("\nANNOTATED DIFF\n\n").append(diff.toAnnotatedText());
        out.append("\nReturn one verdict for each of these identifiers: ")
                .append(String.join(", ", ids))
                .append(".\n");
        return out.toString();
    }
}
