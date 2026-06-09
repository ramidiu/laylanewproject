import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { IonicModule } from '@ionic/angular';
import { RouterModule, Routes } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { TabsPage } from './tabs.page';
import { KycGuard } from '../../core/guards/kyc.guard';
import { RequireKycSubmittedGuard } from '../../core/guards/require-kyc-submitted.guard';

const routes: Routes = [
  {
    path: '',
    component: TabsPage,
    children: [
      {
        path: 'dashboard',
        canActivate: [RequireKycSubmittedGuard],
        loadChildren: () => import('../home/home.module').then(m => m.HomePageModule)
      },
      {
        path: 'send',
        loadChildren: () => import('../send-money/send-money.module').then(m => m.SendMoneyPageModule),
        canActivate: [RequireKycSubmittedGuard, KycGuard]
      },
      {
        path: 'transactions',
        canActivate: [RequireKycSubmittedGuard],
        loadChildren: () => import('../transactions/transactions.module').then(m => m.TransactionsPageModule)
      },
      {
        path: 'beneficiaries',
        canActivate: [RequireKycSubmittedGuard],
        loadChildren: () => import('../beneficiaries/beneficiaries.module').then(m => m.BeneficiariesPageModule)
      },
      {
        path: 'profile',
        loadChildren: () => import('../profile/profile.module').then(m => m.ProfilePageModule)
      },
      {
        path: 'kyc',
        loadChildren: () => import('../kyc/kyc.module').then(m => m.KycPageModule)
      },
      {
        path: 'mfa-setup',
        loadChildren: () => import('../mfa-setup/mfa-setup.module').then(m => m.MfaSetupPageModule)
      },
      {
        path: 'delete-account',
        loadChildren: () => import('../delete-account/delete-account.module').then(m => m.DeleteAccountPageModule)
      },
      {
        path: 'wallet',
        canActivate: [RequireKycSubmittedGuard],
        loadChildren: () => import('../wallet/wallet.module').then(m => m.WalletPageModule)
      },
      {
        path: 'support',
        loadChildren: () => import('../support/support.module').then(m => m.SupportPageModule)
      },
      {
        path: 'notifications',
        loadChildren: () => import('../notifications/notifications.module').then(m => m.NotificationsPageModule)
      },
      {
        path: 'referral',
        loadChildren: () => import('../referral/referral.module').then(m => m.ReferralPageModule)
      },
      {
        path: 'transfer-success',
        loadChildren: () => import('../transfer-success/transfer-success.module').then(m => m.TransferSuccessPageModule)
      },
      {
        path: 'volume-pay',
        loadChildren: () => import('../volume-pay/volume-pay.module').then(m => m.VolumePayPageModule)
      },
      {
        path: 'volume-callback',
        loadChildren: () => import('../volume-callback/volume-callback.module').then(m => m.VolumeCallbackPageModule)
      },
      {
        path: 'trust-callback',
        loadChildren: () => import('../trust-callback/trust-callback.module').then(m => m.TrustCallbackPageModule)
      },
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' }
    ]
  }
];

@NgModule({
  declarations: [TabsPage],
  imports: [
    CommonModule,
    IonicModule,
    FormsModule,
    TranslateModule,
    RouterModule.forChild(routes)
  ]
})
export class TabsPageModule {}
