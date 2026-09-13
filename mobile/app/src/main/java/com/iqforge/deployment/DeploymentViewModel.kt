package com.iqforge.deployment

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.iqforge.git.JGitRepoManager
import com.iqforge.git.Repo
import com.iqforge.github.GitHubApiClient
import com.iqforge.github.GitHubJobDto
import com.iqforge.github.GitHubRepoRef
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git

enum class DeploymentStage(val label: String) {
    PREFLIGHT("Preflight"), BUILD("Build"), TEST("Test"), DEPLOY("Deploy"), VERIFY("Verify")
}

enum class DeploymentCheckState { PENDING, RUNNING, PASSED, ACTION_REQUIRED }

/** Order the preflight checks resolve in — "repo" and "approval" aren't evidence-driven so they're excluded. */
private val SEQUENCED_PREFLIGHT_IDS = listOf("commit", "actions", "impact", "build-config", "dataform")

/** The workflow file this feature pushes/expects at .github/workflows/ in the target repo. */
private const val WORKFLOW_FILE = "deploy.yml"

/** Real step names from deploy.yml, used as a skeleton before a job's live step data arrives. */
private val STAGE_STEP_NAMES: Map<DeploymentStage, List<String>> = mapOf(
    DeploymentStage.BUILD to listOf("Checkout", "Validate site structure", "Package site", "Upload Pages artifact"),
    DeploymentStage.TEST to listOf("Checkout", "Set up Python", "Install html5validator", "Validate HTML5"),
    DeploymentStage.DEPLOY to listOf("Deploy to GitHub Pages"),
    DeploymentStage.VERIFY to listOf("Verify live site responds")
)

/** GitHub injects these into every job automatically — not part of the workflow's own real steps. */
private val NON_WORKFLOW_STEP_NAMES = setOf("Set up job", "Complete job")

data class DeploymentCheck(
    val id: String,
    val title: String,
    val detail: String,
    val state: DeploymentCheckState,
    val codeAction: Boolean = state == DeploymentCheckState.ACTION_REQUIRED
)

data class DeploymentUiState(
    val repository: Repo? = null,
    val repoRef: GitHubRepoRef? = null,
    val commitSha: String = "",
    val environment: String = "Staging",
    val reviewStarted: Boolean = false,
    val activeStage: DeploymentStage = DeploymentStage.PREFLIGHT,
    val preflightChecks: List<DeploymentCheck> = emptyList(),
    val runId: Long? = null,
    val runHtmlUrl: String? = null,
    val jobsByStage: Map<DeploymentStage, GitHubJobDto> = emptyMap(),
    val liveUrl: String? = null,
    val failureEvidence: String? = null,
    val busy: Boolean = false,
    val error: String? = null,
    val planApproved: Boolean = false
)

class DeploymentViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = application.getSharedPreferences("iqforge_workspace", Context.MODE_PRIVATE)
    private val client = GitHubApiClient(tokenProvider = { preferences.getString("github_token", "") })
    private val repoManager = JGitRepoManager(application.filesDir.resolve("repositories"))

    var state by mutableStateOf(DeploymentUiState())
        private set

    fun selectEnvironment(value: String) {
        if (!state.reviewStarted) state = state.copy(environment = value)
    }

    fun startReview(repo: Repo) {
        viewModelScope.launch {
            state = DeploymentUiState(
                repository = repo,
                environment = state.environment,
                reviewStarted = true,
                busy = true,
                preflightChecks = pendingPreflight(repo)
            )
            try {
                val ref = withContext(Dispatchers.IO) { GitHubRepoRef.parse(repoManager.remoteUrl(repo)) }
                val evidence = withContext(Dispatchers.IO) { inspectRepository(repo.root) }
                val actionsReachable = ref != null &&
                    runCatching { client.listWorkflowRuns(ref.owner, ref.repo, WORKFLOW_FILE, perPage = 1) }.isSuccess
                val resolved = completedPreflight(repo, ref, evidence, actionsReachable)

                // Reveal each check's real result one at a time, in order, instead of flipping
                // every box the instant evidence is gathered — matches the Review checklist's
                // box-by-box "thinking, then a tick" pacing instead of an instant batch update.
                for (id in SEQUENCED_PREFLIGHT_IDS) {
                    val next = resolved.firstOrNull { it.id == id } ?: continue
                    delay(450)
                    state = state.copy(
                        preflightChecks = state.preflightChecks.map { if (it.id == id) next else it }
                    )
                }
                state = state.copy(
                    repoRef = ref,
                    commitSha = evidence.commitSha,
                    busy = false,
                    preflightChecks = resolved,
                    error = if (ref == null) "This repository isn't linked to a public GitHub remote." else null
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                state = state.copy(busy = false, error = error.message ?: "Preflight inspection failed.")
            }
        }
    }

    fun approvePlan() {
        state = state.copy(
            planApproved = true,
            preflightChecks = state.preflightChecks.map {
                if (it.id == "approval") it.copy(
                    detail = "${state.environment} deployment plan approved on this device",
                    state = DeploymentCheckState.PASSED
                ) else it
            }
        )
    }

    /**
     * Demo/manual escape hatch: acknowledges every failing preflight check and approves the plan
     * without waiting for evidence to change. Each overridden check stays visibly marked as
     * manually overridden rather than pretending the evidence changed.
     */
    fun overrideAndContinue() {
        if (state.activeStage != DeploymentStage.PREFLIGHT) return
        state = state.copy(
            planApproved = true,
            error = null,
            preflightChecks = state.preflightChecks.map { check ->
                when {
                    check.state == DeploymentCheckState.ACTION_REQUIRED -> check.copy(
                        detail = "${check.detail} — manually overridden",
                        state = DeploymentCheckState.PASSED,
                        codeAction = false
                    )
                    check.id == "approval" -> check.copy(
                        detail = "${state.environment} deployment plan approved on this device",
                        state = DeploymentCheckState.PASSED
                    )
                    else -> check
                }
            }
        )
        startDeployment()
    }

    fun openStage(stage: DeploymentStage) {
        if (stage.ordinal <= highestUnlockedStage()) state = state.copy(activeStage = stage)
    }

    /** The one explicit trigger point: dispatches the real workflow, then watches it run for real. */
    fun startDeployment() {
        val ref = state.repoRef ?: return
        if (!currentStageComplete()) return
        state = state.copy(activeStage = DeploymentStage.BUILD, busy = true, error = null, failureEvidence = null)
        viewModelScope.launch {
            try {
                // Captured BEFORE dispatching — GitHub can list the new run within the same
                // second as the 204 response, so capturing "before" after the dispatch risks
                // it already being the new run, which would make every "did this change?"
                // check below compare a value against itself and never detect the real run.
                val before = runCatching { client.listWorkflowRuns(ref.owner, ref.repo, WORKFLOW_FILE, perPage = 1) }
                    .getOrDefault(emptyList()).firstOrNull()?.id

                client.dispatchWorkflow(
                    ref.owner, ref.repo, WORKFLOW_FILE, ref = "main",
                    inputs = mapOf(
                        "commit_sha" to state.commitSha,
                        "message" to "${state.environment} deployment from iQForge"
                    )
                )
                // workflow_dispatch returns no run id, so poll the workflow's recent runs until
                // one appears that didn't exist a moment ago.
                var runId: Long? = null
                for (attempt in 1..15) {
                    delay(1000)
                    val latest = client.listWorkflowRuns(ref.owner, ref.repo, WORKFLOW_FILE, perPage = 1).firstOrNull()
                    if (latest != null && latest.id != before) {
                        runId = latest.id
                        state = state.copy(runHtmlUrl = latest.htmlUrl)
                        break
                    }
                }
                val id = runId ?: error("GitHub Actions did not start a new run for this dispatch.")
                state = state.copy(runId = id)
                watchRun(ref, id)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                state = state.copy(busy = false, error = error.message ?: "Could not trigger the deployment workflow.")
            }
        }
    }

    /** Re-runs only the failed jobs on the same commit — a real GitHub Actions re-run, not a fresh dispatch. */
    fun retryFailedJob() {
        val ref = state.repoRef ?: return
        val runId = state.runId ?: return
        viewModelScope.launch {
            state = state.copy(busy = true, error = null, failureEvidence = null)
            try {
                client.rerunFailedJobs(ref.owner, ref.repo, runId)
                delay(1500)
                watchRun(ref, runId)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                state = state.copy(busy = false, error = error.message ?: "Could not retry the failed job.")
            }
        }
    }

    fun retry() {
        when (state.activeStage) {
            DeploymentStage.PREFLIGHT -> state.repository?.let { startReview(it) }
            else -> if (state.runId != null) retryFailedJob() else startDeployment()
        }
    }

    fun reset() {
        state = DeploymentUiState(environment = state.environment)
    }

    fun checksFor(stage: DeploymentStage): List<DeploymentCheck> {
        if (stage == DeploymentStage.PREFLIGHT) return state.preflightChecks
        val job = state.jobsByStage[stage]
        val knownSteps = STAGE_STEP_NAMES[stage].orEmpty()
        if (job == null) {
            return knownSteps.map { title ->
                DeploymentCheck("${stage.name.lowercase()}-$title", title, "Waiting for this stage", DeploymentCheckState.PENDING)
            }
        }
        val relevantSteps = job.steps.filter { it.name !in NON_WORKFLOW_STEP_NAMES && !it.name.startsWith("Post ") }
        return relevantSteps.map { step ->
            val checkState = when {
                step.status != "completed" -> DeploymentCheckState.RUNNING
                step.conclusion == "success" -> DeploymentCheckState.PASSED
                step.conclusion == "skipped" -> DeploymentCheckState.PENDING
                step.conclusion == "failure" -> DeploymentCheckState.ACTION_REQUIRED
                else -> DeploymentCheckState.PENDING
            }
            DeploymentCheck(
                id = "${stage.name.lowercase()}-${step.number}",
                title = step.name,
                detail = when (checkState) {
                    DeploymentCheckState.PASSED -> "Completed on GitHub Actions"
                    DeploymentCheckState.RUNNING -> "Running on GitHub Actions…"
                    DeploymentCheckState.ACTION_REQUIRED -> state.failureEvidence ?: "This step failed — see GitHub Actions logs"
                    DeploymentCheckState.PENDING -> "Waiting for this step"
                },
                state = checkState
            )
        }
    }

    fun currentStageComplete(): Boolean {
        val checks = checksFor(state.activeStage)
        return checks.isNotEmpty() && checks.all { it.state == DeploymentCheckState.PASSED }
    }

    fun highestUnlockedStage(): Int {
        if (!state.reviewStarted) return 0
        var unlocked = 0
        DeploymentStage.entries.dropLast(1).forEach { stage ->
            if (checksFor(stage).all { it.state == DeploymentCheckState.PASSED }) unlocked = stage.ordinal + 1
            else return unlocked
        }
        return unlocked
    }

    /** Polls the real run's jobs every few seconds, auto-advancing the active stage tab as jobs start. */
    private suspend fun watchRun(ref: GitHubRepoRef, runId: Long) {
        while (true) {
            val jobs = client.listRunJobs(ref.owner, ref.repo, runId)
            val byStage = jobs.mapNotNull { job ->
                DeploymentStage.entries.firstOrNull { it.name.equals(job.name, ignoreCase = true) }?.let { it to job }
            }.toMap()
            val runningStage = byStage.entries.firstOrNull { (_, job) -> job.status != "completed" }?.key
            val latestKnownStage = byStage.keys.maxByOrNull { it.ordinal }
            val displayStage = runningStage ?: latestKnownStage
            state = state.copy(
                jobsByStage = byStage,
                activeStage = if (displayStage != null && displayStage.ordinal > state.activeStage.ordinal) displayStage else state.activeStage
            )

            val failedJob = byStage.values.firstOrNull { it.conclusion == "failure" }
            if (failedJob != null) {
                val failedStep = failedJob.steps.firstOrNull { it.conclusion == "failure" }
                val logs = runCatching { client.getJobLogs(ref.owner, ref.repo, failedJob.id) }.getOrDefault("")
                state = state.copy(
                    busy = false,
                    failureEvidence = extractFailureExcerpt(logs),
                    error = "${failedJob.name} failed at \"${failedStep?.name ?: "a step"}\"."
                )
                return
            }

            val allJobsAccountedFor = byStage.size == DeploymentStage.entries.size - 1
            if (allJobsAccountedFor && byStage.values.all { it.status == "completed" }) {
                state = state.copy(
                    busy = false,
                    liveUrl = "https://${ref.owner.lowercase()}.github.io/${ref.repo}/"
                )
                return
            }
            delay(3000)
        }
    }

    /** Pulls the lines around the actual failure out of a full GitHub Actions log blob, stripping timestamps. */
    private fun extractFailureExcerpt(rawLogs: String): String {
        val errorLines = rawLogs.lines()
            .filter { it.contains("error", ignoreCase = true) || it.contains("Error:") }
            .map { line -> line.substringAfter("Z ").ifBlank { line } }
        return errorLines.takeLast(12).joinToString("\n").ifBlank {
            "The step failed. See the full log on GitHub Actions for details."
        }
    }

    private data class RepositoryEvidence(
        val commitSha: String,
        val changedFiles: Int,
        val buildDetected: Boolean,
        val dataformDetected: Boolean
    )

    private fun inspectRepository(root: File): RepositoryEvidence {
        val git = Git.open(root)
        git.use {
            val commit = it.repository.resolve("HEAD")?.name.orEmpty()
            val status = it.status().call()
            val changed = status.added.size + status.changed.size + status.modified.size +
                status.removed.size + status.missing.size + status.untracked.size
            val hasWorkflows = root.resolve(".github/workflows").let { dir ->
                dir.isDirectory && dir.listFiles()?.any { f -> f.extension in setOf("yml", "yaml") } == true
            }
            val buildDetected = listOf(
                "package.json", "build.gradle", "build.gradle.kts", "settings.gradle.kts",
                "pom.xml", "requirements.txt", "pyproject.toml", "Cargo.toml", "Dockerfile", "index.html"
            ).any { name -> root.resolve(name).exists() } || root.resolve("mobile/build.gradle.kts").exists() || hasWorkflows
            val dataform = root.resolve("workflow_settings.yaml").exists() ||
                root.resolve("dataform.json").exists() || root.resolve("definitions").isDirectory
            return RepositoryEvidence(commit, changed, buildDetected, dataform)
        }
    }

    private fun pendingPreflight(repo: Repo) = listOf(
        DeploymentCheck("repo", "Repository selected", repo.name, DeploymentCheckState.PASSED),
        DeploymentCheck("commit", "Commit SHA resolved", "Inspecting HEAD", DeploymentCheckState.RUNNING),
        DeploymentCheck("actions", "GitHub Actions reachable", "Checking workflow access", DeploymentCheckState.RUNNING),
        DeploymentCheck("impact", "Changed files analyzed", "Inspecting repository", DeploymentCheckState.RUNNING),
        DeploymentCheck("build-config", "Build configuration detected", "Inspecting toolchain", DeploymentCheckState.RUNNING),
        DeploymentCheck("dataform", "Dataform impact checked", "Detecting SQLX workflow", DeploymentCheckState.RUNNING),
        DeploymentCheck("approval", "Deployment plan approved", "Review the evidence below", DeploymentCheckState.PENDING)
    )

    private fun completedPreflight(repo: Repo, ref: GitHubRepoRef?, evidence: RepositoryEvidence, actionsReachable: Boolean) = listOf(
        DeploymentCheck("repo", "Repository selected", repo.name, DeploymentCheckState.PASSED),
        DeploymentCheck(
            "commit", "Commit SHA resolved", evidence.commitSha.take(12).ifBlank { "HEAD unavailable" },
            if (evidence.commitSha.isNotBlank()) DeploymentCheckState.PASSED else DeploymentCheckState.ACTION_REQUIRED
        ),
        DeploymentCheck(
            "actions", "GitHub Actions reachable",
            when {
                ref == null -> "This project has no public GitHub remote"
                actionsReachable -> "$WORKFLOW_FILE is present and reachable on ${ref.fullName}"
                else -> "Could not reach the $WORKFLOW_FILE workflow on ${ref.fullName} — check the saved GitHub token"
            },
            if (ref != null && actionsReachable) DeploymentCheckState.PASSED else DeploymentCheckState.ACTION_REQUIRED
        ),
        DeploymentCheck(
            "impact", "Changed files analyzed", "${evidence.changedFiles} working-tree change(s) detected",
            DeploymentCheckState.PASSED
        ),
        DeploymentCheck(
            "build-config", "Build configuration detected",
            if (evidence.buildDetected) "A supported toolchain configuration was found" else "No supported build configuration was found",
            if (evidence.buildDetected) DeploymentCheckState.PASSED else DeploymentCheckState.ACTION_REQUIRED
        ),
        DeploymentCheck(
            "dataform", "Dataform impact checked",
            if (evidence.dataformDetected) "Dataform project detected; compilation and assertions will run in Test" else "Not a Dataform repository",
            DeploymentCheckState.PASSED
        ),
        DeploymentCheck("approval", "Deployment plan approved", "Review and approve this ${state.environment} plan", DeploymentCheckState.PENDING)
    )
}
