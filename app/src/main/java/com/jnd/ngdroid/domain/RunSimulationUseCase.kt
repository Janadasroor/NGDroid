package com.jnd.ngdroid.domain

import com.jnd.ngdroid.engine.SimulationRepository

class RunSimulationUseCase(private val repository: SimulationRepository) {

    operator fun invoke() {
        val currentText = repository.netlistText.value
        if (currentText.trim().isEmpty()) {
            return
        }
        repository.runSimulation()
    }
}
