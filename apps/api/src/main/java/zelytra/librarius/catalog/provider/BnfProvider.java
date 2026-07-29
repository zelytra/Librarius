package zelytra.librarius.catalog.provider;

import io.quarkus.logging.Log;
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
import zelytra.librarius.domain.Kind;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.time.Duration;
import java.util.ArrayList;
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
 * <p>What it costs is the shape of the answer: SRU returns a Dublin Core record, which
 * carries a title, a creator, a date, a publisher, a language and a handful of identifiers —
 * and no cover image. A title only the BnF knows therefore reaches the screen without one.
 */
@ApplicationScoped
public class BnfProvider implements CatalogProvider {

    private static final String SRU_VERSION = "1.2";

    private static final String SRU_OPERATION = "searchRetrieve";

    /** Simplest of the schemas the BnF serves; UNIMARC would carry more and cost a parser. */
    private static final String RECORD_SCHEMA = "dublincore";

    /**
     * Page asked of the BnF. Also the size fetched when the year is being filtered here
     * rather than by the catalogue, the same trade AniList's author search makes.
     */
    private static final int MAX_RECORDS = 50;

    /**
     * The BnF indexes languages as MARC codes, like Open Library, while a client holds the
     * ISO 639-1 code of the language it displays. The mapping belongs to the provider, so
     * this deliberately repeats {@code OpenLibraryProvider}'s rather than sharing it: the two
     * catalogues are free to disagree, and the day one of them does, a shared constant would
     * have to be unpicked.
     */
    private static final Map<String, String> MARC_LANGUAGES = Map.of(
            "fr", "fre", "en", "eng", "ja", "jpn", "de", "ger", "es", "spa", "it", "ita");

