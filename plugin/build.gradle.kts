@file:OptIn(ExperimentalAbiValidation::class)

import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.samReceiver)
    alias(libs.plugins.dokka)
    alias(libs.plugins.mavenPublish)
    alias(libs.plugins.gitVersion)
    alias(libs.plugins.gradle.pluginPublish)
    alias(libs.plugins.jacoco.testkit)
}

group = "io.github.gmazzo.build.timeout"
description = "A Gradle plugin that sets an overall run timeout for the build"

java.toolchain.languageVersion.set(JavaLanguageVersion.of(libs.versions.java.get()))
samWithReceiver.annotation(HasImplicitReceiver::class.qualifiedName!!)

kotlin {
    abiValidation.enabled = true
    explicitApi()
}

val originUrl = providers
    .exec { commandLine("git", "remote", "get-url", "origin") }
    .standardOutput.asText.map { it.trim() }

gradlePlugin {
    vcsUrl = originUrl
    website = originUrl

    plugins {
        create("build-timeout") {
            id = "io.github.gmazzo.build.timeout"
            displayName = name
            implementationClass = "io.github.gmazzo.build.timeout.BuildTimeoutPlugin"
            description = project.description
            tags.addAll("build", "timeout")
        }
    }
}

mavenPublishing {
    signAllPublications()
    publishToMavenCentral(automaticRelease = true)

    pom {
        name = "${rootProject.name}-${project.name}"
        description = provider { project.description }
        url = originUrl

        licenses {
            license {
                name = "MIT License"
                url = "https://opensource.org/license/mit/"
            }
        }

        developers {
            developer {
                id = "gmazzo"
                name = id
                email = "gmazzo65@gmail.com"
            }
        }

        scm {
            connection = originUrl
            developerConnection = originUrl
            url = originUrl
        }
    }
}

dependencies {
    compileOnly(gradleKotlinDsl())
    testImplementation(gradleKotlinDsl())
}

afterEvaluate {
    tasks.named<Jar>("javadocJar") {
        from(tasks.dokkaGeneratePublicationJavadoc)
    }
}

tasks.withType<PublishToMavenRepository>().configureEach {
    mustRunAfter(tasks.publishPlugins)
}

tasks.validatePlugins {
    enableStricterValidation = true
}

tasks.check {
    dependsOn(tasks.checkLegacyAbi)
}

tasks.publishPlugins {
    enabled = !"$version".endsWith("-SNAPSHOT")
}

tasks.publish {
    dependsOn(tasks.publishPlugins)
}

testing.suites.withType<JvmTestSuite> {
    useKotlinTest()
}

tasks.test {
    systemProperty("projectRootDir", temporaryDir)
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    reports.xml.required = true
}
