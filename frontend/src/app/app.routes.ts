import { Routes } from '@angular/router';
import { OnboardingComponent } from './onboarding/onboarding.component';
import { DiscoverComponent } from './discover/discover.component';
import { ProfileComponent } from './profile/profile.component';

export const routes: Routes = [
  { path: '', component: OnboardingComponent },
  { path: 'discover', component: DiscoverComponent },
  { path: 'profile', component: ProfileComponent },
  { path: '**', redirectTo: '' },
];
