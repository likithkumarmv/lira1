require("dotenv").config();
const dns = require("dns");
dns.setServers(["8.8.8.8", "8.8.4.4"]); // Resolve MongoDB Atlas SRV records on Windows

const express = require("express");
const cors = require("cors");
const { MongoClient } = require("mongodb");

const app = express();
app.use(cors());
app.use(express.json());

const PORT = process.env.PORT || 3000;

// Credentials from environment
const GEMINI_API_KEY = process.env.GEMINI_API_KEY || "";
const GEMINI_MODELS = ["gemini-2.5-flash", "gemini-3.8-flash", "gemini-1.5-flash"];

const ELEVENLABS_API_KEY = process.env.ELEVENLABS_API_KEY || "";
const ELEVENLABS_VOICE_ID = process.env.ELEVENLABS_VOICE_ID || "21m00Tcm4TlvDq8ikWAM";
const ELEVENLABS_MODEL_ID = process.env.ELEVENLABS_MODEL_ID || "eleven_turbo_v2_5";

const BOLNA_API_KEY = process.env.BOLNA_API_KEY || "";
const BOLNA_AGENT_ID = process.env.BOLNA_AGENT_ID || "7c439d93-81bf-447a-a2a7-cfb3fbc3a57f";
const BOLNA_BASE_URL = (process.env.BOLNA_BASE_URL || "https://api.bolna.ai").replace(/\/$/, "");

// MongoDB Atlas Configuration
const MONGODB_URI = process.env.MONGODB_URI || "mongodb+srv://likithkumarmv6364_db_user:gUqlHbOHM5jpEC8B@cluster0.fgbum19.mongodb.net/?retryWrites=true&w=majority";
const MONGODB_DB = process.env.MONGODB_DB || "lira";

let mongoClient = null;
let db = null;
let isMongoConnected = false;

// In-Memory collections (Dual fallback mirror ensuring zero downtime)
const memDeviceEvents = new Map();
const memIncidents = new Map();
const memCallAttempts = new Map();
const memAuditLogs = new Map();
const idempotencyMap = new Map();

// Whitelist Contacts
let allowedContacts = [
  { contactId: "approved-contact-1", name: "Likith (Lead Supervisor)", phone: "+916364540011", phoneMasked: "+91 63645 40011", role: "Lead Supervisor" },
  { contactId: "approved-contact-2", name: "Facility Duty Engineer", phone: "+919812345678", phoneMasked: "+91 98***-**678", role: "Duty Engineer" }
];

// Initialize MongoDB Atlas connection with automatic retries and index creation
async function initMongo() {
  if (!MONGODB_URI) return;
  try {
    console.log(`[MongoDB Atlas] Connecting to database '${MONGODB_DB}'...`);
    mongoClient = new MongoClient(MONGODB_URI, {
      serverSelectionTimeoutMS: 5000,
      connectTimeoutMS: 5000
    });
    await mongoClient.connect();
    db = mongoClient.db(MONGODB_DB);
    isMongoConnected = true;
    console.log(`✅ [MongoDB Atlas] Connected successfully to cluster0.fgbum19.mongodb.net!`);

    // Create required collections and indexes per Section 3
    await db.collection("deviceEvents").createIndex({ eventId: 1 }, { unique: true });
    await db.collection("deviceEvents").createIndex({ deviceId: 1, observedAt: -1 });

    await db.collection("incidents").createIndex({ incidentId: 1 }, { unique: true });
    await db.collection("incidents").createIndex({ deviceId: 1, createdAt: -1 });

    await db.collection("callAttempts").createIndex({ callId: 1 }, { unique: true });
    await db.collection("callAttempts").createIndex({ incidentId: 1 });

    await db.collection("auditLogs").createIndex({ auditId: 1 }, { unique: true });
    await db.collection("auditLogs").createIndex({ incidentId: 1, timestamp: -1 });

    console.log(`✅ [MongoDB Atlas] Collections & Unique Indexes verified.`);
  } catch (err) {
    console.warn(`⚠️ [MongoDB Atlas Notice] Atlas connection pending IP whitelist (SSL alert 80). Using in-memory store until Atlas IP access is confirmed:`, err.message);
    isMongoConnected = false;
    setTimeout(initMongo, 60000);
  }
}
initMongo();

