import { NgModule } from '@angular/core';
import { PreloadAllModules, RouterModule, Routes } from '@angular/router';
import { AuthGuard } from './core/guards/auth.guard';
import { RoleGuard } from './core/guards/role.guard';
import { PrivacyPolicyPage } from './pages/privacy-policy.page';
import { AboutPage } from './pages/about.page';
import { ContactUsPage } from './pages/contact-us.page';
import { TermsPage } from './pages/terms.page';
import { FaqPage } from './pages/faq.page';
import { ComplaintsPage } from './pages/complaints.page';
import { CookiePolicyPage } from './pages/cookie-policy.page';
import { UserAgreementPage } from './pages/user-agreement.page';
import { MobileTermsPage } from './pages/mobile-terms.page';
import { MobilePrivacyPage } from './pages/mobile-privacy.page';

const routes: Routes = [
  {
    path: '',
    loadChildren: () => import('./landing/landing.module').then(m => m.LandingPageModule)
  },
  // Public info / legal pages
  { path: 'privacy-policy', component: PrivacyPolicyPage },
  { path: 'about-us', component: AboutPage },
  { path: 'contact-us', component: ContactUsPage },
  { path: 'terms', component: TermsPage },
  { path: 'faq', component: FaqPage },
  { path: 'complaints', component: ComplaintsPage },
  { path: 'cookie-policy', component: CookiePolicyPage },
  { path: 'user-agreement', component: UserAgreementPage },
  { path: 'mobile-terms', component: MobileTermsPage },
  { path: 'mobile-privacy', component: MobilePrivacyPage },
  // Staff receipt viewer (print + download PDF) — lazy module so it renders under ion-router-outlet on SPA nav.
  {
    path: 'receipt/:id',
    loadChildren: () => import('./pages/receipt-view.module').then(m => m.ReceiptViewModule),
    canActivate: [AuthGuard]
  },
  {
    path: 'login',
    loadChildren: () => import('./customer/login/login.module').then(m => m.LoginPageModule)
  },
  {
    path: 'admin-login',
    loadChildren: () => import('./customer/admin-login/admin-login.module').then(m => m.AdminLoginPageModule)
  },
  {
    path: 'register',
    loadChildren: () => import('./customer/register/register.module').then(m => m.RegisterPageModule)
  },
  {
    path: 'otp-verify',
    loadChildren: () => import('./customer/otp-verify/otp-verify.module').then(m => m.OtpVerifyPageModule)
  },
  {
    path: 'forgot-password',
    loadChildren: () => import('./customer/forgot-password/forgot-password.module').then(m => m.ForgotPasswordPageModule)
  },
  {
    path: 'reset-password',
    loadChildren: () => import('./customer/reset-password/reset-password.module').then(m => m.ResetPasswordPageModule)
  },
  {
    path: 'admin-mfa-setup',
    loadChildren: () => import('./customer/admin-mfa-setup/admin-mfa-setup.module').then(m => m.AdminMfaSetupPageModule)
  },
  {
    path: 'demo-access',
    loadChildren: () => import('./customer/demo-access/demo-access.module').then(m => m.DemoAccessPageModule)
  },
  {
    path: 'home',
    loadChildren: () => import('./customer/tabs/tabs.module').then(m => m.TabsPageModule),
    canActivate: [AuthGuard]
  },
  {
    path: 'admin',
    loadChildren: () => import('./admin/admin.module').then(m => m.AdminModule),
    canActivate: [AuthGuard, RoleGuard],
    data: { roles: ['ADMIN', 'SUPER_ADMIN'] }
  },
  {
    path: 'partner',
    loadChildren: () => import('./partner/partner.module').then(m => m.PartnerModule),
    canActivate: [AuthGuard, RoleGuard],
    data: { roles: ['PAYOUT_PARTNER'] }
  },
  {
    path: 'agent',
    loadChildren: () => import('./agent/agent.module').then(m => m.AgentModule),
    canActivate: [AuthGuard, RoleGuard],
    data: { roles: ['AGENT'] }
  },
  {
    path: 'superadmin',
    loadChildren: () => import('./superadmin/superadmin.module').then(m => m.SuperAdminModule),
    canActivate: [AuthGuard, RoleGuard],
    data: { roles: ['SUPER_ADMIN', 'ADMIN'] }
  },
  {
    path: 'payin-partner',
    loadChildren: () => import('./payin-partner/payin-partner.module').then(m => m.PayinPartnerModule),
    canActivate: [AuthGuard, RoleGuard],
    data: { roles: ['PAYIN_PARTNER'] }
  },
  { path: '**', redirectTo: '' }
];

@NgModule({
  imports: [
    RouterModule.forRoot(routes, { preloadingStrategy: PreloadAllModules })
  ],
  exports: [RouterModule]
})
export class AppRoutingModule {}
