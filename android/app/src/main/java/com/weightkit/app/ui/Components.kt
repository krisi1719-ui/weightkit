@file:OptIn(ExperimentalMaterial3Api::class)

package com.weightkit.app.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.weightkit.app.R
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.weightkit.app.model.AdIds
import com.weightkit.app.model.AdsHolder
import com.weightkit.app.model.Fmt
import com.weightkit.app.model.ProManager
import com.weightkit.app.model.ShapeKind

val CardShape = RoundedCornerShape(12.dp)

fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

/** Labeled numeric input with unit suffix. Accepts "," or "." as decimal separator. */
@Composable
fun NumberField(label: String, unit: String, value: String, onChange: (String) -> Unit, integer: Boolean = false, tag: String? = null) {
    val fr = remember { FocusRequester() }
    // Large font scales: label above the value, so neither is cut off (same as iOS accessibility sizes).
    val stacked = LocalDensity.current.fontScale >= 1.5f
    Surface(
        shape = CardShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { fr.requestFocus() },
    ) {
      Column(Modifier.padding(top = if (stacked) 8.dp else 0.dp)) {
        if (stacked) Text(
            label, Modifier.padding(horizontal = 14.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
        )
        Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (stacked) Spacer(Modifier.weight(1f)) else Text(
                label, Modifier.weight(1f).padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge,
            )
            BasicTextField(
                value = value,
                onValueChange = { s -> onChange(s.filter { it.isDigit() || (!integer && (it == '.' || it == ',')) }) },
                singleLine = true,
                textStyle = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.End,
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (integer) KeyboardType.Number else KeyboardType.Decimal,
                    imeAction = ImeAction.Done,
                ),
                modifier = Modifier.widthIn(min = 90.dp, max = if (stacked) 280.dp else 140.dp).focusRequester(fr)
                    .then(if (tag != null) Modifier.testTag(tag) else Modifier)
                    .semantics { contentDescription = label },
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterEnd) {
                        if (value.isEmpty()) Text(
                            "0", style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        inner()
                    }
                },
            )
            Spacer(Modifier.width(8.dp))
            Text(unit, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.widthIn(min = 28.dp))
        }
      }
    }
}

/** Row that opens a dropdown of options. */
@Composable
fun DropdownRow(label: String, value: String, options: List<String>, tag: String? = null, onSelect: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Surface(
            shape = CardShape, color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { open = true }
                .then(if (tag != null) Modifier.testTag(tag) else Modifier),
        ) {
            Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, fontWeight = FontWeight.SemiBold, modifier = if (tag != null) Modifier.testTag(tag + "_value") else Modifier)
                Icon(Icons.Filled.ArrowDropDown, null)
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEachIndexed { i, o ->
                DropdownMenuItem(text = { Text(o) }, onClick = { open = false; onSelect(i) },
                    modifier = if (tag != null) Modifier.testTag("${tag}_item_$i") else Modifier)
            }
        }
    }
}

@Composable
fun ProBadge() {
    Text(
        stringResource(R.string.pro_badge),
        color = Color.White,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Black,
        modifier = Modifier.background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50))
            .padding(horizontal = 7.dp, vertical = 2.dp),
    )
}

/** "Pro feature – trial ends <date>" caption shown during the free trial. */
@Composable
fun TrialNotice(pro: ProManager, tag: String = "trial_notice") {
    if (pro.inTrial) {
        Text(
            stringResource(R.string.trial_notice, Fmt.date(pro.trialEnd)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag(tag),
        )
    }
}

@Composable
fun PaywallSheet(pro: ProManager, onDismiss: () -> Unit) {
    val activity = LocalContext.current.findActivity()
    // The sheet has its own window, where the navigation-bar inset reads 0: take it from the activity
    // so the last button ("Restore purchase") stays above the navigation bar.
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // open at full content height: half-open, the bottom ("Restore purchase") sat under the navigation bar
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().testTag("paywall").verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 8.dp + navBottom),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ShapeIcon(ShapeKind.IBEAM, MaterialTheme.colorScheme.primary, Modifier.size(64.dp))
            Text(stringResource(R.string.pro_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                stringResource(R.string.pro_desc), textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(stringResource(R.string.no_ads_note), fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            if (pro.purchased) {
                Text(stringResource(R.string.pro_active), color = Color(0xFF2E9D4F), fontWeight = FontWeight.Bold,
                    modifier = Modifier.testTag("pro_active"))
            } else {
                Button(
                    onClick = { activity?.let { pro.buy(it) } },
                    enabled = !pro.busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("buy_pro"),
                ) {
                    val p = pro.price
                    Text(
                        if (p != null) stringResource(R.string.buy_pro, p) else stringResource(R.string.buy_pro_noprice),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                TextButton(onClick = { pro.refresh() }, enabled = !pro.busy, modifier = Modifier.testTag("restore")) {
                    Text(stringResource(R.string.restore))
                }
                if (pro.error) Text(stringResource(R.string.purchase_failed), color = MaterialTheme.colorScheme.error)
                val ads = AdsHolder.get(LocalContext.current, pro)
                if (ads.rewardedOffer && !pro.hasPro) {
                    androidx.compose.material3.OutlinedButton(
                        onClick = { onDismiss(); activity?.let { ads.showRewarded(it) } },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("reward_offer"),
                    ) { Text(stringResource(R.string.reward_offer)) }
                }
                if (pro.hasReward) {
                    Text(stringResource(R.string.reward_active), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (pro.inTrial) {
                Text(
                    stringResource(R.string.trial_active, Fmt.date(pro.trialEnd)),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.size(24.dp))
        }
    }
}

/** One small adaptive banner (Lists screen only). Nothing for Pro / trial users. */
@Composable
fun BannerSlot(pro: ProManager) {
    val ctx = LocalContext.current
    val ads = AdsHolder.get(ctx, pro)
    if (!ads.adsActive) return
    val widthDp = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp().value.toInt() }
    AndroidView(
        modifier = Modifier.fillMaxWidth().testTag("banner_ad"),
        factory = { c ->
            AdView(c).apply {
                setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(c, widthDp))
                adUnitId = AdIds.banner
                loadAd(AdRequest.Builder().build())
            }
        },
    )
}
