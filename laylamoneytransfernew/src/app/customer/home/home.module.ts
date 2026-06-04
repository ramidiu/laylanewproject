import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { SharedModule } from '../../shared/shared.module';
import { HomePage } from './home.page';

const routes: Routes = [
  { path: '', component: HomePage }
];

@NgModule({
  declarations: [HomePage],
  imports: [
    SharedModule,
    RouterModule.forChild(routes)
  ]
})
export class HomePageModule {}
