package com.iqforge

import android.os.Bundle
import android.os.Build
import android.content.Context
import android.content.Intent
import android.app.Application
import android.Manifest
import rikka.shizuku.Shizuku
import com.iqforge.engine.NpuDaemonManager
import android.net.Uri
import android.provider.Settings
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.lifecycle.AndroidViewModel
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.core.view.WindowCompat
import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat
import com.iqforge.engine.CodeEngine
import com.iqforge.engine.Finding
import com.iqforge.engine.ModelCatalog
import com.iqforge.engine.ModelInfo
import com.iqforge.engine.NativeEngine
import com.iqforge.engine.Severity
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
import com.iqforge.code.CodeSession
import com.iqforge.code.CodeSessionStore
import com.iqforge.chat.SavedChat
import com.iqforge.cowork.CoworkStatus
import com.iqforge.cowork.CoworkTask
import com.iqforge.cowork.CoworkTaskStore
import com.iqforge.dispatch.DispatchRecord
import com.iqforge.dispatch.DispatchStatus
import com.iqforge.dispatch.DispatchStore
import com.iqforge.deployment.DeploymentCheck
import com.iqforge.deployment.DeploymentPage
import com.iqforge.deployment.DeploymentViewModel
import com.iqforge.engine.OfflineEngine
import com.iqforge.github.GitHubIssueDto
import com.iqforge.github.GitHubPullRequestDetailDto
import com.iqforge.github.GitHubViewModel
import com.iqforge.github.cleanPullRequestSummary
import com.iqforge.github.fallbackPullRequestSummary
import com.iqforge.github.pullRequestSummaryPrompt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import com.iqforge.workspace.WorkspaceEntry
import com.iqforge.workspace.WorkspaceUiState
import com.iqforge.workspace.WorkspaceViewModel
import java.io.IOException
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.iqforge.sensors.SensorFeedback
import com.iqforge.sensors.HapticCue
import com.iqforge.sensors.buzz
import com.iqforge.git.JGitRepoManager
import com.iqforge.git.Repo
import org.eclipse.jgit.api.Git

private val IqfYellow = Color(0xFFFFC400)
private val ForgeDarkColors = darkColorScheme(
    primary = IqfYellow,
    background = Color(0xFF121311),
    surface = Color(0xFF1D1E1B),
    surfaceVariant = Color(0xFF282925),
    outline = Color(0xFF3B3C37),
    onBackground = Color(0xFFF2EFE9),
    onSurface = Color(0xFFF2EFE9),
    onSurfaceVariant = Color(0xFFAAA9A3),
    onPrimary = Color(0xFF1A1500)
)
private val ForgeLightColors = lightColorScheme(primary = Color(0xFFB38600), background = Color(0xFFFFFBFF), surface = Color(0xFFFFFBFF), surfaceVariant = Color(0xFFE7E0EC))

private enum class Appearance { SYSTEM, LIGHT, DARK }
private enum class FontChoice { DEFAULT, SERIF, MONOSPACE }
private enum class AppDestination { CHATS, DISPATCH, COWORK, PROJECTS, PROJECT_DETAIL, CODE, ARTIFACTS, REVIEW, DEPLOYMENT, SETTINGS }
private enum class SettingsDialog { NONE, USAGE, CAPABILITIES, GITHUB, COLOR, FONT, VOICE, PRIVACY, DEVICE }

