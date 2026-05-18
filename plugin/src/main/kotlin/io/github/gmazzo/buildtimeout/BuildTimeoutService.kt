package io.github.gmazzo.buildtimeout

import java.time.Duration
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeoutException
import javax.inject.Inject
import kotlin.time.toKotlinDuration
import org.gradle.api.Action
import org.gradle.api.Task
import org.gradle.api.logging.Logging
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters

internal abstract class BuildTimeoutService :
    BuildService<BuildTimeoutService.Params>,
    AutoCloseable,
    TimerTask() {

    private val logger = Logging.getLogger(BuildTimeoutService::class.java)

    private val timer = Timer("BuildTimeoutService", true)

    internal val threadsToInterrupt = ConcurrentLinkedQueue<Thread>()

    private val timeout = parameters.timeout.get().toKotlinDuration()

    var started = false
        private set

    var hasTimeout = false
        private set

    private val timeoutException
        get() = TimeoutException("Build timeout has been exceeded: $timeout")

    private fun start() {
        if (!started) {
            synchronized(this) {
                if (!started) {
                    started = true
                    logger.lifecycle("This build will timeout after $timeout")
                    timer.schedule(this, timeout.inWholeMilliseconds)
                }
            }
        }
    }

    override fun run() {
        hasTimeout = true

        val exception = timeoutException
        logger.error("Build timeout has been exceeded. Interrupting ${threadsToInterrupt.size} tasks", exception)
        with(threadsToInterrupt.iterator()) {
            while (hasNext()) {
                next().interrupt()
                remove()
            }
        }
        throw exception
    }

    override fun close() {
        logger.info("Timeout countdown has been dismissed")
        timer.cancel()
        threadsToInterrupt.clear()
    }

    interface Params : BuildServiceParameters {
        val timeout: Property<Duration>
    }

    internal abstract class OnTaskStarted @Inject constructor(
        private val service: Provider<BuildTimeoutService>
    ) : Action<Task> {

        override fun execute(t: Task): Unit = with(service.get()) {
            start()
            if (hasTimeout) {
                throw timeoutException
            }
            threadsToInterrupt.add(Thread.currentThread())
        }

    }

    internal abstract class OnTaskFinished @Inject constructor(
        private val service: Provider<BuildTimeoutService>
    ) : Action<Task> {

        override fun execute(t: Task): Unit = with(service.get()) {
            threadsToInterrupt.remove(Thread.currentThread())
        }

    }

}
