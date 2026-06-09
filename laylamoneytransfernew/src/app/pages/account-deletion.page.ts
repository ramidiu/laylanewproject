import { Component } from '@angular/core';

@Component({
  selector: 'app-account-deletion',
  template: `
  <ion-content><div class="info-page">
    <div class="info-hero">
      <a class="info-back" href="/">&larr; Back to Home</a>
      <h1>Account Deletion</h1>
      <p>How to delete your Layla Money Transfer account</p>
    </div>
    <div class="info-body">
      <p>Users may request deletion of their Layla Money Transfer account through the mobile application under:</p>
      <p><strong>Settings &rarr; Delete Account</strong></p>
      <p>or by contacting support.</p>

      <h2>What Happens After Verification</h2>
      <p>After we verify your request:</p>
      <ul>
        <li>Account access will be removed.</li>
        <li>Profile information will be deleted.</li>
        <li>Saved recipients will be removed.</li>
        <li>Marketing preferences will be deleted.</li>
      </ul>

      <h2>Data Retained for Legal Reasons</h2>
      <p>
        Due to financial regulations, AML/KYC requirements, fraud prevention, and tax obligations,
        certain transaction history and identity verification records may be retained for the legally
        required retention period before permanent deletion.
      </p>

      <h2>Processing Time</h2>
      <p>Deletion requests are normally processed within 30 days.</p>

      <h2>Need Help?</h2>
      <p>For assistance contact <a href="mailto:support@laylamoneytransfer.co.uk">support@laylamoneytransfer.co.uk</a>.</p>
      <p>See also our <a href="/privacy-policy">Privacy Policy</a>.</p>
    </div>
  </div></ion-content>`
})
export class AccountDeletionPage {}
