# LIRA Backend AI Calling Adapter

Cloud mediation adapter between the LIRA Android app (over cellular HTTPS) and the existing AI voice-calling service.

## Responsibilities
1. Whitelist validation: Restricts outbound calling strictly to approved contacts (ALLOWED_CONTACTS_JSON).
2. Idempotency management: Enforces unique idempotency keys to eliminate duplicate call dispatches from retries.
3. Call state tracking: Normalizes telephony states (queued, dialing, in_progress, completed, 
o_answer, ailed).
4. Gemini Tool Router: Validates allowed tools without raw shell or unrestricted outbound telephony.

## Run Locally
`ash
npm install
npm start
`
Default port: 3000
