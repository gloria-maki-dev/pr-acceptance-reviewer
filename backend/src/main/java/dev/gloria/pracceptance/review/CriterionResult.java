package dev.gloria.pracceptance.review;

/**
 * The result for one criterion, already checked against the diff.
 *
 * @param criterion the acceptance criterion, as the reviewer wrote it
 * @param status the final verdict, after checking the citation
 * @param explanation the model's explanation, or {@code null} if it never gave one
 * @param evidence the model's citation, or {@code null} if it cited nothing
 * @param verificationNote why the citation could not be verified, or {@code null} if it held up
 */
public record CriterionResult(
        String criterion, ReviewStatus status, String explanation, Evidence evidence, String verificationNote) {}
