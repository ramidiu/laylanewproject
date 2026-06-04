import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { SharedModule } from '../../shared/shared.module';
import { VolumePayPage } from './volume-pay.page';

const routes: Routes = [{ path: '', component: VolumePayPage }];

@NgModule({
  declarations: [VolumePayPage],
  imports: [SharedModule, RouterModule.forChild(routes)]
})
export class VolumePayPageModule {}
