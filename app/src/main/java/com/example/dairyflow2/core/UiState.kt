package com.example.dairyflow2.core

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

inline fun <T> UiState<T>.dataOr(default: () -> T): T =
    when (this) {
        is UiState.Success -> data
        else -> default()
    }

