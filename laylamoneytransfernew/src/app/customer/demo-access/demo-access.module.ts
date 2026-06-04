import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { SharedModule } from '../../shared/shared.module';
import { DemoAccessPage } from './demo-access.page';

const routes: Routes = [
  { path: '', component: DemoAccessPage }
];

@NgModule({
  declarations: [DemoAccessPage],
  imports: [
    SharedModule,
    RouterModule.forChild(routes)
  ]
})
export class DemoAccessPageModule {}
