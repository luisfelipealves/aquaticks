package com.luisfelipe.aquaticks.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.luisfelipe.aquaticks.aquaApp
import com.luisfelipe.aquaticks.data.DaySnapshot
import com.luisfelipe.aquaticks.data.Settings
import com.luisfelipe.aquaticks.refreshSurfaces
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class WaterViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application.aquaApp
    val repository = app.repository
    private val day = MutableStateFlow(repository.today())
    val snapshot: StateFlow<DaySnapshot?> = day.flatMapLatest(repository::observe)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    private val _homeRequests = MutableStateFlow(0)
    val homeRequests = _homeRequests.asStateFlow()
    fun openHome() { _homeRequests.value++; refresh() }
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()
    fun dismissMessage() { _message.value = null }
    init { refresh() }
    fun refresh() {
        viewModelScope.launch {
            try {
                repository.ensureToday()
                day.value = repository.today()
                app.reminders.reconcile()
            } catch (_: Exception) { _message.value = "Não foi possível carregar. Tente novamente." }
        }
    }
    fun add(amount: Int, operationId: String = UUID.randomUUID().toString()) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                val added = repository.add(amount, operationId)
                day.value = repository.today()
                if (added) _message.value = "Boa!\n+$amount ml registrados"
                // A UI/system refresh failure must never turn a committed intake into "not saved".
                runCatching {
                    app.refreshSurfaces()
                    app.reminders.reconcile()
                }.onFailure { android.util.Log.e("AquaTicks", "Water saved; surface refresh failed", it) }
            } catch (_: Exception) { _message.value = "Não foi possível salvar. Tente novamente." }
            finally { _busy.value = false }
        }
    }
    fun update(transform: (Settings) -> Settings) {
        viewModelScope.launch {
            try {
                repository.update(transform)
                day.value = repository.today()
                app.reminders.reconcile()
                app.refreshSurfaces()
            } catch (_: Exception) { _message.value = "Não foi possível salvar a configuração." }
        }
    }
}
