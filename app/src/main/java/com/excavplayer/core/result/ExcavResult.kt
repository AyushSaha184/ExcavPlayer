package com.excavplayer.core.result

sealed interface ExcavResult<out T> {
    data class Success<out T>(val data: T) : ExcavResult<T>
    data class Error(val exception: Throwable, val message: String? = exception.message) : ExcavResult<Nothing>
    data object Loading : ExcavResult<Nothing>

    val isSuccess: Boolean get() = this is Success
    val isError: Boolean get() = this is Error
    val isLoading: Boolean get() = this is Loading

    fun getOrNull(): T? = when (this) {
        is Success -> data
        else -> null
    }

    fun exceptionOrNull(): Throwable? = when (this) {
        is Error -> exception
        else -> null
    }
}

inline fun <T, R> ExcavResult<T>.map(transform: (T) -> R): ExcavResult<R> = when (this) {
    is ExcavResult.Success -> ExcavResult.Success(transform(data))
    is ExcavResult.Error -> this
    is ExcavResult.Loading -> this
}
