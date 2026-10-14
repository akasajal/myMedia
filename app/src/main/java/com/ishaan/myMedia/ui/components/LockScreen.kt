package com.ishaan.myMedia.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LockScreen(
    useBiometrics: Boolean,
    onAuthenticateBiometric: () -> Unit,
    onPinEntered: (String) -> Boolean, // returns true if correct
    onUnlocked: () -> Unit
) {
    var currentInput by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf("") }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Default.Lock,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            
            Spacer(Modifier.height(16.dp))
            
            Text(
                text = "Enter PIN to Unlock",
                style = MaterialTheme.typography.titleLarge
            )
            
            Spacer(Modifier.height(32.dp))
            
            // PIN dots
            Row(
                modifier = Modifier.clearAndSetSemantics {
                    contentDescription = "PIN entry"
                    stateDescription = "${currentInput.length} of 4 digits entered"
                },
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                repeat(4) { index ->
                    val isFilled = index < currentInput.length
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(
                                if (isFilled) MaterialTheme.colorScheme.primary 
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                            )
                    )
                }
            }
            
            Spacer(Modifier.height(48.dp))
            if (errorText.isNotEmpty()) Text(
                errorText, color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            
            NumericKeypad(
                onNumberClick = { num ->
                    errorText = ""
                    if (currentInput.length < 4) {
                        currentInput += num
                        if (currentInput.length == 4) {
                            if (onPinEntered(currentInput)) {
                                onUnlocked()
                            } else {
                                currentInput = ""
                                errorText = "Incorrect PIN. Try again."
                            }
                        }
                    }
                },
                onBackspaceClick = {
                    if (currentInput.isNotEmpty()) {
                        currentInput = currentInput.dropLast(1)
                    }
                }
            )
            
            if (useBiometrics) {
                Spacer(Modifier.height(24.dp))
                TextButton(onClick = onAuthenticateBiometric) {
                    Icon(Icons.Default.Fingerprint, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Use Biometrics")
                }
            }
        }
    }
    
    // Auto-trigger biometrics on start
    LaunchedEffect(Unit) {
        if (useBiometrics) {
            onAuthenticateBiometric()
        }
    }
}
