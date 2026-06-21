---
name: android-app-submission
description: End-to-end submission of a native Android app (AAB) to the Google Play Console for review and closed testing, driven through the Play Console web UI with Playwright MCP. Covers building/signing the release bundle, creating the app, store listing, ALL App content declarations (incl. Data safety for location), Store settings, creating a closed-testing track, adding the "All Testers" email list, selecting countries, uploading the AAB, and submitting for review. Includes the Misleading Claims (government-data source link) policy and a field-tested rejection-prevention checklist. Use when asked to "submit/publish an Android app", "push to Play Store", "closed test", or to fix a Play policy rejection.
license: MIT
metadata:
  version: "1.0.0"
---

# Android App Submission — Play Console (closed testing)

Submit a native Android app to **Google Play** for **review + closed testing**. There is no
simple public API for first-time app setup without a Play Developer **service account**, so
this workflow drives the **Play Console web UI with Playwright MCP**. Everything below was
validated end-to-end on a real submission (and a real rejection + fix).

Replace `<DEV_ID>` (developer account id) and `<APP_ID>` (numeric app id) in URLs with the
real values. The console base is
`https://play.google.com/console/u/0/developers/<DEV_ID>/app/<APP_ID>/...`.

## 0. Prerequisites (build a signed AAB first)

- **JDK 17+** (Android Studio bundles one at
  `/Applications/Android Studio.app/Contents/jbr/Contents/Home`).
- **Android SDK** (set `ANDROID_HOME=$HOME/Library/Android/sdk`), platform + build-tools 34/35.
- A **release keystore** (generate once, BACK IT UP — losing it means you can never update):
  ```bash
  "$JAVA_HOME/bin/keytool" -genkeypair -v -keystore keystore/<app>-release.jks \
    -alias <app> -keyalg RSA -keysize 2048 -validity 10000 \
    -storepass <pw> -keypass <pw> \
    -dname "CN=<Org>, O=<Org>, L=Singapore, C=SG"
  # PKCS12 forces key password == store password.
  ```
  Wire signing in `app/build.gradle.kts` from `local.properties` (NEVER commit keystore or
  `local.properties` — gitignore them). Build:
  ```bash
  export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
  export ANDROID_HOME="$HOME/Library/Android/sdk"
  ./gradlew :app:bundleRelease   # -> app/build/outputs/bundle/release/app-release.aab
  ```
  Verify it is signed: `jarsigner -verify .../app-release.aab` → "jar verified".
- **Google Maps key** (if used) in `local.properties` as `MAPS_API_KEY` (manifest placeholder);
  third-party data keys (e.g. `LTA_DATAMALL_ACCOUNT_KEY`) surfaced via `BuildConfig`.
- A **privacy policy URL** that is publicly reachable. Easiest: a `docs/privacy-policy.html`
  page served by **GitHub Pages** (`gh api -X POST repos/<owner>/<repo>/pages -f
  "source[branch]=main" -f "source[path]=/docs"`). Poll until HTTP 200 before using it.

## 1. Driving the console with Playwright MCP — gotchas

- **Sticky footer intercepts clicks.** The Save/Discard footer and asset side-panels
  overlay buttons. When a normal click times out with "subtree intercepts pointer events",
  click via JS instead: `browser_evaluate(() => document.querySelector('button[debug-id="..."]').click())`.
- **Asset uploads:** click the **Upload** button via JS (`debug-id="upload-button"`), then the
  MCP **file chooser** opens → `browser_file_upload([absolutePath])`. After it lands in the
  library it is auto-selected; click **Add** (`debug-id="add-to-content-button"`) via JS.
- **Material radios/checkboxes:** programmatic `.click()` usually works but `aria-checked`
  reads can be **stale** right after — re-snapshot to confirm. Prefer Playwright `nth()` clicks
  on `input[type=checkbox]` / `[role=radio]` when a label is empty in the a11y tree.
- **Text fields bound to Angular forms:** set them with **real typing** (`browser_type`),
  not by assigning `.value` in JS — programmatic value-set often does NOT register and the
  field silently saves empty (this bit us on the contact email). Verify by reloading the page.
- **Right URL for tracks:** the track list is `…/app/<APP_ID>/closed-testing` (NOT
  `…/tracks/closed-testing`, which throws a transient "unexpected error"). From there
  **Manage track** opens `…/tracks/<TRACK_ID>`.

## 2. Create the app

`…/developers/<DEV_ID>/create-new-app` → App name, package name (check availability),
default language, **App**, **Free**, tick both declarations (Program Policies + US export).
After "Create app", find the new id from the app list link `…/app/<APP_ID>/app-dashboard`.

## 3. Main store listing  `…/main-store-listing`

App name, **short description (≤80 chars)**, **full description (≤4000)**. Upload:
- **App icon 512×512** PNG (≤1 MB).
- **Feature graphic 1024×500** PNG/JPG (no alpha — flatten with PIL if needed).
- **2–8 phone screenshots**, 16:9 or 9:16, each side 320–3840 px (≥1080 px for promotion).
  Real device captures are best; clean SVG→PNG mockups of the actual UI are accepted as a
  starting point (`rsvg-convert`/PIL at 1080×1920). Tablet screenshots are NOT required to save.

> **Misleading Claims trap (see §8):** if the app surfaces **government data**, the full
> description AND the app itself must carry a clear, clickable link to the official source.

## 4. App content declarations  `…/app-content/overview`

All of these must be **Actioned** (10 items). Direct slugs:
`privacy-policy`, `ads-declaration`, `testing-credentials` (App access / "Sign in details"),
`content-rating-overview`, `target-audience-content`, `data-privacy-security` (Data safety),
`ad-id-declaration`, `government-apps`, `finance`, `health`.