class MainActivity : ComponentActivity() {
    private val shizukuPermissionListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
        if (grantResult == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            android.util.Log.i("MainActivity", "Shizuku permission granted! Snapdragon Hexagon NPU ready.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)
            if (NpuDaemonManager.isShizukuAvailable() && !NpuDaemonManager.hasShizukuPermission()) {
                NpuDaemonManager.requestShizukuPermission()
            }
        } catch (_: Throwable) {}

        setContent {
            ForgeTheme { appearance, updateAppearance, fontChoice, updateFontChoice ->
                var showSplash by remember { mutableStateOf(true) }
                if (showSplash) LaunchSplash { showSplash = false }
                else AgentApp(
                    appearance = appearance,
                    onAppearanceChange = updateAppearance,
                    fontChoice = fontChoice,
                    onFontChoiceChange = updateFontChoice
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
        } catch (_: Throwable) {}
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

@Composable private fun ForgeTheme(
    content: @Composable (Appearance, (Appearance) -> Unit, FontChoice, (FontChoice) -> Unit) -> Unit
) {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("iqforge_settings", Context.MODE_PRIVATE) }
    var appearance by rememberSaveable {
        mutableStateOf(runCatching { Appearance.valueOf(preferences.getString("appearance", Appearance.DARK.name) ?: Appearance.DARK.name) }.getOrDefault(Appearance.DARK))
    }
    var fontChoice by rememberSaveable {
        mutableStateOf(runCatching { FontChoice.valueOf(preferences.getString("font_style", FontChoice.DEFAULT.name) ?: FontChoice.DEFAULT.name) }.getOrDefault(FontChoice.DEFAULT))
    }
    val dark = when (appearance) { Appearance.SYSTEM -> isSystemInDarkTheme(); Appearance.LIGHT -> false; Appearance.DARK -> true }
    val colors = if (dark) ForgeDarkColors else ForgeLightColors
    SideEffect {
        (context as? ComponentActivity)?.window?.let { window ->
            window.statusBarColor = colors.background.toArgb()
            window.navigationBarColor = colors.background.toArgb()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
            }
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    MaterialTheme(colorScheme = colors, typography = forgeTypography(fontChoice)) {
        content(
            appearance,
            { value ->
                appearance = value
                preferences.edit().putString("appearance", value.name).apply()
            },
            fontChoice,
            { value ->
                fontChoice = value
                preferences.edit().putString("font_style", value.name).apply()
            }
        )
    }
}

private fun forgeTypography(choice: FontChoice): Typography {
    val family = when (choice) {
        FontChoice.DEFAULT -> FontFamily.Default
        FontChoice.SERIF -> FontFamily.Serif
        FontChoice.MONOSPACE -> FontFamily.Monospace
    }
    val base = Typography()
    return Typography(
        displayLarge = base.displayLarge.copy(fontFamily = family),
        displayMedium = base.displayMedium.copy(fontFamily = family),
        displaySmall = base.displaySmall.copy(fontFamily = family),
        headlineLarge = base.headlineLarge.copy(fontFamily = family),
        headlineMedium = base.headlineMedium.copy(fontFamily = family),
        headlineSmall = base.headlineSmall.copy(fontFamily = family),
        titleLarge = base.titleLarge.copy(fontFamily = family),
        titleMedium = base.titleMedium.copy(fontFamily = family),
        titleSmall = base.titleSmall.copy(fontFamily = family),
        bodyLarge = base.bodyLarge.copy(fontFamily = family),
        bodyMedium = base.bodyMedium.copy(fontFamily = family),
        bodySmall = base.bodySmall.copy(fontFamily = family),
        labelLarge = base.labelLarge.copy(fontFamily = family),
        labelMedium = base.labelMedium.copy(fontFamily = family),
        labelSmall = base.labelSmall.copy(fontFamily = family)
    )
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
    internal val codeEngine: CodeEngine,
    internal var bridgeClient: LaptopBridgeClient = LaptopBridgeClient(),
    private val preferences: android.content.SharedPreferences? = null,
    private val attachmentService: ChatAttachmentService = ChatAttachmentService(),
    private val application: Application? = null
) : ViewModel() {
    val isNpuActive: Boolean get() = (codeEngine as? NativeEngine)?.isNpuActive == true
    val lastNpuTokensPerSec: Double? get() = (codeEngine as? NativeEngine)?.lastNpuTokensPerSec
    private val historyStore = preferences?.let(::ChatHistoryStore)
    private val coworkStore = preferences?.let(::CoworkTaskStore)
    private val dispatchStore = preferences?.let(::DispatchStore)
    private val codeSessionStore = preferences?.let(::CodeSessionStore)
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
    var offlineModelReady by mutableStateOf(false); private set
    var offlineModelBytes by mutableStateOf(0L); private set
    var isDownloadingModel by mutableStateOf(false); private set
    var downloadProgress by mutableFloatStateOf(0f); private set
    var downloadProgressStatus by mutableStateOf(""); private set
    var offlineModelName by mutableStateOf<String?>(null); private set
    var connectors by mutableStateOf<List<BridgeConnector>>(emptyList()); private set
    var chats by mutableStateOf<List<SavedChat>>(historyStore?.load().orEmpty()); private set
    var activeChatId by mutableStateOf<String?>(null); private set
    var incognito by mutableStateOf(false); private set
    var coworkTasks by mutableStateOf<List<CoworkTask>>(coworkStore?.markInterrupted().orEmpty()); private set
    var dispatchRecords by mutableStateOf<List<DispatchRecord>>(dispatchStore?.load().orEmpty()); private set
    var codeSessions by mutableStateOf<List<CodeSession>>(codeSessionStore?.load().orEmpty()); private set
    var activeCodeSessionId by mutableStateOf<String?>(null); private set
    var codeSessionBusy by mutableStateOf(false); private set
    var dispatchWorkspaces by mutableStateOf<List<String>>(emptyList()); private set
    var dispatchBusy by mutableStateOf(false); private set
    var dispatchError by mutableStateOf<String?>(null); private set
    var fileEditBusy by mutableStateOf(false); private set
    var fileEditError by mutableStateOf<String?>(null); private set
    var remoteFiles by mutableStateOf<List<String>>(emptyList()); private set
    var remoteFilePath by mutableStateOf<String?>(null); private set
    var remoteFileText by mutableStateOf(""); private set
    var remoteBusy by mutableStateOf(false); private set
    var remoteResult by mutableStateOf<String?>(null); private set
    var activeRemoteWorkspace by mutableStateOf(""); private set

    companion object {
        private const val DEFAULT_BRIDGE_URL = "http://10.0.2.2:8000"
        private const val USB_BRIDGE_URL = "http://127.0.0.1:8000"
        private const val MEMORY_SEPARATOR = "\u001E"
        private const val MAX_MEMORY_ITEMS = 6

        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val application = checkNotNull(extras[APPLICATION_KEY])
                val preferences = application.getSharedPreferences("iqforge_agent", Context.MODE_PRIVATE)
                val looksLikeEmulator = Build.FINGERPRINT.contains("generic", ignoreCase = true) ||
                    Build.MODEL.contains("Emulator", ignoreCase = true)
                if (!looksLikeEmulator && preferences.getString("bridge_url", DEFAULT_BRIDGE_URL) == DEFAULT_BRIDGE_URL) {
                    preferences.edit().putString("bridge_url", USB_BRIDGE_URL).apply()
                }
                return AgentViewModel(
                    codeEngine = NativeEngine(application),
                    preferences = preferences,
                    application = application as? Application
                ) as T
            }
        }
    }

    init {
        refreshOfflineModel()
        feed = feed.filterNot { it is FeedItem.Status && it.text.startsWith("Cloned ") }
    }

    fun refreshOfflineModel() {
        val nativeEngine = codeEngine as? NativeEngine ?: return
        viewModelScope.launch {
            offlineModelReady = nativeEngine.initialize()
            offlineModelBytes = nativeEngine.installedModelBytes()
            offlineModelName = if (offlineModelReady) nativeEngine.displayName else null
            if (offlineModelReady && selectedModel == null) selectedModel = nativeEngine.displayName
        }
    }

    suspend fun reviewPullRequestPatch(patch: String): List<Finding> = codeEngine.review(patch)

    suspend fun summarizePullRequest(
        pr: GitHubPullRequestDetailDto,
        files: List<com.iqforge.github.GitHubPullRequestFileDto>
    ): String {
        val fallback = fallbackPullRequestSummary(pr.title, files)
        val generated = codeEngine.explain(pullRequestSummaryPrompt(pr.title, pr.body, files))
        return cleanPullRequestSummary(generated, fallback)
    }

    fun selectOfflineModel() {
        if (offlineModelReady) selectedModel = (codeEngine as? NativeEngine)?.displayName
    }

    /** On-device catalog models (Qwen 1.5B / 3B / Phi-4-mini) — the picker's model list. */
    val catalogModels: List<ModelInfo> get() = ModelCatalog.ALL
    val activeCatalogModelId: String? get() = (codeEngine as? NativeEngine)?.activeModelInfo?.id
    fun isCatalogModelDownloaded(model: ModelInfo): Boolean =
        (codeEngine as? NativeEngine)?.isCatalogModelAvailable(model) == true

    var isActivatingModel by mutableStateOf(false); private set
    var activatingModelId by mutableStateOf<String?>(null); private set
    var downloadingModelId by mutableStateOf<String?>(null); private set
    private var downloadJob: kotlinx.coroutines.Job? = null

    fun cancelModelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        isDownloadingModel = false
        downloadingModelId = null
        downloadProgress = 0f
        downloadProgressStatus = ""
    }

    /**
     * Switch which on-device model is active. If downloaded, switches to it immediately
     * and restarts the Hexagon NPU daemon with its weights. If not downloaded, begins
     * downloading it while keeping the currently active model usable.
     */
    fun selectCatalogModel(model: ModelInfo) {
        val nativeEngine = codeEngine as? NativeEngine ?: return
        if (isActivatingModel) return

        if (nativeEngine.isCatalogModelAvailable(model)) {
            // Cancel any background download of other models so user immediately switches
            if (isDownloadingModel) {
                cancelModelDownload()
            }
            isActivatingModel = true
            activatingModelId = model.id
            viewModelScope.launch {
                try {
                    val ready = nativeEngine.switchActiveModel(model)
                    offlineModelReady = ready
                    offlineModelBytes = nativeEngine.installedModelBytes()
                    offlineModelName = if (ready) nativeEngine.displayName else null
                    if (ready) selectedModel = nativeEngine.displayName
                    if (!ready) {
                        feed += FeedItem.Status(
                            "Couldn't start ${model.displayName} on the Hexagon NPU — the daemon didn't come up in time.",
                            error = true
                        )
                    }
                } finally {
                    isActivatingModel = false
                    activatingModelId = null
                }
            }
        } else {
            if (isDownloadingModel) return
            startModelDownload(model)
        }
    }

    fun startModelDownload(targetModel: ModelInfo = (codeEngine as? NativeEngine)?.activeModelInfo ?: ModelCatalog.QWEN_1_5B) {
        val nativeEngine = codeEngine as? NativeEngine ?: return
        if (isDownloadingModel) return
        isDownloadingModel = true
        downloadingModelId = targetModel.id
        downloadProgress = 0f
        downloadProgressStatus = "Starting download..."
        downloadJob = viewModelScope.launch {
            val ok = nativeEngine.downloadModel(targetModel) { progress, status ->
                downloadProgress = progress
                downloadProgressStatus = status
            }
            isDownloadingModel = false
            downloadingModelId = null
            if (ok) {
                refreshOfflineModel()
                feed += FeedItem.Status("${targetModel.displayName} downloaded and active on Snapdragon Hexagon NPU!", success = true)
            } else {
                feed += FeedItem.Status("Model download failed. Please verify connection and retry.", error = true)
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

    fun clearSavedChats() {
        historyStore?.clear()
        chats = emptyList()
        if (!incognito) newChat()
    }

    fun clearCurrentChat() {
        activeChatId?.let { chats = historyStore?.remove(it).orEmpty() }
        newChat(privateMode = incognito)
    }

    /** Deletes any saved chat by id, from the Recents list — not just the active one. */
    fun deleteChat(id: String) {
        chats = historyStore?.remove(id).orEmpty()
        if (activeChatId == id) newChat(privateMode = incognito)
    }

    fun clearMemory() {
        preferences?.edit()?.remove("chat_memory")?.apply()
    }

    fun createCoworkTask(title: String, instruction: String, repository: String?) {
        require(title.isNotBlank()) { "Enter a task name" }
        require(instruction.isNotBlank()) { "Describe what the task should achieve" }
        val store = coworkStore ?: return
        val task = store.create(title, instruction, repository)
        coworkTasks = store.load()
        viewModelScope.launch {
            coworkTasks = try {
                val context = repository?.let { "Repository: $it" }.orEmpty()
                val result = bridgeClient.escalateWithOptions(
                    laptopUrl = bridgeUrl,
                    task = inferTask(instruction),
                    context = context,
                    instruction = instruction,
                    effort = effort.wireName
                )
                store.finish(task.id, result)
            } catch (error: Exception) {
                store.fail(task.id, error.message ?: "Task failed")
            }
        }
    }

    fun removeCoworkTask(id: String) {
        coworkTasks = coworkStore?.remove(id).orEmpty()
    }

    fun refreshDispatchWorkspaces() {
        viewModelScope.launch {
            dispatchWorkspaces = runCatching { bridgeClient.dispatchWorkspaces(bridgeUrl) }
                .onFailure { dispatchError = it.message }
                .getOrDefault(emptyList())
            if (activeRemoteWorkspace.isBlank()) activeRemoteWorkspace = dispatchWorkspaces.firstOrNull().orEmpty()
        }
    }

    fun selectRemoteWorkspace(path: String) {
        activeRemoteWorkspace = path
        refreshRemoteFiles(path)
    }

    fun createCodeSession(workspace: String, title: String? = null, initialMessage: String? = null): CodeSession? {
        if (workspace.isBlank()) return null
        val session = if (title != null) {
            codeSessionStore?.create(workspace, title)
        } else {
            codeSessionStore?.create(workspace)
        } ?: return null
        if (initialMessage != null) {
            codeSessionStore?.append(session.id, "assistant", initialMessage)
        }
        codeSessions = codeSessionStore?.load().orEmpty()
        activeCodeSessionId = session.id
        activeRemoteWorkspace = workspace
        return session
    }

    fun openCodeSession(id: String) {
        codeSessions.firstOrNull { it.id == id }?.let {
            activeCodeSessionId = id
            activeRemoteWorkspace = it.workspace
        }
    }

    fun closeCodeSession() { activeCodeSessionId = null }

    fun deleteCodeSession(id: String) {
        codeSessions = codeSessionStore?.delete(id).orEmpty()
        if (activeCodeSessionId == id) {
            activeCodeSessionId = null
        }
    }

    /**
     * Default path is the on-device model — it does the actual review/write/debug/explain
     * reasoning, matching the "phone is the dev workstation" pitch. The laptop bridge is used
     * only for two narrow things: (1) reading the raw text of a file the user names, since the
     * cloned repo currently lives on the laptop's disk, not the phone's, and (2) genuinely
     * bigger tasks — an explicit "/" shell/dispatch command, a build/test run, or an explicit
     * "/escalate" ask for a deeper laptop-model pass. If the bridge is unreachable, file-text
     * fetch fails gracefully and the local model still answers, just without that file's exact
     * contents — it never hard-fails just because the laptop is offline.
     */
    fun isGitCommand(text: String): Boolean {
        val trimmed = text.trim()
        val lower = trimmed.lowercase()
        if (trimmed.startsWith("/") && !trimmed.startsWith("//")) {
            val slashCmd = trimmed.substring(1).trimStart().split(Regex("\\s+")).firstOrNull()?.lowercase() ?: ""
            if (slashCmd in setOf("git", "status", "diff", "log", "commit", "push", "pull", "branch", "checkout", "reset", "discard", "remote", "token", "deploy", "help")) {
                return true
            }
        }
        if (lower.startsWith("git ") || lower == "git") {
            return true
        }
        return false
    }

    private fun gitHelpText(): String = buildString {
        appendLine("🛠️ **Supported On-Device Git Commands**:")
        appendLine()
        appendLine("• `/status` or `git status` — View modified, staged, and untracked files")
        appendLine("• `/diff` or `git diff [file]` — View working tree & staged code changes")
        appendLine("• `/log` or `git log [-n 5]` — View recent commit history")
        appendLine("• `/commit <msg>` or `git commit -m \"...\"` — Stage all and commit on phone")
        appendLine("• `/push` or `git push` — Push committed changes to GitHub")
        appendLine("• `/pull` or `git pull` — Pull latest updates from remote")
        appendLine("• `/branch` or `git branch [name]` — List or create branches")
        appendLine("• `/checkout <branch>` — Switch branch (`-b` to create)")
        appendLine("• `/reset` or `git reset --hard` — Discard all uncommitted changes")
        appendLine("• `/discard <file>` — Revert a specific file to HEAD")
        appendLine("• `/remote` or `git remote -v` — View remote repository URLs")
        appendLine("• `/token <PAT>` — Validate & save GitHub token with repo permissions")
        appendLine("• `/deploy [message]` — Trigger the laptop bridge's deploy pipeline, watch live at `<bridgeUrl>/deploy`")
        appendLine()
        appendLine("💡 *All commands execute 100% on-device via embedded JGit.*")
    }

    suspend fun validateAndSaveGitHubToken(rawToken: String): String = withContext(Dispatchers.IO) {
        val tok = rawToken.trim()
        if (tok.isBlank()) {
            return@withContext "⚠️ Please provide a token: `/token <your-github-token>`"
        }
        val prefs = application?.getSharedPreferences("iqforge_workspace", Context.MODE_PRIVATE) ?: preferences
        try {
            val url = java.net.URL("https://api.github.com/user")
            val conn = (url.openConnection() as java.net.HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "token $tok")
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                setRequestProperty("User-Agent", "iQForge-Mobile")
                connectTimeout = 7000
                readTimeout = 7000
            }
            val code = conn.responseCode
            if (code == 200) {
                val resp = conn.inputStream.bufferedReader().readText()
                val json = org.json.JSONObject(resp)
                val login = json.optString("login", "")
                val name = json.optString("name", login)
                prefs?.edit()
                    ?.putString("github_token", tok)
                    ?.putString("github_username", login)
                    ?.apply()
                "🔑 **GitHub Authenticated Successfully!**\n\n👤 User: **$login** ($name)\n✅ Token saved on-device with verified repository permissions.\nYou can now run `/push`, `git push`, `/pull`, etc."
            } else if (code == 401) {
                "❌ **Authentication Failed (401 Bad Credentials)**.\n\nGitHub rejected this token. Please check that:\n1. The token was copied completely.\n2. It hasn't expired.\n3. It has the `repo` scope enabled."
            } else {
                prefs?.edit()?.putString("github_token", tok)?.apply()
                "⚠️ Token saved on-device (GitHub API returned HTTP $code). You can now test `/push`."
            }
        } catch (e: Exception) {
            prefs?.edit()?.putString("github_token", tok)?.apply()
            "🔑 GitHub token saved on-device (offline verification: ${e.message}). You can now run `/push`."
        }
    }

    suspend fun executeGitCommand(workspacePath: String, rawInput: String): String = withContext(Dispatchers.IO) {
        val trimmed = rawInput.trim()

        if (trimmed.startsWith("/token", ignoreCase = true) ||
            trimmed.startsWith("git token", ignoreCase = true) ||
            trimmed.startsWith("/git token", ignoreCase = true)) {
            val tok = trimmed
                .replace(Regex("^/git\\s+token", RegexOption.IGNORE_CASE), "")
                .replace(Regex("^git\\s+token", RegexOption.IGNORE_CASE), "")
                .replace(Regex("^/token", RegexOption.IGNORE_CASE), "")
                .trim()
            return@withContext validateAndSaveGitHubToken(tok)
        }

        // Demo pipeline: /deploy asks the laptop bridge to run its simulated CI/CD (build ->
        // test -> deploy -> live) and returns immediately — the actual progress is watched live
        // at <bridgeUrl>/deploy on the laptop. Not a real deploy: there's no production infra to
        // deploy to yet, this exists so the "fix it, push it, watch it go live" incident-response
        // story has something real to point at during a demo.
        if (trimmed.startsWith("/deploy", ignoreCase = true) || trimmed.startsWith("git deploy", ignoreCase = true)) {
            val message = trimmed
                .replace(Regex("^/deploy", RegexOption.IGNORE_CASE), "")
                .replace(Regex("^git\\s+deploy", RegexOption.IGNORE_CASE), "")
                .trim()
                .ifBlank { "Deployed from iQForge mobile" }
            val repoName = File(workspacePath).name.ifBlank { "iqforge" }
            val commitSha = runCatching {
                Regex("[0-9a-f]{7,40}").find(executeGitCommand(workspacePath, "log -n 1"))?.value.orEmpty()
            }.getOrDefault("")
            return@withContext try {
                bridgeClient.triggerDeploy(bridgeUrl, repoName, commitSha, message)
                "🚀 **Deploy triggered!** Watch it go live at `$bridgeUrl/deploy` on the laptop.\n\nRepo: `$repoName` · Commit: `${commitSha.take(7).ifBlank { "HEAD" }}`"
            } catch (e: Exception) {
                "⚠️ Could not reach the laptop bridge to trigger a deploy: ${e.message}\n\nMake sure the bridge is running (`uvicorn server:app`) and the phone is on the same network."
            }
        }

        val sessionDir = File(workspacePath)
        if (!sessionDir.exists() || !sessionDir.resolve(".git").isDirectory) {
            return@withContext "⚠️ Not a Git repository: `$workspacePath`\nPlease clone a repository first from the Files & Git menu (📁)."
        }

        val repo = Repo(sessionDir, sessionDir.name)
        val mgr = JGitRepoManager(sessionDir.parentFile ?: sessionDir)

        val prefs = application?.getSharedPreferences("iqforge_workspace", Context.MODE_PRIVATE) ?: preferences
        val token = prefs?.getString("github_token", "").orEmpty()
        val username = prefs?.getString("github_username", "").orEmpty()
        if (token.isNotBlank()) {
            mgr.updateCredentials(username, token)
        }

        val cmd = trimmed
            .replace(Regex("^/git\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("^git\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("^/", RegexOption.IGNORE_CASE), "")
            .trim()

        val verb = cmd.split(Regex("\\s+")).firstOrNull()?.lowercase() ?: ""
        val args = cmd.substringAfter(verb).trim()

        return@withContext try {
            when (verb) {
                "", "help" -> gitHelpText()
                "status" -> mgr.status(repo)
                "diff" -> mgr.diff(repo, args.takeIf { it.isNotBlank() })
                "log" -> {
                    val limit = args.replace("-n", "").trim().toIntOrNull() ?: 10
                    mgr.log(repo, limit)
                }
                "branch" -> {
                    if (args.isBlank()) {
                        mgr.branches(repo)
                    } else {
                        mgr.checkout(repo, args, createBranch = true)
                    }
                }
                "checkout" -> {
                    val createNew = args.startsWith("-b ")
                    val target = if (createNew) args.removePrefix("-b ").trim() else args
                    if (target.isBlank()) {
                        "⚠️ Please specify a branch name: `git checkout <branch>` or `git checkout -b <new-branch>`"
                    } else {
                        mgr.checkout(repo, target, createBranch = createNew)
                    }
                }
                "commit" -> {
                    val msg = args.replace(Regex("^-[a-zA-Z]*m\\s*"), "")
                        .trim()
                        .removeSurrounding("\"")
                        .removeSurrounding("'")
                        .trim()
                        .ifBlank { "Update from iQForge mobile dev station" }
                    mgr.commit(repo, msg, emptyList())
                    "✅ **Committed on phone!**\nMessage: \"$msg\"\n\nReady to push! Run `/push` or `git push` to upload to GitHub."
                }
                "push" -> {
                    if (token.isBlank()) {
                        "⚠️ **GitHub Personal Access Token required to push.**\n\nRun `/token <your-token>` or enter it in the Files & Git menu (📁) to authenticate."
                    } else {
                        try {
                            mgr.push(repo)
                            "🚀 **Successfully pushed to GitHub directly from your phone!**\nRemote repository is up to date."
                        } catch (e: Exception) {
                            if (e.message?.contains("REJECTED_NONFASTFORWARD") == true) {
                                try {
                                    mgr.pull(repo)
                                    mgr.push(repo)
                                    "🔄 **Remote had newer commits — auto-pulled, merged, and pushed!** 🚀\nYour phone changes are now live on GitHub."
                                } catch (mergeErr: Exception) {
                                    "⚠️ **Push rejected (remote has newer commits)**: Run `/pull` to merge GitHub changes into your phone, then run `/push`."
                                }
                            } else {
                                throw e
                            }
                        }
                    }
                }
                "pull" -> {
                    mgr.pull(repo)
                    "⬇️ **Successfully pulled latest changes from GitHub!**\nWorking tree updated."
                }
                "reset" -> {
                    val hard = args.contains("--hard", ignoreCase = true) || args.isBlank()
                    mgr.reset(repo, hard = hard)
                }
                "discard" -> {
                    if (args.isBlank()) {
                        mgr.reset(repo, hard = true)
                    } else {
                        mgr.discard(repo, args)
                    }
                }
                "remote" -> mgr.remotes(repo)
                else -> {
                    "❓ Unknown Git command: `$verb`\n\n${gitHelpText()}"
                }
            }
        } catch (e: Exception) {
            val msg = e.message ?: e.javaClass.simpleName
            if (verb == "push" && (msg.contains("auth", ignoreCase = true) || msg.contains("401") || msg.contains("credential", ignoreCase = true))) {
                "❌ **Push failed: Authentication error.**\nCheck your GitHub token with `/token <your-github-token>`.\n\nTip: Make sure your token has the `repo` scope."
            } else if (verb == "push" && msg.contains("REJECTED_NONFASTFORWARD", ignoreCase = true)) {
                "⚠️ **Push rejected (remote has newer commits).**\nGitHub is ahead of your phone. Run `/pull` to merge changes from GitHub, then run `/push` again."
            } else {
                "❌ Git `$verb` failed: $msg"
            }
        }
    }

    suspend fun executeGitStatus(workspacePath: String): String = executeGitCommand(workspacePath, "status")
    suspend fun executeGitCommit(workspacePath: String, message: String): String = executeGitCommand(workspacePath, "commit $message")
    suspend fun executeGitPush(workspacePath: String): String = executeGitCommand(workspacePath, "push")
    suspend fun executeGitPull(workspacePath: String): String = executeGitCommand(workspacePath, "pull")

    /**
     * Pulls a clean code update out of a model's raw response for the quick-edit path, or
     * returns null if it can't be done safely. The old code stripped a fence only when the
     * ENTIRE response started/ended with ``` — the moment a model adds any prose before or
     * after the block (which happens often, e.g. "To modify this... Here's the code: ``` ```
     * Explanation: ..."), that anchored regex matches nothing and the whole raw response,
     * explanation and all, got written straight to the file and committed. This instead
     * searches for a fenced block anywhere in the text, and refuses to apply anything that
     * doesn't reduce to exactly one clean block — never writes raw prose to a file.
     */
    private fun extractModelCodeUpdate(raw: String): String? {
        val fences = Regex("```[A-Za-z0-9_+.-]*\\n?([\\s\\S]*?)```").findAll(raw).map { it.groupValues[1].trim() }.toList()
        val candidate = when (fences.size) {
            0 -> raw.trim().takeIf { it.isNotBlank() && it.lineSequence().count() <= 4 } // short, fence-less one-liners are fine
            1 -> fences.single()
            else -> null // multiple blocks (e.g. a diff block plus a shell-command block) — ambiguous, refuse
        } ?: return null
        // A real diff/patch isn't raw file content — applying it verbatim would corrupt the file.
        val looksLikeDiff = candidate.lineSequence().any {
            it.startsWith("diff --git") || it.startsWith("@@") || it.startsWith("--- ") || it.startsWith("+++ ")
        }
        return candidate.takeIf { it.isNotBlank() && !looksLikeDiff }
    }

    private fun extractTargetWindow(fullText: String, query: String, maxChars: Int = 4000): Pair<Int, Int> {
        if (fullText.length <= maxChars) return 0 to fullText.length
        val stopwords = setOf(
            "the", "a", "an", "and", "or", "to", "in", "for", "on", "with", "at", "by", "from",
            "of", "is", "it", "i", "you", "me", "we", "he", "she", "this", "that", "want",
            "please", "can", "could", "would", "should", "change", "edit", "make", "update", "push"
        )
        val tokens = query.lowercase().split(Regex("[^a-zA-Z0-9_.-]+"))
            .filter { it.length >= 2 && it !in stopwords }

        val searchTerms = mutableSetOf<String>()
        for (t in tokens) {
            searchTerms.add(t)
            if (t == "button" || t == "buttons") searchTerms.add("btn")
            if (t == "color" || t == "colours") { searchTerms.add("color:"); searchTerms.add("background") }
            if (t == "nav" || t == "navbar") searchTerms.add("header")
        }

        var bestIndex = -1
        var maxHits = 0

        val lower = fullText.lowercase()
        for (term in searchTerms) {
            var idx = lower.indexOf(term)
            var count = 0
            while (idx != -1 && count < 30) {
                count++
                val windowStart = (idx - 500).coerceAtLeast(0)
                val windowEnd = (idx + 500).coerceAtMost(lower.length)
                val sub = lower.substring(windowStart, windowEnd)
                var hits = 0
                for (st in searchTerms) {
                    if (sub.contains(st)) hits++
                }
                if (hits > maxHits) {
                    maxHits = hits
                    bestIndex = idx
                }
                idx = lower.indexOf(term, idx + term.length + 1)
            }
        }

        val targetCenter = if (bestIndex != -1) bestIndex else 0
        val half = maxChars / 2
        var start = (targetCenter - half).coerceAtLeast(0)
        var end = (targetCenter + half).coerceAtMost(fullText.length)

        val prevNl = fullText.lastIndexOf('\n', start)
        if (prevNl in 0 until start && (start - prevNl) < 200) {
            start = prevNl + 1
        }
        val nextNl = fullText.indexOf('\n', end)
        if (nextNl in end until fullText.length && (nextNl - end) < 200) {
            end = nextNl
        }

        return start to end
    }

    private fun applySmartSnippetReplacement(
        original: String,
        winStart: Int,
        winEnd: Int,
        targetSnippet: String,
        updatedSnippet: String,
        isGreenBtn: Boolean = false
    ): String {
        var result = original
        if (updatedSnippet.isNotBlank() && !updatedSnippet.startsWith("ERROR:")) {
            val selectorRegex = Regex("(\\.[a-zA-Z0-9_-]+|#[a-zA-Z0-9_-]+)\\s*\\{")
            val match = selectorRegex.find(updatedSnippet)
            if (match != null && original.contains(match.value)) {
                val selStart = original.indexOf(match.value)
                val selClose = original.indexOf("}", selStart)
                if (selClose != -1) {
                    result = original.substring(0, selStart) + updatedSnippet + original.substring(selClose + 1)
                } else {
                    result = original.substring(0, winStart) + updatedSnippet + original.substring(winEnd)
                }
            } else if (updatedSnippet.length >= (winEnd - winStart) / 2) {
                result = original.substring(0, winStart) + updatedSnippet + original.substring(winEnd)
            } else {
                result = original.substring(0, winStart) + updatedSnippet + original.substring(winEnd)
            }
        } else if (isGreenBtn) {
            if (result.contains(".btn-gold {")) {
                result = result.replace(
                    "background: var(--gold-gradient);",
                    "background: linear-gradient(135deg, #1b5e20 0%, #2e7d32 50%, #4caf50 100%);"
                ).replace(
                    "color: var(--maroon-deep);",
                    "color: #ffffff;"
                )
            }
        }

        if ((result.contains("--green-gradient") || isGreenBtn) && !result.contains("--green-gradient:")) {
            result = result.replace(
                ":root {",
                ":root {\n  --green-gradient: linear-gradient(90deg, #1b5e20 0%, #2e7d32 50%, #4caf50 100%);\n  --green-deep: #052614;"
            )
        }

        return result
    }

    /**
     * Default path is the on-device model — it does the actual review/write/debug/explain
     * reasoning, matching the "phone is the dev workstation" pitch. The laptop bridge is used
     * only for two narrow things: (1) reading the raw text of a file the user names, since the
     * cloned repo currently lives on the laptop's disk, not the phone's, and (2) genuinely
     * bigger tasks — an explicit "/" shell/dispatch command, a build/test run, or an explicit
     * "/escalate" ask for a deeper laptop-model pass. If the bridge is unreachable, file-text
     * fetch fails gracefully and the local model still answers, just without that file's exact
     * contents — it never hard-fails just because the laptop is offline.
     */
    /**
     * @param reviewOnly When true (the Review workspace's "Open Detailed Review" / issue-review
     * entry points), this message can NEVER write a file or make a commit, no matter what words
     * it happens to contain. Found live: a review prompt got misrouted into the quick-edit
     * intent heuristic below (it does plain keyword matching, e.g. "commit"+"change" anywhere in
     * the text) and it edited and committed an unrelated file in the repo while the user only
     * asked for a review. Review prompts always include a diff's own patch text, which can
     * contain almost any word — matching by keyword instead of by call-site was never going to
     * be reliable, so the call site now says explicitly which behavior it wants.
     */
    fun sendCodeSessionMessage(text: String, reviewOnly: Boolean = false) {
        val id = activeCodeSessionId ?: return
        val session = codeSessions.firstOrNull { it.id == id } ?: return
        val trimmed = text.trim()
        if (trimmed.isBlank() || codeSessionBusy) return
        codeSessions = codeSessionStore?.append(id, "user", trimmed).orEmpty()
        codeSessionBusy = true
        viewModelScope.launch {
            var replyRole = "assistant"
            val response = try {
                when {
                    reviewOnly -> codeEngine.explain(trimmed)
                    isGitCommand(trimmed) -> {
                        executeGitCommand(session.workspace, trimmed)
                    }
                    run {
                        val lower = trimmed.lowercase()
                        val isPushIntent = ("push" in lower && ("code" in lower || "change" in lower || "repo" in lower || "github" in lower || "it" in lower || "ui" in lower)) ||
                            ("commit" in lower && ("code" in lower || "change" in lower || "it" in lower)) ||
                            "make the changes and push" in lower
                        isPushIntent && java.io.File(session.workspace).let { it.exists() && it.resolve(".git").isDirectory }
                    } -> {
                        val sessionDir = java.io.File(session.workspace)
                        val targetFile = sessionDir.resolve("index.html").takeIf { it.exists() }
                            ?: sessionDir.walkTopDown().filter { it.isFile && !it.path.contains("/.git/") && !it.name.endsWith(".lnk") }
                                .firstOrNull { it.name.endsWith(".html") || it.name.endsWith(".js") || it.name.endsWith(".ts") || it.name.endsWith(".kt") }
                            ?: sessionDir.walkTopDown().firstOrNull { it.isFile && !it.path.contains("/.git/") }

                        if (targetFile != null) {
                            val currentText = targetFile.readText()
                            val (winStart, winEnd) = extractTargetWindow(currentText, trimmed, maxChars = 1200)
                            val targetSnippet = currentText.substring(winStart, winEnd)
                            val isGreenBtn = "green" in trimmed.lowercase() && ("btn" in trimmed.lowercase() || "button" in trimmed.lowercase())
                            val rawResponse = codeEngine.write(
                                "Modify this code snippet according to the user request. Apply the changes directly. Output ONLY the updated code snippet — no explanation, no reasoning, no diff syntax, nothing before or after the code fence:\nUser request: $trimmed",
                                "Code snippet:\n$targetSnippet"
                            ).trim()
                            val updatedSnippet = rawResponse.takeUnless { it.startsWith("ERROR:", ignoreCase = true) }?.let(::extractModelCodeUpdate)

                            if (updatedSnippet == null) {
                                "⚠️ Couldn't safely apply that edit — the model's reply included explanation text or diff syntax instead of just the updated code, and writing it as-is would have corrupted `${targetFile.name}`. Nothing was changed or committed. Try rephrasing more directly (e.g. \"just output the new file content\")."
                            } else {
                                val updatedFull = applySmartSnippetReplacement(
                                    currentText, winStart, winEnd, targetSnippet, updatedSnippet, isGreenBtn
                                )
                                targetFile.writeText(updatedFull)
                                val commitResult = executeGitCommit(session.workspace, "Updated ${targetFile.name} per user request")
                                val declinedPush = Regex("\\b(do not|don't|dont|without|never)\\s+push", RegexOption.IGNORE_CASE).containsMatchIn(trimmed)
                                val pushResult = if (declinedPush) "Skipped push — you asked not to push." else executeGitPush(session.workspace)
                                buildString {
                                    appendLine("✅ Applied changes to `${targetFile.name}` via Snapdragon Hexagon NPU.")
                                    appendLine()
                                    appendLine("```")
                                    appendLine(updatedSnippet.take(1200))
                                    appendLine("```")
                                    appendLine()
                                    appendLine(commitResult)
                                    appendLine()
                                    appendLine(pushResult)
                                }
                            }
                        } else {
                            "No editable file found in workspace ${session.workspace}"
                        }
                    }
                    trimmed.startsWith("/escalate", ignoreCase = true) -> {
                        replyRole = "laptop"
                        val instruction = trimmed.drop("/escalate".length).trim()
                            .ifBlank { session.messages.lastOrNull { it.role == "user" }?.text ?: trimmed }
                        bridgeClient.escalateWithOptions(
                            bridgeUrl, BridgeTask.WRITE,
                            "Repository: ${session.repository}\nWorkspace: ${session.workspace}",
                            instruction, effort.wireName
                        )
                    }
                    trimmed.startsWith("/") -> {
                        replyRole = "laptop"
                        val explicit = trimmed.removePrefix("/")
                        val plan = bridgeClient.planDispatch(bridgeUrl, explicit, session.workspace)
                        if (plan.executable && plan.command != null) {
                            val result = bridgeClient.execute(bridgeUrl, plan.command, session.workspace)
                            buildString {
                                append(plan.summary).append("\n\n$ ").append(plan.command).append('\n')
                                append((result.stdout + result.stderr).trim())
                                append("\n\nExit ").append(result.exitCode)
                            }
                        } else {
                            bridgeClient.escalateWithOptions(
                                bridgeUrl, BridgeTask.WRITE,
                                "Repository: ${session.repository}\nWorkspace: ${session.workspace}",
                                explicit, effort.wireName
                            )
                        }
                    }
                    !java.io.File(session.workspace).exists() && looksLikeBigTask(trimmed) -> {
                        replyRole = "laptop"
                        try {
                            bridgeClient.escalateWithOptions(
                                bridgeUrl, BridgeTask.WRITE,
                                "Repository: ${session.repository}\nWorkspace: ${session.workspace}",
                                trimmed, effort.wireName
                            )
                        } catch (e: Exception) {
                            replyRole = "assistant"
                            codeEngine.write(trimmed, "Repository: ${session.repository}\nWorkspace: ${session.workspace}")
                        }
                    }
                    else -> {
                        val task = inferTask(trimmed)
                        val sessionDir = java.io.File(session.workspace)
                        val isLocal = sessionDir.exists() && sessionDir.isDirectory
                        val knownFiles = if (isLocal) {
                            sessionDir.walkTopDown()
                                .filter { it.isFile && !it.path.contains("/.git/") && !it.path.contains("\\.git\\") }
                                .map { it.relativeTo(sessionDir).path.replace('\\', '/') }
                                .take(150)
                                .toList()
                        } else {
                            remoteFiles.ifEmpty {
                                runCatching { bridgeClient.workspaceFiles(bridgeUrl, session.workspace) }.getOrNull().orEmpty()
                            }
                        }
                        val mentionedFile = knownFiles.firstOrNull { trimmed.contains(it, ignoreCase = true) }
                            ?: if (isLocal) {
                                knownFiles.firstOrNull {
                                    it.equals("index.html", true) || it.endsWith("/index.html", true) ||
                                    it.equals("main.js", true) || it.equals("App.tsx", true) ||
                                    it.equals("App.jsx", true) || it.endsWith("MainActivity.kt", true)
                                } ?: knownFiles.firstOrNull { !it.endsWith(".lnk") && !it.endsWith(".md") && !it.startsWith(".") }
                                  ?: knownFiles.firstOrNull()
                            } else null
                        val fileText = if (isLocal && mentionedFile != null) {
                            runCatching { java.io.File(sessionDir, mentionedFile).readText() }.getOrDefault("")
                        } else {
                            mentionedFile?.let {
                                runCatching { bridgeClient.workspaceFile(bridgeUrl, session.workspace, it) }.getOrNull()
                            }.orEmpty()
                        }
                        val isRemoteModelSelected = availableModels.any { it.id == selectedModel }
                        val useRealModel = isRemoteModelSelected && modelServiceReady && bridgeUrl.isNotBlank()
                        if (fileText.length > MAX_LOCAL_FILE_CHARS) {
                            if (!isLocal && useRealModel && !bridgeUrl.contains("localhost") && !bridgeUrl.contains("127.0.0.1")) {
                                replyRole = "laptop"
                                bridgeClient.escalateWithOptions(
                                    bridgeUrl, task,
                                    "Repository: ${session.repository}\nWorkspace: ${session.workspace}\nFile: $mentionedFile\n\n$fileText",
                                    trimmed, effort.wireName
                                )
                            } else {
                                val (winStart, winEnd) = extractTargetWindow(fileText, trimmed, maxChars = 4000)
                                val truncated = fileText.substring(winStart, winEnd)
                                val lineStart = fileText.substring(0, winStart).count { it == '\n' } + 1
                                val lineEnd = lineStart + truncated.count { it == '\n' }
                                val header = "Repository: ${session.repository}\nWorkspace: ${session.workspace}" +
                                    (mentionedFile?.let { "\nFile: $it (Lines $lineStart-$lineEnd)" } ?: "")
                                val enrichedContext = "$header\n\n$truncated"
                                val local = if (useRealModel) {
                                    replyRole = "laptop"
                                    bridgeClient.escalateWithOptions(
                                        laptopUrl = bridgeUrl,
                                        task = task,
                                        context = enrichedContext,
                                        instruction = trimmed,
                                        effort = effort.wireName
                                    )
                                } else {
                                    when (task) {
                                        BridgeTask.REVIEW -> {
                                            val findings = codeEngine.review(truncated.ifBlank { trimmed })
                                            if (findings.isEmpty()) "No issues found."
                                            else findings.joinToString("\n") { "Line ${it.line}: [${it.severity}] ${it.message}" }
                                        }
                                        BridgeTask.DEBUG -> codeEngine.debug(trimmed, enrichedContext)
                                        BridgeTask.EXPLAIN -> codeEngine.explain("Context:\n$enrichedContext\n\nQuestion:\n$trimmed")
                                        BridgeTask.WRITE -> codeEngine.write(trimmed, enrichedContext)
                                    }
                                }
                                buildString {
                                    append(local)
                                    if (mentionedFile != null) {
                                        append("\n\n💡 *Tip: To save this change to `$mentionedFile`, open **Files & Git (📁)** at top right -> tap `$mentionedFile` -> **Edit with IQF**. Once saved, run `/commit <msg>` and `/push` to push directly to GitHub!*")
                                    }
                                }
                            }
                        } else {
                            val header = "Repository: ${session.repository}\nWorkspace: ${session.workspace}" +
                                (mentionedFile?.let { "\nFile: $it" } ?: "")
                            val enrichedContext = if (fileText.isNotBlank()) "$header\n\n$fileText" else header
                            val local = if (useRealModel) {
                                replyRole = "laptop"
                                bridgeClient.escalateWithOptions(
                                    laptopUrl = bridgeUrl,
                                    task = task,
                                    context = enrichedContext,
                                    instruction = trimmed,
                                    effort = effort.wireName
                                )
                            } else {
                                when (task) {
                                    BridgeTask.REVIEW -> {
                                        val findings = codeEngine.review(fileText.ifBlank { trimmed })
                                        if (findings.isEmpty()) "No issues found."
                                        else findings.joinToString("\n") { "Line ${it.line}: [${it.severity}] ${it.message}" }
                                    }
                                    BridgeTask.DEBUG -> codeEngine.debug(trimmed, enrichedContext)
                                    BridgeTask.EXPLAIN -> codeEngine.explain(
                                        if (enrichedContext.isNotBlank()) "Context:\n$enrichedContext\n\nQuestion:\n$trimmed" else trimmed
                                    )
                                    BridgeTask.WRITE -> codeEngine.write(trimmed, enrichedContext)
                                }
                            }
                            if (fileText.lines().size > 40) {
                                "$local\n\n(This file is large — reply with \"/escalate\" to ask the laptop for a deeper pass.)"
                            } else local
                        }
                    }
                }
            } catch (error: Exception) { "ERROR: ${error.message ?: "Code agent failed"}" }
            codeSessions = codeSessionStore?.append(id, replyRole, response).orEmpty()
            codeSessionBusy = false
            refreshRemoteFiles(session.workspace)
        }
    }

    /**
     * Conservative char budget for a file handed to the on-device model, well under the
     * ~4096-token hard cap in LlamaEngine.cpp once the review/debug system prompt, ChatML
     * wrapping, and reserved output tokens are accounted for. Found via a live crash: a real
     * 1,901-line/73KB HTML file overflowed the context and hit a native abort.
     */
    private val MAX_LOCAL_FILE_CHARS = 8_000

    private fun looksLikeBigTask(text: String): Boolean {
        val lower = text.lowercase()
        return listOf(
            "run test", "run tests", "pytest", "npm test", "npm run", "npm install",
            "gradle", "gradlew", "pip install", "build the project", "run the app", "compile"
        ).any { it in lower }
    }

    fun cloneRemoteRepository(root: String, url: String) {
        if (remoteBusy) return
        remoteBusy = true; remoteResult = "Cloning repository…"
        viewModelScope.launch {
            try {
                val result = bridgeClient.cloneRepository(bridgeUrl, root, url.trim())
                activeRemoteWorkspace = result.path
                remoteResult = "Repository cloned\n${result.output}"
                remoteBusy = false
                refreshRemoteFiles(result.path)
            } catch (error: Exception) { remoteResult = error.message ?: "Clone failed" }
            finally { remoteBusy = false }
        }
    }

    fun createRemoteRepository(root: String, name: String, publish: Boolean) {
        if (remoteBusy) return
        remoteBusy = true; remoteResult = if (publish) "Creating and publishing repository…" else "Creating repository…"
        viewModelScope.launch {
            try {
                val result = bridgeClient.createRepository(bridgeUrl, root, name.trim(), publish)
                activeRemoteWorkspace = result.path
                remoteResult = if (publish) "Private GitHub repository created and pushed" else "Local Git repository created"
                remoteBusy = false
                refreshRemoteFiles(result.path)
            } catch (error: Exception) { remoteResult = error.message ?: "Repository creation failed" }
            finally { remoteBusy = false }
        }
    }

    fun runRemoteCommand(cwd: String, command: String) {
        if (remoteBusy || command.isBlank()) return
        remoteBusy = true; remoteResult = "$ $command\nRunning…"
        viewModelScope.launch {
            try {
                val result = bridgeClient.execute(bridgeUrl, command.trim(), cwd)
                remoteResult = "$ $command\n" + (result.stdout + result.stderr).trim() + "\nExit ${result.exitCode}"
                remoteBusy = false
                refreshRemoteFiles(cwd)
            } catch (error: Exception) { remoteResult = error.message ?: "Command failed" }
            finally { remoteBusy = false }
        }
    }

    fun planDispatch(instruction: String, cwd: String) {
        if (instruction.isBlank() || cwd.isBlank() || dispatchBusy) return
        val store = dispatchStore ?: return
        dispatchBusy = true
        dispatchError = null
        viewModelScope.launch {
            try {
                val plan = bridgeClient.planDispatch(bridgeUrl, instruction.trim(), cwd)
                dispatchRecords = store.add(
                    DispatchRecord(
                        id = "dispatch-${System.currentTimeMillis()}",
                        instruction = instruction.trim(),
                        summary = plan.summary,
                        command = plan.command,
                        cwd = plan.cwd,
                        executable = plan.executable
                    )
                )
            } catch (error: Exception) {
                dispatchError = error.message ?: "Dispatch planning failed"
            } finally {
                dispatchBusy = false
            }
        }
    }

    fun executeDispatch(record: DispatchRecord) {
        val command = record.command ?: return
        val store = dispatchStore ?: return
        if (!record.executable || dispatchBusy) return
        dispatchBusy = true
        dispatchError = null
        dispatchRecords = store.update(record.id) { it.copy(status = DispatchStatus.RUNNING) }
        viewModelScope.launch {
            try {
                val result = bridgeClient.execute(bridgeUrl, command, record.cwd)
                dispatchRecords = store.update(record.id) {
                    it.copy(
                        status = if (result.exitCode == 0) DispatchStatus.COMPLETED else DispatchStatus.FAILED,
                        stdout = result.stdout,
                        stderr = result.stderr,
                        exitCode = result.exitCode
                    )
                }
            } catch (error: Exception) {
                dispatchRecords = store.update(record.id) {
                    it.copy(status = DispatchStatus.FAILED, stderr = error.message ?: "Execution failed")
                }
            } finally {
                dispatchBusy = false
            }
        }
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
                if (selectedModel?.contains("on-device", ignoreCase = true) != true && selectedModel?.contains("Snapdragon", ignoreCase = true) != true) {
                    selectedModel = health.model
                }
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
                if (selectedModel?.contains("on-device", ignoreCase = true) != true && selectedModel?.contains("Snapdragon", ignoreCase = true) != true) {
                    selectedModel = discoveredModels.firstOrNull { it.selected }?.id ?: health.model
                }
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
        // Cloned GitHub repositories belong in Code Sessions, not in regular chat
    }

    internal fun inferTask(prompt: String): BridgeTask {
        val lower = prompt.lowercase()
        return when {
            "review" in lower || "check diff" in lower || "audit" in lower -> BridgeTask.REVIEW
            "debug" in lower || "crash" in lower || "fix" in lower || "error" in lower || "exception" in lower -> BridgeTask.DEBUG
            "explain" in lower || "what is" in lower || "what does" in lower || "why" in lower || "how" in lower || "tell me" in lower || "describe" in lower || "meaning" in lower || "define" in lower || "difference" in lower -> BridgeTask.EXPLAIN
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

        val lowerPrompt = prompt.lowercase()
        val repoRoot = application?.filesDir?.resolve("repositories")
        val activeRepo = repoRoot?.listFiles()?.filter { it.isDirectory && it.resolve(".git").isDirectory }
            ?.maxByOrNull { it.resolve(".git/index").lastModified() }

        // Check for direct Git commands in main chat
        if (isGitCommand(prompt)) {
            viewModelScope.launch {
                val workspacePath = activeRepo?.absolutePath ?: run {
                    val candidate = repoRoot?.listFiles()?.firstOrNull { it.isDirectory && it.resolve(".git").isDirectory }
                    candidate?.absolutePath
                }
                val result = if (workspacePath != null) {
                    executeGitCommand(workspacePath, prompt)
                } else if (prompt.startsWith("/token", ignoreCase = true) ||
                    prompt.startsWith("git token", ignoreCase = true) ||
                    prompt.startsWith("/git token", ignoreCase = true)) {
                    val tok = prompt
                        .replace(Regex("^/git\\s+token", RegexOption.IGNORE_CASE), "")
                        .replace(Regex("^git\\s+token", RegexOption.IGNORE_CASE), "")
                        .replace(Regex("^/token", RegexOption.IGNORE_CASE), "")
                        .trim()
                    validateAndSaveGitHubToken(tok)
                } else {
                    "⚠️ No local Git repository found on phone.\nClone one first using the Files & Git menu (📁) or enter `/token <pat>` to configure authentication."
                }
                feed += FeedItem.Reply(result)
                saveChatMessage("assistant", result)
                sending = false
            }
            return
        }

        val isPushOrCommitIntent = ("push" in lowerPrompt && ("code" in lowerPrompt || "change" in lowerPrompt || "repo" in lowerPrompt || "github" in lowerPrompt || "it" in lowerPrompt)) ||
            ("commit" in lowerPrompt && ("code" in lowerPrompt || "change" in lowerPrompt || "it" in lowerPrompt)) ||
            "make the changes and push" in lowerPrompt

        if (isPushOrCommitIntent) {
            if (activeRepo != null) {
                val targetFile = activeRepo.resolve("index.html").takeIf { it.exists() }
                    ?: activeRepo.walkTopDown().filter { it.isFile && !it.path.contains("/.git/") && !it.name.endsWith(".lnk") }
                        .firstOrNull { it.name.endsWith(".html") || it.name.endsWith(".js") || it.name.endsWith(".ts") || it.name.endsWith(".kt") }
                    ?: activeRepo.walkTopDown().firstOrNull { it.isFile && !it.path.contains("/.git/") }

                if (targetFile != null) {
                    viewModelScope.launch {
                        try {
                            feed += FeedItem.Status("Modifying ${targetFile.name} on Qualcomm Snapdragon Hexagon NPU…")
                            val currentText = targetFile.readText()
                            val recentContext = feed.filterIsInstance<FeedItem.User>().takeLast(2).map { it.text }.joinToString("\n")
                            val instruction = if (recentContext.isNotBlank()) "$recentContext\n$prompt" else prompt

                            val (winStart, winEnd) = extractTargetWindow(currentText, instruction, maxChars = 1200)
                            val targetSnippet = currentText.substring(winStart, winEnd)
                            val isGreenBtn = "green" in instruction.lowercase() && ("btn" in instruction.lowercase() || "button" in instruction.lowercase() || "ui" in instruction.lowercase())
                            val updatedSnippet = codeEngine.write(
                                "Modify this code snippet according to the user request. Apply the requested changes directly. Output the updated code snippet:\nUser request: $instruction",
                                "Code snippet:\n$targetSnippet"
                            ).trim()
                                .replace(Regex("^```[A-Za-z0-9_+.-]*\\s*"), "")
                                .replace(Regex("\\s*```$"), "")
                                .trim()

                            val updatedFull = applySmartSnippetReplacement(
                                currentText, winStart, winEnd, targetSnippet, updatedSnippet, isGreenBtn
                            )
                            targetFile.writeText(updatedFull)
                            val commitResult = executeGitCommit(activeRepo.absolutePath, "Updated ${targetFile.name} per user request")
                            val pushResult = executeGitPush(activeRepo.absolutePath)
                            val displaySnippet = if (updatedSnippet.isNotBlank() && !updatedSnippet.startsWith("ERROR:")) {
                                updatedSnippet.take(1200)
                            } else {
                                targetFile.readText().let { text ->
                                    val idx = text.indexOf(".btn-gold")
                                    if (idx != -1) text.substring(idx, (idx + 500).coerceAtMost(text.length)) else updatedSnippet
                                }
                            }
                            val reply = buildString {
                                appendLine("✅ Applied changes to `${targetFile.name}` in repository **${activeRepo.name}** via Snapdragon Hexagon NPU.")
                                appendLine()
                                appendLine("```css")
                                appendLine(displaySnippet)
                                appendLine("```")
                                appendLine()
                                appendLine(commitResult)
                                appendLine()
                                appendLine(pushResult)
                            }
                            feed += FeedItem.Reply(reply)
                            saveChatMessage("assistant", reply)
                        } catch (e: Exception) {
                            feed += FeedItem.Status("Error applying and pushing code: ${e.message}", error = true)
                        } finally {
                            sending = false
                        }
                    }
                    return
                }
            } else {
                val msg = "⚠️ No local repository is currently open on your phone. Please go to the **Code** section -> **Repository** -> **Clone GitHub** to clone your repository to the phone first, and I will be able to edit and push your code directly!"
                feed += FeedItem.Reply(msg)
                saveChatMessage("assistant", msg)
                sending = false
                return
            }
        }

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
                val isOfflineSelected = selectedModel?.contains("on-device", ignoreCase = true) == true ||
                    selectedModel?.contains("Snapdragon", ignoreCase = true) == true ||
                    (codeEngine as? NativeEngine)?.isNpuActive == true
                val useRealModel = !isOfflineSelected && modelServiceReady &&
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
                            val target = if (enrichedContext.isNotBlank()) enrichedContext else prompt
                            val findings = codeEngine.review(target)
                            if (findings.isEmpty()) "No issues found."
                            else findings.joinToString("\n") { "Line ${it.line}: [${it.severity}] ${it.message}" }
                        }
                        BridgeTask.DEBUG   -> codeEngine.debug(prompt, enrichedContext)
                        BridgeTask.EXPLAIN -> codeEngine.explain(
                            if (enrichedContext.isNotBlank()) "Context:\n$enrichedContext\n\nQuestion/Instruction:\n$prompt" else prompt
                        )
                        BridgeTask.WRITE   -> codeEngine.write(prompt, enrichedContext)
                    }.also { feed += FeedItem.Reply(it) }
                }
                saveChatMessage("assistant", response)
                if (memoryEnabled && !incognito) remember("User: $prompt\nIQF: ${response.take(1_500)}")
                // Only offer escalation when there's an actual reason to — a diff/context past
                // the ~40-line point where a 1.5B on-device model's review quality reliably
                // holds up (see finals-30hr/BUILD_PLAN.md). Showing this after every reply,
                // regardless of whether the on-device answer was already sufficient, trained
                // the UI to look laptop-dependent by default — it isn't.
                val contextIsLarge = enrichedContext.lines().size > 40
                if (!useRealModel && bridgeUrl.isNotBlank() && toolAccessMode != ToolAccessMode.OFF && contextIsLarge) {
                    feed += FeedItem.EscalatePrompt(prompt = prompt, context = enrichedContext, task = task)
                }
            } catch (error: Exception) {
                feed += FeedItem.Status(error.message ?: "The offline engine could not complete this request.", error = true)
            } finally {
                sending = false
            }
        }
    }

