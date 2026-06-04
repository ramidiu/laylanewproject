import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { SharedModule } from '../../shared/shared.module';
import { SupportPage } from './support.page';

const routes: Routes = [
  { path: '', component: SupportPage }
];

@NgModule({
  declarations: [SupportPage],
  imports: [
    SharedModule,
    RouterModule.forChild(routes)
  ]
})
export class SupportPageModule {}
