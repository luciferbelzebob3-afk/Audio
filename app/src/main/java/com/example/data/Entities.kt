package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val bpm: Double = 0.0,
    val beatFilePath: String? = null,
    val beatOriginalName: String? = null,
    val mixedFilePath: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "vocal_files")
data class VocalFileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val filePath: String,
    val originalName: String,
    val assignedName: String,
    
    // DSP Settings
    val isNormalizedEnabled: Boolean = false,
    val normaliseTargetDb: Float = -1.0f,
    
    val isCompressionEnabled: Boolean = false,
    val compressionThresholdDb: Float = -18.0f,
    val compressionRatio: Float = 4.0f, // e.g. 4:1
    val compressionAttackMs: Float = 15.0f,
    val compressionReleaseMs: Float = 100.0f,
    
    val isEqEnabled: Boolean = false,
    val eqHighPassHz: Float = 90.0f,
    val eqLowMidCutDb: Float = -3.0f,
    val eqHighMidBoostDb: Float = 2.5f,
    val eqHighShelfDb: Float = 2.0f,
    
    val isStereorizerEnabled: Boolean = false,
    val stereorizerDelayMs: Float = 20.0f, // Haas delay
    
    val isDeEsserEnabled: Boolean = false,
    val deEsserFrequencyHz: Float = 6500.0f,
    val deEsserThresholdDb: Float = -20.0f,
    
    val isNoiseGateEnabled: Boolean = false,
    val noiseGateThresholdDb: Float = -48.0f,
    val noiseGateReleaseMs: Float = 150.0f,
    
    val isEchoRemovalEnabled: Boolean = false,
    val echoRemovalAttenuationDb: Float = -6.0f,
    
    val isHumRemovalEnabled: Boolean = false,
    val humRemovalFrequencyHz: Float = 50.0f, // 50 Hz default for European AC
    
    val isLimiterEnabled: Boolean = false,
    val limiterThresholdDb: Float = -3.0f,
    val limiterCeilingDb: Float = -1.0f,
    
    // Track placement parameters
    val isMajor: Boolean = true, // Major vs Minor vocals for double tracking
    val offsetMs: Long = 0,      // Precise offset in ms to align to beat
    val volume: Float = 1.0f,    // 0.0 to 1.5
    val panning: Float = 0.0f,   // -1.0 (Left) to 1.0 (Right)
    
    val processedFilePath: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
