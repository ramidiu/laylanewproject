import { Component } from '@angular/core';

@Component({
  selector: 'app-mobile-terms',
  template: `
  <div class="info-page">
    <div class="info-hero">
      <a class="info-back" href="/">&larr; Back to Home</a>
      <h1>Mobile Terms &amp; Conditions</h1>
      <p>Terms governing use of the Layla Money Transfer mobile app</p>
    </div>
    <div class="info-body">
      <p>These Mobile Terms &amp; Conditions apply to your use of the Layla Money Transfer mobile application, in addition to our general <a href="/terms">Terms of Use</a>.</p>

      <h2>1. Licence to Use the App</h2>
      <p>We grant you a personal, non-transferable, revocable licence to install and use the app on a device you own or control, solely for accessing our money transfer services.</p>

      <h2>2. Device &amp; Security</h2>
      <p>You are responsible for securing your device and your login credentials, including any device passcode or biometric lock. Do not use the app on a jailbroken/rooted device or over untrusted networks.</p>

      <h2>3. App Permissions</h2>
      <p>The app may request permissions such as camera (to capture KYC documents), storage (to save receipts to your Downloads), and notifications. You can manage these in your device settings; some features may not work if permissions are denied.</p>

      <h2>4. Updates</h2>
      <p>We may release updates to improve security and functionality. You may need to install updates to continue using the app.</p>

      <h2>5. Availability</h2>
      <p>We aim to keep the app available but do not guarantee uninterrupted access. Maintenance, network issues, or app-store policies may affect availability.</p>

      <h2>6. Acceptable Use</h2>
      <p>You must not misuse the app, attempt to reverse engineer it, or use it for unlawful activity. We may suspend access for breach of these terms.</p>

      <h2>7. Contact</h2>
      <p>Email <a href="mailto:info@laylamoneytransfer.co.uk">info@laylamoneytransfer.co.uk</a>. See also our <a href="/mobile-privacy">Mobile Privacy Policy</a>.</p>
    </div>
  </div>`
})
export class MobileTermsPage {}
