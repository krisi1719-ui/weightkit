@file:OptIn(ExperimentalMaterial3Api::class)

package com.weightkit.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.weightkit.app.R
import com.weightkit.app.export.Exporter
import com.weightkit.app.model.AppState
import com.weightkit.app.model.CutList
import com.weightkit.app.model.Fmt
import com.weightkit.app.model.ProManager

@Composable
fun ListsScreen(s: AppState, pro: ProManager) {
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    var showNew by remember { mutableStateOf(false) }
    var showPaywall by remember { mutableStateOf(false) }

    val open = s.lists.firstOrNull { it.id == openId }
    if (open != null) {
        ListDetail(s, pro, open, onBack = { openId = null })
        return
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.tab_lists), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f))
            IconButton(modifier = Modifier.testTag("new_list"), onClick = {
                // Free: one list. More lists = Pro.
                if (s.lists.isNotEmpty() && !pro.hasPro) showPaywall = true else showNew = true
            }) { Icon(Icons.Filled.Add, stringResource(R.string.new_list)) }
        }
        if (s.lists.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.lists_empty), textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("lists_empty"))
            }
        } else {
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(s.lists, key = { it.id }) { l ->
                    Surface(shape = CardShape, color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth().clickable { openId = l.id }.testTag("list_row")) {
                        Column(Modifier.padding(16.dp)) {
                            Text(l.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text("${l.items.size} ${stringResource(R.string.items).lowercase()} · ${Fmt.weight(l.grossKg, s.units)}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        BannerSlot(pro)
    }

    if (showNew) {
        var name by remember { mutableStateOf("") }
        val def = stringResource(R.string.default_list)
        AlertDialog(
            onDismissRequest = { showNew = false },
            title = { Text(stringResource(R.string.new_list)) },
            text = {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.list_name)) }, singleLine = true,
                    modifier = Modifier.testTag("new_list_name"))
            },
            confirmButton = {
                TextButton(onClick = { s.newList(name.ifBlank { def }); showNew = false }, modifier = Modifier.testTag("dialog_save")) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = { TextButton(onClick = { showNew = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (showPaywall) PaywallSheet(pro) { showPaywall = false }
}

@Composable
private fun ListDetail(s: AppState, pro: ProManager, list: CutList, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var showPaywall by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val u = s.units

    fun export(pdf: Boolean) {
        if (!pro.hasPro) { showPaywall = true; return }
        Exporter.share(ctx, list, s, pdf)
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("list_back")) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.tab_lists))
            }
            Text(list.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1)
            IconButton(onClick = { confirmDelete = true }, modifier = Modifier.testTag("list_delete")) {
                Icon(Icons.Filled.Delete, stringResource(R.string.delete))
            }
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                OutlinedTextField(list.name, { v -> s.updateList(list.id) { it.copy(name = v) } },
                    label = { Text(stringResource(R.string.list_name)) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("list_name"))
            }
            items(list.items, key = { it.id }) { it ->
                Surface(shape = CardShape, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth().testTag("list_item")) {
                    Row(Modifier.padding(start = 14.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${it.qty} × ${it.shape}", fontWeight = FontWeight.SemiBold)
                            Text(it.size)
                            Text(it.material, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(Fmt.weight(it.totalKg, u), fontWeight = FontWeight.SemiBold)
                            it.cost?.let { c -> Text(Fmt.money(c, s.currency), style = MaterialTheme.typography.bodySmall) }
                        }
                        IconButton(onClick = { s.updateList(list.id) { l -> l.copy(items = l.items.filter { x -> x.id != it.id }) } },
                            modifier = Modifier.testTag("item_delete")) {
                            Icon(Icons.Filled.Delete, stringResource(R.string.delete), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PctField(stringResource(R.string.waste), list.id + "w", list.wastePct, "field_waste") { v ->
                        s.updateList(list.id) { it.copy(wastePct = v) }
                    }
                    PctField(stringResource(R.string.markup), list.id + "m", list.markupPct, "field_markup") { v ->
                        s.updateList(list.id) { it.copy(markupPct = v) }
                    }
                    HorizontalDivider()
                    TotalRow(stringResource(R.string.total_weight), Fmt.weight(list.grossKg, u), "total_weight")
                    if (list.hasCost) {
                        TotalRow(stringResource(R.string.subtotal), Fmt.money(list.netCost, s.currency), "subtotal")
                        TotalRow(stringResource(R.string.total), Fmt.money(list.totalCost, s.currency), "total_cost")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ExportButton(stringResource(R.string.export_pdf), !pro.purchased, Modifier.weight(1f).testTag("export_pdf")) { export(true) }
                        ExportButton(stringResource(R.string.export_csv), !pro.purchased, Modifier.weight(1f).testTag("export_csv")) { export(false) }
                    }
                    TrialNotice(pro, "trial_notice_export")
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(list.name) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; s.deleteList(list.id); onBack() }, modifier = Modifier.testTag("confirm_delete")) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (showPaywall) PaywallSheet(pro) { showPaywall = false }
}

/** Percentage input that keeps its own text (so "2," can be typed) and reports parsed values. */
@Composable
private fun PctField(label: String, key: String, value: Double, tag: String, onValue: (Double) -> Unit) {
    var text by remember(key) { mutableStateOf(if (value == 0.0) "" else Fmt.edit(value)) }
    NumberField(label, "%", text, { t -> text = t; onValue(Fmt.parse(t) ?: 0.0) }, tag = tag)
}

@Composable
private fun TotalRow(a: String, b: String, tag: String) {
    Row(Modifier.fillMaxWidth().testTag(tag).semantics(mergeDescendants = true) {}) {
        Text(a, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        Text(b, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ExportButton(text: String, badge: Boolean, modifier: Modifier, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = modifier.heightIn(min = 52.dp)) {
        Icon(Icons.Filled.Share, null)
        Spacer(Modifier.width(6.dp))
        Text(text, maxLines = 1)
        if (badge) { Spacer(Modifier.width(6.dp)); ProBadge() }
    }
}
