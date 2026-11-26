package zelytra.librarius.contribution;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.BadRequestException;
import zelytra.librarius.author.AuthorService;
import zelytra.librarius.domain.CatalogEditTarget;
import zelytra.librarius.domain.Edition;
import zelytra.librarius.domain.Kind;
import zelytra.librarius.domain.Series;
import zelytra.librarius.domain.SeriesStatus;
import zelytra.librarius.genre.GenreService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * The fields a member may edit (#618), per target: how each is read off the record, parsed from
 * what the form sent, and written back.
 *
 * <p>Every value travels as a string in one canonical form — trimmed, blank meaning "none",
 * numbers and dates in their plain ISO spelling — so a diff compares two strings and a revert
 * writes back exactly what was read. An {@code EDITION} carries the fields of its work: the
 * reader edits one book, whichever table a field lives in.
 */
@ApplicationScoped
public class CatalogFields {

    @Inject
    AuthorService authors;

    @Inject
    GenreService genres;

    @Inject
    EntityManager em;

    @Inject
    zelytra.librarius.domain.repository.SeriesRepository seriesRepository;

    /** One editable field of a record of type {@code R}. */
    private record Field<R>(Function<R, String> read, UnaryOperator<String> parse, BiConsumer<R, String> write) {
    }

    private final Map<String, Field<Edition>> edition = new LinkedHashMap<>();
    private final Map<String, Field<Series>> series = new LinkedHashMap<>();

    public CatalogFields() {
        edition.put("title", new Field<>(e -> e.work.title, required(text(512)), (e, v) -> e.work.title = v));
        edition.put("authors", new Field<>(e -> e.work.authorsText, text(512), this::writeAuthors));
        edition.put("synopsis", new Field<>(e -> e.work.synopsis, text(10_000), (e, v) -> e.work.synopsis = v));
        edition.put("genres", new Field<>(e -> e.work.genresText, text(512), this::writeGenres));
        // The shared kind (#650); a reader's own filing elsewhere (#454) is another work, untouched.
        edition.put("kind", new Field<>(e -> e.work.kind.name(), CatalogFields::kind, this::writeWorkKind));
        edition.put("originalYear", new Field<>(e -> str(e.work.originalYear), integer(-3000, 2100),
                (e, v) -> e.work.originalYear = toInt(v)));
        edition.put("volumeNumber", new Field<>(e -> str(e.work.volumeNumber), integer(0, 100_000),
                (e, v) -> e.work.volumeNumber = toInt(v)));
        edition.put("isbn13", new Field<>(e -> e.isbn13, isbn("97[89]\\d{10}", 13), (e, v) -> e.isbn13 = v));
        edition.put("isbn10", new Field<>(e -> e.isbn10, isbn("\\d{9}[\\dX]", 10), (e, v) -> e.isbn10 = v));
        edition.put("publisher", new Field<>(e -> e.publisher, text(255), (e, v) -> e.publisher = v));
        edition.put("language", new Field<>(e -> e.language, pattern("[a-z]{2,3}"), (e, v) -> e.language = v));
        edition.put("pageCount", new Field<>(e -> str(e.pageCount), integer(1, 100_000), (e, v) -> e.pageCount = toInt(v)));
        edition.put("format", new Field<>(e -> e.format, text(32), (e, v) -> e.format = v));
        edition.put("releaseDate", new Field<>(e -> str(e.releaseDate), CatalogFields::date,
                (e, v) -> e.releaseDate = v == null ? null : LocalDate.parse(v)));
        edition.put("coverUrl", new Field<>(e -> e.coverUrl, CatalogFields::imageUrl, (e, v) -> e.coverUrl = v));
        edition.put("listPrice", new Field<>(e -> price(e.listPrice), CatalogFields::price, CatalogFields::writePrice));

        series.put("title", new Field<>(s -> s.title, required(text(512)), this::writeSeriesTitle));
        series.put("kind", new Field<>(s -> s.kind.name(), CatalogFields::kind, this::writeSeriesKind));
        series.put("originalTitle", new Field<>(s -> s.originalTitle, text(512), (s, v) -> s.originalTitle = v));
        series.put("totalVolumes", new Field<>(s -> str(s.totalVolumes), integer(1, 10_000),
                (s, v) -> s.totalVolumes = toInt(v)));
        series.put("status", new Field<>(s -> s.status == null ? null : s.status.name(), CatalogFields::seriesStatus,
                (s, v) -> s.status = v == null ? null : SeriesStatus.valueOf(v)));
        series.put("synopsis", new Field<>(s -> s.synopsis, text(10_000), (s, v) -> s.synopsis = v));
        series.put("coverUrl", new Field<>(s -> s.coverUrl, CatalogFields::imageUrl, (s, v) -> s.coverUrl = v));
    }

    /** Every editable field's current value, in the canonical form. */
    public Map<String, String> read(CatalogEditTarget type, Object record) {
        Map<String, String> values = new LinkedHashMap<>();
        fields(type).forEach((name, field) -> values.put(name, read(field, record)));
        return values;
    }

    public String read(CatalogEditTarget type, Object record, String name) {
        return read(field(type, name), record);
    }

    /** What {@code raw} canonically means for {@code name} — a 400 for an unknown field or a bad value. */
    public String parse(CatalogEditTarget type, String name, String raw) {
        return field(type, name).parse().apply(raw);
    }

    public void write(CatalogEditTarget type, Object record, String name, String value) {
        @SuppressWarnings("unchecked")
        Field<Object> field = (Field<Object>) field(type, name);
        field.write().accept(record, value);
    }

    public boolean knows(CatalogEditTarget type, String name) {
        return fields(type).containsKey(name);
    }

    @SuppressWarnings("unchecked")
    private static String read(Field<?> field, Object record) {
        return ((Field<Object>) field).read().apply(record);
    }

    private Map<String, ? extends Field<?>> fields(CatalogEditTarget type) {
        return type == CatalogEditTarget.EDITION ? edition : series;
    }

    private Field<?> field(CatalogEditTarget type, String name) {
        Field<?> field = fields(type).get(name);
        if (field == null) {
            throw new BadRequestException("Unknown field: " + name);
        }
        return field;
    }

    // ── Writers that keep the denormalised data in step ─────────────────────

    /** The credit line and the people it names travel together, as when the work was created. */
    private void writeAuthors(Edition e, String value) {
        e.work.authorsText = value;
        e.work.authors.clear();
        e.work.authors.addAll(authors.resolve(value));
    }

    private void writeGenres(Edition e, String value) {
        e.work.genresText = value;
        e.work.genres.clear();
        e.work.genres.addAll(genres.resolve(value));
    }

    /** {@code work.series_title} is the denormalised label of the series (V4): renamed with it. */
    private void writeSeriesTitle(Series s, String value) {
        s.title = value;
        em.createQuery("update Work w set w.seriesTitle = :title where w.series.id = :id")
                .setParameter("title", value)
                .setParameter("id", s.id)
                .executeUpdate();
    }

    /**
     * A book changing kind leaves a series of the old kind for the one of its new kind with the
     * same title, found or founded: a series holds works of one kind ({@code uq_series_kind_title}).
     */
    private void writeWorkKind(Edition e, String value) {
        Kind kind = Kind.valueOf(value);
        e.work.kind = kind;
        Series current = e.work.series;
        if (current != null && current.kind != kind) {
            e.work.series = seriesRepository.findByKindAndTitle(kind, current.title).orElseGet(() -> {
                Series moved = new Series();
                moved.kind = kind;
                moved.title = current.title;
                seriesRepository.persist(moved);
                return moved;
            });
            e.work.seriesTitle = e.work.series.title;
        }
    }

    /** A series changes kind as a whole: every one of its volumes follows, so the run stays one. */
    private void writeSeriesKind(Series s, String value) {
        Kind kind = Kind.valueOf(value);
        s.kind = kind;
        em.createQuery("update Work w set w.kind = :kind where w.series.id = :id")
                .setParameter("kind", kind)
                .setParameter("id", s.id)
                .executeUpdate();
    }

    private static void writePrice(Edition e, String value) {
        e.listPrice = value == null ? null : new BigDecimal(value);
        if (value != null && e.listPriceCurrency == null) {
            e.listPriceCurrency = "EUR";
        }
    }

    // ── Parsers ─────────────────────────────────────────────────────────────

    private static String blankToNull(String raw) {
        return raw == null || raw.isBlank() ? null : raw.trim();
    }

    private static UnaryOperator<String> text(int max) {
        return raw -> {
            String value = blankToNull(raw);
            if (value != null && value.length() > max) {
                throw new BadRequestException("Too long: at most " + max + " characters");
            }
            return value;
        };
    }

    private static UnaryOperator<String> required(UnaryOperator<String> parser) {
        return raw -> {
            String value = parser.apply(raw);
            if (value == null) {
                throw new BadRequestException("A title cannot be empty");
            }
            return value;
        };
    }

    private static UnaryOperator<String> integer(int min, int max) {
        return raw -> {
            String value = blankToNull(raw);
            if (value == null) {
                return null;
            }
            try {
                int number = Integer.parseInt(value);
                if (number < min || number > max) {
                    throw new BadRequestException("Out of range: " + value);
                }
                return Integer.toString(number);
            } catch (NumberFormatException e) {
                throw new BadRequestException("Not a whole number: " + value);
            }
        };
    }

    private static UnaryOperator<String> pattern(String regex) {
        return raw -> {
            String value = blankToNull(raw);
            if (value != null) {
                value = value.toLowerCase(Locale.ROOT);
                if (!value.matches(regex)) {
                    throw new BadRequestException("Invalid value: " + value);
                }
            }
            return value;
        };
    }

    /** Hyphens and spaces are how an ISBN is printed, not part of it. */
    private static UnaryOperator<String> isbn(String regex, int length) {
        return raw -> {
            String value = blankToNull(raw);
            if (value == null) {
                return null;
            }
            value = value.replaceAll("[-\\s]", "").toUpperCase(Locale.ROOT);
            if (value.length() != length || !value.matches(regex)) {
                throw new BadRequestException("Invalid ISBN: " + raw);
            }
            return value;
        };
    }

    private static String date(String raw) {
        String value = blankToNull(raw);
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(value).toString();
        } catch (DateTimeParseException e) {
            throw new BadRequestException("Not an ISO date: " + value);
        }
    }

    private static String price(String raw) {
        String value = blankToNull(raw);
        if (value == null) {
            return null;
        }
        try {
            BigDecimal price = new BigDecimal(value.replace(',', '.'));
            if (price.signum() < 0 || price.compareTo(new BigDecimal("999999.99")) > 0) {
                throw new BadRequestException("Out of range: " + value);
            }
            return price.setScale(2, RoundingMode.HALF_UP).toPlainString();
        } catch (NumberFormatException e) {
            throw new BadRequestException("Not a price: " + value);
        }
    }

    private static String price(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /** A cover is an image a browser loads: http(s) only, never {@code javascript:} or {@code data:}. */
    private static String imageUrl(String raw) {
        String value = blankToNull(raw);
        if (value == null) {
            return null;
        }
        if (value.length() > 1024 || !value.matches("(?i)https?://[^\\s]+")) {
            throw new BadRequestException("Not an image address: " + value);
        }
        return value;
    }

    private static String kind(String raw) {
        String value = blankToNull(raw);
        if (value == null) {
            throw new BadRequestException("A kind cannot be empty");
        }
        try {
            return Kind.valueOf(value.toUpperCase(Locale.ROOT)).name();
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Unknown kind: " + value);
        }
    }

    private static String seriesStatus(String raw) {
        String value = blankToNull(raw);
        if (value == null) {
            return null;
        }
        try {
            return SeriesStatus.valueOf(value.toUpperCase(Locale.ROOT)).name();
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Unknown series status: " + value);
        }
    }

    private static String str(Object value) {
        return value == null ? null : value.toString();
    }

    private static Integer toInt(String value) {
        return value == null ? null : Integer.valueOf(value);
    }
}
