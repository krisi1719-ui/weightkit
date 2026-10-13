@file:OptIn(ExperimentalMaterial3Api::class)

package com.weightkit.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.weightkit.app.R
import com.weightkit.app.model.AdsHolder
import com.weightkit.app.model.AppState
import com.weightkit.app.model.Fmt
import com.weightkit.app.model.ProManager
import com.weightkit.app.model.ShapeKind
import com.weightkit.app.model.UnitSystem

@Composable
fun SettingsScreen(s: AppState, pro: ProManager) {
    var showPaywall by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(stringResource(R.string.tab_settings), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

        Surface(shape = CardShape, color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.fillMaxWidth().clickable { showPaywall = true }.testTag("settings_pro")) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                ShapeIcon(ShapeKind.IBEAM, MaterialTheme.colorScheme.primary, Modifier.size(32.dp))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(stringResource(R.string.pro_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        when {
                            pro.purchased -> stringResource(R.string.pro_active)
                            pro.inTrial -> stringResource(R.string.trial_active, Fmt.date(pro.trialEnd))
                            else -> stringResource(R.string.buy_pro_noprice)
                        },
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }

        Section(stringResource(R.string.units)) {
            for ((u, label) in listOf(UnitSystem.METRIC to R.string.metric, UnitSystem.IMPERIAL to R.string.imperial)) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .selectable(selected = s.units == u, onClick = { s.updateUnits(u) }, role = Role.RadioButton)
                        .testTag("settings_units_" + u.name.lowercase()),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = s.units == u, onClick = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(label))
                }
            }
            OutlinedTextField(s.currency, { s.updateCurrency(it.take(5)) }, label = { Text(stringResource(R.string.currency)) },
                singleLine = true, modifier = Modifier.fillMaxWidth().testTag("currency"))
        }

        Section(stringResource(R.string.company_pdf)) {
            OutlinedTextField(s.company, { s.updateCompany(it) }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("company"),
                label = { Text(stringResource(R.string.company_name)) })
            OutlinedTextField(s.companyAddress, { s.updateCompanyAddress(it.take(120)) }, maxLines = 2,
                modifier = Modifier.fillMaxWidth().testTag("company_address"),
                label = { Text(stringResource(R.string.company_address)) })
            OutlinedTextField(s.companyContact, { s.updateCompanyContact(it.take(120)) }, maxLines = 2,
                modifier = Modifier.fillMaxWidth().testTag("company_contact"),
                label = { Text(stringResource(R.string.company_contact)) })
        }

        Section(stringResource(R.string.theme)) {
            val opts = listOf(R.string.theme_system, R.string.theme_light, R.string.theme_dark)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                opts.forEachIndexed { i, r ->
                    SegmentedButton(selected = s.theme == i, onClick = { s.updateTheme(i) },
                        shape = SegmentedButtonDefaults.itemShape(i, opts.size), modifier = Modifier.testTag("theme_$i")) { Text(stringResource(r)) }
                }
            }
        }

        Section(stringResource(R.string.custom_materials)) {
            for (m in s.customMaterials) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag("custom_material")) {
                    Text(m.name, Modifier.weight(1f))
                    Text(Fmt.smart(m.density) + " kg/m³", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    IconButton(onClick = { s.removeCustomMaterial(m.id) }, modifier = Modifier.testTag("custom_material_delete")) {
                        Icon(Icons.Filled.Delete, stringResource(R.string.delete))
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { if (pro.hasPro) showAdd = true else showPaywall = true }
                    .testTag("add_material"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Add, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.add_material), color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                if (!pro.purchased) ProBadge()
            }
            TrialNotice(pro, "trial_notice_materials")
        }

        Section(stringResource(R.string.about)) {
            Text(stringResource(R.string.no_ads_note))
            Text(stringResource(R.string.data_note), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            val uri = LocalUriHandler.current
            val ctx = LocalContext.current
            Column(
                Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .clickable(role = Role.Button) { uri.openUri("mailto:" + AdsHolder.CONTACT_EMAIL) }
                    .padding(vertical = 8.dp).testTag("contact"),
            ) {
                Text(stringResource(R.string.contact), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(AdsHolder.CONTACT_EMAIL, color = MaterialTheme.colorScheme.primary)
            }
            Text(stringResource(R.string.privacy_policy), color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .clickable(role = Role.Button) { uri.openUri(AdsHolder.PRIVACY_URL) }
                    .padding(vertical = 12.dp).testTag("privacy_policy"))
            val ads = AdsHolder.get(ctx, pro)
            if (ads.privacyOptionsRequired(ctx)) {
                Text(stringResource(R.string.privacy_options), color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .clickable(role = Role.Button) { (ctx as? android.app.Activity)?.let { ads.showPrivacyOptions(it) } }
                        .padding(vertical = 12.dp).testTag("privacy_options"))
            }
        }
    }

    if (showAdd) {
        var name by remember { mutableStateOf("") }
        var dens by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text(stringResource(R.string.add_material)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.name)) }, singleLine = true,
                        modifier = Modifier.testTag("material_name_input"))
                    OutlinedTextField(dens, { dens = it }, label = { Text(stringResource(R.string.density_unit_hint)) },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.testTag("material_density_input"))
                }
            },
            confirmButton = {
                TextButton(modifier = Modifier.testTag("dialog_save"), onClick = {
                    val d = Fmt.parse(dens)
                    if (d != null && d > 0 && name.isNotBlank()) s.addCustomMaterial(name.trim(), d)
                    showAdd = false
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (showPaywall) PaywallSheet(pro) { showPaywall = false }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Surface(shape = CardShape, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}
