package dev.gloria.pracceptance.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * What the screen sends: the acceptance criteria and the diff.
 *
 * <p>The limits are not decoration: they stop an accidental paste from sending half a repository
 * to the model.
 */
public record ReviewRequest(
        @NotEmpty(message = "At least one acceptance criterion is required.")
                @Size(max = 20, message = "At most 20 criteria per review.")
                List<
                                @NotBlank(message = "One of the criteria is empty.")
                                @Size(max = 1000, message = "A criterion cannot exceed 1000 characters.")
                                String>
                        acceptanceCriteria,
        @NotBlank(message = "The diff is required.")
                @Size(max = 200_000, message = "The diff cannot exceed 200,000 characters.")
                String diff) {}
