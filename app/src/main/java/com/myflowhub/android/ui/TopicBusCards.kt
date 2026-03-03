package com.myflowhub.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TopicBusControlCard(
    cardColors: CardColors,
    cardElevation: CardElevation,
    connected: Boolean,
    state: TopicBusState,
    subText: String,
    onSubTextChange: (String) -> Unit,
    maxEventsInput: String,
    onMaxEventsInputChange: (String) -> Unit,
    busy: Boolean,
    onResubscribe: () -> Unit,
    onUnsubscribeSelected: () -> Unit,
    onSubscribeFromInput: () -> Unit,
    onUnsubscribeFromInput: () -> Unit,
    onApplyMaxEvents: () -> Unit,
    onClearEvents: () -> Unit,
) {
    Card(colors = cardColors, elevation = cardElevation, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "TopicBus Control",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text("Target & Subscriptions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Subscribe to topics and stream published events.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                AssistChip(onClick = {}, label = { Text(if (connected) "Connected" else "Disconnected") })
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = state.targetId,
                    onValueChange = { state.targetId = it },
                    modifier = Modifier.weight(1f),
                    label = { Text("Target Node ID") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(enabled = !busy, onClick = onResubscribe) { Text("Resubscribe") }
                    OutlinedButton(enabled = !busy, onClick = onUnsubscribeSelected) { Text("Unsubscribe Selected") }
                }
            }

            OutlinedTextField(
                value = subText,
                onValueChange = onSubTextChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Subscribe / Unsubscribe") },
                placeholder = { Text("topic.a, topic.b (comma, newline, or semicolon separated)") },
                singleLine = false,
                minLines = 3,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(enabled = !busy, onClick = onSubscribeFromInput) { Text("Subscribe") }
                OutlinedButton(enabled = !busy, onClick = onUnsubscribeFromInput) { Text("Unsubscribe") }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = maxEventsInput,
                    onValueChange = onMaxEventsInputChange,
                    modifier = Modifier.weight(1f),
                    label = { Text("Max Events") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(enabled = !busy, onClick = onApplyMaxEvents) { Text("Apply Limit") }
                    TextButton(enabled = !busy, onClick = onClearEvents) { Text("Clear Events") }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = {}, label = { Text("NodeID: ${if (state.selfNodeId > 0) state.selfNodeId else "-"}") })
                AssistChip(onClick = {}, label = { Text("HubID: ${if (state.defaultTargetId > 0) state.defaultTargetId else "-"}") })
                AssistChip(onClick = {}, label = { Text("Topics: ${state.topics.size}") })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TopicBusPublishCard(
    cardColors: CardColors,
    cardElevation: CardElevation,
    publishTopic: String,
    onPublishTopicChange: (String) -> Unit,
    publishName: String,
    onPublishNameChange: (String) -> Unit,
    publishPayload: String,
    onPublishPayloadChange: (String) -> Unit,
    busy: Boolean,
    onUseSelected: () -> Unit,
    onPublish: () -> Unit,
    onClear: () -> Unit,
) {
    Card(colors = cardColors, elevation = cardElevation, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Publish",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text("Send Topic Events", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Publish JSON or plain text payloads to any topic.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Badge { Text("Publish") }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = publishTopic,
                    onValueChange = onPublishTopicChange,
                    modifier = Modifier.weight(1f),
                    label = { Text("Topic") },
                    singleLine = true,
                    placeholder = { Text("topic.status") },
                )
                OutlinedButton(enabled = !busy, onClick = onUseSelected) { Text("Use Selected") }
            }
            OutlinedTextField(
                value = publishName,
                onValueChange = onPublishNameChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Name") },
                singleLine = true,
                placeholder = { Text("event name") },
            )
            OutlinedTextField(
                value = publishPayload,
                onValueChange = onPublishPayloadChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Payload") },
                placeholder = { Text("JSON or plain text") },
                singleLine = false,
                minLines = 4,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(enabled = !busy, onClick = onPublish) { Text("Publish") }
                OutlinedButton(enabled = !busy, onClick = onClear) { Text("Clear") }
            }
        }
    }
}

