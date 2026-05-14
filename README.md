![GitHub](https://img.shields.io/github/license/gmazzo/gradle-build-timeout-plugin)
[![Gradle Plugin Portal](https://img.shields.io/gradle-plugin-portal/v/io.github.gmazzo.build.timeout)](https://plugins.gradle.org/plugin/io.github.gmazzo.build.timeout)
[![Build Status](https://github.com/gmazzo/gradle-build-timeout-plugin/actions/workflows/ci-cd.yaml/badge.svg)](https://github.com/gmazzo/gradle-build-timeout-plugin/actions/workflows/ci-cd.yaml)
[![Coverage](https://codecov.io/gh/gmazzo/gradle-build-timeout-plugin/branch/main/graph/badge.svg?token=D5cDiPWvcS)](https://codecov.io/gh/gmazzo/gradle-build-timeout-plugin)
[![Users](https://img.shields.io/badge/users_by-Sourcegraph-purple)](https://sourcegraph.com/search?q=content:io.github.gmazzo.build.timeout+-repo:github.com/gmazzo/gradle-build-timeout-plugin)

[![Contributors](https://contrib.rocks/image?repo=gmazzo/gradle-build-timeout-plugin)](https://github.com/gmazzo/gradle-build-timeout-plugin/graphs/contributors)

# gradle-build-timeout-plugin

A Gradle plugin that sets an overall run timeout for the build.

> [!NOTE]
> Gradle does support timeouts for [individual tasks](https://docs.gradle.org/current/userguide/controlling_task_execution.html#sec:task_timeouts), but [not for the entire build yet](https://github.com/Glovo/glovo-customer-android/pull/23012).

# Usage

Apply the plugin at the **root** project or its **settings**:

```kotlin
plugins {
    id("io.github.gmazzo.build.timeout") version "<latest>"
}

buildTimeout.timeout(10.minutes)
```
Alternatively, you can set the timeout via a Gradle property:

```bash
./gradlew build -PbuildTimeout=10m
```
or via `gradle.properties`:

```properties
buildTimeout=10m
```
