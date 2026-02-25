package nirmal.auric.music.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import nirmal.auric.music.utils.scanners.LocalMediaScanner
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val scanner: LocalMediaScanner
) : ViewModel() {
    val isScanning = scanner.isScanning.asStateFlow()
    val scannerProgress = scanner.scannerProgress.asStateFlow()

    fun startScan(
        scanPaths: List<String>,
        excludedPaths: List<String>,
        sensitivity: Int,
        strictExt: Boolean,
        useFilenameAsTitle: Boolean
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            scanner.scan(scanPaths, excludedPaths, sensitivity, strictExt, useFilenameAsTitle)
        }
    }
}
