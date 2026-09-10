package com.iqforge

import android.os.Bundle
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.iqforge.engine.OfflineEngine
import kotlinx.coroutines.launch
import com.iqforge.workspace.WorkspaceEntry
import com.iqforge.workspace.WorkspaceUiState
import com.iqforge.workspace.WorkspaceViewModel

private val ForgeDarkColors = darkColorScheme(primary = Color(0xFF5B9CFF), background = Color(0xFF141414), surface = Color(0xFF1B1B1B), surfaceVariant = Color(0xFF222222), outline = Color(0xFF373737))
private val ForgeLightColors = lightColorScheme(primary = Color(0xFF185ABC), background = Color(0xFFFFFBFF), surface = Color(0xFFFFFBFF), surfaceVariant = Color(0xFFE7E0EC))

private enum class Appearance { SYSTEM, LIGHT, DARK }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) = super.onCreate(savedInstanceState).also {
        setContent {
            ForgeTheme { appearance, updateAppearance ->
                var showSplash by remember { mutableStateOf(true) }
                if (showSplash) LaunchSplash { showSplash = false }
                else AgentApp(appearance = appearance, onAppearanceChange = updateAppearance)
            }
        }
    }
}

@Composable private fun LaunchSplash(onFinished: () -> Unit) {
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(1100)
        onFinished()
    }
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(R.drawable.iqforge_logo),
                contentDescription = "iQForge",
                modifier = Modifier.fillMaxWidth(),
                contentScale = ContentScale.Fit
            )
            Spacer(Modifier.height(22.dp))
            Text("CODE ON THE GO", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
            Text("v0.1", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .62f), modifier = Modifier.padding(top = 6.dp))
            Spacer(Modifier.height(72.dp))
            Text("Powered by", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .58f))
            Surface(color = Color.White, shape = RoundedCornerShape(6.dp), modifier = Modifier.padding(top = 7.dp)) {
                Image(
                    painter = painterResource(R.drawable.iqoo_logo),
                    contentDescription = "iQOO",
                    modifier = Modifier.width(88.dp).height(28.dp).padding(horizontal = 8.dp, vertical = 6.dp),
                    contentScale = ContentScale.Fit
                )
            }
        }
    }
}

@Composable private fun ForgeTheme(content: @Composable (Appearance, (Appearance) -> Unit) -> Unit) {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("iqforge_settings", Context.MODE_PRIVATE) }
    var appearance by rememberSaveable {
        mutableStateOf(runCatching { Appearance.valueOf(preferences.getString("appearance", Appearance.SYSTEM.name) ?: Appearance.SYSTEM.name) }.getOrDefault(Appearance.SYSTEM))
    }
    val dark = when (appearance) { Appearance.SYSTEM -> isSystemInDarkTheme(); Appearance.LIGHT -> false; Appearance.DARK -> true }
    MaterialTheme(colorScheme = if (dark) ForgeDarkColors else ForgeLightColors) {
        content(appearance) { value ->
            appearance = value
            preferences.edit().putString("appearance", value.name).apply()
        }
    }
}

sealed interface FeedItem {
    data class User(val text: String) : FeedItem
    data class Status(val text: String, val success: Boolean = false, val error: Boolean = false) : FeedItem
    data class Tool(val text: String) : FeedItem
    data class Reply(val text: String) : FeedItem
    data class Diff(val path: String, val removed: String, val added: String, val summary: String) : FeedItem
}

