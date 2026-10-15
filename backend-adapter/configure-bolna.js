const fs = require('fs');
require('dotenv').config();

const BOLNA_API_KEY = process.env.BOLNA_API_KEY || "bn-d0cce911a6c146a99c0a3a0c5bb7232d";
const BOLNA_AGENT_ID = process.env.BOLNA_AGENT_ID || "7c439d93-81bf-447a-a2a7-cfb3fbc3a57f";
const BOLNA_BASE_URL = process.env.BOLNA_BASE_URL || "https://api.bolna.ai";

async function main() {
  console.log(`[Bolna Config] Fetching agent ${BOLNA_AGENT_ID}...`);
  const getRes = await fetch(`${BOLNA_BASE_URL}/v2/agent/${BOLNA_AGENT_ID}`, {
    headers: { 'Authorization': `Bearer ${BOLNA_API_KEY}` }
  });
  if (!getRes.ok) {
    console.error("Failed to fetch agent:", getRes.status, await getRes.text());
    return;
  }
  const current = await getRes.json();
  const tasks = current.tasks;

  // 1. Synthesizer: Sarvam en-IN (loud, clear, natural, no encoding bugs)
  tasks[0].tools_config.synthesizer = {
    stream: true,
    caching: false,
    provider: 'sarvam',
    buffer_size: 220,
    audio_format: 'wav',
    provider_config: {
      model: 'bulbul:v3',
      speed: 1,
      voice: 'kavya',
      language: 'en-IN',
      loudness: 1.3,
      voice_id: 'kavya'
    }
  };

  // 2. Transcriber: Saaras v4 multilingual
  tasks[0].tools_config.transcriber.language = 'en-IN';
  tasks[0].tools_config.transcriber.multilingual = null;

  // 3. Task timings: give 500ms for audio carrier to establish so the user hears the first word
  tasks[0].task_config.welcome_message_delay = 500;
  tasks[0].task_config.discard_pre_welcome_utterance = false;
  tasks[0].task_config.hangup_after_silence = 30;
  tasks[0].task_config.check_if_user_online = true;
  tasks[0].task_config.check_user_online_message = {
    en: "Hello, are you still on the line?",
    kn: "???????, ???? ????????????????"
  };

  const welcomeMessage = "Hello Likith! This is LIRA AI Security Assistant. A simulated unauthorized entry was detected at the main perimeter portal. This is a hackathon demonstration. Can you hear me clearly?";

  const systemPrompt = `You are LIRA (Linked Intelligence & Response Agent), an autonomous AI voice assistant and security officer for the CHAOS hackathon Jarvis challenge.

Current Demo Security Context:
- A simulated unauthorized entry event was detected at the perimeter portal by Guest 1.
- Event Time: Today during the hackathon simulation session (Asia/Kolkata IST).
- Current Room Status:
  * Total Entries: 3 (Person A, Person B, Guest 1)
  * Authorized: 2 (Person A, Person B)
  * Unauthorized: 1 (Guest 1)
  * People currently inside: 1 person (Person A is currently inside. Person B has exited. Guest 1 was denied entry).
- Contact: Likith (Lead Supervisor).

Response Instructions:
1. Speak loudly, clearly, and with confidence. Speak in Indian English or Kannada depending on how the user speaks to you.
2. If asked 'Who is in the room?' or 'How many people are inside?', reply directly: 'Currently there is one person inside, Person A. Person B has exited, and Guest 1 was unauthorized.'
3. If asked 'What happened?' or 'What is the alert?', explain: 'An unauthorized entry was detected at the perimeter by Guest 1. This is a hackathon simulation alert.'
4. If asked to confirm or end, say: 'Understood, the incident is logged in the timeline and the executive report will be emailed.'
5. Keep every answer under 2 sentences. Always be fast, accurate, and concise.`;

  const payload = {
    agent_config: {
      agent_name: 'LIRA AI Security Assistant',
      agent_welcome_message: welcomeMessage,
      tasks: tasks
    },
    agent_prompts: {
      task_1: {
        system_prompt: systemPrompt
      }
    }
  };

  console.log("[Bolna Config] Updating agent via PUT /v2/agent...");
  const putRes = await fetch(`${BOLNA_BASE_URL}/v2/agent/${BOLNA_AGENT_ID}`, {
    method: "PUT",
    headers: {
      "Authorization": `Bearer ${BOLNA_API_KEY}`,
      "Content-Type": "application/json"
    },
    body: JSON.stringify(payload)
  });

  console.log("PUT status:", putRes.status);
  const result = await putRes.json();
  console.log("Result:", JSON.stringify(result, null, 2));
}

main().catch(err => console.error(err));
