require("dotenv").config();
const nodemailer = require("nodemailer");

async function run() {
  const transporter = nodemailer.createTransport({
    host: process.env.SMTP_HOST || "smtp.gmail.com",
    port: parseInt(process.env.SMTP_PORT || "465"),
    secure: true,
    auth: {
      user: process.env.SMTP_USER,
      pass: process.env.SMTP_APP_PASSWORD
    },
    tls: {
      rejectUnauthorized: false
    }
  });

  try {
    await transporter.verify();
    console.log("✅ SMTP connection verified!");
    const info = await transporter.sendMail({
      from: `"LIRA Security System" <${process.env.SMTP_USER}>`,
      to: process.env.LIRA_REPORT_RECIPIENT,
      subject: "🧪 LIRA Demo Mode — SMTP Integration Online",
      html: `
        <div style="font-family: Arial, sans-serif; padding: 20px; background-color: #0f172a; color: #f8fafc; border-radius: 10px;">
          <h2 style="color: #38bdf8; border-bottom: 2px solid #38bdf8; padding-bottom: 10px;">LIRA — Linked Intelligence & Response Agent</h2>
          <p>SMTP integration verified for hackathon demonstration.</p>
          <p>Email reports will be generated and dispatched upon session completion.</p>
          <hr style="border: 1px solid #334155; margin: 20px 0;">
          <small style="color: #94a3b8;">Timestamp: ${new Date().toLocaleString("en-IN", { timeZone: "Asia/Kolkata" })} IST</small>
        </div>
      `
    });
    console.log("✅ Live email successfully delivered to", process.env.LIRA_REPORT_RECIPIENT, "Message ID:", info.messageId);
  } catch (err) {
    console.error("❌ Email Error:", err.message);
  }
}
run();
