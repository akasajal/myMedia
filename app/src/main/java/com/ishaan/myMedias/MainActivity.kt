package com.ishaan.myMedias

import android.Manifest
import android.os.Build
import android.os.Bundle
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen  // ← NEW
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ishaan.myMedias.data.model.AppTheme
import com.ishaan.myMedias.ui.components.LockScreen
import com.ishaan.myMedias.ui.screens.MainScreen
import com.ishaan.myMedias.ui.theme.GalleryTheme
import com.ishaan.myMedias.viewmodel.GalleryViewModel

class MainActivity : FragmentActivity() {

    private val viewModel: GalleryViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // ── Splash screen — must be called BEFORE super.onCreate() ────────────
        val splashScreen = installSplashScreen()

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

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

            GalleryTheme(darkTheme = isDark) {
                PermissionGate {
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
            .setTitle("Unlock myMedias")
            .setSubtitle("Use biometrics to unlock")
            .setNegativeButtonText("Use PIN")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()
        prompt.authenticate(info)
    }
}

@Composable
fun PermissionGate(onGranted: @Composable () -> Unit) {
    var hasPermission by remember { mutableStateOf(false) }

    val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
        )
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasPermission = results.values.all { it }
    }

    LaunchedEffect(Unit) { launcher.launch(permissions) }

    if (hasPermission) {
        onGranted()
    } else {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Storage permission needed", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                Button(onClick = { launcher.launch(permissions) }) { Text("Grant permission") }
            }
        }
    }
}