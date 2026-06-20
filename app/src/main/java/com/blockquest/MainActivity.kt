// =====================================================================
// MainActivity.kt
// Block Quest — single-activity host for Compose navigation
// =====================================================================

package com.blockquest

import android.os.Bundle
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.blockquest.data.ads.ActivityHolder
import com.blockquest.navigation.BlockQuestNavGraph
import com.blockquest.presentation.ui.theme.BlockQuestTheme
import com.blockquest.presentation.viewmodel.CosmeticsViewModel
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.blockquest.presentation.designsystem.Palettes
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { BlockQuestGame() }
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
