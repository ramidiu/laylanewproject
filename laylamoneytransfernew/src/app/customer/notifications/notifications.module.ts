import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { SharedModule } from '../../shared/shared.module';
import { NotificationsPage } from './notifications.page';

const routes: Routes = [
  { path: '', component: NotificationsPage }
];

@NgModule({
  declarations: [NotificationsPage],
  imports: [
    SharedModule,
    RouterModule.forChild(routes)
  ]
})
export class NotificationsPageModule {}
