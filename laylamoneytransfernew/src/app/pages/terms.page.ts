import { Component } from '@angular/core';

@Component({
  selector: 'app-terms',
  template: `
  <div class="info-page">
    <div class="info-hero">
      <a class="info-back" routerLink="/">&larr; Back to Home</a>
      <h1>Terms of Use</h1>
      <p>Effective Date: 24 June 2025</p>
    </div>
    <div class="info-body">
      <p>These Terms of Use govern your access to and use of the Layla Money Transfer website and mobile application (the "Service"), operated by Layla (London) Limited (Companies House registered number 9672013), authorised and regulated by the Financial Conduct Authority (reference 740120). By using the Service you agree to these terms.</p>

      <h2>1. Eligibility</h2>
      <p>You must be at least 18 years old and legally able to enter into a binding contract to use the Service. You agree to provide accurate, current and complete information when registering and using the Service.</p>

      <h2>2. Identity Verification (KYC/AML)</h2>
      <p>As a regulated money services business, we are required to verify your identity and may request documents and information before processing transactions. We comply with UK Anti-Money Laundering Regulations and HM Revenue &amp; Customs requirements. We may decline, delay, or refund a transaction where verification cannot be completed or where we suspect fraud or unlawful activity.</p>

      <h2>3. Transfers and Exchange Rates</h2>
      <p>Exchange rates and fees are displayed before you confirm a transfer and apply to that transaction. Once a transfer is confirmed and funds are received, it is processed to the beneficiary you specify. You are responsible for ensuring beneficiary details are correct; we are not liable for transfers sent to incorrect details you provide.</p>

      <h2>4. Fees</h2>
      <p>Applicable fees are shown at the time of the transaction. We reserve the right to change our fees and rates at any time, but changes will not affect transactions already confirmed.</p>

      <h2>5. Cancellations and Refunds</h2>
      <p>You may request cancellation of a transfer that has not yet been paid out to the beneficiary. Refunds are made to the original payment source and may be subject to deductions for fees or currency fluctuations. Completed transfers cannot be reversed.</p>

      <h2>6. Acceptable Use</h2>
      <p>You agree not to use the Service for any unlawful purpose, including money laundering, terrorist financing, fraud, or sanctions evasion. We may suspend or terminate accounts that breach these terms or applicable law.</p>

      <h2>7. Limitation of Liability</h2>
      <p>To the extent permitted by law, Layla is not liable for indirect or consequential losses, or for delays caused by third parties, payment networks, or incorrect information you provide. Nothing in these terms excludes liability that cannot be excluded under law.</p>

      <h2>8. Privacy</h2>
      <p>Your use of the Service is also governed by our <a routerLink="/privacy-policy">Privacy Policy</a>, which explains how we handle your personal data.</p>

      <h2>9. Changes to These Terms</h2>
      <p>We may update these Terms from time to time. Continued use of the Service after changes are posted constitutes acceptance of the updated Terms.</p>

      <h2>10. Contact</h2>
      <p>Questions about these Terms? Contact us at <a href="mailto:info@laylamoneytransfer.co.uk">info@laylamoneytransfer.co.uk</a> or see our <a routerLink="/contact-us">Contact page</a>.</p>
    </div>
  </div>`
})
export class TermsPage {}
