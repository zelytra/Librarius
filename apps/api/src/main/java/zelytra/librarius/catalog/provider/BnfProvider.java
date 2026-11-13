package zelytra.librarius.catalog.provider;

import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import zelytra.librarius.catalog.CatalogProvider;
import zelytra.librarius.catalog.CatalogQuery;
import zelytra.librarius.catalog.CatalogResult;
import zelytra.librarius.catalog.VolumeParser;
import zelytra.librarius.domain.Kind;
import zelytra.librarius.scan.Isbn;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.text.Normalizer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Second book catalog provider, backed by the BnF general catalogue over SRU (no API key).
 *
 * <p>Open Library is the Internet Archive's catalogue and its French holdings are thin; the
 * BnF receives the legal deposit of everything published in France, so the two overlap
 * little and complement each other on exactly the collection this application is for. It is
 * also open without registration, quota or key, which is what keeps the deployment free of
 * a credential to carry around.
 *
 * <p>Records are asked in UNIMARC, the format the BnF catalogues in. Dublin Core, which this
 * class read before, flattens the record into a title sentence and a list of creators with
 * their roles glued on: the translator was credited as an author, the volume number was
 * buried in the title and the series was lost. UNIMARC keeps each of those in a field of its
 * own, and says whether the BnF holds a cover image.
 */
@ApplicationScoped
public class BnfProvider implements CatalogProvider {

    private static final String SRU_VERSION = "1.2";

    private static final String SRU_OPERATION = "searchRetrieve";

    private static final String RECORD_SCHEMA = "unimarcxchange";

    /** Namespace of the {@code <mxc:record>} the BnF wraps each UNIMARC record in. */
    private static final String MARC_NS = "info:lc/xmlns/marcxchange-v2";

    /**
     * Page asked of the BnF for each query. Also the size fetched when the year is being
     * filtered here rather than by the catalogue, the same trade AniList's author search makes.
     * A UNIMARC record weighs several kilobytes, so it is kept below what Dublin Core allowed.
     */
    private static final int MAX_RECORDS = 20;

    /**
     * The BnF indexes languages as MARC codes, like Open Library, while a client holds the
     * ISO 639-1 code of the language it displays. The mapping belongs to the provider, so
     * this deliberately repeats {@code OpenLibraryProvider}'s rather than sharing it: the two
     * catalogues are free to disagree, and the day one of them does, a shared constant would
     * have to be unpicked.
     */
    private static final Map<String, String> MARC_LANGUAGES = Map.of(
            "fr", "fre", "en", "eng", "ja", "jpn", "de", "ger", "es", "spa", "it", "ita");

    /** First four consecutive digits of a date ("DL 2001", "cop. 2006-", "[2015]"). */
    private static final Pattern YEAR = Pattern.compile("\\d{4}");

    /** "1 vol. (192 p.)", "1 volume 181 p", "100 p." — not the "[ca 208] p." of an estimate. */
    private static final Pattern PAGES = Pattern.compile("(?<![\\d\\[])(\\d{1,5})\\s*p\\b");

    /** A part number that is only a number, possibly behind its marker: "T.. 31", "[5]", "Livre 1". */
    private static final Pattern PLAIN_NUMBER = Pattern.compile(
            "(?iu)^\\W*(?:t\\.*|tome|vol\\.?|volume|livre|n°)?\\s*(\\d{1,4})\\W*$");

    /** "L' Etranger": the BnF leaves a space after the apostrophe of an elision. */
    private static final Pattern ELISION_SPACE = Pattern.compile("(?<=\\p{L})(['’])\\s+(?=\\p{L})");

    private static final Pattern TRAILING_PUNCTUATION = Pattern.compile("[\\s/:;,=]+$|(?<!\\.)\\.$");

    /** Seen on the live catalogue: the series is a shonen/seinen/… collection, or a manga house. */
    private static final Pattern MANGA_HINT = Pattern.compile(
            "\\b(?:shonen|shojo|seinen|josei|mangas?)\\b");

