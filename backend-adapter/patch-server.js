const fs = require('fs');
let code = fs.readFileSync('server.js', 'utf8');
if (!code.includes('demoRoutes')) {
  const target = 'app.listen(PORT';
  const addition = 'const registerDemoRoutes = require("./demoRoutes");\nregisterDemoRoutes(app, { db, isMongoConnected, allowedContacts, memCallAttempts, BOLNA_API_KEY, BOLNA_AGENT_ID, BOLNA_BASE_URL });\n\n' + target;
  code = code.replace(target, addition);
  fs.writeFileSync('server.js', code, 'utf8');
  console.log('✅ Registered demoRoutes in server.js');
} else {
  console.log('demoRoutes already registered');
}
