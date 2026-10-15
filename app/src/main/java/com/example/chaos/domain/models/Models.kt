package com.example.chaos.domain.models

enum class IncidentState {
    DISARMED,
    ARMED_IDLE,
    INCIDENT_DETECTED,
    AWAITING_ACK,
    ACKNOWLEDGED,
    ESCALATION_PENDING,
    CALLING,
    CALL_RESULT_AVAILABLE,
    CALL_FAILED,
    RESOLVED,
    OPEN;

    companion object {
        fun fromString(value: String?): IncidentState {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: DISARMED
        }
    }
}

data class SensorStatus(
    val configured: Boolean = false,
    val active: Boolean? = null,
    val ready: Boolean? = null,
    val lastChangedUptimeMs: Long? = null,
    val errorCode: String? = null
)

data class IncidentSummary(
    val incidentId: String,
    val type: String,
    val state: IncidentState,
    val startedUptimeMs: Long? = null,
    val ackDeadlineUptimeMs: Long? = null
)

data class DeviceStatus(
    val schemaVersion: Int = 1,
    val deviceId: String = "lira-esp32-01",
    val firmwareVersion: String = "0.1.0",
    val uptimeSeconds: Long = 0,
    val armed: Boolean = false,
    val state: IncidentState = IncidentState.DISARMED,
    val sensors: Map<String, SensorStatus> = emptyMap(),
    val currentIncident: IncidentSummary? = null,
    val lastEventId: String? = null
)

data class Incident(
    val incidentId: String,
    val deviceId: String = "lira-esp32-01",
    val type: String, // motion, vibration, motion_and_vibration, unknown_tag
    val state: IncidentState,
    val startedAt: String? = null,
    val startedUptimeMs: Long? = null,
    val ackDeadlineUptimeMs: Long? = null,
    val evidence: List<String> = emptyList(),
    val acknowledgedBy: String? = null,
    val escalationJobId: String? = null,
    val resolutionNote: String? = null
)

data class CallEscalation(
    val jobId: String,
    val incidentId: String,
    val contactId: String,
    val contactName: String,
    val contactPhoneMasked: String,
    val goal: String,
    val status: String, // queued, dialing, in_progress, completed, no_answer, busy, failed, cancelled, unknown
    val attempt: Int = 1,
    val createdAt: String,
    val durationSeconds: Int? = null,
    val transcriptSummary: String? = null,
    val transcript: String? = null,
    val error: String? = null
)

data class Contact(
    val contactId: String,
    val name: String,
    val phoneMasked: String,
    val role: String
)

data class AutomationPolicy(
    val autoEscalation: Boolean = false,
    val maxCallsPerIncident: Int = 1,
    val acknowledgementTimeoutSeconds: Int = 20,
    val duplicateIncidentCooldownSeconds: Int = 60,
    val relayAutomation: Boolean = false,
    val allowedContactIds: List<String> = listOf("approved-contact-1")
)

data class RegisteredTag(
    val uid: String,
    val label: String,
    val enrolledAt: String
)
