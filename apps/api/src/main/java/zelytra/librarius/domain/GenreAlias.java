package zelytra.librarius.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A provider wording pointing at a canonical {@link Genre}.
 *
 * <p>Seeded by the migration and never written at runtime: it is reference data, the place
 * where "Shounen is Shonen" and "Juvenile fiction is Jeunesse" are stated once, for the
 * backfill of the existing rows and for every genre written afterwards alike.
 *
 * <p>{@link #alias} is itself a code — the output of {@code GenreNormalizer.code()} on the
 * raw wording — so resolving a wording is one lookup on the primary key.
 */
@Entity
@Table(name = "genre_alias")
public class GenreAlias {

    @Id
    @Column(length = 64)
    public String alias;

    @Column(nullable = false, length = 64)
    public String code;
}
