package dev.chr0nzz.traefikmanager.ui.nav

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class NavEditorRequests @Inject constructor() {

    private val _open = MutableStateFlow(false)
    val open: StateFlow<Boolean> = _open.asStateFlow()

    fun request() {
        _open.value = true
    }

    fun consume() {
        _open.value = false
    }
}
