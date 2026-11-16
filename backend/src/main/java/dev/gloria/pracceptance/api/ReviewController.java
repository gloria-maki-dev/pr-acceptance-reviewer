package dev.gloria.pracceptance.api;

import dev.gloria.pracceptance.review.ReviewResult;
import dev.gloria.pracceptance.review.ReviewService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** El único endpoint de la aplicación. */
@RestController
@RequestMapping("/api/reviews")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @PostMapping
    public ReviewResult review(@Valid @RequestBody ReviewRequest request) {
        return reviewService.review(request.acceptanceCriteria(), request.diff());
    }
}
