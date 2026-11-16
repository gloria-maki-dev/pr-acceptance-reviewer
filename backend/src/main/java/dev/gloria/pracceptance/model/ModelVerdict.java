package dev.gloria.pracceptance.model;

import dev.gloria.pracceptance.review.Evidence;
import dev.gloria.pracceptance.review.ReviewStatus;

/**
 * What the model says about one criterion, before anything is checked.
 *
 * <p>Keeping this apart from {@link dev.gloria.pracceptance.review.CriterionResult} is the heart
 * of the project: the model's verdict is a proposal, and it only becomes a result once the
 * citation has been found in the diff.
 */
public record ModelVerdict(ReviewStatus status, String explanation, Evidence evidence) {}
