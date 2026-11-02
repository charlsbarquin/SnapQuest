package com.charlsemilbarquin.frameandfact.feature.reports

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.google.gson.GsonBuilder
import com.charlsemilbarquin.frameandfact.data.history.QuestionIssueType
import com.charlsemilbarquin.frameandfact.data.history.QuestionReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.charlsemilbarquin.frameandfact.ui.theme.FrameFactSpacing
import com.charlsemilbarquin.frameandfact.ui.components.SnapQuestSectionHeader

@Composable
fun QuestionReportsScreen(
    questionId: String?,
    reports: List<QuestionReport>,
    onSave: suspend (QuestionIssueType, String?) -> Unit,
    onDelete: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedIssue by remember(questionId) { mutableStateOf(QuestionIssueType.INCORRECT_ANSWER_KEY) }
    var note by remember(questionId) { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val json = GsonBuilder().setPrettyPrinting().create().toJson(reports)
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(json) }
                        ?: error("Could not open the selected file")
                }
                status = "Reports exported."
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                status = "Export failed. Choose another destination and try again."
            }
        }
    }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(FrameFactSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(FrameFactSpacing.standard)) {
        Text("Question reports", style = MaterialTheme.typography.headlineMedium)
        if (questionId != null) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SnapQuestSectionHeader("Report this question")
                    Text("Question ID: $questionId", style = MaterialTheme.typography.bodyMedium)
                    Column(Modifier.selectableGroup()) {
                        QuestionIssueType.entries.forEach { issue ->
                            Row(Modifier.fillMaxWidth().selectable(
                                selected = selectedIssue == issue,
                                role = Role.RadioButton,
                                onClick = { selectedIssue = issue }
                            ), verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = selectedIssue == issue, onClick = { selectedIssue = issue })
                                Text(issue.label(), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it.take(500) },
                        label = { Text("Optional note") },
                        supportingText = { Text("${note.length}/500") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(onClick = { scope.launch {
                        status = "Saving report…"
                        try {
                            onSave(selectedIssue, note)
                            status = "Report saved on this device."
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            status = "Report could not be saved. Please try again."
                        }
                    } },
                        modifier = Modifier.fillMaxWidth()) { Text("Save report") }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            SnapQuestSectionHeader("Saved reports (${reports.size})")
            TextButton(onClick = { exportLauncher.launch("frame_fact_question_reports.json") }, enabled = reports.isNotEmpty()) {
                Text("Export JSON")
            }
        }
        reports.forEach { report ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${report.issueType.label()} · ${report.questionId}", style = MaterialTheme.typography.titleSmall)
                    report.note?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    TextButton(onClick = { onDelete(report.reportId) }) { Text("Delete report") }
                }
            }
        }
        status?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
    }
}

private fun QuestionIssueType.label(): String = when (this) {
    QuestionIssueType.INCORRECT_ANSWER_KEY -> "Incorrect answer key"
    QuestionIssueType.AMBIGUOUS_QUESTION -> "Ambiguous question"
    QuestionIssueType.TYPOGRAPHICAL_ERROR -> "Typographical error"
    QuestionIssueType.DUPLICATE_QUESTION -> "Duplicate question"
    QuestionIssueType.INCORRECT_DIFFICULTY -> "Incorrect difficulty"
    QuestionIssueType.OTHER -> "Other content issue"
}
