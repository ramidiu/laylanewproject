import { Component } from '@angular/core';

@Component({
  selector: 'app-complaints',
  template: `
  <ion-content><div class="info-page">
    <div class="info-hero">
      <a class="info-back" href="/">&larr; Back to Home</a>
      <h1>Complaint Policy</h1>
      <p>We take complaints seriously and aim to resolve them fairly and quickly</p>
    </div>
    <div class="info-body">
      <p>Layla (London) Limited is committed to providing a high-quality service. If something goes wrong, we want to know so we can put it right.</p>

      <h2>How to Make a Complaint</h2>
      <p>You can contact us by:</p>
      <ul>
        <li>Email: <a href="mailto:info@laylamoneytransfer.co.uk">info@laylamoneytransfer.co.uk</a></li>
        <li>Phone: <a href="tel:+442077238555">+44 (0) 207 7238 555</a></li>
        <li>Post: Layla (London) Limited, 133 Broadley St, Marylebone, London NW8 8BA</li>
      </ul>
      <p>Please include your name, contact details, transaction reference (if any), and a description of the issue.</p>

      <h2>How We Handle Complaints</h2>
      <ul>
        <li>We will acknowledge your complaint promptly, normally within 3 business days.</li>
        <li>We will investigate and aim to provide a final response as quickly as possible, and within 8 weeks at the latest.</li>
        <li>If we need more time, we will keep you informed of progress.</li>
      </ul>

      <h2>If You're Not Satisfied</h2>
      <p>If you are unhappy with our final response, or if 8 weeks have passed without resolution, you may be able to refer your complaint to the <strong>Financial Ombudsman Service</strong>:</p>
      <ul>
        <li>Website: <a href="https://www.financial-ombudsman.org.uk" target="_blank" rel="noopener">www.financial-ombudsman.org.uk</a></li>
        <li>Phone: 0800 023 4567</li>
      </ul>
      <p>This service is free and independent. You typically have up to 6 months from our final response to refer your complaint.</p>

      <p class="info-muted">Layla (London) Limited · Companies House 9672013 · FCA 740120</p>
    </div>
  </div></ion-content>`
})
export class ComplaintsPage {}
