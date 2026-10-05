package com.buyorbye.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage

@Composable
fun ConfirmScreen(vm: MainViewModel) {
    val state by vm.confirm.collectAsStateWithLifecycle()
    val cs = state ?: return
    val price = cs.priceText.replace("$", "").trim().toDoubleOrNull()
    val canCheck = cs.query.isNotBlank() && price != null && price > 0

    val photo = remember(cs.photo) { cs.photo?.asImageBitmap() }

    // No scrolling: the photo takes whatever height is left and shrinks when the
    // keyboard opens, so it stays in view while the fields are being filled in.
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TopBar(title = "Is this it?", onBack = vm::back)

        if (photo != null) {
            Image(
                bitmap = photo,
                contentDescription = "Your photo of the product",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .clip(RoundedCornerShape(12.dp)),
            )
        } else if (cs.imageUrl != null) {
            AsyncImage(
                model = cs.imageUrl,
                contentDescription = null,
                modifier = Modifier.size(120.dp).clip(RoundedCornerShape(12.dp)).align(Alignment.CenterHorizontally),
            )
        }

        OutlinedTextField(
            value = cs.query,
            onValueChange = { vm.editConfirm(query = it) },
            label = { Text("Product") },
            placeholder = { Text("e.g. Pringles Original 5.2 oz") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        BarcodeStatusRow(cs, onRescan = vm::back)

        OutlinedTextField(
            value = cs.priceText,
            onValueChange = { v -> vm.editConfirm(priceText = v.filter { it.isDigit() || it == '.' }) },
            label = { Text("Price here") },
            prefix = { Text("$") },
            supportingText = { Text(if (cs.priceText.isEmpty()) "Couldn't read the tag. Enter the shelf price." else "Read from the shelf tag. Fix it if it's wrong.") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (canCheck) vm.runCheck() }),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Button(
            onClick = vm::runCheck,
            enabled = canCheck,
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Text("Check prices", style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** Says where the product name came from, so the user knows how much to trust it. */
@Composable
private fun BarcodeStatusRow(cs: ConfirmState, onRescan: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val (icon, tint, message) = when (cs.barcode) {
        BarcodeStatus.LOOKING_UP -> Triple(null, muted, "Looking up barcode ${cs.upc}…")
        BarcodeStatus.MATCHED -> Triple(Icons.Outlined.CheckCircle, BuyGreen, "Barcode matched")
        BarcodeStatus.MATCHED_BY_WEB ->
            Triple(Icons.Outlined.CheckCircle, BuyGreen, "Barcode matched by web search. Check the name.")
        BarcodeStatus.NOT_FOUND -> Triple(
            Icons.Outlined.ErrorOutline,
            ByeAmber,
            "Barcode ${cs.upc} not found." + if (cs.guessedFromLabel) " Name guessed from the label, check it." else " Type the name.",
        )
        BarcodeStatus.NONE -> Triple(
            Icons.Outlined.ErrorOutline,
            ByeAmber,
            if (cs.guessedFromLabel) "No barcode read. Name guessed from the label, check it." else "No barcode read. Type the name.",
        )
    }
    val canRescan = cs.barcode == BarcodeStatus.NONE || cs.barcode == BarcodeStatus.NOT_FOUND
    Row(Modifier.fillMaxWidth().padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon == null) {
            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
        } else {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(8.dp))
        Text(message, style = MaterialTheme.typography.bodySmall, color = muted, modifier = Modifier.weight(1f))
        if (canRescan) TextButton(onClick = onRescan) { Text("Rescan") }
    }
}
