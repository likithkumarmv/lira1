package com.example.chaos.domain.state

import android.app.Application
import android.nfc.NfcAdapter
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.chaos.data.backend.BackendApiClient
import com.example.chaos.data.esp32.Esp32ApiClient
import com.example.chaos.domain.models.AutomationPolicy
import com.example.chaos.domain.models.CallEscalation
import com.example.chaos.domain.models.Contact
import com.example.chaos.domain.models.DeviceStatus
import com.example.chaos.domain.models.Incident
import com.example.chaos.domain.models.IncidentState
import com.example.chaos.domain.models.RegisteredTag
import com.example.chaos.domain.models.SensorStatus
import com.example.chaos.networking.BackendStatus
import com.example.chaos.networking.ConnectionManager
import com.example.chaos.networking.WifiStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import com.example.chaos.data.backend.GeminiTriage
import android.media.MediaPlayer
import android.util.Base64
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.UUID

data class LiraUiState(
    val selectedTab: Int = 1, // 0: Connect, 1: Dashboard, 2: Incidents, 3: NFC, 4: Calls, 5: Policy
    val deviceStatus: DeviceStatus = DeviceStatus(),
    val incidents: List<Incident> = emptyList(),
    val activeCall: CallEscalation? = null,
    val callHistory: List<CallEscalation> = emptyList(),
    val registeredTags: List<RegisteredTag> = listOf(
        RegisteredTag("4A:5B:6C:7D", "Master Fob (Demo)", "2026-10-15 08:00"),
        RegisteredTag("9F:1E:2D:3C", "Lab Keycard", "2026-10-15 08:30")
    ),
    val contacts: List<Contact> = listOf(
        Contact("approved-contact-1", "Likith (Lead Supervisor)", "+91 63645 40011", "Lead Supervisor"),
        Contact("approved-contact-2", "Facility Duty Engineer", "+91 98***-**678", "Duty Engineer")
    ),
    val policy: AutomationPolicy = AutomationPolicy(),
    val isPhoneNfcSupported: Boolean = false,
    val isPhoneNfcEnabled: Boolean = false,
    val lastScannedPhoneTagUid: String? = null,
    val lastActionMessage: String? = null,
    val isActionInProgress: Boolean = false,
    val esp32Ssid: String = "LIRA-ESP32-SETUP",
    val esp32Pass: String = "lira12345",
    val esp32BaseUrl: String = "http://192.168.4.1",
    val backendBaseUrl: String = "http://127.0.0.1:3000",
    val ackCountdownSeconds: Int = 0,
    val latestTriage: GeminiTriage? = null,
    val isPlayingVoice: Boolean = false,
    val isDemoModeActive: Boolean = false,
    val demoSession: com.example.chaos.domain.models.DemoSession? = null,
    val demoInitStep: String? = null,
    val isShowcaseRunning: Boolean = false,
    val selectedDemoEvent: com.example.chaos.domain.models.DemoEvent? = null,
    val showEndDemoReportDialog: Boolean = false,
    val demoReportSentSuccess: Boolean = false,
    val demoExportJson: String? = null,
    val demoExportCsv: String? = null
)

class LiraViewModel(application: Application) : AndroidViewModel(application) {

    val connectionManager = ConnectionManager(application.applicationContext)
    val esp32Client = Esp32ApiClient(connectionManager)
    val backendClient = BackendApiClient(connectionManager)

    private val _uiState = MutableStateFlow(LiraUiState())
    val uiState: StateFlow<LiraUiState> = _uiState.asStateFlow()

    private var pollingJob: Job? = null
    private var countdownJob: Job? = null
    private val callAttemptMap = mutableMapOf<String, Int>()

    init {
        val nfcAdapter = NfcAdapter.getDefaultAdapter(application.applicationContext)
        val hasNfc = nfcAdapter != null
        _uiState.update { it.copy(isPhoneNfcSupported = hasNfc) }

        startPolling()
    }

    fun selectTab(index: Int) {
        _uiState.update { it.copy(selectedTab = index) }
    }

    fun updateSsid(ssid: String) {
        _uiState.update { it.copy(esp32Ssid = ssid) }
    }

    fun updatePass(pass: String) {
        _uiState.update { it.copy(esp32Pass = pass) }
    }

    fun updateEsp32Url(url: String) {
        _uiState.update { it.copy(esp32BaseUrl = url) }
        esp32Client.baseUrl = url
    }

