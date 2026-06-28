package com.example.ui

import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
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
                                Text(
                                    text = "Detekované BPM: ${activeProj.bpm}",
                                    color = StudioCyan,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
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
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(StudioDarkBg, RoundedCornerShape(4.dp))
                                                .padding(6.dp),
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
                        text = "Automaticky sloučí podklad s vokálními stopami. Pokud najde stejné/podobné vokální soubory, automaticky je rozdělí na major/minor double tracking stopy, přidá prostorové stereorozšíření a přesně je zarovná do beatu.",
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

    var isDeEssEnabled by remember { mutableStateOf(activeVocal.isDeEsserEnabled) }
    var deEssFreq by remember { mutableFloatStateOf(activeVocal.deEsserFrequencyHz) }
    var deEssThresh by remember { mutableFloatStateOf(activeVocal.deEsserThresholdDb) }

    var isGateEnabled by remember { mutableStateOf(activeVocal.isNoiseGateEnabled) }
    var gateThresh by remember { mutableFloatStateOf(activeVocal.noiseGateThresholdDb) }
    var gateRelease by remember { mutableFloatStateOf(activeVocal.noiseGateReleaseMs) }

    var isEchoEnabled by remember { mutableStateOf(activeVocal.isEchoRemovalEnabled) }
    var echoAtten by remember { mutableFloatStateOf(activeVocal.echoRemovalAttenuationDb) }

    var isHumEnabled by remember { mutableStateOf(activeVocal.isHumRemovalEnabled) }
    var humFreq by remember { mutableFloatStateOf(activeVocal.humRemovalFrequencyHz) }

    var isLimitEnabled by remember { mutableStateOf(activeVocal.isLimiterEnabled) }
    var limitThresh by remember { mutableFloatStateOf(activeVocal.limiterThresholdDb) }
    var limitCeil by remember { mutableFloatStateOf(activeVocal.limiterCeilingDb) }

    // Track Settings
    var isMajorTrack by remember { mutableStateOf(activeVocal.isMajor) }
    var trackVol by remember { mutableFloatStateOf(activeVocal.volume) }
    var trackPan by remember { mutableFloatStateOf(activeVocal.panning) }
    var trackOffset by remember { mutableStateOf(activeVocal.offsetMs) }

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
                IconButton(onClick = { viewModel.navigateTo(Screen.ProjectWorkspace(projectId)) }) {
                    Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Zpět", tint = Color.White)
                }
                Spacer(modifier = Modifier.width(4.dp))
                Column {
                    Text(
                        text = "Úprava Vokálu",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = activeVocal.assignedName,
                        color = StudioCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Comparative Playback Card (Dry vs Wet)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = StudioCardBg),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, StudioBorder, RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Srovnání originálu a upravené stopy",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        // DRY original card
                        Card(
                            colors = CardDefaults.cardColors(containerColor = StudioCardElevated),
                            modifier = Modifier
                                .weight(1.0f)
                                .border(1.dp, StudioBorder, RoundedCornerShape(8.dp))
                                .padding(1.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("Původní (DRY)", color = StudioMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = { viewModel.playAudio(activeVocal.filePath) },
                                    colors = ButtonDefaults.buttonColors(containerColor = StudioBorder, contentColor = Color.White),
                                    modifier = Modifier.size(44.dp),
                                    shape = RoundedCornerShape(22.dp),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isPlaying && playingPath == activeVocal.filePath) Icons.Default.Close else Icons.Default.PlayArrow,
                                        contentDescription = "Přehrát suchý vokál"
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        // WET processed card
                        val isProcessed = activeVocal.processedFilePath != null
                        Card(
                            colors = CardDefaults.cardColors(containerColor = StudioCardElevated),
                            modifier = Modifier
                                .weight(1.0f)
                                .border(
                                    1.dp,
                                    if (isProcessed) StudioGreen.copy(alpha = 0.5f) else StudioBorder,
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(1.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("Upravený (WET)", color = if (isProcessed) StudioGreen else StudioMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        activeVocal.processedFilePath?.let {
                                            viewModel.playAudio(it)
                                        }
                                    },
                                    enabled = isProcessed,
                                    colors = ButtonDefaults.buttonColors(containerColor = StudioGreen, contentColor = Color.Black),
                                    modifier = Modifier.size(44.dp),
                                    shape = RoundedCornerShape(22.dp),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isPlaying && playingPath == activeVocal.processedFilePath) Icons.Default.Close else Icons.Default.PlayArrow,
                                        contentDescription = "Přehrát mokrý vokál"
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // DSP CHAIN HEADER
        item {
            Text(
                text = "DSP Řetězec (Zpracování pro Rapové Vokály)",
                color = StudioGold,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        // 1. NORMALIZACE HLASITOSTI
        item {
            Card(colors = CardDefaults.cardColors(containerColor = StudioCardBg), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().border(1.dp, StudioBorder, RoundedCornerShape(8.dp))) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("1. Normalizace Hlasitosti", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("Vyrovná špičky nahrávky na standardní úroveň", color = StudioMuted, fontSize = 10.sp)
                        }
                        Switch(checked = isNormEnabled, onCheckedChange = { isNormEnabled = it }, colors = SwitchDefaults.colors(checkedThumbColor = StudioGold, checkedTrackColor = StudioGold.copy(alpha = 0.5f)))
                    }
                    if (isNormEnabled) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("Cílová úroveň: ${String.format("%.1f", normTarget)} dB", color = Color.White, fontSize = 12.sp)
                        Slider(
                            value = normTarget,
                            onValueChange = { normTarget = it },
                            valueRange = -6.0f..0.0f,
                            colors = SliderDefaults.colors(thumbColor = StudioGold, activeTrackColor = StudioGold)
                        )
                    }
                }
            }
        }

        // 2. KOMPRESE (COMPRESSOR)
        item {
            Card(colors = CardDefaults.cardColors(containerColor = StudioCardBg), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().border(1.dp, StudioBorder, RoundedCornerShape(8.dp))) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("2. Komprese Hlasu", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("Udrží hlas sytý, stabilní a tlačený dopředu", color = StudioMuted, fontSize = 10.sp)
                        }
                        Switch(checked = isCompEnabled, onCheckedChange = { isCompEnabled = it }, colors = SwitchDefaults.colors(checkedThumbColor = StudioGold, checkedTrackColor = StudioGold.copy(alpha = 0.5f)))
                    }
                    if (isCompEnabled) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("Práh (Threshold): ${String.format("%.1f", compThreshold)} dB", color = Color.White, fontSize = 11.sp)
                        Slider(value = compThreshold, onValueChange = { compThreshold = it }, valueRange = -36.0f..0.0f, colors = SliderDefaults.colors(thumbColor = StudioGold, activeTrackColor = StudioGold))
                        
                        Text("Poměr (Ratio): ${String.format("%.1f", compRatio)} : 1 (Rap: 4:1 až 6:1)", color = Color.White, fontSize = 11.sp)
                        Slider(value = compRatio, onValueChange = { compRatio = it }, valueRange = 1.0f..10.0f, colors = SliderDefaults.colors(thumbColor = StudioGold, activeTrackColor = StudioGold))

                        Row(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.weight(1.0f)) {
                                Text("Náběh: ${compAttack.toInt()} ms", color = Color.White, fontSize = 10.sp)
                                Slider(value = compAttack, onValueChange = { compAttack = it }, valueRange = 1.0f..50.0f, colors = SliderDefaults.colors(thumbColor = StudioGold))
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1.0f)) {
                                Text("Uvolnění: ${compRelease.toInt()} ms", color = Color.White, fontSize = 10.sp)
                                Slider(value = compRelease, onValueChange = { compRelease = it }, valueRange = 10.0f..400.0f, colors = SliderDefaults.colors(thumbColor = StudioGold))
                            }
                        }
                    }
                }
            }
        }

        // 3. EKVALIZÉR (EQ)
        item {
            Card(colors = CardDefaults.cardColors(containerColor = StudioCardBg), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().border(1.dp, StudioBorder, RoundedCornerShape(8.dp))) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("3. Studiový Ekvalizér (EQ)", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("HPF ořez hloubek, vyčištění středů a vzdušné výšky", color = StudioMuted, fontSize = 10.sp)
                        }
                        Switch(checked = isEqEnabled, onCheckedChange = { isEqEnabled = it }, colors = SwitchDefaults.colors(checkedThumbColor = StudioGold, checkedTrackColor = StudioGold.copy(alpha = 0.5f)))
                    }
                    if (isEqEnabled) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("High Pass ořez basů: ${eqHpf.toInt()} Hz", color = Color.White, fontSize = 11.sp)
                        Slider(value = eqHpf, onValueChange = { eqHpf = it }, valueRange = 50.0f..180.0f, colors = SliderDefaults.colors(thumbColor = StudioGold, activeTrackColor = StudioGold))

                        Text("Ořez krabicových středů (300Hz): ${String.format("%.1f", eqBoxyCut)} dB", color = Color.White, fontSize = 11.sp)
                        Slider(value = eqBoxyCut, onValueChange = { eqBoxyCut = it }, valueRange = -8.0f..0.0f, colors = SliderDefaults.colors(thumbColor = StudioGold, activeTrackColor = StudioGold))

                        Text("Zesílení přítomnosti vokálu (Presence): +${String.format("%.1f", eqPresence)} dB", color = Color.White, fontSize = 11.sp)
                        Slider(value = eqPresence, onValueChange = { eqPresence = it }, valueRange = 0.0f..6.0f, colors = SliderDefaults.colors(thumbColor = StudioGold, activeTrackColor = StudioGold))

                        Text("Vzdušné výšky (Air Shelf 10kHz+): +${String.format("%.1f", eqAir)} dB", color = Color.White, fontSize = 11.sp)
                        Slider(value = eqAir, onValueChange = { eqAir = it }, valueRange = 0.0f..6.0f, colors = SliderDefaults.colors(thumbColor = StudioGold, activeTrackColor = StudioGold))
                    }
                }
            }
        }

        // 4. STEREORIZER (DELAY HAAS EFFECT)
        item {
            Card(colors = CardDefaults.cardColors(containerColor = StudioCardBg), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().border(1.dp, StudioBorder, RoundedCornerShape(8.dp))) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("4. Stereorizér (Haasův Prostor)", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("Vytvoří široký stereo prostor z mono vokálu", color = StudioMuted, fontSize = 10.sp)
                        }
                        Switch(checked = isStereoEnabled, onCheckedChange = { isStereoEnabled = it }, colors = SwitchDefaults.colors(checkedThumbColor = StudioGold, checkedTrackColor = StudioGold.copy(alpha = 0.5f)))
                    }
                    if (isStereoEnabled) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("Zpoždění pravého kanálu: ${stereoDelay.toInt()} ms", color = Color.White, fontSize = 11.sp)
                        Slider(value = stereoDelay, onValueChange = { stereoDelay = it }, valueRange = 5.0f..40.0f, colors = SliderDefaults.colors(thumbColor = StudioGold, activeTrackColor = StudioGold))
                    }
                }
            }
        }

        // 5. DE-ESSER
        item {
            Card(colors = CardDefaults.cardColors(containerColor = StudioCardBg), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().border(1.dp, StudioBorder, RoundedCornerShape(8.dp))) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("5. De-esser (Tlumič Sykavek)", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("Ztiší ostré, řezající sykavky (S, Š, Z)", color = StudioMuted, fontSize = 10.sp)
                        }
                        Switch(checked = isDeEssEnabled, onCheckedChange = { isDeEssEnabled = it }, colors = SwitchDefaults.colors(checkedThumbColor = StudioGold, checkedTrackColor = StudioGold.copy(alpha = 0.5f)))
                    }
                    if (isDeEssEnabled) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("Frekvence sykavky: ${deEssFreq.toInt()} Hz", color = Color.White, fontSize = 11.sp)
                        Slider(value = deEssFreq, onValueChange = { deEssFreq = it }, valueRange = 4000.0f..9000.0f, colors = SliderDefaults.colors(thumbColor = StudioGold, activeTrackColor = StudioGold))
                    }
                }
            }
        }

        // 6. ODSTRANĚNÍ ŠUMU (NOISE GATE)
        item {
            Card(colors = CardDefaults.cardColors(containerColor = StudioCardBg), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().border(1.dp, StudioBorder, RoundedCornerShape(8.dp))) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("6. Odstranění Šumu", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("Úplně ztiší šum a nádech v pauzách", color = StudioMuted, fontSize = 10.sp)
                        }
                        Switch(checked = isGateEnabled, onCheckedChange = { isGateEnabled = it }, colors = SwitchDefaults.colors(checkedThumbColor = StudioGold, checkedTrackColor = StudioGold.copy(alpha = 0.5f)))
                    }
                    if (isGateEnabled) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("Práh (Threshold): ${String.format("%.1f", gateThresh)} dB", color = Color.White, fontSize = 11.sp)
                        Slider(value = gateThresh, onValueChange = { gateThresh = it }, valueRange = -70.0f..-24.0f, colors = SliderDefaults.colors(thumbColor = StudioGold, activeTrackColor = StudioGold))
                    }
                }
            }
        }

        // 7. ODSTRANĚNÍ OZVĚNY (DE-REVERB)
        item {
            Card(colors = CardDefaults.cardColors(containerColor = StudioCardBg), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().border(1.dp, StudioBorder, RoundedCornerShape(8.dp))) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("7. Odstranění Ozvěny", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("Tlumí odrazy neakustické místnosti", color = StudioMuted, fontSize = 10.sp)
                        }
                        Switch(checked = isEchoEnabled, onCheckedChange = { isEchoEnabled = it }, colors = SwitchDefaults.colors(checkedThumbColor = StudioGold, checkedTrackColor = StudioGold.copy(alpha = 0.5f)))
                    }
                }
            }
        }

        // 8. ODSTRANĚNÍ HLUKU SÍTĚ (DE-HUM)
        item {
            Card(colors = CardDefaults.cardColors(containerColor = StudioCardBg), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().border(1.dp, StudioBorder, RoundedCornerShape(8.dp))) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("8. Odstranění Brumů (De-hum)", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("Vyřízne elektrický síťový brum (50 Hz / 60 Hz)", color = StudioMuted, fontSize = 10.sp)
                        }
                        Switch(checked = isHumEnabled, onCheckedChange = { isHumEnabled = it }, colors = SwitchDefaults.colors(checkedThumbColor = StudioGold, checkedTrackColor = StudioGold.copy(alpha = 0.5f)))
                    }
                }
            }
        }

        // 9. LIMITER
        item {
            Card(colors = CardDefaults.cardColors(containerColor = StudioCardBg), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().border(1.dp, StudioBorder, RoundedCornerShape(8.dp))) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("9. Limiter", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("Zabrání digitálnímu ořezu a zkreslení masteru", color = StudioMuted, fontSize = 10.sp)
                        }
                        Switch(checked = isLimitEnabled, onCheckedChange = { isLimitEnabled = it }, colors = SwitchDefaults.colors(checkedThumbColor = StudioGold, checkedTrackColor = StudioGold.copy(alpha = 0.5f)))
                    }
                }
            }
        }

        // TRACK MIXING PARAMETERS
        item {
            Text(
                text = "Zasazení do mixu a double-tracking",
                color = StudioGold,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = StudioCardBg),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, StudioBorder, RoundedCornerShape(12.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Typ Stopy: " + (if (isMajorTrack) "MAJOR (Hlavní)" else "MINOR (Double)"), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text("Double tracky slouží pro šířku mixu", color = StudioMuted, fontSize = 11.sp)
                        }
                        Switch(checked = isMajorTrack, onCheckedChange = { isMajorTrack = it }, colors = SwitchDefaults.colors(checkedThumbColor = StudioGold))
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text("Hlasitost stopy v mixu: ${String.format("%.2f", trackVol)}x", color = Color.White, fontSize = 12.sp)
                    Slider(value = trackVol, onValueChange = { trackVol = it }, valueRange = 0.0f..1.5f, colors = SliderDefaults.colors(thumbColor = StudioCyan, activeTrackColor = StudioCyan))

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Stereo Panning (Vyvážení): " + when {
                            trackPan < -0.1f -> "VLEVO (${String.format("%.2f", -trackPan)})"
                            trackPan > 0.1f -> "VPRAVO (${String.format("%.2f", trackPan)})"
                            else -> "STŘED (Lead)"
                        },
                        color = Color.White,
                        fontSize = 12.sp
                    )
                    Slider(value = trackPan, onValueChange = { trackPan = it }, valueRange = -1.0f..1.0f, colors = SliderDefaults.colors(thumbColor = StudioCyan, activeTrackColor = StudioCyan))
                }
            }
        }

        // ACTION SAVE AND PROCESS
        item {
            Button(
                onClick = {
                    val updatedEntity = activeVocal.copy(
                        isNormalizedEnabled = isNormEnabled,
                        normaliseTargetDb = normTarget,
                        isCompressionEnabled = isCompEnabled,
                        compressionThresholdDb = compThreshold,
                        compressionRatio = compRatio,
                        compressionAttackMs = compAttack,
                        compressionReleaseMs = compRelease,
                        isEqEnabled = isEqEnabled,
                        eqHighPassHz = eqHpf,
                        eqLowMidCutDb = eqBoxyCut,
                        eqHighMidBoostDb = eqPresence,
                        eqHighShelfDb = eqAir,
                        isStereorizerEnabled = isStereoEnabled,
                        stereorizerDelayMs = stereoDelay,
                        isDeEsserEnabled = isDeEssEnabled,
                        deEsserFrequencyHz = deEssFreq,
                        deEsserThresholdDb = deEssThresh,
                        isNoiseGateEnabled = isGateEnabled,
                        noiseGateThresholdDb = gateThresh,
                        noiseGateReleaseMs = gateRelease,
                        isEchoRemovalEnabled = isEchoEnabled,
                        echoRemovalAttenuationDb = echoAtten,
                        isHumRemovalEnabled = isHumEnabled,
                        humRemovalFrequencyHz = humFreq,
                        isLimiterEnabled = isLimitEnabled,
                        limiterThresholdDb = limitThresh,
                        limiterCeilingDb = limitCeil,
                        
                        isMajor = isMajorTrack,
                        volume = trackVol,
                        panning = trackPan,
                        offsetMs = trackOffset
                    )
                    viewModel.saveAndProcessVocal(updatedEntity)
                },
                colors = ButtonDefaults.buttonColors(containerColor = StudioCyan, contentColor = Color.Black),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("apply_effects_button")
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("ULOŽIT A APLIKOVAT EFEKTY (FX)", fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
