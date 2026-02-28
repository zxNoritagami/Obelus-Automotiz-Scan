package com.obelus.presentation.viewmodel

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.obelus.analysis.stream.DataStreamAnalyzer
import com.obelus.diagnostic.engine.DiagnosticEngine
import com.obelus.domain.model.DiagnosticReport
import com.obelus.domain.model.FreezeFrameData
import com.obelus.presentation.report.DiagnosticPdfGenerator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * ViewModel que orquestra el flujo de diagnóstico avanzado.
 * Conecta los motores de análisis y diagnóstico con la interfaz de usuario.
 */
@HiltViewModel
class DiagnosticViewModel @Inject constructor(
    private val dataStreamAnalyzer: DataStreamAnalyzer,
    private val diagnosticEngine: DiagnosticEngine,
    private val pdfGenerator: DiagnosticPdfGenerator
) : ViewModel() {

    private val _uiState = MutableStateFlow(DiagnosticUiState())
    val uiState: StateFlow<DiagnosticUiState> = _uiState.asStateFlow()

    private var currentReport: DiagnosticReport? = null

    /**
     * Ejecuta el análisis de diagnóstico completo basado en los DTCs detectados.
     */
    fun runAnalysis(activeDtcs: List<String>) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            
            try {
                val report = diagnosticEngine.analyze(activeDtcs)
                currentReport = report
                
                // Cálculo del Health Score: (1 - Probabilidad de la falla más alta) * 100
                val highestProb = report.findings.firstOrNull()?.posteriorProbability ?: 0.0
                val healthScore = ((1.0 - highestProb) * 100).roundToInt().coerceIn(0, 100)
                
                // Detección de alertas críticas (Severity >= 5)
                val hasCritical = report.findings.any { it.severityLevel >= 5 }

                _uiState.update { 
                    it.copy(
                        findings = report.findings,
                        topFinding = report.findings.firstOrNull(),
                        vehicleHealthScore = healthScore,
                        hasCriticalAlert = hasCritical,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isLoading = false, 
                        errorMessage = e.message ?: "Error desconocido en el motor de diagnóstico"
                    )
                }
            }
        }
    }

    /**
     * Simula u obtiene el cuadro congelado (Freeze Frame) para un DTC.
     * En una implementación real, esto consultaría el Modo 02 vía OBD2.
     */
    fun loadFreezeFrame(dtcCode: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            // Simulación de delay de red OBD2
            delay(1000)
            
            val mockData = FreezeFrameData(
                dtcCode = dtcCode,
                sensorValues = mapOf(
                    "RPM" to 2450.0,
                    "COOLANT_TEMP" to 105.0, // Sobrecalentado
                    "ENGINE_LOAD" to 85.0,
                    "VEHICLE_SPEED" to 110.0,
                    "FUEL_PRESSURE" to 1800.0,
                    "THROTTLE_POS" to 45.0
                )
            )
            
            _uiState.update { it.copy(isLoading = false, selectedFreezeFrame = mockData) }
        }
    }

    fun clearFreezeFrame() {
        _uiState.update { it.copy(selectedFreezeFrame = null) }
    }

    /**
     * Genera y guarda el reporte PDF profesional.
     */
    fun exportPdfReport(context: Context, vin: String?, model: String?) {
        val report = currentReport ?: return
        
        viewModelScope.launch {
            try {
                val file = withContext(Dispatchers.IO) {
                    pdfGenerator.generateReport(context, report, vin, model)
                }
                Toast.makeText(context, "Reporte guardado en: ${file.absolutePath}", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Error al generar PDF: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
