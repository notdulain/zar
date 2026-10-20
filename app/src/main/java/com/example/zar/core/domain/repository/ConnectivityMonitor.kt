package com.example.zar.core.domain.repository

import kotlinx.coroutines.flow.Flow

/** Whether the device can currently reach the internet. Emits the current state first. */
interface ConnectivityMonitor {
    fun observeOnline(): Flow<Boolean>
}
