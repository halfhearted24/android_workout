package com.strongppl.tracker

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.strongppl.tracker.domain.ExerciseEvidence
import com.strongppl.tracker.domain.WorkoutPlan

@Composable
fun EvidenceScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("Exercise guides & evidence", style = MaterialTheme.typography.headlineMedium)
            Text("Research supports training principles; it does not prove every exact exercise variation is best. Coaching cues are labelled separately.", style = MaterialTheme.typography.bodySmall)
        }
        items(WorkoutPlan.days.flatMap { it.exercises }.distinctBy { it.id }, key = { it.id }) { exercise ->
            val guide = ExerciseEvidence.guide(exercise.id)
            var expanded by remember(exercise.id) { mutableStateOf(false) }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(exercise.name, style = MaterialTheme.typography.titleMedium)
                    Text("${exercise.sets} sets · ${exercise.minReps}–${exercise.maxReps} reps")
                    TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Hide guide" else "Open technique & science guide") }
                    if (expanded && guide != null) {
                        HorizontalDivider()
                        Text("Target muscles", style = MaterialTheme.typography.titleSmall)
                        Text(guide.muscles)
                        Text("Equipment & setup", style = MaterialTheme.typography.titleSmall)
                        Text(guide.setup)
                        Text("Execution", style = MaterialTheme.typography.titleSmall)
                        Text(guide.execution)
                        Text("Breathing", style = MaterialTheme.typography.titleSmall)
                        Text(guide.breathing)
                        Text("Core engagement", style = MaterialTheme.typography.titleSmall)
                        Text(guide.bracing)
                        Text("Common mistakes", style = MaterialTheme.typography.titleSmall)
                        Text(guide.mistakes)
                        Text("Safety", style = MaterialTheme.typography.titleSmall)
                        Text(guide.safety)
                        Text("Why it is included", style = MaterialTheme.typography.titleSmall)
                        Text(guide.rationale)
                        Text("Evidence classification: ${guide.evidenceLevel}", style = MaterialTheme.typography.labelMedium)
                        guide.referenceIds.distinct().forEach { refId ->
                            val ref = ExerciseEvidence.reference(refId)
                            if (ref != null) {
                                Text("${ref.authors} (${ref.year}). ${ref.title}. ${ref.journal}. DOI: ${ref.doi}",
                                    style = MaterialTheme.typography.bodySmall)
                                Text("Open paper / source", color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.clickable {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ref.url)))
                                    })
                                Text(ref.takeaway, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
        item {
            Text("Evidence limitations", style = MaterialTheme.typography.titleMedium)
            Text("A cited guideline or review supports the general principle shown. Individual exercise mechanics, band anchor placement, bench angles and symptom modifications include practical coaching judgement. Persistent or worsening shoulder pain needs assessment by a qualified clinician.")
        }
    }
}
