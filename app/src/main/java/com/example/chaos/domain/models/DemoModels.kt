package com.example.chaos.domain.models

data class DemoEvent(
    val eventId: String,
    val sessionId: String,
    val personLabel: String,
    val authResult: String, // "Authorized" or "Unauthorized"
    val type: String,       // "ENTRY" or "EXIT"
    val timestamp: String,
    val timestampIst: String,
    val explanation: String,
    val responseStatus: String // "RESOLVED_SAFE" or "ESCALATION_TRIGGERED"
)

data class DemoIncident(
    val incidentId: String,
    val eventId: String,
    val personLabel: String,
    val startedAt: String,
    val state: String,
    val reason: String
)

data class DemoSession(
    val sessionId: String,
    val status: String = "ACTIVE", // "ACTIVE", "PAUSED", "ENDED"
    val startTime: String,
    val startTimeIst: String,
    val endTime: String? = null,
    val endTimeIst: String? = null,
    val durationSeconds: Int = 0,
    val totalEntries: Int = 0,
    val authorizedCount: Int = 0,
    val unauthorizedCount: Int = 0,
    val peopleInsideCount: Int = 0,
    val peopleInside: List<String> = emptyList(),
    val events: List<DemoEvent> = emptyList(),
    val activeIncident: DemoIncident? = null,
    val calls: List<CallEscalation> = emptyList(),
    val reportSent: Boolean = false,
    val reportSentAt: String? = null,
    val reportRecipient: String = "likithkumarmv6364@gmail.com",
    val reportMessageId: String? = null,
    val reportError: String? = null
)
