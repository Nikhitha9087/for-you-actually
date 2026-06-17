export type Genre =
  | 'THRILLER_MYSTERY'
  | 'HORROR'
  | 'ROMANCE_COMEDY'
  | 'SCIFI_FANTASY'
  | 'DRAMA_SLICE_OF_LIFE';

export type Reaction = 'MORE_LIKE_THIS' | 'NOT_FOR_ME' | 'SEEN_IT';

export interface GenreMeta {
  id: Genre;
  label: string;
  blurb: string;
  placeholder: string;
}

export const GENRES: GenreMeta[] = [
  {
    id: 'THRILLER_MYSTERY',
    label: 'Thriller & Mystery',
    blurb: 'Tension, twists, things not being what they seem.',
    placeholder: 'e.g. Parasite — the slow dread and the rug-pull ending',
  },
  {
    id: 'HORROR',
    label: 'Horror',
    blurb: 'Dread, the uncanny, fear that lingers.',
    placeholder: 'e.g. Hereditary — quiet, then unbearable',
  },
  {
    id: 'ROMANCE_COMEDY',
    label: 'Romance & Comedy',
    blurb: 'Warmth, wit, people falling for each other.',
    placeholder: 'e.g. About Time — sweet without being saccharine',
  },
  {
    id: 'SCIFI_FANTASY',
    label: 'Sci-Fi & Fantasy',
    blurb: 'Big ideas, other worlds, what-ifs.',
    placeholder: 'e.g. Arrival — quiet, aching, brain-bending',
  },
  {
    id: 'DRAMA_SLICE_OF_LIFE',
    label: 'Drama & Slice of Life',
    blurb: 'Real people, real feelings, the small stuff.',
    placeholder: 'e.g. Past Lives — ordinary moments that wreck you',
  },
];

export const GENRE_LABELS: Record<Genre, string> = GENRES.reduce(
  (acc, g) => ({ ...acc, [g.id]: g.label }),
  {} as Record<Genre, string>
);

export interface OnboardPick {
  genre: Genre;
  title: string;
  why: string;
  overview?: string | null;
  tmdbId?: number | null;
}

export interface MovieSuggestion {
  tmdbId: number;
  title: string;
  year: number | null;
  language: string;
  overview: string | null;
  posterUrl: string | null;
}

export interface WatchProvider {
  name: string;
  logoUrl: string | null;
}

export interface WatchInfo {
  region: string | null;
  link: string | null;
  providers: WatchProvider[];
}

export interface Recommendation {
  id: number;
  title: string;
  originalLanguage: string;
  releaseYear: number | null;
  genres: Genre[];
  posterUrl: string | null;
  matchScore: number;
  whyYoullLikeIt: string | null;
  watch: WatchInfo | null;
}

export interface TasteShelf {
  genre: Genre;
  items: Recommendation[];
}

export interface TasteProfile {
  drawnTo: string[];
  avoid: string[];
  shelves: TasteShelf[];
}
