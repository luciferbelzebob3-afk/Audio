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

    val allProjects: StateFlow<List<ProjectEntity>> =
        repository.allProjects.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

    private val _currentScreen = MutableStateFlow<Screen>(Screen.Dashboard)
    val currentScreen = _currentScreen.asStateFlow()

    private val _currentProject = MutableStateFlow<ProjectEntity?>(null)
    val currentProject = _currentProject.asStateFlow()

    private val _vocals = MutableStateFlow<List<VocalFileEntity>>(emptyList())
    val vocals = _vocals.asStateFlow()

    private val _currentVocal = MutableStateFlow<VocalFileEntity?>(null)
    val currentVocal = _currentVocal.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage = _statusMessage.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    private val _playingPath = MutableStateFlow<String?>(null)
    val playingPath = _playingPath.asStateFlow()

    private var mediaPlayer: MediaPlayer? = null

    private val waveformCache = mutableMapOf<String, FloatArray>()

    init {
        viewModelScope.launch {
            _currentScreen.collect { screen ->

                stopAudio()

                when(screen) {

                    Screen.Dashboard -> {
                        _currentProject.value = null
                        _currentVocal.value = null
                        _vocals.value = emptyList()
                    }

                    is Screen.ProjectWorkspace ->
                        loadProjectDetails(screen.projectId)

                    is Screen.VocalProcessor ->
                        loadVocalDetails(screen.vocalFileId)
                }
            }
        }
    }


    fun navigateTo(screen: Screen) {
        _currentScreen.value = screen
    }


    private suspend fun loadProjectDetails(id: Long) {

    val project = repository.getProjectById(id)

    _currentProject.value = project

    if(project != null) {

        repository.getVocalsForProject(id)
            .collectLatest { list ->

                _vocals.value = list

            }
    }
}


    private suspend fun loadVocalDetails(id: Long) {

        _currentVocal.value =
            repository.getVocalById(id)
    }
    fun clearStatusMessage() {
        _statusMessage.value = null
    }


    fun createProject(name: String) {

        viewModelScope.launch(Dispatchers.IO) {

            val cleanName =
                name.trim().ifEmpty { "Nový Projekt" }

            val projectId =
                repository.insertProject(
                    ProjectEntity(name = cleanName)
                )


            val dir = getProjectDir(projectId)

            if (!dir.exists()) {
                dir.mkdirs()
            }


            withContext(Dispatchers.Main) {
                navigateTo(Screen.ProjectWorkspace(projectId))
            }
        }
    }



    fun deleteProject(project: ProjectEntity) {

        viewModelScope.launch(Dispatchers.IO) {

            getProjectDir(project.id)
                .deleteRecursively()


            repository.deleteProject(project)


            withContext(Dispatchers.Main) {

                if (_currentProject.value?.id == project.id) {
                    navigateTo(Screen.Dashboard)
                }
            }
        }
    }



    fun importBeatFile(
        projectId: Long,
        originalName: String,
        inputStream: InputStream
    ) {

        viewModelScope.launch {

            _isLoading.value = true
            _statusMessage.value =
                "Importuji beat..."


            withContext(Dispatchers.IO) {

                try {

                    val project =
                        repository.getProjectById(projectId)
                            ?: return@withContext


                    val dir =
                        getProjectDir(projectId)

                    if (!dir.exists())
                        dir.mkdirs()



                    val extension =
                        originalName.substringAfterLast(
                            ".",
                            "wav"
                        )


                    val sourceFile =
                        File(
                        dir,
                       "${project.name}_BEAT.wav"
                        )


                    FileOutputStream(sourceFile)
                        .use { output ->
                            inputStream.copyTo(output)
                        }



                    val detectedBpm =
                        if (extension.lowercase() == "wav") {
                            AudioEngine.detectBPM(sourceFile)
                        } else {
                            project.bpm
                        }



                    val updated =
                        project.copy(

                            beatFilePath =
                                sourceFile.absolutePath,

                            beatOriginalName =
                                originalName,

                            bpm =
                                detectedBpm
                        )



                    repository.updateProject(updated)

                    _currentProject.value = updated


                    _statusMessage.value =
                        "Beat načten. BPM: $detectedBpm"


                } catch(e: Exception) {

                    Log.e(TAG,
                        "Import beat failed",
                        e
                    )

                    _statusMessage.value =
                        "Chyba importu: ${e.message}"

                } finally {

                    _isLoading.value = false
                }
            }
        }
    }





    fun importMultipleVocalFiles(
        projectId: Long,
        vocalStreams: List<Pair<String, InputStream>>
    ) {


        viewModelScope.launch {


            _isLoading.value = true


            withContext(Dispatchers.IO) {


                try {


                    val project =
                        repository.getProjectById(projectId)
                            ?: return@withContext



                    val dir =
                        getProjectDir(projectId)


                    if (!dir.exists())
                        dir.mkdirs()



                    var number =
                        _vocals.value.size



                    vocalStreams.forEach { (name, stream) ->


                        number++


                        val file =
                            File(
                                dir,
                                "${project.name}_VOKAL$number.wav"
                            )



                        FileOutputStream(file)
                            .use {
                                stream.copyTo(it)
                            }



                        val vocal =
                            VocalFileEntity(

                                projectId =
                                    projectId,

                                filePath =
                                    file.absolutePath,

                                originalName =
                                    name,

                                assignedName =
                                    "${project.name}_VOKAL$number",

                                isMajor =
                                    number == 1,

                                volume =
                                    if(number == 1)
                                        1f
                                    else
                                        0.65f,

                                panning =
                                    if(number == 1)
                                        0f
                                    else
                                        -0.75f
                            )



                        repository.insertVocalFile(vocal)

                    }


                    _statusMessage.value =
                        "Vokály importovány"


                } catch(e: Exception) {

                    Log.e(TAG,
                        "Import vocals failed",
                        e
                    )

                    _statusMessage.value =
                        "Chyba vokálu: ${e.message}"


                } finally {

                    _isLoading.value = false
                }
            }
        }
    }
    fun saveAndProcessVocal(vocal: VocalFileEntity) {

        viewModelScope.launch {

            _isLoading.value = true

            _statusMessage.value =
                "Zpracovávám vokál..."


            withContext(Dispatchers.IO) {


                try {

                    val input =
                        File(vocal.filePath)


                    if (!input.exists()) {

                        _statusMessage.value =
                            "Vstupní soubor neexistuje"

                        return@withContext
                    }



                    repository.updateVocalFile(vocal)

                    _currentVocal.value = vocal



                    val output =
                        File(
                            getProjectDir(vocal.projectId),
                            "${vocal.assignedName}_PROCESSED.wav"
                        )



                    if(output.exists())
                        output.delete()



                    AudioEngine.processVocal(
                        input,
                        output,
                        vocal
                    )



                    if(output.exists()) {

                        val updated =
                            vocal.copy(
                                filePath =
                                    output.absolutePath
                            )


                        repository.updateVocalFile(updated)

                        _currentVocal.value =
                            updated


                        _statusMessage.value =
                            "Vokál zpracován"

                    } else {

                        _statusMessage.value =
                            "Export selhal"
                    }



                } catch(e: Exception) {


                    Log.e(
                        TAG,
                        "Processing error",
                        e
                    )


                    _statusMessage.value =
                        "Chyba zpracování: ${e.message}"

                } finally {

                    _isLoading.value = false
                }
            }
        }
    }




    fun playAudio(path: String) {


        stopAudio()



        try {


            val file =
                File(path)


            if(!file.exists()) {

                _statusMessage.value =
                    "Soubor nenalezen"

                return
            }



            mediaPlayer =
                MediaPlayer().apply {


                    setDataSource(
                        file.absolutePath
                    )


                    setOnPreparedListener {

                        start()

                        _isPlaying.value =
                            true

                        _playingPath.value =
                            path
                    }



                    setOnCompletionListener {

                        _isPlaying.value =
                            false

                        _playingPath.value =
                            null

                        release()

                        mediaPlayer = null
                    }



                    setOnErrorListener { _, _, _ ->


                        _isPlaying.value =
                            false


                        _playingPath.value =
                            null


                        release()

                        mediaPlayer = null


                        true
                    }



                    prepareAsync()
                }



        } catch(e: Exception) {


            Log.e(
                TAG,
                "Playback error",
                e
            )


            _statusMessage.value =
                "Nelze přehrát: ${e.message}"

        }
    }





    fun stopAudio() {


        try {


            mediaPlayer?.let {


                if(it.isPlaying)
                    it.stop()


                it.release()
            }


        } catch(_: Exception) {


        } finally {


            mediaPlayer = null

            _isPlaying.value =
                false

            _playingPath.value =
                null
        }
    }




    fun loadWaveform(
        filePath: String,
        bars: Int = 50
    ) {


        if(waveformCache.containsKey(filePath))
            return



        viewModelScope.launch(Dispatchers.IO) {


            val file =
                File(filePath)



            if(file.exists()) {


                AudioEngine
                    .extractWaveform(
                        file,
                        bars
                    )
                    ?.let {


                        waveformCache[filePath] =
                            it

                    }
            }
        }
    }
    fun getWaveform(
        path: String
    ): FloatArray {

        return waveformCache[path]
            ?: FloatArray(0)
    }




    fun mixActiveProject(
        projectId: Long
    ) {


        viewModelScope.launch {


            _isLoading.value = true

            _statusMessage.value =
                "Míchám projekt..."



            withContext(Dispatchers.IO) {


                try {


                    val project =
                        repository.getProjectById(
                            projectId
                        )


                    if(project == null) {

                        _statusMessage.value =
                            "Projekt nenalezen"

                        return@withContext
                    }



                    val beat =
                        project.beatFilePath
                            ?.let {
                                File(it)
                            }



                    val vocals =
                       repository
                        .getVocalsForProject(projectId)
                         .first()
                          .map {
                            File(it.filePath)
                           }



                    if(beat == null || !beat.exists()) {

                        _statusMessage.value =
                            "Chybí beat"

                        return@withContext
                    }



                    val output =
                        File(
                            getProjectDir(projectId),
                            "${project.name}_MASTER.wav"
                        )



                    if(output.exists())
                        output.delete()



                    AudioEngine.mixProject(
                        beat,
                        vocals,
                        output
                    )



                    if(output.exists()) {

                        _statusMessage.value =
                            "Master export hotový"

                    } else {

                        _statusMessage.value =
                            "Master export selhal"
                    }



                } catch(e: Exception) {


                    Log.e(
                        TAG,
                        "Mix error",
                        e
                    )


                    _statusMessage.value =
                        "Chyba mixu: ${e.message}"

                } finally {

                    _isLoading.value = false
                }
            }
        }
    }


