package com.iqforge

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.iqforge.workspace.WorkspaceEntry
import com.iqforge.workspace.WorkspaceUiState
import com.iqforge.workspace.WorkspaceViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { WorkspaceApp() } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkspaceApp(viewModel: WorkspaceViewModel = viewModel()) {
    val state by viewModel.state
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.repo?.name ?: "iQForge") },
                navigationIcon = {
                    if (state.selectedFile != null) {
                        IconButton(onClick = viewModel::closeEditor) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to files")
                        }
                    }
                },
                actions = {
                    if (state.repo != null && state.selectedFile == null) {
                        IconButton(onClick = viewModel::pull, enabled = !state.busy) {
                            Icon(Icons.Default.Refresh, contentDescription = "Pull repository")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)
        ) {
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 8.dp))
            }
            state.message?.let {
                Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical = 8.dp))
            }
            when {
                state.busy -> BusyContent(state.operation)
                state.repo == null -> CloneContent(state, viewModel)
                state.selectedFile == null -> FileTreeContent(state, viewModel)
                else -> state.selectedFile?.let { selected ->
                    EditorContent(
                        path = selected.relativePath,
                        text = state.editorText,
                        dirty = state.editorDirty,
                        onTextChange = viewModel::updateEditor,
                        onSave = viewModel::saveFile
                    )
                }
            }
        }
    }
}

@Composable
private fun BusyContent(operation: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(12.dp))
        Text(operation)
    }
}

@Composable
private fun CloneContent(state: WorkspaceUiState, viewModel: WorkspaceViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Clone a repository", style = MaterialTheme.typography.headlineSmall)
        Text("Public repositories need only a URL. For a private GitHub repository, add your username and a personal access token; the token stays in memory only.")
        OutlinedTextField(
            value = state.repoUrl,
            onValueChange = viewModel::updateRepoUrl,
            label = { Text("HTTPS repository URL") },
            placeholder = { Text("https://github.com/owner/repository.git") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
        )
        OutlinedTextField(
            value = state.githubUsername,
            onValueChange = viewModel::updateUsername,
            label = { Text("GitHub username (optional)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        OutlinedTextField(
            value = state.githubToken,
            onValueChange = viewModel::updateToken,
            label = { Text("GitHub token (optional)") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Button(
            onClick = viewModel::cloneRepository,
            enabled = state.repoUrl.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Clone to phone") }
    }
}

@Composable
private fun FileTreeContent(state: WorkspaceUiState, viewModel: WorkspaceViewModel) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text("Files", style = MaterialTheme.typography.titleMedium)
        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp, max = 420.dp)) {
            items(state.entries, key = { it.relativePath }) { entry ->
                TextButton(onClick = { viewModel.openEntry(entry) }, modifier = Modifier.fillMaxWidth()) {
                    Spacer(Modifier.padding(start = (entry.depth * 8).dp))
                    Icon(
                        imageVector = when {
                            !entry.directory -> Icons.Default.Description
                            entry.expanded -> Icons.Default.FolderOpen
                            else -> Icons.Default.Folder
                        },
                        contentDescription = null
                    )
                    Text(
                        entry.name,
                        modifier = Modifier.padding(start = 8.dp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
        OutlinedTextField(
            value = state.githubUsername,
            onValueChange = viewModel::updateUsername,
            label = { Text("GitHub username") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        OutlinedTextField(
            value = state.githubToken,
            onValueChange = viewModel::updateToken,
            label = { Text("Token for private pull/push") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        OutlinedTextField(
            value = state.commitMessage,
            onValueChange = viewModel::updateCommitMessage,
            label = { Text("Commit message") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(onClick = viewModel::commit, modifier = Modifier.fillMaxWidth(0.5f)) { Text("Commit") }
            Button(onClick = viewModel::push, modifier = Modifier.fillMaxWidth()) { Text("Push") }
        }
    }
}

@Composable
private fun EditorContent(
    path: String,
    text: String,
    dirty: Boolean,
    onTextChange: (String) -> Unit,
    onSave: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(path, style = MaterialTheme.typography.labelMedium)
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier.fillMaxWidth().heightIn(min = 260.dp, max = 600.dp).padding(vertical = 8.dp),
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            label = { Text(if (dirty) "Edited" else "Editor") }
        )
        Button(
            onClick = onSave,
            enabled = dirty,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
        ) { Text("Save file") }
    }
}
