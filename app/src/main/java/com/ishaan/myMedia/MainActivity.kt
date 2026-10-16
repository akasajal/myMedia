package com.ishaan.myMedia

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.view.WindowManager
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen  // ← NEW
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ishaan.myMedia.data.model.AppTheme
import com.ishaan.myMedia.ui.components.LockScreen
import com.ishaan.myMedia.ui.screens.MainScreen
import com.ishaan.myMedia.ui.theme.GalleryTheme
import com.ishaan.myMedia.viewmodel.GalleryViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {

    private val viewModel: GalleryViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // ── Splash screen — must be called BEFORE super.onCreate() ────────────
        val splashScreen = installSplashScreen()

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Protect previews before gallery content is drawn, including on a cold start.
        updateRecentsPrivacy(viewModel.uiState.value.privacyPrefs.pinEnabled)
        lifecycleScope.launch {
            viewModel.uiState
                .map { it.privacyPrefs.pinEnabled }
                .distinctUntilChanged()
                .collect { updateRecentsPrivacy(it) }
        }

        // Optional: keep the splash visible while the ViewModel is still loading
        // (e.g. reading SharedPreferences for theme / lock state).
        // Remove this block if your initial load is already fast enough.
        splashScreen.setKeepOnScreenCondition {
            viewModel.uiState.value.isLoading   // flip this flag to false once ready
        }
        // ─────────────────────────────────────────────────────────────────────

        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val theme   by viewModel.theme.collectAsStateWithLifecycle()

            val isDark = when (theme) {
                AppTheme.SYSTEM -> isSystemInDarkTheme()
                AppTheme.LIGHT  -> false
                AppTheme.DARK   -> true
            }

            GalleryTheme(darkTheme = isDark, accentColor = uiState.accentColor) {
                PermissionGate(onPermissionsChanged = viewModel::refreshMediaOnResume) {
                    if (uiState.isLocked) {
                        LockScreen(
                            useBiometrics           = uiState.privacyPrefs.biometricsEnabled,
                            onAuthenticateBiometric = { launchBiometricPrompt { viewModel.unlock() } },
                            onPinEntered            = { pin -> viewModel.checkPin(pin) },
                            onUnlocked              = { viewModel.unlock() },
                        )
                    } else {
                        MainScreen(viewModel)
                    }
                }
            }
        }
    }

    private fun updateRecentsPrivacy(lockEnabled: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Keep foreground screenshots available while preventing media snapshots in Recents.
            setRecentsScreenshotEnabled(!lockEnabled)
        } else if (lockEnabled) {
            // Older versions have no Recents-only snapshot control.
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    override fun onStop() {
        super.onStop()
        val prefs = viewModel.uiState.value.privacyPrefs
        if (prefs.pinEnabled && prefs.instantLockEnabled) {
            viewModel.lock()
        }
    }

    private fun launchBiometricPrompt(onSuccess: () -> Unit) {
        val executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }
        })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock myMedia")
            .setSubtitle("Use biometrics to unlock")
            .setNegativeButtonText("Use PIN")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()
        prompt.authenticate(info)
    }
}

@Composable
fun PermissionGate(onPermissionsChanged: () -> Unit, onGranted: @Composable () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val permissionsChanged by rememberUpdatedState(onPermissionsChanged)
    var hasPermission by remember { mutableStateOf(false) }
    var folderOnly by rememberSaveable { mutableStateOf(false) }
    val permissions = when {
        Build.VERSION.SDK_INT >= 34 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
    fun refreshAccess() {
        hasPermission = permissions.any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED } ||
            context.contentResolver.persistedUriPermissions.any { it.isReadPermission }
        if (hasPermission || folderOnly) permissionsChanged()
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refreshAccess() }
    DisposableEffect(lifecycleOwner, folderOnly) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refreshAccess() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (hasPermission || folderOnly) {
        onGranted()
    } else {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text("Choose media access", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))
                Text("Allow photos, videos, or selected media. You can also choose individual folders in Settings without granting library access.")
                Spacer(Modifier.height(16.dp))
                Button(onClick = { launcher.launch(permissions) }) { Text("Allow media access") }
                TextButton(onClick = {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                }) { Text("Open permission settings") }
                TextButton(onClick = { folderOnly = true; permissionsChanged() }) { Text("Continue with folders") }
            }
        }
    }
}
