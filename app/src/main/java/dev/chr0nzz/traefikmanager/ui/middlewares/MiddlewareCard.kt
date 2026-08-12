package dev.chr0nzz.traefikmanager.ui.middlewares

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.CompareArrows
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.chr0nzz.traefikmanager.data.model.MiddlewareDef
import dev.chr0nzz.traefikmanager.data.model.MiddlewareTemplates
import dev.chr0nzz.traefikmanager.ui.components.CardDivider
import dev.chr0nzz.traefikmanager.ui.components.IconTile
import dev.chr0nzz.traefikmanager.ui.components.TmCard
import dev.chr0nzz.traefikmanager.ui.components.YamlPreview
import dev.chr0nzz.traefikmanager.ui.theme.LocalTmPalette
import dev.chr0nzz.traefikmanager.ui.theme.MonoFamily
import dev.chr0nzz.traefikmanager.ui.theme.TmSpacing

fun middlewareIcon(kind: String): ImageVector = when (kind) {
    "basicAuth", "digestAuth", "forwardAuth" -> Icons.Outlined.Lock
    "headers" -> Icons.Outlined.Code
    "ipAllowList", "ipWhiteList" -> Icons.Outlined.FilterAlt
    "rateLimit", "inFlightReq", "inFlightConn" -> Icons.Outlined.Speed
    "redirectScheme", "redirectRegex" -> Icons.Outlined.SwapHoriz
    "stripPrefix", "addPrefix", "replacePath", "replacePathRegex" -> Icons.Outlined.Link
    "compress" -> Icons.Outlined.Air
    "retry", "circuitBreaker", "buffering" -> Icons.Outlined.Bolt
    "chain" -> Icons.Outlined.CompareArrows
    "plugin" -> Icons.Outlined.Extension
    else -> Icons.Outlined.Shield
}

@Composable
fun MiddlewareCard(
    middleware: MiddlewareDef,
    usageCount: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalTmPalette.current
    val kind = MiddlewareTemplates.kindOf(middleware.yaml)
    val isTcp = middleware.type == "tcp"

    TmCard(
        modifier = modifier,
        accentColor = if (selected) palette.purple else null,
        onClick = onClick,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TmSpacing.sm),
            modifier = Modifier.fillMaxWidth(),
        ) {
            IconTile(icon = middlewareIcon(kind))
            Box(modifier = Modifier.weight(1f)) {
                androidx.compose.foundation.layout.Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(TmSpacing.xs),
                    ) {
                        if (isTcp) {
                            Text(
                                text = "TCP",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = palette.green,
                            )
                        }
                        Text(
                            text = middleware.name,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text = kind,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFamily),
                        color = palette.muted,
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = TmSpacing.sm)
                .clip(RoundedCornerShape(6.dp))
                .background(palette.bg)
                .padding(horizontal = TmSpacing.sm, vertical = TmSpacing.xs)
                .heightIn(max = 62.dp),
        ) {
            YamlPreview(source = middleware.yaml.lines().take(4).joinToString("\n"))
        }

        CardDivider(modifier = Modifier.padding(top = TmSpacing.sm))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = TmSpacing.xs),
        ) {
            Text(
                text = when (usageCount) {
                    0 -> "unused"
                    1 -> "used by 1 route"
                    else -> "used by $usageCount routes"
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (usageCount == 0) palette.yellow else palette.muted,
                modifier = Modifier.weight(1f),
            )
            if (middleware.configFile.isNotEmpty()) {
                Text(
                    text = middleware.configFile.removeSuffix(".yml").removeSuffix(".yaml"),
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFamily),
                    color = palette.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
