package io.github.gmazzo.buildtimeout

import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.toJavaDuration
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.initialization.Settings
import org.gradle.api.invocation.Gradle
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.provider.ProviderFactory
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.newInstance
import org.gradle.kotlin.dsl.registerIfAbsent
import org.gradle.kotlin.dsl.the

public class BuildTimeoutPlugin @Inject constructor(
    private val providers: ProviderFactory,
    private val objects: ObjectFactory,
) : Plugin<Any> {

    override fun apply(target: Any) {
        when (target) {
            is Gradle -> target.gradle.configure()
            is Settings -> target.configure(target.gradle)
            is Project -> target.configure(target.gradle)
            else -> error("Unsupported plugin target: $target")
        }
    }

    private fun ExtensionAware.configure(gradle: Gradle) {
        gradle.apply<BuildTimeoutPlugin>()

        val extension = gradle.the<BuildTimeoutExtension>()
        extensions.add(BuildTimeoutExtension::class.java, "buildTimeout", extension)
    }

    private fun Gradle.configure() {
        val extension = extensions.create("buildTimeout", BuildTimeoutExtension::class).apply {
            val buildTimeout = providers
                .gradleProperty("buildTimeout")
                .map(Duration::parse)
                .map { it.toJavaDuration() }

            timeout
                .convention(buildTimeout)
                .finalizeValueOnRead()

        }

        val timeoutService = gradle.sharedServices.registerIfAbsent("buildTimeoutService", BuildTimeoutService::class) {
            parameters.timeout.set(extension.timeout)
        }

        val onTaskStarted: BuildTimeoutService.OnTaskStarted = objects.newInstance(timeoutService)
        val onTaskFinished: BuildTimeoutService.OnTaskFinished = objects.newInstance(timeoutService)

        projectsEvaluated {
            allprojects {
                tasks.configureEach {
                    usesService(timeoutService)
                    doFirst(onTaskStarted)
                    doLast(onTaskFinished)
                }
            }
        }
    }

}
