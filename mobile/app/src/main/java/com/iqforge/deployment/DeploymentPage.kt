package com.iqforge.deployment

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.iqforge.git.Repo

private val SuccessGreen = Color(0xFF63C174)

@Composable
fun DeploymentPage(
    repositories: List<Repo>,
    currentRepository: Repo?,
    deployment: DeploymentViewModel,
    onOpenCode: (DeploymentCheck) -> Unit,
    onOpenDetailedReview: () -> Unit
) {
    val state = deployment.state
    var selectedName by rememberSaveable(currentRepository?.name, repositories.size) {
        mutableStateOf(currentRepository?.name ?: repositories.firstOrNull()?.name.orEmpty())
    }
    val selectedRepo = repositories.firstOrNull { it.name == selectedName } ?: currentRepository

    if (state.reviewStarted) {
        DeploymentWorkspace(
            deployment = deployment,
            onBack = deployment::reset,
            onOpenCode = onOpenCode,
            onOpenDetailedReview = onOpenDetailedReview
        )
        return
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Deployment", style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(top = 14.dp))
            Text(
                "Review the exact commit, complete every required check, then deploy and verify it from your phone — for real, on GitHub Actions.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        item { SectionLabel("REPOSITORY") }
        if (repositories.isEmpty() && currentRepository == null) {
            item {
                DeploymentNotice("Clone or open a repository in Code before starting a deployment review.", error = true)
            }
        } else {
            items(repositories.ifEmpty { listOfNotNull(currentRepository) }, key = { it.root.absolutePath }) { repo ->
                val selected = repo.name == selectedRepo?.name
                ElevatedCard(onClick = { selectedName = repo.name }, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (selected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                            null,
                            tint = if (selected) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(repo.name, fontWeight = FontWeight.Bold)
                            Text(repo.root.absolutePath, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
        item {
            SectionLabel("TARGET ENVIRONMENT")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                listOf("Demo", "Staging", "Production").forEach { environment ->
                    if (state.environment == environment) {
                        Button(onClick = { deployment.selectEnvironment(environment) }) { Text(environment) }
                    } else {
                        OutlinedButton(onClick = { deployment.selectEnvironment(environment) }) { Text(environment) }
                    }
                }
            }
        }
        item {
            DeploymentNotice(
                "Starting a deployment review only runs safety checks. Nothing is released until the Deploy stage completes on GitHub Actions."
            )
        }
        item {
            Button(
                onClick = { selectedRepo?.let { deployment.startReview(it) } },
                enabled = selectedRepo != null,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Start Deployment Review", fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun DeploymentWorkspace(
    deployment: DeploymentViewModel,
    onBack: () -> Unit,
    onOpenCode: (DeploymentCheck) -> Unit,
    onOpenDetailedReview: () -> Unit
) {
    val state = deployment.state
    val stage = state.activeStage
    val checks = deployment.checksFor(stage)
    val unlocked = deployment.highestUnlockedStage()
    val context = LocalContext.current

    val completedStages = state.jobsByStage.values.count { it.status == "completed" && it.conclusion == "success" }
    val progress = if (state.liveUrl != null) 100 else (completedStages * 100) / 4

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Close deployment review") }
            Column(Modifier.weight(1f)) {
                Text(
                    state.repository?.name ?: "Deployment",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    "${state.environment} · ${state.commitSha.take(12).ifBlank { "resolving HEAD" }}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            AssistChip(
                onClick = {},
                label = { Text(if (state.liveUrl != null) "Live" else stage.label) }
            )
        }

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DeploymentStage.entries.forEach { item ->
                val enabled = item.ordinal <= unlocked
                if (item == stage) {
                    Button(onClick = { deployment.openStage(item) }, enabled = enabled) { Text(item.label) }
                } else {
                    OutlinedButton(onClick = { deployment.openStage(item) }, enabled = enabled) {
                        if (!enabled) {
                            Icon(Icons.Default.Lock, null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(item.label)
                    }
                }
            }
        }

        LinearProgressIndicator(
            progress = { progress.coerceIn(0, 100) / 100f },
            modifier = Modifier.fillMaxWidth()
        )

        LazyColumn(
            Modifier.weight(1f).fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text("${stage.label.uppercase()} CHECKLIST", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                Text(
                    if (stage == DeploymentStage.PREFLIGHT) "Checks receive a tick only after evidence confirms completion."
                    else "Live from GitHub Actions — each step ticks as the real job reports it.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            items(checks, key = { it.id }) { check ->
                DeploymentCheckRow(check, onOpenCode)
            }
            state.error?.let { error ->
                item { DeploymentNotice(error, error = true) }
            }
            state.runHtmlUrl?.let { url ->
                item {
                    OutlinedButton(onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.OpenInNew, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("View run on GitHub")
                    }
                }
            }
            if (!state.failureEvidence.isNullOrBlank()) {
                item {
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("REAL FAILURE LOG", fontWeight = FontWeight.Bold)
                            Text(
                                state.failureEvidence,
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }

        val hasActionRequired = checks.any { it.state == DeploymentCheckState.ACTION_REQUIRED }
        HorizontalDivider()
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onOpenDetailedReview, modifier = Modifier.weight(1f)) {
                    Text("Detailed Review", fontWeight = FontWeight.Bold)
                }
                when {
                    state.error != null || hasActionRequired -> Button(
                        onClick = { deployment.retry() },
                        enabled = !state.busy,
                        modifier = Modifier.weight(1f)
                    ) { Text(if (stage == DeploymentStage.PREFLIGHT) "Rerun Checks" else "Retry Failed Job") }
                    stage == DeploymentStage.PREFLIGHT && !state.planApproved -> Button(
                        onClick = deployment::approvePlan,
                        enabled = !state.busy && checks.none { it.state == DeploymentCheckState.ACTION_REQUIRED },
                        modifier = Modifier.weight(1f)
                    ) { Text("Approve Plan") }
                    stage == DeploymentStage.PREFLIGHT && state.planApproved -> Button(
                        onClick = deployment::startDeployment,
                        enabled = !state.busy,
                        modifier = Modifier.weight(1f)
                    ) { Text("Continue to Build") }
                    stage == DeploymentStage.VERIFY && state.liveUrl != null -> Button(
                        onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(state.liveUrl)))
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.OpenInNew, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Open Live Site")
                    }
                    else -> Button(onClick = {}, enabled = false, modifier = Modifier.weight(1f)) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Running on GitHub Actions…")
                    }
                }
            }
            // Demo override: a failing preflight check otherwise blocks the whole pipeline. This
            // lets the operator explicitly trigger the real Build/Test/Deploy/Verify run anyway,
            // with every overridden check still visibly marked.
            if (stage == DeploymentStage.PREFLIGHT && hasActionRequired) {
                OutlinedButton(
                    onClick = deployment::overrideAndContinue,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Continue to Build Anyway", fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun DeploymentCheckRow(check: DeploymentCheck, onOpenCode: (DeploymentCheck) -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            when (check.state) {
                DeploymentCheckState.PASSED -> Icon(Icons.Default.CheckCircle, null, tint = SuccessGreen)
                DeploymentCheckState.RUNNING -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                DeploymentCheckState.ACTION_REQUIRED -> Icon(Icons.Default.ErrorOutline, null, tint = MaterialTheme.colorScheme.error)
                DeploymentCheckState.PENDING -> Icon(Icons.Default.RadioButtonUnchecked, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(check.title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                Text(check.detail, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
            }
            if (check.state == DeploymentCheckState.ACTION_REQUIRED && check.codeAction) {
                IconButton(onClick = { onOpenCode(check) }) {
                    Icon(Icons.Default.Code, "Fix ${check.title} in Code")
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
}

@Composable
private fun DeploymentNotice(text: String, error: Boolean = false) {
    Surface(
        color = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text,
            color = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(14.dp)
        )
    }
}
