import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { SharedModule } from '../../shared/shared.module';
import { ProfilePage } from './profile.page';

const routes: Routes = [
  { path: '', component: ProfilePage }
];

@NgModule({
  declarations: [ProfilePage],
  imports: [
    SharedModule,
    RouterModule.forChild(routes)
  ]
})
export class ProfilePageModule {}
