import { Component } from '@angular/core';

@Component({
  selector: 'app-cookie-policy',
  template: `
  <ion-content><div class="info-page">
    <div class="info-hero">
      <a class="info-back" href="/">&larr; Back to Home</a>
      <h1>Cookie Policy</h1>
      <p>Effective Date: 24 June 2025</p>
    </div>
    <div class="info-body">
      <p>This Cookie Policy explains how Layla Money Transfer uses cookies and similar technologies on our website and mobile application.</p>

      <h2>What Are Cookies?</h2>
      <p>Cookies are small text files stored on your device when you visit a website. They help the site remember your actions and preferences over time.</p>

      <h2>How We Use Cookies</h2>
      <ul>
        <li><strong>Essential cookies</strong> — enable core functionality such as secure login, session handling, and security. The site cannot work properly without these.</li>
        <li><strong>Preference cookies</strong> — remember your settings, such as language (English/Arabic).</li>
        <li><strong>Analytics cookies</strong> — help us understand how the site is used so we can improve performance and the user experience.</li>
      </ul>

      <h2>Managing Cookies</h2>
      <p>You can control or delete cookies through your browser settings. Disabling certain cookies may affect functionality such as login and session persistence.</p>

      <h2>Third-Party Cookies</h2>
      <p>Some cookies may be set by trusted third parties (for example, analytics providers). These partners are contractually obligated to protect your data.</p>

      <h2>Changes to This Policy</h2>
      <p>We may update this Cookie Policy from time to time. Changes will be posted on this page with an updated "Effective Date."</p>

      <h2>Contact</h2>
      <p>Questions about cookies? Email <a href="mailto:info@laylamoneytransfer.co.uk">info@laylamoneytransfer.co.uk</a>. See also our <a href="/privacy-policy">Privacy Policy</a>.</p>
    </div>
  </div></ion-content>`
})
export class CookiePolicyPage {}
