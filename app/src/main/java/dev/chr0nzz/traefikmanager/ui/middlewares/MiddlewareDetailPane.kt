package dev.chr0nzz.traefikmanager.ui.middlewares

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.chr0nzz.traefikmanager.data.model.MiddlewareDef
import dev.chr0nzz.traefikmanager.data.model.MiddlewareTemplates
import dev.chr0nzz.traefikmanager.ui.components.DetailRow
import dev.chr0nzz.traefikmanager.ui.components.MessageState
import dev.chr0nzz.traefikmanager.ui.components.SectionLabel
import dev.chr0nzz.traefikmanager.ui.components.TmCard
import dev.chr0nzz.traefikmanager.ui.components.TooltipIconButton
import dev.chr0nzz.traefikmanager.ui.components.YamlPreview
import dev.chr0nzz.traefikmanager.ui.theme.LocalTmPalette
import dev.chr0nzz.traefikmanager.ui.theme.MonoFamily
import dev.chr0nzz.traefikmanager.ui.theme.TmSpacing
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import dev.chr0nzz.traefikmanager.R

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MiddlewareDetailPane(
    middleware: MiddlewareDef?,
    usageCount: Int,
    showBack: Boolean,
    onBack: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
    onEdit: (MiddlewareDef) -> Unit = {},
    onDelete: (MiddlewareDef) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (middleware == null) {
        MessageState(
            icon = Icons.Outlined.TouchApp,
            headline = stringResource(R.string.middlewares_select),
            body = stringResource(R.string.middlewares_select_body),
            modifier = modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(top = TmSpacing.xxl),
        )
        return
    }

    val palette = LocalTmPalette.current
    val clipboard = LocalClipboardManager.current
    val kind = MiddlewareTemplates.kindOf(middleware.yaml)
    val isTcp = middleware.type == "tcp"

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(contentPadding)
                .padding(start = TmSpacing.lg, end = TmSpacing.lg, top = TmSpacing.lg, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(TmSpacing.md),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(TmSpacing.sm),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (showBack) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.middlewares_back))
                    }
                }
                Text(
                    text = if (isTcp) "TCP" else "HTTP",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (isTcp) palette.green else palette.muted,
                )
                Text(
                    text = middleware.name,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { heading() },
                )
            }

            TmCard {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(TmSpacing.sm),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = TmSpacing.xs),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Layers,
                        contentDescription = null,
                        tint = palette.purple,
                        modifier = Modifier.size(15.dp),
                    )
                    Text(
                        text = stringResource(R.string.common_label_middleware),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.semantics { heading() },
                    )
                }
                DetailRow(stringResource(R.string.common_label_name), middleware.name, mono = true)
                DetailRow(stringResource(R.string.common_label_protocol), if (isTcp) "TCP" else "HTTP")
                DetailRow(stringResource(R.string.middlewares_kind), kind, mono = true)
                if (middleware.configFile.isNotEmpty()) {
                    DetailRow(stringResource(R.string.common_label_config_file), middleware.configFile, mono = true)
                }
                DetailRow(
                    label = stringResource(R.string.middlewares_usage),
                    value = if (usageCount == 0) {
                        stringResource(R.string.middlewares_unused)
                    } else {
                        pluralStringResource(R.plurals.middlewares_used_by, usageCount, usageCount)
                    },
                    last = true,
                )
            }

            SectionLabel(stringResource(R.string.middlewares_configuration))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(palette.card)
                    .padding(TmSpacing.md),
            ) {
                YamlPreview(source = middleware.yaml)
            }
        }

        HorizontalFloatingToolbar(
            expanded = true,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(contentPadding)
                .padding(bottom = TmSpacing.lg),
        ) {
            TooltipIconButton(
                label = stringResource(R.string.middlewares_edit),
                icon = Icons.Outlined.Edit,
                onClick = { onEdit(middleware) },
            )
            TooltipIconButton(
                label = stringResource(R.string.middlewares_delete),
                icon = Icons.Outlined.Delete,
                onClick = { onDelete(middleware) },
            )
            TooltipIconButton(
                label = stringResource(R.string.middlewares_copy_yaml),
                icon = Icons.Outlined.ContentCopy,
                onClick = { clipboard.setText(AnnotatedString(middleware.yaml)) },
            )
        }
    }
}
