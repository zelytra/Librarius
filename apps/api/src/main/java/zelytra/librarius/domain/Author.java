package zelytra.librarius.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A person credited on a {@link Work}.
 *
 * <p>Shared catalog data, like {@link Work}, {@link Edition} and {@link Series}: what belongs
 * to a user is the follow ({@link AuthorFollow}), nothing else.
 *
 * <p>{@link #nameKey} is the identity — the fold of {@link #name} produced by
 * {@code AuthorNormalizer} — and {@link #name} only the spelling to show, the same split
 * {@link Genre} makes between its code and its label. Two credits resolve to this row when
 * they fold alike, and only then: there is no alias table relating names, and no attempt to
 * tell two people of the same name apart. See {@code V13__author_entities.sql} for what that
 * buys and what it costs.
 *
 * <p>No {@code kind}: a {@link Series} is scoped by kind because a manga and a novel may
 * share a title, whereas an author writing both is one person.
 */
@Entity
@Table(name = "author")
public class Author {

    @Id
    @GeneratedValue
    public UUID id;

    @Column(nullable = false, length = 512)
    public String name;

    @Column(name = "name_key", nullable = false, length = 512, unique = true)
    public String nameKey;

    /** Portrait, {@code null} until a provider supplies one — free text names no picture. */
    @Column(name = "photo_url", length = 1024)
    public String photoUrl;

    /**
     * Catalog this author was taken from, {@code null} when the row was folded out of a
     * free-text credit — which is every row the backfill of V13 created.
     *
     * <p>Held together with {@link #providerRef}, as on {@link Work} and {@link Edition}
     * since V12: {@code ck_author_provider_reference} refuses half a pair. This is where the
     * answer to two writers sharing a name eventually lands — a catalogue identifier tells
     * them apart, a spelling never will.
     */
    @Column(length = 32)
    public String provider;

    /** Identifier of the author in {@link #provider}'s catalog. Filled with it, or not at all. */
    @Column(name = "provider_ref", length = 255)
    public String providerRef;

    @Column(name = "created_at", insertable = false, updatable = false)
    public OffsetDateTime createdAt;
}
