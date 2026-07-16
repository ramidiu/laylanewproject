import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { SharedModule } from '../../shared/shared.module';
import { TrustCallbackPage } from './trust-callback.page';

const routes: Routes = [{ path: '', component: TrustCallbackPage }];

@NgModule({
  declarations: [TrustCallbackPage],
  imports: [SharedModule, RouterModule.forChild(routes)]
})
export class TrustCallbackPageModule {}
