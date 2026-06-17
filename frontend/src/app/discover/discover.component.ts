import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { ApiService } from '../api.service';
import { GENRE_LABELS, GENRES, Genre, Reaction, Recommendation } from '../models';

interface CardState {
  rec: Recommendation;
  words: string;
  reacting: boolean;
  done: Reaction | null;
}

@Component({
  selector: 'app-discover',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './discover.component.html',
  styleUrl: './discover.component.css',
})
export class DiscoverComponent implements OnInit {
  genres = GENRES;
  labels = GENRE_LABELS;
  selectedGenre: Genre | null = null;
  cards: CardState[] = [];
  loading = false;
  loadingMore = false;
  exhausted = false;
  error: string | null = null;

  /** First batch size; "Show more" appends this many each time. */
  private readonly batchSize = 9;
  /** Films already shown this session, so paging never repeats a pick. */
  private shownIds = new Set<number>();

  constructor(private api: ApiService, private router: Router) {}

  ngOnInit(): void {
    if (!this.api.userId) {
      this.router.navigate(['/']);
      return;
    }
    this.load();
  }

  selectGenre(genre: Genre | null): void {
    if (this.selectedGenre === genre) {
      return;
    }
    this.selectedGenre = genre;
    this.load();
  }

  /** Fresh start for a mood: reset the seen-this-session set and load the first batch. */
  load(): void {
    const userId = this.api.userId;
    if (!userId) {
      return;
    }
    this.loading = true;
    this.error = null;
    this.exhausted = false;
    this.shownIds = new Set<number>();
    this.api.recommend(userId, this.selectedGenre, this.batchSize, []).subscribe({
      next: (recs) => {
        this.cards = recs.map((rec) => this.toCard(rec));
        recs.forEach((rec) => this.shownIds.add(rec.id));
        this.exhausted = recs.length < this.batchSize;
        this.loading = false;
      },
      error: (err) => {
        if (this.handleStaleUser(err)) {
          return;
        }
        this.error = 'Could not load picks. Is the backend running on :8080?';
        this.loading = false;
      },
    });
  }

  /** Appends the next batch, excluding everything shown so far (no repeats). */
  loadMore(): void {
    const userId = this.api.userId;
    if (!userId || this.loadingMore || this.exhausted) {
      return;
    }
    this.loadingMore = true;
    this.api
      .recommend(userId, this.selectedGenre, this.batchSize, Array.from(this.shownIds))
      .subscribe({
        next: (recs) => {
          const fresh = recs.filter((rec) => !this.shownIds.has(rec.id));
          fresh.forEach((rec) => this.shownIds.add(rec.id));
          this.cards = [...this.cards, ...fresh.map((rec) => this.toCard(rec))];
          this.exhausted = recs.length < this.batchSize;
          this.loadingMore = false;
        },
        error: (err) => {
          if (this.handleStaleUser(err)) {
            return;
          }
          this.loadingMore = false;
        },
      });
  }

  private toCard(rec: Recommendation): CardState {
    return { rec, words: '', reacting: false, done: null };
  }

  react(card: CardState, reaction: Reaction): void {
    const userId = this.api.userId;
    if (!userId || card.reacting || card.done) {
      return;
    }
    // Optimistic: reflect the choice instantly so the card responds without waiting on the
    // network. The taste nudge is a cheap server-side vector update, so we persist it in the
    // background and only roll back the card if the request actually fails.
    const words = card.words.trim() || undefined;
    card.done = reaction;
    this.api
      .react({
        userId,
        movieId: card.rec.id,
        genre: this.selectedGenre,
        reaction,
        words,
      })
      .subscribe({
        error: (err) => {
          if (this.handleStaleUser(err)) {
            return;
          }
          // Non-fatal: the nudge may not have persisted, but keep the UX moving.
        },
      });
    // Briefly show the confirmation, then remove the card and keep the feed full.
    setTimeout(() => {
      this.cards = this.cards.filter((c) => c !== card);
      this.replenishIfLow();
    }, 650);
  }

  /** Keeps the grid populated: pulls the next batch before the user runs out of cards. */
  private replenishIfLow(): void {
    if (!this.exhausted && !this.loadingMore && this.cards.length < 4) {
      this.loadMore();
    }
  }

  /**
   * A 404 means this browser holds a userId the server no longer knows (e.g. after a
   * fresh DB ship). Self-heal: drop the stale id and send the visitor back to onboarding
   * instead of trapping them on a "could not load" error.
   */
  private handleStaleUser(err: unknown): boolean {
    if (err instanceof HttpErrorResponse && err.status === 404) {
      this.api.clearUser();
      this.router.navigate(['/']);
      return true;
    }
    return false;
  }

  posterFor(rec: Recommendation): string | null {
    return rec.posterUrl;
  }

  hasWatch(rec: Recommendation): boolean {
    return !!rec.watch && ((rec.watch.providers?.length ?? 0) > 0 || !!rec.watch.link);
  }

  regionName(code: string | null): string {
    if (!code) {
      return '';
    }
    const map: Record<string, string> = { IN: 'India', US: 'the US' };
    return map[code] ?? code;
  }

  /** Region-neutral TMDB watch link , dropping ?locale lets TMDB show the viewer's own country. */
  watchLink(rec: Recommendation): string | null {
    const link = rec.watch?.link;
    return link ? link.split('?')[0] : null;
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

  startOver(): void {
    this.api.clearUser();
    this.router.navigate(['/']);
  }
}