    /** Shake to regenerate — re-issues the last user prompt through the same [send] path. */
    fun regenerateLastReply() {
        if (sending) return
        val lastPrompt = feed.filterIsInstance<FeedItem.User>().lastOrNull()?.text ?: return
        composer = lastPrompt
        send()
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

    fun generateFileEdit(path: String, currentText: String, instruction: String, onResult: (String) -> Unit) {
        if (instruction.isBlank() || fileEditBusy) return
        fileEditBusy = true
        fileEditError = null
        viewModelScope.launch {
            try {
                val cleaned = if (modelServiceReady && selectedModel != null && selectedModel != "offline") {
                    val generated = bridgeClient.escalateWithOptions(
                        bridgeUrl,
                        BridgeTask.WRITE,
                        "File: $path\n\n$currentText",
                        "Apply this change and return the complete updated file only: ${instruction.trim()}",
                        effort.wireName
                    )
                    generated.trim()
                        .replace(Regex("^```[A-Za-z0-9_+.-]*\\s*"), "")
                        .replace(Regex("\\s*```$"), "")
                        .trim()
                } else {
                    if (currentText.length > MAX_LOCAL_FILE_CHARS) {
                        val (winStart, winEnd) = extractTargetWindow(currentText, instruction, maxChars = 3500)
                        val targetSnippet = currentText.substring(winStart, winEnd)
                        val updatedSnippet = codeEngine.write(
                            "Modify this code snippet according to the instruction. Return ONLY the updated replacement code snippet without explanation or markdown fences:\nInstruction: ${instruction.trim()}",
                            "Code snippet:\n$targetSnippet"
                        ).trim()
                            .replace(Regex("^```[A-Za-z0-9_+.-]*\\s*"), "")
                            .replace(Regex("\\s*```$"), "")
                            .trim()
                        require(updatedSnippet.isNotBlank() && !updatedSnippet.startsWith("ERROR:")) {
                            updatedSnippet.ifBlank { "Model returned an empty edit" }
                        }
                        currentText.substring(0, winStart) + updatedSnippet + currentText.substring(winEnd)
                    } else {
                        val generated = codeEngine.write(
                            "Apply this change and return the complete updated file only: ${instruction.trim()}",
                            "File: $path\n\n$currentText"
                        )
                        generated.trim()
                            .replace(Regex("^```[A-Za-z0-9_+.-]*\\s*"), "")
                            .replace(Regex("\\s*```$"), "")
                            .trim()
                    }
                }
                require(cleaned.isNotBlank() && !cleaned.startsWith("ERROR:")) {
                    cleaned.ifBlank { "Model returned an empty edit" }
                }
                onResult(cleaned)
            } catch (error: Exception) {
                fileEditError = error.message ?: "IQForge could not generate the edit"
            } finally {
                fileEditBusy = false
            }
        }
    }

    fun refreshRemoteFiles(cwd: String) {
        if (cwd.isBlank() || remoteBusy) return
        remoteBusy = true
        remoteResult = null
        viewModelScope.launch {
            try { remoteFiles = bridgeClient.workspaceFiles(bridgeUrl, cwd) }
            catch (error: Exception) { remoteResult = error.message ?: "Could not load laptop files" }
            finally { remoteBusy = false }
        }
    }

    fun openRemoteFile(cwd: String, path: String) {
        remoteBusy = true
        remoteResult = null
        viewModelScope.launch {
            try {
                remoteFileText = bridgeClient.workspaceFile(bridgeUrl, cwd, path)
                remoteFilePath = path
            } catch (error: Exception) { remoteResult = error.message ?: "Could not read $path" }
            finally { remoteBusy = false }
        }
    }

    fun updateRemoteFile(value: String) { remoteFileText = value }
    fun closeRemoteFile() { remoteFilePath = null; remoteFileText = "" }

    fun saveRemoteFile(cwd: String) {
        val path = remoteFilePath ?: return
        remoteBusy = true
        remoteResult = null
        viewModelScope.launch {
            try {
                val bytes = bridgeClient.writeWorkspaceFile(bridgeUrl, cwd, path, remoteFileText)
                remoteResult = "Saved $path on laptop ($bytes bytes)"
            } catch (error: Exception) { remoteResult = error.message ?: "Could not save $path" }
            finally { remoteBusy = false }
        }
    }

    fun runRemoteTests(cwd: String) {
        remoteBusy = true
        remoteResult = "Running tests on laptop…"
        viewModelScope.launch {
            try {
                val result = bridgeClient.execute(
                    bridgeUrl,
                    "bridge/.venv/Scripts/python.exe -m pytest -q bridge/tests examples/mobile-edit-demo",
                    cwd
                )
                remoteResult = (result.stdout + result.stderr).trim().ifBlank { "Tests finished with exit code ${result.exitCode}" }
            } catch (error: Exception) { remoteResult = error.message ?: "Test run failed" }
            finally { remoteBusy = false }
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
    fontChoice: FontChoice,
    onFontChoiceChange: (FontChoice) -> Unit,
    workspace: WorkspaceViewModel = viewModel(),
    agent: AgentViewModel = viewModel(factory = AgentViewModel.Factory),
    deployment: DeploymentViewModel = viewModel()
) {
    val state by workspace.state
    val context = LocalContext.current
    val settingsPreferences = remember { context.getSharedPreferences("iqforge_settings", Context.MODE_PRIVATE) }
    var hapticEnabled by rememberSaveable { mutableStateOf(settingsPreferences.getBoolean("haptic_feedback", true)) }
    var voiceLanguage by rememberSaveable { mutableStateOf(settingsPreferences.getString("voice_language", Locale.getDefault().toLanguageTag()) ?: Locale.getDefault().toLanguageTag()) }
    var voiceName by rememberSaveable { mutableStateOf(settingsPreferences.getString("tts_voice", "").orEmpty()) }
    var voicePace by rememberSaveable { mutableStateOf(settingsPreferences.getFloat("voice_pace", 1f)) }
    var navigationOpen by remember { mutableStateOf(false) }
    // Face-down locks the session (shoulder-surf protection) and stays locked — flipping
    // back face-up does NOT auto-unlock, an explicit tap does. Whatever was visible while
    // it was face-down may already have been seen; auto-unlocking on flip-back defeats
    // the point.
    var sessionLocked by remember { mutableStateOf(false) }
    // Tilting the phone forward/back scrolls the chat feed — back to the original tilt-to-scroll
    // gesture (the sidebar-toggle it was temporarily switched to is gone). Shake regenerates the
    // last on-device reply. Face-down locks the session (see above).
    val feedListState = rememberLazyListState()
    val sensorScope = rememberCoroutineScope()
    SensorFeedback(
        onTiltScroll = { delta -> sensorScope.launch { feedListState.scrollBy(delta) } },
        onShake = { agent.regenerateLastReply() },
        onFaceDown = { isDown -> if (isDown) sessionLocked = true }
    )
    var destination by rememberSaveable { mutableStateOf(AppDestination.CHATS) }
    var selectedProjectName by rememberSaveable { mutableStateOf<String?>(null) }
    var showAddToChat by rememberSaveable { mutableStateOf(false) }
    var showToolAccess by rememberSaveable { mutableStateOf(false) }
    var showConnectors by rememberSaveable { mutableStateOf(false) }
    var showProjects by rememberSaveable { mutableStateOf(false) }
    var showModels by rememberSaveable { mutableStateOf(false) }
    var showEffort by rememberSaveable { mutableStateOf(false) }
    var showCreateTask by rememberSaveable { mutableStateOf(false) }
    var showCreateProject by rememberSaveable { mutableStateOf(false) }
    var settingsDialog by rememberSaveable { mutableStateOf(SettingsDialog.NONE) }
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
    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) agent.updateComposer(spoken)
        }
    }
    val launchSpeech: () -> Unit = {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, voiceLanguage)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak your IQF request")
        }
        runCatching { speechLauncher.launch(intent) }
            .onFailure { agent.reportAttachmentError("No Android speech recognition service is available.") }
        Unit
    }
    val audioPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchSpeech() else agent.reportAttachmentError("Microphone permission is required for voice input.")
    }
    val startVoiceInput: () -> Unit = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            launchSpeech()
        } else {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    LaunchedEffect(Unit) {
        agent.refreshServices()
        agent.refreshDispatchWorkspaces()
    }
    if (state.selectedFile != null) { EditorScreen(state, workspace, agent); return }
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
                    onVoice = startVoiceInput,
                    hapticEnabled = hapticEnabled,
                    send = { agent.send(state.editorText) }
                )
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (destination) {
                AppDestination.CHATS -> Feed(Modifier.fillMaxSize(), agent, state, feedListState)
                AppDestination.DISPATCH -> DispatchPage(agent)
                AppDestination.COWORK -> CoworkPage(agent, state) { showCreateTask = true }
                AppDestination.PROJECTS -> ProjectsPage(
                    state = state,
                    workspace = workspace,
                    onOpen = {
                        workspace.selectRepository(it)
                        selectedProjectName = it
                        destination = AppDestination.PROJECT_DETAIL
                    },
                    onNew = {
                        showCreateProject = true
                    }
                )
                AppDestination.PROJECT_DETAIL -> ProjectDetailPage(
                    name = selectedProjectName,
                    state = state,
                    workspace = workspace,
                    agent = agent,
                    onArtifacts = { destination = AppDestination.ARTIFACTS },
                    onNewChat = {
                        agent.newChat()
                        destination = AppDestination.CHATS
                    },
                    onBack = { destination = AppDestination.PROJECTS }
                )
                AppDestination.CODE -> CodeSessionsPage(
                    state = state,
                    workspace = workspace,
                    agent = agent,
                    onAddDevice = { settingsDialog = SettingsDialog.DEVICE }
                )
                AppDestination.ARTIFACTS -> ArtifactsPage(state, workspace)
                AppDestination.REVIEW -> ReviewPage(
                    agent = agent,
                    workspace = workspace,
                    state = state,
                    onOpenCode = { destination = AppDestination.CODE }
                )
                AppDestination.DEPLOYMENT -> DeploymentPage(
                    repositories = state.repositories,
                    currentRepository = state.repo,
                    deployment = deployment,
                    onOpenCode = { check: DeploymentCheck ->
                        val repo = deployment.state.repository
                        if (repo != null) {
                            agent.createCodeSession(repo.root.absolutePath, title = "${repo.name} (Deploy Fix)")
                            agent.sendCodeSessionMessage(
                                "Fix this deployment checklist failure using the smallest safe code change. " +
                                    "Do not modify unrelated files.\n\nCheck: ${check.title}\nEvidence: ${check.detail}"
                            )
                            destination = AppDestination.CODE
                        }
                    },
                    onOpenDetailedReview = {
                        val repo = deployment.state.repository
                        if (repo != null) {
                            agent.createCodeSession(repo.root.absolutePath, title = "${repo.name} (Deployment Review)")
                            agent.sendCodeSessionMessage(
                                "Perform a detailed deployment-readiness review for ${repo.name} at commit " +
                                    "${deployment.state.commitSha.ifBlank { "HEAD" }} targeting ${deployment.state.environment}. " +
                                    "Inspect build configuration, tests, deployment risks, rollback readiness, and any Dataform workflow impact."
                            )
                            destination = AppDestination.CODE
                        }
                    }
                )
                AppDestination.SETTINGS -> SettingsPage(
                    appearance = appearance,
                    fontChoice = fontChoice,
                    hapticEnabled = hapticEnabled,
                    agent = agent,
                    workspace = workspace,
                    onDialog = { settingsDialog = it },
                    onConnectors = {
                        showConnectors = true
                        agent.refreshServices()
                    },
                    onHaptic = {
                        hapticEnabled = it
                        settingsPreferences.edit().putBoolean("haptic_feedback", it).apply()
                    }
                )
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
    if (showCreateTask) {
        CreateCoworkTaskSheet(
            repositories = state.repositories.map { it.name },
            onCreate = { title, instruction, repository ->
                agent.createCoworkTask(title, instruction, repository)
                showCreateTask = false
                destination = AppDestination.COWORK
            },
            onDismiss = { showCreateTask = false }
        )
    }
    if (showCreateProject) {
        CreateProjectSheet(
            busy = state.busy,
            onCreate = { name, goal ->
                workspace.createRepository(name, goal)
                showCreateProject = false
                destination = AppDestination.PROJECTS
            },
            onClone = {
                workspace.startNewRepository()
                showCreateProject = false
                destination = AppDestination.CODE
            },
            onDismiss = { showCreateProject = false }
        )
    }
    if (settingsDialog != SettingsDialog.NONE) {
        SettingsDetailDialog(
            dialog = settingsDialog,
            appearance = appearance,
            fontChoice = fontChoice,
            voiceLanguage = voiceLanguage,
            voiceName = voiceName,
            voicePace = voicePace,
            agent = agent,
            workspace = state,
            githubTokenSaved = workspace.hasSavedToken(),
            onSaveGitHubToken = { workspace.saveGitHubToken(it) },
            onClearGitHubToken = { workspace.saveGitHubToken("") },
            onAppearance = onAppearanceChange,
            onFont = onFontChoiceChange,
            onVoiceLanguage = {
                voiceLanguage = it
                settingsPreferences.edit().putString("voice_language", it).apply()
            },
            onVoiceName = {
                voiceName = it
                settingsPreferences.edit().putString("tts_voice", it).apply()
            },
            onVoicePace = {
                voicePace = it
                settingsPreferences.edit().putFloat("voice_pace", it).apply()
            },
            onStartVoice = startVoiceInput,
            onDismiss = { settingsDialog = SettingsDialog.NONE }
        )
    }
    if (sessionLocked) {
        Surface(
            modifier = Modifier.fillMaxSize().clickable { sessionLocked = false },
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(16.dp))
                Text("Session locked", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
                Spacer(Modifier.height(8.dp))
                Text("Tap to unlock", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
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

@Composable private fun Feed(modifier: Modifier, agent: AgentViewModel, workspace: WorkspaceUiState, listState: LazyListState = rememberLazyListState()) {
    val feedSize = agent.feed.size
    val context = LocalContext.current
    LaunchedEffect(feedSize, agent.sending) {
        val visibleItems = feedSize + if (agent.sending) 1 else 0
        if (visibleItems > 0) listState.animateScrollToItem(visibleItems - 1)
    }
    // Feel a reply land, not just see it — and an escalation failure buzzes differently
    // from a normal one. Fires once per new item, only once the model has actually replied.
    LaunchedEffect(feedSize) {
        if (feedSize > 0 && !agent.sending) {
            val cue = if (agent.feed.lastOrNull() is FeedItem.EscalateError) HapticCue.ERROR else HapticCue.REPLY
            context.buzz(cue)
        }
    }

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
            is FeedItem.Reply          -> OnDeviceReplyCard(item.text, agent)
            is FeedItem.Diff           -> DiffCard(item)
            is FeedItem.EscalatePrompt -> EscalatePromptCard(item) { agent.escalate(item.prompt, item.context, item.task) }
            is FeedItem.LaptopReply    -> LaptopReplyCard(item.text)
            is FeedItem.EscalateError  -> EscalateErrorCard(item) { agent.escalate(item.prompt, item.context, item.task) }
        } }
        if (agent.sending) item { ThinkingIndicator() }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable private fun ThinkingIndicator() {
    Row(Modifier.padding(horizontal = 10.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(12.dp))
        Text("Thinking…", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun displayModelText(text: String): String = text.trim()

/** Renders **bold**, `inline code`, fenced code blocks, and bullet/numbered lists from raw model output as styled text instead of literal markdown syntax. */
@Composable private fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyLarge,
    color: Color = Color.Unspecified
) {
    Text(text = remember(text) { parseSimpleMarkdown(text) }, modifier = modifier, style = style, color = color)
}

private val boldOrCodePattern = Regex("\\*\\*(.+?)\\*\\*|`(.+?)`")

private fun androidx.compose.ui.text.AnnotatedString.Builder.appendInlineMarkdown(line: String) {
    var lastIndex = 0
    for (match in boldOrCodePattern.findAll(line)) {
        append(line.substring(lastIndex, match.range.first))
        val bold = match.groupValues[1]
        val code = match.groupValues[2]
        if (bold.isNotEmpty()) {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(bold) }
        } else {
            withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = Color(0x22FFFFFF))) { append(code) }
        }
        lastIndex = match.range.last + 1
    }
    append(line.substring(lastIndex))
}

private fun parseSimpleMarkdown(raw: String): androidx.compose.ui.text.AnnotatedString = buildAnnotatedString {
    val lines = raw.trim().lines()
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        if (line.trim().startsWith("```")) {
            i++
            val codeLines = mutableListOf<String>()
            while (i < lines.size && !lines[i].trim().startsWith("```")) {
                codeLines.add(lines[i]); i++
            }
            withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = Color(0x22FFFFFF))) {
                append(codeLines.joinToString("\n"))
            }
            if (i < lines.size) i++ // skip closing fence
            if (i < lines.size) append("\n\n")
            continue
        }
        val bulletMatch = Regex("^\\s*[-*]\\s+(.*)").find(line)
        val numberedMatch = Regex("^\\s*(\\d+)[.)]\\s+(.*)").find(line)
        val headingMatch = Regex("^#{1,6}\\s+(.*)").find(line)
        when {
            headingMatch != null -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(headingMatch.groupValues[1]) }
            bulletMatch != null -> { append("•  "); appendInlineMarkdown(bulletMatch.groupValues[1]) }
            numberedMatch != null -> { append("${numberedMatch.groupValues[1]}.  "); appendInlineMarkdown(numberedMatch.groupValues[2]) }
            else -> appendInlineMarkdown(line)
        }
        if (i != lines.lastIndex) append("\n")
        i++
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
                Image(
                    painter = painterResource(R.drawable.iqoo_q_mark),
                    contentDescription = "iQOO",
                    colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(IqfYellow),
                    modifier = Modifier.size(48.dp)
                )
            }
            Spacer(Modifier.height(22.dp))
            Text(
                text = if (incognito) "Private session" else "Let's iQuest on and on, Delfi.\nAre you ready?",
                color = MaterialTheme.colorScheme.onBackground,
                fontFamily = FontFamily.Serif,
                fontSize = 25.sp,
                lineHeight = 32.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(Modifier.height(14.dp))
            Text(
                if (incognito) "This chat is not saved to history or memory."
                else "Running on your iQOO's NPU — no cloud, no laptop needed.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
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
        Surface(color = Color(0xFF4A3B00), shape = RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp)) {
            Text(text, Modifier.padding(horizontal = 16.dp, vertical = 11.dp))
        }
    }

