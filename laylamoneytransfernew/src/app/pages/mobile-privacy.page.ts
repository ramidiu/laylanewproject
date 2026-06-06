import { Component } from '@angular/core';

@Component({
  selector: 'app-mobile-privacy',
  template: `
  <ion-content><div class="info-page">
    <div class="info-hero">
      <a class="info-back" href="/">&larr; Back to Home</a>
      <h1>Mobile Privacy Policy</h1>
      <p>How the Layla mobile app handles your data</p>
    </div>
    <div class="info-body">
      <p>This Mobile Privacy Policy explains how the Layla Money Transfer mobile application collects and uses your information. It supplements our main <a href="/privacy-policy">Privacy Policy</a>.</p>

      <h2>Information the App Collects</h2>
      <ul>
        <li>Account and profile details you provide (name, email, phone)</li>
        <li>KYC documents you upload (ID, proof of address) for identity verification</li>
        <li>Transaction and beneficiary details you enter</li>
        <li>Device information (model, OS version) and app diagnostics</li>
        <li>Location data, only if you grant permission</li>
      </ul>

      <h2>Permissions We Request</h2>
      <ul>
        <li><strong>Camera</strong> — to photograph KYC documents</li>
        <li><strong>Storage / Files</strong> — to save receipts to your device's Downloads</li>
        <li><strong>Notifications</strong> — to send transaction and security updates</li>
        <li><strong>Location</strong> — optional, used only where you allow it</li>
      </ul>
      <p>You can change these permissions at any time in your device settings.</p>

      <h2>How We Use This Information</h2>
      <p>To provide money transfer services, verify your identity (KYC/AML), process transactions, secure your account, and improve the app. We do not sell your personal data.</p>

      <h2>Data Security</h2>
      <p>Data is transmitted over encrypted (SSL/TLS) connections and stored securely. Access is restricted to authorised personnel.</p>

      <h2>Your Rights</h2>
      <p>You may request access, correction, or deletion of your data by emailing <a href="mailto:info@laylamoneytransfer.co.uk">info@laylamoneytransfer.co.uk</a>.</p>

      <h2>Changes</h2>
      <p>We may update this policy and will post changes here. Continued use of the app means you accept the updated policy.</p>
    </div>
  </div></ion-content>`
})
export class MobilePrivacyPage {}
