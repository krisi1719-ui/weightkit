package com.weightkit.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.LaunchedEffect
import com.weightkit.app.model.AdsHolder
import com.weightkit.app.model.AppHolder
import com.weightkit.app.model.ProHolder
import com.weightkit.app.model.ShapeKind
import com.weightkit.app.ui.CalculatorScreen
import com.weightkit.app.ui.ListsScreen
import com.weightkit.app.ui.SettingsScreen
import com.weightkit.app.ui.ShapeIcon
import com.weightkit.app.ui.WeightKitTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val state = AppHolder.get(this)
        val pro = ProHolder.get(this, state.firstLaunch)
        pro.connect()
        pro.touchClock()
        val ads = AdsHolder.get(this, pro)
        ads.start(this)
        setContent {
            WeightKitTheme(state.theme) {
                var tab by rememberSaveable { mutableIntStateOf(0) }
                LaunchedEffect(tab) {
                    // full-screen ad only at this natural break, at most every 48 h, free users only
                    if (tab == 1) ads.showInterstitialIfDue(this@MainActivity)
                }
                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                selected = tab == 0, onClick = { tab = 0 }, modifier = Modifier.testTag("tab_calc"),
                                icon = { ShapeIcon(ShapeKind.IBEAM, LocalContentColor.current, Modifier.size(24.dp)) },
                                label = { Text(stringResource(R.string.tab_calc)) },
                            )
                            NavigationBarItem(
                                selected = tab == 1, onClick = { tab = 1 }, modifier = Modifier.testTag("tab_lists"),
                                icon = { Icon(Icons.AutoMirrored.Filled.List, null) },
                                label = { Text(stringResource(R.string.tab_lists)) },
                            )
                            NavigationBarItem(
                                selected = tab == 2, onClick = { tab = 2 }, modifier = Modifier.testTag("tab_settings"),
                                icon = { Icon(Icons.Filled.Settings, null) },
                                label = { Text(stringResource(R.string.tab_settings)) },
                            )
                        }
                    }
                ) { inner ->
                    Box(Modifier.fillMaxSize().padding(inner)) {
                        when (tab) {
                            0 -> CalculatorScreen(state, pro)
                            1 -> ListsScreen(state, pro)
                            else -> SettingsScreen(state, pro)
                        }
                    }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        AppHolder.get(this).saveCalc()
    }

    override fun onResume() {
        super.onResume()
        val pro = ProHolder.get(this, AppHolder.get(this).firstLaunch)
        pro.touchClock()
        pro.refresh()
        AdsHolder.get(this, pro).start(this) // starts only once the trial / reward is over
        com.weightkit.app.debug.DebugHooks.logState(pro)
    }
}
