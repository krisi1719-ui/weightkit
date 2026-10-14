@file:OptIn(ExperimentalMaterial3Api::class)

package com.weightkit.app.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weightkit.app.R
import com.weightkit.app.model.AppState
import com.weightkit.app.model.CalcOutcome
import com.weightkit.app.model.CalcResult
import com.weightkit.app.model.Catalog
import com.weightkit.app.model.Fmt
import com.weightkit.app.model.ListItem
import com.weightkit.app.model.ProManager
import com.weightkit.app.model.Reverse
import com.weightkit.app.model.ShapeKind
import com.weightkit.app.model.UnitSystem

private const val LB_PER_IN3 = 27679.9 // kg/m³ per lb/in³

@Composable
fun CalculatorScreen(s: AppState, pro: ProManager) {
    val ctx = LocalContext.current
    val res = LocalResources.current
    val units = s.units
    s.calc.proAllowed = pro.hasPro // Pro tables only with Pro (trial / reward / purchase)
    val material = s.material(s.materialId)
    val outcome = s.compute(material.density)
    var showPaywall by rememberSaveable { mutableStateOf(false) }
    var showMaterials by rememberSaveable { mutableStateOf(false) }
    val defaultList = stringResource(R.string.default_list)
    val shapeTitle = stringResource(s.shape.titleRes)

    Column(Modifier.fillMaxSize().imePadding()) {
        // header: title + unit switch
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(shapeTitle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            SingleChoiceSegmentedButtonRow {
                SegmentedButton(selected = units == UnitSystem.METRIC, onClick = { s.updateUnits(UnitSystem.METRIC) },
                    shape = SegmentedButtonDefaults.itemShape(0, 2), modifier = Modifier.testTag("units_metric")) { Text("mm·kg") }
                SegmentedButton(selected = units == UnitSystem.IMPERIAL, onClick = { s.updateUnits(UnitSystem.IMPERIAL) },
                    shape = SegmentedButtonDefaults.itemShape(1, 2), modifier = Modifier.testTag("units_imperial")) { Text("in·lb") }
            }
        }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ShapeChips(s.shape) { s.shape = it }

            if (s.shape == ShapeKind.SECTION) SectionPickers(s, pro) { showPaywall = true }
            if (s.shape == ShapeKind.REBAR) {
                val items = s.catalog.rebar?.items.orEmpty()
                DropdownRow(stringResource(R.string.dim_diameter), (s.rebarItem?.name ?: "") + " mm",
                    items.map { it.name + " mm" }, tag = "rebar_picker") { s.rebarIndex = it }
            }
            for (f in s.shape.fields) {
                NumberField(stringResource(f.labelRes), units.dimUnit, s.fields[f.key] ?: "", { s.fields[f.key] = it },
                    tag = "field_${f.key}")
            }
            if (s.shape.isLinear) {
                NumberField(stringResource(R.string.dim_length), units.lengthUnit, s.lengthText, { s.lengthText = it }, tag = "field_length")
            }
            NumberField(stringResource(R.string.quantity), stringResource(R.string.qty), s.qtyText, { s.qtyText = it }, integer = true,
                tag = "field_qty")

            // material
            Surface(
                shape = CardShape, color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable { showMaterials = true }.testTag("material_row"),
            ) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.material), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(material.name, fontWeight = FontWeight.SemiBold, modifier = Modifier.testTag("material_name"))
                    }
                    Text(densityText(material.density, units), color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("material_density"))
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                }
            }

            NumberField(
                stringResource(if (units == UnitSystem.METRIC) R.string.price_per_kg else R.string.price_per_lb),
                s.currency, s.priceText, { s.priceText = it }, tag = "field_price",
            )

            ReverseSection(s, pro, outcome) { showPaywall = true }

            Button(
                onClick = {
                    val r = (outcome as? CalcOutcome.Ok)?.r ?: return@Button
                    val shapeName = if (s.shape == ShapeKind.SECTION) (s.family?.name ?: shapeTitle) else shapeTitle
                    val name = s.addToList(
                        ListItem(shape = shapeName, material = material.name, size = s.sizeDescription(),
                            qty = s.quantity, pieceKg = r.pieceKg, pricePerKg = s.pricePerKg),
                        defaultList,
                    )
                    Toast.makeText(ctx, res.getString(R.string.added_to, name), Toast.LENGTH_SHORT).show()
                },
                enabled = outcome is CalcOutcome.Ok,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("add_to_list"),
            ) {
                Icon(Icons.Filled.Add, null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.add_to_list), style = MaterialTheme.typography.titleMedium)
            }
        }

        ResultCard(outcome, units, s.pricePerKg, s.currency, s.quantity)
    }

    if (showPaywall) PaywallSheet(pro) { showPaywall = false }
    if (showMaterials) MaterialSheet(s) { showMaterials = false }
}

