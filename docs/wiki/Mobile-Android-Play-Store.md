# Publishing the Android app on Google Play

Everything the repository can do is done: a `vX.Y.Z` tag builds a **signed app bundle** and a
signed APK (`.github/workflows/mobile-release.yml`), the app wears its own icon and splash
screen, and the store artwork is in `apps/mobile/store/`. What is left needs a human with a
Google account and a payment card, and this page is the order to do it in.

Read it once from end to end before starting: two of the steps below **cannot be undone**.

## 0. What you need before anything else

| | What | Cost | Lead time |
|---|---|---|---|
| Google Play developer account | A Google account, a payment card, and an identity document | **25 USD once**, no renewal | Identity verification is usually a few days, and can reach two weeks |
| A privacy policy on a public URL | A page Play can reach without signing in | — | Written as part of [#74](https://github.com/zelytra/Librarius/issues/74) |
| The release keystore | Created with `keytool`, backed up outside the repository | — | Minutes; see `.claude/docs/MOBILE.md` § 8 |
| Screenshots | Two to eight per form factor, from a real run | — | Minutes, on a phone or at phone width in a browser |

One rule shapes the whole schedule: a **personal** developer account registered after
13 November 2023 must run a closed test with **at least 12 testers opted in continuously for
14 days** before it may even apply for production access. The 14 days are consecutive — a
tester who opts out restarts their own clock — and an account registered as an organisation
is exempt. Line the twelve up before cutting the tag: they, not the build, decide when the
app can be published.

## 1. Create the keystore, before anything else

`.claude/docs/MOBILE.md` § 8 has the exact commands and the four repository secrets to
create: `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`,
`ANDROID_KEY_PASSWORD`. Until they exist, `mobile-release` skips signing and says so in a
warning annotation rather than failing.

**This keystore is the upload key.** Lose it and you can ask Google to reset it (Play App
Signing, below) — lose it *without* Play App Signing and the application can never be updated
again under its identity. Back it up outside this repository.

## 2. Create the application in the Play Console

1. **Play Console → Create app.** Name `Librarius`, default language French (France), type
   *App*, *Free*. The free/paid choice **cannot be changed** once the app is published.
2. Note the **package name**: `fr.zelytra.librarius`. It comes from
   `apps/mobile/capacitor.config.ts` and `apps/mobile/android/app/build.gradle`, and it can
   never be changed afterwards — a different package name is a different application.
3. **Keep Play App Signing on** (it is on by default and effectively mandatory for a new
   app). Google holds the key that signs what users install; the keystore from step 1 is only
   the *upload* key, which Google can reset if you lose it. This is the safety net that makes
   step 1 recoverable.

## 3. Fill the store listing

The French copy below is the application's own, and English is the second locale the app
ships (`apps/web/src/i18n/locales/`); add both.

| Field | Value |
|---|---|
| App name (30 chars) | `Librarius` |
| Short description (80) | `Votre bibliothèque de livres et de mangas, volume par volume.` |
| Full description (4000) | What the app does: collection down to the volume and the edition, reading progress, wishlist and budget, catalog search across providers, imports from Booknode, Babelio and Mangacollec, statistics, reading calendar, and the social feed. No marketing claim the app does not deliver — Play rejects a listing whose screenshots or text do not match the app. |
| Category | `Books & Reference` |
| App icon | `apps/mobile/store/icon-512.png` |
| Feature graphic | `apps/mobile/store/feature-graphic.png` |
| Phone screenshots | 2 to 8, 16:9 or 9:16, each side between 320 px and 3840 px. **Not in the repository** — see below |
| Contact e-mail, website | The owner's address, and <https://librarius.zelytra.fr> |
| Privacy policy URL | The page from [#74](https://github.com/zelytra/Librarius/issues/74) |

**Screenshots.** Shoot them from a signed-in app with a real library, not from an empty one:
the home dashboard, the collection, a title's detail, the statistics, and the scanner are the
five that show what the product is. On a phone, install the APK from the tagged run and use
the device's own screenshot. In a browser, open <https://librarius.zelytra.fr> at 390 × 844
with the device toolbar and capture the frame.

## 4. Answer the content declarations

Play will not let a release out before these are complete. Answer them from what the code
actually does:

- **Data safety.** Librarius collects and stores, tied to an account:
  - *Account*: e-mail address and display name, held by Keycloak (`infra/keycloak/`), used
    for sign-in only. Collected, not shared, encrypted in transit, deletable by the user
    (the account deletion endpoint, `AccountDeletionTest`).
  - *App activity*: the library, reading progress, ratings, reviews, wishlist and the social
    feed — the product itself. Collected, not shared, user-deletable with the account.
  - *Photos*: **not collected.** The scanner reads a barcode through the camera and keeps no
    image; the permission is declared in `AndroidManifest.xml` with
    `android.hardware.camera` as *not required*.
  - *Crash and diagnostic data*: the error reports of
    [#493](https://github.com/zelytra/Librarius/issues/493) — a message, a stack trace, the
    user agent and the recent in-app log — stored on the API, visible to the administrator.
    Declare them as *Crash logs* and *Diagnostics*, collected and not shared.
- **Ads**: none. The app contains no advertising SDK.
- **Content rating**: fill the IARC questionnaire honestly — a library manager with
  user-written reviews and a social feed is "user-generated content", which raises the rating
  and requires a way to report content. The report button of #493 is that way.
- **Target audience**: 13 and over is the honest answer for an account-based social app;
  anything under 13 pulls in the Families policy and a much longer review.
- **Government app / financial features / health**: no to all.

## 5. Upload the first build

1. Cut the tag the milestone deserves (`.claude/docs/CONVENTIONS.md` § "When a tag is cut").
2. Open the `mobile-release` run for that tag and download the
   **`librarius-play-bundle-<version>`** artifact: `app-release.aab`. The other artifact, the
   APK, is for sideloading — Play refuses it.
3. **Internal testing → Create new release → upload the `.aab`.** Internal testing reaches up
   to 100 testers you name, with no review wait: it is where you check the app on a real
   device before anyone else sees it.
4. Write the release notes in French and English. They are not generated from the changelog.
5. Promote to **closed testing** once it works, which is where the 12-testers-for-14-days
   requirement is satisfied, then to production.

The version numbers come from the tag and need no editing: `versionName` is the semantic
version, `versionCode` the integer `mobile-release.yml` derives from it (major × 1 000 000 +
minor × 1 000 + patch). Play refuses a `versionCode` it has already seen, so never re-upload a
build from the same tag.

## 6. Afterwards

- A release is **reviewed** by Play — hours to a few days for the first one, usually faster
  after that.
- Every update repeats step 5 only: new tag, new bundle, new release.
- **Keep the target API level current.** Play has required API 36 of every new app and every
  update since 31 August 2026, and raises the floor each year: a build that falls behind is
  refused at upload. The level lives in `apps/mobile/android/variables.gradle`, and raising it
  usually means moving Capacitor itself — [#524](https://github.com/zelytra/Librarius/issues/524)
  is the precedent to follow.

## What is deliberately not automated

Uploading the bundle from CI is possible — a Play service-account JSON in a secret, and an
action that pushes the artifact onto a track. It is not set up, for one reason: nothing about
this product needs a release every day, and the account that could publish silently is a
credential with real blast radius. The manual upload is three clicks on a release the owner
has already decided to cut. If that changes, the shape to follow is `mobile-release.yml`'s:
the step is skipped with an annotation while the secret is absent, never red.
