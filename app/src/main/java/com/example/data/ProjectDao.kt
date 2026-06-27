package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY timestamp DESC")
    fun getAllProjects(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun getProjectById(id: Long): ProjectEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: ProjectEntity): Long

    @Update
    suspend fun updateProject(project: ProjectEntity)

    @Delete
    suspend fun deleteProject(project: ProjectEntity)

    @Query("SELECT * FROM vocal_files WHERE projectId = :projectId ORDER BY timestamp ASC")
    fun getVocalsForProject(projectId: Long): Flow<List<VocalFileEntity>>

    @Query("SELECT * FROM vocal_files WHERE id = :id")
    suspend fun getVocalById(id: Long): VocalFileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVocalFile(vocalFile: VocalFileEntity): Long

    @Update
    suspend fun updateVocalFile(vocalFile: VocalFileEntity)

    @Delete
    suspend fun deleteVocalFile(vocalFile: VocalFileEntity)
}
