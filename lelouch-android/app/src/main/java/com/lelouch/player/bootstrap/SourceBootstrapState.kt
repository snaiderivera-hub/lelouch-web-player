package com.lelouch.player.bootstrap

import com.lelouch.core.model.SourceConfig

sealed interface SourceBootstrapState {
    data object Loading : SourceBootstrapState
    data class Ready(val source: SourceConfig) : SourceBootstrapState
    data object Empty : SourceBootstrapState
    data class ErrorNoSource(val message: String, val retryable: Boolean = true) : SourceBootstrapState
    data class UsingLocalCache(val source: SourceConfig, val warning: String) : SourceBootstrapState
}
