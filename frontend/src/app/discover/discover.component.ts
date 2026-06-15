import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
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
  error: string | null = null;

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

  load(): void {
    const userId = this.api.userId;
    if (!userId) {
      return;
    }
    this.loading = true;
    this.error = null;
    this.api.recommend(userId, this.selectedGenre, 3).subscribe({
      next: (recs) => {
        this.cards = recs.map((rec) => ({
          rec,
          words: '',
          reacting: false,
          done: null,
        }));
        this.loading = false;
      },
      error: () => {
        this.error = 'Could not load picks. Is the backend running on :8080?';
        this.loading = false;
      },
    });
  }

  react(card: CardState, reaction: Reaction): void {
    const userId = this.api.userId;
    if (!userId || card.reacting) {
      return;
    }
    card.reacting = true;
    this.api
      .react({
        userId,
        movieId: card.rec.id,
        genre: this.selectedGenre,
        reaction,
        words: card.words.trim() || undefined,
      })
      .subscribe({
        next: () => {
          card.done = reaction;
          card.reacting = false;
        },
        error: () => {
          card.reacting = false;
        },
      });
  }

  get allReacted(): boolean {
    return this.cards.length > 0 && this.cards.every((c) => c.done !== null);
  }

  posterFor(rec: Recommendation): string | null {
    return rec.posterUrl;
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
