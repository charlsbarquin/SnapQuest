package com.charlsemilbarquin.frameandfact.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import com.charlsemilbarquin.frameandfact.R

@Composable
fun SnapQuestWordmark(
    modifier: Modifier = Modifier,
    markSize: Dp = 40.dp,
    textStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.titleLarge
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SnapQuestMark(modifier = Modifier.size(markSize))
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)) {
                    append("Snap")
                }
                withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)) {
                    append("Quest")
                }
            },
            style = textStyle,
            maxLines = 1
        )
    }
}

@Composable
fun SnapQuestMark(modifier: Modifier = Modifier, contentDescription: String? = null) {
    val isDarkSurface = MaterialTheme.colorScheme.background.luminance() < 0.5f
    Image(
        painter = painterResource(if (isDarkSurface) R.drawable.ic_snapquest_mark_light else R.drawable.ic_snapquest_mark),
        contentDescription = contentDescription,
        modifier = modifier
    )
}

@Composable
fun SnapQuestBackButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val arrowColor = MaterialTheme.colorScheme.onSurface
    Box(
        modifier = modifier
            .size(48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = "Navigate back"
                role = Role.Button
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(22.dp)) {
            val stroke = 2.dp.toPx()
            val centerY = size.height / 2f
            drawLine(
                color = arrowColor,
                start = androidx.compose.ui.geometry.Offset(size.width * .84f, centerY),
                end = androidx.compose.ui.geometry.Offset(size.width * .2f, centerY),
                strokeWidth = stroke
            )
            drawLine(
                color = arrowColor,
                start = androidx.compose.ui.geometry.Offset(size.width * .2f, centerY),
                end = androidx.compose.ui.geometry.Offset(size.width * .48f, size.height * .22f),
                strokeWidth = stroke
            )
            drawLine(
                color = arrowColor,
                start = androidx.compose.ui.geometry.Offset(size.width * .2f, centerY),
                end = androidx.compose.ui.geometry.Offset(size.width * .48f, size.height * .78f),
                strokeWidth = stroke
            )
        }
    }
}
