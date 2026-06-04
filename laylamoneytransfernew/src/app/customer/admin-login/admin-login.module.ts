import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { SharedModule } from '../../shared/shared.module';
import { AdminLoginPage } from './admin-login.page';

const routes: Routes = [
  { path: '', component: AdminLoginPage }
];

@NgModule({
  declarations: [AdminLoginPage],
  imports: [
    SharedModule,
    RouterModule.forChild(routes)
  ]
})
export class AdminLoginPageModule {}
