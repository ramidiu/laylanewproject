import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { SharedModule } from '../../shared/shared.module';
import { RegisterPage } from './register.page';

const routes: Routes = [
  { path: '', component: RegisterPage }
];

@NgModule({
  declarations: [RegisterPage],
  imports: [
    SharedModule,
    RouterModule.forChild(routes)
  ]
})
export class RegisterPageModule {}
