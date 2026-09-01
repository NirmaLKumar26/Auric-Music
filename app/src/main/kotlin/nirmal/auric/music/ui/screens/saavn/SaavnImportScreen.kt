package nirmal.auric.music.ui.screens.saavn

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.music.saavn.Saavn
import nirmal.auric.music.R
import nirmal.auric.music.ui.component.IconButton
import nirmal.auric.music.ui.utils.backToMain
import java.net.URLEncoder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaavnImportScreen(navController: NavController) {
    var url by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    Column {
        TopAppBar(
            title = { Text(stringResource(R.string.saavn_import_title)) },
            navigationIcon = {
                IconButton(
                    onClick = navController::navigateUp,
                    onLongClick = navController::backToMain,
                ) {
                    Icon(
                        painterResource(R.drawable.arrow_back),
                        contentDescription = null,
                    )
                }
            },
        )
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.saavn_import_desc))
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = url,
                onValueChange = {
                    url = it
                    error = null
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text(stringResource(R.string.saavn_import_hint)) },
                isError = error != null,
                supportingText = error?.let { { Text(it) } },
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    val target = Saavn.parseUrl(url)
                    if (target == null) {
                        error = "Paste a JioSaavn song, album, or playlist link"
                    } else {
                        val type = target.type.name.lowercase()
                        navController.navigate("saavn/$type/${URLEncoder.encode(target.token, "UTF-8")}")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.saavn_import_open))
            }
        }
    }
}
