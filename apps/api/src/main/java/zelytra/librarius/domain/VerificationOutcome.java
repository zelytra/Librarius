package zelytra.librarius.domain;

/** Whether a {@link CatalogEdit} was checked against a store page (#620), and how it went. */
public enum VerificationOutcome {
    /** No new sale link to check against. */
    NOT_RUN,
    /** A store page carries the book's ISBN and title. */
    PASSED,
    /** No store page could confirm the book — {@code verificationDetail} says why. */
    FAILED
}
