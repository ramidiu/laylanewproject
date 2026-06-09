import { Component } from '@angular/core';

@Component({
  selector: 'app-privacy-policy',
  template: `
  <ion-content><div class="info-page">
    <div class="info-hero">
      <a class="info-back" href="/">&larr; Back to Home</a>
      <h1>Privacy Policy</h1>
      <p>Effective Date: 24 June 2025</p>
    </div>
    <div class="info-body">
      <p>At Layla Money Transfer, we are committed to protecting your personal data and privacy. This policy explains how we collect, use, protect, and handle your personal information through our website and mobile application.</p>

      <h2>What Information Do We Collect?</h2>
      <p>When you visit or interact with our website or mobile app, we may collect the following information:</p>
      <ul>
        <li>Name</li><li>Email address</li><li>Phone number</li>
        <li>Billing and payment information (where applicable)</li>
        <li>Device information (such as device model, OS version, and browser type)</li>
        <li>Location data (if permitted)</li>
        <li>Log data including IP address, access times, and activity logs</li>
      </ul>

      <h2>When Do We Collect Information?</h2>
      <ul>
        <li>Register or create an account</li>
        <li>Place an order or initiate a transaction</li>
        <li>Contact customer support</li>
        <li>Use our mobile app or website features</li>
        <li>Consent to marketing communications</li>
        <li>Grant permission for location access (in the app)</li>
      </ul>

      <h2>How Do We Use Your Information?</h2>
      <ul>
        <li>Process transactions and provide money transfer services</li>
        <li>Verify your identity and comply with AML/KYC obligations</li>
        <li>Improve website and app performance</li>
        <li>Send transactional and occasional promotional emails</li>
        <li>Provide customer service and technical support</li>
        <li>Analyse user behaviour to improve our services</li>
      </ul>

      <h2>How Do We Protect Your Information?</h2>
      <ul>
        <li>Secure server and database encryption</li>
        <li>Access control for authorised personnel only</li>
        <li>Regular malware scanning and vulnerability monitoring</li>
        <li>Encrypted transmission via SSL/TLS protocols</li>
      </ul>
      <p>Despite our best efforts, no online transmission is 100% secure. You share data at your own risk.</p>

      <h2>Do We Use Cookies?</h2>
      <p>Yes. Cookies help us remember user preferences, monitor site usage to improve the experience, and enable functionality such as login and session handling. You can modify your browser settings to disable cookies, but some features may not function properly.</p>

      <h2>Third-Party Disclosure</h2>
      <p>We do not sell or trade your personal data. We may share data with hosting providers and analytics partners, KYC/AML verification partners, and payment processors. These third parties are contractually obligated to keep your data confidential.</p>
      <p>We may disclose your information if required by law or regulation, to enforce our policies, or to protect rights, safety, or property.</p>

      <h2>User Rights and Controls</h2>
      <p>Depending on your location, you may have rights to access your personal data, request correction or deletion, withdraw consent or object to data processing, and request data portability. To exercise these rights, contact us at <a href="mailto:info@laylamoneytransfer.co.uk">info@laylamoneytransfer.co.uk</a>.</p>

      <h2>Account Deletion</h2>
      <p>You can request deletion of your account at any time, either from within the mobile app under <strong>Settings &rarr; Delete Account</strong>, via our <a href="/account-deletion">Account Deletion</a> page, or by contacting support.</p>
      <p><strong>What is deleted:</strong> once your request is verified, your account access is removed and your profile information, saved recipients, and marketing preferences are deleted.</p>
      <p><strong>What is retained:</strong> certain transaction history and identity verification (KYC) records may be retained for the legally required retention period under Anti-Money Laundering (AML), KYC, fraud-prevention, tax, and financial regulations before being permanently deleted. This retention is a legal obligation and applies even after you request deletion.</p>
      <p><strong>Processing time:</strong> deletion requests are normally processed within 30 days. For assistance, contact <a href="mailto:support@laylamoneytransfer.co.uk">support@laylamoneytransfer.co.uk</a>.</p>

      <h2>Children's Privacy</h2>
      <p>Our services are not intended for children under the age of 13, and we do not knowingly collect data from them. If we learn that we have collected personal data from a child, we will delete it immediately.</p>

      <h2>Changes to This Policy</h2>
      <p>We may update this Privacy Policy from time to time. Changes will be posted on this page with an updated "Effective Date."</p>

      <h2>Contact Us</h2>
      <p>If you have any questions regarding this privacy policy, you may contact us at:<br>
      <strong>Layla Money Transfer</strong><br>
      Email: <a href="mailto:info@laylamoneytransfer.co.uk">info@laylamoneytransfer.co.uk</a><br>
      Website: <a href="https://laylamoneytransfer.co.uk">https://laylamoneytransfer.co.uk</a></p>
    </div>
  </div></ion-content>`
})
export class PrivacyPolicyPage {}
