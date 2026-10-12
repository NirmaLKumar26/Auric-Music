package nirmal.auric.music.utils

import android.content.Context
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first
import nirmal.auric.music.constants.LanguageInferenceAccountKey
import nirmal.auric.music.constants.PreferredHomeLanguageCodesKey
import nirmal.auric.music.constants.PreferredHomeLanguagesManuallySetKey
import nirmal.auric.music.db.MusicDatabase
import java.util.Locale

object AccountPreferenceResolver {
    fun shouldInferLanguages(
        manuallySet: Boolean,
        lastAccountId: String?,
        accountId: String,
        currentLanguages: Set<String>,
    ): Boolean = !manuallySet &&
        accountId.isNotBlank() &&
        (lastAccountId != accountId || currentLanguages.isEmpty())

    fun inferLanguageCodes(
        metadata: List<String>,
        locale: Locale = Locale.getDefault(),
    ): List<String> {
        val scores = linkedMapOf(
            "tamil" to 0,
            "hindi" to 0,
            "telugu" to 0,
            "malayalam" to 0,
            "kannada" to 0,
            "punjabi" to 0,
            "bengali" to 0,
            "gujarati" to 0,
        )

        metadata.forEach { value ->
            value.forEach { character ->
                val code = character.code
                val language = when (code) {
                    in 0x0B80..0x0BFF -> "tamil"
                    in 0x0900..0x097F -> if (locale.language == "mr") "marathi" else "hindi"
                    in 0x0C00..0x0C7F -> "telugu"
                    in 0x0D00..0x0D7F -> "malayalam"
                    in 0x0C80..0x0CFF -> "kannada"
                    in 0x0A00..0x0A7F -> "punjabi"
                    in 0x0980..0x09FF -> "bengali"
                    in 0x0A80..0x0AFF -> "gujarati"
                    else -> null
                }
                if (language != null) scores[language] = scores.getOrDefault(language, 0) + 1
            }
        }

        val inferred = scores.entries
            .filter { it.value >= MIN_SCRIPT_CHARACTERS }
            .sortedByDescending { it.value }
            .take(MAX_LANGUAGES)
            .map { it.key }
            .toMutableList()

        locale.toHomeLanguageCode()?.let { localeLanguage ->
            if (localeLanguage !in inferred) inferred.add(0, localeLanguage)
        }
        return inferred.distinct().take(MAX_LANGUAGES)
    }

    suspend fun inferAndPersistIfNeeded(
        context: Context,
        database: MusicDatabase,
        accountId: String,
    ) {
        if (accountId.isBlank()) return
        val preferences = context.dataStore.data.first()
        if (!shouldInferLanguages(
                manuallySet = preferences[PreferredHomeLanguagesManuallySetKey] == true,
                lastAccountId = preferences[LanguageInferenceAccountKey],
                accountId = accountId,
                currentLanguages = preferences[PreferredHomeLanguageCodesKey].orEmpty(),
            )
        ) return

        val songs = (
            database.likedSongsByRowIdAsc().first() +
                database.songsByRowIdAsc().first()
            ).distinctBy { it.id }
        val metadata = songs.flatMap { song ->
            listOf(song.title, song.artists.joinToString(" ") { it.name })
        }
        val inferred = inferLanguageCodes(metadata)
        context.dataStore.edit { settings ->
            settings[LanguageInferenceAccountKey] = accountId
            if (inferred.isNotEmpty()) {
                settings[PreferredHomeLanguageCodesKey] = inferred.toSet()
            }
        }
    }

    private fun Locale.toHomeLanguageCode(): String? = when (language) {
        "ta" -> "tamil"
        "hi" -> "hindi"
        "te" -> "telugu"
        "ml" -> "malayalam"
        "kn" -> "kannada"
        "pa" -> "punjabi"
        "bn" -> "bengali"
        "en" -> "english"
        "mr" -> "marathi"
        "gu" -> "gujarati"
        else -> null
    }

    private const val MIN_SCRIPT_CHARACTERS = 2
    private const val MAX_LANGUAGES = 4
}
