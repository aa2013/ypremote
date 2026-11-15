package com.yp.remote

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.yp.remote.data.WorkoutStore
import com.yp.remote.ble.TreadmillManager
import com.yp.remote.ui.GradientButton
import com.yp.remote.ui.LocalYp
import com.yp.remote.ui.MeshBackground
import com.yp.remote.ui.YpMotion
import com.yp.remote.ui.YpRadius
import com.yp.remote.ui.YpSettings
import com.yp.remote.ui.YpTheme
import com.yp.remote.ui.ConnectScreen
import com.yp.remote.ui.DebugScreen
import com.yp.remote.ui.HistoryScreen
import com.yp.remote.ui.RemoteScreen
import com.yp.remote.ui.SettingsScreen
import com.yp.remote.ui.ThemeMode

/** 页面路由 */
private enum class Screen { CONNECT, HISTORY, REMOTE, SETTINGS, DEBUG }

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        TreadmillManager.init(this)
        WorkoutStore.init(this)
        enableEdgeToEdge()
        setContent {
            AppRoot()
        }
    }
}

@Composable
private fun AppRoot() {
    val mode by YpSettings.themeMode.collectAsState()
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    YpTheme(dark = dark) {
        // 窗口底色随主题：Activity 重建（切语言/主题）与冷启动不再闪系统默认白底
        val yp = LocalYp.current
        val activity = LocalContext.current as? Activity
        LaunchedEffect(dark, activity) {
            val win = activity?.window ?: return@LaunchedEffect
            win.setBackgroundDrawable(ColorDrawable(yp.bg.toArgb()))
            WindowCompat.getInsetsController(win, win.decorView).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
        Navigation()
    }
}

@Composable
private fun Navigation() {
    var granted by rememberSaveable { mutableStateOf(hasBlePermission()) }
    var screen by rememberSaveable { mutableStateOf(Screen.CONNECT) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        granted = result.values.all { it } && hasBlePermission()
    }

    LaunchedEffect(Unit) {
        if (!granted) permissionLauncher.launch(neededPermissions())
    }

    // 返回键按层级回退：调试 → 设置 → 连接页
    BackHandler(enabled = screen != Screen.CONNECT) {
        screen = if (screen == Screen.DEBUG) Screen.SETTINGS else Screen.CONNECT
    }

    AnimatedContent(
        targetState = screen,
        transitionSpec = {
            // 方向感知转场：前进从右滑入、后退反向（位移 1/3 屏 + 淡入淡出）
            val forward = targetState.ordinal >= initialState.ordinal
            val enter = slideInHorizontally(
                tween(YpMotion.durTrans, easing = YpMotion.ease),
            ) { full -> if (forward) full / 3 else -full / 3 } +
                fadeIn(tween(YpMotion.durTrans, easing = YpMotion.ease))
            val exit = slideOutHorizontally(
                tween(YpMotion.durTrans, easing = YpMotion.ease),
            ) { full -> if (forward) -full / 3 else full / 3 } +
                fadeOut(tween(YpMotion.durTrans, easing = YpMotion.ease))
            enter togetherWith exit
        },
        label = "navigation",
    ) { target ->
        Box(modifier = Modifier.fillMaxSize()) {
            MeshBackground(Modifier.fillMaxSize())
            when (target) {
                Screen.CONNECT -> ConnectScreen(
                    onOpenRemote = { screen = Screen.REMOTE },
                    onOpenHistory = { screen = Screen.HISTORY },
                    onOpenSettings = { screen = Screen.SETTINGS },
                    onOpenDebug = { screen = Screen.DEBUG },
                )
                Screen.HISTORY -> HistoryScreen(
                    onBack = { screen = Screen.CONNECT },
                )
                Screen.REMOTE -> RemoteScreen(
                    onExit = { screen = Screen.CONNECT },
                    onOpenConnect = { screen = Screen.CONNECT },
                )
                Screen.SETTINGS -> SettingsScreen(
                    onBack = { screen = Screen.CONNECT },
                    onOpenDebug = { screen = Screen.DEBUG },
                )
                Screen.DEBUG -> DebugScreen(
                    onBack = { screen = Screen.SETTINGS },
                )
            }

            if (!granted) {
                PermissionGate(
                    onRequest = { permissionLauncher.launch(neededPermissions()) },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

/** 权限门：半透明遮罩 + 居中说明卡片 */
@Composable
private fun PermissionGate(onRequest: () -> Unit, modifier: Modifier = Modifier) {
    val yp = LocalYp.current
    Box(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.45f))
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .padding(32.dp)
                .background(yp.surface, YpRadius.overlay)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(R.string.permission_title),
                color = yp.ink,
                fontSize = 18.sp,
            )
            Text(
                stringResource(R.string.permission_body),
                color = yp.ink2,
                fontSize = 14.sp,
                modifier = Modifier.padding(vertical = 16.dp),
            )
            GradientButton(stringResource(R.string.permission_grant)) { onRequest() }
        }
    }
}

/** S 及以上用新权限；旧系统需要定位权限才能扫描 */
private fun neededPermissions(): Array<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    arrayOf(
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT,
    )
} else {
    arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
    )
}

private fun hasBlePermission(): Boolean {
    val context: Context = TreadmillManager.context() ?: return false
    return neededPermissions().all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
}