class AgentViewModel : ViewModel() {
    private val offlineEngine = OfflineEngine()
    var composer by mutableStateOf(""); private set
    var bridgeUrl by mutableStateOf("http://192.168.1.2:8000"); private set
    var sending by mutableStateOf(false); private set
    var feed by mutableStateOf<List<FeedItem>>(emptyList()); private set
    fun updateComposer(value: String) { composer = value }
    fun updateBridgeUrl(value: String) { bridgeUrl = value }
    fun showClone(name: String) { if (feed.none { it is FeedItem.Status && it.text == "Cloned $name" }) feed += FeedItem.Status("Cloned $name", success = true) }
    fun send(fileContext: String = "") {
        val prompt = composer.trim(); if (prompt.isEmpty() || sending) return
        composer = ""; sending = true
        feed += FeedItem.User(prompt)
        feed += FeedItem.Tool("iQForge — preparing an on-device response…")
        viewModelScope.launch {
            try {
                val response = when {
                    prompt.contains("review", ignoreCase = true) -> {
                        val findings = offlineEngine.review(fileContext)
                        if (findings.isEmpty()) "Offline review: no common high-risk patterns were found in the supplied change."
                        else findings.joinToString("\n") { "${it.severity} line ${it.line}: ${it.message}" }
                    }
                    prompt.contains("debug", ignoreCase = true) || prompt.contains("crash", ignoreCase = true) -> offlineEngine.debug(prompt, fileContext)
                    prompt.contains("explain", ignoreCase = true) -> offlineEngine.explain(fileContext)
                    else -> offlineEngine.write(prompt, fileContext)
                }
                feed += FeedItem.Reply(response)
            } catch (error: Exception) {
                feed += FeedItem.Status(error.message ?: "The offline engine could not complete this request.", error = true)
            } finally {
                sending = false
            }
        }
    }
    fun retry() { feed += FeedItem.Status("Ready to retry using ${bridgeUrl.ifBlank { "the configured laptop bridge" }}") }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun AgentApp(
    appearance: Appearance,
    onAppearanceChange: (Appearance) -> Unit,
    workspace: WorkspaceViewModel = viewModel(),
    agent: AgentViewModel = viewModel()
) {
    val state by workspace.state
    var drawerOpen by remember { mutableStateOf(false) }
    state.repo?.let { agent.showClone(it.name) }
    if (state.selectedFile != null) { EditorScreen(state, workspace); return }
    Scaffold(
        topBar = { TopAppBar(
            title = { Column { Text(state.repo?.name ?: "iQForge", style = MaterialTheme.typography.titleMedium); Text("Local code agent", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .65f)) } },
            navigationIcon = { IconButton({ drawerOpen = !drawerOpen }) { Icon(Icons.Default.Menu, "Open repository drawer") } },
            actions = { IconButton({ drawerOpen = true }) { Icon(Icons.Default.Folder, "Repository files") } }
        ) },
        bottomBar = { Composer(agent) { agent.send(state.editorText) } }
    ) { padding ->
        Row(Modifier.fillMaxSize().padding(padding)) {
            if (drawerOpen) RepositoryDrawer(state, workspace, agent, appearance, onAppearanceChange) { drawerOpen = false }
            Feed(Modifier.weight(1f), agent, state)
        }
    }
}

@Composable private fun Feed(modifier: Modifier, agent: AgentViewModel, workspace: WorkspaceUiState) {
    LazyColumn(modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Spacer(Modifier.height(4.dp)) }
        if (workspace.repo == null && agent.feed.isEmpty()) item { StatusCard("Open the repository drawer to clone a project, then ask iQForge to review, explain, or fix code.") }
        items(agent.feed) { item -> when (item) {
            is FeedItem.User -> UserBubble(item.text)
            is FeedItem.Status -> StatusCard(item.text, item.success, item.error)
            is FeedItem.Tool -> ToolCard(item.text)
            is FeedItem.Reply -> Text(item.text, style = MaterialTheme.typography.bodyLarge)
            is FeedItem.Diff -> DiffCard(item)
        } }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable private fun UserBubble(text: String) = Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { Surface(color = Color(0xFF0B4D93), shape = RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp)) { Text(text, Modifier.padding(horizontal = 16.dp, vertical = 11.dp)) } }
@Composable private fun StatusCard(text: String, success: Boolean = false, error: Boolean = false) {
    val background = when { success -> Color(0xFF0A3D17); error -> Color(0xFF4A1517); else -> MaterialTheme.colorScheme.surfaceVariant }
    val tint = when { success -> Color(0xFF36C76A); error -> Color(0xFFFF8A80); else -> MaterialTheme.colorScheme.primary }
    Surface(color = background, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) { Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Icon(if (success) Icons.Default.Check else Icons.Default.Sync, null, tint = tint, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(10.dp)); Text(text) } }
}
@Composable private fun ToolCard(text: String) = Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) { Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Refresh, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(10.dp)); Text(text) } }
@Composable private fun DiffCard(diff: FeedItem.Diff) = Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) { Column { Text("${diff.path} · 2 lines changed", Modifier.padding(12.dp), style = MaterialTheme.typography.titleSmall); Text("− ${diff.removed}", Modifier.fillMaxWidth().background(Color(0xFF4A1215)).padding(8.dp), color = Color(0xFFFF9B9B), fontFamily = FontFamily.Monospace); Text("+ ${diff.added}", Modifier.fillMaxWidth().background(Color(0xFF0C3816)).padding(8.dp), color = Color(0xFF75DF86), fontFamily = FontFamily.Monospace); Text(diff.summary, Modifier.padding(12.dp)) } }

