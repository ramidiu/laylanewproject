import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { SharedModule } from '../../shared/shared.module';
import { LoginPage } from './login.page';

const routes: Routes = [
  { path: '', component: LoginPage }
];

@NgModule({
  declarations: [LoginPage],
  imports: [
    SharedModule,
    RouterModule.forChild(routes)
  ]
})
export class LoginPageModule {}