fun densityText(d: Double, u: UnitSystem): String =
    if (u == UnitSystem.METRIC) Fmt.smart(d) + " kg/m³" else Fmt.fixed(d / LB_PER_IN3, 4) + " lb/in³"

@Composable
private fun ShapeChips(selected: ShapeKind, onSelect: (ShapeKind) -> Unit) {
    Row(Modifier.testTag("shape_row").horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        for (sh in ShapeKind.entries) {
            val sel = sh == selected
            val fg = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
            Surface(
                shape = CardShape,
                color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.width(84.dp).heightIn(min = 88.dp).clickable { onSelect(sh) }.testTag("shape_${sh.id}")
                    .semantics { this.selected = sel },
            ) {
                Column(Modifier.padding(vertical = 10.dp, horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    ShapeIcon(sh, fg, Modifier.size(34.dp))
                    Spacer(Modifier.size(6.dp))
                    // fixed-width chip: the icon carries the meaning; cap the caption at 1.3× (TalkBack reads it in full)
                    val scale = LocalDensity.current.fontScale
                    val base = MaterialTheme.typography.labelSmall
                    Text(stringResource(sh.titleRes), color = fg, textAlign = TextAlign.Center, maxLines = 2,
                        style = if (scale > 1.3f) base.copy(fontSize = base.fontSize * (1.3f / scale)) else base)
                }
            }
        }
    }
}

@Composable
private fun SectionPickers(s: AppState, pro: ProManager, onLocked: () -> Unit) {
    val fams = s.catalog.profileFamilies
    val fam = s.family ?: return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DropdownRow(stringResource(R.string.standard), fam.name,
            fams.map { it.name + if (it.pro && !pro.purchased) "  ★ Pro" else "" }, tag = "family_picker") { i ->
            val f = fams[i]
            if (f.pro && !pro.hasPro) onLocked() else { s.familyId = f.id; s.itemIndex = 0 }
        }
        DropdownRow(stringResource(R.string.profile), s.sectionItem?.name ?: "", fam.items.map { it.name }, tag = "profile_picker") {
            s.itemIndex = it
        }
        Text(fam.source, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (fam.pro) TrialNotice(pro, "trial_notice_profile")
    }
}

@Composable
private fun ReverseSection(s: AppState, pro: ProManager, outcome: CalcOutcome, onLocked: () -> Unit) {
    val units = s.units
    Surface(shape = CardShape, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.reverse_title), style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (!pro.purchased) ProBadge()
            }
            if (!pro.hasPro) {
                OutlinedButton(onClick = onLocked, modifier = Modifier.testTag("reverse_unlock")) { Text(stringResource(R.string.buy_pro_noprice)) }
                return@Column
            }
            NumberField(stringResource(R.string.target_weight), units.weightUnit, s.targetText, { s.targetText = it }, tag = "field_target")
            val targetKg = s.calc.targetKg
            val r = (outcome as? CalcOutcome.Ok)?.r
            if (targetKg != null && r != null) {
                val perM = r.perMeterKg
                if (perM != null) {
                    Reverse.neededLengthM(targetKg, perM, s.quantity)?.let {
                        KV(stringResource(R.string.needed_length), Fmt.smart(it / units.mPerLength) + " " + units.lengthUnit,
                            "reverse_length")
                    }
                } else if (s.shape == ShapeKind.PLATE) {
                    val t = s.calc.thicknessMM
                    if (t != null) Reverse.neededThickness(targetKg, t, r.pieceKg, s.quantity)?.let {
                        KV(stringResource(R.string.needed_thickness), Fmt.smart(it / units.mmPerDim) + " " + units.dimUnit,
                            "reverse_thickness")
                    }
                }
                if (r.pieceKg > 0) KV(stringResource(R.string.pieces_in_target),
                    Reverse.wholePieces(targetKg, r.pieceKg).toString(), "reverse_pieces")
            }
            TrialNotice(pro, "trial_notice_reverse")
        }
    }
}

