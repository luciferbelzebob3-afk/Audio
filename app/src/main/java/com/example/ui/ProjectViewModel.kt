package com.example.ui

import android.app.Application
import android.media.MediaPlayer
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AudioEngine
import com.example.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

sealed interface Screen {
    object Dashboard : Screen
    data class ProjectWorkspace(val projectId: Long) : Screen
    data class VocalProcessor(val projectId: Long, val vocalFileId: Long) : Screen
}

class ProjectViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "ProjectViewModel"
    private val database = ProjectDatabase.getDatabase(application)
    private val repository = ProjectRepository(database.projectDao())

    // UI States
    val allProjects: StateFlow<List<ProjectEntity>> = repository.allProjects
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _currentScreen = MutableStateFlow<Screen>(Screen.Dashboard)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    private val _vocals = MutableStateFlow<List<VocalFileEntity>>(emptyList())
    val vocals: StateFlow<List<VocalFileEntity>> = _vocals.asStateFlow()

    private val _currentProject = MutableStateFlow<ProjectEntity?>(null)
    val currentProject: StateFlow<ProjectEntity?> = _currentProject.asStateFlow()

    private val _currentVocal = MutableStateFlow<VocalFileEntity?>(null)
    val currentVocal: StateFlow<VocalFileEntity?> = _currentVocal.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    // Media Player State
    private var mediaPlayer: MediaPlayer? = null
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _playingPath = MutableStateFlow<String?>(null)
    val playingPath: StateFlow<String?> = _playingPath.asStateFlow()

    init {
        // Monitor current screen and update project/vocal context accordingly
        viewModelScope.launch {
            _currentScreen.collect { screen ->
                stopAudio()
                when (screen) {
                    is Screen.Dashboard -> {
                        _currentProject.value = null
                        _currentVocal.value = null
                        _vocals.value = emptyList()
                    }
                    is Screen.ProjectWorkspace -> {
                        loadProjectDetails(screen.projectId)
                    }
                    is Screen.VocalProcessor -> {
                        loadVocalDetails(screen.vocalFileId)
                    }
                }
            }
        }
    }

    fun navigateTo(screen: Screen) {
        _currentScreen.value = screen
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    private fun loadProjectDetails(projectId: Long) {
        viewModelScope.launch {
            val project = repository.getProjectById(projectId)
            _currentProject.value = project
            if (project != null) {
                repository.getVocalsForProject(projectId).collect { list ->
                    _vocals.value = list
                }
            }
        }
    }

    private suspend fun loadVocalDetails(vocalId: Long) {
        val vocal = repository.getVocalById(vocalId)
        _currentVocal.value = vocal
    }

    fun createProject(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val cleanName = name.trim().ifEmpty { "Nový Projekt" }
            val newProj = ProjectEntity(name = cleanName)
            val projectId = repository.insertProject(newProj)
            
            // Create dedicated project directory
            val projDir = getProjectDir(projectId)
            if (!projDir.exists()) projDir.mkdirs()

            withContext(Dispatchers.Main) {
                navigateTo(Screen.ProjectWorkspace(projectId))
            }
        }
    }

    fun deleteProject(project: ProjectEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            // Delete actual files in project dir
            val projDir = getProjectDir(project.id)
            if (projDir.exists()) {
                projDir.deleteRecursively()
            }
            repository.deleteProject(project)
            withContext(Dispatchers.Main) {
                if (_currentProject.value?.id == project.id) {
                    navigateTo(Screen.Dashboard)
                }
            }
        }
    }

    /**
     * Imports an instrumental beat file and automatically detects its BPM
     */
    fun importBeatFile(projectId: Long, originalName: String, inputStream: InputStream) {
        viewModelScope.launch {
            _isLoading.value = true
            _statusMessage.value = "Importuji a analyzuji instrumentální beat..."

            withContext(Dispatchers.IO) {
                try {
                    val project = repository.getProjectById(projectId) ?: return@withContext
                    val projectDir = getProjectDir(projectId)
                    if (!projectDir.exists()) projectDir.mkdirs()

                    val finalBeatFile = File(projectDir, "${project.name}_BEAT.wav")
                    if (finalBeatFile.exists()) finalBeatFile.delete()

                    FileOutputStream(finalBeatFile).use { fos ->
                        inputStream.copyTo(fos)
                    }

                    _statusMessage.value = "Detekuji přesné BPM instrumentálu..."
                    val detectedBpm = AudioEngine.detectBPM(finalBeatFile)

                    val updatedProject = project.copy(
                        beatFilePath = finalBeatFile.absolutePath,
                        beatOriginalName = originalName,
                        bpm = detectedBpm
                    )
                    repository.updateProject(updatedProject)
                    _currentProject.value = updatedProject
                    _statusMessage.value = "Beat úspěšně nahrán! BPM detekováno: $detectedBpm"
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to import beat file", e)
                    _statusMessage.value = "Chyba při importu beatu: ${e.message}"
                } finally {
                    _isLoading.value = false
                }
            }
        }
    }

    /**
     * Imports multiple vocal files selected from the device
     */
    fun importMultipleVocalFiles(projectId: Long, vocalStreams: List<Pair<String, InputStream>>) {
        viewModelScope.launch {
            _isLoading.value = true
            _statusMessage.value = "Importuji ${vocalStreams.size} vokálních stop..."

            withContext(Dispatchers.IO) {
                try {
                    val project = repository.getProjectById(projectId) ?: return@withContext
                    val projectDir = getProjectDir(projectId)
                    if (!projectDir.exists()) projectDir.mkdirs()

                    var currentVocalNumber = _vocals.value.size

                    for ((originalName, inputStream) in vocalStreams) {
                        currentVocalNumber++
                        val assignedName = "${project.name}_VOKAL$currentVocalNumber"

                        val finalVocalFile = File(projectDir, "$assignedName.wav")
                        if (finalVocalFile.exists()) finalVocalFile.delete()

                        FileOutputStream(finalVocalFile).use { fos ->
                            inputStream.copyTo(fos)
                        }

                        // Determine if major or minor double tracking
                        var setAsMajor = true
                        if (currentVocalNumber > 1) {
                            val likelyDouble = originalName.lowercase().contains("double") ||
                                               originalName.lowercase().contains("back") ||
                                               currentVocalNumber > 1
                            if (likelyDouble) {
                                setAsMajor = false
                            }
                        }

                        val vocalEntity = VocalFileEntity(
                            projectId = projectId,
                            filePath = finalVocalFile.absolutePath,
                            originalName = originalName,
                            assignedName = assignedName,
                            isMajor = setAsMajor,
                            volume = if (setAsMajor) 1.0f else 0.65f,
                            panning = if (setAsMajor) 0.0f else -0.75f
                        )

                        repository.insertVocalFile(vocalEntity)
                    }

                    _statusMessage.value = "Úspěšně importováno ${vocalStreams.size} vokálních stop!"
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to import vocals", e)
                    _statusMessage.value = "Chyba při importu vokálů: ${e.message}"
                } finally {
                    _isLoading.value = false
                }
            }
        }
    }

    /**
     * Synthesizes and loads mock high-quality beat and vocal files for offline demonstration and testing.
     */
    fun createSyntheticProjectAssets(projectId: Long) {
        viewModelScope.launch {
            _isLoading.value = true
            _statusMessage.value = "Generuji realistický beat a vokální stopy..."
            
            withContext(Dispatchers.IO) {
                try {
                    val project = repository.getProjectById(projectId) ?: return@withContext
                    val projectDir = getProjectDir(projectId)
                    if (!projectDir.exists()) projectDir.mkdirs()

                    // 1. Generate Synthetic Beat WAV
                    val beatFile = File(projectDir, "${project.name}_BEAT.wav")
                    _statusMessage.value = "Generuji 90 BPM instrumentál s hlubokým sub-basem..."
                    val bpm = AudioEngine.generateTestBeat(getApplication(), beatFile)

                    val updatedProject = project.copy(
                        beatFilePath = beatFile.absolutePath,
                        beatOriginalName = "Vygenerovaný_Test_Beat.wav",
                        bpm = bpm
                    )
                    repository.updateProject(updatedProject)
                    _currentProject.value = updatedProject

                    // 2. Generate Lead Vocal (Major)
                    val leadVocalFile = File(projectDir, "${project.name}_VOKAL1.wav")
                    _statusMessage.value = "Generuji hlavní rapový vokál (se šumem, brumem a sykavkami)..."
                    AudioEngine.generateTestVocal(getApplication(), leadVocalFile)

                    val leadVocal = VocalFileEntity(
                        projectId = projectId,
                        filePath = leadVocalFile.absolutePath,
                        originalName = "Test_Vokal_Hlavni.wav",
                        assignedName = "${project.name}_VOKAL1",
                        isMajor = true,
                        volume = 1.0f,
                        panning = 0.0f
                    )
                    repository.insertVocalFile(leadVocal)

                    // 3. Generate Backing Double Vocal (Minor)
                    // Copy lead vocal to create a double backing track
                    val backVocalFile = File(projectDir, "${project.name}_VOKAL2.wav")
                    leadVocalFile.copyTo(backVocalFile, overwrite = true)

                    val backVocal = VocalFileEntity(
                        projectId = projectId,
                        filePath = backVocalFile.absolutePath,
                        originalName = "Test_Vokal_Double.wav",
                        assignedName = "${project.name}_VOKAL2",
                        isMajor = false,
                        volume = 0.65f,
                        panning = -0.75f, // Wide left
                        offsetMs = 0      // Mixer will automatically add delayed offset for double tracking
                    )
                    repository.insertVocalFile(backVocal)

                    _statusMessage.value = "Projekt úspěšně vygenerován s Lead & Backing vokály!"
                } catch (e: Exception) {
                    Log.e(TAG, "Error generating synthetic assets", e)
                    _statusMessage.value = "Chyba při generování: ${e.message}"
                } finally {
                    _isLoading.value = false
                }
            }
        }
    }

    /**
     * Saves vocal's settings and triggers processing in the background
     */
    fun saveAndProcessVocal(vocal: VocalFileEntity) {
        viewModelScope.launch {
            _isLoading.value = true
            _statusMessage.value = "Zpracovávám efekty rapového řetězce..."

            withContext(Dispatchers.IO) {
                try {
                    repository.updateVocalFile(vocal)
                    _currentVocal.value = vocal

                    val inputFile = File(vocal.filePath)
                    val projectDir = getProjectDir(vocal.projectId)
                    val processedFile = File(projectDir, "${vocal.assignedName}_ZPRACOVANO.wav")

                    if (processedFile.exists()) processedFile.delete()

                    val success = AudioEngine.processVocal(inputFile, processedFile, vocal)
                    if (success) {
                        val updatedVocal = vocal.copy(processedFilePath = processedFile.absolutePath)
                        repository.updateVocalFile(updatedVocal)
                        _currentVocal.value = updatedVocal
                        _statusMessage.value = "Vokální efekty byly úspěšně aplikovány!"
                    } else {
                        _statusMessage.value = "Některé efekty selhaly při zpracování."
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed processing vocal", e)
                    _statusMessage.value = "Chyba při zpracování: ${e.message}"
                } finally {
                    _isLoading.value = false
                }
            }
        }
    }

    /**
     * Delete vocal file from project
     */
    fun deleteVocal(vocal: VocalFileEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val file = File(vocal.filePath)
            if (file.exists()) file.delete()

            vocal.processedFilePath?.let {
                val procFile = File(it)
                if (procFile.exists()) procFile.delete()
            }

            repository.deleteVocalFile(vocal)
            _statusMessage.value = "Vokál ${vocal.assignedName} smazán."
        }
    }

    /**
     * Automatically aligns and mixes all vocals (major and minor doubles) with the beat track
     */
    fun mixActiveProject() {
        val project = _currentProject.value ?: return
        val vocalsList = _vocals.value
        val beatPath = project.beatFilePath

        if (beatPath == null) {
            _statusMessage.value = "Chyba: Projekt neobsahuje žádný beat pro smíchání."
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _statusMessage.value = "Míchám beat a vokály. Zarovnávám double stopy..."

            withContext(Dispatchers.IO) {
                try {
                    val beatFile = File(beatPath)
                    val projectDir = getProjectDir(project.id)
                    val mixedOutFile = File(projectDir, "${project.name}_MASTER_MIX.wav")

                    // Map vocals to Pair of File and Vocal settings
                    // Prefer using the fully processed/FX-applied vocal if available, otherwise fallback to original!
                    val vocalPairs = vocalsList.map { vocal ->
                        val activePath = vocal.processedFilePath ?: vocal.filePath
                        Pair(File(activePath), vocal)
                    }

                    if (vocalPairs.isEmpty()) {
                        _statusMessage.value = "Chyba: Projekt neobsahuje žádné vokální stopy."
                        return@withContext
                    }

                    val success = AudioEngine.mixProject(beatFile, vocalPairs, mixedOutFile)
                    if (success) {
                        val updatedProject = project.copy(mixedFilePath = mixedOutFile.absolutePath)
                        repository.updateProject(updatedProject)
                        _currentProject.value = updatedProject
                        _statusMessage.value = "Master Mix byl úspěšně vygenerován!"
                    } else {
                        _statusMessage.value = "Míchání stop se nezdařilo."
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Mixing failed", e)
                    _statusMessage.value = "Chyba při míchání: ${e.message}"
                } finally {
                    _isLoading.value = false
                }
            }
        }
    }

    // Media Playback Implementation
    fun playAudio(filePath: String) {
        if (_isPlaying.value && _playingPath.value == filePath) {
            pauseAudio()
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) {
                    stopAudio()
                }

                val player = MediaPlayer().apply {
                    setDataSource(filePath)
                    prepare()
                    start()
                    setOnCompletionListener {
                        _isPlaying.value = false
                        _playingPath.value = null
                    }
                }

                mediaPlayer = player
                _isPlaying.value = true
                _playingPath.value = filePath
            } catch (e: Exception) {
                Log.e(TAG, "MediaPlayer failed to play: $filePath", e)
                _statusMessage.value = "Chyba přehrávání: Soubor nelze přehrát."
            }
        }
    }

    fun pauseAudio() {
        mediaPlayer?.let { player ->
            if (player.isPlaying) {
                player.pause()
                _isPlaying.value = false
            } else {
                player.start()
                _isPlaying.value = true
            }
        }
    }

    fun stopAudio() {
        mediaPlayer?.let {
            try {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping media player", e)
            }
        }
        mediaPlayer = null
        _isPlaying.value = false
        _playingPath.value = null
    }

    private fun getProjectDir(projectId: Long): File {
        val rootDir = getApplication<Application>().filesDir
        return File(rootDir, "projects/$projectId")
    }

    override fun onCleared() {
        super.onCleared()
        stopAudio()
    }
}
