package com.ishaan.myMedia.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PinSetupDialog(
    onDismiss: () -> Unit,
    onPinSet: (String) -> Unit
) {
    var step by remember { mutableIntStateOf(1) } // 1: Initial, 2: Verify
    var firstPin by remember { mutableStateOf("") }
    var currentInput by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (step == 1) "Set PIN" else "Confirm PIN")
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (step == 1) "Enter a 4-digit PIN" else "Re-enter your PIN to verify",
                    style = MaterialTheme.typography.bodyMedium
                )
                
                Spacer(Modifier.height(24.dp))
                
                // PIN dots
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    repeat(4) { index ->
                        val isFilled = index < currentInput.length
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isFilled) MaterialTheme.colorScheme.primary 
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                                )
                        )
                    }
                }
                
                if (errorText.isNotEmpty()) {
                    Text(
                        text = errorText,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
                
                Spacer(Modifier.height(32.dp))
                
                NumericKeypad(
                    onNumberClick = { num ->
                        if (currentInput.length < 4) {
                            currentInput += num
                            errorText = ""
                            
                            if (currentInput.length == 4) {
                                if (step == 1) {
                                    firstPin = currentInput
                                    currentInput = ""
                                    step = 2
                                } else {
                                    if (currentInput == firstPin) {
                                        onPinSet(currentInput)
                                    } else {
                                        errorText = "PINs do not match. Try again."
                                        currentInput = ""
                                        step = 1
                                        firstPin = ""
                                    }
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
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun NumericKeypad(
    onNumberClick: (String) -> Unit,
    onBackspaceClick: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val rows = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("", "0", "BACK")
        )
        
        rows.forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                row.forEach { item ->
                    if (item.isEmpty()) {
                        Spacer(Modifier.size(64.dp))
                    } else if (item == "BACK") {
                        IconButton(
                            onClick = onBackspaceClick,
                            modifier = Modifier.size(64.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Backspace, null)
                        }
                    } else {
                        Surface(
                            onClick = { onNumberClick(item) },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.size(64.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = item,
                                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Normal)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
