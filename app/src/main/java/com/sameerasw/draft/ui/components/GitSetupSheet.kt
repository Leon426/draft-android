package com.sameerasw.draft.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.sameerasw.draft.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitSetupSheet(
    initialRepoUrl: String,
    initialPat: String,
    initialAuthorName: String,
    initialAuthorEmail: String,
    isLoading: Boolean,
    errorMessage: String?,
    onSaveAndClone: (repoUrl: String, pat: String, authorName: String, authorEmail: String) -> Unit,
    onDismissRequest: () -> Unit
) {
    var repoUrl by remember { mutableStateOf(initialRepoUrl.ifBlank { "https://github.com/username/draft-notes.git" }) }
    var pat by remember { mutableStateOf(initialPat) }
    var authorName by remember { mutableStateOf(initialAuthorName.ifBlank { "Draft User" }) }
    var authorEmail by remember { mutableStateOf(initialAuthorEmail.ifBlank { "draft@local" }) }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.setup_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            OutlinedTextField(
                value = repoUrl,
                onValueChange = { repoUrl = it },
                label = { Text(stringResource(R.string.setup_repo_url_label)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                enabled = !isLoading
            )

            OutlinedTextField(
                value = pat,
                onValueChange = { pat = it },
                label = { Text(stringResource(R.string.setup_pat_label)) },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                enabled = !isLoading
            )

            OutlinedTextField(
                value = authorName,
                onValueChange = { authorName = it },
                label = { Text(stringResource(R.string.setup_author_name_label)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                enabled = !isLoading
            )

            OutlinedTextField(
                value = authorEmail,
                onValueChange = { authorEmail = it },
                label = { Text(stringResource(R.string.setup_author_email_label)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                enabled = !isLoading
            )

            if (!errorMessage.isNullOrBlank()) {
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.padding(vertical = 8.dp))
                Text(stringResource(R.string.setup_in_progress), style = MaterialTheme.typography.bodySmall)
            } else {
                Button(
                    onClick = { onSaveAndClone(repoUrl, pat, authorName, authorEmail) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = repoUrl.isNotBlank() && pat.isNotBlank()
                ) {
                    Text(stringResource(R.string.setup_clone_button))
                }
            }
        }
    }
}
