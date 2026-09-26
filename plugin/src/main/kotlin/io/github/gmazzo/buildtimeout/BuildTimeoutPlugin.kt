package io.github.gmazzo.buildtimeout

import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.toJavaDuration
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.configuration.BuildFeatures
import org.gradle.api.initialization.Settings
import org.gradle.api.invocation.Gradle
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.newInstance
import org.gradle.kotlin.dsl.registerIfAbsent

public class BuildTimeoutPlugin @Inject constructor(
    private val providers: ProviderFactory,
    objects: ObjectFactory,
    features: BuildFeatures,
) : Plugin<ExtensionAware> {

    private val isolatedProjects = features.isolatedProjects.active.get()
    private val onTaskStarted: BuildTimeoutService.OnTaskStarted = objects.newInstance()
    private val onTaskFinished: BuildTimeoutService.OnTaskFinished = objects.newInstance()

    override fun apply(target: ExtensionAware) {
        val gradle = when (target) {
            is Gradle -> target
            is Settings -> target.gradle
            is Project -> target.gradle
            else -> error("Unsupported plugin target: $target")
        }

        val extension = target.extensions.create<BuildTimeoutExtension>("buildTimeout").apply {

            val buildTimeout = providers
                .gradleProperty("buildTimeout")
                .map(Duration::parse)
                .map { it.toJavaDuration() }

            timeout
                .convention(buildTimeout)
                .finalizeValueOnRead()

        }

        val timeoutService = gradle.sharedServices
            .registerIfAbsent("buildTimeoutService", BuildTimeoutService::class) {
                parameters.timeout.value(extension.timeout)
            }

        when (target) {
            is Project -> {
                target.configureTasks(timeoutService)

                if (!isolatedProjects) {
                    target.subprojects { configureTasks(timeoutService) }
                }
            }

            else -> gradle.lifecycle.beforeProject {
                apply<BuildTimeoutPlugin>()
            }
        }
    }

    private fun Project.configureTasks(timeoutService: Provider<BuildTimeoutService>) = tasks.configureEach {
        usesService(timeoutService)
        doFirst(onTaskStarted)
        doLast(onTaskFinished)
    }

}
