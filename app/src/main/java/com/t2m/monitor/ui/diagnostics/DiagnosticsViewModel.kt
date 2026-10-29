package com.t2m.monitor.ui.diagnostics

import androidx.lifecycle.ViewModel
import com.t2m.monitor.domain.diagnostics.CollectorDiagnostic
import com.t2m.monitor.domain.diagnostics.DiagnosticError
import com.t2m.monitor.domain.diagnostics.DiagnosticsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

/**
 * 诊断页 ViewModel：只读统计与重置操作。
 */
@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
    private val repository: DiagnosticsRepository,
) : ViewModel() {

    val diagnostics: StateFlow<List<CollectorDiagnostic>> = repository.diagnostics

    val recentErrors: StateFlow<List<DiagnosticError>> = repository.recentErrors

    fun reset() {
        repository.reset()
    }
}
