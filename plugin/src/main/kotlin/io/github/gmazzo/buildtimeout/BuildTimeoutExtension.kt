package io.github.gmazzo.buildtimeout

import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit
import kotlin.time.toJavaDuration
import org.gradle.api.provider.Property

@JvmDefaultWithoutCompatibility
public interface BuildTimeoutExtension {

    public val timeout: Property<Duration>

    public fun timeout(duration: Duration) {
        timeout.value(duration)
    }

    public fun timeout(amount: Long, unit: ChronoUnit = ChronoUnit.MILLIS) {
        timeout(Duration.of(amount, unit))
    }

    public fun timeout(amount: Long, unit: TimeUnit = TimeUnit.MILLISECONDS) {
        timeout(amount, unit.toChronoUnit())
    }

    public fun timeout(duration: kotlin.time.Duration) {
        timeout(duration.toJavaDuration())
    }

}
