package nirmal.auric.music.viewmodels

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.saavn.Saavn
import com.music.saavn.SaavnCollection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.URLDecoder
import javax.inject.Inject

@HiltViewModel
class SaavnCollectionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val type: String = savedStateHandle.get<String>("type").orEmpty()
    val id: String = try {
        URLDecoder.decode(savedStateHandle.get<String>("id").orEmpty(), "UTF-8")
    } catch (_: Exception) {
        savedStateHandle.get<String>("id").orEmpty()
    }

    private val _collection = MutableStateFlow<SaavnCollection?>(null)
    val collection = _collection.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { Saavn.collection(type, id) }
                .onSuccess { result ->
                    _collection.value = result
                    if (result.songs.isEmpty()) {
                        _error.value = "No songs found"
                    }
                }
                .onFailure { e ->
                    _error.value = e.message ?: "Failed to load JioSaavn"
                }
        }
    }
}
