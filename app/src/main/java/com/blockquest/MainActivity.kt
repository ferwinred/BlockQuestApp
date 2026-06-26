// =====================================================================
// MainActivity.kt
// Block Quest — single-activity host for Compose navigation
// =====================================================================

package com.blockquest

import android.os.Bundle
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.material3.MaterialTheme
import androidx.hilt.navigation.compose.hiltViewModel
import com.blockquest.data.ads.ActivityHolder
import com.blockquest.navigation.BlockQuestNavGraph
import com.blockquest.presentation.ui.theme.BlockQuestTheme
import com.blockquest.presentation.viewmodel.CosmeticsViewModel
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.blockquest.presentation.designsystem.Palettes
import com.google.android.gms.ads.MobileAds
import com.blockquest.data.ads.AdConsentManager
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var consentManager: AdConsentManager

    override fun getSystemService(name: String): Any? {
        if (name == Context.PERSISTENT_DATA_BLOCK_SERVICE) {
            return null
        }
        return super.getSystemService(name)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LaunchedEffect(Unit) {
                consentManager.requestConsentIfNeeded(this@MainActivity)
                MobileAds.initialize(this@MainActivity) {}
            }
            BlockQuestGame()
        }
    }

    override fun onResume() {
        super.onResume()
        ActivityHolder.bind(this)
    }

    override fun onPause() {
        super.onPause()
        ActivityHolder.unbind(this)
    }
}

@Composable
private fun BlockQuestGame() {
    val vm: CosmeticsViewModel =
        hiltViewModel(checkNotNull<ViewModelStoreOwner>(LocalViewModelStoreOwner.current) {
            "No ViewModelStoreOwner was provided via LocalViewModelStoreOwner"
        }, null)
    val state by vm.ui.collectAsStateWithLifecycle()
    val palette = Palettes.forTheme(state.activeSkinId)
    BlockQuestTheme(palette = palette) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            BlockQuestNavGraph()
        }
    }
}
