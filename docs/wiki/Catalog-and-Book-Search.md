# Catalog & book search

The **Discover** screen searches external catalogs rather than a table Librarius owns. This
page explains how a search gets from the search box to a result, where the data comes from,
and how imported titles get their missing details later.

## Three providers, one shape

`CatalogService` fans a search out to every provider at once (virtual threads, one call per
provider, each bounded by `librarius.catalog.provider.call-timeout`) and normalises the answers
into `CatalogResult`. A free-text search is then handed to `CatalogRanker` (see
[Relevance](#relevance)); a search without text (ISBN, author, year) keeps the providers'
round-robin order, merged on series + volume or title + author.

| Provider | Covers | Answers |
|---|---|---|
| **Open Library** | books, every language | one result per **work**, built from its French edition when there is one (`lang=fr`): French title, cover (from a cover id, never guessed from an ISBN), ISBN. `popularity` is the work's edition count (400 for *Harry Potter à l'école des sorciers*, 1 for a pamphlet); the work reference lists its other editions |
| **BnF** (SRU, UNIMARC) | French books, BD, manga | one result per **edition** (legal-deposit record). Two queries side by side: the exact title phrase first (`bib.title adj`), then every word on title or author. Clean titles ("Blue Lock - Tome 31"), series and volume, authors and illustrators (never the translator), `BOOK`/`COMIC`/`MANGA` from the catalogue's hints, no popularity |
| **AniList** (GraphQL) | manga | one `MANGA` result per **series**, sorted `[SEARCH_MATCH, POPULARITY_DESC]` then the main series first (the most popular title holding the query, so *Kimetsu no Yaiba* before its *Kimetsu Academy* spin-off); asked without the volume ("jujutsu kaisen 12" → "jujutsu kaisen"); staff credited by role (story, art), long-vowel spellings folded. Its romaji, native titles and synonyms are kept in memory (`CatalogProvider.alternativeTitles`) for the kinds cross-check |

None needs an API key. A criterion a provider cannot honour (publisher on AniList, say) is
dropped for that provider rather than faked.

A BnF result carries a cover only when the record links to a front cover, as the relative
`/api/covers/bnf/{ark}`: the API fetches, resizes and caches the BnF's image. That endpoint is
the only anonymous one (`@PermitAll`), because an `<img>` cannot send a bearer token and a
catalogue cover is not user data; it is hidden from the OpenAPI schema. `popularity` is part of
the search answer.

## Relevance

`CatalogRanker` turns the merged answers into one page, in six steps.

1. **Clean-up** of provider artefacts: a credit glued to a title is cut when it is the credited
   author ("L'étranger Par Albert Camus"), the space after an elision goes ("L' Etranger"), an
   all-capitals title is put in sentence case, a bare initial gets its dot ("J. K Rowling").
2. **Kinds**: when AniList answered a manga series, every result whose series (or title) is that
   series — under its title, the part before a colon ("Demon Slayer"), its romaji or native
   title or a synonym ("L'Attaque des Titans") — becomes `MANGA`. Inside one series, a volume
   catalogued `MANGA` (else `COMIC`) makes the other volumes so when they credit the same
   people or carry a credit that cannot be compared (kanji, none): Maxe L'Hermenier's comic
   "Fantine" does not turn Hugo's volumes into comics. A manga is never downgraded.
3. **Grouping**: the editions of one work become one result. A book never merges with a comic
   or manga — Hugo's novel stays apart from its adaptations — and Open Library, which files
   everything as a book, joins the book group of the same work when there is one, else the
   comic or manga group. Within a family, results are one work when they share an ISBN, or a
   series and a volume with a credited person in common (or an uncomparable credit: the Blue
   Lock spin-off drawn by another artist stays apart), or a folded title and first author
   (initials-insensitive). Only a language-edition statement such as "(French Edition)" is
   dropped from the title: an anniversary edition or "… : Serdaigle" stays apart. The survivor is
   the French edition, then the one with a cover, then the most complete, then Open Library's
   work; it takes what it lacks (cover, synopsis, pages, series, ISBN) from the others and the
   highest popularity. AniList series are never merged into a book record; a record numbered in
   a series but marked as merchandise (the "Carnet des pourfendeurs") is never grouped as a volume.
   A volume the BnF titles by its part ("Romance dawn") is shown under its series, "One piece -
   Tome 1 : Romance dawn", and a volume credited in kanji only shows the Latin credit of its
   series.
4. **Score**. A title tier first — exact title > title starting with the query or "query +
   volume" > title containing the query phrase > every query word > some of them (a title much
   longer than the query that merely contains its words counts as partial). Words are compared
   whole, so "one piece 1" does not match tome 113. Then:
   - **author intent**: "l'étranger camus" (a credited author's surname plus the rest of the
     title) counts as an exact title; a query that is wholly a credited person's name
     ("victor hugo") ranks the works *by* that person level with an exact title, and the books
     *about* them (their name in the title, not crediting them) as derivatives. The words must
     name one credited person: an anthology crediting a "Frank Andriat" and a "Philippe Herbet"
     is not by Frank Herbert;
   - **popularity** (log-scaled edition count, plus the size of the group; a BnF phrase match
     with a cover counts as moderately popular) orders a tier, and a work of 50 editions or more
     is lifted one tier — never two;
   - **derivatives** drop below every genuine match of their tier and of the tier below: study
     guides (étude, analyse, profil, fiche de lecture…, a title opening on "Étudier", "Étude
     sur", "Une lecture de", or quoting the query between quotation marks next to words of its
     own), colouring and activity books (coloring, agenda, calendrier, carnet, tarot, poster…),
     magazines, anime comics and "party" spin-offs unless the query names them, exhibition
     catalogues, box sets ("coffret", "box set", "Gde collection"). "Guide", "lire" and "dossier"
     only count when somebody else than the best-ranked work's author wrote the book — "Le
     Guide du voyageur galactique" is Douglas Adams's novel;
   - a title match **by somebody else** than the best-ranked work's author ("Dune" by Brian
     Herbert, Chaïko's comic "Les misérables") drops one tier, and so does a result with **no
     author** at all;
   - inside a tier: French first (another language lower, an unknown one neutral), a cover, a
     complete record.
