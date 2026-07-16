import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { SharedModule } from '../../shared/shared.module';
import { DeleteAccountPage } from './delete-account.page';

const routes: Routes = [
  { path: '', component: DeleteAccountPage }
];

@NgModule({
  declarations: [DeleteAccountPage],
  imports: [
    SharedModule,
    RouterModule.forChild(routes)
  ]
})
export class DeleteAccountPageModule {}
