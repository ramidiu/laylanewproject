import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { SharedModule } from '../../shared/shared.module';
import { VolumeCallbackPage } from './volume-callback.page';

const routes: Routes = [{ path: '', component: VolumeCallbackPage }];

@NgModule({
  declarations: [VolumeCallbackPage],
  imports: [SharedModule, RouterModule.forChild(routes)]
})
export class VolumeCallbackPageModule {}
