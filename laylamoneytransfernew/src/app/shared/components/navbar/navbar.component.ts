import { Component, Input, Output, EventEmitter } from '@angular/core';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-navbar',
  template: `
    <ion-header>
      <ion-toolbar class="fb-toolbar">
        <ion-buttons slot="start">
          <ion-menu-button *ngIf="showMenu" color="light"></ion-menu-button>
          <ion-back-button *ngIf="showBack" color="light" [defaultHref]="backHref"></ion-back-button>
        </ion-buttons>

        <ion-title>
          <div class="fb-toolbar__title">
            <img *ngIf="showLogo" src="assets/images/layla-logo.webp" alt="Layla Money Transfer" class="fb-toolbar__logo logo-white" />
            <span>{{ title }}</span>
          </div>
        </ion-title>

        <ion-buttons slot="end">
          <ion-button *ngIf="showNotifications" (click)="notificationClick.emit()" class="fb-toolbar__notification-btn">
            <ion-icon name="notifications-outline" slot="icon-only"></ion-icon>
            <ion-badge *ngIf="notificationCount > 0" class="fb-toolbar__badge">{{ notificationCount }}</ion-badge>
          </ion-button>
          <ion-button *ngIf="showAvatar" (click)="avatarClick.emit()" class="fb-toolbar__avatar-btn">
            <div class="fb-toolbar__avatar">
              {{ initials }}
            </div>
          </ion-button>
        </ion-buttons>
      </ion-toolbar>
    </ion-header>
  `,
  styleUrls: ['./navbar.component.scss']
})
export class NavbarComponent {
  @Input() title: string = 'Layla Money Transfer';
  @Input() showMenu: boolean = false;
  @Input() showBack: boolean = false;
  @Input() backHref: string = '/';
  @Input() showLogo: boolean = false;
  @Input() showNotifications: boolean = true;
  @Input() showAvatar: boolean = true;
  @Input() notificationCount: number = 0;

  @Output() notificationClick = new EventEmitter<void>();
  @Output() avatarClick = new EventEmitter<void>();

  constructor(private authService: AuthService) {}

  get initials(): string {
    const user = this.authService.getCurrentUser();
    if (!user) return 'FB';
    const email = user.email || '';
    return email.substring(0, 2).toUpperCase();
  }
}
