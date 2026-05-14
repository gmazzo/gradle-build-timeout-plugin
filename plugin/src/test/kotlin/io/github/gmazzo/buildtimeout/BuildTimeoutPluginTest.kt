package io.github.gmazzo.buildtimeout

import java.io.File
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.gradle.testkit.runner.UnexpectedBuildFailure
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Execution(ExecutionMode.SAME_THREAD) // the build dir is shared
class BuildTimeoutPluginTest {

    private val tempDir = File(System.getProperty("tempDir"))

    @BeforeAll
    fun setup() {
        tempDir.deleteRecursively()
        tempDir.mkdirs()

        File(tempDir, "build.gradle.kts").writeText(
            """
            import kotlin.time.Duration

            plugins {
                id("io.github.gmazzo.build.timeout")
            }

            val task1Duration = providers.gradleProperty("task1Duration").map(Duration::parse).get()
            val task2Duration = providers.gradleProperty("task2Duration").map(Duration::parse).get()

            val task1 = tasks.register("task1") {
                doLast {
                    logger.lifecycle("Task1 started")
                    Thread.sleep(task1Duration.inWholeMilliseconds)
                    logger.lifecycle("Task1 finished")
                }
            }

            val task2 = tasks.register("task2") {
                dependsOn(task1)
                doLast {
                    logger.lifecycle("Task2 started")
                    Thread.sleep(task2Duration.inWholeMilliseconds)
                    logger.lifecycle("Task2 finished")
                }
            }

            val build = tasks.register("build") {
                dependsOn(task1, task2)
            }
            """.trimIndent()
        )
        File(tempDir, "settings.gradle.kts").writeText(
            """
            plugins {
                id("jacoco-testkit-coverage")
            }
            """.trimIndent()
        )
    }

    @ParameterizedTest(name = "task1={0}, task2={1}, timeout={2}")
    @CsvSource(
        "500ms, 500ms, 10s, SUCCESS, SUCCESS, SUCCESS",
        "100ms, 3s, 1s, SUCCESS, FAILED, ",
    )
    fun `build times out`(
        task1Duration: String,
        task2Duration: String,
        buildTimeout: String,
        expectedTask1Outcome: TaskOutcome?,
        expectedTask2Outcome: TaskOutcome?,
        expectedBuildOutcome: TaskOutcome?,
    ) {
        val build = GradleRunner.create()
            .withProjectDir(tempDir)
            .withPluginClasspath()
            .withArguments(
                "--stacktrace",
                "-Ptask1Duration=$task1Duration",
                "-Ptask2Duration=$task2Duration",
                "-PbuildTimeout=$buildTimeout",
                "build"
            )
            .forwardOutput()

        val result = when (expectedBuildOutcome) {
            TaskOutcome.SUCCESS -> build.build()
            else -> assertThrows<UnexpectedBuildFailure> { build.build() }.buildResult
        }

        assertEquals(expectedTask1Outcome, result.task(":task1")?.outcome)
        assertEquals(expectedTask2Outcome, result.task(":task2")?.outcome)
        assertEquals(expectedBuildOutcome, result.task(":build")?.outcome)
    }

}
