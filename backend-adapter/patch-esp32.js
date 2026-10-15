const fs = require('fs');
const filePath = 'C:/Users/yashas.k/AndroidStudioProjects/CHAOS/firmware/esp32-lira/esp32-lira.ino';
let code = fs.readFileSync(filePath, 'utf8');

if (!code.includes('tftDemoMessage')) {
  code = code.replace(
    'String currentIncidentType = "";',
    'String currentIncidentType = "";\nString tftDemoMessage = "LIRA READY";'
  );

  const endpointTarget = 'server.on("/api/v1/events", HTTP_GET,';
  const displayEndpoint = `
  server.on("/api/v1/display", HTTP_POST, []() {
    if (server.hasArg("plain")) {
      String body = server.arg("plain");
      int idx = body.indexOf("\"message\":\"");
      if (idx >= 0) {
        int endIdx = body.indexOf("\"", idx + 11);
        if (endIdx > idx) {
          tftDemoMessage = body.substring(idx + 11, endIdx);
        }
      }
      updateDisplay();
      server.send(200, "application/json", "{\"success\":true,\"display\":\"" + tftDemoMessage + "\"}");
      return;
    }
    server.send(400, "application/json", "{\"error\":\"missing body\"}");
  });
`;
  code = code.replace(endpointTarget, displayEndpoint + '\n  ' + endpointTarget);

  const displayTarget = 'digitalWrite(TFT_CS, HIGH);\n}';
  const displayAddition = `
  // Demo Mode Status Banner
  if (tftDemoMessage.length() > 0) {
    tft.setCursor(10, 165);
    tft.setTextSize(2);
    tft.setTextColor(ILI9341_YELLOW);
    tft.print("[SIM] ");
    tft.setTextColor(ILI9341_CYAN);
    tft.print(tftDemoMessage);
  }

  digitalWrite(TFT_CS, HIGH);
}`;
  code = code.replace(displayTarget, displayAddition);

  fs.writeFileSync(filePath, code, 'utf8');
  console.log('✅ Updated esp32-lira.ino with /api/v1/display endpoint and TFT sync');
} else {
  console.log('TFT display endpoint already present in esp32-lira.ino');
}
