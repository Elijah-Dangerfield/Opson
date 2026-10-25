package com.opson.server.db

import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Bridge `kotlin.time.Instant` (domain layer, what `Clock.now()` returns) ↔
 * `java.time.Instant` (what Exposed's `timestamp` column type accepts). Done by
 * hand instead of pulling in kotlinx-datetime just for this.
 *
 * Postgres TIMESTAMPTZ truncates to microseconds, which is fine here.
 */
@OptIn(ExperimentalTime::class)
fun Instant.toJavaInstant(): java.time.Instant =
    java.time.Instant.ofEpochSecond(epochSeconds, nanosecondsOfSecond.toLong())

@OptIn(ExperimentalTime::class)
fun java.time.Instant.toKotlinInstant(): Instant =
    Instant.fromEpochSeconds(epochSecond, nano.toLong())
