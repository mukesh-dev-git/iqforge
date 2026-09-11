package com.iqforge

import android.os.Bundle
import android.content.Context
import android.app.Application
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.lifecycle.AndroidViewModel
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.core.view.WindowCompat
import androidx.core.content.FileProvider
import com.iqforge.engine.CodeEngine
import com.iqforge.engine.NativeEngine
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.iqforge.bridge.BridgeTask
import com.iqforge.bridge.LaptopBridgeClient
import com.iqforge.bridge.BridgeConnector
import com.iqforge.bridge.BridgeModel
import com.iqforge.chat.AttachmentKind
import com.iqforge.chat.ChatAttachment
import com.iqforge.chat.ChatAttachmentService
import com.iqforge.chat.ChatHistoryStore
import com.iqforge.chat.SavedChat
import com.iqforge.engine.OfflineEngine
import kotlinx.coroutines.launch
import com.iqforge.workspace.WorkspaceEntry
import com.iqforge.workspace.WorkspaceUiState
import com.iqforge.workspace.WorkspaceViewModel
import java.io.IOException
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val IqfCoral = Color(0xFFDA7756)
private val ForgeDarkColors = darkColorScheme(
    primary = IqfCoral,
    background = Color(0xFF121311),
    surface = Color(0xFF1D1E1B),
    surfaceVariant = Color(0xFF282925),
    outline = Color(0xFF3B3C37),
    onBackground = Color(0xFFF2EFE9),
    onSurface = Color(0xFFF2EFE9),
    onSurfaceVariant = Color(0xFFAAA9A3)
)
private val ForgeLightColors = lightColorScheme(primary = Color(0xFF185ABC), background = Color(0xFFFFFBFF), surface = Color(0xFFFFFBFF), surfaceVariant = Color(0xFFE7E0EC))

private enum class Appearance { SYSTEM, LIGHT, DARK }
private enum class AppDestination { CHATS, PROJECTS, CODE, ARTIFACTS }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) = super.onCreate(savedInstanceState).also {
        window.statusBarColor = android.graphics.Color.rgb(18, 19, 17)
        window.navigationBarColor = android.graphics.Color.BLACK
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
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
        mutableStateOf(runCatching { Appearance.valueOf(preferences.getString("appearance", Appearance.DARK.name) ?: Appearance.DARK.name) }.getOrDefault(Appearance.DARK))
    }
    val dark = when (appearance) { Appearance.SYSTEM -> isSystemInDarkTheme(); Appearance.LIGHT -> false; Appearance.DARK -> true }
    MaterialTheme(colorScheme = if (dark) ForgeDarkColors else ForgeLightColors) {
        content(appearance) { value ->
            appearance = value
            preferences.edit().putString("appearance", value.name).apply()
        }
    }
}

// ---------------------------------------------------------------------------
// Feed item model
// ---------------------------------------------------------------------------

sealed interface FeedItem {
    data class User(val text: String) : FeedItem
    data class Status(val text: String, val success: Boolean = false, val error: Boolean = false) : FeedItem
    data class Tool(val text: String) : FeedItem
    data class Reply(val text: String) : FeedItem
    data class Diff(val path: String, val removed: String, val added: String, val summary: String) : FeedItem

    /**
     * Shown after every successful OfflineEngine reply when a bridge URL is configured.
     * The user must tap "Ask laptop" explicitly — nothing is sent to the network automatically.
     *
     * @param loading  true while the HTTP call is in-flight (button replaced by a spinner)
     */
    data class EscalatePrompt(
        val prompt: String,
        val context: String,
        val task: BridgeTask,
        val loading: Boolean = false
    ) : FeedItem

    /** Successful response received from the laptop bridge. */
    data class LaptopReply(val text: String) : FeedItem

    /**
     * Network or timeout error from an escalation attempt.
     * The preceding OfflineEngine reply is preserved above this card.
     * Carries enough data to retry the exact same call.
     */
    data class EscalateError(
        val message: String,
        val prompt: String,
        val context: String,
        val task: BridgeTask
    ) : FeedItem
}

enum class ToolAccessMode(val label: String) {
    AUTO("Auto"),
    ASK("On demand"),
    AUTOMATIC("Always available"),
    OFF("Off")
}

enum class EffortLevel(val label: String, val wireName: String) {
    LOW("Low", "low"),
    MEDIUM("Medium", "medium"),
    HIGH("High", "high"),
    EXTRA("Extra", "extra"),
    MAX("Max", "max")
}

// ---------------------------------------------------------------------------
// ViewModel
// ---------------------------------------------------------------------------

