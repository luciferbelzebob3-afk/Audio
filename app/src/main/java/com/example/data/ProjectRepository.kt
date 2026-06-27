package com.example.data

import kotlinx.coroutines.flow.Flow

class ProjectRepository(private val projectDao: ProjectDao) {
    val allProjects: Flow<List<ProjectEntity>> = projectDao.getAllProjects()

    fun getVocalsForProject(projectId: Long): Flow<List<VocalFileEntity>> {
        return projectDao.getVocalsForProject(projectId)
    }

    suspend fun getProjectById(projectId: Long): ProjectEntity? {
        return projectDao.getProjectById(projectId)
    }

    suspend fun insertProject(project: ProjectEntity): Long {
        return projectDao.insertProject(project)
    }

    suspend fun updateProject(project: ProjectEntity) {
        projectDao.updateProject(project)
    }

    suspend fun deleteProject(project: ProjectEntity) {
        projectDao.deleteProject(project)
    }

    suspend fun getVocalById(vocalId: Long): VocalFileEntity? {
        return projectDao.getVocalById(vocalId)
    }

    suspend fun insertVocalFile(vocalFile: VocalFileEntity): Long {
        return projectDao.insertVocalFile(vocalFile)
    }

    suspend fun updateVocalFile(vocalFile: VocalFileEntity) {
        projectDao.updateVocalFile(vocalFile)
    }

    suspend fun deleteVocalFile(vocalFile: VocalFileEntity) {
        projectDao.deleteVocalFile(vocalFile)
    }
}
