package app.letostudio.gameemulator.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.letostudio.gameemulator.R
import app.letostudio.gameemulator.ui.theme.RnRadius
import app.letostudio.gameemulator.ui.theme.RnTheme

/** Pill search field with leading search icon and trailing close. */
@Composable
fun RnSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    onClose: () -> Unit,
    closeDescription: String,
    modifier: Modifier = Modifier,
) {
    val c = RnTheme.colors
    val focus = remember { FocusRequester() }
    val fm = LocalFocusManager.current
    LaunchedEffect(Unit) { focus.requestFocus() }
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .background(c.surface, RoundedCornerShape(RnRadius.pill))
            .border(1.dp, c.line, RoundedCornerShape(RnRadius.pill))
            .padding(start = 14.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RnIcon(R.drawable.ms_search, tint = c.inkMuted, size = 20.dp)
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(placeholder, style = RnTheme.type.body, color = c.inkMuted)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = RnTheme.type.body.copy(color = c.ink),
                cursorBrush = SolidColor(c.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { fm.clearFocus() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        }
        RnIconButton(R.drawable.ms_close, closeDescription, onClose, size = 40.dp, iconSize = 20.dp)
    }
}

/** Labelled outlined text field (rename dialog, cheat code). */
@Composable
fun RnTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    monospace: Boolean = false,
    minLines: Int = 1,
) {
    val c = RnTheme.colors
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = RnTheme.type.caption, color = c.inkMuted)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            minLines = minLines,
            textStyle = (if (monospace) TextStyle(fontFamily = FontFamily.Monospace) else RnTheme.type.body).copy(color = c.ink),
            cursorBrush = SolidColor(c.primary),
            modifier = Modifier
                .fillMaxWidth()
                .background(c.surfaceSunken, RoundedCornerShape(RnRadius.md))
                .border(1.dp, c.line, RoundedCornerShape(RnRadius.md))
                .padding(12.dp),
        )
    }
}
