package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.InputStream
import com.example.data.ProjectEntity
import com.example.data.VocalFileEntity

/**
 * Minimal ProjectViewModel stub to satisfy UI imports and provide compile-time
 * accessible StateFlow properties and no-op implementations for functions used
 * by the UI. Replace with real implementations as needed.
 */
class ProjectViewModel : ViewModel() {
    // Navigation / UI state
    private val _currentScreen = MutableStateFlow<Screen>(Screen.Dashboard)
    val currentScreen: StateFlow<Screen> = _currentScreen

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage

    // Projects / vocals
    private val _allProjects = MutableStateFlow<List<ProjectEntity>>(emptyList())
    val allProjects: StateFlow<List<ProjectEntity>> = _allProjects

    private val _currentProject = MutableStateFlow<ProjectEntity?>(null)
    val currentProject: StateFlow<ProjectEntity?> = _currentProject

    private val _vocals = MutableStateFlow<List<VocalFileEntity>>(emptyList())
    val vocals: StateFlow<List<VocalFileEntity>> = _vocals

    // Playback / waveform
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _playingPath = MutableStateFlow<String?>(null)
    val playingPath: StateFlow<String?> = _playingPath

    private val _waveformState = MutableStateFlow<Map<String, FloatArray?>>(emptyMap())
    val waveformState: StateFlow<Map<String, FloatArray?>> = _waveformState

    private val _currentVocal = MutableStateFlow<VocalFileEntity?>(null)
    val currentVocal: StateFlow<VocalFileEntity?> = _currentVocal

    // Navigation
    fun navigateTo(screen: Screen) {
        _currentScreen.value = screen
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    // Project operations (no-op / placeholder implementations)
    fun createProject(name: String) {
        viewModelScope.launch {
            _statusMessage.value = "Vytvořen projekt: $name"
            // TODO: persist project and update _allProjects/_currentProject
        }
    }

    fun deleteProject(project: ProjectEntity) {
        viewModelScope.launch {
            _statusMessage.value = "Projekt smazán"
            // TODO: delete from storage and update _allProjects
        }
    }

    fun importBeatFile(projectId: Long, name: String, stream: InputStream) {
        viewModelScope.launch {
            _statusMessage.value = "Beat importován: $name"
            // TODO: save file, update project.beatFilePath and _currentProject/_allProjects
        }
    }

    fun importMultipleVocalFiles(projectId: Long, files: List<Pair<String, InputStream>>) {
        viewModelScope.launch {
            _statusMessage.value = "Import vokálů: ${files.size} souborů"
            // TODO: save files and update _vocals/_currentProject
        }
    }

    fun createSyntheticProjectAssets(projectId: Long) {
        viewModelScope.launch {
            _statusMessage.value = "Vygenerováno demo"
            // TODO: create demo beat/vocals and update state
        }
    }

    fun loadWaveform(path: String) {
        viewModelScope.launch {
            // Ensure key exists in waveform map; real implementation should generate waveform floats
            val current = _waveformState.value.toMutableMap()
            if (!current.containsKey(path)) {
                current[path] = null
                _waveformState.value = current
            }
        }
    }

    fun playAudio(path: String) {
        viewModelScope.launch {
            if (_isPlaying.value && _playingPath.value == path) {
                // stop
                _isPlaying.value = false
                _playingPath.value = null
            } else {
                _isPlaying.value = true
                _playingPath.value = path
            }
        }
    }

    fun updateProjectBpm(id: Long, bpm: Double) {
        viewModelScope.launch {
            _statusMessage.value = "Aktualizováno BPM na $bpm"
            // TODO: find project by id and update BPM, then update _allProjects/_currentProject
        }
    }

    fun mixActiveProject() {
        viewModelScope.launch {
            _statusMessage.value = "Mixování spuštěno"
            // TODO: perform mixing, write file, set project.mixedFilePath
        }
    }

    fun deleteVocal(vocal: VocalFileEntity) {
        viewModelScope.launch {
            _statusMessage.value = "Vokál smazán"
            // TODO: remove vocal from storage and update _vocals/_currentProject
        }
    }

    // Other helpers can be added as needed by UI
}
