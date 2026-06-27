package com.ekam.baton.core.network.repository

sealed class McpNetworkResult<out T> {
    data class Success<out T>(val data: T) : McpNetworkResult<T>()
    data class Error(val code: Int, val message: String, val exception: Throwable? = null) : McpNetworkResult<Nothing>()
}
