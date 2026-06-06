import { Component } from '@angular/core';

@Component({
  selector: 'app-contact-us',
  template: `
  <ion-content><div class="info-page">
    <div class="info-hero">
      <a class="info-back" href="/">&larr; Back to Home</a>
      <h1>Contact Us</h1>
      <p>We're here to help — get in touch</p>
    </div>
    <div class="info-body">
      <div class="info-contact-row">
        <ion-icon name="location-outline"></ion-icon>
        <div><strong>Address</strong><br>133 Broadley St, Marylebone, London NW8 8BA, United Kingdom</div>
      </div>
      <div class="info-contact-row">
        <ion-icon name="call-outline"></ion-icon>
        <div><strong>Phone</strong><br>
          <a href="tel:+442077238555">+44 (0) 207 7238 555</a><br>
          <a href="tel:+447954466888">07954 466 888</a><br>
          <a href="tel:+447984919818">07984 919 818</a>
        </div>
      </div>
      <div class="info-contact-row">
        <ion-icon name="mail-outline"></ion-icon>
        <div><strong>Email</strong><br>
          <a href="mailto:info@laylamoneytransfer.co.uk">info@laylamoneytransfer.co.uk</a>
        </div>
      </div>
      <div class="info-contact-row">
        <ion-icon name="time-outline"></ion-icon>
        <div><strong>Opening Hours</strong><br>Monday – Saturday: 11:00am to 7:00pm<br>Sunday: Closed</div>
      </div>

      <h2>Our Team</h2>
      <div class="info-cards">
        <div class="info-card">
          <h3>Azim Elsidieg</h3>
          <p class="info-muted">Director</p>
          <p><a href="mailto:azim@laylamoneytransfer.co.uk">azim@laylamoneytransfer.co.uk</a></p>
        </div>
        <div class="info-card">
          <h3>Khalid Ahmed</h3>
          <p class="info-muted">Customer Support</p>
          <p><a href="mailto:khalid@laylamoneytransfer.co.uk">khalid@laylamoneytransfer.co.uk</a></p>
        </div>
      </div>

      <p class="info-muted">Layla (London) Limited · Companies House 9672013 · FCA 740120</p>
    </div>
  </div></ion-content>`
})
export class ContactUsPage {}
