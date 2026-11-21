/** One of the three legal documents (#74), as a page draws it. */
export interface LegalDocument {
  title: string;
  /** Shown under the title: when the text last changed in substance. */
  updated: string;
  sections: { heading: string; paragraphs: string[] }[];
}

export const LEGAL_DOCS = ['terms', 'privacy', 'notice'] as const;
export type LegalDoc = (typeof LEGAL_DOCS)[number];

export type LegalContent = Record<LegalDoc, LegalDocument>;

/**
 * The documents per language, fetched with the page that reads them. They are long, read
 * rarely and by few, so they stay out of the locale files the first screen is painted from —
 * French is in the eager payload, whose budget is nearly spent.
 */
export const LEGAL_CONTENT: Record<string, () => Promise<{ default: LegalContent }>> = {
  fr: () => import('./content.fr'),
  en: () => import('./content.en'),
};