- **Privacy policy:** paste the public URL.
- **Ads:** "No, my app does not contain ads" (unless it does).
- **Sign in details / App access:** "No" if nothing is gated behind login.
- **Content rating:** Start questionnaire → email + category **All Other App Types** + accept
  IARC terms → answer **No** to every content question for a utility app → Save → Next → Save.
- **Target audience:** for an adult utility pick **18 and over** only (skips the child-safety
  sub-steps) → Save.
- **Advertising ID:** "No" if you don't use AD_ID (don't include `play-services-ads`).
- **Government apps / Financial features / Health:** select "does not provide / not a
  government app / no health features".
- **Data safety** (the long one): "Does your app collect or share required data types?" →
  **Yes** if you read **location** (it is sent to Maps SDK/geocoder). Then:
  - Encrypted in transit → **Yes**; account creation → "**does not allow users to create an
    account**"; "Can users log in with external accounts?" → **No**; data deletion (optional) → No.
  - **Data types:** check **Approximate location** + **Precise location**.
  - **Data usage** → open each location type → **Collected** (not Shared) → processed
    **ephemerally: Yes** → **Users can choose** (optional, since search-by-postcode works
    without permission) → purpose **App functionality** → Save. Repeat for both.
  - Preview → Save.

## 5. Store settings  `…/store-settings`  (a real publish blocker)

Set the **App category** (e.g. *Maps & Navigation*) AND **Store listing contact details →
Email address** (required). The dashboard "Provide app information" checklist will sit at
"10 of 11 complete" and the release will error "**Your app cannot be published yet. Complete
the steps on the Dashboard**" until BOTH are set. Type the email with real keystrokes and
**reload to confirm it persisted** (JS value-set silently fails here).

## 6. Closed testing track  `…/closed-testing` → Manage track

1. **Create release** → upload `app-release.aab` (Play App Signing is on by default), set a
   release name and **release notes** (`<en-US>…</en-US>`).
2. **Testers tab** → either reuse an existing email list (e.g. **"All Testers"**) by ticking
   it, or **Create email list** ("Add email addresses" field needs real typing + Enter to
   chip the address) → confirm "Create" → tick the list → **Save**.
   - To satisfy the user's standard ask, **add the "All Testers" list** to every closed track.
3. **Countries / regions tab** → **Add countries / regions** → search & tick (e.g. Singapore,
   or select-all) → **Save**.

## 7. Send for review

Release **Review** step: the only enabled action is **Save** (other unreviewed changes are
batched). Save → a dialog offers **Go to overview** → on **Publishing overview** click
**Submit N changes for review** → confirm **Send changes for review**. Pre-checks run
(~14 min) then it goes to Google review (typically hours–7 days). Two warnings are harmless:
*no deobfuscation file* and *no native debug symbols*.

After approval, share the **opt-in URL** (Closed testing → track → Testers) with testers; they
must accept before installing from Play.

## 8. Misleading Claims policy — government data (most common rejection here)

**Symptom:** Policy status → "App rejected — Misleading Claims policy" → detail:
*"Missing Source Link for Government Information … your app provides government information but
lacks one or more clear and accessible URL/link(s) to the original source(s)."* Evidence is
usually the **Full description**.

**Why:** LTA DataMall, data.gov.sg, OneMap, HDB, URA, MOH, etc. are **government** sources.
Any app surfacing that data — affiliated or not — must clearly cite the **official source with
a working link**, both in the listing and **inside the app**.

**Fix (do all three):**
1. **Full description** — add an explicit line, e.g.
   *"Data source: Singapore Land Transport Authority (LTA) DataMall —
   https://datamall.lta.gov.sg/. This app is not affiliated with or endorsed by LTA."*
2. **In the app** — show a tappable source link on a visible surface (an **About tab** is
   ideal; see the companion `android-feedback-about` skill) AND/or a small attribution on the
   main screen. Link to the real source (e.g. `https://datamall.lta.gov.sg/content/datamall/en.html`).
3. Bump **versionCode**, rebuild the AAB, create a **new release** on the track, and resubmit.

## 9. Rejection-prevention checklist (run BEFORE submitting)

- [ ] **Government/official data?** → source link in description **and** in-app (About tab). #1 cause here.
- [ ] Screenshots reflect the **actual app** (no fabricated claims, no competitor brands, no device frames implying another OS).
- [ ] Description has **no unverifiable superlatives** ("official", "#1", "best") unless true and substantiated.
- [ ] **Privacy policy URL** is live (HTTP 200) and describes the data the app actually touches.
- [ ] **Data safety** matches reality — if you request `ACCESS_FINE_LOCATION`, declare location (under-declaring = rejection).
- [ ] **App category + contact email** set in Store settings (else "cannot be published").
- [ ] **Permissions** are all used; remove unused ones. No `AD_ID` permission unless you show ads (and then declare ads).
- [ ] Target **API level** meets the current Play minimum (targetSdk 34/35).
- [ ] App **runs without crashing** on first launch with no account (reviewers use a clean device).
- [ ] If anything is gated, fill **Sign in details** with working test credentials.
- [ ] Content rating answers are **truthful**; 18+ target audience for adult utilities.
- [ ] Back up the **keystore + passwords** somewhere safe and out of git.

## Resume / iterate

The console autosaves drafts. To fix a rejection: make the change, **bump versionCode**,
`./gradlew :app:bundleRelease`, upload as a **new release** on the same track, and
**Submit changes for review** again from Publishing overview.
