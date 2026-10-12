package nirmal.auric.music.ui.screens

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class YtMusicSessionProbe(
    val loggedIn: Boolean = false,
    val visitorData: String? = null,
    val dataSyncId: String? = null,
    val delegatedSessionId: String? = null,
)

private val sessionProbeJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

fun parseYtMusicSessionProbe(rawResult: String?): YtMusicSessionProbe? {
    if (rawResult.isNullOrBlank() || rawResult == "null") return null
    return runCatching {
        val payload = sessionProbeJson.decodeFromString<String>(rawResult)
        sessionProbeJson.decodeFromString<YtMusicSessionProbe>(payload)
    }.recoverCatching {
        sessionProbeJson.decodeFromString<YtMusicSessionProbe>(rawResult)
    }.getOrNull()
}

const val YT_MUSIC_SESSION_PROBE_SCRIPT = """
(function () {
  try {
    if (!window.ytcfg || !window.ytcfg.get) return null;
    var value = function (key) {
      var result = window.ytcfg.get(key);
      return (result === undefined || result === null || result === '') ? null : String(result);
    };
    return JSON.stringify({
      loggedIn: !!window.ytcfg.get('LOGGED_IN'),
      visitorData: value('VISITOR_DATA'),
      dataSyncId: value('DATASYNC_ID'),
      delegatedSessionId: value('DELEGATED_SESSION_ID')
    });
  } catch (error) {
    return null;
  }
})()
"""
