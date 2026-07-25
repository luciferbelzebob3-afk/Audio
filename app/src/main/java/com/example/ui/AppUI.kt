package com.example.ui

import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.ProjectEntity
import com.example.data.VocalFileEntity
import com.example.ui.theme.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun AppUI(viewModel: ProjectViewModel) {
    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Observe snackbar/notifications
    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            // Can be extended to display toast or banner
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(StudioDarkBg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Status Message Info Bar
            statusMessage?.let { msg ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = StudioCardElevated),
                    shape = RoundedCornerShape(0.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Info",
                            tint = StudioCyan,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = msg,
                            color = Color.White,
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1.0f)
                        )
                        IconButton(
                            onClick = { viewModel.clearStatusMessage() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Zavřít",
                                tint = Color.LightGray,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Screen Navigation Router
            Box(modifier = Modifier.weight(1.0f)) {
                when (val screen = currentScreen) {
                    is Screen.Dashboard -> DashboardScreen(viewModel)
                    is Screen.ProjectWorkspace -> ProjectWorkspaceScreen(viewModel, screen.projectId)
                    is Screen.VocalProcessor -> VocalProcessorScreen(viewModel, screen.projectId, screen.vocalFileId)
                }
            }
        }

        // Full Screen Loading Overlay
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.75f))
                    .clickable(enabled = false) {},
                contentAlignment = Alignment.Center
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = StudioCardBg),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .padding(32.dp)
                        .border(1.dp, StudioGold, RoundedCornerShape(16.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = StudioCyan)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = statusMessage ?: "Zpracovávám audio...",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DashboardScreen(viewModel: ProjectViewModel) {
    val projects by viewModel.allProjects.collectAsStateWithLifecycle()
    var projectName by remember { mutableStateOf("") }
    val formatter = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Aesthetic Header
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Studio Logo",
                        tint = StudioGold,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "BEAT & VOCAL STUDIO",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 2.sp
                    )
                }
                Text(
                    text = "Aplikace pro inteligentní úpravu rapových vokálů a mix s beaty",
                    color = StudioMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        // Create Project Creator Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = StudioCardBg),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, StudioBorder, RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Vytvořit Nový Projekt",
                        color = StudioGold,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = projectName,
                        onValueChange = { projectName = it },
                        label = { Text("Název projektu (např. Rap_Hit_2026)") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = StudioCyan,
                            unfocusedBorderColor = StudioBorder,
                            focusedLabelColor = StudioCyan,
                            unfocusedLabelColor = StudioMuted
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("project_name_input")
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            if (projectName.isNotBlank()) {
                                viewModel.createProject(projectName)
                                projectName = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = StudioGold, contentColor = Color.Black),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("create_project_button")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Vytvořit a otevřít", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Projects Section Title
        item {
            Text(
                text = "Vaše Projekty",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        // Projects List Items
        if (projects.isEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Prázdný stav",
                        tint = StudioMuted,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Zatím nemáte žádný projekt",
                        color = Color.LightGray,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Zadejte název nahoře a začněte míchat rapové pecky!",
                        color = StudioMuted,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp).padding(top = 4.dp)
                    )
                }
            }
        } else {
            items(projects) { project ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = StudioCardBg),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, StudioBorder, RoundedCornerShape(12.dp))
                        .clickable { viewModel.navigateTo(Screen.ProjectWorkspace(project.id)) }
                        .testTag("project_card_${project.id}"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1.0f)) {
                            Text(
                                text = project.name,
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = formatter.format(Date(project.timestamp)),
                                color = StudioMuted,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(top = 2.dp)
                            )

                            // Wave info row
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (project.beatFilePath != null) {
                                    SuggestionChip(
                                        onClick = {},
                                        label = { Text("BPM: ${project.bpm}") },
                                        colors = SuggestionChipDefaults.suggestionChipColors(
                                            labelColor = StudioCyan
                                        ),
                                        border = SuggestionChipDefaults.suggestionChipBorder(
                                            enabled = true,
                                            borderColor = StudioCyan.copy(alpha = 0.5f)
                                        )
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                SuggestionChip(
                                    onClick = {},
                                    label = {
                                        Text(
                                            if (project.beatFilePath != null) "Beat nahraný" else "Bez beatu"
                                        )
                                    },
                                    colors = SuggestionChipDefaults.suggestionChipColors(
                                        labelColor = if (project.beatFilePath != null) StudioGreen else StudioRed
                                    )
                                )
                            }
                        }

                        IconButton(
                            onClick = { viewModel.deleteProject(project) },
                            modifier = Modifier.testTag("delete_project_${project.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Smazat",
                                tint = StudioRed.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun ProjectWorkspaceScreen(viewModel: ProjectViewModel, projectId: Long) {
    val project by viewModel.currentProject.collectAsStateWithLifecycle()
    val vocals by viewModel.vocals.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val playingPath by viewModel.playingPath.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Launcher for Beat file import
    val beatLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            try {
                val cr = context.contentResolver
                val stream = cr.openInputStream(it)
                if (stream != null) {
                    var name = "import_beat.wav"
                    cr.query(it, null, null, null, null)?.use { cursor ->
                        val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1 && cursor.moveToFirst()) {
                            name = cursor.getString(nameIndex)
                        }
                    }
                    viewModel.importBeatFile(projectId, name, stream)
                }
            } catch (e: Exception) {
                Log.e("UI", "Beat launcher error", e)
            }
        }
    }

    // Launcher for multiple Vocal files import at once
    val vocalLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            val vocalsToImport = mutableListOf<Pair<String, java.io.InputStream>>()
            for (uri in uris) {
                try {
                    val cr = context.contentResolver
                    val stream = cr.openInputStream(uri)
                    if (stream != null) {
                        var name = "import_vocal.wav"
                        cr.query(uri, null, null, null, null)?.use { cursor ->
                            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                            if (nameIndex != -1 && cursor.moveToFirst()) {
                                name = cursor.getString(nameIndex)
                            }
                        }
                        vocalsToImport.add(Pair(name, stream))
                    }
                } catch (e: Exception) {
                    Log.e("UI", "Vocal launcher error for uri: $uri", e)
                }
            }
            if (vocalsToImport.isNotEmpty()) {
                viewModel.importMultipleVocalFiles(projectId, vocalsToImport)
            }
        }
    }

    if (project == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = StudioGold)
        }
        return
    }

    val activeProj = project!!

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Workspace Top Header Navigation
        item {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.navigateTo(Screen.Dashboard) }) {
                    Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Zpět", tint = Color.White)
                }
                Spacer(modifier = Modifier.width(4.dp))
                Column {
                    Text(
                        text = activeProj.name,
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Pracovní plocha projektu",
                        color = StudioMuted,
                        fontSize = 11.sp
                    )
                }
            }
        }

        // SECTION 1: INSTRUMENTAL BEAT
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = StudioCardBg),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, StudioBorder, RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "1. Instrumentální Beat",
                            color = StudioGold,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (activeProj.beatFilePath != null) {
                            IconButton(onClick = { beatLauncher.launch("audio/*") }) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Změnit",
                                    tint = StudioCyan
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    if (activeProj.beatFilePath == null) {
                        // Empty Beat State
                        Text(
                            text = "Projekt zatím neobsahuje žádný podkladový beat.",
                            color = StudioMuted,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = { beatLauncher.launch("audio/*") },
                                colors = ButtonDefaults.buttonColors(containerColor = StudioCyan, contentColor = Color.Black),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .weight(1.0f)
                                    .height(44.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Nahrát WAV / MP3", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            OutlinedButton(
                                onClick = { viewModel.createSyntheticProjectAssets(projectId) },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = StudioGold),
                                border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(brush = Brush.linearGradient(listOf(StudioGold, StudioGold))),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .weight(1.0f)
                                    .height(44.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(imageVector = Icons.Default.Star, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Zkušební Demo", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                            }
                        }
                    } else {
                        // Beat Loaded State
                        val file = File(activeProj.beatFilePath!!)
                        LaunchedEffect(activeProj.beatFilePath) {
                            viewModel.loadWaveform(activeProj.beatFilePath!!)
                        }
                        val waveformData = viewModel.waveformState.collectAsState().value[activeProj.beatFilePath!!]

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(StudioCardElevated, RoundedCornerShape(8.dp))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { viewModel.playAudio(activeProj.beatFilePath!!) },
                                colors = IconButtonDefaults.iconButtonColors(containerColor = StudioGold, contentColor = Color.Black)
                            ) {
                                Icon(
                                    imageVector = if (isPlaying && playingPath == activeProj.beatFilePath) Icons.Default.Close else Icons.Default.PlayArrow,
                                    contentDescription = "Přehrát"
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1.0f)) {
                                Text(
                                    text = file.name,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    var isEditingBpm by remember { mutableStateOf(false) }
                                    var bpmText by remember(activeProj.bpm) { mutableStateOf(activeProj.bpm.toString()) }
                                    
                                    if (isEditingBpm) {
                                        TextField(
                                            value = bpmText,
                                            onValueChange = { bpmText = it },
                                            modifier = Modifier.width(100.dp).height(48.dp),
                                            textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 12.sp),
                                            colors = TextFieldDefaults.colors(
                                                unfocusedContainerColor = StudioDarkBg,
                                                focusedContainerColor = StudioDarkBg,
                                                focusedIndicatorColor = StudioGreen,
                                                unfocusedIndicatorColor = StudioBorder
                                            ),
                                            singleLine = true
                                        )
                                        IconButton(onClick = { 
                                            isEditingBpm = false
                                            val newBpm = bpmText.toDoubleOrNull()
                                            if (newBpm != null) {
                                                viewModel.updateProjectBpm(activeProj.id, newBpm)
                                            }
                                        }, modifier = Modifier.size(32.dp)) {
                                            Icon(Icons.Default.Check, "Save BPM", tint = StudioGreen, modifier = Modifier.size(16.dp))
                                        }
                                    } else {
                                        Text(
                                            text = "Detekované BPM: ${activeProj.bpm}",
                                            color = StudioCyan,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        IconButton(onClick = { isEditingBpm = true }, modifier = Modifier.size(32.dp)) {
                                            Icon(Icons.Default.Edit, "Edit BPM", tint = StudioMuted, modifier = Modifier.size(14.dp))
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                waveformData?.let {
                                    WaveformVisualizer(
                                        waveform = it,
                                        color = StudioGold,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(30.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // SECTION 2: VOCAL TRACKS
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = StudioCardBg),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, StudioBorder, RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "2. Rapové Vokály",
                            color = StudioGold,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(
                            onClick = { vocalLauncher.launch("audio/*") },
                            colors = IconButtonDefaults.iconButtonColors(contentColor = StudioCyan)
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = "Přidat vokál")
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    if (vocals.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = StudioMuted,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Žádné nahrané vokály.",
                                color = StudioMuted,
                                fontSize = 12.sp
                            )
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            vocals.forEach { vocal ->
                                val vFile = File(vocal.filePath)
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(StudioCardElevated, RoundedCornerShape(8.dp))
                                        .border(
                                            1.dp,
                                            if (vocal.isMajor) StudioGold.copy(alpha = 0.5f) else StudioCyan.copy(alpha = 0.5f),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .padding(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Play Original
                                        IconButton(
                                            onClick = { viewModel.playAudio(vocal.filePath) },
                                            modifier = Modifier.size(36.dp),
                                            colors = IconButtonDefaults.iconButtonColors(containerColor = StudioBorder, contentColor = Color.White)
                                        ) {
                                            Icon(
                                                imageVector = if (isPlaying && playingPath == vocal.filePath) Icons.Default.Close else Icons.Default.PlayArrow,
                                                contentDescription = "Přehrát original",
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(8.dp))

                                        // Vocal Labels
                                        Column(modifier = Modifier.weight(1.0f)) {
                                            Text(
                                                text = vocal.assignedName,
                                                color = Color.White,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = if (vocal.isMajor) "MAJOR (Lead)" else "MINOR (Double)",
                                                    color = if (vocal.isMajor) StudioGold else StudioCyan,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = " • Pan: ${vocal.panning} • Vol: ${vocal.volume}",
                                                    color = StudioMuted,
                                                    fontSize = 10.sp
                                                )
                                            }
                                        }

                                        // Waveform for Original Vocal
                                        LaunchedEffect(vocal.filePath) {
                                            viewModel.loadWaveform(vocal.filePath)
                                        }
                                        val vocalWaveform = viewModel.waveformState.collectAsState().value[vocal.filePath]
                                        vocalWaveform?.let {
                                            WaveformVisualizer(
                                                waveform = it,
                                                color = if (vocal.isMajor) StudioGold else StudioCyan,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(top = 8.dp)
                                                    .height(24.dp)
                                            )
                                        }

                                        // Edit FX Button
                                        Button(
                                            onClick = { viewModel.navigateTo(Screen.VocalProcessor(projectId, vocal.id)) },
                                            colors = ButtonDefaults.buttonColors(containerColor = StudioBorder, contentColor = StudioCyan),
                                            contentPadding = PaddingValues(horizontal = 8.dp),
                                            modifier = Modifier.height(30.dp),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Build,
                                                contentDescription = null,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Efekty FX", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }

                                        IconButton(onClick = { viewModel.deleteVocal(vocal) }) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Smazat",
                                                tint = StudioRed,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }

                                    // If processed, offer to preview processed wet sound
                                    if (vocal.processedFilePath != null) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(StudioDarkBg, RoundedCornerShape(4.dp))
                                                .padding(6.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(imageVector = Icons.Default.Star, contentDescription = null, tint = StudioGreen, modifier = Modifier.size(12.dp))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text("FX Upravený vokál:", color = StudioGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                }
                                                IconButton(
                                                    onClick = { viewModel.playAudio(vocal.processedFilePath) },
                                                    modifier = Modifier.size(28.dp),
                                                    colors = IconButtonDefaults.iconButtonColors(containerColor = StudioGreen, contentColor = Color.Black)
                                                ) {
                                                    Icon(
                                                        imageVector = if (isPlaying && playingPath == vocal.processedFilePath) Icons.Default.Close else Icons.Default.PlayArrow,
                                                        contentDescription = "Přehrát FX",
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }
                                            }
                                            
                                            // Waveform for Processed Vocal
                                            LaunchedEffect(vocal.processedFilePath) {
                                                viewModel.loadWaveform(vocal.processedFilePath!!)
                                            }
                                            val fxWaveform = viewModel.waveformState.collectAsState().value[vocal.processedFilePath]
                                            fxWaveform?.let {
                                                WaveformVisualizer(
                                                    waveform = it,
                                                    color = StudioGreen,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(top = 4.dp)
                                                        .height(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // SECTION 3: AUTOMIX & EXPORT MASTER
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = StudioCardBg),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, StudioBorder, RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "3. Finální Mix & Export",
                        color = StudioGold,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Automaticky sloučí podklad s vokálními stopami. Pokud najde stejné/podobné vokální soubory, automaticky je rozdělí na major/minor double tracking stopy, [...]",
                        color = StudioMuted,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = { viewModel.mixActiveProject() },
                        enabled = activeProj.beatFilePath != null && vocals.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = StudioGreen, contentColor = Color.Black),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("mix_project_button")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("SMÍCHAT PROJEKT (MIX)", fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                        }
                    }

                    // If master mix exists, show player
                    if (activeProj.mixedFilePath != null) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(StudioCardElevated, RoundedCornerShape(8.dp))
                                .border(1.dp, StudioGreen, RoundedCornerShape(8.dp))
                                .padding(12.dp)
                        ) {
                            Text(
                                text = "HOTOVÝ MASTER MIX",
                                color = StudioGreen,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { viewModel.playAudio(activeProj.mixedFilePath!!) },
                                    colors = IconButtonDefaults.iconButtonColors(containerColor = StudioGreen, contentColor = Color.Black)
                                ) {
                                    Icon(
                                        imageVector = if (isPlaying && playingPath == activeProj.mixedFilePath) Icons.Default.Close else Icons.Default.PlayArrow,
                                        contentDescription = "Přehrát master"
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = File(activeProj.mixedFilePath!!).name,
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "Smícháno v profesionálním poměru • Double tracky panned L/R!",
                                        color = StudioMuted,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                            
                            // Waveform for Master Mix
                            LaunchedEffect(activeProj.mixedFilePath) {
                                viewModel.loadWaveform(activeProj.mixedFilePath!!)
                            }
                            val masterWaveform = viewModel.waveformState.collectAsState().value[activeProj.mixedFilePath!!]
                            masterWaveform?.let {
                                WaveformVisualizer(
                                    waveform = it,
                                    color = StudioGreen,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 12.dp)
                                        .height(36.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun VocalProcessorScreen(viewModel: ProjectViewModel, projectId: Long, vocalId: Long) {
    val vocal by viewModel.currentVocal.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val playingPath by viewModel.playingPath.collectAsStateWithLifecycle()

    if (vocal == null || vocal!!.id != vocalId) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = StudioGold)
        }
        return
    }

    val activeVocal = vocal!!

    // Internal states for local sliders to customize DSP chain
    var isNormEnabled by remember { mutableStateOf(activeVocal.isNormalizedEnabled) }
    var normTarget by remember { mutableFloatStateOf(activeVocal.normaliseTargetDb) }

    var isCompEnabled by remember { mutableStateOf(activeVocal.isCompressionEnabled) }
    var compThreshold by remember { mutableFloatStateOf(activeVocal.compressionThresholdDb) }
    var compRatio by remember { mutableFloatStateOf(activeVocal.compressionRatio) }
    var compAttack by remember { mutableFloatStateOf(activeVocal.compressionAttackMs) }
    var compRelease by remember { mutableFloatStateOf(activeVocal.compressionReleaseMs) }

    var isEqEnabled by remember { mutableStateOf(activeVocal.isEqEnabled) }
    var eqHpf by remember { mutableFloatStateOf(activeVocal.eqHighPassHz) }
    var eqBoxyCut by remember { mutableFloatStateOf(activeVocal.eqLowMidCutDb) }
    var eqPresence by remember { mutableFloatStateOf(activeVocal.eqHighMidBoostDb) }
    var eqAir by remember { mutableFloatStateOf(activeVocal.eqHighShelfDb) }

    var isStereoEnabled by remember { mutableStateOf(activeVocal.isStereorizerEnabled) }
    var stereoDelay by remember { mutableFloatStateOf(activeVocal.stereorizerDelayMs) }

    // Placeholder for extended UI - continued in actual project
}
