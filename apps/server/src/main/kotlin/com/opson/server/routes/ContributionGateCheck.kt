package com.opson.server.routes

import com.opson.server.domain.ContributionGate
import com.opson.server.domain.ContributionSignals
import com.opson.server.domain.ContributionVerdict
import com.opson.server.domain.UserId
import com.opson.server.http.ClientContext
import com.opson.server.plugins.problem
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.header
import io.ktor.server.response.respond
import org.slf4j.Logger

/**
 * Headers the app sends with contribution requests only (a food submission, a report), never
 * with logging: the random install key the app keeps for this (a Keychain item on iOS, the
 * install id on Android) and, once remote config turns it on, a DeviceCheck or Play Integrity
 * token. An app without them falls back to `X-Install-Id`.
 */
const val HEADER_DEVICE_KEY = "X-Device-Key"
const val HEADER_INTEGRITY_TOKEN = "X-Integrity-Token"

/**
 * Asks [gate] whether this account may contribute from this device. When it may not, answers
 * 403 `contributions_restricted` and returns true. The body is the ordinary problem envelope,
 * not the ban envelope, so the app shows a message instead of its blocked screen, and an app
 * from before this treats it as an ordinary failure. It never says whether the account or the
 * device was the reason.
 */
internal suspend fun ApplicationCall.refuseIfRestricted(
    gate: ContributionGate,
    userId: UserId,
    requestHash: String?,
    logger: Logger,
    what: String,
): Boolean {
    val signals = ContributionSignals(
        platform = request.header(ClientContext.HEADER_PLATFORM).orEmpty(),
        deviceKey = request.header(HEADER_DEVICE_KEY) ?: request.header(ClientContext.HEADER_INSTALL_ID),
        integrityToken = request.header(HEADER_INTEGRITY_TOKEN),
        requestHash = requestHash,
    )
    val verdict = gate.check(userId, signals)
    if (verdict !is ContributionVerdict.Restricted) return false
    logger.info("{} outcome=restricted by={}", what, verdict.by.name.lowercase())
    respond(
        HttpStatusCode.Forbidden,
        problem("contributions_restricted", "This account can't add to Opson's food database right now."),
    )
    return true
}