    /**
     * Publishers whose whole catalogue is manga. Matched on the normalised publisher only: a
     * Japanese-looking author or an original title would guess, and a guess puts a French
     * novel on the manga shelf.
     */
    private static final Pattern MANGA_PUBLISHER = Pattern.compile(
            "\\b(?:pika|kana|ki[- ]?oon|kurokawa|glenat manga|tonkam|kaze|crunchyroll|panini manga|"
                    + "mangetsu|akata|doki[- ]?doki|nobi[- ]?nobi|ototo|taifu|komikku|noeve|meian|"
                    + "vega|soleil manga)\\b");

    /** What a 200 $f/$g says when the credited people drew or scripted rather than wrote. */
    private static final Pattern COMIC_RESPONSIBILITY = Pattern.compile(
            "\\b(?:scenario|dessins?|dibujo|guion)\\b");

    @Inject
    @RestClient
    BnfClient client;

    /** Absolute deadline of one call, whatever the BnF does with the socket. */
    @ConfigProperty(name = "librarius.catalog.provider.call-timeout", defaultValue = "12S")
    Duration callTimeout;

    @Override
    public String name() {
        return "bnf";
    }

    @Override
    public Kind kind() {
        return Kind.BOOK;
    }

    @Override
    public List<CatalogResult> search(CatalogQuery query, int limit) {
        List<String> queries = cqlQueries(query);
        if (queries.isEmpty()) {
            // Only a year was given. Asking the BnF for everything printed that year would
            // answer, and the answer would be noise.
            return List.of();
        }
        int size = pageSize(query, limit);
        List<String> answers;
        try {
            // The queries run side by side and each recovers on its own: the phrase query is
            // the precise one and the all-words query the forgiving one, so losing either
            // because the other failed would throw away a usable answer. Each carries its own
            // deadline, then the join is only a safety net.
            List<Uni<String>> calls = queries.stream().map(cql -> call(cql, size)).toList();
            answers = Uni.join().all(calls).andFailFast().await().atMost(callTimeout.plusSeconds(2));
        } catch (Exception e) {
            // Same contract as the other two providers: a failure is no result, never an
            // exception reaching CatalogService, so the aggregate degrades to what the other
            // book provider found instead of turning into a 500.
            Log.warnf("BnF search failed: %s", e.getMessage());
            return List.of();
        }
        // The exact phrase comes first, then what only the all-words query found. The same
        // ark in both answers is the same record.
        Map<String, CatalogResult> merged = new LinkedHashMap<>();
        for (String xml : answers) {
            for (CatalogResult result : parseQuietly(xml)) {
                if (query.year() != null && !query.year().equals(result.year())) {
                    continue;
                }
                String key = result.providerRef() != null ? result.providerRef() : "#" + merged.size();
                merged.putIfAbsent(key, result);
            }
        }
        // A free-text search hands back every record: CatalogService ranks the candidates of
        // every provider by relevance before trimming them to the limit.
        return merged.values().stream()
                .limit(query.text() != null ? 2L * MAX_RECORDS : limit)
                .toList();
    }

    /** One SRU call that cannot fail: an error or a missing answer is an empty string. */
    private Uni<String> call(String cql, int size) {
        return Uni.createFrom().deferred(() -> client.search(SRU_VERSION, SRU_OPERATION, cql, RECORD_SCHEMA, size))
                .ifNoItem().after(callTimeout).fail()
                .onFailure().recoverWithItem(failure -> {
                    Log.warnf("BnF query failed (%s): %s", cql, failure.getMessage());
                    return "";
                });
    }

    private static List<CatalogResult> parseQuietly(String xml) {
        if (xml == null || xml.isBlank()) {
            return List.of();
        }
        try {
            return parse(xml);
        } catch (Exception e) {
            Log.warnf("BnF answer unreadable: %s", e.getMessage());
            return List.of();
        }
    }

    @Override
    public boolean searchesByPrefix() {
        return true;
    }

    /** Legal-deposit records: one per printing, not one per book. */
    @Override
    public boolean listsWorks() {
        return false;
    }

    @Override
    public List<CatalogResult> upcoming(int limit) {
        // A legal-deposit catalogue records what has been published, not what is announced.
        return List.of();
    }

