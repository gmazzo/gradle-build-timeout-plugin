package io.github.gmazzo.buildtimeout

import java.io.File
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.gradle.testkit.runner.UnexpectedBuildFailure
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Execution(ExecutionMode.SAME_THREAD) // the build dir is shared
class BuildTimeoutPluginTest {

    private val tempDir = File(System.getProperty("tempDir"))

    private fun Scenario.prepare(rootDir: File): Unit = with(rootDir) {
        deleteRecursively()
        mkdirs()

        resolve("gradle.properties").writeText(
            """
            org.gradle.caching=true
            org.gradle.configuration-cache=true
            org.gradle.isolated-projects=${projectIsolation}
        """.trimIndent()
        )

        resolve("settings.gradle.kts").writeText(
            """
            plugins {
                ${if (applyAtSettings) "id(\"io.github.gmazzo.build.timeout\")" else ""}
                id("jacoco-testkit-coverage")
            }

            ${if (multiModule) "include(\":foo\", \":bar\")" else ""}
            """.trimIndent()
        )

        addBuildScript()
        if (multiModule) {
            resolve("foo").addBuildScript()
            resolve("bar").addBuildScript()
        }
    }

    context(scenario: Scenario)
    fun File.addBuildScript() {
        mkdirs()
        resolve("build.gradle.kts").writeText(
            $$"""
            import kotlin.time.Duration
            import kotlin.time.Duration.Companion.milliseconds
            import org.gradle.api.tasks.Input

            plugins {
                $${if (scenario.applyAtProject) "id(\"io.github.gmazzo.build.timeout\")" else ""}
            }

            val task1 = tasks.register<WaitTask>("task1") {
                duration = providers.gradleProperty("task1Duration").map(Duration::parse).get()
            }

            val task2 = tasks.register<WaitTask>("task2") {
                dependsOn(task1)
                duration = providers.gradleProperty("task2Duration").map(Duration::parse).get()
            }

            val build = tasks.register("build") {
                dependsOn(task1, task2)
            }

            abstract class WaitTask : DefaultTask() {

                @get:Input
                abstract val duration: Property<Duration>

                @TaskAction
                fun performWait() {
                    val start = System.currentTimeMillis()

                    logger.lifecycle("$name started")
                    Thread.sleep(duration.get().inWholeMilliseconds)
                    logger.lifecycle("$name finished after ${(System.currentTimeMillis() - start).milliseconds}")
                }
            }
            """.trimIndent()
        )
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("scenarios")
    fun `build times out`(scenario: Scenario) {
        val rootDir = tempDir.resolve(scenario.dirName)

        scenario.prepare(rootDir)

        val build = GradleRunner.create()
            .withProjectDir(rootDir)
            .withPluginClasspath()
            .withArguments(
                "--stacktrace",
                "-Ptask1Duration=${scenario.task1Duration}",
                "-Ptask2Duration=${scenario.task2Duration}",
                "-PbuildTimeout=${scenario.buildTimeout}",
                "build"
            )
            .forwardOutput()

        val result = when (scenario.expectedBuildOutcome) {
            TaskOutcome.SUCCESS -> build.build()
            else -> assertThrows<UnexpectedBuildFailure> { build.build() }.buildResult
        }

        assertEquals(scenario.expectedTask1Outcome, result.task(":task1")?.outcome)
        assertEquals(scenario.expectedTask2Outcome, result.task(":task2")?.outcome)
        assertEquals(scenario.expectedBuildOutcome, result.task(":build")?.outcome)
    }

    fun scenarios() = listOf(
        Scenario(
            task1Duration = "500ms",
            task2Duration = "500ms",
            buildTimeout = "10s",
            expectedTask1Outcome = TaskOutcome.SUCCESS,
            expectedTask2Outcome = TaskOutcome.SUCCESS,
            expectedBuildOutcome = TaskOutcome.SUCCESS,
        ),
        Scenario(
            task1Duration = "100ms",
            task2Duration = "3s",
            buildTimeout = "1s",
            expectedTask1Outcome = TaskOutcome.SUCCESS,
            expectedTask2Outcome = TaskOutcome.FAILED,
            expectedBuildOutcome = null,
        ),
    ).flatMap {
        listOf(
            it,
            it.copy(multiModule = false, applyAtSettings = true, applyAtProject = false),
            it.copy(multiModule = true),
            it.copy(multiModule = true, applyAtSettings = true, applyAtProject = false),
        )
    }.flatMap {
        listOf(it, it.copy(projectIsolation = true))
    }.map(Arguments::of)

    data class Scenario(
        val multiModule: Boolean = false,
        val projectIsolation: Boolean = false,
        val applyAtSettings: Boolean = false,
        val applyAtProject: Boolean = true,
        val task1Duration: String,
        val task2Duration: String,
        val buildTimeout: String,
        val expectedTask1Outcome: TaskOutcome?,
        val expectedTask2Outcome: TaskOutcome?,
        val expectedBuildOutcome: TaskOutcome?,
    ) {
        val dirName = buildString {
            append(task1Duration)
            append("-")
            append(task2Duration)
            append("-timeout")
            append(buildTimeout)
            if (multiModule) append("-multi")
            if (projectIsolation) append("-isolated")
            if (applyAtSettings) if (applyAtProject) append("-both") else append("-settings")
        }

        override fun toString() = dirName
    }

}
