import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

plugins {
    id("io.github.gmazzo.build.timeout")
}

// TODO lower the timeout to validate it works
buildTimeout.timeout(2.minutes)

tasks.register("build") {
    doLast { Thread.sleep(20.seconds.inWholeMilliseconds) }
}