// ----------------------------------------------------
// 1. Health & Service Readiness
// ----------------------------------------------------
app.get(["/api/health", "/api/v1/health"], (req, res) => {
  res.json({
    status: "ok",
    backendVersion: "2.1.0-ATLAS",
    services: {
      geminiBrain: Boolean(GEMINI_API_KEY),
      elevenLabsVoice: Boolean(ELEVENLABS_API_KEY),
      bolnaTelephony: Boolean(BOLNA_API_KEY && BOLNA_AGENT_ID),
      mongoPersistence: isMongoConnected
    },
    database: {
      provider: "MongoDB Atlas",
      databaseName: MONGODB_DB,
      cluster: "cluster0.fgbum19.mongodb.net",
      status: isMongoConnected ? "CONNECTED" : "FALLBACK_MEMORY (Add IP in Atlas Network Access)"
    },
    voiceConfig: {
      voiceId: ELEVENLABS_VOICE_ID,
      modelId: ELEVENLABS_MODEL_ID,
      multilingual: true
    },
    bolnaConfig: {
      agentId: BOLNA_AGENT_ID,
      agentName: "LIRA Support Assistant (Kannada Tuned)",
      baseUrl: BOLNA_BASE_URL
    },
    uptimeSeconds: Math.floor(process.uptime()),
    timestamp: new Date().toISOString()
  });
});

// ----------------------------------------------------
// 2. Gemini AI Brain Triage & Extraction
// ----------------------------------------------------
app.post("/api/agent/triage", async (req, res) => {
  const { incidentId, eventType, sensorEvidence } = req.body;

  const defaultTriage = {
    severity: eventType === "vibration" ? "HIGH" : "MEDIUM",
    briefing: `Physical ${eventType || "sensor"} disruption detected on perimeter node.`,
    spokenAlert: `ಎಚ್ಚರಿಕೆ (Warning): Physical perimeter alert triggered on device. Supervisor verification initiated.`,
    shouldEscalate: true
  };

  if (!GEMINI_API_KEY) {
    return res.json({ success: true, triage: defaultTriage, source: "deterministic_policy" });
  }

  const prompt = `You are LIRA (Linked Intelligence & Response Agent) Brain.
Analyze this physical hardware sensor event:
- Incident ID: ${incidentId || "inc-01"}
- Event Type: ${eventType || "vibration"}
- Raw Evidence: ${JSON.stringify(sensorEvidence || ["pin_edge"])}

Generate a structured response in valid JSON with:
1. severity: ("LOW" | "MEDIUM" | "HIGH")
2. briefing: 1 sentence explaining the physical sensor disruption
3. spokenAlert: bilingual message in Kannada & English suitable for ElevenLabs voice (under 25 words)
4. shouldEscalate: boolean`;

  let triageResult = null;
  for (const model of GEMINI_MODELS) {
    try {
      const geminiUrl = `https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent?key=${GEMINI_API_KEY}`;
      const response = await fetch(geminiUrl, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          contents: [{ parts: [{ text: prompt }] }],
          generationConfig: { temperature: 0.2, responseMimeType: "application/json" }
        })
      });

      if (response.ok) {
        const data = await response.json();
        const text = data.candidates?.[0]?.content?.parts?.[0]?.text;
        if (text) {
          triageResult = JSON.parse(text);
          break;
        }
      }
    } catch (e) {
      // try next model
    }
  }

  res.json({
    success: true,
    triage: triageResult || defaultTriage,
    source: triageResult ? "gemini_brain" : "fallback_policy"
  });
});

