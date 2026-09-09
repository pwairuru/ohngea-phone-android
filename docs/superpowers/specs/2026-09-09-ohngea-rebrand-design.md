# Ohngea Phone Rebrand — Design Spec

Date: 2026-09-09 | Branch: `ohngea-rebrand` (off `master`) | Upstream sync: merge, never rebase

## Goal

Rebrand linphone-android as **Ohngea Phone**, SIP client for FusionPBX
(`/home/wairuru/projects/fusionpbx/`). Shorter onboarding (direct to
third-party SIP login), Ohngea colors + icon, Opus-first codecs.
Keep merging upstream `master` with minimal conflicts.

## Approach (chosen)

Option A: applicationId + display-name rename, keep `org.linphone`
Kotlin package and Gradle `namespace`. Full source move rejected
(500+ files, poisons merges). Product-flavor split rejected (overkill).

## Changes

### 1. Identity

- `app/src/main/res/values/strings.xml:4`: `<!ENTITY appName "Linphone">` → `"Ohngea Phone"`.
  Drives `app_name`, notification titles, all `&appName;` usages.
- `app/build.gradle.kts:17,106`: `packageName` → `com.ohngea.phone`.
  `file_provider`, `appAuthRedirectScheme`, `linphone_openid_callback_scheme`
  follow automatically (derived from package).
- Scrub user-visible `linphone.org` defaults:
  - `assets/linphonerc_default`: `account_creator` url, `contacts_filter`,
    log/file-transfer URLs (point at Ohngea infra or generic).
  - `assets/assistant_third_party_default_values`: `stun_server`
    `stun.linphone.org` → host from Ohngea infra; if none exists yet,
    keep `stun.linphone.org` temporarily and file follow-up (release check).
  - Privacy/terms URLs in strings + Landing consent flow.
- Keep GPL copyright headers (license obligation).
- No SIP domain prefill (user decision); generic empty form stays.

### 2. Onboarding (keep carousel, direct to third-party)

Current: `MainActivity` first-launch → `WelcomeActivity` (3 pages) →
`AssistantActivity` starting at `landingFragment` (Linphone login /
register / recover / QR / third-party via warning screen).

New: Welcome carousel (rebranded copy/art) → `SKIP_LANDING_EXTRA` →
`AssistantActivity` with `startDestination = thirdPartySipAccountLoginFragment`.

- `app/src/main/res/navigation/assistant_nav_graph.xml:6`: start destination
  → `thirdPartySipAccountLoginFragment`.
- Remove/orphan: `landingFragment` actions to Register/Recover/Linphone-login;
  drop `thirdPartySipAccountWarningFragment` step (direct login).
- Keep: Permissions global action, QR scanner (provisioning), Help.
- Re-attach conditions/privacy acceptance (today gated in `LandingFragment`)
  to login screen or first Welcome page so consent isn't lost.
- Delete dead fragments (`LandingFragment`, `RegisterFragment`,
  `RecoverAccountFragment`, `AccountLoginViewModel` paths) only after
  nav graph no longer references them; keep QR + third-party files.

### 3. Colors / theme

Ohngea web theme (`frontend/src/index.css` `.theme-ohngea`):
primary teal `hsl(174,72%,40%)` ≈ `#1DA795`, ring amber `#F59E0B`
(logo `frontend/public/logo.svg` uses `#14B8A6` + `#F59E0B`).

- `values/colors.xml`: add `ohngea_teal_500` (≈ `#1DA795`) + dark variant,
  `ohngea_amber_500` (`#F59E0B`).
- `values/themes.xml:18`: `color_main1_500` → `@color/ohngea_teal_500`
  (keep `orange_*` names as aliases where referenced widely to cut diff).
- Night theme: brighter teal; warnings/focus use amber.
- Verify light + dark manually.

### 4. Icon

Source `frontend/public/logo.svg` / `icon-512.png` (teal/amber speech bubble).

- Generate adaptive icon (foreground = logo mark, background = dark teal).
- Replace `mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher*.png`
  + `mipmap-anydpi/ic_launcher*.xml`. Round icon same asset.
- Verify on API 28 and 33+ launchers.

### 5. Codecs (first-launch only)

Priority: Opus > Speex (wideband 32000, then 16000, 8000 narrow) > rest.

- Add explicit `[audio_codec_N]` sections to `assets/linphonerc_default`
  in priority order, all enabled. First launch copies asset → user config;
  later user toggles persist untouched.
- MUST verify section format against SDK: dump live config via
  `HelpViewModel` linphonerc export path and match key names
  (`mime`, `rate`, `channels`, `enabled`).
- Fallback if asset sections ignored: one-time in-code ordering in
  `CorePreferences` first-launch path guarded by new prefs flag.

### 6. Upstream sync

- Work on `ohngea-rebrand`. One commit per subsystem
  (identity, onboarding, theme, icon, codecs).
- Sync: `git merge master` into branch. Never rebase published branch.

## Testing

- Clean install: Welcome → (Skip/Start) → third-party login, no Linphone screens.
- Fresh-config dump confirms Opus first, Speex next.
- Light/dark theme pass; launcher icon API 28 + 33+.
- `assembleDebug` + `ktlint` clean.

## Out of scope

- SIP domain/transport prefill; backend/provisioning changes;
  Play Store listing; FCM sender migration (note: `google-services.json`
  must be re-issued for `com.ohngea.phone` before release).
