package zelytra.librarius.catalog;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CatalogQueryTest {

    @Test
    void theWordsToMatchLeaveALeadingArticleOut() {
        // Catalogues require every word they are given: "Un pull moche" found nothing where
        // "Pull moche" found the book, its title carrying no "un" (#607).
        assertEquals("pull moche", CatalogQuery.of("Un pull moche").wordsToMatch());
        assertEquals("nom du vent", CatalogQuery.of("le nom du vent").wordsToMatch());
        assertEquals("Game of Thrones", CatalogQuery.of("A Game of Thrones").wordsToMatch());
        assertEquals("pull moche", CatalogQuery.of("LES   pull moche").wordsToMatch());
    }

    @Test
    void theWordsToMatchKeepAnArticleThatIsTheWholeTextOrInsideIt() {
        assertEquals("Les", CatalogQuery.of("Les").wordsToMatch());
        assertEquals("pull de un", CatalogQuery.of("pull de un").wordsToMatch());
        // An elided article is part of its word, and "Lune" only starts like "un".
        assertEquals("l'étranger", CatalogQuery.of("l'étranger").wordsToMatch());
        assertEquals("Lune rouge", CatalogQuery.of("Lune rouge").wordsToMatch());
        assertNull(new CatalogQuery(null, "herbert", null, null, null, null).wordsToMatch());
    }

    @Test
    void foldsBlankCriteriaToNull() {
        // A browser submits an untouched field as an empty string; a provider must not have
        // to tell that apart from an absent one.
        CatalogQuery query = new CatalogQuery("  dune ", "  ", null, "", "   ", null);

        assertEquals("dune", query.text());
        assertNull(query.author());
        assertNull(query.language());
        assertNull(query.publisher());
    }

    @Test
    void isEmptyOnlyWhenNoCriterionIsSet() {
        assertTrue(new CatalogQuery(null, null, null, null, null, null).isEmpty());
        assertTrue(new CatalogQuery("   ", "", null, null, null, null).isEmpty());
        assertFalse(CatalogQuery.of("dune").isEmpty());
        // An advanced criterion on its own is a search: the free text is not required.
        assertFalse(new CatalogQuery(null, "herbert", null, null, null, null).isEmpty());
        assertFalse(new CatalogQuery(null, null, 1965, null, null, null).isEmpty());
        assertFalse(new CatalogQuery(null, null, null, null, null, "9780441013593").isEmpty());
    }

    @Test
    void givesEveryCriterionItsOwnCacheKey() {
        CatalogQuery text = CatalogQuery.of("dune");

        assertNotEquals(text.cacheKey(),
                new CatalogQuery("dune", "herbert", null, null, null, null).cacheKey());
        assertNotEquals(text.cacheKey(),
                new CatalogQuery("dune", null, 1965, null, null, null).cacheKey());
        assertNotEquals(text.cacheKey(),
                new CatalogQuery("dune", null, null, "fr", null, null).cacheKey());
        assertNotEquals(text.cacheKey(),
                new CatalogQuery("dune", null, null, null, "pocket", null).cacheKey());
        assertNotEquals(text.cacheKey(),
                new CatalogQuery("dune", null, null, null, null, "9780441013593").cacheKey());
    }

    @Test
    void keepsTheSameCacheKeyForTheSameSearchTypedDifferently() {
        assertEquals(CatalogQuery.of("dune").cacheKey(),
                new CatalogQuery(" dune ", "", null, "", "", "").cacheKey());
    }
}
