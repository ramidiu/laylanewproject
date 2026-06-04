package com.remitz.modules.auth.service;

import com.remitz.modules.notification.service.BrevoEmailSender;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.Base64;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthEmailService {

    private static final String LOGO_RESOURCE_PATH = "static/remitz-logo-email.png";
    private static final String LOGO_CID = "remitz-logo";

    private final BrevoEmailSender brevoEmailSender;

    @Value("${app.frontend-url:https://layla.money}")
    private String frontendUrl;

    @Value("${brand.name:Layla Money Transfer}")
    private String brandName;

    @Value("${brand.tagline:Fast, Safe & Affordable International Money Transfers}")
    private String brandTagline;

    @Value("${brand.address:}")
    private String brandAddress;

    @Value("${brand.support-email:support@laylamoneytransfer.com}")
    private String brandSupportEmail;

    @Value("${brand.logo-url:}")
    private String brandLogoUrl;

    @Async
    public void sendPasswordResetEmail(String to, String resetToken, String fullName) {
        brevoEmailSender.send(to, brandName + " - Reset Your Password", buildPasswordResetHtml(resetToken, fullName));
    }

    @Async
    public void sendWelcomeEmail(String to, String fullName) {
        brevoEmailSender.send(to, "Welcome to " + brandName + "!", buildWelcomeEmailHtml(fullName));
    }

    @Async
    public void sendOtpEmail(String to, String otp, String fullName) {
        brevoEmailSender.send(to, brandName + " - Verify Your Email", buildOtpEmailHtml(otp, fullName));
    }

    @Async
    public void sendDemoAccessEmail(String to, String firstName, String username, String password,
                                      String roleLabel, String loginUrl, String dashboardNote,
                                      java.time.LocalDateTime expiresAt) {
        brevoEmailSender.send(to,
                brandName + " - Your demo access credentials",
                buildDemoAccessHtml(firstName, username, password, roleLabel, loginUrl, dashboardNote, expiresAt));
    }

    private String brandHeader() {
        // Reference the logo by hosted https URL — NOT a base64 data: URI. Gmail strips
        // data: image URIs (logo wouldn't render) and a ~250KB inline logo pushed the whole
        // email past Gmail's 102KB limit ("[Message clipped]"). A hosted URL fixes both.
        String logoUrl = !brandLogoUrl.isBlank()
                ? brandLogoUrl
                : (frontendUrl == null || frontendUrl.isBlank()
                        ? ""
                        : frontendUrl.replaceAll("/+$", "") + "/assets/images/email-logo.png");
        String logoBlock = logoUrl.isEmpty()
                ? "<span style=\"color:#ffffff;font-size:22px;font-weight:700;letter-spacing:.5px;\">" + brandName + "</span>"
                : "<img src=\"" + logoUrl + "\" alt=\"" + brandName + "\" style=\"max-width:180px;height:auto;margin:0 auto;display:block;\" />";
        return """
                <tr>
                    <td style="background-color:#1B3571;padding:28px 30px;text-align:center;border-radius:12px 12px 0 0;">
                        %s
                    </td>
                </tr>
                """.formatted(logoBlock);
    }

    private String brandFooter() {
        String addressBlock = brandAddress == null || brandAddress.isBlank()
                ? ""
                : "<p style=\"color:#718096;margin:0 0 6px;font-size:12px;text-align:center;\">"
                  + brandAddress.replace("\n", "<br>")
                  + "</p>";
        return """
                <tr>
                    <td style="background-color:#f7f9fc;padding:24px 30px;text-align:center;border-radius:0 0 12px 12px;border-top:1px solid #e2e8f0;">
                        <p style="color:#4a5568;margin:0 0 8px;font-size:13px;font-weight:600;">%s</p>
                        %s
                        <p style="color:#718096;margin:0 0 6px;font-size:12px;text-align:center;">
                            Need help? Contact us at
                            <a href="mailto:%s" style="color:#1B3571;text-decoration:none;">%s</a>
                        </p>
                        <p style="color:#a0aec0;margin:12px 0 0;font-size:11px;text-align:center;">
                            &copy; %d %s. All rights reserved. This is an automated message — please do not reply.
                        </p>
                    </td>
                </tr>
                """.formatted(
                        brandName,
                        addressBlock,
                        brandSupportEmail,
                        brandSupportEmail,
                        java.time.Year.now().getValue(),
                        brandName);
    }

    private String buildPasswordResetHtml(String resetToken, String fullName) {
        String resetLink = frontendUrl + "/reset-password?token=" + resetToken;
        String header = brandHeader();
        String footer = brandFooter();
        return """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                </head>
                <body style="margin:0;padding:0;font-family:'Segoe UI',Tahoma,Geneva,Verdana,sans-serif;background-color:#f4f6f9;">
                    <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="max-width:600px;margin:0 auto;padding:40px 20px;">
                        %s
                        <tr>
                            <td style="background-color:#ffffff;padding:40px 30px;box-shadow:0 2px 8px rgba(0,0,0,0.08);">
                                <h2 style="color:#1B3571;margin:0 0 16px;font-size:22px;">Reset Your Password</h2>
                                <p style="color:#4a5568;margin:0 0 24px;font-size:16px;line-height:1.5;">
                                    Hi %s,<br><br>
                                    We received a request to reset your password. Click the button below to set a new password:
                                </p>
                                <div style="text-align:center;margin:0 0 24px;">
                                    <a href="%s" style="display:inline-block;background-color:#1B3571;color:#ffffff;text-decoration:none;padding:14px 32px;border-radius:8px;font-size:16px;font-weight:600;">
                                        Reset Password
                                    </a>
                                </div>
                                <p style="color:#718096;margin:0 0 8px;font-size:14px;">This link will expire in <strong>1 hour</strong>.</p>
                                <p style="color:#718096;margin:0 0 16px;font-size:14px;">If you did not request a password reset, please ignore this email. Your password will remain unchanged.</p>
                                <p style="color:#a0aec0;margin:0 0 8px;font-size:12px;">If the button doesn't work, copy and paste this link into your browser:</p>
                                <p style="color:#4299e1;margin:0;font-size:12px;word-break:break-all;">%s</p>
                            </td>
                        </tr>
                        %s
                    </table>
                </body>
                </html>
                """.formatted(header, fullName, resetLink, resetLink, footer);
    }

    private String buildWelcomeEmailHtml(String fullName) {
        String loginLink = frontendUrl + "/login";
        String header = brandHeader();
        String footer = brandFooter();
        return """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                </head>
                <body style="margin:0;padding:0;font-family:'Segoe UI',Tahoma,Geneva,Verdana,sans-serif;background-color:#f4f6f9;">
                    <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="max-width:600px;margin:0 auto;padding:40px 20px;">
                        %s
                        <tr>
                            <td style="background-color:#ffffff;padding:40px 30px;box-shadow:0 2px 8px rgba(0,0,0,0.08);">
                                <h2 style="color:#1B3571;margin:0 0 16px;font-size:22px;">Welcome to %s!</h2>
                                <p style="color:#4a5568;margin:0 0 24px;font-size:16px;line-height:1.5;">
                                    Hi %s,<br><br>
                                    Thank you for joining %s! Your account has been verified and you're all set to start sending money globally.
                                </p>
                                <div style="background-color:#f0f4fa;border-radius:8px;padding:20px;margin:0 0 24px;">
                                    <h3 style="color:#1B3571;margin:0 0 12px;font-size:16px;">Get started in 3 steps:</h3>
                                    <p style="color:#4a5568;margin:0 0 8px;font-size:14px;">1. Complete your KYC verification</p>
                                    <p style="color:#4a5568;margin:0 0 8px;font-size:14px;">2. Add a recipient</p>
                                    <p style="color:#4a5568;margin:0;font-size:14px;">3. Send your first transfer</p>
                                </div>
                                <div style="text-align:center;margin:0 0 24px;">
                                    <a href="%s" style="display:inline-block;background-color:#1B3571;color:#ffffff;text-decoration:none;padding:14px 32px;border-radius:8px;font-size:16px;font-weight:600;">
                                        Sign In to Your Account
                                    </a>
                                </div>
                            </td>
                        </tr>
                        %s
                    </table>
                </body>
                </html>
                """.formatted(header, brandName, fullName, brandName, loginLink, footer);
    }

    private String buildOtpEmailHtml(String otp, String fullName) {
        String header = brandHeader();
        String footer = brandFooter();
        return """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                </head>
                <body style="margin:0;padding:0;font-family:'Segoe UI',Tahoma,Geneva,Verdana,sans-serif;background-color:#f4f6f9;">
                    <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="max-width:600px;margin:0 auto;padding:40px 20px;">
                        %s
                        <tr>
                            <td style="background-color:#ffffff;padding:40px 30px;box-shadow:0 2px 8px rgba(0,0,0,0.08);">
                                <h2 style="color:#1B3571;margin:0 0 16px;font-size:22px;">Verify Your Email</h2>
                                <p style="color:#4a5568;margin:0 0 24px;font-size:16px;line-height:1.5;">
                                    Hi %s,<br><br>
                                    Welcome to %s! Please use the following verification code to complete your registration:
                                </p>
                                <div style="background-color:#f0f4fa;border:2px dashed #1B3571;border-radius:8px;padding:20px;text-align:center;margin:0 0 24px;">
                                    <span style="font-size:36px;font-weight:700;color:#1B3571;letter-spacing:8px;">%s</span>
                                </div>
                                <p style="color:#718096;margin:0 0 8px;font-size:14px;">This code will expire in <strong>10 minutes</strong>.</p>
                                <p style="color:#718096;margin:0;font-size:14px;">If you did not create an account, please ignore this email.</p>
                            </td>
                        </tr>
                        %s
                    </table>
                </body>
                </html>
                """.formatted(header, fullName, brandName, otp, footer);
    }

    private String buildDemoAccessHtml(String firstName, String username, String password,
                                        String roleLabel, String loginUrl, String dashboardNote,
                                        java.time.LocalDateTime expiresAt) {
        String header = brandHeader();
        String footer = brandFooter();
        String expiresLabel = expiresAt != null ? expiresAt.toString().replace('T', ' ').substring(0, 16) + " UTC" : "24 hours from now";
        return """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                </head>
                <body style="margin:0;padding:0;font-family:'Segoe UI',Tahoma,Geneva,Verdana,sans-serif;background-color:#f4f6f9;">
                    <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="max-width:600px;margin:0 auto;padding:40px 20px;">
                        %s
                        <tr>
                            <td style="background-color:#ffffff;padding:40px 30px;box-shadow:0 2px 8px rgba(0,0,0,0.08);">
                                <h2 style="color:#1B3571;margin:0 0 12px;font-size:22px;">Welcome, %s</h2>
                                <p style="color:#4a5568;margin:0 0 18px;font-size:15px;line-height:1.55;">
                                    Your demo access to %s is ready. You're signed up as a
                                    <strong style="color:#1B3571;">%s</strong>. %s
                                </p>

                                <div style="background:#f0f4fa;border:1px dashed #1B3571;border-radius:10px;padding:20px 22px;margin:20px 0;">
                                    <div style="font-size:11px;letter-spacing:0.8px;text-transform:uppercase;color:#6b7280;margin-bottom:4px;">Username</div>
                                    <div style="font-size:15px;font-weight:600;color:#111827;font-family:ui-monospace,monospace;word-break:break-all;">%s</div>

                                    <div style="font-size:11px;letter-spacing:0.8px;text-transform:uppercase;color:#6b7280;margin:14px 0 4px;">Temporary password</div>
                                    <div style="font-size:18px;font-weight:700;color:#1B3571;font-family:ui-monospace,monospace;letter-spacing:1px;">%s</div>

                                    <div style="font-size:11px;letter-spacing:0.8px;text-transform:uppercase;color:#92400e;margin:14px 0 4px;">Expires</div>
                                    <div style="font-size:13px;font-weight:600;color:#92400e;">%s</div>
                                </div>

                                <div style="text-align:center;margin:26px 0 10px;">
                                    <a href="%s" style="display:inline-block;background-color:#1B3571;color:#ffffff;text-decoration:none;padding:14px 34px;border-radius:10px;font-size:15px;font-weight:600;">
                                        Sign in to %s
                                    </a>
                                </div>

                                <p style="color:#718096;margin:20px 0 0;font-size:12px;line-height:1.6;text-align:center;">
                                    For your security, these credentials expire in <strong>24 hours</strong>.
                                    Need another session? Visit the demo page again to generate new credentials.
                                </p>
                            </td>
                        </tr>
                        %s
                    </table>
                </body>
                </html>
                """.formatted(header, firstName, brandName, roleLabel, dashboardNote,
                        username, password, expiresLabel, loginUrl, brandName, footer);
    }
}
