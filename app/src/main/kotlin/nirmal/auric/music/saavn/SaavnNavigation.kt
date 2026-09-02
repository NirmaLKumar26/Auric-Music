package nirmal.auric.music.saavn

import androidx.navigation.NavController
import com.music.saavn.Saavn
import com.music.saavn.SaavnCollectionType
import java.net.URLEncoder

fun NavController.navigateSaavnUrl(raw: String): Boolean {
    val target = Saavn.parseUrl(raw) ?: return false
    val type = when (target.type) {
        SaavnCollectionType.SONG -> "song"
        SaavnCollectionType.ALBUM -> "album"
        SaavnCollectionType.ARTIST -> "artist"
        SaavnCollectionType.PLAYLIST -> "playlist"
    }
    navigate("saavn/$type/${URLEncoder.encode(target.token, "UTF-8")}")
    return true
}
