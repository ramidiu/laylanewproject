import { Component } from '@angular/core';

@Component({
  selector: 'app-user-agreement',
  template: `
  <div class="info-page">
    <div class="info-hero">
      <a class="info-back" routerLink="/">&larr; Back to Home</a>
      <h1>User Agreement</h1>
      <p>Effective Date: 24 June 2025</p>
    </div>
    <div class="info-body">
      <p>This User Agreement covers your Layla Money Transfer account and your relationship with Layla (London) Limited (Companies House 9672013, FCA 740120). It should be read together with our <a routerLink="/terms">Terms of Use</a> and <a routerLink="/privacy-policy">Privacy Policy</a>.</p>

      <h2>1. Your Account</h2>
      <p>You are responsible for keeping your login credentials confidential and for all activity under your account. Notify us immediately of any unauthorised access. You must provide accurate information and keep it up to date.</p>

      <h2>2. Verification</h2>
      <p>To open and use an account you must complete identity verification (KYC). We may request additional documents at any time to meet our legal obligations.</p>

      <h2>3. Acceptable Use</h2>
      <p>You agree to use your account only for lawful purposes and not for money laundering, fraud, terrorist financing, or sanctions evasion. We may suspend or close accounts that breach this agreement or applicable law.</p>

      <h2>4. Authorisations</h2>
      <p>By confirming a transfer you authorise us to debit the stated amount and process it to your chosen beneficiary. You are responsible for the accuracy of beneficiary details.</p>

      <h2>5. Account Suspension &amp; Closure</h2>
      <p>We may suspend, restrict, or close your account where required by law, for security reasons, or for breach of these terms. You may close your account at any time once outstanding transactions are complete.</p>

      <h2>6. Communications</h2>
      <p>You agree to receive transactional and service communications by email and in-app notifications. Marketing communications are optional and can be turned off.</p>

      <h2>7. Changes</h2>
      <p>We may update this agreement from time to time. Continued use of your account after changes are posted means you accept them.</p>

      <h2>8. Contact</h2>
      <p>Email <a href="mailto:info@laylamoneytransfer.co.uk">info@laylamoneytransfer.co.uk</a> or visit our <a routerLink="/contact-us">Contact page</a>.</p>
    </div>
  </div>`
})
export class UserAgreementPage {}