@Composable
internal fun TopicBusSubscriptionListCard(
    cardColors: CardColors,
    cardElevation: CardElevation,
    topics: List<String>,
    selectedTopic: String,
    onSelect: (String) -> Unit,
) {
    Card(colors = cardColors, elevation = cardElevation, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "Subscription List",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("Active Topics", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val allSelected = selectedTopic.isBlank()
                if (allSelected) {
                    FilledTonalButton(onClick = { onSelect("") }, modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("All")
                            Text("${topics.size}")
                        }
                    }
                } else {
                    OutlinedButton(onClick = { onSelect("") }, modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("All")
                            Text("${topics.size}")
                        }
                    }
                }

                for (topic in topics) {
                    val selected = selectedTopic == topic
                    if (selected) {
                        FilledTonalButton(onClick = { onSelect(topic) }, modifier = Modifier.fillMaxWidth()) {
                            Text(topic, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    } else {
                        OutlinedButton(onClick = { onSelect(topic) }, modifier = Modifier.fillMaxWidth()) {
                            Text(topic, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                if (topics.isEmpty()) {
                    Text("No topics yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
internal fun TopicBusSnapshotCard(
    cardColors: CardColors,
    cardElevation: CardElevation,
    selectedTopic: String,
    lastFrameAt: String,
    cached: Int,
) {
    Card(colors = cardColors, elevation = cardElevation, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "Snapshot",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("TopicBus Status", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

            SnapshotLine(label = "Selected Topic", value = selectedTopic.ifBlank { "All" })
            SnapshotLine(label = "Last Frame", value = lastFrameAt.ifBlank { "-" })
            SnapshotLine(label = "Events Cached", value = cached.toString())
        }
    }
}

@Composable
internal fun TopicBusEventStreamCard(
    modifier: Modifier,
    cardColors: CardColors,
    cardElevation: CardElevation,
    selectedTopic: String,
    events: List<TopicBusEvent>,
    selectedIndex: Int,
    onSelectIndex: (Int) -> Unit,
) {
    Card(colors = cardColors, elevation = cardElevation, modifier = modifier) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Event Stream",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text("Publish Events", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                Badge { Text("${selectedTopic.ifBlank { "All" }} · ${events.size}") }
            }

            Column(modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp, max = 420.dp)) {
                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(events.size) { idx ->
                        val ev = events[idx]
                        val selected = idx == selectedIndex
                        val line = buildString {
                            append(ev.topic)
                            append(" | ")
                            append(ev.name)
                            val ts = formatTimestamp(ev.ts)
                            if (ts.isNotBlank()) {
                                append(" | ")
                                append(ts)
                            }
                        }
                        if (selected) {
                            FilledTonalButton(onClick = { onSelectIndex(idx) }, modifier = Modifier.fillMaxWidth()) {
                                Text(line, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        } else {
                            OutlinedButton(onClick = { onSelectIndex(idx) }, modifier = Modifier.fillMaxWidth()) {
                                Text(line, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    if (events.isEmpty()) {
                        item {
                            Text("No events yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun TopicBusEventDetailCard(
    modifier: Modifier,
    cardColors: CardColors,
    cardElevation: CardElevation,
    event: TopicBusEvent?,
) {
    Card(colors = cardColors, elevation = cardElevation, modifier = modifier) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "Event Detail",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("Selected Payload", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 260.dp),
            ) {
                val text = event?.dataRaw?.ifBlank { "" } ?: "Select an event to inspect the payload."
                Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                    Text(
                        text,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
    }
}

@Composable
private fun SnapshotLine(label: String, value: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
            Text(value, style = MaterialTheme.typography.bodySmall)
        }
    }
}
