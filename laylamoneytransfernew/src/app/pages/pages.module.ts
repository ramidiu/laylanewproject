import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { IonicModule } from '@ionic/angular';

import { PrivacyPolicyPage } from './privacy-policy.page';
import { AboutPage } from './about.page';
import { ContactUsPage } from './contact-us.page';
import { TermsPage } from './terms.page';

@NgModule({
  imports: [CommonModule, RouterModule, IonicModule],
  declarations: [PrivacyPolicyPage, AboutPage, ContactUsPage, TermsPage],
  exports: [PrivacyPolicyPage, AboutPage, ContactUsPage, TermsPage]
})
export class PagesModule {}