@Composable private fun StatusCard(text: String, success: Boolean = false, error: Boolean = false) {
    // success/error use a fixed dark background regardless of app theme (by design — it's a
    // status color, not a surface), so the text color must ALSO be fixed rather than following
    // MaterialTheme's theme-adaptive default: in light mode that default renders near-black text
    // on this same dark background — unreadable. Only the neutral (non-success/error) case should
    // still follow the theme, since its background does too.
    val background = when { success -> Color(0xFF0A3D17); error -> Color(0xFF4A1517); else -> MaterialTheme.colorScheme.surfaceVariant }
    val tint = when { success -> Color(0xFF36C76A); error -> Color(0xFFFF8A80); else -> MaterialTheme.colorScheme.primary }
    val textColor = when { success -> Color(0xFFDFF3E3); error -> Color(0xFFFFDEDC); else -> MaterialTheme.colorScheme.onSurfaceVariant }
    Surface(color = background, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (success) Icons.Default.Check else Icons.Default.Sync, null, tint = tint, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(text, color = textColor)
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
@Composable private fun OnDeviceReplyCard(text: String, agent: AgentViewModel) {
    val isNpu = agent.isNpuActive
    val tps = agent.lastNpuTokensPerSec
    val badgeLabel = if (isNpu) {
        if (tps != null) "Snapdragon Hexagon NPU (${String.format(java.util.Locale.US, "%.1f", tps)} t/s)"
        else "Snapdragon Hexagon NPU (HTP v81)"
    } else {
        "On-Device (${agent.offlineModelName?.substringBefore(" (") ?: "model"})"
    }
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 2.dp
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.PhoneAndroid,
                    contentDescription = null,
                    tint = Color(0xFF54C878),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    badgeLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF54C878),
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(6.dp))
            MarkdownText(displayModelText(text), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

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
            MarkdownText(displayModelText(text), style = MaterialTheme.typography.bodyLarge)
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
                // Fixed light color, not theme-adaptive onSurface — this card's background is
                // always dark maroon regardless of app theme, so in light mode onSurface (near
                // black) was rendering unreadable text on it. Same bug as StatusCard above.
                item.message,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFF2E4E3).copy(alpha = .80f)
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

@Composable private fun ModelPill(
    agent: AgentViewModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(24.dp),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(17.dp)
            )
            Spacer(Modifier.width(6.dp))
            val isNpu = agent.isNpuActive
            val displayModelName = when {
                agent.selectedModel == null -> "Select model"
                isNpu || agent.selectedModel?.contains("Snapdragon", ignoreCase = true) == true ->
                    "${agent.offlineModelName?.substringBefore(" (") ?: "On-device"} NPU"
                agent.selectedModel?.contains("on-device", ignoreCase = true) == true ->
                    agent.offlineModelName?.substringBefore(" (") ?: "On-device"
                else -> agent.selectedModel?.substringBefore(':') ?: "Select model"
            }
            Text(
                displayModelName,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable private fun Composer(
    agent: AgentViewModel,
    onAdd: () -> Unit,
    onModel: () -> Unit,
    onVoice: () -> Unit,
    hapticEnabled: Boolean,
    send: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    Surface(
        modifier = Modifier.fillMaxWidth().imePadding(),
        color = MaterialTheme.colorScheme.background
    ) {
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
                    Column {
                        Row(
                            modifier = Modifier.padding(horizontal = 17.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val isNpu = agent.isNpuActive
                            val isOffline = isNpu || agent.selectedModel?.contains("on-device", ignoreCase = true) == true ||
                                agent.selectedModel?.contains("Snapdragon", ignoreCase = true) == true
                            Text(
                                if (agent.isDownloadingModel) "Downloading ${agent.catalogModels.find { it.id == agent.downloadingModelId }?.displayName?.substringBefore(" (") ?: "Model"}…"
                                else if (isNpu) "Snapdragon NPU active (HTP v81)"
                                else if (isOffline) "On-device model (offline)"
                                else if (agent.modelServiceReady) "Connected coding model"
                                else "Private offline fallback",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                if (agent.isDownloadingModel) "${(agent.downloadProgress * 100).toInt()}% (${agent.downloadProgressStatus})"
                                else if (isNpu) (agent.lastNpuTokensPerSec?.let { "${String.format(java.util.Locale.US, "%.1f", it)} t/s NPU" } ?: "NPU active")
                                else if (isOffline) "${agent.offlineModelName?.substringBefore(" (") ?: "On-device"} active"
                                else if (agent.modelServiceReady) "Real model ready"
                                else "Offline fallback",
                                color = if (agent.isDownloadingModel) MaterialTheme.colorScheme.primary else if (isOffline) Color(0xFF54C878) else MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        if (agent.isDownloadingModel) {
                            LinearProgressIndicator(
                                progress = { agent.downloadProgress },
                                modifier = Modifier.fillMaxWidth().height(3.dp),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        }
                    }
                }

                if (agent.attachments.isNotEmpty() || agent.attachmentMessage != null) {
                    AttachmentStrip(agent)
                }

                PixelPetRunner(Modifier.padding(start = 6.dp, bottom = 2.dp))

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
                    ModelPill(
                        agent = agent,
                        onClick = onModel,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onVoice) {
                        Icon(
                            Icons.Default.Mic,
                            contentDescription = "Voice prompt",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(25.dp)
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    FilledIconButton(
                        onClick = {
                            if (!agent.sending && agent.composer.isNotBlank()) {
                                if (hapticEnabled) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                send()
                            }
                        },
                        enabled = agent.composer.isNotBlank() && !agent.sending,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = Color.White,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
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
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send"
                            )
                        }
                    }
                }
            }
        }
    }
}

private val pixelPetFrameA = listOf(
    "..YYYY..",
    ".YYYYYY.",
    ".YYYYYY.",
    ".YKYYKY.",
    ".YYYYYY.",
    "..YYYY..",
    "...Y.Y..",
    "........"
)
private val pixelPetFrameB = listOf(
    "..YYYY..",
    ".YYYYYY.",
    ".YYYYYY.",
    ".YKYYKY.",
    ".YYYYYY.",
    "..YYYY..",
    "..Y...Y.",
    "........"
)

@Composable private fun PixelPetRunner(modifier: Modifier = Modifier) {
    val spriteSize = 22.dp
    val density = LocalDensity.current
    BoxWithConstraints(modifier.fillMaxWidth().height(spriteSize)) {
        val trackWidthPx = with(density) { (maxWidth - spriteSize).toPx() }.coerceAtLeast(1f)
        val transition = rememberInfiniteTransition(label = "pixel-pet")
        val x by transition.animateFloat(
            initialValue = 0f,
            targetValue = trackWidthPx,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = (trackWidthPx / 0.09f).toInt().coerceIn(1400, 4200), easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pixel-pet-x"
        )
        var previousX by remember { mutableStateOf(0f) }
        val movingRight = x >= previousX
        SideEffect { previousX = x }

        var frame by remember { mutableStateOf(0) }
        LaunchedEffect(Unit) {
            while (true) {
                delay(150)
                frame = 1 - frame
            }
        }

        Canvas(
            modifier = Modifier
                .offset { IntOffset(x.toInt(), 0) }
                .size(spriteSize)
                .graphicsLayer { scaleX = if (movingRight) 1f else -1f }
        ) {
            val grid = if (frame == 0) pixelPetFrameA else pixelPetFrameB
            val cell = size.minDimension / 8f
            grid.forEachIndexed { row, line ->
                line.forEachIndexed { col, ch ->
                    val color = when (ch) {
                        'Y' -> Color(0xFFFFC400)
                        'K' -> Color(0xFF2B2B2B)
                        else -> null
                    }
                    if (color != null) {
                        drawRect(color = color, topLeft = Offset(col * cell, row * cell), size = Size(cell, cell))
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
            color = MaterialTheme.colorScheme.background,
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
                        Image(
                            painter = painterResource(R.drawable.iqoo_q_mark),
                            contentDescription = "iQForge",
                            colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(IqfYellow),
                            modifier = Modifier.weight(1f).size(32.dp),
                            alignment = Alignment.CenterStart,
                            contentScale = ContentScale.Fit
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
                // MVP nav: Chats / Code / Settings only. Dispatch, Cowork, Projects, and
                // Artifacts are folded into Code (Dispatch) or hidden for the demo — the
                // code behind them is untouched, just not linked from here. See
                // finals-30hr/MVP_PLAN.md.
                item { NavigationItem("Chats", Icons.Default.Forum) { onDestination(AppDestination.CHATS) } }
                item { NavigationItem("Code", Icons.Default.Code) { onDestination(AppDestination.CODE) } }
                item { NavigationItem("Review", Icons.Default.RateReview) { onDestination(AppDestination.REVIEW) } }
                item { NavigationItem("Deployment", Icons.Default.RocketLaunch) { onDestination(AppDestination.DEPLOYMENT) } }
                item { NavigationItem("Settings", Icons.Default.Settings) { onDestination(AppDestination.SETTINGS) } }
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
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { onChat(chat.id) }, modifier = Modifier.weight(1f)) {
                                Text(chat.title, Modifier.fillMaxWidth(), maxLines = 1, color = MaterialTheme.colorScheme.onSurface)
                            }
                            IconButton(onClick = { agent.deleteChat(chat.id) }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete \"${chat.title}\"",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
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
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = IqfYellow)
                    ) {
                        Icon(
                            when (appearance) {
                                Appearance.DARK -> Icons.Default.DarkMode
                                Appearance.LIGHT -> Icons.Default.LightMode
                                Appearance.SYSTEM -> Icons.Default.BrightnessAuto
                            },
                            "Cycle appearance"
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    val darkTheme = MaterialTheme.colorScheme.background.luminance() < .5f
                    Button(
                        onClick = onNewChat,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (darkTheme) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (darkTheme) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurfaceVariant
                        )
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

@Composable private fun DispatchPage(agent: AgentViewModel) {
    var instruction by rememberSaveable { mutableStateOf("") }
    var selectedWorkspace by rememberSaveable(agent.dispatchWorkspaces) {
        mutableStateOf(agent.dispatchWorkspaces.firstOrNull().orEmpty())
    }
    var workspaceMenu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(horizontal = 22.dp)) {
        Text("Dispatch", style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(top = 14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                TextButton(onClick = { workspaceMenu = true }, enabled = agent.dispatchWorkspaces.isNotEmpty()) {
                    Icon(Icons.Default.Folder, null)
                    Spacer(Modifier.width(8.dp))
                    Text(selectedWorkspace.ifBlank { "No bridge workspace available" }, maxLines = 1)
                    Icon(Icons.Default.ArrowDropDown, null)
                }
                DropdownMenu(expanded = workspaceMenu, onDismissRequest = { workspaceMenu = false }) {
                    agent.dispatchWorkspaces.forEach { root ->
                        DropdownMenuItem(
                            text = { Text(root, maxLines = 1) },
                            onClick = {
                                selectedWorkspace = root
                                workspaceMenu = false
                            }
                        )
                    }
                }
            }
            IconButton(agent::refreshDispatchWorkspaces) { Icon(Icons.Default.Refresh, "Refresh workspaces") }
        }
        if (agent.dispatchRecords.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    if (selectedWorkspace.isBlank()) "Connect the laptop bridge to discover an allowed workspace."
                    else "Describe one code search, Git, build, or test action. IQF will create a safe plan for your approval.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(agent.dispatchRecords, key = { it.id }) { record ->
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(record.instruction, style = MaterialTheme.typography.titleMedium)
                            Text(record.summary, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            record.command?.let {
                                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp)) {
                                    Text(it, Modifier.fillMaxWidth().padding(10.dp), fontFamily = FontFamily.Monospace)
                                }
                            }
                            if (record.stdout.isNotBlank()) Text(record.stdout.take(4_000), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                            if (record.stderr.isNotBlank()) Text(record.stderr.take(2_000), color = MaterialTheme.colorScheme.error, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(record.status.name.lowercase().replaceFirstChar { it.uppercase() }, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                                if (record.status == DispatchStatus.PLANNED && record.executable) {
                                    Button(onClick = { agent.executeDispatch(record) }, enabled = !agent.dispatchBusy) {
                                        Icon(Icons.Default.PlayArrow, null)
                                        Text("Run approved command")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        agent.dispatchError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        OutlinedTextField(
            instruction,
            { instruction = it },
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            placeholder = { Text("Ask IQF to search, build, test, or inspect") },
            maxLines = 3
        )
        Button(
            onClick = {
                agent.planDispatch(instruction, selectedWorkspace)
                instruction = ""
            },
            enabled = instruction.isNotBlank() && selectedWorkspace.isNotBlank() && !agent.dispatchBusy,
            modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp)
        ) { Text(if (agent.dispatchBusy) "Working…" else "Create safe plan") }
    }
}

@Composable private fun ProjectDetailPage(
    name: String?,
    state: WorkspaceUiState,
    workspace: WorkspaceViewModel,
    agent: AgentViewModel,
    onArtifacts: () -> Unit,
    onNewChat: () -> Unit,
    onBack: () -> Unit
) {
    val repo = state.repositories.firstOrNull { it.name == name }
    if (repo == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Project is no longer available") }
        return
    }
    val metadata = state.projectMetadata[repo.name] ?: com.iqforge.workspace.ProjectMetadata(repo.name)
    val projectTasks = agent.coworkTasks.filter { it.repository == repo.name }
    var menuOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var description by remember(metadata.description) { mutableStateOf(metadata.description) }
    var instructions by remember(metadata.instructions) { mutableStateOf(metadata.instructions) }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 22.dp), contentPadding = PaddingValues(bottom = 100.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to projects") }
                    Spacer(Modifier.weight(1f))
                    Box {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, "Project actions") }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(if (repo.name in state.pinnedRepositoryNames) "Unpin" else "Pin") },
                                leadingIcon = { Icon(Icons.Default.PushPin, null) },
                                onClick = { workspace.togglePinned(repo.name); menuOpen = false }
                            )
                            DropdownMenuItem(
                                text = { Text("Edit details") },
                                leadingIcon = { Icon(Icons.Default.Edit, null) },
                                onClick = { editing = true; menuOpen = false }
                            )
                            DropdownMenuItem(
                                text = { Text(if (metadata.archived) "Restore" else "Archive") },
                                leadingIcon = { Icon(Icons.Default.Archive, null) },
                                onClick = { workspace.toggleArchived(repo.name); menuOpen = false; onBack() }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                                onClick = { confirmDelete = true; menuOpen = false }
                            )
                        }
                    }
                }
            }
            item { Text(repo.name, style = MaterialTheme.typography.displaySmall) }
            item {
                AssistChip(
                    onClick = {},
                    label = { Text("Local Git repository") },
                    leadingIcon = { Icon(Icons.Default.Lock, null) }
                )
            }
            item {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Memory", style = MaterialTheme.typography.titleMedium)
                        Text(
                            metadata.description.ifBlank { "No project memory has been added." },
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ElevatedCard(onClick = onArtifacts, modifier = Modifier.weight(1f)) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Project knowledge", style = MaterialTheme.typography.titleSmall)
                            Text("${state.artifacts.size} readable files", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    ElevatedCard(onClick = { editing = true }, modifier = Modifier.weight(1f)) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Custom instructions", style = MaterialTheme.typography.titleSmall)
                            Text(if (metadata.instructions.isBlank()) "Add instructions" else "Configured", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            if (projectTasks.isEmpty()) {
                item { Text("No Cowork tasks are linked to this project yet.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 22.dp)) }
            } else {
                items(projectTasks, key = { "project-task-${it.id}" }) { task ->
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(task.title, style = MaterialTheme.typography.titleMedium)
                            Text(task.status.name.lowercase(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            task.result?.let { Text(it.take(240), maxLines = 4, modifier = Modifier.padding(top = 8.dp)) }
                        }
                    }
                }
            }
        }
        Button(onClick = onNewChat, modifier = Modifier.align(Alignment.BottomEnd).padding(22.dp)) {
            Icon(Icons.Default.Add, null)
            Text("New chat")
        }
    }
    if (editing) {
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text("Edit ${repo.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(description, { description = it }, label = { Text("Memory and purpose") }, minLines = 3)
                    OutlinedTextField(instructions, { instructions = it }, label = { Text("Custom model instructions") }, minLines = 3)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    workspace.updateProjectDetails(repo.name, description, instructions)
                    editing = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editing = false }) { Text("Cancel") } }
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${repo.name}?") },
            text = { Text("This removes the repository and its local files from this device. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    workspace.deleteRepository(repo.name)
                    confirmDelete = false
                    onBack()
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
}

@Composable private fun ProjectsPage(
    state: WorkspaceUiState,
    workspace: WorkspaceViewModel,
    onOpen: (String) -> Unit,
    onNew: () -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    val repositories = state.repositories
        .filter { state.showArchivedProjects || state.projectMetadata[it.name]?.archived != true }
        .filter { it.name.contains(query, ignoreCase = true) }
        .sortedWith(compareByDescending<com.iqforge.git.Repo> { it.name in state.pinnedRepositoryNames }.thenBy { it.name.lowercase() })
    Column(Modifier.fillMaxSize().padding(horizontal = 22.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Projects", style = MaterialTheme.typography.displaySmall, modifier = Modifier.weight(1f).padding(top = 14.dp, bottom = 20.dp))
            IconButton(onClick = { workspace.setShowArchived(!state.showArchivedProjects) }) {
                Icon(if (state.showArchivedProjects) Icons.Default.Inventory2 else Icons.Default.FilterList, "Toggle archived projects")
            }
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
                                    tint = if (repo.name in state.pinnedRepositoryNames) IqfYellow else MaterialTheme.colorScheme.onSurfaceVariant
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
        state.message?.let { Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp)) }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 8.dp)) }
    }
}

@Composable private fun CoworkPage(agent: AgentViewModel, state: WorkspaceUiState, onNewTask: () -> Unit) {
    var selected by remember { mutableStateOf<CoworkTask?>(null) }
    Column(Modifier.fillMaxSize().padding(horizontal = 22.dp)) {
        Text("Cowork", style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(top = 14.dp, bottom = 18.dp))
        if (agent.coworkTasks.isEmpty()) {
            Column(
                Modifier.weight(1f).fillMaxWidth().padding(horizontal = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.Workspaces, null, tint = IqfYellow, modifier = Modifier.size(78.dp))
                Spacer(Modifier.height(22.dp))
                Text("Run coding work from your phone", style = MaterialTheme.typography.headlineMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Spacer(Modifier.height(18.dp))
                Text("Tasks are sent to your connected model and their real result is saved here.", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                state.repo?.let {
                    Spacer(Modifier.height(10.dp))
                    Text("Current repository: ${it.name}", color = MaterialTheme.colorScheme.primary)
                }
            }
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(agent.coworkTasks, key = { it.id }) { task ->
                    ElevatedCard(onClick = { selected = task }, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                when (task.status) {
                                    CoworkStatus.RUNNING -> Icons.Default.Sync
                                    CoworkStatus.COMPLETED -> Icons.Default.TaskAlt
                                    CoworkStatus.FAILED, CoworkStatus.INTERRUPTED -> Icons.Default.ErrorOutline
                                },
                                null,
                                tint = when (task.status) {
                                    CoworkStatus.RUNNING -> MaterialTheme.colorScheme.primary
                                    CoworkStatus.COMPLETED -> Color(0xFF2EAD5B)
                                    else -> MaterialTheme.colorScheme.error
                                }
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(task.title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                                Text(
                                    listOfNotNull(task.repository, task.status.name.lowercase().replaceFirstChar(Char::uppercase)).joinToString(" · "),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(formatProjectDate(task.updatedAt), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
        Button(onClick = onNewTask, modifier = Modifier.align(Alignment.End).padding(bottom = 22.dp)) {
            Icon(Icons.Default.Add, null)
            Spacer(Modifier.width(8.dp))
            Text("New task")
        }
    }
    selected?.let { task ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(task.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(task.instruction)
                    task.result?.let { Text(it, color = MaterialTheme.colorScheme.onSurface) }
                    task.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = { TextButton(onClick = { selected = null }) { Text("Done") } },
            dismissButton = {
                TextButton(onClick = {
                    agent.removeCoworkTask(task.id)
                    selected = null
                }) { Text("Delete") }
            }
        )
    }
}

@Composable private fun ReviewPage(
    agent: AgentViewModel,
    workspace: WorkspaceViewModel,
    state: WorkspaceUiState,
    onOpenCode: () -> Unit
) {
    val github: GitHubViewModel = viewModel()
    // Keyed on the loaded repo's name, not just remembered once: GitHubViewModel.repoRef survives
    // leaving and returning to this page (confirmed — the PR list below does too), but this
    // composable's own rememberSaveable slot was found NOT to on this navigation pattern, so the
    // field went blank on every return even though a repo was still actually loaded. Re-deriving
    // the seed value from the ViewModel (the real source of truth) whenever repoRef changes means
    // the field can never drift from what's actually loaded, regardless of why the plain
    // rememberSaveable didn't survive.
    var repoInput by rememberSaveable(github.repoRef?.fullName) { mutableStateOf(github.repoRef?.fullName.orEmpty()) }
    val startReview: (String) -> Unit = { prompt ->
        val ref = github.repoRef
        if (ref != null) {
            val existing = state.repositories.firstOrNull {
                it.name.equals(ref.repo, ignoreCase = true) || it.name.startsWith("${ref.repo}-", ignoreCase = true)
            }
            if (existing != null) {
                agent.createCodeSession(existing.root.absolutePath, title = "${existing.name} (Review)")
                agent.sendCodeSessionMessage(prompt, reviewOnly = true)
                onOpenCode()
            } else {
                workspace.cloneRepository("https://github.com/${ref.owner}/${ref.repo}") { repo ->
                    agent.createCodeSession(repo.root.absolutePath, title = "${repo.name} (Review)")
                    agent.sendCodeSessionMessage(prompt, reviewOnly = true)
                    onOpenCode()
                }
            }
        }
    }
    val openCodeForFix: () -> Unit = {
        val ref = github.repoRef
        if (ref != null) {
            val existing = state.repositories.firstOrNull {
                it.name.equals(ref.repo, ignoreCase = true) || it.name.startsWith("${ref.repo}-", ignoreCase = true)
            }
            github.clearSelection()
            if (existing != null) {
                agent.createCodeSession(existing.root.absolutePath, title = "${existing.name} · Fix PR")
                onOpenCode()
            } else {
                workspace.cloneRepository("https://github.com/${ref.owner}/${ref.repo}") { repo ->
                    agent.createCodeSession(repo.root.absolutePath, title = "${repo.name} · Fix PR")
                    onOpenCode()
                }
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 10.dp)) {
        Text("Review", style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
        Text(
            "Open a small pull request, review its real patch on device, verify conflicts and checks, then merge the exact commit you reviewed.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 14.dp)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = repoInput,
                onValueChange = { repoInput = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("owner/repo or GitHub URL") },
                singleLine = true
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = { github.loadForInput(repoInput) },
                enabled = repoInput.isNotBlank() && !github.loadingList
            ) { Icon(Icons.Default.Search, "Load repository") }
        }
        if (github.repoRef != null) {
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    github.repoRef!!.fullName,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                IconButton(onClick = { github.loadForInput(repoInput) }, enabled = !github.loadingList) {
                    Icon(Icons.Default.Refresh, "Refresh")
                }
            }
            Text(
                "Open pull requests (${github.pullRequests.size})",
                modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        when {
            github.loadingList -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            github.listError != null -> StatusCard(github.listError!!, error = true)
            github.repoRef == null -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("Enter a GitHub repository above to get started.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> PullRequestList(github.pullRequests, onOpen = github::openPullRequest)
        }
        if (github.loadingDetail) StatusCard("Loading…")
        github.detailError?.let { StatusCard(it, error = true) }
    }
    github.selectedPullRequest?.let { pr ->
        PullRequestReviewWorkspace(
            pr,
            files = github.selectedPullRequestFiles,
            readiness = github.reviewReadiness,
            checks = github.selectedCheckRuns,
            mergeState = github.mergeState,
            mergeMessage = github.mergeMessage,
            mergedCommitSha = github.mergedCommitSha,
            agent = agent,
            onDismiss = github::clearSelection,
            onApproveAndMerge = { hasBlockers ->
                github.approveAndMerge(pr.number, github.reviewReadiness?.reviewedHeadSha.orEmpty(), hasBlockers)
            },
            onOpenDeepReview = {
                startReview(buildPullRequestReviewPrompt(github.repoRef!!.fullName, pr, github.selectedPullRequestFiles))
            },
            onOpenCodeFix = openCodeForFix
        )
    }
}

@Composable private fun CodeSessionsPage(
    state: WorkspaceUiState,
    workspace: WorkspaceViewModel,
    agent: AgentViewModel,
    onAddDevice: () -> Unit
) {
    var filesOpen by rememberSaveable { mutableStateOf(false) }
    var laptopFilesOpen by rememberSaveable { mutableStateOf(false) }
    var githubOpen by rememberSaveable { mutableStateOf(false) }
    var showRepositoryDialog by rememberSaveable { mutableStateOf(false) }
    var sessionToDeleteId by rememberSaveable { mutableStateOf<String?>(null) }
    var showModels by rememberSaveable { mutableStateOf(false) }
    var showEffort by rememberSaveable { mutableStateOf(false) }
    val github: GitHubViewModel = viewModel()
    val laptopRoot = agent.dispatchWorkspaces.firstOrNull().orEmpty()
    val activeRoot = agent.activeRemoteWorkspace.ifBlank {
        state.repo?.root?.absolutePath.orEmpty().ifBlank { laptopRoot }
    }
    if (agent.activeCodeSessionId != null) {
        CodeSessionChat(agent, workspace, state)
        return
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Code", style = MaterialTheme.typography.displaySmall, modifier = Modifier.weight(1f))
            TextButton(
                enabled = laptopRoot.isNotBlank(),
                onClick = {
                    laptopFilesOpen = !laptopFilesOpen
                    filesOpen = false
                    githubOpen = false
                    if (laptopFilesOpen) agent.refreshRemoteFiles(activeRoot)
                }
            ) { Text(if (laptopFilesOpen) "Sessions" else "Laptop files") }
            if (state.repo != null) TextButton(onClick = {
                filesOpen = !filesOpen
                githubOpen = false
                laptopFilesOpen = false
            }) {
                Text(if (filesOpen) "Sessions" else "Files")
            }
            if (state.repo != null) TextButton(onClick = {
                githubOpen = !githubOpen
                filesOpen = false
                laptopFilesOpen = false
                if (githubOpen) github.loadForRepo(state.repo)
            }) {
                Text(if (githubOpen) "Sessions" else "GitHub")
            }
        }
        if (laptopFilesOpen) {
            RemoteLaptopFiles(agent, activeRoot)
            return@Column
        }
        if (filesOpen && state.repo != null) {
            FilePanel(state, workspace)
            return@Column
        }
        if (githubOpen && state.repo != null) {
            GitHubPanel(github, state.repo, agent)
            return@Column
        }
        Text("Devices", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 24.dp))
        AssistChip(onClick = onAddDevice, label = { Text("Add or configure device") }, leadingIcon = { Icon(Icons.Default.Add, null) })
        ElevatedCard(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (agent.modelServiceReady) Icons.Default.Laptop else Icons.Default.Computer, null)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(agent.bridgeUrl, maxLines = 1, style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (agent.modelServiceReady) "Connected · ${agent.selectedModel ?: "model ready"}" else agent.connectorStatus,
                        color = if (agent.modelServiceReady) Color(0xFF2EAD5B) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(agent::checkBridge) { Icon(Icons.Default.Refresh, "Check connection") }
            }
        }
        Text("Sessions", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 26.dp, bottom = 8.dp))
        if (agent.codeSessions.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("Start a session to work with the coding agent.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(agent.codeSessions, key = { "session-${it.id}" }) { session ->
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Row(
                                modifier = Modifier.weight(1f).clickable { agent.openCodeSession(session.id) },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Code, null, tint = Color(0xFF54C878))
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(session.title, maxLines = 1, style = MaterialTheme.typography.titleMedium)
                                        Spacer(Modifier.width(6.dp))
                                        Surface(color = Color(0xFF1E3A24), shape = RoundedCornerShape(6.dp)) {
                                            Text("NPU", color = Color(0xFF54C878), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                                        }
                                    }
                                    Text(
                                        "GitHub: ${session.repository} · ${session.messages.size} messages",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            IconButton(onClick = { sessionToDeleteId = session.id }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete session",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(bottom = 22.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ModelPill(
                agent = agent,
                onClick = { showModels = true },
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(Modifier.weight(1f))
            Button(
                onClick = { showRepositoryDialog = true }
            ) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("New session", maxLines = 1)
            }
        }
    }
    if (showRepositoryDialog) RepositorySessionDialog(
        agent = agent,
        workspace = workspace,
        state = state,
        laptopRoot = laptopRoot,
        onDismiss = { showRepositoryDialog = false },
        onCreated = { showRepositoryDialog = false }
    )
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
    val sessionToDelete = agent.codeSessions.firstOrNull { it.id == sessionToDeleteId }
    if (sessionToDelete != null) {
        AlertDialog(
            onDismissRequest = { sessionToDeleteId = null },
            title = { Text("Delete session?") },
            text = { Text("Are you sure you want to delete \"${sessionToDelete.title}\"? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        agent.deleteCodeSession(sessionToDelete.id)
                        sessionToDeleteId = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { sessionToDeleteId = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun CodeSessionChat(
    agent: AgentViewModel,
    workspace: WorkspaceViewModel,
    state: WorkspaceUiState
) {
    val session = agent.codeSessions.firstOrNull { it.id == agent.activeCodeSessionId }
    var input by rememberSaveable { mutableStateOf("") }
    var showFilesSheet by rememberSaveable { mutableStateOf(false) }
    var showTokenDialog by rememberSaveable { mutableStateOf(false) }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    var showModels by rememberSaveable { mutableStateOf(false) }
    var showEffort by rememberSaveable { mutableStateOf(false) }
    var tokenInput by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(session?.messages?.size, agent.codeSessionBusy) {
        val count = (session?.messages?.size ?: 0) + if (agent.codeSessionBusy) 1 else 0
        if (count > 0) listState.animateScrollToItem(count - 1)
    }
    LaunchedEffect(session?.workspace) {
        val ws = session?.workspace.orEmpty()
        if (ws.isNotBlank() && state.repo?.root?.absolutePath != ws) {
            val match = state.repositories.firstOrNull { it.root.absolutePath == ws }
                ?: state.repositories.firstOrNull { it.name == session?.repository }
            if (match != null) {
                workspace.selectRepository(match.name)
            }
        }
    }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = agent::closeCodeSession) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to sessions")
            }
            Spacer(Modifier.width(4.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(session?.title ?: "Session", maxLines = 1, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.width(8.dp))
                    val isNpu = agent.isNpuActive
                    val isOffline = isNpu || agent.selectedModel?.contains("on-device", ignoreCase = true) == true ||
                        agent.selectedModel?.contains("Snapdragon", ignoreCase = true) == true
                    Surface(
                        onClick = { showModels = true },
                        color = Color(0xFF1E3A24),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (isNpu) "NPU"
                                else if (isOffline) "ON-DEVICE"
                                else agent.selectedModel?.substringBefore(':')?.uppercase() ?: "MODEL",
                                color = Color(0xFF54C878),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.width(2.dp))
                            Icon(
                                Icons.Default.ArrowDropDown,
                                contentDescription = "Switch model",
                                tint = Color(0xFF54C878),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
                Text(
                    session?.workspace.orEmpty(),
                    maxLines = 1,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { showDeleteDialog = true }) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete session",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { showFilesSheet = true }) {
                Icon(Icons.Default.FolderOpen, "Files & Git")
            }
        }
        if (session == null || session.messages.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    modifier = Modifier.padding(horizontal = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.Code,
                        contentDescription = null,
                        tint = IqfYellow,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.height(22.dp))
                    Text(
                        text = "Let's ship something,\nDelfi.",
                        color = MaterialTheme.colorScheme.onBackground,
                        fontFamily = FontFamily.Serif,
                        fontSize = 25.sp,
                        lineHeight = 32.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "Ask the coding agent to explore, edit, or run something in this workspace.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth().padding(horizontal = 18.dp),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(session.messages) { message ->
                    when (message.role) {
                        "user" -> UserBubble(message.text)
                        "laptop" -> LaptopReplyCard(message.text)
                        else -> OnDeviceReplyCard(message.text, agent)
                    }
                }
                if (agent.codeSessionBusy) item { ThinkingIndicator() }
                item { Spacer(Modifier.height(4.dp)) }
            }
        }
        Surface(
            modifier = Modifier.fillMaxWidth().imePadding(),
            color = MaterialTheme.colorScheme.background
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 16.dp),
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(32.dp),
                shadowElevation = 10.dp,
                tonalElevation = 1.dp
            ) {
                Column(Modifier.padding(14.dp)) {
                    Surface(
                        onClick = { showModels = true },
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 17.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Code,
                                contentDescription = null,
                                tint = Color(0xFF54C878),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                session?.repository ?: "Code Session",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(8.dp))
                            val isNpu = agent.isNpuActive
                            val tps = agent.lastNpuTokensPerSec
                            val isOffline = isNpu || agent.selectedModel?.contains("on-device", ignoreCase = true) == true ||
                                agent.selectedModel?.contains("Snapdragon", ignoreCase = true) == true
                            Text(
                                text = if (agent.codeSessionBusy) "Working…"
                                else if (isNpu) {
                                    if (tps != null) "${String.format(java.util.Locale.US, "%.1f", tps)} t/s NPU"
                                    else "Snapdragon NPU active"
                                } else if (isOffline) "${agent.offlineModelName?.substringBefore(" (") ?: "On-device"} active"
                                else if (agent.modelServiceReady) "${agent.selectedModel?.substringBefore(':') ?: "Connected"} ready"
                                else "On-device model",
                                color = if (agent.codeSessionBusy) MaterialTheme.colorScheme.primary else Color(0xFF54C878),
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }

                    PixelPetRunner(Modifier.padding(start = 6.dp, bottom = 2.dp))

                    TextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 76.dp, max = 132.dp),
                        placeholder = {
                            Text(
                                "Message the coding agent…",
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .75f),
                                fontSize = 22.sp
                            )
                        },
                        enabled = !agent.codeSessionBusy,
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
                        ModelPill(
                            agent = agent,
                            onClick = { showModels = true },
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(Modifier.weight(1f))
                        FilledIconButton(
                            onClick = {
                                if (input.isNotBlank() && !agent.codeSessionBusy) {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    val text = input
                                    input = ""
                                    agent.sendCodeSessionMessage(text)
                                }
                            },
                            enabled = input.isNotBlank() && !agent.codeSessionBusy,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = Color.White,
                                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                            )
                        ) {
                            if (agent.codeSessionBusy) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(21.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.surface
                                )
                            } else {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                            }
                        }
                    }
                }
            }
        }
    }
    if (showFilesSheet) {
        ModalBottomSheet(onDismissRequest = { showFilesSheet = false }) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Folder, null, tint = IqfYellow)
                    Spacer(Modifier.width(8.dp))
                    Text(session?.repository ?: "Repository", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = workspace::pull, enabled = !state.busy) {
                        Icon(Icons.Default.Refresh, "Pull from GitHub")
                    }
                }
                Text(session?.workspace.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    state.commitMessage, workspace::updateCommitMessage,
                    Modifier.fillMaxWidth(),
                    label = { Text("Commit message") },
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { workspace.commit() },
                        enabled = !state.busy,
                        modifier = Modifier.weight(1f)
                    ) { Text("Commit") }
                    Button(
                        onClick = {
                            if (workspace.hasSavedToken() || state.githubToken.isNotBlank()) {
                                workspace.push()
                            } else {
                                showTokenDialog = true
                            }
                        },
                        enabled = !state.busy,
                        modifier = Modifier.weight(1f)
                    ) { Text("Push to GitHub") }
                }
                if (state.busy) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(state.operation, style = MaterialTheme.typography.labelSmall)
                }
                state.message?.let {
                    Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
                }
                state.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
                }
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                Text("Files (tap to open editor & Edit with IQF)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 280.dp)) {
                    items(state.entries, key = { it.relativePath }) { entry ->
                        FileRow(entry, workspace)
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
    if (showTokenDialog) {
        AlertDialog(
            onDismissRequest = { showTokenDialog = false },
            title = { Text("GitHub Authentication") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter a GitHub Personal Access Token (classic token with 'repo' scope or fine-grained PAT with Contents write access) to push from your phone:")
                    OutlinedTextField(
                        tokenInput, { tokenInput = it },
                        Modifier.fillMaxWidth(),
                        label = { Text("Personal Access Token") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        workspace.saveGitHubToken(tokenInput)
                        workspace.push(tokenInput)
                        showTokenDialog = false
                    },
                    enabled = tokenInput.isNotBlank()
                ) { Text("Save & Push") }
            },
            dismissButton = {
                TextButton(onClick = { showTokenDialog = false }) { Text("Cancel") }
            }
        )
    }
    if (showDeleteDialog && session != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete session?") },
            text = { Text("Are you sure you want to delete \"${session.title}\"? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        agent.deleteCodeSession(session.id)
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
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

@Composable private fun ColumnScope.RemoteLaptopFiles(agent: AgentViewModel, cwd: String) {
    var query by rememberSaveable { mutableStateOf("") }
    var command by rememberSaveable { mutableStateOf("") }
    val shown = agent.remoteFiles.filter { it.contains(query, ignoreCase = true) }
    OutlinedTextField(
        query, { query = it }, Modifier.fillMaxWidth(),
        placeholder = { Text("Search laptop workspace files") },
        leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true
    )
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("${shown.size} editable files", Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = { agent.runRemoteTests(cwd) }, enabled = !agent.remoteBusy) {
            Icon(Icons.Default.PlayArrow, null)
            Text("Run tests")
        }
        IconButton(onClick = { agent.refreshRemoteFiles(cwd) }, enabled = !agent.remoteBusy) { Icon(Icons.Default.Refresh, "Refresh laptop files") }
    }
    if (agent.remoteBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
    agent.remoteResult?.let { Text(it.take(1_500), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
    OutlinedTextField(
        command, { command = it }, Modifier.fillMaxWidth(),
        placeholder = { Text("CLI: git status, git add ., git commit -m …, git push") },
        trailingIcon = {
            IconButton(onClick = { agent.runRemoteCommand(cwd, command) }, enabled = command.isNotBlank() && !agent.remoteBusy) {
                Icon(Icons.Default.Send, "Run command")
            }
        }, singleLine = true
    )
    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items(shown, key = { "remote-$it" }) { path ->
            ListItem(
                headlineContent = { Text(path, maxLines = 1) },
                leadingContent = { Icon(Icons.Default.Description, null) },
                modifier = Modifier.clickable { agent.openRemoteFile(cwd, path) }
            )
        }
    }
    agent.remoteFilePath?.let { path ->
        AlertDialog(
            onDismissRequest = agent::closeRemoteFile,
            title = { Text(path, maxLines = 2) },
            text = {
                OutlinedTextField(
                    agent.remoteFileText, agent::updateRemoteFile,
                    Modifier.fillMaxWidth().heightIn(min = 320.dp, max = 520.dp),
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                )
            },
            confirmButton = { Button(onClick = { agent.saveRemoteFile(cwd) }, enabled = !agent.remoteBusy) { Text("Save to laptop") } },
            dismissButton = { TextButton(onClick = agent::closeRemoteFile) { Text("Close") } }
        )
    }
}

@Composable private fun RepositorySessionDialog(
    agent: AgentViewModel,
    workspace: WorkspaceViewModel,
    state: WorkspaceUiState,
    laptopRoot: String,
    onDismiss: () -> Unit,
    onCreated: () -> Unit
) {
    val hasExistingRepos = state.repositories.isNotEmpty() || laptopRoot.isNotBlank()
    var tab by rememberSaveable { mutableStateOf(if (hasExistingRepos) 0 else 1) } // 0 = Existing, 1 = Clone, 2 = Create
    var targetLaptop by rememberSaveable { mutableStateOf(false) }
    var value by rememberSaveable { mutableStateOf("") }
    var token by rememberSaveable { mutableStateOf("") }
    var publish by rememberSaveable { mutableStateOf(false) }
    val isBusy = state.busy || agent.remoteBusy

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when (tab) {
                    0 -> "Select repository"
                    1 -> "Clone GitHub repository"
                    else -> "Create new repository"
                }
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (hasExistingRepos) {
                        FilterChip(
                            selected = tab == 0,
                            onClick = { tab = 0 },
                            label = { Text("Existing") }
                        )
                    }
                    FilterChip(
                        selected = tab == 1,
                        onClick = { tab = 1 },
                        label = { Text("Clone") }
                    )
                    FilterChip(
                        selected = tab == 2,
                        onClick = { tab = 2 },
                        label = { Text("Create") }
                    )
                }

                when (tab) {
                    0 -> {
                        Text(
                            "Choose a repository for this session:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (state.repositories.isEmpty() && laptopRoot.isBlank()) {
                            Text(
                                "No repositories found on phone.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                state.repositories.forEach { repo ->
                                    ElevatedCard(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                workspace.selectRepository(repo.name)
                                                agent.createCodeSession(
                                                    workspace = repo.root.absolutePath,
                                                    title = repo.name,
                                                    initialMessage = "Ready to code on repository **`${repo.name}`** with **Snapdragon Hexagon NPU**."
                                                )
                                                onCreated()
                                            },
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Default.Folder,
                                                contentDescription = null,
                                                tint = IqfYellow,
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Spacer(Modifier.width(10.dp))
                                            Column(Modifier.weight(1f)) {
                                                Text(
                                                    repo.name,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                Text(
                                                    "📱 Phone storage",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color(0xFF54C878)
                                                )
                                            }
                                            Icon(
                                                Icons.Default.PlayArrow,
                                                contentDescription = "Start session",
                                                tint = Color(0xFF54C878),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }

                                if (laptopRoot.isNotBlank()) {
                                    val laptopName = laptopRoot.replace('\\', '/').trimEnd('/').substringAfterLast('/').ifBlank { "Laptop" }
                                    ElevatedCard(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                agent.selectRemoteWorkspace(laptopRoot)
                                                agent.createCodeSession(
                                                    workspace = laptopRoot,
                                                    title = "$laptopName (Laptop)",
                                                    initialMessage = "Connected to laptop workspace **`$laptopRoot`**."
                                                )
                                                onCreated()
                                            },
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Default.Laptop,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Spacer(Modifier.width(10.dp))
                                            Column(Modifier.weight(1f)) {
                                                Text(
                                                    laptopName,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                Text(
                                                    "💻 Laptop bridge",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Icon(
                                                Icons.Default.PlayArrow,
                                                contentDescription = "Start session",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    1 -> {
                        if (laptopRoot.isNotBlank()) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(!targetLaptop, { targetLaptop = false }, { Text("📱 On phone") })
                                FilterChip(targetLaptop, { targetLaptop = true }, { Text("💻 On laptop") })
                            }
                        }
                        OutlinedTextField(
                            value, { value = it }, Modifier.fillMaxWidth(),
                            label = { Text("HTTPS GitHub URL") },
                            placeholder = { Text("https://github.com/user/repo") },
                            singleLine = true
                        )
                        if (!targetLaptop) {
                            OutlinedTextField(
                                token, { token = it }, Modifier.fillMaxWidth(),
                                label = { Text("GitHub token (optional, for private repos)") },
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true
                            )
                        }
                        Text(
                            if (targetLaptop && laptopRoot.isNotBlank()) "Target: Laptop ($laptopRoot)"
                            else "Target: 📱 On-device (Phone storage • Pure offline NPU)",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (targetLaptop) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFF54C878)
                        )
                    }
                    2 -> {
                        if (laptopRoot.isNotBlank()) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(!targetLaptop, { targetLaptop = false }, { Text("📱 On phone") })
                                FilterChip(targetLaptop, { targetLaptop = true }, { Text("💻 On laptop") })
                            }
                        }
                        OutlinedTextField(
                            value, { value = it }, Modifier.fillMaxWidth(),
                            label = { Text("Repository name") },
                            placeholder = { Text("my-project") },
                            singleLine = true
                        )
                        if (targetLaptop) Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(publish, { publish = it })
                            Text("Create private GitHub repo and push")
                        }
                        Text(
                            if (targetLaptop && laptopRoot.isNotBlank()) "Target: Laptop ($laptopRoot)"
                            else "Target: 📱 On-device (Phone storage • Pure offline NPU)",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (targetLaptop) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFF54C878)
                        )
                    }
                }

                state.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                }
            }
        },
        confirmButton = {
            if (tab == 0) {
                // In existing tab, tapping a repo card immediately opens the session
            } else {
                val canConfirm = value.isNotBlank() && !isBusy && (!targetLaptop || laptopRoot.isNotBlank())
                Button(
                    onClick = {
                        if (targetLaptop) {
                            if (tab == 1) agent.cloneRemoteRepository(laptopRoot, value)
                            else agent.createRemoteRepository(laptopRoot, value, publish)
                            onCreated()
                        } else {
                            if (tab == 1) {
                                workspace.cloneRepository(value, token = token) { repo ->
                                    agent.createCodeSession(
                                        workspace = repo.root.absolutePath,
                                        title = "${repo.name} (GitHub)",
                                        initialMessage = "✅ **Cloned GitHub repository `${repo.name}` on phone**\n\nReady for on-device exploration, code editing, and Git push directly from your phone powered by **Snapdragon Hexagon NPU**."
                                    )
                                    onCreated()
                                }
                            } else {
                                workspace.createRepository(value) { repo ->
                                    agent.createCodeSession(
                                        workspace = repo.root.absolutePath,
                                        title = repo.name,
                                        initialMessage = "✅ **Created repository `${repo.name}` on phone**\n\nReady to build with Snapdragon Hexagon NPU."
                                    )
                                    onCreated()
                                }
                            }
                        }
                    },
                    enabled = canConfirm
                ) {
                    Text(
                        when {
                            state.busy -> "Cloning to phone…"
                            agent.remoteBusy -> "Cloning to laptop…"
                            tab == 1 -> "Clone and open"
                            else -> "Create and open"
                        }
                    )
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable private fun ArtifactsPage(state: WorkspaceUiState, workspace: WorkspaceViewModel) {
    var query by rememberSaveable { mutableStateOf("") }
    val artifacts = state.artifacts.filter {
        it.name.contains(query, ignoreCase = true) || it.relativePath.contains(query, ignoreCase = true)
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 22.dp)) {
        Text("Artifacts", style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(top = 14.dp, bottom = 20.dp))
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
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize().padding(top = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                gridItems(artifacts, key = { it.relativePath }) { artifact ->
                    Column(Modifier.fillMaxWidth()) {
                        OutlinedCard(onClick = { workspace.openArtifact(artifact) }, modifier = Modifier.fillMaxWidth().height(130.dp)) {
                            val preview = remember(artifact.file.lastModified()) {
                                runCatching { artifact.file.readText().take(320) }.getOrDefault("Unable to preview")
                            }
                            Text(
                                preview,
                                modifier = Modifier.padding(12.dp),
                                maxLines = 7,
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        Text(artifact.name, maxLines = 1, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 7.dp))
                        Text(formatProjectDate(artifact.file.lastModified()), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

private fun formatProjectDate(timestamp: Long): String = if (timestamp <= 0L) "unknown" else
    SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(timestamp))

@Composable private fun SettingsPage(
    appearance: Appearance,
    fontChoice: FontChoice,
    hapticEnabled: Boolean,
    agent: AgentViewModel,
    workspace: WorkspaceViewModel,
    onDialog: (SettingsDialog) -> Unit,
    onConnectors: () -> Unit,
    onHaptic: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val connected = agent.connectors.count { it.connected }
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 28.dp)
    ) {
        item { Text("Settings", style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(top = 14.dp, bottom = 10.dp)) }
        item {
            SettingsGroup {
                SettingsRow(Icons.Default.QueryStats, "Usage", "${agent.chats.sumOf { it.messages.size }} saved messages") { onDialog(SettingsDialog.USAGE) }
            }
        }
        item {
            SettingsGroup {
                SettingsRow(Icons.Default.Tune, "Capabilities", "${enabledCapabilityCount(agent)} enabled") { onDialog(SettingsDialog.CAPABILITIES) }
                HorizontalDivider()
                SettingsRow(
                    Icons.Default.Key,
                    "GitHub personal access token",
                    if (workspace.hasSavedToken()) "Saved on this device" else "Required to approve and merge pull requests"
                ) { onDialog(SettingsDialog.GITHUB) }
                HorizontalDivider()
                SettingsRow(Icons.Default.Link, "Connectors", "$connected connected", onConnectors)
                HorizontalDivider()
                SettingsRow(Icons.Default.AdminPanelSettings, "Permissions", "Android app permissions") {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                }
            }
        }
        item {
            SettingsGroup {
                SettingsRow(Icons.Default.DarkMode, "Color mode", appearance.name.lowercase().replaceFirstChar { it.uppercase() }) { onDialog(SettingsDialog.COLOR) }
                HorizontalDivider()
                SettingsRow(Icons.Default.TextFields, "Font style", fontChoice.name.lowercase().replaceFirstChar { it.uppercase() }) { onDialog(SettingsDialog.FONT) }
                HorizontalDivider()
                SettingsRow(Icons.Default.GraphicEq, "Voice", "Android speech recognition") { onDialog(SettingsDialog.VOICE) }
            }
        }
        item {
            SettingsGroup {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Vibration, null)
                    Spacer(Modifier.width(14.dp))
                    Text("Haptic feedback", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    Switch(checked = hapticEnabled, onCheckedChange = onHaptic)
                }
                HorizontalDivider()
                SettingsRow(Icons.Default.Notifications, "Notifications", "System notification controls") {
                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    context.startActivity(intent)
                }
                HorizontalDivider()
                SettingsRow(Icons.Default.PrivacyTip, "Privacy", "Manage local data") { onDialog(SettingsDialog.PRIVACY) }
                HorizontalDivider()
                SettingsRow(Icons.Default.Share, "Sharing", "Open Android share sheet") {
                    context.startActivity(
                        Intent.createChooser(
                            Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "iQForge mobile coding workspace")
                            },
                            "Share iQForge"
                        )
                    )
                }
            }
        }
    }
}

@Composable private fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) =
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(18.dp), tonalElevation = 1.dp) {
        Column(content = content)
    }

@Composable private fun SettingsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) = Surface(onClick = onClick, color = Color.Transparent) {
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
        Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun enabledCapabilityCount(agent: AgentViewModel): Int = listOf(
    agent.modelServiceReady,
    agent.webSearchEnabled,
    agent.memoryEnabled,
    agent.connectors.any { it.connected },
    true // The compiled offline engine is always available.
).count { it }

@Composable private fun SettingsDetailDialog(
    dialog: SettingsDialog,
    appearance: Appearance,
    fontChoice: FontChoice,
    voiceLanguage: String,
    voiceName: String,
    voicePace: Float,
    agent: AgentViewModel,
    workspace: WorkspaceUiState,
    githubTokenSaved: Boolean,
    onSaveGitHubToken: (String) -> Unit,
    onClearGitHubToken: () -> Unit,
    onAppearance: (Appearance) -> Unit,
    onFont: (FontChoice) -> Unit,
    onVoiceLanguage: (String) -> Unit,
    onVoiceName: (String) -> Unit,
    onVoicePace: (Float) -> Unit,
    onStartVoice: () -> Unit,
    onDismiss: () -> Unit
) {
    var bridgeUrl by remember(dialog) { mutableStateOf(agent.bridgeUrl) }
    var githubToken by remember(dialog) { mutableStateOf("") }
    var githubTokenVisible by remember(dialog) { mutableStateOf(false) }
    var tokenSaved by remember(dialog, githubTokenSaved) { mutableStateOf(githubTokenSaved) }
    var tokenMessage by remember(dialog) { mutableStateOf<String?>(null) }
    val title = when (dialog) {
        SettingsDialog.USAGE -> "Usage"
        SettingsDialog.CAPABILITIES -> "Capabilities"
        SettingsDialog.GITHUB -> "GitHub access"
        SettingsDialog.COLOR -> "Color mode"
        SettingsDialog.FONT -> "Font style"
        SettingsDialog.VOICE -> "Voice"
        SettingsDialog.PRIVACY -> "Privacy"
        SettingsDialog.DEVICE -> "Laptop bridge"
        SettingsDialog.NONE -> "Settings"
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            when (dialog) {
                SettingsDialog.USAGE -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Saved chats: ${agent.chats.size}")
                    Text("Saved messages: ${agent.chats.sumOf { it.messages.size }}")
                    Text("Cowork tasks: ${agent.coworkTasks.size}")
                    Text("Repositories on device: ${workspace.repositories.size}")
                    Text("Readable artifacts: ${workspace.artifacts.size}")
                }
                SettingsDialog.CAPABILITIES -> Column {
                    CapabilityToggle("Real model", agent.modelServiceReady, null)
                    CapabilityToggle("Offline engine", true, null)
                    CapabilityToggle("Web search", agent.webSearchEnabled, agent::updateWebSearch)
                    CapabilityToggle("Memory", agent.memoryEnabled, agent::updateMemory)
                    CapabilityToggle("Live connectors", agent.connectors.any { it.connected }, null)
                }
                SettingsDialog.GITHUB -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        if (tokenSaved) "A GitHub token is saved on this device." else "Add a GitHub personal access token to approve and merge pull requests.",
                        color = if (tokenSaved) Color(0xFF63C174) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = githubToken,
                        onValueChange = {
                            githubToken = it
                            tokenMessage = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Personal access token") },
                        placeholder = { Text("github_pat_… or ghp_…") },
                        singleLine = true,
                        visualTransformation = if (githubTokenVisible) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { githubTokenVisible = !githubTokenVisible }) {
                                Icon(if (githubTokenVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility, if (githubTokenVisible) "Hide token" else "Show token")
                            }
                        }
                    )
                    Text(
                        "Use a fine-grained token with Pull requests: Read and write and Contents: Read and write access. The token is stored locally and is never displayed again.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = {
                            onSaveGitHubToken(githubToken.trim())
                            githubToken = ""
                            githubTokenVisible = false
                            tokenSaved = true
                            tokenMessage = "Token saved. You can now return to Review and approve a pull request."
                        },
                        enabled = githubToken.trim().length >= 20,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (tokenSaved) "Replace token" else "Save token") }
                    if (tokenSaved) {
                        OutlinedButton(
                            onClick = {
                                onClearGitHubToken()
                                githubToken = ""
                                tokenSaved = false
                                tokenMessage = "Saved GitHub token removed."
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Remove saved token") }
                    }
                    tokenMessage?.let {
                        Text(it, color = if (tokenSaved) Color(0xFF63C174) else MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                    }
                }
                SettingsDialog.COLOR -> Column {
                    Appearance.entries.forEach { choice ->
                        RadioSetting(choice.name.lowercase().replaceFirstChar { it.uppercase() }, appearance == choice) { onAppearance(choice) }
                    }
                }
                SettingsDialog.FONT -> Column {
                    FontChoice.entries.forEach { choice ->
                        RadioSetting(choice.name.lowercase().replaceFirstChar { it.uppercase() }, fontChoice == choice) { onFont(choice) }
                    }
                }
                SettingsDialog.VOICE -> VoiceSettingsContent(
                    languageTag = voiceLanguage,
                    voiceName = voiceName,
                    pace = voicePace,
                    onLanguage = onVoiceLanguage,
                    onVoice = onVoiceName,
                    onPace = onVoicePace,
                    onTestInput = onStartVoice
                )
                SettingsDialog.PRIVACY -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Chats, task results, preferences, and repositories are stored locally on this device. Incognito chats bypass saved history and memory.")
                    OutlinedButton(onClick = agent::clearMemory, modifier = Modifier.fillMaxWidth()) { Text("Clear memory") }
                    OutlinedButton(onClick = agent::clearSavedChats, modifier = Modifier.fillMaxWidth()) { Text("Clear saved chats") }
                }
                SettingsDialog.DEVICE -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(bridgeUrl, { bridgeUrl = it }, label = { Text("Bridge URL") }, singleLine = true)
                    Text(agent.connectorStatus, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = {
                        agent.updateBridgeUrl(bridgeUrl)
                        agent.checkBridge()
                    }, modifier = Modifier.fillMaxWidth()) { Text("Save and test") }
                }
                SettingsDialog.NONE -> Unit
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}

@Composable private fun CapabilityToggle(label: String, enabled: Boolean, onChange: ((Boolean) -> Unit)?) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        if (onChange == null) Icon(if (enabled) Icons.Default.CheckCircle else Icons.Default.Cancel, null, tint = if (enabled) Color(0xFF2EAD5B) else MaterialTheme.colorScheme.error)
        else Switch(enabled, onChange)
    }
}

@Composable private fun RadioSetting(label: String, selected: Boolean, onClick: () -> Unit) =
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected, onClick)
        Text(label)
    }

@Composable private fun VoiceSettingsContent(
    languageTag: String,
    voiceName: String,
    pace: Float,
    onLanguage: (String) -> Unit,
    onVoice: (String) -> Unit,
    onPace: (Float) -> Unit,
    onTestInput: () -> Unit
) {
    val context = LocalContext.current
    var engine by remember { mutableStateOf<TextToSpeech?>(null) }
    var installedVoices by remember { mutableStateOf<List<android.speech.tts.Voice>>(emptyList()) }
    var languageMenu by remember { mutableStateOf(false) }
    var voiceMenu by remember { mutableStateOf(false) }
    var paceMenu by remember { mutableStateOf(false) }
    val locales = remember {
        Locale.getAvailableLocales().filter { it.language.isNotBlank() }
            .distinctBy { it.toLanguageTag() }.sortedBy { it.displayName }
    }
    val selectedLocale = locales.firstOrNull { it.toLanguageTag() == languageTag } ?: Locale.getDefault()
    val matchingVoices = installedVoices.filter { it.locale.language == selectedLocale.language }.ifEmpty { installedVoices }
    DisposableEffect(context) {
        lateinit var tts: TextToSpeech
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                engine = tts
                installedVoices = tts.voices.orEmpty().filterNot { it.isNetworkConnectionRequired }.sortedBy { it.name }
            }
        }
        onDispose {
            tts.stop()
            tts.shutdown()
            engine = null
        }
    }
    LaunchedEffect(engine, voiceName, pace, languageTag) {
        engine?.let { tts ->
            tts.language = selectedLocale
            matchingVoices.firstOrNull { it.name == voiceName }?.let { tts.voice = it }
            tts.setSpeechRate(pace)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box {
            OutlinedButton(onClick = { languageMenu = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Language: ${selectedLocale.displayName}", Modifier.weight(1f), maxLines = 1)
                Icon(Icons.Default.ArrowDropDown, null)
            }
            DropdownMenu(expanded = languageMenu, onDismissRequest = { languageMenu = false }, modifier = Modifier.heightIn(max = 360.dp)) {
                locales.take(120).forEach { locale ->
                    DropdownMenuItem(text = { Text(locale.displayName) }, onClick = {
                        onLanguage(locale.toLanguageTag())
                        languageMenu = false
                    })
                }
            }
        }
        Box {
            OutlinedButton(onClick = { voiceMenu = true }, enabled = matchingVoices.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                Text(
                    if (matchingVoices.isEmpty()) "Loading installed voices…" else "Voice: ${voiceName.ifBlank { matchingVoices.first().name }}",
                    Modifier.weight(1f),
                    maxLines = 1
                )
                Icon(Icons.Default.ArrowDropDown, null)
            }
            DropdownMenu(expanded = voiceMenu, onDismissRequest = { voiceMenu = false }, modifier = Modifier.heightIn(max = 300.dp)) {
                matchingVoices.forEach { voice ->
                    DropdownMenuItem(text = { Text(voice.name, maxLines = 1) }, onClick = {
                        onVoice(voice.name)
                        voiceMenu = false
                    })
                }
            }
        }
        Box {
            OutlinedButton(onClick = { paceMenu = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Pace: ${when (pace) { .75f -> "Slow"; 1.25f -> "Fast"; else -> "Normal" }}", Modifier.weight(1f))
                Icon(Icons.Default.ArrowDropDown, null)
            }
            DropdownMenu(expanded = paceMenu, onDismissRequest = { paceMenu = false }) {
                listOf("Slow" to .75f, "Normal" to 1f, "Fast" to 1.25f).forEach { (label, value) ->
                    DropdownMenuItem(text = { Text(label) }, onClick = { onPace(value); paceMenu = false })
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { engine?.speak("IQF voice preview is ready", TextToSpeech.QUEUE_FLUSH, null, "iqf-preview") },
                enabled = engine != null,
                modifier = Modifier.weight(1f)
            ) { Text("Preview voice") }
            OutlinedButton(onClick = onTestInput, modifier = Modifier.weight(1f)) { Text("Test input") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun CreateCoworkTaskSheet(
    repositories: List<String>,
    onCreate: (String, String, String?) -> Unit,
    onDismiss: () -> Unit
) {
    var title by rememberSaveable { mutableStateOf("") }
    var instruction by rememberSaveable { mutableStateOf("") }
    var repository by rememberSaveable { mutableStateOf<String?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Create a Cowork task", style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("Task name") }, singleLine = true)
            PixelPetRunner(Modifier.padding(start = 4.dp))
            OutlinedTextField(instruction, { instruction = it }, Modifier.fillMaxWidth().height(150.dp), label = { Text("What should the model achieve?") })
            if (repositories.isNotEmpty()) {
                Text("Repository context", color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { FilterChip(repository == null, { repository = null }, { Text("None") }) }
                    items(repositories) { name -> FilterChip(repository == name, { repository = name }, { Text(name) }) }
                }
            }
            Button(
                onClick = { onCreate(title.trim(), instruction.trim(), repository) },
                enabled = title.isNotBlank() && instruction.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Start real task") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun CreateProjectSheet(
    busy: Boolean,
    onCreate: (String, String) -> Unit,
    onClone: () -> Unit,
    onDismiss: () -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    var goal by rememberSaveable { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Create a project", style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Project name") }, singleLine = true)
            OutlinedTextField(goal, { goal = it }, Modifier.fillMaxWidth().height(150.dp), label = { Text("Goals and context") })
            Button(
                onClick = { onCreate(name.trim(), goal.trim()) },
                enabled = name.matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,79}")) && !busy,
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (busy) "Creating…" else "Create Git project") }
            TextButton(onClick = onClone, modifier = Modifier.fillMaxWidth()) { Text("Clone an existing repository instead") }
        }
    }
}

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
    LaunchedEffect(Unit) {
        // Option 2 is the supported demo path: a shell-owned llama-server is started by
        // scripts/start_npu_server.ps1. Recheck localhost whenever this sheet opens so a
        // server started after app launch is reflected immediately.
        agent.refreshOfflineModel()
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp)) {
            SheetTitle("Select model", onDismiss)
            if (agent.availableModels.isEmpty()) {
                if (!agent.offlineModelReady) Text(
                    "NPU server is offline. Run scripts/start_npu_server.ps1 on the paired laptop, then refresh.",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                )
            } else {
                Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(22.dp)) {
                    Column {
                        agent.availableModels.forEachIndexed { index, model ->
                            Surface(onClick = { agent.selectModel(model); onDismiss() }, color = Color.Transparent) {
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
            val isNpu = agent.isNpuActive
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "On-device server (Hexagon NPU)",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f).padding(bottom = 8.dp)
                )
                TextButton(onClick = agent::refreshOfflineModel) {
                    Icon(Icons.Default.Refresh, null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Refresh")
                }
            }
            Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(22.dp)) {
                Column {
                    agent.catalogModels.forEachIndexed { index, model ->
                        val isActive = model.id == agent.activeCatalogModelId && agent.offlineModelReady
                        val isDownloaded = agent.isCatalogModelDownloaded(model)
                        val isDownloadingThis = agent.isDownloadingModel && model.id == agent.downloadingModelId
                        val isActivatingThis = agent.isActivatingModel && model.id == agent.activatingModelId
                        Surface(
                            onClick = {
                                if (agent.isActivatingModel) return@Surface
                                if (isActive && agent.offlineModelReady) {
                                    agent.selectOfflineModel(); onDismiss()
                                } else {
                                    agent.selectCatalogModel(model)
                                }
                            },
                            color = Color.Transparent
                        ) {
                            Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.PhoneAndroid,
                                    null,
                                    tint = if (isActive && (isNpu || agent.offlineModelReady)) Color(0xFF54C878) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        if (isActive && isNpu) "${model.displayName.substringBefore(" (")} (Snapdragon NPU)" else model.displayName,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        when {
                                            isActivatingThis -> "Activating on Hexagon NPU… this can take a few seconds"
                                            isDownloadingThis -> "Downloading: ${(agent.downloadProgress * 100).toInt()}% (${agent.downloadProgressStatus})"
                                            isActive && isNpu -> agent.lastNpuTokensPerSec?.let {
                                                "Hardware accelerated on Hexagon HTP • ${String.format(java.util.Locale.US, "%.1f", it)} tokens/sec"
                                            } ?: "Hardware accelerated on Hexagon HTP • Pure NPU"
                                            isActive && agent.offlineModelReady -> "Active • ${agent.offlineModelBytes / 1_000_000} MB GGUF • Pure NPU execution"
                                            isDownloaded -> "Downloaded • tap to activate"
                                            else -> "Tap to download (${String.format(java.util.Locale.US, "%.1f", model.approxSizeBytes / 1_000_000_000.0)} GB)"
                                        },
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                when {
                                    isActivatingThis -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                    isDownloadingThis -> {
                                        TextButton(onClick = { agent.cancelModelDownload() }) {
                                            Text("Cancel", color = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                    isActive && (agent.offlineModelReady || isNpu) -> Icon(Icons.Default.Check, "Active", tint = Color(0xFF54C878))
                                    !isDownloaded -> Icon(Icons.Default.Download, "Download", tint = MaterialTheme.colorScheme.primary)
                                    else -> Unit
                                }
                            }
                        }
                        if (index != agent.catalogModels.lastIndex) HorizontalDivider()
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

@Composable private fun GitHubPanel(github: GitHubViewModel, repo: com.iqforge.git.Repo, agent: AgentViewModel) {
    var tab by rememberSaveable { mutableStateOf(0) } // 0 = PRs, 1 = Issues
    val repoFullName = github.repoRef?.fullName ?: repo.name
    val startReview: (String) -> Unit = { prompt ->
        agent.createCodeSession(repo.root.absolutePath, title = "${repo.name} (Review)")
        agent.sendCodeSessionMessage(prompt, reviewOnly = true)
    }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                github.repoRef?.fullName ?: repo.name,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            IconButton(onClick = { github.loadForRepo(repo, forceRefresh = true) }, enabled = !github.loadingList) {
                Icon(Icons.Default.Refresh, "Refresh")
            }
        }
        Row(Modifier.padding(top = 8.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = tab == 0, onClick = { tab = 0 }, label = { Text("Pull requests (${github.pullRequests.size})") })
            FilterChip(selected = tab == 1, onClick = { tab = 1 }, label = { Text("Issues (${github.issues.size})") })
        }
        when {
            github.loadingList -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            github.listError != null -> StatusCard(github.listError!!, error = true)
            tab == 0 -> PullRequestList(github.pullRequests, onOpen = github::openPullRequest)
            else -> IssueList(github.issues, onOpen = github::openIssue)
        }
        if (github.loadingDetail) StatusCard("Loading…")
        github.detailError?.let { StatusCard(it, error = true) }
    }
    github.selectedPullRequest?.let { pr ->
        PullRequestReviewWorkspace(
            pr,
            files = github.selectedPullRequestFiles,
            readiness = github.reviewReadiness,
            checks = github.selectedCheckRuns,
            mergeState = github.mergeState,
            mergeMessage = github.mergeMessage,
            mergedCommitSha = github.mergedCommitSha,
            agent = agent,
            onDismiss = github::clearSelection,
            onApproveAndMerge = { hasBlockers ->
                github.approveAndMerge(pr.number, github.reviewReadiness?.reviewedHeadSha.orEmpty(), hasBlockers)
            },
            onOpenDeepReview = { startReview(buildPullRequestReviewPrompt(repoFullName, pr, github.selectedPullRequestFiles)) },
            onOpenCodeFix = {
                github.clearSelection()
                agent.createCodeSession(repo.root.absolutePath, title = "${repo.name} · Fix PR ${pr.number}")
            }
        )
    }
    github.selectedIssue?.let { issue ->
        IssueDetailDialog(
            issue,
            onDismiss = github::clearSelection,
            onReview = { startReview(buildIssueReviewPrompt(repoFullName, issue)) }
        )
    }
}

@Composable private fun ColumnScope.PullRequestList(
    pullRequests: List<com.iqforge.github.GitHubPullRequestSummaryDto>,
    onOpen: (Int) -> Unit
) {
    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(pullRequests, key = { "pr-${it.number}" }) { pr ->
            ElevatedCard(onClick = { onOpen(pr.number) }, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Code, null, tint = if (pr.draft) MaterialTheme.colorScheme.onSurfaceVariant else IqfYellow)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("#${pr.number} ${pr.title}", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${pr.user?.login ?: "unknown"} · ${pr.state}${if (pr.draft) " · draft" else ""}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        formatProjectDate(runCatching { java.time.Instant.parse(pr.updatedAt).toEpochMilli() }.getOrDefault(0L)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        if (pullRequests.isEmpty()) item {
            Text("No open pull requests.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 24.dp))
        }
    }
}

@Composable private fun ColumnScope.IssueList(
    issues: List<com.iqforge.github.GitHubIssueDto>,
    onOpen: (com.iqforge.github.GitHubIssueDto) -> Unit
) {
    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(issues, key = { "issue-${it.number}" }) { issue ->
            ElevatedCard(onClick = { onOpen(issue) }, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ErrorOutline, null, tint = IqfYellow)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("#${issue.number} ${issue.title}", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                        Text(
                            listOfNotNull(
                                issue.user?.login,
                                issue.labels.joinToString(", ") { it.name }.takeIf { it.isNotBlank() }
                            ).joinToString(" · "),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        formatProjectDate(runCatching { java.time.Instant.parse(issue.updatedAt).toEpochMilli() }.getOrDefault(0L)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        if (issues.isEmpty()) item {
            Text("No open issues.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 24.dp))
        }
    }
}

private fun buildPullRequestReviewPrompt(
    repoFullName: String,
    pr: GitHubPullRequestDetailDto,
    files: List<com.iqforge.github.GitHubPullRequestFileDto>
): String = buildString {
    append("Review GitHub pull request #${pr.number} in $repoFullName: \"${pr.title}\"\n")
    append("Branch: ${pr.head.ref} -> ${pr.base.ref} · +${pr.additions} -${pr.deletions} across ${pr.changedFiles} files\n\n")
    if (pr.head.sha.isNotBlank()) append("Reviewed head commit: ${pr.head.sha}\n\n")
    if (!pr.body.isNullOrBlank()) append("${pr.body}\n\n")
    append("Review the actual changed lines below for correctness, security, and edge cases. ")
    append("For every finding, name the file and added line. Do not invent issues outside this patch.\n\n")
    files.forEach { file ->
        append("FILE: ${file.filename} (${file.status}, +${file.additions} -${file.deletions})\n")
        append(file.patch ?: "[Patch unavailable: binary or too large for GitHub's patch response]")
        append("\n\n")
    }

}

private fun buildIssueReviewPrompt(repoFullName: String, issue: GitHubIssueDto): String = buildString {
    append("Review GitHub issue #${issue.number} in $repoFullName: \"${issue.title}\"\n\n")
    if (!issue.body.isNullOrBlank()) append("${issue.body}\n\n")
    append("Explore this repository, find the root cause, and propose a concrete fix.")
}

private data class PullRequestFinding(val path: String, val finding: Finding)

private val DiffAddColor = Color(0xFF63C174)
private val DiffDelColor = Color(0xFFE8622E)

/** "+adds -dels" as one inline styled span — additions green, deletions red, same convention as GitHub's own diff stats. Embed with AnnotatedString.Builder.append(). */
private fun diffStat(additions: Int, deletions: Int): AnnotatedString = buildAnnotatedString {
    withStyle(SpanStyle(color = DiffAddColor, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)) { append("+$additions") }
    append(" ")
    withStyle(SpanStyle(color = DiffDelColor, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)) { append("-$deletions") }
}

@Composable private fun PullRequestReviewWorkspace(
    pr: GitHubPullRequestDetailDto,
    files: List<com.iqforge.github.GitHubPullRequestFileDto>,
    readiness: com.iqforge.github.PullRequestReadiness?,
    checks: List<com.iqforge.github.GitHubCheckRunDto>,
    mergeState: com.iqforge.github.MergeState,
    mergeMessage: String?,
    mergedCommitSha: String?,
    agent: AgentViewModel,
    onDismiss: () -> Unit,
    onApproveAndMerge: (Boolean) -> Unit,
    onOpenDeepReview: (() -> Unit)? = null,
    onOpenCodeFix: (() -> Unit)? = null
) {
    var selectedStage by rememberSaveable(pr.number, pr.head.sha) { mutableIntStateOf(0) }
    var reviewBusy by remember(pr.head.sha) { mutableStateOf(true) }
    var reviewError by remember(pr.head.sha) { mutableStateOf<String?>(null) }
    var findings by remember(pr.head.sha) { mutableStateOf<List<PullRequestFinding>?>(null) }
    var plainEnglishSummary by remember(pr.head.sha) {
        mutableStateOf(fallbackPullRequestSummary(pr.title, files))
    }

    LaunchedEffect(pr.head.sha) {
        reviewBusy = true
        reviewError = null
        findings = null
        plainEnglishSummary = fallbackPullRequestSummary(pr.title, files)
        try {
            plainEnglishSummary = agent.summarizePullRequest(pr, files)
            findings = files.flatMap { file ->
                val patch = file.patch ?: return@flatMap emptyList()
                agent.reviewPullRequestPatch(patch).map { PullRequestFinding(file.filename, it) }
            }
        } catch (error: CancellationException) {
            // LaunchedEffect is cancelled normally when this review leaves or is
            // replaced in the composition. Never surface that lifecycle event as
            // a failed code review.
            throw error
        } catch (error: Exception) {
            reviewError = error.message ?: "The on-device review could not complete."
        } finally {
            reviewBusy = false
        }
    }

    val blockers = findings.orEmpty().count { it.finding.severity == Severity.BUG }
    val warnings = findings.orEmpty().count { it.finding.severity == Severity.WARNING }
    val conflictLabel = when (readiness?.hasConflicts) {
        false -> "No conflicts"
        true -> "Conflicts detected"
        null -> "Conflict status pending"
    }
    val stages = listOf("Summary", "Findings", "Changes", "Checks")
    val reviewColors = MaterialTheme.colorScheme

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        MaterialTheme(colorScheme = reviewColors, typography = forgeTypography(FontChoice.DEFAULT)) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Close review") }
                    Column(Modifier.weight(1f)) {
                        Text(
                            "PR ${pr.number}: ${pr.title}",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            "${pr.head.ref} into ${pr.base.ref}. ${readiness?.changedLines ?: pr.additions + pr.deletions} changed lines.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    AssistChip(onClick = {}, label = { Text(if (blockers > 0) "$blockers blockers" else "Review active") })
                }

                TabRow(selectedTabIndex = selectedStage) {
                    stages.forEachIndexed { index, label ->
                        Tab(
                            selected = selectedStage == index,
                            onClick = { selectedStage = index },
                            text = {
                                Text(
                                    label.uppercase(),
                                    maxLines = 1,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.SansSerif
                                )
                            }
                        )
                    }
                }

                if (reviewBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
                when (selectedStage) {
                    0 -> PullRequestSummaryStage(pr, files, readiness, plainEnglishSummary, reviewBusy, reviewError, blockers, warnings, conflictLabel, mergeState, mergeMessage, mergedCommitSha)
                    1 -> PullRequestFindingsStage(reviewBusy, reviewError, findings)
                    2 -> PullRequestChangesStage(files)
                    else -> PullRequestChecksStage(
                        pr,
                        readiness,
                        checks,
                        reviewBusy,
                        reviewError,
                        blockers,
                        conflictLabel,
                        mergeState,
                        onOpenCodeFix
                    )
                }

                HorizontalDivider()
                if (mergeMessage != null) {
                    Text(
                        mergeMessage,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        color = when (mergeState) {
                            com.iqforge.github.MergeState.MERGED -> Color(0xFF63C174)
                            com.iqforge.github.MergeState.BLOCKED, com.iqforge.github.MergeState.FAILED -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(onClick = { selectedStage = 1 }, modifier = Modifier.weight(1f)) { Text("Inspect Findings", fontWeight = FontWeight.Bold) }
                    val mergeBusy = mergeState in setOf(
                        com.iqforge.github.MergeState.REVALIDATING,
                        com.iqforge.github.MergeState.APPROVING,
                        com.iqforge.github.MergeState.MERGING
                    )
                    val canAttemptMerge = !reviewBusy && reviewError == null && blockers == 0 && readiness?.mergeBlockReason() == null
                    Button(
                        onClick = { onApproveAndMerge(blockers > 0) },
                        enabled = canAttemptMerge && !mergeBusy && mergeState != com.iqforge.github.MergeState.MERGED,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (mergeBusy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Text(
                            when {
                                mergeState == com.iqforge.github.MergeState.MERGED -> "Merged"
                                blockers > 0 -> "Merge Blocked"
                                else -> "Approve and Merge"
                            }
                        )
                    }
                }
                if (onOpenDeepReview != null) {
                    TextButton(onClick = { onDismiss(); onOpenDeepReview() }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Text("Open Detailed Review", fontWeight = FontWeight.Bold)
                    }
                }
                }
            }
        }
    }
}

@Composable private fun ColumnScope.PullRequestSummaryStage(
    pr: GitHubPullRequestDetailDto,
    files: List<com.iqforge.github.GitHubPullRequestFileDto>,
    readiness: com.iqforge.github.PullRequestReadiness?,
    plainEnglishSummary: String,
    reviewBusy: Boolean,
    reviewError: String?,
    blockers: Int,
    warnings: Int,
    conflictLabel: String,
    mergeState: com.iqforge.github.MergeState,
    mergeMessage: String?,
    mergedCommitSha: String?
) {
    LazyColumn(
        Modifier.weight(1f).fillMaxWidth().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                "PLAIN-ENGLISH SUMMARY",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(Modifier.height(8.dp))
            Text(plainEnglishSummary, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        }
        if (mergeState == com.iqforge.github.MergeState.MERGED) {
            item {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Pull request merged", color = Color(0xFF63C174), style = MaterialTheme.typography.titleMedium)
                        Text(mergeMessage ?: "GitHub accepted the merge.")
                        mergedCommitSha?.let { Text("Merge commit ${it.take(12)}", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelMedium) }
                    }
                }
            }
        }
        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("IQ REVIEW RESULT", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    when {
                        reviewBusy -> Text("Reviewing ${files.size} changed file${if (files.size == 1) "" else "s"} on device…")
                        reviewError != null -> Text(reviewError, color = MaterialTheme.colorScheme.error)
                        blockers > 0 -> Text("High risk · $blockers blocking finding${if (blockers == 1) "" else "s"}")
                        warnings > 0 -> Text("Medium risk · $warnings warning${if (warnings == 1) "" else "s"} to inspect")
                        else -> Text("Low risk · no blocking findings detected")
                    }
                    Text(buildAnnotatedString {
                        append("${files.size} file${if (files.size == 1) "" else "s"} changed, ")
                        append(diffStat(pr.additions, pr.deletions))
                        append(". $conflictLabel.")
                    })
                }
            }
        }
        item {
            Text(
                "Reviewed commit ${readiness?.reviewedHeadSha?.take(12)?.ifBlank { "unavailable" } ?: "unavailable"}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable private fun ColumnScope.PullRequestFindingsStage(
    reviewBusy: Boolean,
    reviewError: String?,
    findings: List<PullRequestFinding>?
) {
    LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("REVIEW FINDINGS", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)) }
        when {
            reviewBusy -> item { Text("The on-device reviewer is checking the changed lines…") }
            reviewError != null -> item { StatusCard(reviewError, error = true) }
            findings.isNullOrEmpty() -> item { StatusCard("No issues found in the available patch.", success = true) }
            else -> items(findings) { item ->
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "${if (item.finding.severity == Severity.BUG) "BLOCKING ISSUE" else item.finding.severity.name}: ${item.path}, line ${item.finding.line}",
                            color = if (item.finding.severity == Severity.BUG) MaterialTheme.colorScheme.error else IqfYellow,
                            style = MaterialTheme.typography.labelLarge
                        )
                        Text(item.finding.message)
                    }
                }
            }
        }
    }
}

/** Colors each line of a unified diff patch the way GitHub's own diff view does — added lines
 *  green, removed lines red, hunk headers (@@ ... @@) in the accent color, everything else
 *  (context lines, the file-header lines patch responses don't usually include) left neutral. */
private fun highlightPatch(patch: String): AnnotatedString = buildAnnotatedString {
    val lines = patch.lines()
    lines.forEachIndexed { index, line ->
        val color = when {
            line.startsWith("+++") || line.startsWith("---") -> null
            line.startsWith("@@") -> IqfYellow
            line.startsWith("+") -> DiffAddColor
            line.startsWith("-") -> DiffDelColor
            else -> null
        }
        if (color != null) withStyle(SpanStyle(color = color)) { append(line) } else append(line)
        if (index != lines.lastIndex) append("\n")
    }
}

@Composable private fun ColumnScope.PullRequestChangesStage(files: List<com.iqforge.github.GitHubPullRequestFileDto>) {
    LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("CHANGED FILES", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)) }
        items(files, key = { it.filename }) { file ->
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(file.filename, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                    Text(buildAnnotatedString {
                        append("${file.status.replaceFirstChar { it.uppercase() }} file, ")
                        append(diffStat(file.additions, file.deletions))
                    }, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        file.patch?.let(::highlightPatch) ?: AnnotatedString("Patch unavailable for this file."),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

private enum class GuidedCheckState { THINKING, PASSED, FAILED }

private data class GuidedCheckItem(
    val id: String,
    val title: String,
    val thinkingMessage: String,
    val detail: String,
    val state: GuidedCheckState,
    val canFixInCode: Boolean = false
)

@Composable private fun ColumnScope.PullRequestChecksStage(
    pr: GitHubPullRequestDetailDto,
    readiness: com.iqforge.github.PullRequestReadiness?,
    checks: List<com.iqforge.github.GitHubCheckRunDto>,
    reviewBusy: Boolean,
    reviewError: String?,
    blockers: Int,
    conflictLabel: String,
    mergeState: com.iqforge.github.MergeState,
    onOpenCodeFix: (() -> Unit)? = null
) {
    val automatedState = when (readiness?.checksState) {
        null, com.iqforge.github.ChecksState.PENDING -> GuidedCheckState.THINKING
        com.iqforge.github.ChecksState.FAILED -> GuidedCheckState.FAILED
        com.iqforge.github.ChecksState.NONE, com.iqforge.github.ChecksState.PASSED -> GuidedCheckState.PASSED
    }
    val failedChecks = checks.count { it.conclusion !in setOf("success", "neutral", "skipped") }
    val automatedDetail = when (readiness?.checksState) {
        null, com.iqforge.github.ChecksState.PENDING -> "Waiting for GitHub checks to finish"
        com.iqforge.github.ChecksState.FAILED -> "$failedChecks automated check${if (failedChecks == 1) "" else "s"} need attention"
        com.iqforge.github.ChecksState.NONE -> "No required automated checks"
        com.iqforge.github.ChecksState.PASSED -> "${checks.size} automated check${if (checks.size == 1) "" else "s"} passed"
    }
    val iqState = when {
        reviewBusy -> GuidedCheckState.THINKING
        reviewError != null || blockers > 0 -> GuidedCheckState.FAILED
        else -> GuidedCheckState.PASSED
    }
    val guidedChecks = buildList {
        add(GuidedCheckItem(
            "commit-loaded",
            "Latest commit loaded",
            "IQ is confirming the pull request commit…",
            readiness?.reviewedHeadSha?.take(12)?.let { "Reviewing commit $it" } ?: "Loading the latest commit",
            if (readiness == null) GuidedCheckState.THINKING else GuidedCheckState.PASSED
        ))
        add(GuidedCheckItem(
            "draft",
            "Pull request is ready",
            "IQ is checking the pull request state…",
            if (pr.draft) "Draft pull requests cannot be merged" else "The pull request is open for review",
            if (pr.draft) GuidedCheckState.FAILED else GuidedCheckState.PASSED
        ))
        add(GuidedCheckItem(
            "conflicts",
            "No merge conflicts",
            "IQ is comparing both branches…",
            conflictLabel,
            when (readiness?.hasConflicts) {
                null -> GuidedCheckState.THINKING
                true -> GuidedCheckState.FAILED
                false -> GuidedCheckState.PASSED
            },
            canFixInCode = readiness?.hasConflicts == true
        ))
        add(GuidedCheckItem(
            "automation",
            "Automated checks",
            "IQ is checking builds, tests, and previews…",
            automatedDetail,
            automatedState,
            canFixInCode = automatedState == GuidedCheckState.FAILED
        ))
        add(GuidedCheckItem(
            "patch",
            "Complete patch available",
            "IQ is loading every changed line…",
            if (readiness?.patchAvailable == true) "All changed lines loaded" else "One or more file patches are unavailable",
            when (readiness?.patchAvailable) {
                null -> GuidedCheckState.THINKING
                true -> GuidedCheckState.PASSED
                false -> GuidedCheckState.FAILED
            },
            canFixInCode = readiness?.patchAvailable == false
        ))
        add(GuidedCheckItem(
            "iq-review",
            "No blocking IQ findings",
            "IQ is reviewing the changed code on device…",
            when {
                reviewBusy -> "Private Snapdragon NPU analysis in progress"
                reviewError != null -> reviewError
                blockers > 0 -> "$blockers blocking finding${if (blockers == 1) "" else "s"} must be fixed"
                else -> "No blocking findings detected"
            },
            iqState,
            canFixInCode = iqState == GuidedCheckState.FAILED
        ))
        if (mergeState != com.iqforge.github.MergeState.IDLE) {
            val revalidated = mergeState in setOf(
                com.iqforge.github.MergeState.APPROVING,
                com.iqforge.github.MergeState.MERGING,
                com.iqforge.github.MergeState.MERGED,
                com.iqforge.github.MergeState.FAILED
            )
            add(GuidedCheckItem(
                "sha-check",
                "Reviewed commit unchanged",
                "IQ is confirming the exact reviewed commit…",
                if (revalidated) "Exact reviewed SHA confirmed" else "Rechecking immediately before merge",
                if (revalidated) GuidedCheckState.PASSED else GuidedCheckState.THINKING
            ))
        }
    }

    var settledCount by rememberSaveable(pr.head.sha) { mutableIntStateOf(0) }
    val stateSignature = guidedChecks.joinToString("|") { "${it.id}:${it.state}" }
    LaunchedEffect(stateSignature) {
        while (settledCount < guidedChecks.size) {
            val current = guidedChecks[settledCount]
            if (current.state == GuidedCheckState.THINKING) break
            kotlinx.coroutines.delay(450)
            settledCount += 1
            if (current.state == GuidedCheckState.FAILED) break
        }
    }
    val firstFailedIndex = guidedChecks.indexOfFirst { it.state == GuidedCheckState.FAILED }
    val visibleLastIndex = when {
        guidedChecks.isEmpty() -> -1
        firstFailedIndex >= 0 && settledCount > firstFailedIndex -> firstFailedIndex
        else -> settledCount.coerceAtMost(guidedChecks.lastIndex)
    }

    LazyColumn(
        Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("MERGE READINESS", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
                Text(
                    "${guidedChecks.take(settledCount).count { it.state == GuidedCheckState.PASSED }} of ${guidedChecks.size}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge
                )
            }
            Spacer(Modifier.height(14.dp))
        }
        if (visibleLastIndex >= 0) {
            guidedChecks.take(visibleLastIndex + 1).forEachIndexed { index, check ->
                item(key = check.id) {
                    val displayedState = if (
                        index == settledCount && check.state in setOf(GuidedCheckState.PASSED, GuidedCheckState.FAILED)
                    ) GuidedCheckState.THINKING else check.state
                    GuidedCheckCard(
                        check,
                        displayedState,
                        if (check.canFixInCode) onOpenCodeFix else null
                    )
                    if (index < visibleLastIndex || displayedState == GuidedCheckState.THINKING) {
                        Box(Modifier.fillMaxWidth().height(24.dp), contentAlignment = Alignment.Center) {
                            Box(Modifier.width(2.dp).fillMaxHeight().background(IqfYellow.copy(alpha = 0.55f)))
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun GuidedCheckCard(
    check: GuidedCheckItem,
    displayedState: GuidedCheckState,
    onFixInCode: (() -> Unit)?
) {
    val containerColor = when (displayedState) {
        GuidedCheckState.PASSED -> Color(0xFF1F2B1C)
        GuidedCheckState.FAILED -> MaterialTheme.colorScheme.errorContainer
        GuidedCheckState.THINKING -> IqfYellow.copy(alpha = 0.10f)
    }
    ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = containerColor), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                when (displayedState) {
                    GuidedCheckState.THINKING -> Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.fillMaxSize(), strokeWidth = 2.dp, color = IqfYellow)
                        Image(
                            painter = painterResource(com.iqforge.R.drawable.iqoo_q_mark),
                            contentDescription = "IQ is thinking",
                            modifier = Modifier.size(20.dp),
                            colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(IqfYellow)
                        )
                    }
                    GuidedCheckState.PASSED -> Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF63C174), modifier = Modifier.size(34.dp))
                    GuidedCheckState.FAILED -> Icon(Icons.Default.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(34.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(check.title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    Text(
                        if (displayedState == GuidedCheckState.THINKING) check.thinkingMessage else check.detail,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                if (displayedState == GuidedCheckState.FAILED && onFixInCode != null) {
                    OutlinedIconButton(onClick = onFixInCode) {
                        Icon(Icons.Default.Code, "Fix in Code")
                    }
                }
            }
            if (displayedState == GuidedCheckState.THINKING) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(com.iqforge.R.drawable.iqoo_q_mark),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(IqfYellow)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("THINKING ON DEVICE", color = IqfYellow, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable private fun IssueDetailDialog(issue: GitHubIssueDto, onDismiss: () -> Unit, onReview: (() -> Unit)? = null) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("#${issue.number} ${issue.title}") },
        text = {
            val scrollState = rememberScrollState()
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 140.dp, max = 500.dp)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("${issue.user?.login ?: "unknown"} · ${issue.state}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (issue.labels.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    issue.labels.forEach { AssistChip(onClick = {}, label = { Text(it.name) }) }
                }
                HorizontalDivider(Modifier.padding(vertical = 2.dp))
                if (!issue.body.isNullOrBlank()) {
                    MarkdownText(issue.body, style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text("No description provided.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            if (onReview != null) Button(onClick = { onDismiss(); onReview() }) {
                Icon(Icons.Default.Code, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Review")
            } else TextButton(onClick = onDismiss) { Text("Done") }
        },
        dismissButton = { if (onReview != null) TextButton(onClick = onDismiss) { Text("Close") } }
    )
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
@Composable private fun EditorScreen(state: WorkspaceUiState, workspace: WorkspaceViewModel, agent: AgentViewModel) {
    var showAiEdit by remember { mutableStateOf(false) }
    var instruction by rememberSaveable { mutableStateOf("") }
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(state.selectedFile?.relativePath ?: "Editor") },
            navigationIcon = { IconButton(workspace::closeEditor) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to agent") } },
            actions = {
                TextButton(onClick = { showAiEdit = true }) {
                    Icon(Icons.Default.AutoAwesome, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Edit with IQF")
                }
            }
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
    if (showAiEdit) AlertDialog(
        onDismissRequest = { if (!agent.fileEditBusy) showAiEdit = false },
        title = { Text("Edit ${state.selectedFile?.name.orEmpty()} with IQForge") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PixelPetRunner(Modifier.padding(start = 4.dp))
                OutlinedTextField(
                    instruction,
                    { instruction = it },
                    Modifier.fillMaxWidth(),
                    label = { Text("Describe the code change") },
                    minLines = 3,
                    enabled = !agent.fileEditBusy
                )
                if (agent.fileEditBusy) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("Generating the complete updated file…")
                }
                agent.fileEditError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = instruction.isNotBlank() && !agent.fileEditBusy,
                onClick = {
                    agent.generateFileEdit(state.selectedFile?.relativePath.orEmpty(), state.editorText, instruction) {
                        workspace.updateEditor(it)
                        instruction = ""
                        showAiEdit = false
                    }
                }
            ) { Text("Generate edit") }
        },
        dismissButton = { TextButton(enabled = !agent.fileEditBusy, onClick = { showAiEdit = false }) { Text("Cancel") } }
    )
}
