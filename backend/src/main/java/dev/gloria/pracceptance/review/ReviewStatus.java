package dev.gloria.pracceptance.review;

/** The verdict on one acceptance criterion. */
public enum ReviewStatus {
    /** Met: the diff satisfies it, and the citation backing that is verified. */
    MET,
    /** Not met: the diff violates it, and the citation proving that is verified. */
    NOT_MET,
    /**
     * Not verified: the diff gives too little context, or the model's citation matches no line of
     * the diff. Any verdict that cannot be checked ends up here.
     */
    NOT_VERIFIED
}