class AgentViewModel(
    private val codeEngine: CodeEngine,
    internal var bridgeClient: LaptopBridgeClient = LaptopBridgeClient(),
    private val preferences: android.content.SharedPreferences? = null,
    private val attachmentService: ChatAttachmentService = ChatAttachmentService()
) : ViewModel() {
    private val historyStore = preferences?.let(::ChatHistoryStore)
    var composer by mutableStateOf(""); private set
    var bridgeUrl by mutableStateOf(preferences?.getString("bridge_url", DEFAULT_BRIDGE_URL) ?: DEFAULT_BRIDGE_URL); private set
    var sending by mutableStateOf(false); private set
    var feed by mutableStateOf<List<FeedItem>>(emptyList()); internal set
    var attachments by mutableStateOf<List<ChatAttachment>>(emptyList()); private set
    var attachmentBusy by mutableStateOf(false); private set
    var attachmentMessage by mutableStateOf<String?>(null); private set
    var webSearchEnabled by mutableStateOf(preferences?.getBoolean("web_search", false) ?: false); private set
    var memoryEnabled by mutableStateOf(preferences?.getBoolean("memory", true) ?: true); private set
    var toolAccessMode by mutableStateOf(
        runCatching {
            ToolAccessMode.valueOf(preferences?.getString("tool_access", ToolAccessMode.AUTO.name) ?: ToolAccessMode.AUTO.name)
        }.getOrDefault(ToolAccessMode.AUTO)
    ); private set
    var effort by mutableStateOf(
        runCatching {
            EffortLevel.valueOf(preferences?.getString("effort", EffortLevel.MEDIUM.name) ?: EffortLevel.MEDIUM.name)
        }.getOrDefault(EffortLevel.MEDIUM)
    ); private set
    var connectorStatus by mutableStateOf("Not checked"); private set
    var checkingConnector by mutableStateOf(false); private set
    var availableModels by mutableStateOf<List<BridgeModel>>(emptyList()); private set
    var selectedModel by mutableStateOf<String?>(null); private set
    var modelServiceReady by mutableStateOf(false); private set
    var connectors by mutableStateOf<List<BridgeConnector>>(emptyList()); private set
    var chats by mutableStateOf<List<SavedChat>>(historyStore?.load().orEmpty()); private set
    var activeChatId by mutableStateOf<String?>(null); private set
    var incognito by mutableStateOf(false); private set

    companion object {
        private const val DEFAULT_BRIDGE_URL = "http://10.0.2.2:8000"
        private const val MEMORY_SEPARATOR = "\u001E"
        private const val MAX_MEMORY_ITEMS = 6

        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val application = checkNotNull(extras[APPLICATION_KEY])
                return AgentViewModel(
                    codeEngine = NativeEngine(application),
                    preferences = application.getSharedPreferences("iqforge_agent", Context.MODE_PRIVATE)
                ) as T
            }
        }
    }

    fun updateComposer(value: String) { composer = value }
    fun updateBridgeUrl(value: String) {
        bridgeUrl = value
        preferences?.edit()?.putString("bridge_url", value)?.apply()
        connectorStatus = "Not checked"
    }
    fun updateWebSearch(enabled: Boolean) {
        webSearchEnabled = enabled
        preferences?.edit()?.putBoolean("web_search", enabled)?.apply()
    }
    fun updateMemory(enabled: Boolean) {
        memoryEnabled = enabled
        preferences?.edit()?.putBoolean("memory", enabled)?.apply()
    }
    fun updateToolAccess(mode: ToolAccessMode) {
        toolAccessMode = mode
        preferences?.edit()?.putString("tool_access", mode.name)?.apply()
    }
    fun updateEffort(level: EffortLevel) {
        effort = level
        preferences?.edit()?.putString("effort", level.name)?.apply()
    }

    fun newChat(privateMode: Boolean = false) {
        activeChatId = if (privateMode) null else "chat-${System.currentTimeMillis()}"
        incognito = privateMode
        composer = ""
        attachments = emptyList()
        attachmentMessage = null
        feed = emptyList()
    }

    fun openChat(id: String) {
        val chat = chats.firstOrNull { it.id == id } ?: return
        activeChatId = chat.id
        incognito = false
        composer = ""
        attachments = emptyList()
        feed = chat.messages.map { message ->
            if (message.role == "user") FeedItem.User(message.text) else FeedItem.Reply(message.text)
        }
    }

    private fun saveChatMessage(role: String, text: String) {
        if (incognito) return
        val id = activeChatId ?: "chat-${System.currentTimeMillis()}".also { activeChatId = it }
        chats = historyStore?.append(id, role, text).orEmpty()
    }

    fun attachCamera(bitmap: android.graphics.Bitmap) = attach("Camera") {
        attachmentService.fromCamera(bitmap)
    }

    fun attachCamera(context: Context, uri: android.net.Uri) = attach("Camera") {
        attachmentService.fromCamera(context, uri)
    }

    fun attachPhoto(context: Context, uri: android.net.Uri) = attach("Photo") {
        attachmentService.fromPhoto(context, uri)
    }

    fun attachFile(context: Context, uri: android.net.Uri) = attach("File") {
        attachmentService.fromFile(context, uri)
    }

    fun removeAttachment(id: String) {
        attachments = attachments.filterNot { it.id == id }
        attachmentMessage = if (attachments.isEmpty()) null else "${attachments.size} item(s) attached"
    }

    fun reportAttachmentError(message: String) {
        attachmentMessage = message
    }

    private fun attach(source: String, loader: suspend () -> ChatAttachment) {
        if (attachmentBusy) return
        attachmentBusy = true
        attachmentMessage = "Reading $source..."
        viewModelScope.launch {
            try {
                val attachment = loader()
                attachments = (attachments.filterNot { it.id == attachment.id } + attachment).takeLast(5)
                attachmentMessage = "Attached ${attachment.name} (${attachment.content.length} characters)"
            } catch (error: Exception) {
                attachmentMessage = error.message ?: "$source could not be attached."
            } finally {
                attachmentBusy = false
            }
        }
    }

    fun checkBridge() {
        if (checkingConnector) return
        checkingConnector = true
        connectorStatus = "Checking..."
        viewModelScope.launch {
            connectorStatus = try {
                val health = bridgeClient.health(bridgeUrl)
                modelServiceReady = health.modelReachable
                selectedModel = health.model
                if (health.modelReachable) {
                    "Connected - ${health.backend}${health.model?.let { " / $it" }.orEmpty()}"
                } else {
                    "Bridge online - model unavailable"
                }
            } catch (error: Exception) {
                "Unavailable - ${error.message ?: "connection failed"}"
            } finally {
                checkingConnector = false
            }
        }
    }

    fun refreshServices() {
        if (checkingConnector) return
        checkingConnector = true
        connectorStatus = "Checking live services..."
        viewModelScope.launch {
            try {
                val health = bridgeClient.health(bridgeUrl)
                var discoveredModels = bridgeClient.models(bridgeUrl)
                if (discoveredModels.isNotEmpty() && discoveredModels.none { it.selected }) {
                    val verified = bridgeClient.selectModel(bridgeUrl, discoveredModels.first().id)
                    discoveredModels = discoveredModels.map { it.copy(selected = it.id == verified.id) }
                }
                val discoveredConnectors = bridgeClient.connectors(bridgeUrl)
                availableModels = discoveredModels
                connectors = discoveredConnectors
                selectedModel = discoveredModels.firstOrNull { it.selected }?.id ?: health.model
                modelServiceReady = discoveredModels.any { it.id == selectedModel }
                val connectedCount = discoveredConnectors.count { it.connected }
                connectorStatus = "$connectedCount live connector(s)"
            } catch (error: Exception) {
                modelServiceReady = false
                connectorStatus = "Unavailable - ${error.message ?: "connection failed"}"
            } finally {
                checkingConnector = false
            }
        }
    }

    fun selectModel(model: BridgeModel) {
        viewModelScope.launch {
            try {
                val selected = bridgeClient.selectModel(bridgeUrl, model.id)
                selectedModel = selected.id
                availableModels = availableModels.map { it.copy(selected = it.id == selected.id) }
                modelServiceReady = true
                connectorStatus = "Connected - ollama / ${selected.id}"
            } catch (error: Exception) {
                connectorStatus = "Model selection failed - ${error.message.orEmpty()}"
            }
        }
    }
    fun showClone(name: String) {
        if (feed.none { it is FeedItem.Status && it.text == "Cloned $name" })
            feed += FeedItem.Status("Cloned $name", success = true)
    }

    /** Infer the BridgeTask from a plain-text user prompt. */
    internal fun inferTask(prompt: String): BridgeTask {
        val lower = prompt.lowercase()
        return when {
            "review" in lower -> BridgeTask.REVIEW
            "debug" in lower || "crash" in lower -> BridgeTask.DEBUG
            "explain" in lower -> BridgeTask.EXPLAIN
            else -> BridgeTask.WRITE
        }
    }

    fun send(fileContext: String = "") {
        val prompt = composer.trim(); if (prompt.isEmpty() || sending) return
        val selectedAttachments = attachments
        val remembered = if (memoryEnabled && !incognito) rememberedContext() else ""
        composer = ""; sending = true
        attachments = emptyList()
        attachmentMessage = null
        feed += FeedItem.User(prompt)
        saveChatMessage("user", prompt)
        val willUseVerifiedModel = modelServiceReady &&
            (toolAccessMode == ToolAccessMode.AUTO || toolAccessMode == ToolAccessMode.AUTOMATIC)
        feed += FeedItem.Tool(
            if (willUseVerifiedModel) "iQForge — running ${selectedModel ?: "verified model"}..."
            else "iQForge — preparing the offline fallback..."
        )
        viewModelScope.launch {
            try {
                val task = inferTask(prompt)
                var enrichedContext = buildContext(fileContext, selectedAttachments, remembered)
                if (webSearchEnabled) {
                    try {
                        val results = bridgeClient.search(bridgeUrl, prompt)
                        if (results.isNotEmpty()) {
                            val grounding = results.joinToString("\n") {
                                "- ${it.title}: ${it.snippet} (${it.url})"
                            }
                            enrichedContext += "\n\nWeb search grounding:\n$grounding"
                            feed += FeedItem.Status("Web search added ${results.size} live source(s).", success = true)
                        } else {
                            feed += FeedItem.Status("Web search completed with no matching instant results.")
                        }
                    } catch (error: Exception) {
                        feed += FeedItem.Status("Web search unavailable; continuing on-device. ${error.message.orEmpty()}", error = true)
                    }
                }
                val useRealModel = modelServiceReady &&
                    (toolAccessMode == ToolAccessMode.AUTO || toolAccessMode == ToolAccessMode.AUTOMATIC)
                val response = if (useRealModel) {
                    bridgeClient.escalateWithOptions(
                        laptopUrl = bridgeUrl,
                        task = task,
                        context = enrichedContext,
                        instruction = prompt,
                        effort = effort.wireName
                    ).also { feed += FeedItem.LaptopReply(it) }
                } else {
                    when (task) {
                        BridgeTask.REVIEW  -> {
                            val findings = codeEngine.review(enrichedContext)
                            if (findings.isEmpty()) "No issues found."
                            else findings.joinToString("\n") { "Line ${it.line}: [${it.severity}] ${it.message}" }
                        }
                        BridgeTask.DEBUG   -> codeEngine.debug(prompt, enrichedContext)
                        BridgeTask.EXPLAIN -> codeEngine.explain(enrichedContext)
                        BridgeTask.WRITE   -> codeEngine.write(prompt, enrichedContext)
                    }.also { feed += FeedItem.Reply(it) }
                }
                saveChatMessage("assistant", response)
                if (memoryEnabled && !incognito) remember("User: $prompt\nIQF: ${response.take(1_500)}")
                if (!useRealModel && bridgeUrl.isNotBlank() && toolAccessMode != ToolAccessMode.OFF) {
                    feed += FeedItem.EscalatePrompt(prompt = prompt, context = enrichedContext, task = task)
                }
            } catch (error: Exception) {
                feed += FeedItem.Status(error.message ?: "The offline engine could not complete this request.", error = true)
            } finally {
                sending = false
            }
        }
    }

    /**
     * Send the same prompt + file context to the laptop bridge.
     * Called when the user taps "Ask laptop" on an [FeedItem.EscalatePrompt] card,
     * or "Retry" on an [FeedItem.EscalateError] card.
     */
    fun escalate(prompt: String, context: String, task: BridgeTask) {
        val url = bridgeUrl.trim()

        // Guard: refuse to make a network call if the URL is not configured.
        if (url.isBlank()) {
            replaceLast(
                matchType = { it is FeedItem.EscalatePrompt || it is FeedItem.EscalateError },
                replacement = FeedItem.EscalateError(
                    message = "Set the laptop bridge URL in the drawer first.",
                    prompt = prompt, context = context, task = task
                )
            )
            return
        }

        // Show the loading state immediately in the existing card slot.
        replaceLast(
            matchType = { it is FeedItem.EscalatePrompt || it is FeedItem.EscalateError },
            replacement = FeedItem.EscalatePrompt(prompt = prompt, context = context, task = task, loading = true)
        )

        viewModelScope.launch {
            try {
                val result = bridgeClient.escalate(
                    laptopUrl = url,
                    task = task,
                    context = context,
                    instruction = prompt
                )
                replaceLast(
                    matchType = { it is FeedItem.EscalatePrompt },
                    replacement = FeedItem.LaptopReply(result)
                )
                saveChatMessage("assistant", result)
            } catch (e: IOException) {
                replaceLast(
                    matchType = { it is FeedItem.EscalatePrompt },
                    replacement = FeedItem.EscalateError(
                        message = e.message ?: "Laptop bridge unreachable.",
                        prompt = prompt, context = context, task = task
                    )
                )
            } catch (e: Exception) {
                replaceLast(
                    matchType = { it is FeedItem.EscalatePrompt },
                    replacement = FeedItem.EscalateError(
                        message = e.message ?: "Unexpected error from the laptop bridge.",
                        prompt = prompt, context = context, task = task
                    )
                )
            }
        }
    }

    /** Replace the last feed item matching [matchType] with [replacement]. Thread-safe on Main. */
    private fun replaceLast(matchType: (FeedItem) -> Boolean, replacement: FeedItem) {
        val idx = feed.indexOfLast(matchType)
        if (idx == -1) {
            feed += replacement
        } else {
            val mutable = feed.toMutableList()
            mutable[idx] = replacement
            feed = mutable
        }
    }

    private fun buildContext(
        fileContext: String,
        selectedAttachments: List<ChatAttachment>,
        remembered: String
    ): String = buildString {
        if (fileContext.isNotBlank()) append("Open editor context:\n$fileContext")
        selectedAttachments.forEach { attachment ->
            if (isNotEmpty()) append("\n\n")
            append("Attached ${attachment.kind.name.lowercase()} - ${attachment.name}:\n")
            append(attachment.content)
        }
        if (remembered.isNotBlank()) {
            if (isNotEmpty()) append("\n\n")
            append("Recent local memory:\n$remembered")
        }
    }.take(96_000)

    private fun rememberedContext(): String = preferences
        ?.getString("chat_memory", "")
        .orEmpty()
        .split(MEMORY_SEPARATOR)
        .filter { it.isNotBlank() }
        .takeLast(3)
        .joinToString("\n\n")

    private fun remember(entry: String) {
        val history = preferences?.getString("chat_memory", "")
            .orEmpty()
            .split(MEMORY_SEPARATOR)
            .filter { it.isNotBlank() }
            .plus(entry.take(2_000))
            .takeLast(MAX_MEMORY_ITEMS)
        preferences?.edit()?.putString("chat_memory", history.joinToString(MEMORY_SEPARATOR))?.apply()
    }
}

