# Building and publishing the iOS app without a Mac

Librarius has no Apple hardware behind it beyond the owner's iPhone, and everything Apple
requires to ship an app — `xcodebuild`, the code-signing tools, the uploader — runs on macOS
only. This page is the way around that: **rent the Mac by the minute in CI, and let Apple
itself issue the signing material**.

Nothing here needs a Mac of your own. It does need a paid Apple Developer Program membership,
and an iPhone to install the result.

## Before paying anything: the PWA already installs on an iPhone

Librarius is a progressive web app. Open <https://librarius.zelytra.fr> in Safari, *Share →
Add to Home Screen*, and it sits on the home screen with its own icon, full screen, with no
Apple account and no build at all. What that version does **not** get is the native shell's
own capabilities — the barcode scanner through the camera plugin, native push
([#69](https://github.com/zelytra/Librarius/issues/69)) and anything else a plugin adds — and
it cannot be found on the App Store.

Everything below is for the native application. If the only goal is "the app on my iPhone",
the paragraph above costs nothing and takes a minute.

## Installing on an iPhone without the App Store

Three ways, and only the first costs nothing.

### 1. Sideload an unsigned build with a free Apple ID — 0 €

Every tag publishes **`librarius-<version>-ios-unsigned.ipa`** among the files of its GitHub
release (#541) — and as the `librarius-ios-unsigned-<version>` workflow artifact — an `.ipa`
built without a signing identity, for as long as the Apple secrets are absent. It cannot be installed as it is: a
sideloading tool re-signs it with a free Apple ID and installs it over USB or Wi-Fi.

- **Sideloadly** runs on Windows and macOS and is the simplest from a Windows machine.
- **AltStore** / **SideStore** do the same and can refresh the signature by themselves while
  the phone and the computer are on one Wi-Fi network, which matters because of the next
  point.

What Apple's free tier costs instead of money:

| | Free Apple ID | Paid membership |
|---|---|---|
| Signature lifetime | **7 days**, then the app refuses to open until it is re-signed | 1 year |
| Applications at once | **3** per Apple ID | no such limit |
| Needs a computer | yes, at least for the first install | no (TestFlight) |

On iOS 16 and later the phone also asks for **Developer Mode** (Settings → Privacy &
Security → Developer Mode) before a sideloaded app will launch, and the developer profile has
to be trusted in Settings → General → VPN & Device Management.

This is Apple's own "free provisioning", used through a third-party tool rather than Xcode —
nothing is jailbroken and nothing is circumvented. The tools are not Apple's, though: read
what they do before handing them an Apple ID, and prefer an Apple ID that is not the one
holding your main iCloud account.

### 2. Ad hoc distribution — needs the 99 USD membership

The membership lets you register device UDIDs (**100 per product family per membership year**)
and sign a build for exactly those devices. The `.ipa` is then installed directly — by link,
by email, by a tool — with **no App Review and no store**, and the signature lasts a year.
This is the paid version of what you are asking for, and it is what `ios-release.yml` would
produce with `method: ad-hoc` instead of `app-store-connect` in
`apps/mobile/ios-export-options.plist`.

### 3. TestFlight — needs the same membership

Not a store listing, but it does go through App Store Connect. **Internal** testers (up to 100
people on the team) get a build with no review, minutes after the upload; builds expire after
90 days. It is what `ios-release.yml` does today, and the least work once the membership
exists.

## What it costs, and how long it takes

| | What | Cost | Lead time |
|---|---|---|---|
| Apple Developer Program | Individual membership, renewed yearly | **99 USD / year** | Enrolment is usually same-day; identity checks can take a few days |
| GitHub-hosted macOS runner | `macos-latest`, used by two workflows | **Free** on a public repository. On a private one, macOS minutes bill at 10× the Linux rate | A build is a handful of minutes |
| TestFlight | Included in the membership | — | Internal testers (up to 100, your own account included) get a build **minutes** after upload, with no review |
| App Store review | Included | — | Typically 24–48 hours for the first submission |

Enrolment can be done from the iPhone itself, with the *Apple Developer* app, which is the
only part of this that needs an Apple device at all. An individual membership publishes under
your own legal name; an organisation membership needs a D-U-N-S number and takes longer.

## How the pieces fit

```text
tag vX.Y.Z ──▶ ios-release.yml on macos-latest
                 │  pnpm mobile:build        the same web bundle as Android
                 │  xcodebuild archive       -allowProvisioningUpdates + API key
                 │  xcodebuild -exportArchive  destination: upload
                 ▼
            App Store Connect ──▶ TestFlight ──▶ the owner's iPhone
```

Two choices are worth understanding before touching any of it.

**No certificate is stored anywhere.** The usual iOS pipeline keeps a `.p12` certificate and
a provisioning profile in secrets (or in a private repository, through `fastlane match`), both
of which are normally created on a Mac. Instead, `xcodebuild` is given
`-allowProvisioningUpdates` and an **App Store Connect API key**, and asks Apple for the
distribution certificate and the matching profile at build time. There is nothing to create
on a Mac, nothing to renew by hand, and nothing to leak.

**The project is committed, not generated on every build.** `apps/mobile/ios/` is a real
directory in this repository, like `apps/mobile/android/`, for the reasons
`.claude/docs/MOBILE.md` § 7 gives: the hand edits (the OAuth URL scheme, the camera usage
description) live in files `cap add` writes once, and regenerating from scratch would discard
them every time.

## Step 1 — create the project, once

`cap add ios` needs macOS, so it runs in CI too:

1. **Actions → `ios-bootstrap` → Run workflow.** It installs the dependencies, builds the web
   bundle, runs `cap add ios`, applies the hand edits
   (`apps/mobile/scripts/patch-ios-project.sh`), **compiles the result unsigned**
   (`CODE_SIGNING_ALLOWED=NO`) and uploads it as the `librarius-ios-project` artifact.
2. Commit that directory to a branch and open a pull request — or tick *push_branch* when
   launching the workflow and it pushes `ios-bootstrap-<run number>` for you.
3. Merge it. From then on `ios-release` has something to build, and `cap sync` keeps the web
   bundle and the plugin list in step on every run.

That unsigned compile is **the free way to test the iOS build**: it needs no Apple account, no
certificate and no device, and it fails on anything wrong with the scaffold, the Swift
packages or a plugin. What it cannot tell you is whether the app *runs* — for that the build
has to be signed and installed, which is the paid half below.

Run this workflow again only when `@capacitor/ios` moves. It refuses to run while
`apps/mobile/ios` exists, because `cap add` overwrites hand edits.

Capacitor 8 scaffolds the project with **Swift Package Manager**, not CocoaPods: there is no
`Podfile`, no `App.xcworkspace`, and `xcodebuild -project App.xcodeproj` is the right
invocation.

## Step 2 — the Apple side

1. **Enrol** in the Apple Developer Program (<https://developer.apple.com/programs/>, or the
   Apple Developer app on the iPhone).
2. **App Store Connect → Apps → New App.** Platform iOS, name `Librarius`, primary language
   French, bundle ID `fr.zelytra.librarius` — the identifier from
   `apps/mobile/capacitor.config.ts`, which has to be registered first under *Certificates,
   Identifiers & Profiles → Identifiers*. It can never be changed afterwards.
3. **Create an API key**: *Users and Access → Integrations → App Store Connect API → Team
   keys → +*. Give it the **Admin** role — cloud-managed distribution signing is refused to
   App Manager and Developer keys. Download the `.p8` **once**: Apple never shows it again.
4. Note the **Key ID**, the **Issuer ID** (above the key list) and your **Team ID**
   (*Membership details*).

## Step 3 — the four repository secrets

GitHub → Settings → Secrets and variables → Actions → New repository secret:

| Secret | Contents |
|---|---|
| `APPLE_ASC_KEY_ID` | The key's ID, e.g. `2X9R4HXF34` |
| `APPLE_ASC_ISSUER_ID` | The issuer UUID of the team's API keys |
| `APPLE_ASC_KEY_P8_BASE64` | The whole `.p8` file, base64-encoded (`base64 AuthKey_XXXX.p8`) |
| `APPLE_TEAM_ID` | The ten-character team identifier |

They are read only by `ios-release.yml`, decoded into `$RUNNER_TEMP` — never into the
checkout — and removed at the end of the job. Until all four exist, the workflow does
everything it can and then skips the signing steps with a warning annotation, so a tag never
goes red over a pending manual step.

## Step 4 — release

Cut a `vX.Y.Z` tag on `main`, as for every other release.

**Before the membership is paid for**, that tag still builds: `ios-release` compiles the app
unsigned and says in a warning annotation that nothing was uploaded. A release candidate
therefore checks the iOS build without an Apple account — it simply cannot produce anything
installable.

**Once the four secrets exist**, `ios-release` archives the app,
signs it, and uploads it straight to App Store Connect (`destination: upload` in
`apps/mobile/ios-export-options.plist` — no `altool`, no Transporter). The build appears in
TestFlight a few minutes later, after Apple's automatic processing.

- `CFBundleShortVersionString` is the tag's version, `CFBundleVersion` the same integer
  Android's `versionCode` uses, so a release carries one build number everywhere. App Store
  Connect refuses a build number it has already seen.
- **Install it on the iPhone**: TestFlight app → the build appears under internal testers.
  This is also the only way to run the app on a device without Xcode.
- **Submit to the App Store** from App Store Connect when the build is worth publishing:
  screenshots (6.7" and 6.1" iPhone), description, keywords, support URL, privacy policy URL
  and the *App Privacy* questionnaire — the same answers as the Play data-safety form, which
  [Publishing the Android app](https://github.com/zelytra/Librarius/wiki/Mobile-Android-Play-Store)
  lists endpoint by endpoint.

## What only a device can confirm

Everything above is written from the Capacitor 8 scaffold and Apple's documented behaviour;
**no part of it has run yet**, because it cannot run from Linux. The first `ios-bootstrap`
run is the real test of step 1, and the first tag the real test of steps 2 to 4. Expect to
fix something on the first attempt.

Beyond the build, these are the behaviours [#71](https://github.com/zelytra/Librarius/issues/71)
keeps open, and which only a run on a real iPhone can answer:

- sign-in through `ASWebAuthenticationSession` and the callback on
  `fr.zelytra.librarius://auth` ([#528](https://github.com/zelytra/Librarius/issues/528));
- the barcode scanner's camera permission and its French usage description;
- the safe areas around the notch and the home indicator
  ([#529](https://github.com/zelytra/Librarius/issues/529));
- API calls from the `capacitor://localhost` origin, which the API's CORS list allows
  ([#527](https://github.com/zelytra/Librarius/issues/527)).
