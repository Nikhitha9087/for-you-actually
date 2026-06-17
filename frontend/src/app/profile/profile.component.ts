import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Router, RouterLink } from '@angular/router';
import { ApiService } from '../api.service';
import { GENRE_LABELS, Recommendation, TasteProfile } from '../models';

@Component({
  selector: 'app-profile',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './profile.component.html',
  styleUrl: './profile.component.css',
})
export class ProfileComponent implements OnInit {
  labels = GENRE_LABELS;
  profile: TasteProfile | null = null;
  loading = false;
  error: string | null = null;

  constructor(private api: ApiService, private router: Router) {}

  ngOnInit(): void {
    if (!this.api.userId) {
      this.router.navigate(['/']);
      return;
    }
    this.load();
  }

  load(): void {
    const userId = this.api.userId;
    if (!userId) {
      return;
    }
    this.loading = true;
    this.error = null;
    this.api.profile(userId).subscribe({
      next: (p) => {
        this.profile = p;
        this.loading = false;
      },
      error: (err) => {
        if (err instanceof HttpErrorResponse && err.status === 404) {
          this.api.clearUser();
          this.router.navigate(['/']);
          return;
        }
        this.error = 'Could not load your taste profile. Is the backend running on :8080?';
        this.loading = false;
      },
    });
  }

  get hasMirror(): boolean {
    return !!this.profile && (this.profile.drawnTo.length > 0 || this.profile.avoid.length > 0);
  }

  langName(code: string): string {
    const map: Record<string, string> = {
      en: 'English', ko: 'Korean', ja: 'Japanese', es: 'Spanish',
      fr: 'French', de: 'German', hi: 'Hindi', it: 'Italian',
      zh: 'Chinese', pt: 'Portuguese', ta: 'Tamil', te: 'Telugu',
      ml: 'Malayalam', th: 'Thai', sv: 'Swedish', da: 'Danish',
    };
    return map[code] ?? code.toUpperCase();
  }

  trackById(_: number, item: Recommendation): number {
    return item.id;
  }
}