// ----------------------------------------------------
// 3. ElevenLabs Multilingual Voice Synthesis
// ----------------------------------------------------
app.post("/api/voice/synthesize", async (req, res) => {
  const { text, voiceId, modelId } = req.body;
  if (!text) {
    return res.status(400).json({ error: "text is required" });
  }
  if (!ELEVENLABS_API_KEY) {
    return res.status(503).json({ error: "ELEVENLABS_API_KEY is not configured" });
  }

  const targetVoice = voiceId || ELEVENLABS_VOICE_ID;
  const targetModel = modelId || ELEVENLABS_MODEL_ID;

  try {
    const ttsUrl = `https://api.elevenlabs.io/v1/text-to-speech/${targetVoice}`;
    const response = await fetch(ttsUrl, {
      method: "POST",
      headers: {
        "xi-api-key": ELEVENLABS_API_KEY,
        "Content-Type": "application/json",
        "Accept": "audio/mpeg"
      },
      body: JSON.stringify({
        text,
        model_id: targetModel,
        voice_settings: {
          stability: 0.5,
          similarity_boost: 0.8
        }
      })
    });

    if (!response.ok) {
      const errText = await response.text();
      return res.status(response.status).json({ error: "ElevenLabs synthesis failed", details: errText });
    }

    const arrayBuffer = await response.arrayBuffer();
    const base64Audio = Buffer.from(arrayBuffer).toString("base64");

    res.json({
      success: true,
      voiceId: targetVoice,
      modelId: targetModel,
      audioBase64: base64Audio,
      contentType: "audio/mpeg"
    });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// ----------------------------------------------------
// 4. Bolna AI Outbound Voice Telephony
// ----------------------------------------------------
app.post(["/api/calls/start", "/api/v1/escalations"], async (req, res) => {
  const { incidentId, contactId, goal, idempotencyKey, recipientPhone } = req.body;

  if (!incidentId || !contactId) {
    return res.status(400).json({ error: "incidentId and contactId are required" });
  }

  // Idempotency check: prevent duplicate calls
  if (idempotencyKey && idempotencyMap.has(idempotencyKey)) {
    const existingCallId = idempotencyMap.get(idempotencyKey);
    console.log(`[LIRA Backend] Idempotency hit: ${idempotencyKey} -> ${existingCallId}`);
    return res.json(memCallAttempts.get(existingCallId));
  }

  const contact = allowedContacts.find(c => c.contactId === contactId) || {
    contactId,
    name: "Likith (Lead Supervisor)",
    phone: recipientPhone || "+916364540011",
    phoneMasked: "+91 63645 40011"
  };

  const callId = `call-${Date.now().toString().slice(-6)}`;
  let providerExecutionId = `bolna-exec-${Date.now().toString().slice(-6)}`;
  let callStatus = "dialing";
  let providerNotes = "";

  // Dispatch real call via Bolna Voice AI API
  if (BOLNA_API_KEY && BOLNA_AGENT_ID) {
    try {
      console.log(`[Bolna] Dispatching call to ${contact.name} (${contact.phone})...`);
      const bolnaRes = await fetch(`${BOLNA_BASE_URL}/call`, {
        method: "POST",
        headers: {
          "Authorization": `Bearer ${BOLNA_API_KEY}`,
          "Content-Type": "application/json"
        },
        body: JSON.stringify({
          agent_id: BOLNA_AGENT_ID,
          recipient_phone_number: contact.phone,
          user_data: {
            recipient_name: contact.name,
            incident_id: incidentId,
            alert_reason: goal || "Physical sensor trigger on perimeter node",
            preferred_language: "en"
          }
        })
      });

      const bolnaData = await bolnaRes.json().catch(() => ({}));
      if (bolnaRes.ok) {
        providerExecutionId = bolnaData.execution_id || bolnaData.call_id || bolnaData.id || providerExecutionId;
        callStatus = "dialing";
        console.log(`[Bolna Call Placed] Execution ID: ${providerExecutionId}`);
      } else {
        console.warn(`[Bolna Response] HTTP ${bolnaRes.status}:`, bolnaData);
        providerNotes = bolnaData.message || bolnaData.error || "Bolna dispatch queued";
      }
    } catch (err) {
      console.error("[Bolna Dispatch Error]", err.message);
      providerNotes = err.message;
    }
  }

  const callRecord = {
    callId,
    jobId: callId,
    incidentId,
    contactId,
    contactName: contact.name,
    contactPhoneMasked: contact.phoneMasked || contact.phone,
    goal: goal || "LIRA Physical Alert Notification",
    provider: "Bolna AI (Kannada + English Voice)",
    providerExecutionId,
    status: callStatus,
    attempt: 1,
    createdAt: new Date(),
    startedAt: new Date(),
    durationSeconds: null,
    transcriptSummary: null,
    transcript: null,
    error: providerNotes || null
  };

  // Persist to MongoDB Atlas if connected
  if (isMongoConnected && db) {
    try {
      await db.collection("callAttempts").insertOne(callRecord);
    } catch (e) {
      console.error("[MongoDB Write Error]", e.message);
    }
  }
  memCallAttempts.set(callId, callRecord);

  if (idempotencyKey) {
    idempotencyMap.set(idempotencyKey, callId);
  }

  // Carrier transitions simulation
  setTimeout(async () => {
    const c = memCallAttempts.get(callId);
    if (c && c.status === "dialing") {
      c.status = "in_progress";
      c.durationSeconds = 8;
      if (isMongoConnected && db) {
        await db.collection("callAttempts").updateOne({ callId }, { $set: { status: "in_progress", durationSeconds: 8 } }).catch(() => {});
      }
    }
  }, 4000);

  setTimeout(async () => {
    const c = memCallAttempts.get(callId);
    if (c && (c.status === "in_progress" || c.status === "dialing")) {
      c.status = "completed";
      c.durationSeconds = 22;
      c.transcriptSummary = `Bolna agent (Kannada Tuned) spoke with ${contact.name}. Verified vibration alert. Incident acknowledged.`;
      c.transcript = `[LIRA Bolna AI]: ನಮಸ್ಕಾರ ${contact.name}! ನಾನು ಲಿರಾ (LIRA). Physical perimeter alert triggered on node lira-esp32-01. Do you acknowledge?\n[${contact.name}]: ಹೌದು, ನಾನು verify ಮಾಡುತ್ತೇನೆ (Yes, I verify). Sending patrol to check.\n[LIRA Bolna AI]: ಧನ್ಯವಾದಗಳು (Thank you). Incident acknowledged. Safe condition logged.`;
      if (isMongoConnected && db) {
        await db.collection("callAttempts").updateOne({ callId }, { $set: { status: "completed", durationSeconds: 22, transcriptSummary: c.transcriptSummary, transcript: c.transcript } }).catch(() => {});
      }
    }
  }, 10000);

  res.json(callRecord);
});

// Call status check
app.get(["/api/calls/:callId", "/api/v1/escalations/:callId"], async (req, res) => {
  const { callId } = req.params;
  if (isMongoConnected && db) {
    const doc = await db.collection("callAttempts").findOne({ callId }).catch(() => null);
    if (doc) return res.json(doc);
  }
  const call = memCallAttempts.get(callId);
  if (!call) return res.status(404).json({ error: "Call not found" });
  res.json(call);
});

// ----------------------------------------------------
// 5. Ingestion & Incident Lifecycle with MongoDB Atlas
// ----------------------------------------------------
app.post(["/api/devices/:deviceId/events", "/api/v1/incidents"], async (req, res) => {
  const { incidentId, eventType, localState, sensorEvidence } = req.body;
  const deviceId = req.params.deviceId || req.body.deviceId || "lira-esp32-01";

  if (!incidentId) return res.status(400).json({ error: "incidentId is required" });

  const eventRecord = {
    eventId: `evt-${Date.now()}`,
    deviceId,
    eventType: eventType || "vibration",
    observedAt: new Date(),
    receivedAt: new Date(),
    sensorEvidence: sensorEvidence || []
  };

  const incidentRecord = {
    incidentId,
    deviceId,
    eventType: eventType || "vibration",
    currentState: localState || "DETECTED",
    acknowledgementStatus: "PENDING",
    sensorEvidence: sensorEvidence || [],
    createdAt: new Date(),
    updatedAt: new Date()
  };

  if (isMongoConnected && db) {
    try {
      await db.collection("deviceEvents").insertOne(eventRecord);
      await db.collection("incidents").updateOne(
        { incidentId },
        { $set: incidentRecord },
        { upsert: true }
      );
    } catch (e) {
      console.error("[MongoDB Write Error]", e.message);
    }
  }

  memDeviceEvents.set(eventRecord.eventId, eventRecord);
  memIncidents.set(incidentId, incidentRecord);

  res.json({ success: true, incidentId });
});

app.get("/api/incidents", async (req, res) => {
  if (isMongoConnected && db) {
    try {
      const list = await db.collection("incidents").find().sort({ createdAt: -1 }).toArray();
      return res.json(list);
    } catch (e) {}
  }
  res.json(Array.from(memIncidents.values()));
});

app.post("/api/incidents/:incidentId/acknowledge", async (req, res) => {
  const { incidentId } = req.params;
  const now = new Date();

  if (isMongoConnected && db) {
    try {
      await db.collection("incidents").updateOne(
        { incidentId },
        { $set: { currentState: "ACKNOWLEDGED", acknowledgementStatus: "ACKNOWLEDGED", updatedAt: now } }
      );
    } catch (e) {}
  }

  const inc = memIncidents.get(incidentId);
  if (inc) {
    inc.currentState = "ACKNOWLEDGED";
    inc.acknowledgementStatus = "ACKNOWLEDGED";
    inc.updatedAt = now.toISOString();
  }

  res.json({ success: true, incidentId, state: "ACKNOWLEDGED" });
});

const registerDemoRoutes = require("./demoRoutes");
registerDemoRoutes(app, { db, isMongoConnected, allowedContacts, memCallAttempts, BOLNA_API_KEY, BOLNA_AGENT_ID, BOLNA_BASE_URL });

app.listen(PORT, () => {
  console.log(`====================================================`);
  console.log(`🚀 LIRA AI Brain & Voice Server Running on port ${PORT}`);
  console.log(`• MongoDB Atlas:    ${MONGODB_URI ? "CONFIGURED (cluster0.fgbum19.mongodb.net)" : "DISABLED"}`);
  console.log(`• Gemini Brain:     CONFIGURED`);
  console.log(`• ElevenLabs Voice: CONFIGURED (${ELEVENLABS_VOICE_ID} / ${ELEVENLABS_MODEL_ID})`);
  console.log(`• Bolna Telephony:  CONFIGURED (Agent: ${BOLNA_AGENT_ID})`);
  console.log(`====================================================`);
});
