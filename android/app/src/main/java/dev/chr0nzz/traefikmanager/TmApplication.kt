package dev.chr0nzz.traefikmanager

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import dagger.hilt.android.HiltAndroidApp
import dev.chr0nzz.traefikmanager.data.api.IconAuthInterceptor
import javax.inject.Inject
import okhttp3.OkHttpClient

@HiltAndroidApp
class TmApplication : Application(), SingletonImageLoader.Factory {

    @Inject
    lateinit var okHttpClient: OkHttpClient

    @Inject
    lateinit var iconAuthInterceptor: IconAuthInterceptor

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        val imageClient = lazy {
            okHttpClient.newBuilder()
                .addInterceptor(iconAuthInterceptor)
                .followRedirects(true)
                .build()
        }
        return ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { imageClient.value }))
            }
            .build()
    }
}
