package com.example.zar.features.freelance.domain.repository

import com.example.zar.features.freelance.domain.model.Project
import kotlinx.coroutines.flow.Flow

/** The signed-in user's freelance projects. Projects are archived, never deleted. */
interface ProjectRepository {
    fun observeProjects(): Flow<List<Project>>

    /** Creates or updates. Saves return once stored locally (offline first). */
    suspend fun saveProject(project: Project)
}
