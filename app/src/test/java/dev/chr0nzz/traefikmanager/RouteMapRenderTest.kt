package dev.chr0nzz.traefikmanager

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dev.chr0nzz.traefikmanager.data.model.Route
import dev.chr0nzz.traefikmanager.data.model.RouteMapBuilder
import dev.chr0nzz.traefikmanager.ui.routemap.RouteMapCanvas
import dev.chr0nzz.traefikmanager.ui.theme.TmTheme
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RouteMapRenderTest {

    private fun strings(vararg values: String): JsonElement =
        Json.parseToJsonElement(values.joinToString(prefix = "[", postfix = "]") { "\"$it\"" })

    private fun route(name: String, middlewares: List<String> = emptyList()) = Route(
        id = name,
        name = name,
        serviceName = "$name-service",
        target = "http://10.0.0.1:80",
        entryPoints = strings("websecure"),
        middlewares = strings(*middlewares.toTypedArray()),
    )

    @Test
    fun `the canvas composes and draws without throwing`() {
        val activity = Robolectric.buildActivity(TestActivity::class.java).setup().get()
        val graph = RouteMapBuilder.build(
            listOf(route("blog", listOf("auth")), route("api"), route("git")),
        )
        val view = ComposeView(activity).apply {
            setViewTreeLifecycleOwner(activity)
            setViewTreeSavedStateRegistryOwner(activity)
            setViewTreeViewModelStoreOwner(activity)
            setContent {
                TmTheme {
                    RouteMapCanvas(graph = graph, focusIds = emptySet(), onTap = {})
                }
            }
        }
        activity.setContentView(view)
        ShadowLooper.idleMainLooper()

        val spec = android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.EXACTLY)
        val tall = android.view.View.MeasureSpec.makeMeasureSpec(1920, android.view.View.MeasureSpec.EXACTLY)
        view.measure(spec, tall)
        view.layout(0, 0, 1080, 1920)
        ShadowLooper.idleMainLooper()
        view.draw(Canvas(Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888)))
        ShadowLooper.idleMainLooper()
    }

    class TestActivity : Activity(), ViewModelStoreOwner, SavedStateRegistryOwner {
        private val controller = SavedStateRegistryController.create(this)
        private val registry = androidx.lifecycle.LifecycleRegistry(this)
        override val viewModelStore = ViewModelStore()
        override val savedStateRegistry get() = controller.savedStateRegistry
        override val lifecycle: androidx.lifecycle.Lifecycle get() = registry

        override fun onCreate(savedInstanceState: android.os.Bundle?) {
            controller.performRestore(null)
            super.onCreate(savedInstanceState)
            registry.currentState = androidx.lifecycle.Lifecycle.State.RESUMED
        }
    }
}