    /**
     * A full page when the year is filtered here or when free text is to be ranked by
     * CatalogService; otherwise only what is needed.
     */
    private static int pageSize(CatalogQuery query, int limit) {
        return query.year() != null || query.text() != null ? MAX_RECORDS : Math.min(limit, MAX_RECORDS);
    }

    /**
     * Renders the criteria as the CQL queries SRU expects: every criterion is a fielded term
     * on a {@code bib.*} index, joined by {@code and}, so the BnF narrows them itself.
     *
     * <p>Free text asks two questions. The exact phrase on the title ({@code adj}) answers
     * "le nom du vent" with the editions of that novel and nothing else, where the all-words
     * query puts books that merely contain those words first — the novel was not even in the
     * page. The all-words query on title or author still runs second, because a reader also
     * types an author, half a title or the words in another order. Without free text, or with
     * an ISBN, there is one question only.
     *
     * <p>The year is applied to the parsed records instead. The publication-date index is not
     * the plain year a caller supplies — an edition carries a date of the shape "DL 2001" —
     * and a term the catalogue rejects is not a narrower search, it is a diagnostic and no
     * results at all.
     */
    static List<String> cqlQueries(CatalogQuery query) {
        StringBuilder criteria = new StringBuilder();
        append(criteria, "bib.author", query.author());
        append(criteria, "bib.publisher", query.publisher());
        append(criteria, "bib.isbn", query.isbn());
        if (query.language() != null) {
            String language = query.language().toLowerCase(Locale.ROOT);
            append(criteria, "bib.language", MARC_LANGUAGES.getOrDefault(language, language));
        }
        // "tome", "vol." and "t." are the reader's words, never the catalogue's: a BnF
        // volume is titled "One Piece" with its number apart, so "one piece tome 1" matched
        // nothing at all.
        String text = query.text() == null ? "" : cleaned(VolumeParser.withoutMarkers(query.text()));
        List<String> terms = new ArrayList<>();
        if (!text.isEmpty() && query.prefixLastWord()) {
            // The reader stopped mid-word: one forgiving question, the last word truncated
            // ("nom du ven*" finds "Le nom du vent"). A phrase cannot carry a truncation.
            terms.add("bib.title all \"" + text + "*\"" + and(criteria));
        } else if (text.isEmpty()) {
            if (!criteria.isEmpty()) {
                terms.add(criteria.toString());
            }
        } else {
            terms.add("bib.title adj \"" + text + "\"" + and(criteria));
            terms.add("(bib.title all \"" + text + "\" or bib.author all \"" + text + "\")" + and(criteria));
        }
        // Printed text only. The general catalogue also describes video games, films, records
        // and periodicals: "one piece" answered Namco's games and a DJ remix next to the
        // manga. The index narrows most of it; toResult() drops what still slips through (a
        // box set catalogued as "multimédia multisupport").
        return terms.stream().map(term -> term + " and bib.doctype any \"a\"").toList();
    }

    private static String and(StringBuilder criteria) {
        return criteria.isEmpty() ? "" : " and " + criteria;
    }

    /**
     * Appends {@code index all "value"} to the criteria. The quotes keep a multi-word value in
     * one term, and the ones the user typed are dropped rather than escaped: a stray quote
     * would unbalance the CQL and the BnF would answer a diagnostic instead of records.
     */
    private static void append(StringBuilder cql, String index, String value) {
        if (value == null) {
            return;
        }
        String cleaned = cleaned(value);
        if (cleaned.isEmpty()) {
            return;
        }
        if (!cql.isEmpty()) {
            cql.append(" and ");
        }
        cql.append(index).append(" all \"").append(cleaned).append('"');
    }

    private static String cleaned(String value) {
        return value.replace("\"", " ").replace("\\", " ").replaceAll("\\s+", " ").trim();
    }

