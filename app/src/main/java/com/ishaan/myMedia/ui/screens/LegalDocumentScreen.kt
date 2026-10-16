package com.ishaan.myMedia.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ishaan.myMedia.R

enum class LegalDocument { TERMS, PRIVACY }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LegalDocumentScreen(document: LegalDocument, onBack: () -> Unit) {
    val privacy = document == LegalDocument.PRIVACY
    val headings = stringArrayResource(if (privacy) R.array.privacy_headings else R.array.terms_headings)
    val paragraphs = stringArrayResource(if (privacy) R.array.privacy_paragraphs else R.array.terms_paragraphs)
    val uriHandler = LocalUriHandler.current
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(if (privacy) R.string.privacy_policy_title else R.string.terms_title)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.widget_back))
                }
            },
        )
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            item { Text(stringResource(R.string.legal_updated), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant) }
            itemsIndexed(headings) { index, heading ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(heading, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                    SelectionContainer {
                        Text(paragraphs[index], style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            item {
                TextButton(onClick = { uriHandler.openUri("https://github.com/akasajal") }) {
                    Text(stringResource(R.string.legal_contact))
                }
            }
        }
    }
}