    fun updateBackendUrl(url: String) {
        _uiState.update { it.copy(backendBaseUrl = url) }
        backendClient.baseUrl = url
    }

    fun togglePhoneNfc(enabled: Boolean) {
        if (_uiState.value.isPhoneNfcSupported) {
            _uiState.update { it.copy(isPhoneNfcEnabled = enabled) }
        }
    }

    fun updatePolicy(
        autoEscalation: Boolean,
        maxCalls: Int,
        ackTimeoutSec: Int,
        duplicateCooldownSec: Int,
        relayAutomation: Boolean
    ) {
        _uiState.update {
            it.copy(
                policy = it.policy.copy(
                    autoEscalation = autoEscalation,
                    maxCallsPerIncident = maxCalls.coerceIn(1, 2),
                    acknowledgementTimeoutSeconds = ackTimeoutSec,
                    duplicateIncidentCooldownSeconds = duplicateCooldownSec,
                    relayAutomation = relayAutomation
                )
            )
        }
    }

    fun connectEsp32() {
        val s = _uiState.value
        connectionManager.connectEsp32(s.esp32Ssid, s.esp32Pass)
    }

    fun disconnectEsp32() {
        connectionManager.disconnectEsp32()
    }

    fun testBackend() {
        viewModelScope.launch {
            connectionManager.setBackendStatus(BackendStatus.TESTING)
            val res = backendClient.checkHealth()
            if (res.isSuccess && res.getOrDefault(false)) {
                connectionManager.setBackendStatus(BackendStatus.ONLINE)
                _uiState.update { it.copy(lastActionMessage = "Backend connected successfully over cellular") }
            } else {
                connectionManager.setBackendStatus(BackendStatus.OFFLINE)
                _uiState.update { it.copy(lastActionMessage = "Backend unreachable or offline over cellular") }
            }
        }
    }

    fun setArmState(arm: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(isActionInProgress = true) }
            val result = esp32Client.setArmState(arm)
            _uiState.update { it.copy(isActionInProgress = false) }