// ---------------------------------------------------------------------------
// App shell
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun AgentApp(
    appearance: Appearance,
    onAppearanceChange: (Appearance) -> Unit,
    workspace: WorkspaceViewModel = viewModel(),
    agent: AgentViewModel = viewModel(factory = AgentViewModel.Factory)
) {
    val state by workspace.state
    val context = LocalContext.current
    var navigationOpen by remember { mutableStateOf(false) }
    var destination by rememberSaveable { mutableStateOf(AppDestination.CHATS) }
    var showAddToChat by rememberSaveable { mutableStateOf(false) }
    var showToolAccess by rememberSaveable { mutableStateOf(false) }
    var showConnectors by rememberSaveable { mutableStateOf(false) }
    var showProjects by rememberSaveable { mutableStateOf(false) }
    var showModels by rememberSaveable { mutableStateOf(false) }
    var showEffort by rememberSaveable { mutableStateOf(false) }
    var cameraUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { captured ->
        val uri = cameraUri
        if (captured && uri != null) agent.attachCamera(context, uri)
        else if (!captured) agent.reportAttachmentError("Camera capture was cancelled.")
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) cameraUri?.let(cameraLauncher::launch)
        else agent.reportAttachmentError("Camera permission is required to scan code.")
    }
    val photoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { agent.attachPhoto(context, it) }
    }
    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { agent.attachFile(context, it) }
    }
    LaunchedEffect(Unit) { agent.refreshServices() }
    state.repo?.let { agent.showClone(it.name) }
    if (state.selectedFile != null) { EditorScreen(state, workspace); return }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            MinimalAgentHeader(
                incognito = agent.incognito,
                onMenu = { navigationOpen = !navigationOpen },
                onIncognito = {
                    agent.newChat(privateMode = !agent.incognito)
                    destination = AppDestination.CHATS
                }
            )
        },
        bottomBar = {
            if (destination == AppDestination.CHATS) {
                Composer(
                    agent = agent,
                    onAdd = { showAddToChat = true },
                    onModel = { showModels = true },
                    send = { agent.send(state.editorText) }
                )
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (destination) {
                AppDestination.CHATS -> Feed(Modifier.fillMaxSize(), agent, state)
                AppDestination.PROJECTS -> ProjectsPage(
                    state = state,
                    workspace = workspace,
                    onOpen = {
                        workspace.selectRepository(it)
                        destination = AppDestination.CODE
                    },
                    onNew = {
                        workspace.startNewRepository()
                        destination = AppDestination.CODE
                    }
                )
                AppDestination.CODE -> CodeWorkspacePage(state, workspace)
                AppDestination.ARTIFACTS -> ArtifactsPage(state, workspace)
            }
        }
    }
    if (navigationOpen) {
        NavigationMenu(
            state = state,
            workspace = workspace,
            agent = agent,
            appearance = appearance,
            onAppearanceChange = onAppearanceChange,
            onDestination = {
                destination = it
                navigationOpen = false
            },
            onChat = {
                agent.openChat(it)
                destination = AppDestination.CHATS
                navigationOpen = false
            },
            onNewChat = {
                agent.newChat()
                destination = AppDestination.CHATS
                navigationOpen = false
            },
            onIncognito = {
                agent.newChat(privateMode = true)
                destination = AppDestination.CHATS
                navigationOpen = false
            },
            onDismiss = { navigationOpen = false }
        )
    }
    if (showAddToChat) {
        AddToChatSheet(
            agent = agent,
            hasProject = state.repo != null,
            onDismiss = { showAddToChat = false },
            onCamera = {
                val directory = File(context.cacheDir, "camera").apply { mkdirs() }
                val target = File(directory, "capture-${System.currentTimeMillis()}.jpg")
                cameraUri = FileProvider.getUriForFile(context, "${context.packageName}.files", target)
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            },
            onPhotos = {
                photoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onFiles = {
                fileLauncher.launch(arrayOf("text/*", "application/json", "application/xml", "application/javascript"))
            },
            onProject = {
                showAddToChat = false
                showProjects = true
            },
            onToolAccess = {
                showAddToChat = false
                showToolAccess = true
            },
            onConnectors = {
                showAddToChat = false
                showConnectors = true
                agent.refreshServices()
            }
        )
    }
    if (showToolAccess) {
        ToolAccessDialog(
            selected = agent.toolAccessMode,
            onSelect = {
                agent.updateToolAccess(it)
                showToolAccess = false
            },
            onDismiss = { showToolAccess = false }
        )
    }
    if (showConnectors) {
        ConnectorsSheet(agent = agent, onDismiss = { showConnectors = false })
    }
    if (showProjects) {
        ProjectSelectorSheet(
            state = state,
            onSelect = {
                workspace.selectRepository(it)
                showProjects = false
            },
            onClone = {
                showProjects = false
                workspace.startNewRepository()
                destination = AppDestination.CODE
            },
            onDismiss = { showProjects = false }
        )
    }
    if (showModels) {
        ModelSelectorSheet(
            agent = agent,
            onEffort = {
                showModels = false
                showEffort = true
            },
            onDismiss = { showModels = false }
        )
    }
    if (showEffort) {
        EffortSheet(agent = agent, onDismiss = { showEffort = false })
    }
}

