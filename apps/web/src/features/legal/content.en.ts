import type { LegalContent } from './content';

// Translated from the French, which is the authored text. The bracketed passages are facts
// only the publisher can state (#74): they are filled in before the service is promoted.
const TODO = (what: string) => `[To be completed: ${what}]`;

const content: LegalContent = {
  terms: {
    title: 'Terms of service',
    updated: 'Last updated: November 21, 2026',
    sections: [
      {
        heading: 'What the service is',
        paragraphs: [
          'Librarius is an online personal library for books and manga: you keep your collection there, track your reading, maintain your wishlist and, if you want to, share your activity with the readers you follow.',
          'By creating an account and accepting these terms, you agree to abide by them. If you do not accept them, do not use the service.',
        ],
      },
      {
        heading: 'Your account',
        paragraphs: [
          'Your account is personal. You are responsible for keeping your password confidential and for what is done from your account.',
          'The display name, profile picture, reviews and comments you publish must respect the law and other readers: no unlawful, hateful or defamatory content, nothing that infringes the rights of others.',
        ],
      },
      {
        heading: 'Content and catalog',
        paragraphs: [
          'Your ratings, reviews and reading sheets remain yours. By making them visible to other readers, you allow them to be shown within the service, and only there.',
          'Catalog entries (titles, authors, covers) come from public sources — Open Library, AniList, the BnF — and from readers’ corrections. Their accuracy is not guaranteed.',
        ],
      },
      {
        heading: 'Imports from other sites',
        paragraphs: [
          'The Booknode, Babelio and Mangacollec imports read your own library, in your own browser and with your own session, then have you save a file that you import. No Librarius server signs in to those sites on your behalf.',
          'You remain bound by those sites’ terms of use. These imports are offered to retrieve your own data; they may stop working if those sites change.',
        ],
      },
      {
        heading: 'Moderation',
        paragraphs: [
          'Reported content may be hidden or removed. An account that breaches these terms may be suspended or deleted, after a warning except in an emergency.',
        ],
      },
      {
        heading: 'Availability and warranties',
        paragraphs: [
          'The service is provided free of charge and “as is”, with no guarantee of availability or of being free of errors. Interruptions may happen, notably during updates.',
          'Export your library regularly (Settings › Data and account) if it matters to you.',
        ],
      },
      {
        heading: 'Ending the service',
        paragraphs: [
          'You can delete your account at any time from Settings › Data and account; your data is then erased as the privacy policy describes.',
          'Should the service close, readers would be told early enough to export their data.',
        ],
      },
      {
        heading: 'Changes',
        paragraphs: [
          'These terms may change. Any significant change will be shown to you the next time you sign in, and must be accepted to keep using the service.',
          `These terms are governed by French law. Contact: ${TODO('contact e-mail address')}.`,
        ],
      },
    ],
  },
  privacy: {
    title: 'Privacy policy',
    updated: 'Last updated: November 21, 2026',
    sections: [
      {
        heading: 'Data controller',
        paragraphs: [`${TODO('name or company name of the publisher')}, reachable at ${TODO('contact e-mail address')}.`],
      },
      {
        heading: 'Data collected',
        paragraphs: [
          'Your account: e-mail address, sign-in username, display name and, if you provide them, your profile picture, language, time zone and country. With Google or Apple, only the e-mail address is imported.',
          'Your library: the books and manga you add, their statuses, your progress, ratings, reviews, reading sheets, categories, wishlist, yearly goal and achievements.',
          'Your social life in the app: the readers you follow, those who follow you, the members you block, comments and notifications.',
          'What you send us: problem reports, with the technical log of the session they attach, and content reports.',
          'Technical logs (technical identifier, requests, errors) needed to run and secure the service. No advertising cookie, no third-party tracker, no audience measurement.',
        ],
      },
      {
        heading: 'Why, and on what basis',
        paragraphs: [
          'Providing the service you asked for by creating your account (performance of a contract): keeping your library, showing your profile to the readers you allow, sending you the notifications you turned on.',
          'Securing and running it (legitimate interest): technical logs, abuse limits, moderation of reported content.',
          'Nothing is sold, rented or used for advertising.',
        ],
      },
      {
        heading: 'Processors and recipients',
        paragraphs: [
          `Hosting: ${TODO('name and address of the host, country of the servers')}. Sign-in (Keycloak) and the database run on those same servers.`,
          'Google and Apple, only if you choose to sign in with them.',
          'Google Fonts serves the interface’s typefaces: your browser downloads them from Google’s servers, which then see your device’s IP address.',
          'The Open Library, AniList and BnF catalogs receive the title searches made by the server, with no data about you. Covers may be loaded straight from their servers, which then see your device’s IP address.',
          'Other readers only see the part of your activity your visibility settings open to them.',
        ],
      },
      {
        heading: 'How long it is kept',
        paragraphs: [
          'Your data is kept for as long as your account exists.',
          'When the account is deleted, your sign-in and all your data are erased immediately. The encrypted backups, kept off the server to rebuild the service after a disaster, hold a copy for up to six months before being pruned.',
          'Technical logs only hold your technical identifier and follow the host’s rotation.',
        ],
      },
      {
        heading: 'Your rights',
        paragraphs: [
          'You can access your data, correct it, object to its processing, ask for its restriction, its portability or its erasure.',
          'In the app: Settings › Profile to correct it, Settings › Data and account to export your whole library (portability) or delete your account (erasure).',
          `For any other request: ${TODO('contact e-mail address')}. You may also refer the matter to the CNIL (www.cnil.fr).`,
        ],
      },
      {
        heading: 'Security',
        paragraphs: [
          'Traffic is encrypted (HTTPS), every piece of data is tied to your account and only read on your behalf, and backups are encrypted.',
        ],
      },
    ],
  },
  notice: {
    title: 'Legal notice',
    updated: 'Last updated: November 21, 2026',
    sections: [
      {
        heading: 'Publisher',
        paragraphs: [
          `${TODO('name or company name, legal status, postal address')}.`,
          `Publication director: ${TODO('name')}.`,
          `Contact: ${TODO('contact e-mail address')}.`,
        ],
      },
      {
        heading: 'Host',
        paragraphs: [`${TODO('company name, address and phone number of the host')}.`],
      },
      {
        heading: 'Catalog data',
        paragraphs: ['Entries and covers from Open Library, AniList and the Bibliothèque nationale de France, under their respective licenses.'],
      },
    ],
  },
};

export default content;
