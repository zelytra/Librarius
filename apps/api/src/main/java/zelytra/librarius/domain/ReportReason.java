package zelytra.librarius.domain;

/**
 * Why a member is flagging a catalog object: a short, closed picklist the client offers, with
 * an optional free-text comment carrying the specifics.
 *
 * <p>Kept coarse on purpose — a finer taxonomy would be guesswork until the revocation logic
 * (#195) and a future admin view say what they actually distinguish. {@link #OTHER} plus the
 * comment covers whatever the four named reasons miss.
 */
public enum ReportReason {

    /** The cover shown belongs to another book, or to another edition. */
    WRONG_COVER,

    /** A wrong author, title, description or other bibliographic field. */
    WRONG_INFO,

    /** This object duplicates one already in the catalog. */
    DUPLICATE,

    /** Anything the four above do not name — spelled out in the comment. */
    OTHER
}