@Composable private fun MinimalAgentHeader(
    incognito: Boolean,
    onMenu: () -> Unit,
    onIncognito: () -> Unit
) =
    Surface(color = MaterialTheme.colorScheme.background) {
        Row(
            modifier = Modifier.fillMaxWidth().height(70.dp).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onMenu) {
                Icon(
                    Icons.Default.Menu,
                    contentDescription = "Open navigation",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = .72f),
                    modifier = Modifier.size(30.dp)
                )
            }
            Spacer(Modifier.weight(1f))
            if (incognito) {
                Text("Incognito chat", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
            }
            IconButton(onClick = onIncognito) {
                Icon(
                    if (incognito) Icons.Default.Close else Icons.Default.VisibilityOff,
                    contentDescription = if (incognito) "Exit incognito chat" else "Start incognito chat",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(27.dp)
                )
            }
        }
    }

// ---------------------------------------------------------------------------
// Feed & card composables
// ---------------------------------------------------------------------------

@Composable private fun Feed(modifier: Modifier, agent: AgentViewModel, workspace: WorkspaceUiState) {
    val listState = rememberLazyListState()
    val feedSize = agent.feed.size
    LaunchedEffect(feedSize) { if (feedSize > 0) listState.animateScrollToItem(feedSize - 1) }

    if (agent.feed.isEmpty()) {
        EmptyAgentState(modifier, workspace.repo?.name, agent.incognito)
        return
    }

    LazyColumn(
        modifier.fillMaxSize().padding(horizontal = 18.dp),
        state = listState,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Spacer(Modifier.height(12.dp)) }
        items(agent.feed) { item -> when (item) {
            is FeedItem.User           -> UserBubble(item.text)
            is FeedItem.Status         -> StatusCard(item.text, item.success, item.error)
            is FeedItem.Tool           -> ToolCard(item.text)
            is FeedItem.Reply          -> Text(item.text, style = MaterialTheme.typography.bodyLarge)
            is FeedItem.Diff           -> DiffCard(item)
            is FeedItem.EscalatePrompt -> EscalatePromptCard(item) { agent.escalate(item.prompt, item.context, item.task) }
            is FeedItem.LaptopReply    -> LaptopReplyCard(item.text)
            is FeedItem.EscalateError  -> EscalateErrorCard(item) { agent.escalate(item.prompt, item.context, item.task) }
        } }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable private fun EmptyAgentState(modifier: Modifier, repositoryName: String?, incognito: Boolean) =
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (incognito) {
                Icon(
                    Icons.Default.VisibilityOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(44.dp)
                )
            } else {
                Text(
                    "IQF",
                    color = IqfCoral,
                    fontWeight = FontWeight.Bold,
                    fontSize = 30.sp,
                    letterSpacing = 2.sp
                )
            }
            Spacer(Modifier.height(22.dp))
            Text(
                text = if (incognito) "Private session" else "Up late, Delfi?",
                color = MaterialTheme.colorScheme.onBackground,
                fontFamily = FontFamily.Serif,
                fontSize = 25.sp,
                lineHeight = 32.sp
            )
            if (incognito) {
                Spacer(Modifier.height(14.dp))
                Text(
                    "This chat is not saved to history or memory.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
            if (repositoryName != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = repositoryName,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }

@Composable private fun UserBubble(text: String) =
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(color = Color(0xFF553126), shape = RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp)) {
            Text(text, Modifier.padding(horizontal = 16.dp, vertical = 11.dp))
        }
    }

@Composable private fun StatusCard(text: String, success: Boolean = false, error: Boolean = false) {
    val background = when { success -> Color(0xFF0A3D17); error -> Color(0xFF4A1517); else -> MaterialTheme.colorScheme.surfaceVariant }
    val tint = when { success -> Color(0xFF36C76A); error -> Color(0xFFFF8A80); else -> MaterialTheme.colorScheme.primary }
    Surface(color = background, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (success) Icons.Default.Check else Icons.Default.Sync, null, tint = tint, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(text)
        }
    }
}

