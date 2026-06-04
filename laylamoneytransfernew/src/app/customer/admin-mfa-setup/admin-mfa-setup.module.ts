import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule, Routes } from '@angular/router';
import { IonicModule } from '@ionic/angular';
import { AdminMfaSetupPage } from './admin-mfa-setup.page';

const routes: Routes = [
  { path: '', component: AdminMfaSetupPage }
];

@NgModule({
  imports: [
    CommonModule,
    FormsModule,
    IonicModule,
    RouterModule.forChild(routes)
  ],
  declarations: [AdminMfaSetupPage]
})
export class AdminMfaSetupPageModule {}
