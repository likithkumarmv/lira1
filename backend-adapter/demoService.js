const nodemailer = require("nodemailer");

function createDemoService({ db, isMongoConnected, allowedContacts, triggerBolnaCall }) {
  let currentSession = null;
  const sessionsHistory = new Map();

  function getIstTimestamp() {
    return new Date().toLocaleString("en-IN", {
      timeZone: "Asia/Kolkata",
      dateStyle: "medium",
      timeStyle: "medium"
    }) + " IST";
  }

  // Pre-configured simulated showcase events
  const showcaseSequence = [
    {
      personLabel: "Person A (Lead Supervisor Likith)",
      authResult: "Authorized",
      type: "ENTRY",
      explanation: "Approved staff RFID fob scanned at gate node. Access granted.",
      responseStatus: "RESOLVED_SAFE"
    },
    {
      personLabel: "Person B (Certified Duty Engineer)",
      authResult: "Authorized",
      type: "ENTRY",
      explanation: "Duty engineer keycard validated against access whitelist.",
      responseStatus: "RESOLVED_SAFE"
    },
    {
      personLabel: "Guest 1 (Unidentified Individual)",
      authResult: "Unauthorized",
      type: "ENTRY",
      explanation: "Unrecognized NFC/credential. Motion detected on perimeter boundary.",
      responseStatus: "ESCALATION_TRIGGERED"
    },
    {
      personLabel: "Person B (Certified Duty Engineer)",
      authResult: "Authorized",
      type: "EXIT",
      explanation: "Duty engineer badge tapped at exit reader. Checkout confirmed.",
      responseStatus: "RESOLVED_SAFE"
    }
  ];

  function startSession() {
    const sessionId = `demo-sess-${Date.now().toString().slice(-6)}`;
    const now = new Date();
    currentSession = {
      sessionId,
      status: "ACTIVE",
      startTime: now.toISOString(),
      startTimeIst: getIstTimestamp(),
      endTime: null,
      endTimeIst: null,
      durationSeconds: 0,
      totalEntries: 0,
      authorizedCount: 0,
      unauthorizedCount: 0,
      peopleInsideCount: 0,
      peopleInside: [],
      events: [],
      activeIncident: null,
      calls: [],
      reportSent: false,
      reportSentAt: null,
      reportRecipient: process.env.LIRA_REPORT_RECIPIENT || "likithkumarmv6364@gmail.com",
      reportMessageId: null
    };

    sessionsHistory.set(sessionId, currentSession);

    if (isMongoConnected && db) {
      db.collection("demoSessions").insertOne({ ...currentSession }).catch(() => {});
    }

    return currentSession;
  }

  function addEvent({ personLabel, authResult, type, explanation, responseStatus }) {
    if (!currentSession || currentSession.status === "ENDED") {
      startSession();
    }

    const eventId = `demo-evt-${Date.now().toString().slice(-6)}`;
    const event = {
      eventId,
      sessionId: currentSession.sessionId,
      personLabel: personLabel || "Simulated Visitor",
      authResult: authResult || "Authorized",
      type: type || "ENTRY",
      timestamp: new Date().toISOString(),
      timestampIst: getIstTimestamp(),
      explanation: explanation || "Simulated perimeter entry event for testing.",
      responseStatus: responseStatus || (authResult === "Unauthorized" ? "ESCALATION_TRIGGERED" : "RESOLVED_SAFE")
    };

    currentSession.events.unshift(event);

    if (event.type === "ENTRY") {
      currentSession.totalEntries++;
      if (event.authResult === "Authorized") {
        currentSession.authorizedCount++;
        if (!currentSession.peopleInside.includes(event.personLabel)) {
          currentSession.peopleInside.push(event.personLabel);
        }
      } else {
        currentSession.unauthorizedCount++;
      }
    } else if (event.type === "EXIT") {
      const idx = currentSession.peopleInside.indexOf(event.personLabel);
      if (idx >= 0) {
        currentSession.peopleInside.splice(idx, 1);
      }
    }
    currentSession.peopleInsideCount = currentSession.peopleInside.length;

    // If Unauthorized, initiate incident & Bolna voice call
    if (event.authResult === "Unauthorized") {
      const incId = `inc-demo-${Date.now().toString().slice(-4)}`;
      currentSession.activeIncident = {
        incidentId: incId,
        eventId: event.eventId,
        personLabel: event.personLabel,
        startedAt: event.timestampIst,
        state: "ESCALATION_PENDING",
        reason: "Simulated unauthorized entry detected. Initiating hackathon test call."
      };

      if (triggerBolnaCall) {
        triggerBolnaCall({
          incidentId: incId,
          goal: `LIRA Hackathon Demo: Simulated unauthorized perimeter entry by ${event.personLabel} at ${event.timestampIst}.`,
          isSimulation: true
        }).then(callRecord => {
          if (callRecord) {
            currentSession.calls.unshift(callRecord);
          }
        }).catch(() => {});
      }
    }

    if (isMongoConnected && db) {
      db.collection("demoEvents").insertOne(event).catch(() => {});
      db.collection("demoSessions").updateOne(
        { sessionId: currentSession.sessionId },
        { $set: currentSession }
      ).catch(() => {});
    }

    return event;
  }

  function pauseSession() {
    if (currentSession && currentSession.status === "ACTIVE") {
      currentSession.status = "PAUSED";
    }
    return currentSession;
  }

  function resumeSession() {
    if (currentSession && currentSession.status === "PAUSED") {
      currentSession.status = "ACTIVE";
    }
    return currentSession;
  }

  function resetSession() {
    return startSession();
  }

  async function endSessionAndSendReport() {
    if (!currentSession) {
      return { error: "No active session to end." };
    }

    const now = new Date();
    currentSession.status = "ENDED";
    currentSession.endTime = now.toISOString();
    currentSession.endTimeIst = getIstTimestamp();
    const startMs = new Date(currentSession.startTime).getTime();
    currentSession.durationSeconds = Math.max(1, Math.round((now.getTime() - startMs) / 1000));

    // Send email report
    const emailResult = await sendEmailReport(currentSession);
    currentSession.reportSent = emailResult.success;
    currentSession.reportSentAt = emailResult.success ? getIstTimestamp() : null;
    currentSession.reportMessageId = emailResult.messageId || null;
    currentSession.reportError = emailResult.error || null;

    if (isMongoConnected && db) {
      db.collection("demoSessions").updateOne(
        { sessionId: currentSession.sessionId },
        { $set: currentSession }
      ).catch(() => {});
    }

    return {
      session: currentSession,
      emailResult
    };
  }

  async function sendEmailReport(session) {
    const recipient = process.env.LIRA_REPORT_RECIPIENT || "likithkumarmv6364@gmail.com";
    const smtpUser = process.env.SMTP_USER;
    const smtpPass = process.env.SMTP_APP_PASSWORD;

    if (!smtpUser || !smtpPass) {
      return { success: false, error: "SMTP credentials not configured" };
    }

    const transporter = nodemailer.createTransport({
      host: process.env.SMTP_HOST || "smtp.gmail.com",
      port: parseInt(process.env.SMTP_PORT || "465"),
      secure: true,
      auth: {
        user: smtpUser,
        pass: smtpPass
      },
      tls: {
        rejectUnauthorized: false
      }
    });

    const eventRows = session.events.map(ev => `
      <tr style="border-bottom: 1px solid #334155;">
        <td style="padding: 10px; font-family: monospace; color: #94a3b8;">${ev.eventId}</td>
        <td style="padding: 10px; font-weight: bold; color: #f8fafc;">${ev.personLabel}</td>
        <td style="padding: 10px; color: ${ev.type === "ENTRY" ? "#38bdf8" : "#a855f7"};">${ev.type}</td>
        <td style="padding: 10px;">
          <span style="background: ${ev.authResult === "Authorized" ? "#065f46" : "#991b1b"}; color: ${ev.authResult === "Authorized" ? "#34d399" : "#f87171"}; padding: 3px 8px; border-radius: 4px; font-size: 11px; font-weight: bold;">
            ${ev.authResult.toUpperCase()}
          </span>
        </td>
        <td style="padding: 10px; color: #cbd5e1; font-size: 12px;">${ev.timestampIst}</td>
        <td style="padding: 10px; color: #94a3b8; font-size: 12px;">${ev.explanation}</td>
      </tr>
    `).join("");

    const callRows = session.calls.length > 0 ? session.calls.map(c => `
      <div style="background: #1e293b; padding: 12px; border-radius: 8px; margin-top: 8px; border-left: 4px solid #38bdf8;">
        <div><b>Provider:</b> ${c.provider || "Bolna Voice AI"} &nbsp;|&nbsp; <b>Recipient:</b> ${c.contactName || "Likith"} (${c.contactPhoneMasked || "+91 63645 40011"})</div>
        <div><b>Status:</b> <span style="color: #34d399; font-weight: bold;">${c.status.toUpperCase()}</span> &nbsp;|&nbsp; <b>Duration:</b> ${c.durationSeconds || 22}s</div>
        ${c.transcript ? `<div style="margin-top: 8px; font-size: 12px; color: #cbd5e1; background: #0f172a; padding: 8px; border-radius: 6px;"><b>Transcript:</b><br><pre style="white-space: pre-wrap; font-family: inherit; margin: 4px 0;">${c.transcript}</pre></div>` : ""}
      </div>
    `).join("") : `<div style="color: #64748b; font-style: italic;">No phone escalations required during this session.</div>`;

    const htmlContent = `
      <!DOCTYPE html>
      <html>
      <head>
        <meta charset="utf-8">
        <style>
          body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif; background-color: #0b1120; color: #f8fafc; margin: 0; padding: 24px; }
          .container { max-width: 800px; margin: 0 auto; background-color: #0f172a; border-radius: 12px; border: 1px solid #1e293b; overflow: hidden; }
          .header { background: linear-gradient(135deg, #0284c7, #0f172a); padding: 24px; border-bottom: 1px solid #334155; }
          .badge-sim { display: inline-block; background-color: #f59e0b; color: #000; font-weight: 800; font-size: 11px; padding: 4px 10px; border-radius: 20px; letter-spacing: 1px; margin-bottom: 8px; }
          .grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; padding: 20px; background-color: #1e293b; }
          .stat-card { background-color: #0f172a; padding: 12px; border-radius: 8px; text-align: center; border: 1px solid #334155; }
          .stat-val { font-size: 24px; font-weight: 900; color: #38bdf8; }
          .stat-lbl { font-size: 11px; text-transform: uppercase; color: #94a3b8; letter-spacing: 0.5px; }
          .content { padding: 24px; }
          table { width: 100%; border-collapse: collapse; margin-top: 12px; font-size: 13px; }
          th { text-align: left; padding: 10px; background: #1e293b; color: #94a3b8; font-size: 11px; text-transform: uppercase; }
        </style>
      </head>
      <body>
        <div class="container">
          <div class="header">
            <span class="badge-sim">SIMULATION DEMONSTRATION MODE</span>
            <h1 style="margin: 0; font-size: 24px; color: #ffffff;">LIRA — Linked Intelligence & Response Agent</h1>
            <p style="margin: 4px 0 0 0; color: #bae6fd; font-size: 13px;">Executive Security Operations Summary • Hackathon Showcase Session</p>
          </div>

          <div class="grid">
            <div class="stat-card">
              <div class="stat-val">${session.totalEntries}</div>
              <div class="stat-lbl">Total Entries</div>
            </div>
            <div class="stat-card">
              <div class="stat-val" style="color: #34d399;">${session.authorizedCount}</div>
              <div class="stat-lbl">Authorized</div>
            </div>
            <div class="stat-card">
              <div class="stat-val" style="color: #f87171;">${session.unauthorizedCount}</div>
              <div class="stat-lbl">Unauthorized</div>
            </div>
            <div class="stat-card">
              <div class="stat-val" style="color: #fbbf24;">${session.peopleInsideCount}</div>
              <div class="stat-lbl">Inside Facility</div>
            </div>
          </div>

          <div class="content">
            <h3 style="color: #38bdf8; margin-top: 0; border-bottom: 1px solid #334155; padding-bottom: 8px;">Session Metadata</h3>
            <p style="font-size: 13px; line-height: 1.6; color: #cbd5e1;">
              <b>Session ID:</b> <code>${session.sessionId}</code><br>
              <b>Started:</b> ${session.startTimeIst}<br>
              <b>Ended:</b> ${session.endTimeIst || "In Progress"}<br>
              <b>Total Active Duration:</b> ${session.durationSeconds} seconds<br>
              <b>Currently Inside:</b> ${session.peopleInside.length > 0 ? session.peopleInside.join(", ") : "None (Facility Vacant)"}
            </p>

            <h3 style="color: #38bdf8; margin-top: 24px; border-bottom: 1px solid #334155; padding-bottom: 8px;">Simulated Perimeter Access Ledger</h3>
            <table>
              <thead>
                <tr>
                  <th>Event ID</th>
                  <th>Person</th>
                  <th>Type</th>
                  <th>Result</th>
                  <th>Timestamp (IST)</th>
                  <th>Explanation</th>
                </tr>
              </thead>
              <tbody>
                ${eventRows}
              </tbody>
            </table>

            <h3 style="color: #38bdf8; margin-top: 24px; border-bottom: 1px solid #334155; padding-bottom: 8px;">Voice AI Telephony & Escalation Log</h3>
            ${callRows}

            <div style="margin-top: 28px; padding: 14px; background: #1e293b; border-left: 4px solid #f59e0b; border-radius: 6px; font-size: 12px; color: #cbd5e1;">
              <b>Simulation Disclosure:</b> This report was generated during a demonstration session of LIRA for the CHAOS hackathon. No real perimeter breach occurred.
            </div>
          </div>
        </div>
      </body>
      </html>
    `;

    try {
      const info = await transporter.sendMail({
        from: `"LIRA Security Agent" <${smtpUser}>`,
        to: recipient,
        subject: `🚨 [LIRA REPORT] Security Operations Session ${session.sessionId} (${session.unauthorizedCount > 0 ? "ALERT TRIGGERED" : "NORMAL"})`,
        html: htmlContent
      });
      return { success: true, messageId: info.messageId };
    } catch (err) {
      console.error("[Email Error]", err.message);
      return { success: false, error: err.message };
    }
  }

  function exportData(format) {
    if (!currentSession) return "";

    if (format === "json") {
      return JSON.stringify(currentSession, null, 2);
    }

    if (format === "csv") {
      const headers = "Event ID,Session ID,Person Label,Type,Authorization,Timestamp (IST),Response Status,Explanation\n";
      const rows = currentSession.events.map(ev => 
        `"${ev.eventId}","${ev.sessionId}","${ev.personLabel}","${ev.type}","${ev.authResult}","${ev.timestampIst}","${ev.responseStatus}","${ev.explanation}"`
      ).join("\n");
      return headers + rows;
    }

    return "";
  }

  return {
    getCurrentSession: () => currentSession || startSession(),
    startSession,
    addEvent,
    pauseSession,
    resumeSession,
    resetSession,
    endSessionAndSendReport,
    sendEmailReport,
    exportData,
    showcaseSequence
  };
}

module.exports = { createDemoService };
