import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { SharedModule } from '../../shared/shared.module';
import { ResetPasswordPage } from './reset-password.page';

const routes: Routes = [
  { path: '', component: ResetPasswordPage }
];

@NgModule({
  declarations: [ResetPasswordPage],
  imports: [
    SharedModule,
    RouterModule.forChild(routes)
  ]
})
export class ResetPasswordPageModule {}
