import { Component } from '@angular/core';

@Component({
  selector: 'app-about',
  template: `
  <div class="info-page">
    <div class="info-hero">
      <a class="info-back" href="/">&larr; Back to Home</a>
      <h1>About Us</h1>
      <p>Seamless global transfers you can trust</p>
    </div>
    <div class="info-body">
      <p>Layla (London) Limited was established by executive director Abdelazim Elsidieg in July 2015. The company's main objective is to provide customers with an affordable and efficient means of transferring their money abroad.</p>
      <p>The company office is located directly off London's bustling Edgware Road, and is easily accessible via public transport.</p>
      <p>Layla was registered by Companies House under the registered number <strong>9672013</strong> and is also regulated by the Financial Conduct Authority (<strong>740120</strong>).</p>
      <p>Layla is fully compliant with EU Transfer of Funds Regulations and Anti-Money Laundering Regulations outlined by HM Revenue &amp; Customs to ensure a secure service. Layla has built a steady customer base by continuously being committed to providing a friendly, personalised service, as well as competitive market rates.</p>

      <h2>Why Choose Layla</h2>
      <div class="info-cards">
        <div class="info-card"><h3>Safe &amp; Secure</h3><p class="info-muted">FCA-authorised, so your transfer is safe and secure — with better rates than the bank.</p></div>
        <div class="info-card"><h3>Low Cost</h3><p class="info-muted">Our exchange rates are very competitive, so you earn more for your money.</p></div>
        <div class="info-card"><h3>Easy &amp; Fast</h3><p class="info-muted">Our money transfer services are lightning fast so your beneficiary gets the money quickly.</p></div>
        <div class="info-card"><h3>24/7 Support</h3><p class="info-muted">A team of specialists ready to help with any queries.</p></div>
      </div>

      <h2>Visit Us</h2>
      <p>133 Broadley St, Marylebone, London NW8 8BA<br>
      Opening Hours: Monday – Saturday 11:00am to 7:00pm · Sunday: Closed</p>
      <p><a href="/contact-us">Contact us &rarr;</a></p>
    </div>
  </div>`
})
export class AboutPage {}