@Composable
private fun KV(k: String, v: String, tag: String) {
    Row(Modifier.fillMaxWidth().testTag(tag).semantics(mergeDescendants = true) {}) {
        Text(k, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(v, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ResultCard(outcome: CalcOutcome, units: UnitSystem, pricePerKg: Double?, currency: String, qty: Int) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
        Column {
            HorizontalDivider()
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (outcome) {
                    CalcOutcome.Missing -> Text(
                        stringResource(R.string.invalid_input), color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp).testTag("result_missing"), textAlign = TextAlign.Center,
                    )
                    CalcOutcome.Wall -> Row(Modifier.fillMaxWidth().padding(vertical = 16.dp).testTag("result_wall"), horizontalArrangement = Arrangement.Center) {
                        Icon(Icons.Filled.Warning, null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.invalid_wall), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                    }
                    is CalcOutcome.Ok -> ResultContent(outcome.r, units, pricePerKg, currency, qty)
                }
            }
        }
    }
}

@Composable
private fun ResultContent(r: CalcResult, units: UnitSystem, pricePerKg: Double?, currency: String, qty: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.total_weight), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(Fmt.weight(r.totalKg, units), fontSize = 32.sp, fontWeight = FontWeight.Bold, maxLines = 1,
            modifier = Modifier.testTag("result_total"))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        if (qty > 1) Metric(stringResource(R.string.weight_piece), Fmt.weight(r.pieceKg, units), "result_piece")
        r.perMeterKg?.let { pm ->
            if (units == UnitSystem.METRIC) Metric(stringResource(R.string.weight_per_m), Fmt.smart(pm) + " kg/m", "result_per_length")
            else Metric(stringResource(R.string.weight_per_ft), Fmt.smart(pm * 0.3048 / 0.45359237) + " lb/ft", "result_per_length")
        }
        pricePerKg?.let { Metric(stringResource(R.string.cost), Fmt.money(it * r.totalKg, currency), "result_cost") }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        Metric(stringResource(R.string.volume), volumeText(r.volumeM3 * qty, units), "result_volume")
        r.surfaceM2?.let { Metric(stringResource(R.string.surface), surfaceText(it * qty, units), "result_surface") }
        r.tableKgM?.let { t ->
            val txt = if (units == UnitSystem.METRIC) Fmt.smart(t) + " kg/m"
            else Fmt.smart(r.tableLbFt ?: (t * 0.3048 / 0.45359237)) + " lb/ft"
            Metric(stringResource(R.string.table_mass, "").trim(), txt, "result_table")
        }
    }
}

@Composable
private fun Metric(label: String, value: String, tag: String) {
    Column(Modifier.testTag(tag).semantics(mergeDescendants = true) {}) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

private fun volumeText(m3: Double, u: UnitSystem): String =
    if (u == UnitSystem.IMPERIAL) {
        val in3 = m3 / 1.6387064e-5
        if (in3 < 1728) Fmt.smart(in3) + " in³" else Fmt.smart(in3 / 1728) + " ft³"
    } else if (m3 < 0.001) Fmt.smart(m3 * 1e6) + " cm³" else Fmt.smart(m3 * 1000) + " dm³"

private fun surfaceText(m2: Double, u: UnitSystem): String =
    if (u == UnitSystem.METRIC) Fmt.smart(m2) + " m²" else Fmt.smart(m2 / 0.09290304) + " ft²"

@Composable
fun MaterialSheet(s: AppState, onDismiss: () -> Unit) {
    val groups = Catalog.groups + if (s.customMaterials.isEmpty()) emptyList() else listOf("custom")
    val groupNames = mapOf(
        "steel" to R.string.group_steel, "stainless" to R.string.group_stainless,
        "aluminium" to R.string.group_aluminium, "copper" to R.string.group_copper,
        "other" to R.string.group_other, "plastic" to R.string.group_plastic, "custom" to R.string.group_custom,
    )
    var query by rememberSaveable { mutableStateOf("") }
    val all = s.allMaterials
    ModalBottomSheet(onDismissRequest = onDismiss) {
        OutlinedTextField(
            query, { query = it }, singleLine = true,
            label = { Text(stringResource(R.string.search)) },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).testTag("material_search"),
        )
        // edge-to-edge sheet: the last material scrolls clear of the navigation bar
        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        LazyColumn(Modifier.fillMaxWidth().testTag("material_list"), contentPadding = PaddingValues(bottom = 24.dp + navBottom)) {
            for (g in groups) {
                val mats = all.filter { it.group == g && (query.isBlank() || it.name.contains(query.trim(), ignoreCase = true)) }
                if (mats.isEmpty()) continue
                item(key = "h_$g") {
                    Text(stringResource(groupNames[g] ?: R.string.material), style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp))
                }
                items(mats, key = { it.id }) { m ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { s.materialId = m.id; onDismiss() }
                            .testTag("material_${m.id}").padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(m.name, Modifier.weight(1f))
                        Text(densityText(m.density, s.units), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (m.id == s.materialId) {
                            Spacer(Modifier.width(8.dp))
                            Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}