    /** First four consecutive digits of a Dublin Core date ("DL 2001", "cop. 1965", "2023"). */
    private static final Pattern YEAR = Pattern.compile("\\d{4}");

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
        String cql = cqlQuery(query);
        if (cql.isEmpty()) {
            // Only a year was given. Asking the BnF for everything printed that year would
            // answer, and the answer would be noise.
            return List.of();
        }
        try {
            String xml = client
                    .search(SRU_VERSION, SRU_OPERATION, cql, RECORD_SCHEMA, pageSize(query, limit))
                    .await().atMost(callTimeout);
            if (xml == null || xml.isBlank()) {
                return List.of();
            }
            return parse(xml).stream()
                    .filter(result -> query.year() == null || query.year().equals(result.year()))
                    .limit(limit)
                    .toList();
        } catch (Exception e) {
            // Same contract as the other two providers: a failure is no result, never an
            // exception reaching CatalogService, so the aggregate degrades to what the other
            // book provider found instead of turning into a 500.
            Log.warnf("BnF search failed: %s", e.getMessage());
            return List.of();
        }
    }

    @Override
    public List<CatalogResult> upcoming(int limit) {
        // A legal-deposit catalogue records what has been published, not what is announced.
        return List.of();
    }

    /** A locally filtered year needs a page to filter from; anything else asks for what it needs. */
    private static int pageSize(CatalogQuery query, int limit) {
        return query.year() != null ? MAX_RECORDS : Math.min(limit, MAX_RECORDS);
    }

    /**
     * Renders the criteria as the CQL that SRU expects: every criterion is a fielded term on
     * a {@code bib.*} index, joined by {@code and}, so the BnF narrows them itself.
     *
     * <p>The year is the exception: it is applied to the parsed records instead. The
     * publication-date index is not the plain year a caller supplies — an edition carries a
     * date of the shape "DL 2001" — and a term the catalogue rejects is not a narrower
     * search, it is a diagnostic and no results at all.
     */
    private static String cqlQuery(CatalogQuery query) {
        StringBuilder cql = new StringBuilder();
        append(cql, "bib.anywhere", query.text());
        append(cql, "bib.author", query.author());
        append(cql, "bib.publisher", query.publisher());
        append(cql, "bib.isbn", query.isbn());
        if (query.language() != null) {
            String language = query.language().toLowerCase(Locale.ROOT);
            append(cql, "bib.language", MARC_LANGUAGES.getOrDefault(language, language));
        }
        return cql.toString();
    }

    /**
     * Appends {@code index all "value"} to the query. The quotes keep a multi-word value in
     * one term, and the ones the user typed are dropped rather than escaped: a stray quote
     * would unbalance the CQL and the BnF would answer a diagnostic instead of records.
     */
    private static void append(StringBuilder cql, String index, String value) {
        if (value == null) {
            return;
        }
        String cleaned = value.replace("\"", " ").replace("\\", " ").replaceAll("\\s+", " ").trim();
        if (cleaned.isEmpty()) {
            return;
        }
        if (!cql.isEmpty()) {
            cql.append(" and ");
        }
        cql.append(index).append(" all \"").append(cleaned).append('"');
    }

    /**
     * Reads the records out of the SRU envelope.
     *
     * <p>Elements are matched on their local name, with the namespace left as a wildcard: the
     * envelope and Dublin Core each have one, they are not what identifies a field here, and
     * a prefix the BnF decides to change should not empty the answer.
     */
    static List<CatalogResult> parse(String xml) throws Exception {
        Document document = documentBuilder().parse(new InputSource(new StringReader(xml)));
        NodeList records = document.getElementsByTagNameNS("*", "record");
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

    private static CatalogResult toResult(Element record) {
        String title = isbdTitle(first(record, "title"));
        if (title == null) {
            // A record with no title cannot be shown, and would collide with every other
            // untitled one on the aggregation's title+author key.
            return null;
        }
        List<String> identifiers = all(record, "identifier");
        return new CatalogResult("BOOK", title, creators(record), year(first(record, "date")),
                null, null, isbn13(identifiers), first(record, "publisher"),
                first(record, "language"), null, "bnf", ark(identifiers));
    }

    /**
     * Drops the ISBD statement of responsibility from a title.
     *
     * <p>A BnF title is a full ISBD statement — {@code "Fondation / Isaac Asimov"}, or
     * {@code "Dune ; [suivi de] Le messie de Dune / Frank Herbert ; traduit de l'américain
     * par Michel Demuth"} — where Open Library holds plain {@code "Fondation"}. Everything
     * from the {@code " / "} on is the authorship, which {@code dc:creator} already carries,
     * so it is cut: it is what makes the title fit a result tile, and what lets a book both
     * catalogues hold merge on the title+author key instead of being listed twice.
     */
    private static String isbdTitle(String raw) {
        if (raw == null) {
            return null;
        }
        int responsibility = raw.indexOf(" / ");
        String title = (responsibility > 0 ? raw.substring(0, responsibility) : raw).trim();
        return title.isEmpty() ? null : title;
    }

    /**
     * Joins the creators the way Open Library joins its authors, so that a book both
     * catalogues know produces the same aggregation key and is shown once.
     *
     * <p>Repeats are dropped: a BnF record routinely carries the same author twice — once
     * per role it filled — and {@code "Frank Herbert, Frank Herbert"} would match nothing
     * and read as a mistake.
     */
    private static String creators(Element record) {
        List<String> names = all(record, "creator").stream()
                .map(BnfProvider::normalizeCreator)
                .filter(name -> name != null && !name.isEmpty())
                .distinct()
                .toList();
        return names.isEmpty() ? null : String.join(", ", names);
    }

    /**
     * Turns a BnF creator into the plain name Open Library returns.
     *
     * <p>The catalogue writes an authority heading — {@code "Herbert, Frank (1920-1986).
     * Auteur du texte"} — where Open Library writes {@code "Frank Herbert"}. Left as-is the
     * two never match and the same novel is listed twice, so the life dates and the role are
     * dropped and the inverted name is put back in reading order.
     *
     * <p>Without dates to cut at, the role is the last {@code ". "} segment — but only when
     * that segment is a plain phrase: {@code "Tolkien, J. R. R."} ends in an initial, not in
     * a role, and cutting there would lose half the name.
     */
    static String normalizeCreator(String raw) {
        if (raw == null) {
            return null;
        }
        String name = raw.trim();
        int dates = name.indexOf('(');
        if (dates > 0) {
            name = name.substring(0, dates);
        } else {
            int role = name.lastIndexOf(". ");
            if (role > 0) {
                String tail = name.substring(role + 2);
                if (!tail.contains(".") && !tail.contains(",")) {
                    name = name.substring(0, role);
                }
            }
        }
        name = name.trim();
        int comma = name.indexOf(',');
        if (comma > 0 && name.indexOf(',', comma + 1) < 0) {
            String family = name.substring(0, comma).trim();
            String given = name.substring(comma + 1).trim();
            name = given.isEmpty() ? family : given + ' ' + family;
        }
        return name.isEmpty() ? null : name;
    }

    /**
     * Picks the ISBN-13 out of the identifiers.
     *
     * <p>A Dublin Core identifier is a sentence, not a number — {@code "978-2-266-11624-2
     * (br.) : 8,90 EUR"} — so the trailing binding and price are cut off before the digits
     * are read, otherwise the price would be read as part of the number.
     */
    private static String isbn13(List<String> identifiers) {
        for (String identifier : identifiers) {
            int note = identifier.indexOf('(');
            String candidate = (note > 0 ? identifier.substring(0, note) : identifier)
                    .replaceAll("[^0-9]", "");
            if (candidate.length() == 13 && (candidate.startsWith("978") || candidate.startsWith("979"))) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * The record's ark, which is the BnF's own stable identifier for it, kept as the
     * {@code providerRef} so a result can be traced back to the notice it came from.
     */
    private static String ark(List<String> identifiers) {
        for (String identifier : identifiers) {
            int ark = identifier.indexOf("ark:/");
            if (ark >= 0) {
                return identifier.substring(ark).trim();
            }
        }
        return null;
    }

    private static Integer year(String date) {
        if (date == null) {
            return null;
        }
        Matcher matcher = YEAR.matcher(date);
        return matcher.find() ? Integer.valueOf(matcher.group()) : null;
    }

    private static String first(Element record, String localName) {
        List<String> values = all(record, localName);
        return values.isEmpty() ? null : values.get(0);
    }

    private static List<String> all(Element record, String localName) {
        NodeList nodes = record.getElementsByTagNameNS("*", localName);
        List<String> values = new ArrayList<>(nodes.getLength());
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            String text = node.getTextContent();
            if (text != null && !text.isBlank()) {
                values.add(text.trim());
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