    /**
     * Reads the records out of the SRU envelope.
     *
     * <p>Records are found by the MARCXchange namespace and their fields by tag, which is what
     * identifies them; the prefix the BnF binds to it is not, and may change. The SRU envelope
     * has a {@code record} of its own, hence the namespace rather than the local name alone.
     */
    static List<CatalogResult> parse(String xml) throws Exception {
        Document document = documentBuilder().parse(new InputSource(new StringReader(xml)));
        NodeList records = document.getElementsByTagNameNS(MARC_NS, "record");
        List<CatalogResult> results = new ArrayList<>(records.getLength());
        for (int i = 0; i < records.getLength(); i++) {
            if (records.item(i) instanceof Element record) {
                CatalogResult result = toResult(record);
                if (result != null) {
                    results.add(result);
                }
            }
        }
        return results;
    }

    /**
     * How many volumes the BnF records for a series (#429): the highest volume number among
     * its records whose series title is this one. A legal-deposit catalogue holds every
     * published volume, so it answers for the book and comic series AniList never knew; it
     * reads one page of records, so a very long run may come out short — never long.
     */
    @Override
    public java.util.OptionalInt seriesVolumes(String title) {
        if (title == null || title.isBlank()) {
            return java.util.OptionalInt.empty();
        }
        String wanted = normalized(title).trim();
        return search(new CatalogQuery(title, null, null, null, null, null), MAX_RECORDS).stream()
                .filter(r -> r.seriesTitle() != null && normalized(r.seriesTitle()).trim().equals(wanted))
                .map(CatalogResult::volumeNumber)
                .filter(java.util.Objects::nonNull)
                .mapToInt(Integer::intValue)
                .filter(volume -> volume > 1 && volume < 1000)
                .max();
    }

    /** A publisher's price, as the BnF records it: amount and ISO currency. */
    public record ListPrice(java.math.BigDecimal amount, String currency) {
    }

    /** "14,95 EUR", "6.90 EUR", "Br. : 15 €", "29,90 euros". */
    private static final Pattern PRICE = Pattern.compile(
            "(?iu)(\\d{1,5}(?:[.,]\\d{1,2})?)\\s*(?:EUR\\b|€|euros?\\b)");

    /**
     * The price of a new copy of the edition carrying this ISBN (#421), from UNIMARC 010$d.
     *
     * <p>Under the French fixed book price law, the publisher's price the legal deposit
     * records is what the book costs new in any shop in France. Empty when the BnF does not
     * know the ISBN, records no price, or does not answer — a lookup never throws.
     */
    public java.util.Optional<ListPrice> priceByIsbn(String isbn13) {
        if (isbn13 == null || isbn13.isBlank()) {
            return java.util.Optional.empty();
        }
        String xml = call("bib.isbn all \"" + isbn13 + "\"", 5)
                .await().atMost(callTimeout.plusSeconds(2));
        if (xml == null || xml.isBlank()) {
            return java.util.Optional.empty();
        }
        try {
            return priceIn(xml, isbn13);
        } catch (Exception e) {
            Log.warnf("BnF price answer unreadable: %s", e.getMessage());
            return java.util.Optional.empty();
        }
    }

    /**
     * The price recorded for that ISBN in an SRU answer: the 010 whose ISBN matches first,
     * since a record lists one 010 per binding, then any priced 010.
     */
    static java.util.Optional<ListPrice> priceIn(String xml, String isbn13) throws Exception {
        Document document = documentBuilder().parse(new InputSource(new StringReader(xml)));
        NodeList records = document.getElementsByTagNameNS(MARC_NS, "record");
        ListPrice fallback = null;
        for (int i = 0; i < records.getLength(); i++) {
            if (!(records.item(i) instanceof Element record)) {
                continue;
            }
            for (Element field : fields(record, "010")) {
                ListPrice price = parsePrice(sub(field, 'd'));
                if (price == null) {
                    continue;
                }
                if (isbn13.equals(Isbn.toIsbn13(sub(field, 'a')))) {
                    return java.util.Optional.of(price);
                }
                if (fallback == null) {
                    fallback = price;
                }
            }
        }
        return java.util.Optional.ofNullable(fallback);
    }