            result.onSuccess { (isArmed, state) ->
                _uiState.update {
                    it.copy(
                        deviceStatus = it.deviceStatus.copy(armed = isArmed, state = state),
                        lastActionMessage = if (isArmed) "Device Armed successfully" else "Device Disarmed"
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(lastActionMessage = "Arm state change failed: ${err.message}")
                }
            }
        }
    }

    fun acknowledgeCurrentIncident(method: String = "app") {
        val currentInc = _uiState.value.deviceStatus.currentIncident ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isActionInProgress = true) }
            val result = esp32Client.acknowledgeIncident(currentInc.incidentId, method)
            _uiState.update { it.copy(isActionInProgress = false) }

            result.onSuccess {
                _uiState.update {
                    val updatedIncidents = it.incidents.map { inc ->
                        if (inc.incidentId == currentInc.incidentId) {
                            inc.copy(state = IncidentState.ACKNOWLEDGED, acknowledgedBy = method)
                        } else inc
                    }
                    it.copy(
                        incidents = updatedIncidents,
                        deviceStatus = it.deviceStatus.copy(
                            state = IncidentState.ACKNOWLEDGED,
                            currentIncident = null
                        ),
                        lastActionMessage = "Incident ${currentInc.incidentId} acknowledged via $method"
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(lastActionMessage = "Acknowledgement failed: ${err.message}")
                }
            }
        }
    }

    fun handlePhoneNfcScanned(uid: String) {
        _uiState.update { it.copy(lastScannedPhoneTagUid = uid) }
        val registered = _uiState.value.registeredTags.any { it.uid.equals(uid, ignoreCase = true) }
        if (!registered) {
            _uiState.update { it.copy(lastActionMessage = "Unregistered phone NFC tag scanned: $uid") }
            return
        }

        val currentInc = _uiState.value.deviceStatus.currentIncident
        if (currentInc != null && currentInc.state == IncidentState.AWAITING_ACK) {
            acknowledgeCurrentIncident(method = "phone_nfc")
        } else {
            val willArm = !_uiState.value.deviceStatus.armed
            setArmState(willArm)
        }
    }

    fun enrollTag(uid: String, label: String) {
        if (uid.isBlank()) return
        val nowStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
        _uiState.update {
            val exists = it.registeredTags.any { t -> t.uid.equals(uid, ignoreCase = true) }
            if (exists) {
                it.copy(lastActionMessage = "Tag $uid already registered")
            } else {
                val updated = it.registeredTags + RegisteredTag(uid.trim().uppercase(), label.ifBlank { "Registered Tag" }, nowStr)
                it.copy(registeredTags = updated, lastActionMessage = "Enrolled tag $uid")
            }
        }
    }

    fun removeTag(uid: String) {
        _uiState.update {
            val updated = it.registeredTags.filterNot { t -> t.uid.equals(uid, ignoreCase = true) }
            it.copy(registeredTags = updated, lastActionMessage = "Removed tag $uid")
        }
    }

    fun simulateSensorEdge(sensorType: String) {
        if (!_uiState.value.deviceStatus.armed) {
            _uiState.update {
                it.copy(lastActionMessage = "Notice: Device is DISARMED. Tap 'Arm Device' first to activate incident detection.")
            }
            return
        }

        val newIncId = "inc-${System.currentTimeMillis() % 10000}"
        val timeoutSec = _uiState.value.policy.acknowledgementTimeoutSeconds
        val nowMs = System.currentTimeMillis()
        val deadlineMs = nowMs + (timeoutSec * 1000L)

        val newIncSummary = com.example.chaos.domain.models.IncidentSummary(
            incidentId = newIncId,
            type = sensorType,
            state = IncidentState.AWAITING_ACK,
            startedUptimeMs = nowMs,
            ackDeadlineUptimeMs = deadlineMs
        )

        val newIncident = Incident(
            incidentId = newIncId,
            type = sensorType,
            state = IncidentState.AWAITING_ACK,
            startedAt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()),
            startedUptimeMs = nowMs,
            ackDeadlineUptimeMs = deadlineMs,
            evidence = listOf("${sensorType}_digital_edge", "awaiting_ack_countdown")
        )

        _uiState.update { state ->
            val updatedSensors = state.deviceStatus.sensors.toMutableMap()
            updatedSensors[sensorType] = com.example.chaos.domain.models.SensorStatus(configured = true, active = true, ready = true)
            state.copy(
                deviceStatus = state.deviceStatus.copy(
                    state = IncidentState.AWAITING_ACK,
                    currentIncident = newIncSummary,
                    sensors = updatedSensors
                ),
                incidents = listOf(newIncident) + state.incidents,
                ackCountdownSeconds = timeoutSec,
                lastActionMessage = "Physical event simulated: $sensorType detected! Awaiting acknowledgement."
            )
        }

        requestGeminiTriageForIncident(newIncId, sensorType, listOf("${sensorType}_digital_edge", "awaiting_ack_countdown"))

        // Manage countdown
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            for (remaining in timeoutSec downTo 0) {
                _uiState.update { it.copy(ackCountdownSeconds = remaining) }
                delay(1000)
                val curr = _uiState.value.deviceStatus.currentIncident
                if (curr == null || curr.state != IncidentState.AWAITING_ACK) break
            }

            val finalState = _uiState.value.deviceStatus.currentIncident
            if (finalState != null && finalState.state == IncidentState.AWAITING_ACK) {
                _uiState.update {
                    val updatedInc = it.incidents.map { inc ->
                        if (inc.incidentId == finalState.incidentId) inc.copy(state = IncidentState.ESCALATION_PENDING) else inc
                    }
                    it.copy(
                        incidents = updatedInc,
                        deviceStatus = it.deviceStatus.copy(
                            state = IncidentState.ESCALATION_PENDING,
                            currentIncident = finalState.copy(state = IncidentState.ESCALATION_PENDING)
                        ),
                        lastActionMessage = "Acknowledgement timeout expired. Escalation pending."
                    )
                }

                if (_uiState.value.policy.autoEscalation) {
                    val approvedId = _uiState.value.policy.allowedContactIds.firstOrNull()
                    if (approvedId != null) {
                        startApprovedCall(approvedId, finalState.incidentId)
                    }
                }
            }
        }
    }

    fun simulateTagTap(uid: String) {
        handlePhoneNfcScanned(uid)
    }

    fun requestGeminiTriageForIncident(incidentId: String, eventType: String, evidence: List<String>) {
        viewModelScope.launch {
            val res = backendClient.requestGeminiTriage(incidentId, eventType, evidence)
            res.onSuccess { triage ->
                _uiState.update { it.copy(latestTriage = triage) }
            }
        }
    }

    fun playElevenLabsVoice(text: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isPlayingVoice = true, lastActionMessage = "Synthesizing voice alert via ElevenLabs...") }
            val res = backendClient.synthesizeVoice(text)
            res.onSuccess { base64Audio ->
                try {
                    val audioBytes = Base64.decode(base64Audio, Base64.DEFAULT)
                    val tempFile = File.createTempFile("eleven_lira", ".mp3", getApplication<Application>().cacheDir)
                    FileOutputStream(tempFile).use { it.write(audioBytes) }

                    val mediaPlayer = MediaPlayer()
                    mediaPlayer.setDataSource(tempFile.absolutePath)
                    mediaPlayer.prepare()
                    mediaPlayer.setOnCompletionListener {
                        _uiState.update { it.copy(isPlayingVoice = false, lastActionMessage = "Voice playback completed.") }
                        tempFile.delete()
                        mediaPlayer.release()
                    }
                    mediaPlayer.start()
                    _uiState.update { it.copy(lastActionMessage = "Speaking bilingual alert (ElevenLabs)...") }
                } catch (e: Exception) {
                    _uiState.update { it.copy(isPlayingVoice = false, lastActionMessage = "Playback error: ${e.message}") }
                }
            }.onFailure { err ->
                _uiState.update { it.copy(isPlayingVoice = false, lastActionMessage = "ElevenLabs TTS error: ${err.message}") }
            }
        }
    }

    fun startApprovedCall(contactId: String, incidentId: String? = null) {
        val contact = _uiState.value.contacts.firstOrNull { it.contactId == contactId } ?: return
        val currentInc = _uiState.value.deviceStatus.currentIncident

        val targetIncident: Incident = if (incidentId != null) {
            _uiState.value.incidents.firstOrNull { it.incidentId == incidentId }
                ?: Incident(
                    incidentId = incidentId,
                    type = "escalated_incident",
                    state = IncidentState.ESCALATION_PENDING,
                    evidence = listOf("manual_escalation")
                )
        } else currentInc?.let {
            Incident(
                incidentId = it.incidentId,
                type = it.type,
                state = it.state,
                evidence = listOf("manual_operator_request")
            )
        } ?: Incident(
            incidentId = "test-call-${System.currentTimeMillis() % 10000}",
            type = "operator_test",
            state = IncidentState.ESCALATION_PENDING,
            evidence = listOf("manual_test_call")
        )

        val attempts = callAttemptMap.getOrDefault(targetIncident.incidentId, 0)
        if (attempts >= _uiState.value.policy.maxCallsPerIncident) {
            _uiState.update {
                it.copy(lastActionMessage = "Call limit reached for incident (${_uiState.value.policy.maxCallsPerIncident} max)")
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isActionInProgress = true) }
            val idempotencyKey = "${targetIncident.incidentId}-attempt-${attempts + 1}"
            val goal = "LIRA Alert: ${targetIncident.type.replace('_', ' ')} detected on physical sensor. Acknowledge status."

            val result = backendClient.createEscalation(
                incident = targetIncident,
                contactId = contact.contactId,
                goal = goal,
                idempotencyKey = idempotencyKey
            )
            _uiState.update { it.copy(isActionInProgress = false) }

            result.onSuccess { call ->
                callAttemptMap[targetIncident.incidentId] = attempts + 1
                val detailedCall = call.copy(
                    contactName = contact.name,
                    contactPhoneMasked = contact.phoneMasked
                )
                _uiState.update {
                    it.copy(
                        activeCall = detailedCall,
                        callHistory = listOf(detailedCall) + it.callHistory.filterNot { c -> c.jobId == detailedCall.jobId },
                        lastActionMessage = "Call dispatched: ${detailedCall.jobId}"
                    )
                }
                pollCallStatus(detailedCall.jobId)
            }.onFailure { err ->
                _uiState.update {
                    it.copy(lastActionMessage = "Call escalation failed: ${err.message}")
                }
            }
        }
    }

    private fun pollCallStatus(jobId: String) {
        viewModelScope.launch {
            var counter = 0
            while (isActive && counter < 15) {
                delay(3000)
                counter++
                val res = backendClient.getEscalationStatus(jobId)
                if (res.isSuccess) {
                    val updated = res.getOrNull()
                    if (updated != null) {
                        _uiState.update { state ->
                            val currentActive = if (state.activeCall?.jobId == jobId) updated else state.activeCall
                            val history = state.callHistory.map { if (it.jobId == jobId) updated else it }
                            state.copy(activeCall = currentActive, callHistory = history)
                        }
                        if (updated.status in listOf("completed", "failed", "no_answer", "cancelled")) {
                            break
                        }
                    }
                }
            }
        }
    }

    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (isActive) {
                delay(1500)
                if (connectionManager.esp32WifiStatus.value == WifiStatus.CONNECTED) {
                    val statusRes = esp32Client.getStatus()
                    statusRes.onSuccess { status ->
                        _uiState.update { it.copy(deviceStatus = status) }
                        handleIncomingDeviceStatus(status)
                    }
                }
            }
        }
    }

    private fun handleIncomingDeviceStatus(status: DeviceStatus) {
        val currentInc = status.currentIncident ?: return

        // Manage countdown if in AWAITING_ACK
        if (currentInc.state == IncidentState.AWAITING_ACK && currentInc.ackDeadlineUptimeMs != null) {
            val remainingMs = currentInc.ackDeadlineUptimeMs - (status.uptimeSeconds * 1000)
            val sec = (remainingMs / 1000).toInt().coerceAtLeast(0)
            _uiState.update { it.copy(ackCountdownSeconds = sec) }
        } else {
            _uiState.update { it.copy(ackCountdownSeconds = 0) }
        }

        // Auto-escalation policy trigger
        if (currentInc.state == IncidentState.ESCALATION_PENDING && _uiState.value.policy.autoEscalation) {
            val approvedId = _uiState.value.policy.allowedContactIds.firstOrNull()
            if (approvedId != null) {
                val attempts = callAttemptMap.getOrDefault(currentInc.incidentId, 0)
                if (attempts < _uiState.value.policy.maxCallsPerIncident && _uiState.value.activeCall?.incidentId != currentInc.incidentId) {
                    startApprovedCall(approvedId, currentInc.incidentId)
                }
            }
        }
    }

    // ----------------------------------------------------
    // Demo Mode Management
    // ----------------------------------------------------
    private var showcaseJob: Job? = null
    private var demoDurationJob: Job? = null

    fun startDemoMode() {
        if (_uiState.value.isDemoModeActive && _uiState.value.demoSession != null) {
            _uiState.update { it.copy(selectedTab = 6) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isActionInProgress = true, demoInitStep = "Initializing LIRA...") }
            delay(400)
            _uiState.update { it.copy(demoInitStep = "Connecting to security system...") }
            delay(400)
            _uiState.update { it.copy(demoInitStep = "Preparing simulation...") }
            delay(400)
            _uiState.update { it.copy(demoInitStep = "Demo session active") }

            val res = backendClient.startDemoSession()
            res.onSuccess { session ->
                _uiState.update {
                    it.copy(
                        isDemoModeActive = true,
                        demoSession = session,
                        selectedTab = 6,
                        isActionInProgress = false,
                        demoInitStep = null,
                        lastActionMessage = "Demo Mode initialized. Real-time simulation active."
                    )
                }
                esp32Client.updateTftDisplay("DEMO MODE ACTIVE")
                startDemoDurationTimer()
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isActionInProgress = false,
                        demoInitStep = null,
                        lastActionMessage = "Demo init error: ${err.message}"
                    )
                }
            }
        }
    }

    private fun startDemoDurationTimer() {
        demoDurationJob?.cancel()
        demoDurationJob = viewModelScope.launch {
            while (isActive) {
                delay(1000)
                val curr = _uiState.value.demoSession
                if (curr != null && curr.status == "ACTIVE") {
                    _uiState.update {
                        it.copy(demoSession = curr.copy(durationSeconds = curr.durationSeconds + 1))
                    }
                } else if (curr != null && curr.status == "ENDED") {
                    break
                }
            }
        }
    }

    fun triggerNextShowcaseStep() {
        viewModelScope.launch {
            val res = backendClient.triggerDemoShowcaseStep()
            res.onSuccess { (event, session) ->
                _uiState.update {
                    it.copy(
                        demoSession = session,
                        lastActionMessage = "Simulated Event: ${event?.personLabel} (${event?.authResult})"
                    )
                }
                if (event != null) {
                    if (event.authResult == "Authorized") {
                        esp32Client.updateTftDisplay("ENTRY: AUTHORIZED")
                    } else {
                        esp32Client.updateTftDisplay("SECURITY ALERT")
                        delay(2500)
                        esp32Client.updateTftDisplay("CALL IN PROGRESS")
                    }
                }
            }
        }
    }

    fun runShowcaseScenario() {
        if (_uiState.value.isShowcaseRunning) {
            showcaseJob?.cancel()
            _uiState.update { it.copy(isShowcaseRunning = false, lastActionMessage = "Showcase paused.") }
            return
        }

        _uiState.update { it.copy(isShowcaseRunning = true, lastActionMessage = "Running showcase scenario...") }
        showcaseJob = viewModelScope.launch {
            for (step in 1..4) {
                triggerNextShowcaseStep()
                delay(4000)
            }
            _uiState.update { it.copy(isShowcaseRunning = false, lastActionMessage = "Showcase scenario steps completed.") }
        }
    }

    fun pauseDemoMode() {
        viewModelScope.launch {
            showcaseJob?.cancel()
            val res = backendClient.pauseDemoSession()
            res.onSuccess { session ->
                _uiState.update { it.copy(demoSession = session, isShowcaseRunning = false, lastActionMessage = "Demo session paused.") }
            }
        }
    }

    fun resumeDemoMode() {
        viewModelScope.launch {
            val res = backendClient.resumeDemoSession()
            res.onSuccess { session ->
                _uiState.update { it.copy(demoSession = session, lastActionMessage = "Demo session resumed.") }
            }
        }
    }

    fun resetDemoMode() {
        viewModelScope.launch {
            showcaseJob?.cancel()
            val res = backendClient.resetDemoSession()
            res.onSuccess { session ->
                _uiState.update { it.copy(demoSession = session, isShowcaseRunning = false, lastActionMessage = "Demo session reset.") }
                esp32Client.updateTftDisplay("DEMO MODE ACTIVE")
            }
        }
    }

    fun endDemoMode() {
        viewModelScope.launch {
            showcaseJob?.cancel()
            demoDurationJob?.cancel()
            _uiState.update { it.copy(isActionInProgress = true, lastActionMessage = "Finalizing session & generating email report...") }

            val res = backendClient.endDemoSession()
            _uiState.update { it.copy(isActionInProgress = false) }

            res.onSuccess { (session, emailSuccess) ->
                _uiState.update {
                    it.copy(
                        demoSession = session,
                        isShowcaseRunning = false,
                        showEndDemoReportDialog = true,
                        demoReportSentSuccess = emailSuccess,
                        lastActionMessage = if (emailSuccess) "Session ended. Report delivered to ${session.reportRecipient}!" else "Session ended. Email report pending retry."
                    )
                }
                esp32Client.updateTftDisplay("SESSION ENDED")
            }.onFailure { err ->
                _uiState.update { it.copy(lastActionMessage = "End session error: ${err.message}") }
            }
        }
    }

    fun selectDemoEvent(event: com.example.chaos.domain.models.DemoEvent?) {
        _uiState.update { it.copy(selectedDemoEvent = event) }
    }

    fun dismissDemoReportDialog() {
        _uiState.update { it.copy(showEndDemoReportDialog = false) }
    }

    fun resendDemoReport() {
        viewModelScope.launch {
            _uiState.update { it.copy(isActionInProgress = true, lastActionMessage = "Resending report email...") }
            val res = backendClient.resendDemoReport()
            _uiState.update { it.copy(isActionInProgress = false) }
            res.onSuccess {
                _uiState.update { it.copy(demoReportSentSuccess = true, lastActionMessage = "Report email successfully resent!") }
            }.onFailure { err ->
                _uiState.update { it.copy(lastActionMessage = "Resend error: ${err.message}") }
            }
        }
    }

    fun exportDemo(format: String) {
        viewModelScope.launch {
            val res = backendClient.exportDemoReport(format)
            res.onSuccess { data ->
                if (format == "json") {
                    _uiState.update { it.copy(demoExportJson = data, lastActionMessage = "JSON export ready.") }
                } else {
                    _uiState.update { it.copy(demoExportCsv = data, lastActionMessage = "CSV export ready.") }
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        pollingJob?.cancel()
        countdownJob?.cancel()
        showcaseJob?.cancel()
        demoDurationJob?.cancel()
        connectionManager.cleanUp()
    }
}