@Composable private fun ToolCard(text: String) =
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Refresh, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(text)
        }
    }

@Composable private fun DiffCard(diff: FeedItem.Diff) =
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
        Column {
            Text("${diff.path} . 2 lines changed", Modifier.padding(12.dp), style = MaterialTheme.typography.titleSmall)
            Text("- ${diff.removed}", Modifier.fillMaxWidth().background(Color(0xFF4A1215)).padding(8.dp), color = Color(0xFFFF9B9B), fontFamily = FontFamily.Monospace)
            Text("+ ${diff.added}", Modifier.fillMaxWidth().background(Color(0xFF0C3816)).padding(8.dp), color = Color(0xFF75DF86), fontFamily = FontFamily.Monospace)
            Text(diff.summary, Modifier.padding(12.dp))
        }
    }

/**
 * Shown after each offline reply when a bridge URL is configured.
 *   Idle:    "Ask laptop" button — user must tap explicitly.
 *   Loading: button replaced by a progress indicator while the HTTP call is in-flight.
 */
@Composable private fun EscalatePromptCard(item: FeedItem.EscalatePrompt, onAskLaptop: () -> Unit) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Laptop,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Deeper answer available",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "Send to laptop (${item.task.wireName})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = .65f)
                )
            }
            Spacer(Modifier.width(8.dp))
            if (item.loading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            } else {
                Button(
                    onClick = onAskLaptop,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("Ask laptop")
                }
            }
        }
    }
}

/**
 * Successful laptop bridge response.
 * Styled with a laptop icon + primary tint to visually distinguish from the offline reply above.
 */
@Composable private fun LaptopReplyCard(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 2.dp
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Laptop,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "Laptop",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(text, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

/**
 * Escalation failed — network or timeout error.
 * The offline reply above it in the feed is preserved.
 * User can tap Retry to re-issue the exact same request.
 */
@Composable private fun EscalateErrorCard(item: FeedItem.EscalateError, onRetry: () -> Unit) {
    Surface(
        color = Color(0xFF4A1517),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.WifiOff,
                    contentDescription = null,
                    tint = Color(0xFFFF8A80),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Laptop bridge unavailable",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFFFF8A80)
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                item.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .80f)
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = onRetry,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Refresh, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Retry")
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Bottom bar & drawers
// ---------------------------------------------------------------------------

@Composable private fun Composer(agent: AgentViewModel, onAdd: () -> Unit, onModel: () -> Unit, send: () -> Unit) =
    Surface(color = MaterialTheme.colorScheme.background) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 16.dp),
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(32.dp),
            shadowElevation = 10.dp,
            tonalElevation = 1.dp
        ) {
            Column(Modifier.padding(14.dp)) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 17.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (agent.modelServiceReady) "Connected coding model" else "Private offline fallback",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            if (agent.modelServiceReady) "Real model ready" else "Offline fallback",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                if (agent.attachments.isNotEmpty() || agent.attachmentMessage != null) {
                    AttachmentStrip(agent)
                }

                TextField(
                    value = agent.composer,
                    onValueChange = agent::updateComposer,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 76.dp, max = 132.dp),
                    placeholder = {
                        Text(
                            "Chat with IQF...",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .75f),
                            fontSize = 22.sp
                        )
                    },
                    enabled = !agent.sending,
                    maxLines = 4,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 20.sp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent
                    )
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilledIconButton(
                        onClick = onAdd,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Icon(Icons.Default.Add, "Attach code from camera")
                    }
                    Spacer(Modifier.width(10.dp))
                    Surface(
                        onClick = onModel,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                agent.selectedModel?.substringBefore(':') ?: "Select model",
                                fontWeight = FontWeight.Medium,
                                maxLines = 1
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = {}) {
                        Icon(
                            Icons.Default.Mic,
                            contentDescription = "Voice prompt",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(25.dp)
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    FilledIconButton(
                        onClick = { if (agent.composer.isNotBlank() && !agent.sending) send() },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = if (agent.composer.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            contentColor = if (agent.composer.isNotBlank()) Color.White else MaterialTheme.colorScheme.surface
                        )
                    ) {
                        if (agent.sending) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(21.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.surface
                            )
                        } else {
                            Icon(
                                if (agent.composer.isBlank()) Icons.Default.GraphicEq else Icons.AutoMirrored.Filled.Send,
                                contentDescription = if (agent.composer.isBlank()) "Voice conversation" else "Send"
                            )
                        }
                    }
                }
            }
        }
    }

