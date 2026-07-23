package app.guitar.app

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType

/**
 * A numeric value TEXT that opens a type-in dialog on DOUBLE-TAP — the fast
 * path for setting an exact number instead of dragging its slider (mirrors
 * chorect-web's double-clickable slider labels). [value] is the current
 * number; [onSet] receives the typed value clamped to [min]..[max].
 */
@Composable
fun NumericValueText(
    text: String,
    value: Float,
    min: Float,
    max: Float,
    onSet: (Float) -> Unit,
    style: TextStyle = MaterialTheme.typography.labelMedium,
    fontWeight: FontWeight? = null,
    color: Color = Color.Unspecified,
    maxLines: Int = 1,
    /** Optional single-tap action (e.g. audition) — lives in the SAME gesture
     *  detector so it doesn't swallow the double-tap. */
    onTap: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var dialog by remember { mutableStateOf(false) }
    Text(
        text,
        style = style,
        fontWeight = fontWeight,
        color = color,
        maxLines = maxLines,
        modifier = modifier.pointerInput(onTap) {
            detectTapGestures(
                onDoubleTap = { dialog = true },
                onTap = onTap?.let { { _ -> it() } },
            )
        },
    )
    if (dialog) {
        var input by remember { mutableStateOf(if (value % 1f == 0f) value.toInt().toString() else value.toString()) }
        AlertDialog(
            onDismissRequest = { dialog = false },
            title = { Text("Set value (${min.toInt()}–${max.toInt()})") },
            text = {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    input.replace(',', '.').toFloatOrNull()?.let { onSet(it.coerceIn(min, max)) }
                    dialog = false
                }) { Text("Set") }
            },
            dismissButton = { TextButton(onClick = { dialog = false }) { Text("Cancel") } },
        )
    }
}
