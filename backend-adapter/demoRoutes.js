const { createDemoService } = require("./demoService");

module.exports = function registerDemoRoutes(app, { db, isMongoConnected, allowedContacts, memCallAttempts, BOLNA_API_KEY, BOLNA_AGENT_ID, BOLNA_BASE_URL }) {
  let showcaseIndex = 0;

  const demoService = createDemoService({
    db,
    isMongoConnected,
    allowedContacts,
    triggerBolnaCall: async ({ incidentId, goal, isSimulation }) => {
      const contact = allowedContacts[0] || { name: "Likith (Lead Supervisor)", phone: "+916364540011", phoneMasked: "+91 63645 40011" };
      const callId = `call-${Date.now().toString().slice(-6)}`;
      let providerExecutionId = `bolna-exec-${Date.now().toString().slice(-6)}`;
      let callStatus = "dialing";
      let providerNotes = "";

      if (BOLNA_API_KEY && BOLNA_AGENT_ID) {
        try {
          console.log(`[Demo Mode Bolna] Calling ${contact.name} (${contact.phone})...`);
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
                alert_reason: goal || "Simulated unauthorized entry in LIRA Demo Mode",
                preferred_language: "en"
              }
            })
          });

          const bolnaData = await bolnaRes.json().catch(() => ({}));
          if (bolnaRes.ok) {
            providerExecutionId = bolnaData.execution_id || bolnaData.call_id || bolnaData.id || providerExecutionId;
            callStatus = "dialing";
            console.log(`[Demo Mode Bolna Call Placed] Execution ID: ${providerExecutionId}`);
          } else {
            providerNotes = bolnaData.message || bolnaData.error || "Bolna dispatch queued";
          }
        } catch (err) {
          console.error("[Demo Mode Bolna Error]", err.message);
          providerNotes = err.message;
        }
      }

      const callRecord = {
        callId,
        jobId: callId,
        incidentId,
        contactId: contact.contactId || "approved-contact-1",
        contactName: contact.name,
        contactPhoneMasked: contact.phoneMasked || contact.phone,
        goal: goal || "Simulated Perimeter Security Escalation",
        provider: "Bolna AI (Kannada + English Voice)",
        providerExecutionId,
        status: callStatus,
        attempt: 1,
        createdAt: new Date().toISOString(),
        startedAt: new Date().toISOString(),
        durationSeconds: 22,
        transcriptSummary: `[DEMO MODE] Bolna agent called ${contact.name}. Informed about simulated unauthorized entry. Alert acknowledged.`,
        transcript: `[LIRA Bolna AI]: Namaskara ${contact.name}! This is LIRA AI Agent conducting a hackathon demonstration. A simulated unauthorized entry was detected at the perimeter. Do you acknowledge this simulation?\n[${contact.name}]: Yes, I acknowledge. Security team is on site.\n[LIRA Bolna AI]: Dhanyavadagalu! Simulated alert acknowledged and logged.`,
        error: providerNotes || null
      };

      if (memCallAttempts) memCallAttempts.set(callId, callRecord);
      if (isMongoConnected && db) {
        db.collection("callAttempts").insertOne(callRecord).catch(() => {});
      }

      setTimeout(() => {
        callRecord.status = "in_progress";
      }, 3000);

      setTimeout(() => {
        callRecord.status = "completed";
      }, 10000);

      return callRecord;
    }
  });

  app.post("/api/demo/start", (req, res) => {
    showcaseIndex = 0;
    const session = demoService.startSession();
    res.json({ success: true, session });
  });

  app.get("/api/demo/session", (req, res) => {
    const session = demoService.getCurrentSession();
    res.json({ success: true, session });
  });

  app.post("/api/demo/event", (req, res) => {
    const event = demoService.addEvent(req.body || {});
    res.json({ success: true, event, session: demoService.getCurrentSession() });
  });

  app.post("/api/demo/showcase", (req, res) => {
    const seq = demoService.showcaseSequence;
    const eventData = seq[showcaseIndex % seq.length];
    showcaseIndex++;
    const event = demoService.addEvent(eventData);
    res.json({ success: true, event, step: showcaseIndex, session: demoService.getCurrentSession() });
  });

  app.post("/api/demo/pause", (req, res) => {
    res.json({ success: true, session: demoService.pauseSession() });
  });

  app.post("/api/demo/resume", (req, res) => {
    res.json({ success: true, session: demoService.resumeSession() });
  });

  app.post("/api/demo/reset", (req, res) => {
    showcaseIndex = 0;
    res.json({ success: true, session: demoService.resetSession() });
  });

  app.post("/api/demo/end", async (req, res) => {
    const result = await demoService.endSessionAndSendReport();
    res.json(result);
  });

  app.post("/api/demo/report/resend", async (req, res) => {
    const session = demoService.getCurrentSession();
    const emailResult = await demoService.sendEmailReport(session);
    res.json({ success: emailResult.success, emailResult, session });
  });

  app.get("/api/demo/export/:format", (req, res) => {
    const { format } = req.params;
    const data = demoService.exportData(format.toLowerCase());
    if (format.toLowerCase() === "csv") {
      res.setHeader("Content-Type", "text/csv");
      res.setHeader("Content-Disposition", "attachment; filename=lira_demo_report.csv");
      return res.send(data);
    }
    res.setHeader("Content-Type", "application/json");
    res.send(data);
  });

  console.log("✅ Demo Mode Routes Registered: /api/demo/*");
};
