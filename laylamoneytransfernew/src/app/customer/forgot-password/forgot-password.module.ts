import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { SharedModule } from '../../shared/shared.module';
import { ForgotPasswordPage } from './forgot-password.page';

const routes: Routes = [
  { path: '', component: ForgotPasswordPage }
];

@NgModule({
  declarations: [ForgotPasswordPage],
  imports: [
    SharedModule,
    RouterModule.forChild(routes)
  ]
})
export class ForgotPasswordPageModule {}