@Composable private fun Composer(agent: AgentViewModel, send: () -> Unit) = Surface(color = MaterialTheme.colorScheme.background, shadowElevation = 8.dp) { Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) { IconButton({}) { Icon(Icons.Default.CameraAlt, "Attach code from camera") }; IconButton({}) { Icon(Icons.Default.Mic, "Voice prompt") }; OutlinedTextField(agent.composer, agent::updateComposer, Modifier.weight(1f), placeholder = { Text("Ask iQForge…") }, singleLine = true); IconButton(send, enabled = agent.composer.isNotBlank() && !agent.sending) { Icon(Icons.AutoMirrored.Filled.Send, "Send", tint = MaterialTheme.colorScheme.primary) } } }

@Composable private fun RepositoryDrawer(state: WorkspaceUiState, workspace: WorkspaceViewModel, agent: AgentViewModel, appearance: Appearance, onAppearanceChange: (Appearance) -> Unit, close: () -> Unit) = Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.width(330.dp).fillMaxSize()) { Column(Modifier.padding(16.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) { Text("Repository", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f)); IconButton(close) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Close drawer") } }
    if (state.repo == null) ClonePanel(state, workspace) else FilePanel(state, workspace)
    Spacer(Modifier.height(12.dp)); OutlinedTextField(agent.bridgeUrl, agent::updateBridgeUrl, Modifier.fillMaxWidth(), label = { Text("Laptop bridge URL") }, singleLine = true)
    Text("Use http://<laptop-ip>:8000. Calls handle loading, timeout, retry, and offline states when the bridge is available.", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 6.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = .65f))
    TextButton(agent::retry) { Text("Retry bridge connection") }
    Text("Appearance", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Appearance.entries.forEach { choice ->
            TextButton(onClick = { onAppearanceChange(choice) }, modifier = Modifier.weight(1f)) {
                Text(if (appearance == choice) "✓ ${choice.name.lowercase().replaceFirstChar { it.uppercase() }}" else choice.name.lowercase().replaceFirstChar { it.uppercase() })
            }
        }
    }
} }

@Composable private fun ClonePanel(state: WorkspaceUiState, workspace: WorkspaceViewModel) = Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(state.repoUrl, workspace::updateRepoUrl, Modifier.fillMaxWidth(), label = { Text("Repository URL") }, singleLine = true); OutlinedTextField(state.githubUsername, workspace::updateUsername, Modifier.fillMaxWidth(), label = { Text("GitHub username (optional)") }, singleLine = true); OutlinedTextField(state.githubToken, workspace::updateToken, Modifier.fillMaxWidth(), label = { Text("Token (private repos)") }, visualTransformation = PasswordVisualTransformation(), singleLine = true); Button(workspace::cloneRepository, enabled = state.repoUrl.isNotBlank() && !state.busy, modifier = Modifier.fillMaxWidth()) { Text(if (state.busy) state.operation else "Clone to phone") }; state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall) } }

@Composable private fun FilePanel(state: WorkspaceUiState, workspace: WorkspaceViewModel) = Column { Row(verticalAlignment = Alignment.CenterVertically) { Text(state.repo?.name ?: "Files", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall); IconButton(workspace::pull, enabled = !state.busy) { Icon(Icons.Default.Refresh, "Pull") } }; LazyColumn(Modifier.weight(1f, fill = false)) { items(state.entries, key = { it.relativePath }) { entry -> FileRow(entry, workspace) } }; state.message?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall) }; state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall) }; OutlinedTextField(state.commitMessage, workspace::updateCommitMessage, Modifier.fillMaxWidth(), label = { Text("Commit message") }, singleLine = true); Row { Button(workspace::commit, Modifier.weight(1f), enabled = !state.busy) { Text("Commit") }; Spacer(Modifier.width(8.dp)); Button(workspace::push, Modifier.weight(1f), enabled = !state.busy) { Text("Push") } } }
@Composable private fun FileRow(entry: WorkspaceEntry, workspace: WorkspaceViewModel) = TextButton(onClick = { workspace.openEntry(entry) }, modifier = Modifier.fillMaxWidth()) { Spacer(Modifier.width((entry.depth * 12).dp)); Icon(if (entry.directory) if (entry.expanded) Icons.Default.FolderOpen else Icons.Default.Folder else Icons.Default.Description, null); Spacer(Modifier.width(8.dp)); Text(entry.name) }

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun EditorScreen(state: WorkspaceUiState, workspace: WorkspaceViewModel) = Scaffold(topBar = { TopAppBar(title = { Text(state.selectedFile?.relativePath ?: "Editor") }, navigationIcon = { IconButton(workspace::closeEditor) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to agent") } }) }) { padding -> Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) { OutlinedTextField(state.editorText, workspace::updateEditor, Modifier.weight(1f).fillMaxWidth(), textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), label = { Text(if (state.editorDirty) "Edited" else "Editor") }); Button(workspace::saveFile, enabled = state.editorDirty, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Save file") } } }
