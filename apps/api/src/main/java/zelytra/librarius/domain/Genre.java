package zelytra.librarius.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * A genre of the shared catalog.
 *
 * <p>{@link #code} is the identity: it is what the statistics group on, what the collection
 * filters on, and what a client puts in a URL. {@link #label} is only what a screen shows,
 * and carries no meaning of its own — two spellings of the same genre share a code, never a
 * label.
 *
 * <p>Codes are produced by {@code GenreNormalizer} from whatever wording a provider or the
 * manual form used.
 */
@Entity
@Table(name = "genre")
public class Genre {

    @Id
    @GeneratedValue
    public UUID id;

    @Column(nullable = false, length = 64, unique = true)
    public String code;

    @Column(nullable = false, length = 64)
    public String label;
}
