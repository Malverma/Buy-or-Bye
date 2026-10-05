package com.buyorbye.app.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.automirrored.outlined.TrendingFlat
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.buyorbye.app.domain.Channel
import com.buyorbye.app.domain.Decision
import com.buyorbye.app.domain.Option
import com.buyorbye.app.domain.Verdict
import java.util.Locale

@Composable
fun VerdictScreen(vm: MainViewModel) {
    val check by vm.check.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Box(Modifier.padding(horizontal = 8.dp)) { TopBar(title = "", onBack = vm::back) }
        when (val c = check) {
            CheckState.Idle, is CheckState.Loading -> Centered {
                CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
                Text((c as? CheckState.Loading)?.step ?: "", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            is CheckState.Failed -> Centered {
                Text(c.message, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                Button(onClick = vm::runCheck) { Text("Retry") }
            }
            is CheckState.Ready -> Ready(vm, c.data)
        }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { content() }
}

@Composable
private fun Ready(vm: MainViewModel, data: CheckData) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val qty by vm.quantity.collectAsStateWithLifecycle()
    val verdict = remember(data, settings, qty) { vm.verdict(data, settings, qty) }
    val params = remember(data, settings, qty) { vm.params(data, settings, qty) }
    val context = LocalContext.current
    var showMath by remember { mutableStateOf(false) }
    val best = verdict.best
    val color = if (verdict.decision == Decision.BUY) BuyGreen else ByeAmber

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            data.product.query,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        // Verdict: always text + icon, never color alone.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (verdict.decision == Decision.BUY) Icons.Outlined.CheckCircle else Icons.AutoMirrored.Outlined.TrendingFlat,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(56.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                verdict.decision.name,
                color = color,
                fontSize = 72.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.semantics { heading() },
            )
        }
        Text(headline(verdict, data, settings.minSavings), style = MaterialTheme.typography.titleLarge)
        if (best != null) Text(subline(best), color = MaterialTheme.colorScheme.onSurfaceVariant)

        if (verdict.decision == Decision.BYE && best != null) {
            Button(
                onClick = { openOption(context, best) },
                colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.Black),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(if (best.result.channel == Channel.IN_STORE) "Navigate" else "Open ${best.result.retailer}") }
        }

        if (best != null) {
            HorizontalDivider()
            Row(
                Modifier.fillMaxWidth().clickable { showMath = !showMath }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Show the math", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                Icon(if (showMath) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = null)
            }
            AnimatedVisibility(showMath) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    MathRow("Price difference${if (qty > 1) " × $qty" else ""}", money((data.priceHere - best.result.price) * qty))
                    best.trip?.let { t ->
                        MathRow(
                            "Gas (${miles(t.extraMiles)} @ ${money(params.gasPrice)}/gal, ${fmt(params.mpg)} mpg)",
                            "-" + money(t.fuelCost),
                        )
                        if (params.valueOfTimePerHour > 0) {
                            MathRow("Time (${minutes(t.extraMinutes)} @ ${money(params.valueOfTimePerHour)}/hr)", "-" + money(t.timeCost))
                        }
                        if (t.wearCost > 0) MathRow("Vehicle wear", "-" + money(t.wearCost))
                    }
                    if (best.result.channel == Channel.ONLINE) {
                        MathRow("Shipping", best.result.shipping?.let { "-" + money(it) } ?: "unknown")
                    }
                    HorizontalDivider()
                    MathRow("Net savings", money(best.netSavings), bold = true)
                    MathRow("Your minimum to make it worth it", money(settings.minSavings))
                }
            }
        }

        HorizontalDivider()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Quantity", modifier = Modifier.weight(1f))
            IconButton(onClick = { vm.setQuantity(qty - 1) }, enabled = qty > 1) {
                Icon(Icons.Outlined.Remove, contentDescription = "Fewer")
            }
            Text("$qty", style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(32.dp), textAlign = TextAlign.Center)
            IconButton(onClick = { vm.setQuantity(qty + 1) }) {
                Icon(Icons.Outlined.Add, contentDescription = "More")
            }
        }
        HorizontalDivider()

        // All offers, best first, plus the price here for reference.
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            verdict.options.forEach { OptionRow(it, onClick = { openOption(context, it) }) }
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                Text("Here", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                Text(money(data.priceHere))
            }
        }

        Notes(data, verdict)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun OptionRow(o: Option, onClick: () -> Unit) {
    val r = o.result
    Row(
        Modifier.fillMaxWidth().clip8().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(r.retailer, fontWeight = FontWeight.SemiBold)
            Text(
                when (r.channel) {
                    Channel.IN_STORE -> listOfNotNull(o.driveMiles?.let(::miles), o.driveMinutes?.let(::minutes)).joinToString(" · ")
                    Channel.ONLINE -> "Online" + (r.deliveryText?.let { " · $it" } ?: "")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(money(r.price))
            Text(
                (if (o.netSavings >= 0) "saves " else "costs ") + money(kotlin.math.abs(o.netSavings)),
                style = MaterialTheme.typography.bodySmall,
                color = if (o.netSavings > 0) BuyGreen else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Notes(data: CheckData, verdict: Verdict) {
    val notes = buildList {
        addAll(data.search.warnings)
        if (data.gas.estimated) add("Gas price ${money(data.gas.pricePerGallon)}/gal is from your settings (no live price available).")
        else add("Gas ${money(data.gas.pricePerGallon)}/gal from ${data.gas.source}.")
        if (data.routesEstimated) add("Drive distances are estimates.")
        if (verdict.options.any { it.result.channel == Channel.IN_STORE }) {
            add("Store prices are each retailer's online price and may vary in store.")
        }
        add("Prices via Google Shopping.")
    }
    Column(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        notes.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun MathRow(label: String, value: String, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = if (bold) FontWeight.Bold else null)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = if (bold) FontWeight.Bold else null)
    }
}

private fun headline(v: Verdict, data: CheckData, minSavings: Double): String {
    val best = v.best
    return when {
        best == null -> "No better price found"
        v.decision == Decision.BYE -> "Save ${money(best.netSavings)} at ${best.result.retailer}"
        best.result.price >= data.priceHere -> "Best price is here"
        best.netSavings <= 0 -> "${best.result.retailer} is cheaper, but not worth the trip"
        else -> "Only ${money(best.netSavings)} cheaper at ${best.result.retailer} (under your ${money(minSavings)} minimum)"
    }
}

private fun subline(o: Option): String = when (o.result.channel) {
    Channel.IN_STORE -> listOfNotNull(
        money(o.result.price),
        o.driveMiles?.let(::miles),
        o.driveMinutes?.let(::minutes),
        o.result.store?.address,
    ).joinToString(" · ")
    Channel.ONLINE -> listOfNotNull(money(o.result.price), "online", o.result.deliveryText).joinToString(" · ")
}

private fun openOption(context: Context, o: Option) {
    val store = o.result.store
    val uri = if (o.result.channel == Channel.IN_STORE && store != null) {
        Uri.parse("https://www.google.com/maps/dir/?api=1&destination=${store.location.lat},${store.location.lng}&travelmode=driving")
    } else {
        o.result.link?.let(Uri::parse) ?: return
    }
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    } catch (_: ActivityNotFoundException) {
    }
}

private fun fmt(d: Double) = if (d % 1.0 == 0.0) "%.0f".format(Locale.US, d) else "%.1f".format(Locale.US, d)

private fun Modifier.clip8() = this.then(Modifier.clip(RoundedCornerShape(8.dp)))
