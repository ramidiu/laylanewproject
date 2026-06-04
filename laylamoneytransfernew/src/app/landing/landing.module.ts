import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { SharedModule } from '../shared/shared.module';
import { LandingPage } from './landing.page';

const routes: Routes = [
  { path: '', component: LandingPage }
];

@NgModule({
  declarations: [LandingPage],
  imports: [
    SharedModule,
    RouterModule.forChild(routes)
  ]
})
export class LandingPageModule {}
