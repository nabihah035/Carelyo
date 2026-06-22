package com.example.carelyo.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Properties
import javax.mail.Authenticator
import javax.mail.Message
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

object EmailService {
    private const val SMTP_HOST = "smtp.gmail.com"
    private const val SMTP_PORT = "587"
    private const val SENDER_EMAIL = "carelyohealth@gmail.com"
    private const val SENDER_PASSWORD = "kcqc nzei fbia fsbk"

    private fun createSession(): Session {
        val props = Properties().apply {
            put("mail.smtp.auth", "true")
            put("mail.smtp.starttls.enable", "true")
            put("mail.smtp.host", SMTP_HOST)
            put("mail.smtp.port", SMTP_PORT)
            put("mail.smtp.ssl.trust", SMTP_HOST)
        }
        return Session.getInstance(props, object : Authenticator() {
            override fun getPasswordAuthentication() =
                PasswordAuthentication(SENDER_EMAIL, SENDER_PASSWORD)
        })
    }

    suspend fun sendHtmlEmail(to: String, subject: String, htmlBody: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val message = MimeMessage(createSession()).apply {
                    setFrom(InternetAddress(SENDER_EMAIL, "Carelyo"))
                    setRecipients(Message.RecipientType.TO, InternetAddress.parse(to))
                    setSubject(subject)
                    setContent(htmlBody, "text/html; charset=utf-8")
                }
                Transport.send(message)
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun sendEmail(to: String, subject: String, body: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val message = MimeMessage(createSession()).apply {
                    setFrom(InternetAddress(SENDER_EMAIL, "Carelyo"))
                    setRecipients(Message.RecipientType.TO, InternetAddress.parse(to))
                    setSubject(subject)
                    setText(body)
                }
                Transport.send(message)
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    fun buildPasswordResetOtpHtml(userEmail: String, otp: String): String = """
        <!DOCTYPE html>
        <html>
        <body style="margin:0;padding:0;background-color:#f5f5f5;font-family:Arial,sans-serif;">
          <div style="max-width:600px;margin:40px auto;background:#ffffff;border-radius:16px;overflow:hidden;box-shadow:0 2px 8px rgba(0,0,0,0.08);">
            <div style="background:#4A90D9;padding:32px;text-align:center;">
              <h1 style="color:#ffffff;margin:0;font-size:26px;letter-spacing:1px;">Carelyo</h1>
              <p style="color:rgba(255,255,255,0.80);margin:8px 0 0 0;font-size:13px;">Your Care Management Platform</p>
            </div>
            <div style="padding:40px 32px;">
              <h2 style="color:#1a1a1a;margin:0 0 20px 0;font-size:22px;">Password Reset Request</h2>
              <p style="color:#555;line-height:1.7;margin:0 0 8px 0;">Hi there,</p>
              <p style="color:#555;line-height:1.7;margin:0 0 28px 0;">
                We received a request to reset the password for your Carelyo account associated with 
                <strong>$userEmail</strong>. Use the OTP below to reset your password.
              </p>
              <div style="text-align:center;margin:36px 0;background:#f8f9fa;padding:24px;border-radius:12px;">
                <p style="color:#666;font-size:14px;margin:0 0 8px 0;">Your One-Time Password (OTP)</p>
                <div style="font-size:48px;font-weight:bold;color:#4A90D9;letter-spacing:12px;font-family:'Courier New',monospace;">
                  $otp
                </div>
              </div>
              <p style="color:#888;font-size:13px;line-height:1.6;margin:0 0 8px 0;">
                This OTP will expire in <strong>5 minutes</strong>. Please do not share this code with anyone.
              </p>
              <div style="background:#fff3cd;border-left:4px solid #ffc107;padding:14px 16px;border-radius:4px;margin:24px 0;">
                <p style="color:#856404;font-size:13px;margin:0;">
                  ⚠️ If you did not request this password reset, please ignore this email 
                  and ensure your account security.
                </p>
              </div>
              <hr style="border:none;border-top:1px solid #eee;margin:0 0 20px 0;">
              <p style="color:#aaa;font-size:12px;line-height:1.6;margin:0;">
                This is an automated message from Carelyo. Please do not reply to this email.
              </p>
            </div>
            <div style="background:#f9f9f9;padding:16px 32px;text-align:center;border-top:1px solid #eee;">
              <p style="color:#bbb;font-size:11px;margin:0;">© 2025 Carelyo · All rights reserved</p>
            </div>
          </div>
        </body>
        </html>
    """.trimIndent()
}