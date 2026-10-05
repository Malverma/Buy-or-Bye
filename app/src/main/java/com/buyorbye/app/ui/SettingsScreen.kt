package com.buyorbye.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.buyorbye.app.BuildConfig
import java.util.Locale

@Composable
fun SettingsScreen(vm: MainViewModel) {
    val s by vm.settings.collectAsStateWithLifecycle()
    var homeMessage by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TopBar(title = "Settings", onBack = vm::back)

        Section("Your car")
        NumberField("Fuel economy", s.mpg, suffix = "mpg") { vm.saveSettings(s.copy(mpg = it)) }
        ToggleRow("Use my gas price", "Instead of live prices from nearby stations", s.gasOverride) {
            vm.saveSettings(s.copy(gasOverride = it))
        }
        NumberField(
            "Gas price",
            s.manualGasPrice,
            prefix = "$",
            suffix = "/gal",
            help = if (s.gasOverride) "Always used" else "Used when live prices aren't available",
        ) { vm.saveSettings(s.copy(manualGasPrice = it)) }
        ToggleRow("Count vehicle wear", "Tires, oil, depreciation", s.wearEnabled) { vm.saveSettings(s.copy(wearEnabled = it)) }
        if (s.wearEnabled) {
            NumberField("Wear cost", s.wearPerMile, prefix = "$", suffix = "/mi") { vm.saveSettings(s.copy(wearPerMile = it)) }
        }

        Section("What's worth it")
        NumberField("Value of your time", s.valueOfTimePerHour, prefix = "$", suffix = "/hr", help = "Set to 0 to count gas only") {
            vm.saveSettings(s.copy(valueOfTimePerHour = it))
        }
        NumberField("Minimum savings", s.minSavings, prefix = "$", help = "Say BYE only when you'd save at least this much") {
            vm.saveSettings(s.copy(minSavings = it))
        }
        NumberField("Search radius", s.radiusMiles, suffix = "mi") { vm.saveSettings(s.copy(radiusMiles = it)) }
        ToggleRow("Include online stores", "Amazon, retailer websites, etc.", s.includeOnline) {
            vm.saveSettings(s.copy(includeOnline = it))
        }

        Section("Home")
        Text(
            if (s.home != null) "Home is set. Detours are measured against your drive home."
            else "Not set. Detours are counted as a round trip from the store you're in.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                homeMessage = "Getting location…"
                vm.setHomeToCurrent { ok -> homeMessage = if (ok) "Home set to your current location." else "Couldn't get your location." }
            }) { Text("Set home to here") }
            if (s.home != null) TextButton(onClick = { vm.saveSettings(s.copy(home = null)) }) { Text("Clear") }
        }
        homeMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }

        Section("Data")
        TextButton(onClick = vm::clearHistory) { Text("Clear history") }
        Text(
            "Price search (SerpApi): ${if (BuildConfig.SERPAPI_KEY.isBlank()) "not configured" else "configured"}\n" +
                "Live gas & drive times (Google Maps): ${if (BuildConfig.MAPS_API_KEY.isBlank()) "not configured" else "configured"}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Section(title: String) {
    HorizontalDivider(Modifier.padding(top = 8.dp))
    Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Text field that keeps the user's raw input and saves only valid numbers. */
@Composable
private fun NumberField(
    label: String,
    value: Double,
    prefix: String? = null,
    suffix: String? = null,
    help: String? = null,
    onValid: (Double) -> Unit,
) {
    var text by remember { mutableStateOf(format(value)) }
    // Sync when the stored value changes elsewhere (e.g. first load), but not while
    // the user is mid-edit on an equivalent value like "3." for 3.0.
    LaunchedEffect(value) { if (text.toDoubleOrNull() != value) text = format(value) }
    val invalid = text.toDoubleOrNull()?.let { it < 0 } ?: true
    OutlinedTextField(
        value = text,
        onValueChange = { v ->
            text = v.filter { it.isDigit() || it == '.' }
            text.toDoubleOrNull()?.takeIf { it >= 0 }?.let(onValid)
        },
        label = { Text(label) },
        prefix = prefix?.let { { Text(it) } },
        suffix = suffix?.let { { Text(it) } },
        supportingText = help?.let { { Text(it) } },
        isError = invalid,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

private fun format(d: Double): String =
    if (d % 1.0 == 0.0) "%.0f".format(Locale.US, d) else "%.2f".format(Locale.US, d).trimEnd('0')