@Composable private fun AttachmentStrip(agent: AgentViewModel) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        if (agent.attachments.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(agent.attachments, key = { it.id }) { attachment ->
                    InputChip(
                        selected = true,
                        onClick = { agent.removeAttachment(attachment.id) },
                        label = { Text(attachment.name, maxLines = 1) },
                        avatar = {
                            Icon(
                                when (attachment.kind) {
                                    AttachmentKind.CAMERA -> Icons.Default.CameraAlt
                                    AttachmentKind.PHOTO -> Icons.Default.Photo
                                    AttachmentKind.FILE -> Icons.Default.Description
                                },
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        trailingIcon = { Icon(Icons.Default.Close, "Remove ${attachment.name}", Modifier.size(16.dp)) }
                    )
                }
            }
        }
        agent.attachmentMessage?.let { message ->
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (agent.attachmentBusy) {
                    CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(7.dp))
                }
                Text(
                    message,
                    color = if (message.contains("could not", true) || message.contains("required", true) || message.startsWith("No "))
                        MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun AddToChatSheet(
    agent: AgentViewModel,
    hasProject: Boolean,
    onDismiss: () -> Unit,
    onCamera: () -> Unit,
    onPhotos: () -> Unit,
    onFiles: () -> Unit,
    onProject: () -> Unit,
    onToolAccess: () -> Unit,
    onConnectors: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF151614),
        contentColor = MaterialTheme.colorScheme.onSurface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.outline) }
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onDismiss) { Icon(Icons.Default.Close, "Close add to chat") }
                    Text(
                        "Add to chat",
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.size(48.dp))
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AddToChatTile("Camera", Icons.Default.CameraAlt, onCamera, Modifier.weight(1f))
                    AddToChatTile("Photos", Icons.Default.PhotoLibrary, onPhotos, Modifier.weight(1f))
                    AddToChatTile("Files", Icons.Default.UploadFile, onFiles, Modifier.weight(1f))
                }
            }
            item {
                Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(24.dp)) {
                    Column {
                        AddToChatToggle(
                            title = "Web search",
                            subtitle = "Ground prompts with live sources through the laptop bridge",
                            icon = Icons.Default.Language,
                            checked = agent.webSearchEnabled,
                            onCheckedChange = agent::updateWebSearch
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.background)
                        AddToChatToggle(
                            title = "Memory",
                            subtitle = "Remember recent context privately on this device",
                            icon = Icons.Default.History,
                            checked = agent.memoryEnabled,
                            onCheckedChange = agent::updateMemory
                        )
                    }
                }
            }
            item {
                Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(24.dp)) {
                    Column {
                        AddToChatRow(
                            title = "Add to project",
                            subtitle = if (hasProject) "Current repository attached" else "Choose or clone a repository",
                            icon = Icons.Default.Inventory2,
                            onClick = onProject
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.background)
                        AddToChatRow(
                            title = "Tool access",
                            subtitle = agent.toolAccessMode.label,
                            icon = Icons.Default.BusinessCenter,
                            onClick = onToolAccess
                        )
                    }
                }
            }
            item {
                Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(24.dp)) {
                    AddToChatRow(
                        title = "Connectors & plugins",
                        subtitle = agent.connectorStatus,
                        icon = Icons.Default.Link,
                        loading = agent.checkingConnector,
                        onClick = onConnectors
                    )
                }
            }
            item {
                Text(
                    "Connection states come from live provider checks. Unreachable services are never shown as connected.",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable private fun AddToChatTile(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) = Surface(
    onClick = onClick,
    modifier = modifier.height(142.dp),
    color = MaterialTheme.colorScheme.surface,
    shape = RoundedCornerShape(22.dp)
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(50)) {
            Icon(icon, null, Modifier.padding(15.dp).size(25.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable private fun AddToChatToggle(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) = Row(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
    verticalAlignment = Alignment.CenterVertically
) {
    SheetIcon(icon)
    Spacer(Modifier.width(14.dp))
    Column(Modifier.weight(1f)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    }
    Spacer(Modifier.width(10.dp))
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = MaterialTheme.colorScheme.primary,
            uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    )
}

@Composable private fun AddToChatRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    loading: Boolean = false,
    onClick: () -> Unit
) = Surface(onClick = onClick, color = Color.Transparent) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SheetIcon(icon)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
        if (loading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
        else Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable private fun SheetIcon(icon: androidx.compose.ui.graphics.vector.ImageVector) =
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(50)) {
        Icon(icon, null, Modifier.padding(12.dp).size(23.dp))
    }

@Composable private fun ToolAccessDialog(
    selected: ToolAccessMode,
    onSelect: (ToolAccessMode) -> Unit,
    onDismiss: () -> Unit
) = AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Laptop tool access") },
    text = {
        Column {
            Text(
                "Controls whether IQF may send the current prompt and attached context to your configured laptop bridge.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(12.dp))
            listOf(ToolAccessMode.AUTO, ToolAccessMode.ASK, ToolAccessMode.AUTOMATIC).forEach { mode ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = selected == mode, onClick = { onSelect(mode) })
                    Column {
                        Text(mode.label, fontWeight = FontWeight.Medium)
                        Text(
                            when (mode) {
                                ToolAccessMode.AUTO -> "IQF chooses the verified real model when it is available."
                                ToolAccessMode.ASK -> "Load the laptop model only when you tap Ask laptop."
                                ToolAccessMode.AUTOMATIC -> "Keep the verified model ready for every request."
                                ToolAccessMode.OFF -> "Never send prompts to the laptop bridge."
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    },
    confirmButton = { TextButton(onDismiss) { Text("Done") } }
)

@Composable private fun NavigationMenu(
    state: WorkspaceUiState,
    workspace: WorkspaceViewModel,
    agent: AgentViewModel,
    appearance: Appearance,
    onAppearanceChange: (Appearance) -> Unit,
    onDestination: (AppDestination) -> Unit,
    onChat: (String) -> Unit,
    onNewChat: () -> Unit,
    onIncognito: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(Modifier.fillMaxSize()) {
        Surface(onClick = onDismiss, color = Color.Black.copy(alpha = .42f), modifier = Modifier.fillMaxSize()) {}
        Surface(
            color = Color(0xFF111210),
            modifier = Modifier.width(350.dp).fillMaxHeight(),
            shadowElevation = 18.dp
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 22.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "IQF",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontFamily = FontFamily.Serif,
                            fontSize = 30.sp,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onIncognito) {
                            Icon(
                                Icons.Default.VisibilityOff,
                                "Start incognito chat",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
                item { NavigationItem("Chats", Icons.Default.Forum) { onDestination(AppDestination.CHATS) } }
                item { NavigationItem("Projects", Icons.Default.Inventory2) { onDestination(AppDestination.PROJECTS) } }
                item { NavigationItem("Code", Icons.Default.Code) { onDestination(AppDestination.CODE) } }
                item { NavigationItem("Artifacts", Icons.Default.Category) { onDestination(AppDestination.ARTIFACTS) } }
                item { HorizontalDivider(Modifier.padding(vertical = 12.dp)) }
                if (state.pinnedRepositoryNames.isNotEmpty()) {
                    item { NavigationSection("Pinned") }
                    items(
                        state.repositories.filter { it.name in state.pinnedRepositoryNames },
                        key = { "pin-${it.root.absolutePath}" }
                    ) { repo ->
                        NavigationItem(repo.name, Icons.Default.Folder) {
                            workspace.selectRepository(repo.name)
                            onDestination(AppDestination.CODE)
                        }
                    }
                    item { HorizontalDivider(Modifier.padding(vertical = 12.dp)) }
                }
                item { NavigationSection("Recents") }
                if (agent.chats.isEmpty()) {
                    item {
                        Text(
                            "Your saved chats will appear here.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                } else {
                    items(agent.chats.take(12), key = { it.id }) { chat ->
                        TextButton(onClick = { onChat(chat.id) }, modifier = Modifier.fillMaxWidth()) {
                            Text(chat.title, Modifier.fillMaxWidth(), maxLines = 1, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
                item { Spacer(Modifier.height(90.dp)) }
            }
            Column(
                Modifier.fillMaxSize().padding(22.dp),
                verticalArrangement = Arrangement.Bottom
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilledIconButton(
                        onClick = {
                            onAppearanceChange(
                                when (appearance) {
                                    Appearance.DARK -> Appearance.LIGHT
                                    Appearance.LIGHT -> Appearance.SYSTEM
                                    Appearance.SYSTEM -> Appearance.DARK
                                }
                            )
                        },
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = IqfCoral)
                    ) { Icon(Icons.Default.Tune, "Cycle appearance") }
                    Spacer(Modifier.weight(1f))
                    Button(
                        onClick = onNewChat,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onSurface, contentColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Icon(Icons.Default.Add, null)
                        Spacer(Modifier.width(8.dp))
                        Text("New chat")
                    }
                }
            }
        }
    }
}

@Composable private fun NavigationItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) = TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
    Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.width(14.dp))
    Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface, textAlign = androidx.compose.ui.text.style.TextAlign.Start)
}

@Composable private fun NavigationSection(label: String) {
    Text(
        label,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
    )
}

@Composable private fun ProjectsPage(
    state: WorkspaceUiState,
    workspace: WorkspaceViewModel,
    onOpen: (String) -> Unit,
    onNew: () -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    val repositories = state.repositories
        .filter { it.name.contains(query, ignoreCase = true) }
        .sortedWith(compareByDescending<com.iqforge.git.Repo> { it.name in state.pinnedRepositoryNames }.thenBy { it.name.lowercase() })
    Column(Modifier.fillMaxSize().padding(horizontal = 22.dp)) {
        Text("Projects", fontFamily = FontFamily.Serif, fontSize = 36.sp, modifier = Modifier.padding(top = 14.dp, bottom = 20.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search projects") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            singleLine = true,
            shape = RoundedCornerShape(18.dp)
        )
        if (repositories.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    if (query.isBlank()) "No repositories are cloned on this device yet." else "No project matches '$query'.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(Modifier.weight(1f).padding(top = 14.dp)) {
                items(repositories, key = { it.root.absolutePath }) { repo ->
                    Surface(onClick = { onOpen(repo.name) }, color = Color.Transparent) {
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 13.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(repo.name, style = MaterialTheme.typography.titleLarge)
                                Text(
                                    "Edited ${formatProjectDate(repo.root.resolve(".git/index").lastModified())}",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { workspace.togglePinned(repo.name) }) {
                                Icon(
                                    if (repo.name in state.pinnedRepositoryNames) Icons.Default.PushPin else Icons.Default.PushPin,
                                    contentDescription = if (repo.name in state.pinnedRepositoryNames) "Unpin ${repo.name}" else "Pin ${repo.name}",
                                    tint = if (repo.name in state.pinnedRepositoryNames) IqfCoral else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
        Button(
            onClick = onNew,
            modifier = Modifier.align(Alignment.End).padding(bottom = 22.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onSurface, contentColor = MaterialTheme.colorScheme.surface)
        ) {
            Icon(Icons.Default.Add, null)
            Spacer(Modifier.width(8.dp))
            Text("New project")
        }
    }
}

@Composable private fun CodeWorkspacePage(state: WorkspaceUiState, workspace: WorkspaceViewModel) {
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 10.dp)) {
        Text("Code", fontFamily = FontFamily.Serif, fontSize = 34.sp, modifier = Modifier.padding(bottom = 14.dp))
        if (state.repo == null) ClonePanel(state, workspace) else FilePanel(state, workspace)
    }
}

@Composable private fun ArtifactsPage(state: WorkspaceUiState, workspace: WorkspaceViewModel) {
    var query by rememberSaveable { mutableStateOf("") }
    val artifacts = state.artifacts.filter {
        it.name.contains(query, ignoreCase = true) || it.relativePath.contains(query, ignoreCase = true)
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 22.dp)) {
        Text("Artifacts", fontFamily = FontFamily.Serif, fontSize = 36.sp, modifier = Modifier.padding(top = 14.dp, bottom = 20.dp))
        OutlinedTextField(
            query, { query = it }, Modifier.fillMaxWidth(),
            placeholder = { Text("Search code artifacts") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            singleLine = true,
            shape = RoundedCornerShape(18.dp)
        )
        if (state.repo == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Open a project to browse its real artifacts.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(top = 12.dp)) {
                items(artifacts, key = { it.relativePath }) { artifact ->
                    Surface(onClick = { workspace.openArtifact(artifact) }, color = Color.Transparent) {
                        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Description, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(artifact.name, style = MaterialTheme.typography.titleMedium)
                                Text(artifact.relativePath, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatProjectDate(timestamp: Long): String = if (timestamp <= 0L) "unknown" else
    SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(timestamp))

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun ConnectorsSheet(agent: AgentViewModel, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp)) {
            SheetTitle("Connectors", onDismiss) {
                IconButton(agent::refreshServices, enabled = !agent.checkingConnector) {
                    Icon(Icons.Default.Refresh, "Refresh live connectors")
                }
            }
            if (agent.checkingConnector && agent.connectors.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (agent.connectors.isEmpty()) {
                Text(
                    agent.connectorStatus,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(24.dp)
                )
            } else {
                Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(22.dp)) {
                    Column {
                        agent.connectors.forEachIndexed { index, connector ->
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 17.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                SheetIcon(
                                    when (connector.id) {
                                        "github" -> Icons.Default.Code
                                        "ollama" -> Icons.Default.Memory
                                        else -> Icons.Default.Language
                                    }
                                )
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(connector.name, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        connector.detail,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                Icon(
                                    if (connector.connected) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                                    contentDescription = connector.status,
                                    tint = if (connector.connected) Color(0xFF4CAF70) else MaterialTheme.colorScheme.error
                                )
                            }
                            if (index != agent.connectors.lastIndex) HorizontalDivider()
                        }
                    }
                }
            }
            Text(
                "These states are live checks against Ollama, GitHub's API, and the web-search provider. Authentication secrets are never bundled in the APK.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(12.dp)
            )
            Spacer(Modifier.height(20.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun ProjectSelectorSheet(
    state: WorkspaceUiState,
    onSelect: (String) -> Unit,
    onClone: () -> Unit,
    onDismiss: () -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    val projects = state.repositories.filter { it.name.contains(query, ignoreCase = true) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp)) {
            SheetTitle("Add to project", onDismiss) {
                IconButton(onClone) { Icon(Icons.Default.Add, "Clone another repository") }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search projects") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
                shape = RoundedCornerShape(18.dp)
            )
            Spacer(Modifier.height(14.dp))
            if (projects.isEmpty()) {
                Text("No cloned project matches your search.", Modifier.padding(20.dp))
            } else {
                Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(22.dp)) {
                    Column {
                        projects.forEachIndexed { index, repo ->
                            Surface(onClick = { onSelect(repo.name) }, color = Color.Transparent) {
                                Row(
                                    Modifier.fillMaxWidth().padding(18.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Inventory2, null)
                                    Spacer(Modifier.width(14.dp))
                                    Text(repo.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                                    if (repo.root == state.repo?.root) Icon(Icons.Default.Check, "Selected")
                                }
                            }
                            if (index != projects.lastIndex) HorizontalDivider()
                        }
                    }
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun ModelSelectorSheet(
    agent: AgentViewModel,
    onEffort: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp)) {
            SheetTitle("Select model", onDismiss)
            if (agent.availableModels.isEmpty()) {
                Text(
                    "No verified model is available. Start Ollama and install a model, then refresh Connectors.",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(20.dp)
                )
            } else {
                Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(22.dp)) {
                    Column {
                        agent.availableModels.forEachIndexed { index, model ->
                            Surface(onClick = { agent.selectModel(model) }, color = Color.Transparent) {
                                Row(
                                    Modifier.fillMaxWidth().padding(18.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            model.id,
                                            color = if (model.id == agent.selectedModel) Color(0xFF75AFFF) else MaterialTheme.colorScheme.onSurface,
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        Text(
                                            listOfNotNull(model.parameterSize, model.quantization).joinToString(" - "),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                    if (model.id == agent.selectedModel) Icon(Icons.Default.Check, "Selected", tint = Color(0xFF75AFFF))
                                }
                            }
                            if (index != agent.availableModels.lastIndex) HorizontalDivider()
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Surface(onClick = onEffort, color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(22.dp)) {
                Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    SheetIcon(Icons.Default.Timer)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Effort", style = MaterialTheme.typography.titleMedium)
                        Text(agent.effort.label, color = Color(0xFF75AFFF))
                    }
                    Icon(Icons.Default.ChevronRight, null)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun EffortSheet(agent: AgentViewModel, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp)) {
            SheetTitle("Effort", onDismiss)
            Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(22.dp)) {
                Column {
                    EffortLevel.entries.forEachIndexed { index, level ->
                        Surface(
                            onClick = { agent.updateEffort(level) },
                            color = Color.Transparent
                        ) {
                            Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        level.label,
                                        color = if (level == agent.effort) Color(0xFF75AFFF) else MaterialTheme.colorScheme.onSurface,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    if (level == EffortLevel.MEDIUM) Text("Default", style = MaterialTheme.typography.labelSmall)
                                }
                                if (level == agent.effort) Icon(Icons.Default.Check, "Selected", tint = Color(0xFF75AFFF))
                            }
                        }
                        if (index != EffortLevel.entries.lastIndex) HorizontalDivider()
                    }
                }
            }
            Text(
                "Effort changes the depth and response budget sent to the real model.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(12.dp)
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable private fun SheetTitle(
    title: String,
    onBack: () -> Unit,
    action: @Composable () -> Unit = { Spacer(Modifier.size(48.dp)) }
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        Text(
            title,
            Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )
        action()
    }
    Spacer(Modifier.height(14.dp))
}

@Composable private fun RepositoryDrawer(
    state: WorkspaceUiState,
    workspace: WorkspaceViewModel,
    agent: AgentViewModel,
    appearance: Appearance,
    onAppearanceChange: (Appearance) -> Unit,
    close: () -> Unit
) = Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.width(330.dp).fillMaxSize()) {
    Column(Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Repository", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            IconButton(close) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Close drawer") }
        }
        if (state.repo == null) ClonePanel(state, workspace) else FilePanel(state, workspace)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            agent.bridgeUrl, agent::updateBridgeUrl, Modifier.fillMaxWidth(),
            label = { Text("Laptop bridge URL") }, singleLine = true
        )
        Text(
            "Use http://<laptop-ip>:8000. Calls handle loading, timeout, retry, and offline states when the bridge is available.",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(top = 6.dp),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = .65f)
        )
        Text("Appearance", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Appearance.entries.forEach { choice ->
                TextButton(onClick = { onAppearanceChange(choice) }, modifier = Modifier.weight(1f)) {
                    Text(if (appearance == choice) "v ${choice.name.lowercase().replaceFirstChar { it.uppercase() }}" else choice.name.lowercase().replaceFirstChar { it.uppercase() })
                }
            }
        }
    }
}

@Composable private fun ClonePanel(state: WorkspaceUiState, workspace: WorkspaceViewModel) =
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(state.repoUrl, workspace::updateRepoUrl, Modifier.fillMaxWidth(), label = { Text("Repository URL") }, singleLine = true)
        OutlinedTextField(state.githubUsername, workspace::updateUsername, Modifier.fillMaxWidth(), label = { Text("GitHub username (optional)") }, singleLine = true)
        OutlinedTextField(state.githubToken, workspace::updateToken, Modifier.fillMaxWidth(), label = { Text("Token (private repos)") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
        Button(workspace::cloneRepository, enabled = state.repoUrl.isNotBlank() && !state.busy, modifier = Modifier.fillMaxWidth()) {
            Text(if (state.busy) state.operation else "Clone to phone")
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall) }
    }

@Composable private fun FilePanel(state: WorkspaceUiState, workspace: WorkspaceViewModel) =
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(state.repo?.name ?: "Files", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            IconButton(workspace::pull, enabled = !state.busy) { Icon(Icons.Default.Refresh, "Pull") }
        }
        LazyColumn(Modifier.weight(1f, fill = false)) {
            items(state.entries, key = { it.relativePath }) { entry -> FileRow(entry, workspace) }
        }
        state.message?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall) }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall) }
        OutlinedTextField(state.commitMessage, workspace::updateCommitMessage, Modifier.fillMaxWidth(), label = { Text("Commit message") }, singleLine = true)
        Row {
            Button(workspace::commit, Modifier.weight(1f), enabled = !state.busy) { Text("Commit") }
            Spacer(Modifier.width(8.dp))
            Button(workspace::push, Modifier.weight(1f), enabled = !state.busy) { Text("Push") }
        }
    }

@Composable private fun FileRow(entry: WorkspaceEntry, workspace: WorkspaceViewModel) =
    TextButton(onClick = { workspace.openEntry(entry) }, modifier = Modifier.fillMaxWidth()) {
        Spacer(Modifier.width((entry.depth * 12).dp))
        Icon(if (entry.directory) if (entry.expanded) Icons.Default.FolderOpen else Icons.Default.Folder else Icons.Default.Description, null)
        Spacer(Modifier.width(8.dp))
        Text(entry.name)
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun EditorScreen(state: WorkspaceUiState, workspace: WorkspaceViewModel) =
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(state.selectedFile?.relativePath ?: "Editor") },
            navigationIcon = { IconButton(workspace::closeEditor) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to agent") } }
        )
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            OutlinedTextField(
                state.editorText, workspace::updateEditor,
                Modifier.weight(1f).fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                label = { Text(if (state.editorDirty) "Edited" else "Editor") }
            )
            Button(workspace::saveFile, enabled = state.editorDirty, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text("Save file")
            }
        }
    }