    /** The amount in a 010$d, or {@code null} when it states none in euros. */
    static ListPrice parsePrice(String text) {
        if (text == null) {
            return null;
        }
        Matcher matcher = PRICE.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        java.math.BigDecimal amount = new java.math.BigDecimal(matcher.group(1).replace(',', '.'));
        return amount.signum() > 0 ? new ListPrice(amount.setScale(2, java.math.RoundingMode.HALF_UP), "EUR") : null;
    }

    private static CatalogResult toResult(Element record) {
        Element titleField = field(record, "200");
        String base = titleField == null ? null : clean(sub(titleField, 'a'));
        if (base == null || !isPrintedText(titleField)) {
            // A record with no title cannot be shown, and would collide with every other
            // untitled one on the aggregation's title+author key.
            return null;
        }
        String partNumber = sub(titleField, 'h');
        Integer partVolume = number(partNumber);
        Element series461 = field(record, "461");
        Element series225 = fieldWith(record, "225", 'v');

        // The series: the catalogue's own link to it (461) when there is one. A collection
        // (225) is often the publisher's ("Pika Shônen", "Folio"), so it is only a series when
        // it numbers the volume. With neither, a numbered part belongs to the work it is
        // titled after.
        String series = series461 != null ? clean(sub(series461, 't'))
                : series225 != null ? clean(sub(series225, 'a'))
                : partNumber != null ? base : null;
        Integer volume = partVolume != null ? partVolume
                : series461 != null ? number(sub(series461, 'v'))
                : series225 != null ? number(sub(series225, 'v')) : null;

        Element imprint = imprint(record);
        String ark = ark(record);
        return new CatalogResult(kind(record, titleField, imprint).name(), title(titleField, base, partNumber, partVolume),
                authors(record), imprint == null ? null : year(sub(imprint, 'd')), cover(record, ark),
                clean(sub(field(record, "330"), 'a')), isbn13(record),
                imprint == null ? null : clean(sub(imprint, 'c')), sub(field(record, "101"), 'a'), null,
                series, volume, pages(sub(field(record, "215"), 'a')), "bnf", ark);
    }

    /**
     * "Blue Lock - Tome 31": the title proper, the part number spelled out and the part's own
     * name after it. The BnF writes the number as "T.. 31", "[5]" or "56", none of which a
     * reader would recognise on a tile.
     */
    private static String title(Element titleField, String base, String partNumber, Integer volume) {
        StringBuilder title = new StringBuilder(base);
        if (volume != null) {
            title.append(" - Tome ").append(volume);
        } else if (clean(partNumber) != null) {
            title.append(" - ").append(clean(partNumber));
        }
        String partName = clean(sub(titleField, 'i'));
        if (partName != null) {
            title.append(" : ").append(partName);
        }
        return title.toString();
    }

    /**
     * A record that says what it is: "Texte imprimé" is the general material designation the
     * catalogue gives a book, "Multimédia multisupport" a box set. A record that says nothing
     * is kept, so a change of wording at the BnF cannot empty every answer.
     */
    private static boolean isPrintedText(Element titleField) {
        String designation = sub(titleField, 'b');
        return designation == null || normalized(designation).startsWith("texte imprim");
    }

    /**
     * Credits the authors, then the illustrators — who matter for a comic or a manga — and
     * nobody else: a translator is not an author, and neither is the adapter or the
     * lettering artist. A record whose persons carry no relator code keeps them all, except
     * the ones it names as translators in words.
     */
    private static String authors(Element record) {
        List<Person> persons = new ArrayList<>();
        for (Element person : fields(record, "700", "701", "702")) {
            String surname = clean(sub(person, 'a'));
            if (surname == null) {
                continue;
            }
            String forename = clean(sub(person, 'b'));
            String code = sub(person, '4');
            int rank;
            if (code == null) {
                String role = normalized(sub(person, 'c'));
                rank = role.contains("traduct") ? -1 : 0;
            } else {
                rank = switch (code.trim()) {
                    case "070" -> 0;
                    case "440" -> 1;
                    default -> -1;
                };
            }
            if (rank >= 0) {
                persons.add(new Person(rank, forename == null ? surname : forename + ' ' + surname));
            }
        }
        // Stable: within one rank the catalogue's order is kept, first author first.
        List<String> names = persons.stream()
                .sorted(Comparator.comparingInt(Person::rank))
                .map(Person::name)
                .distinct()
                .toList();
        return names.isEmpty() ? null : String.join(", ", names);
    }