5. **Trim**: when a genuine result matches the title (exact, prefix, phrase, or an author with
   the rest of the title), the page loses its padding — everything matching less than every
   query word, an all-words match much longer than the query, a foreign edition of the
   best-ranked author's work when a French one is there. A record with neither author nor cover
   goes when five others have one. The volumes of a series run stay. A short honest page ("le
   nom du vent" → the novel) beats twenty rows of 18th-century notices.
6. **Volumes in order**: the volumes of one series are emitted together in ascending order from
   the position of the best-ranked one, AniList's series entry first. When the query names a
   volume ("one piece tome 1", "jujutsu kaisen 12"), that volume leads, then the series, then
   the rest. "Blue Lock 1" is read as volume 1 only because another result is a volume of
   "Blue Lock"; a bare number alone ("Fahrenheit 451") never is.

**Prefix retry.** When nothing reaches the two top title tiers and the plain query has two
words or more, the last of three letters or more ("le nom du ven"), the reader probably stopped
mid-word: Open Library (`title:(le AND nom AND du AND ven*)`) and the BnF
(`bib.title all "le nom du ven*"`) are asked once more (`CatalogQuery.prefixLastWord`), cached
like any call and charged nothing to the quota, and the merged page is ranked again with the
last word matching the start of a title word.

`CatalogRelevanceTest` runs ten recorded real answers (`src/test/resources/catalog/`) through
the real provider mappings and ranking. Discover also shows the authors of the shared catalog
the words name, above the titles (`GET /api/authors?q=`).

## Quota and cache

A caller has a quota (`RateLimiter`, 30 searches a minute and 500 a day by default, chart
value `api.catalogQuota`): a scan or a search that reaches the providers spends it, one
answered from Librarius's own data does not. Results are cached twice: in process
(Caffeine) and in PostgreSQL (`catalog_cache`), so a deploy does not send every popular search
back out to the providers. Details: [`.claude/docs/DATA-MODEL.md`](https://github.com/zelytra/Librarius/blob/main/.claude/docs/DATA-MODEL.md).

## Scanning a barcode

`GET /api/scan/{isbn}` (ISBN-13 or ISBN-10, check digit verified) answers whether the book
is the reader's already, on their wishlist, or new — see [API](https://github.com/zelytra/Librarius/wiki/API#scan). Imported
libraries carry no ISBN, so after the ISBN pass the catalog says what the book is and the
library is searched again by title and author, then series and volume, then a reworded
title with the same numbers.

A scanning session can file what it finds on its own (#168): the reader picks a destination
before scanning — to-read pile, read, reading, wishlist, or "check only" (the default) — and
each new title the catalog recognises is added there with no confirmation, while a title
already owned or wished for is only reported. The screen counts the session's additions and
offers an undo on each.

## Filling in imported titles

Booknode and Babelio give a title, an author and a cover — no ISBN, length or publisher.
`ImportEnricher` (scheduled, `librarius.imports.enrich.every`, 60 editions a run) looks each
such edition up in the catalog and fills only what is missing, when the candidate is
provably the same book. Until a library is filled in, the statistics estimate pages read from
the known lengths and say so ("≈").

## Upcoming releases

Release dates are the providers' own — mostly Japanese or English editions. There is no free
source of French publishers' calendars, so dates are shown as indicative.

The endpoint reference is on the [API](https://github.com/zelytra/Librarius/wiki/API) page.
