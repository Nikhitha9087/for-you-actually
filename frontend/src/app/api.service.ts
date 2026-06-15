import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import {
  Genre,
  MovieSuggestion,
  OnboardPick,
  Reaction,
  Recommendation,
  TasteProfile,
} from './models';
import { environment } from '../environments/environment';

const API_BASE = environment.apiBase;
const USER_KEY = 'fya_userId';

@Injectable({ providedIn: 'root' })
export class ApiService {
  constructor(private http: HttpClient) {}

  get userId(): string | null {
    return localStorage.getItem(USER_KEY);
  }

  setUserId(id: string): void {
    localStorage.setItem(USER_KEY, id);
  }

  clearUser(): void {
    localStorage.removeItem(USER_KEY);
  }

  onboard(picks: OnboardPick[]): Observable<{ userId: string }> {
    return this.http.post<{ userId: string }>(`${API_BASE}/onboard`, { picks });
  }

  recommend(
    userId: string,
    genre: Genre | null,
    count = 3
  ): Observable<Recommendation[]> {
    let params = new HttpParams().set('userId', userId).set('count', count);
    if (genre) {
      params = params.set('genre', genre);
    }
    return this.http.get<Recommendation[]>(`${API_BASE}/recommend`, { params });
  }

  react(body: {
    userId: string;
    movieId: number;
    genre: Genre | null;
    reaction: Reaction;
    words?: string;
  }): Observable<{ ok: boolean }> {
    return this.http.post<{ ok: boolean }>(`${API_BASE}/react`, body);
  }

  profile(userId: string): Observable<TasteProfile> {
    const params = new HttpParams().set('userId', userId);
    return this.http.get<TasteProfile>(`${API_BASE}/profile`, { params });
  }

  search(query: string): Observable<MovieSuggestion[]> {
    const params = new HttpParams().set('q', query);
    return this.http.get<MovieSuggestion[]>(`${API_BASE}/search`, { params });
  }
}