    private record Person(int rank, String name) {
    }

    /**
     * Manga, comic or book. The manga hints win over the generic ones, since every manga is
     * also catalogued as a "bande dessinée". Nothing is guessed from a romanised author or an
     * original title: a translated Japanese novel has both and is a book.
     */
    private static Kind kind(Element record, Element titleField, Element imprint) {
        StringBuilder manga = new StringBuilder();
        for (Element collection : fields(record, "225")) {
            manga.append(' ').append(sub(collection, 'a'));
        }
        for (Element subject : fields(record, "608")) {
            manga.append(' ').append(sub(subject, 'a'));
        }
        if (MANGA_HINT.matcher(normalized(manga.toString())).find()
                || imprint != null && MANGA_PUBLISHER.matcher(normalized(sub(imprint, 'c'))).find()) {
            return Kind.MANGA;
        }
        for (Element subject : fields(record, "608")) {
            if (normalized(sub(subject, 'a')).contains("bandes dessinees")) {
                return Kind.COMIC;
            }
        }
        for (Element dewey : fields(record, "676")) {
            String number = sub(dewey, 'a');
            if (number != null && number.trim().startsWith("741.5")) {
                return Kind.COMIC;
            }
        }
        boolean illustrated = fields(record, "700", "701", "702").stream()
                .anyMatch(person -> "440".equals(trimmed(sub(person, '4'))));
        if (illustrated) {
            for (Element statement : fields(record, "200")) {
                for (String text : subs(statement, 'f', 'g')) {
                    if (COMIC_RESPONSIBILITY.matcher(normalized(text)).find()) {
                        return Kind.COMIC;
                    }
                }
            }
        }
        return Kind.BOOK;
    }

    /** The first publication statement: 214 is the current field, 210 the one older records use. */
    private static Element imprint(Element record) {
        List<Element> imprints = fields(record, "214", "210");
        return imprints.isEmpty() ? null : imprints.get(0);
    }

    /**
     * The BnF holds a scan of the cover when the record links to a "Première de couverture"
     * (856 $b). The image itself is served by our own {@code /api/covers/bnf/{ark}}, which is
     * why only the relative path is returned; a record without the link has no cover, and none
     * is guessed from the ISBN.
     */
    private static String cover(Element record, String ark) {
        if (ark == null) {
            return null;
        }
        for (Element link : fields(record, "856")) {
            if (normalized(sub(link, 'b')).startsWith("premiere de couverture")) {
                return "/api/covers/bnf/" + ark.substring(ark.lastIndexOf('/') + 1);
            }
        }
        return null;
    }

    /**
     * The record's ark, which is the BnF's own stable identifier for it, kept as the
     * {@code providerRef} so a result can be traced back to the notice it came from.
     */
    private static String ark(Element record) {
        String id = record.getAttribute("id");
        if (id.startsWith("ark:/")) {
            return id;
        }
        for (Element control : children(record, "controlfield")) {
            String text = control.getTextContent();
            int ark = text == null ? -1 : text.indexOf("ark:/");
            if (ark >= 0) {
                return text.substring(ark).trim();
            }
        }
        return null;
    }

    /** The first valid ISBN of 010, then the EAN of 073, as an ISBN-13 without hyphens. */
    private static String isbn13(Element record) {
        for (Element field : fields(record, "010", "073")) {
            String isbn = Isbn.toIsbn13(sub(field, 'a'));
            if (isbn != null) {
                return isbn;
            }
        }
        return null;
    }

    private static Integer pages(String extent) {
        if (extent == null) {
            return null;
        }
        Matcher matcher = PAGES.matcher(extent);
        return matcher.find() ? Integer.valueOf(matcher.group(1)) : null;
    }

    private static Integer year(String date) {
        if (date == null) {
            return null;
        }
        Matcher matcher = YEAR.matcher(date);
        return matcher.find() ? Integer.valueOf(matcher.group()) : null;
    }

