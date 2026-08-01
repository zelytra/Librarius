package zelytra.librarius.domain;

/**
 * Nature of a work, and the taxonomy {@code work.kind} and {@code series.kind} are stored
 * under.
 *
 * <p>The v0.8 milestone widens the catalogue past books and manga: a comic (BD), a graphic
 * novel and an audiobook are each their own medium here. The values are added rather than
 * replaced with a second taxonomy — {@code kind} is a bare string column with nothing to
 * widen, and nothing downstream (the work-matching key, the library filters, the Panache
 * entities) assumes exactly two of them, so growing the enum keeps one consistent rule.
 *
 * <p>An audiobook narration of a novel is therefore its own {@code work}, not a second
 * {@code edition} of the print one. That models an audiobook less faithfully than an
 * edition-level format would, but it fits {@code COMIC} and {@code GRAPHIC_NOVEL} — an
 * adaptation is a different creative work, not a different format of the same text — and
 * one rule for the three additions is worth more here than a special case for one.
 */
public enum Kind {
    BOOK,
    MANGA,
    COMIC,
    GRAPHIC_NOVEL,
    AUDIOBOOK
}
