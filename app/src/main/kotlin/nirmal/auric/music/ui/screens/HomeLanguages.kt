package nirmal.auric.music.ui.screens

data class HomeLanguage(
    val code: String,
    val title: String,
    val glyph: String,
    val color: Long,
) {
    val searchQuery: String get() = "$title songs"

    companion object {
        fun fromCode(code: String): HomeLanguage? =
            All.find { it.code.equals(code, ignoreCase = true) }

        val All = listOf(
            HomeLanguage("tamil", "Tamil", "த", 0xFFE53935),
            HomeLanguage("hindi", "Hindi", "हि", 0xFFFF8F00),
            HomeLanguage("telugu", "Telugu", "తె", 0xFF8E24AA),
            HomeLanguage("malayalam", "Malayalam", "മ", 0xFF00897B),
            HomeLanguage("kannada", "Kannada", "ಕ", 0xFF1E88E5),
            HomeLanguage("punjabi", "Punjabi", "ਪ", 0xFF43A047),
            HomeLanguage("bengali", "Bengali", "বা", 0xFFD81B60),
            HomeLanguage("english", "English", "En", 0xFF5E35B1),
            HomeLanguage("marathi", "Marathi", "म", 0xFF6D4C41),
            HomeLanguage("gujarati", "Gujarati", "ગ", 0xFF00838F),
        )
    }
}
