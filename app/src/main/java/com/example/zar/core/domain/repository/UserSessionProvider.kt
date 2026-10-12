package com.example.zar.core.domain.repository

import com.example.zar.core.domain.model.UserId
import kotlinx.coroutines.flow.Flow

/** The signed-in user. Every repository scopes reads and writes to this ID. */
interface UserSessionProvider {
    fun currentUserId(): UserId?
    fun observeUserId(): Flow<UserId?>
}