    private static Integer number(String value) {
        if (value == null) {
            return null;
        }
        Matcher matcher = PLAIN_NUMBER.matcher(value.trim());
        return matcher.matches() ? Integer.valueOf(matcher.group(1)) : null;
    }

    /**
     * A title or a name made presentable: spaces collapsed, the elision's space removed, the
     * ISBD punctuation that trails a subfield ({@code " /"}, {@code " :"}) cut, and the
     * brackets or quotes that enclose the whole value dropped. {@code null} when nothing is left.
     */
    static String clean(String raw) {
        if (raw == null) {
            return null;
        }
        String text = ELISION_SPACE.matcher(raw.replaceAll("\\s+", " ").trim()).replaceAll("$1");
        String previous;
        do {
            previous = text;
            text = TRAILING_PUNCTUATION.matcher(text).replaceAll("").trim();
            if (text.length() > 1 && (text.startsWith("[") && text.endsWith("]")
                    || text.startsWith("\"") && text.endsWith("\""))) {
                text = text.substring(1, text.length() - 1).trim();
            }
        } while (!text.equals(previous));
        return text.isEmpty() ? null : text;
    }

    /** Lower case without accents, so "Shônen", "shōnen" and "SHONEN" are one word. */
    private static String normalized(String text) {
        if (text == null) {
            return "";
        }
        return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
    }

    private static String trimmed(String text) {
        return text == null ? null : text.trim();
    }

    private static List<Element> children(Element parent, String localName) {
        List<Element> found = new ArrayList<>();
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element element && localName.equals(element.getLocalName())) {
                found.add(element);
            }
        }
        return found;
    }

    /** The data fields with one of these tags, in the order the tags are given, then the record's. */
    private static List<Element> fields(Element record, String... tags) {
        List<Element> found = new ArrayList<>();
        List<Element> all = children(record, "datafield");
        for (String tag : tags) {
            for (Element field : all) {
                if (tag.equals(field.getAttribute("tag"))) {
                    found.add(field);
                }
            }
        }
        return found;
    }

    private static Element field(Element record, String tag) {
        List<Element> found = fields(record, tag);
        return found.isEmpty() ? null : found.get(0);
    }

    /** First field of that tag that carries the subfield: a record may repeat 225 for each collection. */
    private static Element fieldWith(Element record, String tag, char code) {
        for (Element field : fields(record, tag)) {
            if (sub(field, code) != null) {
                return field;
            }
        }
        return null;
    }

    /** First non-blank value of the subfield, or {@code null}. */
    private static String sub(Element field, char code) {
        List<String> values = subs(field, code);
        return values.isEmpty() ? null : values.get(0);
    }

    private static List<String> subs(Element field, char... codes) {
        List<String> values = new ArrayList<>();
        if (field == null) {
            return values;
        }
        for (Element sub : children(field, "subfield")) {
            String text = sub.getTextContent();
            if (text == null || text.isBlank()) {
                continue;
            }
            for (char code : codes) {
                if (sub.getAttribute("code").equals(String.valueOf(code))) {
                    values.add(text.trim());
                }
            }
        }
        return values;
    }

    /**
     * Parser for a document that comes from a third party: entity resolution is off and a
     * doctype is refused outright, so a hostile or merely broken answer cannot turn a catalog
     * search into a file read or an expansion bomb.
     */
    private static DocumentBuilder documentBuilder() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        // The default handler prints the parse error to stderr, outside the log. A malformed
        // answer is reported once, by search(), like every other way this provider can fail.
        builder.setErrorHandler(QUIET);
        return builder;
    }

    /** Reports parse errors through the exception only, leaving stderr alone. */
    private static final ErrorHandler QUIET = new ErrorHandler() {

        @Override
        public void warning(SAXParseException e) {
            // A warning still yields a usable document: nothing to report, nothing to abort.
        }

        @Override
        public void error(SAXParseException e) throws SAXException {
            throw e;
        }

        @Override
        public void fatalError(SAXParseException e) throws SAXException {
            throw e;
        }
    };
}
