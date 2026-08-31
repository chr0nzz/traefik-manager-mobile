package dev.chr0nzz.traefikmanager.ui.components

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.core.graphics.PathParser
import dev.chr0nzz.traefikmanager.ui.theme.LocalTmPalette
import kotlin.math.ln
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val MAP_ASSET = "world-map.svg"

private const val VIEW_X = 0f
private const val VIEW_Y = 10f
private const val VIEW_W = 1000f
private const val VIEW_H = 415f

private data class CountryShape(val code: String, val path: Path)

private object WorldMapShapes {

    @Volatile
    private var cached: List<CountryShape>? = null

    private val ENTRY = Regex("""data-cc="([A-Z]{2})"[^>]*?\sd="([^"]+)"""")

    suspend fun load(context: Context): List<CountryShape> {
        cached?.let { return it }
        return withContext(Dispatchers.IO) {
            cached ?: runCatching {
                val svg = context.assets.open(MAP_ASSET).bufferedReader().use { it.readText() }
                val shapes = mutableListOf<CountryShape>()
                ENTRY.findAll(svg).forEach { match ->
                    val code = match.groupValues[1]
                    val data = match.groupValues[2]
                    val parsed = runCatching {
                        PathParser.createPathFromPathData(data).asComposePath()
                    }.getOrNull()
                    if (parsed != null) shapes += CountryShape(code, parsed)
                }
                shapes.toList()
            }.getOrDefault(emptyList()).also { cached = it }
        }
    }
}

@Composable
fun WorldMap(
    counts: Map<String, Int>,
    modifier: Modifier = Modifier,
    selected: String? = null,
) {
    val context = LocalContext.current
    var shapes by remember { mutableStateOf<List<CountryShape>>(emptyList()) }
    val palette = LocalTmPalette.current

    LaunchedEffect(Unit) { shapes = WorldMapShapes.load(context) }

    if (shapes.isEmpty()) return

    val max = counts.values.maxOrNull() ?: 0
    val label = if (counts.isEmpty()) {
        "World map, no located traffic"
    } else {
        "World map, ${counts.size} countries with traffic"
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(VIEW_W / VIEW_H)
            .semantics { contentDescription = label },
    ) {
        val factor = size.width / VIEW_W
        scale(scale = factor, pivot = androidx.compose.ui.geometry.Offset.Zero) {
            translate(left = -VIEW_X, top = -VIEW_Y) {
                shapes.forEach { shape ->
                    val count = counts[shape.code] ?: 0
                    val colour = when {
                        count <= 0 -> palette.muted.copy(alpha = 0.18f)
                        else -> {
                            val ratio = ln((count + 1).toDouble()) / ln((max + 1).toDouble())
                            palette.blue.copy(alpha = (0.28f + 0.72f * ratio.toFloat()).coerceIn(0.28f, 1f))
                        }
                    }
                    drawPath(path = shape.path, color = colour, style = Fill)
                    if (shape.code == selected) {
                        drawPath(
                            path = shape.path,
                            color = palette.blue,
                            style = Stroke(width = 2f / factor),
                        )
                    }
                }
            }
        }
    }
}
