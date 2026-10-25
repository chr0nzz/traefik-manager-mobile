package dev.chr0nzz.traefikmanager

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.chr0nzz.traefikmanager.data.model.UiText

fun UiText?.english(): String? =
    this?.resolve(ApplicationProvider.getApplicationContext<Context>().resources)
