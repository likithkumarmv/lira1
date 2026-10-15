const fs = require('fs');

let dr = fs.readFileSync('demoRoutes.js', 'utf8');
dr = dr.replaceAll('preferred_language: "kn"', 'preferred_language: "en"');
fs.writeFileSync('demoRoutes.js', dr);

let s = fs.readFileSync('server.js', 'utf8');
s = s.replaceAll('preferred_language: "kn"', 'preferred_language: "en"');
fs.writeFileSync('server.js', s);

console.log('Successfully updated preferred_language to en');
