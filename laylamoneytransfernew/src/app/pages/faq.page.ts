import { Component } from '@angular/core';

@Component({
  selector: 'app-faq',
  template: `
  <div class="info-page">
    <div class="info-hero">
      <a class="info-back" routerLink="/">&larr; Back to Home</a>
      <h1>Frequently Asked Questions</h1>
      <p>Everything you need to know about sending money with Layla</p>
    </div>
    <div class="info-body">
      <h2>How do I send money with Layla?</h2>
      <p>Create an account, complete identity verification (KYC), add your recipient's details, choose an amount and delivery method, pay, and we'll process the transfer to your beneficiary.</p>

      <h2>Which countries can I send to?</h2>
      <p>We support a growing list of corridors (including Sudan and other destinations). The available receive countries and delivery methods are shown when you start a transfer.</p>

      <h2>What delivery methods are available?</h2>
      <p>Depending on the destination: <strong>Bank Transfer</strong>, <strong>Cash Pickup</strong>, and <strong>Mobile Wallet</strong>. Only the methods enabled for your chosen country will appear.</p>

      <h2>How long does a transfer take?</h2>
      <p>Many transfers are processed quickly, often within minutes to a few hours, depending on the destination, delivery method, and verification status.</p>

      <h2>What are the fees and exchange rates?</h2>
      <p>The fee and the live exchange rate are shown before you confirm, so you always see exactly what your recipient will get.</p>

      <h2>Why do I need to verify my identity?</h2>
      <p>As an FCA-regulated business, we are legally required to verify customers (KYC) and comply with Anti-Money Laundering rules. You may be asked to upload an ID document and proof of address.</p>

      <h2>Can I cancel a transfer?</h2>
      <p>You can request cancellation of a transfer that has not yet been paid out to the beneficiary. Completed transfers cannot be reversed. See our <a routerLink="/terms">Terms of Use</a>.</p>

      <h2>How do I get a receipt?</h2>
      <p>Open the transaction in your account and tap <strong>View Receipt</strong> or <strong>Download Receipt</strong>.</p>

      <h2>How do I contact support?</h2>
      <p>Email <a href="mailto:info@laylamoneytransfer.co.uk">info@laylamoneytransfer.co.uk</a> or see our <a routerLink="/contact-us">Contact page</a>.</p>
    </div>
  </div>`
})
export class FaqPage {}
