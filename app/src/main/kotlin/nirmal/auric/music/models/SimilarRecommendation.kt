package nirmal.auric.music.models

import com.echo.innertube.models.YTItem
import nirmal.auric.music.db.entities.LocalItem

data class SimilarRecommendation(
    val title: LocalItem,
    val items: List<YTItem>,
)
