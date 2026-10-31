package dev.field.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.field.app.ui.theme.Accent
import dev.field.app.ui.theme.AccentDark
import dev.field.app.ui.theme.Bg
import dev.field.app.ui.theme.Border
import dev.field.app.ui.theme.Danger
import dev.field.app.ui.theme.Surface
import dev.field.app.ui.theme.SurfaceRaised
import dev.field.app.ui.theme.TextPrimary
import dev.field.app.ui.theme.TextSecondary

/** Mindustry-style flat panel: dark surface, 1dp border, 2dp accent top edge. */
@Composable
fun LabPanel(
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .background(Surface)
            .border(1.dp, Border)
            .padding(top = 4.dp),
    ) {
        if (accent) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(Accent),
            )
        }
        content()
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        color = TextSecondary,
        fontSize = 10.sp,
        letterSpacing = 1.2.sp,
        fontWeight = FontWeight.Medium,
        modifier = modifier.padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

@Composable
fun LabButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    danger: Boolean = false,
    enabled: Boolean = true,
) {
    val background = when {
        !enabled -> Surface
        accent -> Accent
        else -> SurfaceRaised
    }
    val textColor = when {
        !enabled -> TextSecondary.copy(alpha = 0.5f)
        accent -> Bg
        danger -> Danger
        else -> TextPrimary
    }
    Box(
        modifier = modifier
            .height(40.dp)
            .background(background, RectangleShape)
            .border(1.dp, if (accent) AccentDark else Border, RectangleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 12.sp,
            letterSpacing = 0.6.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
fun LabIconButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
) {
    Box(
        modifier = modifier
            .size(38.dp)
            .background(if (selected) Accent else SurfaceRaised, RectangleShape)
            .border(1.dp, if (selected) AccentDark else Border, RectangleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = when {
                !enabled -> TextSecondary.copy(alpha = 0.4f)
                selected -> Bg
                else -> TextPrimary
            },
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
fun LabSlider(
    label: String,
    value: Double,
    range: ClosedFloatingPointRange<Double>,
    onValueChange: (Double) -> Unit,
    modifier: Modifier = Modifier,
    valueText: String = "%.2f".format(value),
    onFinished: () -> Unit = {},
) {
    Column(modifier = modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, color = TextSecondary, fontSize = 11.sp)
            Text(valueText, color = TextPrimary, fontSize = 11.sp)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toDouble()) },
            onValueChangeFinished = onFinished,
            valueRange = range.start.toFloat()..range.endInclusive.toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = Accent,
                activeTrackColor = Accent,
                inactiveTrackColor = Border,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
                .height(28.dp),
        )
    }
}

@Composable
fun LabToggle(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(horizontal = 10.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            color = TextPrimary,
            fontSize = 12.sp,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Bg,
                checkedTrackColor = Accent,
                uncheckedThumbColor = TextSecondary,
                uncheckedTrackColor = SurfaceRaised,
                uncheckedBorderColor = Border,
            ),
        )
    }
}

@Composable
fun KeyValueRow(key: String, value: String, modifier: Modifier = Modifier, valueColor: Color = TextPrimary) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(key, color = TextSecondary, fontSize = 11.sp)
        Text(value, color = valueColor, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun Chip(text: String, highlighted: Boolean = false, modifier: Modifier = Modifier) {
    Box(
        modifier
            .background(if (highlighted) Accent else SurfaceRaised)
            .border(1.dp, if (highlighted) AccentDark else Border, RectangleShape)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text,
            color = if (highlighted) Bg else TextSecondary,
            fontSize = 10.sp,
            letterSpacing = 0.6.sp,
        )
    }
}

@Composable
fun DividerLine(modifier: Modifier = Modifier) {
    Spacer(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Border.copy(alpha = 0.6f)),
    )
}

@Composable
fun TitleText(text: String, modifier: Modifier = Modifier, color: Color = TextPrimary) {
    Text(
        text = text,
        color = color,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 2.sp,
        modifier = modifier,
    )
}

@Composable
fun BodyText(text: String, modifier: Modifier = Modifier, color: Color = TextSecondary) {
    Text(
        text = text,
        color = color,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        modifier = modifier,
    )
}

val PanelWidth = 260.dp
val SlotSize = 46.dp
val ToolbarHeight = 58.dp
val ButtonSpacing = 8.dp
