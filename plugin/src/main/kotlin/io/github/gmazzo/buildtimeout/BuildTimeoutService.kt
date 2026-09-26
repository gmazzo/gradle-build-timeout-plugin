package io.github.gmazzo.buildtimeout

import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeoutException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.toKotlinDuration
import org.gradle.api.Action
import org.gradle.api.Task
import org.gradle.api.logging.Logging
import org.gradle.api.provider.Property
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.api.services.ServiceReference

internal abstract class BuildTimeoutService :
    BuildService<BuildTimeoutService.Params>,
    AutoCloseable,
    TimerTask() {

    private val logger = Logging.getLogger(BuildTimeoutService::class.java)

    private val timer = Timer("BuildTimeoutService", true)

    internal val threadsToInterrupt = ConcurrentLinkedQueue<Thread>()

    private var startedAt: Long? = null

    private var timedOutAfter: Duration? = null

    private val timeoutException
        get() = TimeoutException("Build timeout has been exceeded: $timedOutAfter")

    private fun start() {
        if (startedAt == null) {
            synchronized(this) {
                if (startedAt == null) {
                    startedAt = System.currentTimeMillis()
                    val timeout = parameters.timeout.get().toKotlinDuration()

                    logger.lifecycle("This build will timeout after $timeout")
                    timer.schedule(this, timeout.inWholeMilliseconds)
                }
            }
        }
    }

    override fun run() {
        timedOutAfter = (System.currentTimeMillis() - startedAt!!).milliseconds

        val exception = timeoutException
        logger.error("${exception.message}. Interrupting ${threadsToInterrupt.size} tasks", exception)
        with(threadsToInterrupt.iterator()) {
            while (hasNext()) {
                next().interrupt()
                remove()
            }
        }
        throw exception
    }

    init {
        println("*** init BuildTimeoutService $this")
    }

    override fun close() {
        logger.info("Timeout countdown has been dismissed")
        timer.cancel()
        threadsToInterrupt.clear()
    }

    interface Params : BuildServiceParameters {
        val timeout: Property<java.time.Duration>
    }

    internal abstract class OnTaskStarted : Action<Task> {

        @get:ServiceReference
        abstract val service: Property<BuildTimeoutService>

        override fun execute(task: Task): Unit = with(service.get()) {
            threadsToInterrupt.add(Thread.currentThread())

            start()
            if (timedOutAfter != null) {
                throw timeoutException
            }
        }

    }

    internal abstract class OnTaskFinished : Action<Task> {

        @get:ServiceReference
        abstract val service: Property<BuildTimeoutService>

        override fun execute(task: Task): Unit = with(service.get()) {
            threadsToInterrupt.remove(Thread.currentThread())
        }

    }

}
