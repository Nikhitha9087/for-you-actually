import { Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { Subject, Subscription, of } from 'rxjs';
import {
  catchError,
  debounceTime,
  distinctUntilChanged,
  map,
  switchMap,
} from 'rxjs/operators';
import { ApiService } from '../api.service';
import { GENRES, Genre, MovieSuggestion, OnboardPick } from '../models';

interface PickState {
  title: string;
  why: string;
  overview: string | null;
  tmdbId: number | null;
  confirmed: boolean;
  language: string | null;
  year: number | null;
}

@Component({
  selector: 'app-onboarding',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './onboarding.component.html',
  styleUrl: './onboarding.component.css',
})
export class OnboardingComponent implements OnInit, OnDestroy {
  genres = GENRES;
  picks: Record<string, PickState> = {};
  submitting = false;
  error: string | null = null;

  activeGenre: Genre | null = null;
  suggestions: MovieSuggestion[] = [];
  searching = false;

  private search$ = new Subject<{ genre: Genre; q: string }>();
  private sub?: Subscription;

  constructor(private api: ApiService, private router: Router) {}

  ngOnInit(): void {
    if (this.api.userId) {
      this.router.navigate(['/discover']);
      return;
    }
    for (const g of this.genres) {
      this.picks[g.id] = {
        title: '',
        why: '',
        overview: null,
        tmdbId: null,
        confirmed: false,
        language: null,
        year: null,
      };
    }

    this.sub = this.search$
      .pipe(
        debounceTime(250),
        distinctUntilChanged((a, b) => a.genre === b.genre && a.q === b.q),
        switchMap(({ genre, q }) => {
          if (q.trim().length < 2) {
            return of({ genre, results: [] as MovieSuggestion[] });
          }
          this.searching = true;
          return this.api.search(q).pipe(
            map((results) => ({ genre, results })),
            catchError(() => of({ genre, results: [] as MovieSuggestion[] }))
          );
        })
      )
      .subscribe(({ genre, results }) => {
        this.searching = false;
        if (this.activeGenre === genre) {
          this.suggestions = results;
        }
      });
  }

  ngOnDestroy(): void {
    this.sub?.unsubscribe();
  }

  onType(genre: Genre, value: string): void {
    const pick = this.picks[genre];
    pick.title = value;
    // Editing the text invalidates a prior confirmed match.
    pick.confirmed = false;
    pick.overview = null;
    pick.tmdbId = null;
    pick.language = null;
    pick.year = null;
    this.activeGenre = genre;
    this.search$.next({ genre, q: value });
  }

  select(genre: Genre, s: MovieSuggestion): void {
    const pick = this.picks[genre];
    pick.title = s.title;
    pick.overview = s.overview;
    pick.tmdbId = s.tmdbId;
    pick.language = s.language;
    pick.year = s.year;
    pick.confirmed = true;
    this.suggestions = [];
    this.activeGenre = null;
  }

  closeSoon(): void {
    setTimeout(() => {
      this.activeGenre = null;
      this.suggestions = [];
    }, 180);
  }

  langName(code: string | null): string {
    if (!code) {
      return '';
    }
    const map: Record<string, string> = {
      en: 'English', ko: 'Korean', ja: 'Japanese', es: 'Spanish',
      fr: 'French', de: 'German', hi: 'Hindi', it: 'Italian',
      zh: 'Chinese', pt: 'Portuguese', ta: 'Tamil', te: 'Telugu',
      ml: 'Malayalam', th: 'Thai', sv: 'Swedish', da: 'Danish', ru: 'Russian',
    };
    return map[code] ?? code.toUpperCase();
  }

  get filledCount(): number {
    return this.genres.filter((g) => this.picks[g.id]?.title.trim()).length;
  }

  get canSubmit(): boolean {
    return this.filledCount > 0 && !this.submitting;
  }

  submit(): void {
    const payload: OnboardPick[] = this.genres
      .filter((g) => this.picks[g.id]?.title.trim())
      .map((g) => {
        const p = this.picks[g.id];
        return {
          genre: g.id,
          title: p.title.trim(),
          why: p.why.trim(),
          overview: p.overview,
          tmdbId: p.tmdbId,
        };
      });

    if (payload.length === 0) {
      return;
    }

    this.submitting = true;
    this.error = null;
    this.api.onboard(payload).subscribe({
      next: (res) => {
        this.api.setUserId(res.userId);
        this.router.navigate(['/discover']);
      },
      error: (err) => {
        this.error =
          err?.error?.message ||
          'Could not build your profile. Pick films from the suggestions list, or check the backend (:8080).';
        this.submitting = false;
      },
    });
  }
}
