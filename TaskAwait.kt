package com.example.inknotes.recognition

import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Suspends until this Play-Services [Task] completes, returning its result or rethrowing its
 * exception. ML Kit's download/model/recognition calls are all [Task]-based; this is the one
 * small adapter that lets [MlKitModelManager] and [MlKitInkRecognizer] use plain coroutines
 * instead of callback listeners, without pulling in a whole extra coroutines-play-services
 * dependency for a single method.
 */
internal suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { cont ->
    addOnCompleteListener { task ->
        val exception = task.exception
        when {
            exception != null -> cont.resumeWithException(exception)
            task.isCanceled -> cont.cancel()
            else -> cont.resume(task.result)
        }
    }
}
