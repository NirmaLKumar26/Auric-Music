package nirmal.auric.music.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class AccountPreferenceResolverTest {
    @Test
    fun detectsIndicScriptsAndUsesLocaleAsTieBreaker() {
        val result = AccountPreferenceResolver.inferLanguageCodes(
            metadata = listOf("தமிழ் பாடல்", "മലയാളം പാട്ട്", "English title"),
            locale = Locale.ENGLISH,
        )

        assertEquals("english", result.first())
        assertTrue("tamil" in result)
        assertTrue("malayalam" in result)
    }

    @Test
    fun distinguishesMarathiFromHindiUsingLocale() {
        val result = AccountPreferenceResolver.inferLanguageCodes(
            metadata = listOf("मराठी गाणे"),
            locale = Locale.forLanguageTag("mr-IN"),
        )

        assertEquals(listOf("marathi"), result)
    }

    @Test
    fun returnsNoGuessForUnsupportedLocaleAndLatinMetadata() {
        val result = AccountPreferenceResolver.inferLanguageCodes(
            metadata = listOf("Romanized song title"),
            locale = Locale.JAPANESE,
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun usesLocaleWhenAccountLibraryIsEmpty() {
        val result = AccountPreferenceResolver.inferLanguageCodes(
            metadata = emptyList(),
            locale = Locale.forLanguageTag("te-IN"),
        )

        assertEquals(listOf("telugu"), result)
    }

    @Test
    fun manualSelectionPreventsAccountInference() {
        val shouldInfer = AccountPreferenceResolver.shouldInferLanguages(
            manuallySet = true,
            lastAccountId = "old-account",
            accountId = "new-account",
            currentLanguages = setOf("tamil"),
        )

        assertTrue(!shouldInfer)
    }
}