private val _waveformState =
    MutableStateFlow<FloatArray?>(null)

val waveformState =
    _waveformState.asStateFlow()


fun updateProjectBpm(
    projectId: Long,
    bpm: Double
) {

    viewModelScope.launch(Dispatchers.IO) {

        val project =
            repository.getProjectById(projectId)
                ?: return@launch


        val updated =
            project.copy(
                bpm = bpm.toInt()
            )


        repository.updateProject(updated)


        withContext(Dispatchers.Main) {
            _currentProject.value = updated
        }
    }
}



fun deleteVocal(
    vocal: VocalFileEntity
) {

    viewModelScope.launch(Dispatchers.IO) {

        File(vocal.filePath)
            .delete()

        repository.deleteVocalFile(vocal)
    }
}


        } catch(e: Exception) {

            Log.e(
                TAG,
                "Delete vocal error",
                e
            )
        }
    }
}




fun createSyntheticProjectAssets(
    projectId: Long
) {

    viewModelScope.launch(Dispatchers.IO) {

        val project =
            repository.getProjectById(projectId)
                ?: return@launch


        val dir =
            getProjectDir(projectId)


        if(!dir.exists())
            dir.mkdirs()



        val beat =
            File(
                dir,
                "${project.name}_TEST_BEAT.wav"
            )


        val vocal =
            File(
                dir,
                "${project.name}_TEST_VOCAL.wav"
            )



        AudioEngine.generateTestBeat(
            beat
        )


        AudioEngine.generateTestVocal(
            vocal
        )



        repository.updateProject(
            project.copy(
                beatFilePath =
                    beat.absolutePath
            )
        )


        _statusMessage.value =
            "Test audio vytvořen"
    }
}




fun updateWaveform(
    path: String
) {

    viewModelScope.launch(Dispatchers.IO) {

        val waveform =
            AudioEngine.extractWaveform(
                File(path)
            )

        withContext(Dispatchers.Main) {

            _waveformState.value =
                waveform
        }
    }
}

    private fun getProjectDir(
        projectId: Long
    ): File {


        return File(
            getApplication<Application>()
                .filesDir,

            "projects/$projectId"
        )
    }




    override fun onCleared() {

        stopAudio()

        super.onCleared()
    }
}