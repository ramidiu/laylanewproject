import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { IonicModule } from '@ionic/angular';

import { PrivacyPolicyPage } from './privacy-policy.page';
import { AboutPage } from './about.page';
import { ContactUsPage } from './contact-us.page';
import { TermsPage } from './terms.page';
import { FaqPage } from './faq.page';
import { ComplaintsPage } from './complaints.page';
import { CookiePolicyPage } from './cookie-policy.page';
import { UserAgreementPage } from './user-agreement.page';
import { MobileTermsPage } from './mobile-terms.page';
import { MobilePrivacyPage } from './mobile-privacy.page';
import { ReceiptViewPage } from './receipt-view.page';

const PAGES = [
  PrivacyPolicyPage, AboutPage, ContactUsPage, TermsPage,
  FaqPage, ComplaintsPage, CookiePolicyPage, UserAgreementPage,
  MobileTermsPage, MobilePrivacyPage, ReceiptViewPage
];

@NgModule({
  imports: [CommonModule, RouterModule, IonicModule],
  declarations: [...PAGES],
  exports: [...PAGES]
})
export class PagesModule {}
