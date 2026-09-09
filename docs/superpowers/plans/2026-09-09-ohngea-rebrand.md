# Ohngea Phone Rebrand Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebrand linphone-android as Ohngea Phone with direct third-party SIP onboarding, Ohngea colors/icon, and Opus-first codecs.

**Architecture:** Config-first rebrand on branch `ohngea-rebrand`. Upstream already ships assistant-hiding flags (`assistant_go_directly_to_third_party_sip_account_login`, `assistant_hide_create_account`), so onboarding is mostly `[ui]` config + nav-graph pruning. One commit per task.

**Tech Stack:** Kotlin, Android Gradle, Navigation component, linphonerc config assets.

**Spec:** `docs/superpowers/specs/2026-09-09-ohngea-rebrand-design.md`

## Global Constraints

- Branch: `ohngea-rebrand`. Upstream sync via `git merge master`, never rebase.
- Keep `org.linphone` Kotlin package and Gradle `namespace`; only `applicationId` changes.
- Keep GPL copyright headers.
- No SIP domain/transport prefill (generic empty form stays).
- `google-services.json` must be re-issued for the new applicationId before release (noted, not done here).

---

### Task 1: App identity (name, applicationId, URL scrub)

**Files:**
- Modify: `app/src/main/res/values/strings.xml:4` (appName entity)
- Modify: `app/build.gradle.kts:17` (packageName)
- Modify: `app/src/main/assets/linphonerc_default` (`[ui]` contacts_filter, `[account_creator]` url, log/file-transfer URLs)
- Modify: `app/src/main/res/values/strings.xml:13-14,35-44,432` (linphone.org URLs)

**Interfaces:**
- Consumes: nothing.
- Produces: `applicationId = com.ohngea.phone`; display name `Ohngea Phone` via `&appName;`.

- [ ] **Step 1: Change appName entity**

In `app/src/main/res/values/strings.xml`, replace:
```xml
<!ENTITY appName "Linphone">
```
with:
```xml
<!ENTITY appName "Ohngea Phone">
```

- [ ] **Step 2: Change applicationId**

In `app/build.gradle.kts`, replace:
```kotlin
val packageName = "org.linphone"
```
with:
```kotlin
val packageName = "com.ohngea.phone"
```
`file_provider`, `appAuthRedirectScheme`, `linphone_openid_callback_scheme` derive from `packageName` already — verify by grep, change nothing else.

- [ ] **Step 3: Scrub linphone.org service URLs**

In `app/src/main/assets/linphonerc_default`, replace:
```
contacts_filter=sip.linphone.org
```
with:
```
contacts_filter=
```
and replace `url=https://subscribe.linphone.org/api/` with `url=` (empty — no account creator in third-party-only flow). Leave log/file-transfer URLs (`files.linphone.org`) intact unless Ohngea hosts equivalents (they are functional defaults, not branding).

In `app/src/main/res/values/strings.xml`, add one canonical host string:
```xml
<string name="ohngea_website_url" translatable="false">https://ohngea.com</string>
```
(owner to correct the domain if different — noted in commit message).
Repoint brand-identity URLs at it, preserving paths:
- `assistant_web_platform_link`, `assistant_linphone_contact_us_link`, `website_contact_url`, `website_download_url`, `website_user_guide_url`, `website_privacy_policy_url`, `website_terms_and_conditions_url`, `web_platform_register_email_url`, `web_platform_forgotten_password_url`, `website_translate_weblate_url`, `website_open_source_licences_usage_url`, `conversation_end_to_end_encrypted_bottom_sheet_link`, `manage_account_dialog_remove_account_message` (references `https://sip.linphone.org`).

- [ ] **Step 4: Verify build config**

Run: `./gradlew :app:assembleDebug -x ktlintFormat --offline` (drop `--offline` if dependencies missing; expect success).
Then verify:
```bash
rg -n "org\.linphone" app/build.gradle.kts | head -5
aapt dump badging app/build/outputs/apk/debug/*.apk | rg -i "package:|application-label:"
```
Expected: `package: name='com.ohngea.phone'`, `application-label:'Ohngea Phone'`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/res/values/strings.xml app/build.gradle.kts app/src/main/assets/linphonerc_default
git commit -m "feat: rebrand app identity to Ohngea Phone"
```

---

### Task 2: Direct third-party onboarding

**Files:**
- Modify: `app/src/main/assets/linphonerc_default` (add `[ui]` flags)
- Modify: `app/src/main/res/navigation/assistant_nav_graph.xml` (startDestination, prune actions)
- Modify: `app/src/main/java/org/linphone/ui/assistant/fragment/ThirdPartySipAccountLoginFragment.kt` (consent dialog on first show)
- Delete (only if unreferenced after nav edit): `LandingFragment.kt`, `RegisterFragment.kt`, `RegisterCodeConfirmationFragment.kt`, `RecoverAccountFragment.kt`, `AccountLoginViewModel.kt`, layouts `assistant_landing_fragment.xml`, `assistant_register_fragment.xml`, `assistant_register_confirm_sms_code_fragment.xml`, `assistant_recover_account_fragment.xml`
- Modify: `app/src/main/res/layout/welcome_page_*.xml` (rebrand copy)

**Interfaces:**
- Consumes: Task 1 (privacy/terms URLs now Ohngea-hosted, used by consent dialog).
- Produces: cold start with no account lands on third-party login; no Linphone-account screens reachable.

- [ ] **Step 1: Set assistant flags in default config**

Append to `[ui]` section of `app/src/main/assets/linphonerc_default`:
```
assistant_go_directly_to_third_party_sip_account_login=1
assistant_hide_create_account=1
```
These drive `CorePreferences.assistantDirectlyGoToThirdPartySipAccountLogin` (`CorePreferences.kt:470`) and `hideAssistantCreateAccount` (`CorePreferences.kt:438`), which auto-fire `skipLandingToThirdPartySipAccountEvent` in `AccountLoginViewModel.kt:139`. Keep `assistant_disable_qr_code=0` (QR provisioning stays).

- [ ] **Step 2: Make third-party login the nav start destination**

In `assistant_nav_graph.xml`, change `app:startDestination="@id/landingFragment"` to `app:startDestination="@id/thirdPartySipAccountLoginFragment"`. Remove the `landingFragment` node and all actions pointing at `registerFragment`, `recoverAccountFragment`, `landingFragment`, and `thirdPartySipAccountWarningFragment` (warning step skipped: direct login). Keep `qrCodeScannerFragment`, `permissionsFragment` (+global action), `helpFragment`, `debugFragment`.

- [ ] **Step 3: Re-attach conditions/privacy consent to login fragment**

`LandingFragment.showAcceptConditionsAndPrivacyDialog` is orphaned by Step 2. In `ThirdPartySipAccountLoginFragment.onViewCreated`, if `!corePreferences.conditionsAndPrivacyPolicyAccepted`, show the same dialog via `DialogUtils.getAcceptConditionsAndPrivacyDialog` + `AcceptConditionsAndPolicyDialogModel`, setting `corePreferences.conditionsAndPrivacyPolicyAccepted = true` on accept (mirror `LandingFragment.kt:179-226`). Gate the login button behind acceptance.

- [ ] **Step 4: Delete dead screens**

Delete the files listed above only after `rg -l "landingFragment|RegisterFragment|RecoverAccountFragment|AccountLoginViewModel" app/src/main --glob '*.kt' --glob '*.xml'` returns no references outside the deleted files themselves.

- [ ] **Step 5: Rebrand Welcome carousel copy**

Update `welcome_page_1/2/3.xml` texts/drawables referencing Linphone to Ohngea Phone. Keep 3-page flow; Skip/Start already route to Assistant with `SKIP_LANDING_EXTRA`.

- [ ] **Step 6: Verify**

Run: `./gradlew :app:assembleDebug` (expect success, no `unresolved reference` for deleted fragments).
Run: `rg -n "landingFragment|subscribe\.linphone|sip\.linphone\.org" app/src/main/java app/src/main/res/navigation || echo CLEAN` — expect CLEAN.
Manual: clean install → Welcome → Start → third-party SIP login visible, no register/recover/Linphone-login screens.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/assets/linphonerc_default app/src/main/res/navigation/assistant_nav_graph.xml app/src/main/java/org/linphone/ui/assistant app/src/main/res/layout/welcome_page_*.xml
git commit -m "feat: direct third-party SIP onboarding, drop Linphone account flow"
```

---

### Task 3: Ohngea color theme

**Files:**
- Modify: `app/src/main/res/values/colors.xml` (add teal + amber)
- Modify: `app/src/main/res/values/themes.xml:17-18,72` (repoint primary + outgoing bubble)
- Modify: `app/src/main/res/drawable/linphone_splashscreen.xml` (or current splash drawable name — check `res/drawable` for splash asset) only if it hardcodes orange.

**Interfaces:**
- Consumes: nothing.
- Produces: `color_main1_500` = Ohngea teal in light and night themes.

- [ ] **Step 1: Add Ohngea colors**

Append to `values/colors.xml`:
```xml
<color name="ohngea_teal_100">#D9F2EE</color>
<color name="ohngea_teal_500">#1DA795</color>
<color name="ohngea_teal_500_night">#35BFAE</color>
<color name="ohngea_amber_500">#F59E0B</color>
```
(`#1DA795` = `hsl(174,72%,40%)` web primary; `#F59E0B` = logo waveform amber.)

- [ ] **Step 2: Repoint theme**

In `values/themes.xml`, replace:
```xml
<item name="color_main1_100">@color/orange_main_100</item>
<item name="color_main1_500">@color/orange_main_500</item>
```
with:
```xml
<item name="color_main1_100">@color/ohngea_teal_100</item>
<item name="color_main1_500">@color/ohngea_teal_500</item>
```
and `color_chat_bubble_outgoing` `@color/orange_main_100` → `@color/ohngea_teal_100`. Check `values-night*/` overlays for `color_main1_500` overrides; point night override at `ohngea_teal_500_night`. Keep `orange_*` color defs in place (other usages untouched = smaller diff).

- [ ] **Step 3: Verify**

Run: `./gradlew :app:assembleDebug` (expect success).
Run: `rg -n "orange_main_500|orange_main_100" app/src/main/res/values/themes.xml app/src/main/res/values-night* || echo CLEAN` — expect CLEAN.
Manual: light + dark mode pass over dialer, login, settings, chat bubble.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/res/values/colors.xml app/src/main/res/values/themes.xml app/src/main/res/values-night*
git commit -m "feat: apply Ohngea teal/amber theme"
```

---

### Task 4: App icon from Ohngea logo

**Files:**
- Create/replace: `app/src/main/res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher.png`, `ic_launcher_round.png`, `linphone_launcher_icon_foreground.png` (check exact names present per density first)
- Modify: `app/src/main/res/mipmap-anydpi/ic_launcher.xml`, `ic_launcher_round.xml`
- Source: `/home/wairuru/projects/fusionpbx/frontend/public/logo.svg` (teal bubble + amber waveform), `icon-512.png` as fallback raster.

**Interfaces:**
- Consumes: Task 1 (adaptive-icon background should match `ohngea_teal_500` family).
- Produces: launcher icon showing Ohngea mark, no Linphone branding.

- [ ] **Step 1: Render icon assets**

Convert `logo.svg` to 512px PNG foreground with transparent padding (Android Studio Image Asset tool or `rsvg-convert`/`inkscape`), solid background `#0F2E2B` (dark teal). Export `ic_launcher.png` + `ic_launcher_round.png` at 48/72/96/144/192px into matching `mipmap-*` dirs, overwriting existing files. Keep `linphone_launcher_icon_foreground.png` name or rename consistently with `mipmap-anydpi/*.xml` content — update the xml files to match.

- [ ] **Step 2: Verify**

Run: `./gradlew :app:assembleDebug` (expect success, aapt links adaptive icon).
Manual: install on API 28 and 33+ emulator, confirm launcher + splash show Ohngea mark.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/res/mipmap-*
git commit -m "feat: Ohngea launcher icon"
```

---

### Task 5: Opus-first codec defaults (first launch only)

**Files:**
- Modify: `app/src/main/assets/linphonerc_default` (append `[audio_codec_N]` sections)
- Create: `app/src/test/java/org/linphone/CodecDefaultsTest.kt` (asset-order assertion)
- Reference: live key names verified by dumping config via `HelpViewModel.kt:259` linphonerc export on a debug build before writing sections.

**Interfaces:**
- Consumes: nothing (independent of Tasks 1-4).
- Produces: fresh installs order Opus > Speex-wideband > Speex-narrowband > rest.

- [ ] **Step 1: Confirm SDK config key format**

On a debug build, export `.linphonerc` via Settings → Help path (`HelpViewModel.kt:259`) and note the exact `[audio_codec_N]` entry keys (expected: `mime`, `rate`, `channels`, `enabled`). If keys differ, use the dumped names in Step 3.

- [ ] **Step 2: Write failing asset-order test**

Create `app/src/test/java/org/linphone/CodecDefaultsTest.kt`:
```kotlin
package org.linphone

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class CodecDefaultsTest {
    @Test
    fun `default config orders opus first then speex`() {
        val text = File("src/main/assets/linphonerc_default").readText()
        val sections = Regex("""\[audio_codec_(\d+)\](.*?)(?=\n\[|\Z)""", RegexOption.DOT_MATCHES_ALL)
            .findAll(text).sortedBy { it.groupValues[1].toInt() }.toList()
        fun entry(body: String, key: String) =
            Regex("""^${key}=(.*)$""", RegexOption.MULTILINE).find(body)!!.groupValues[1].trim()
        val mimes = sections.map { entry(it.groupValues[2], "mime").lowercase() }
        assertEquals("opus", mimes[0])
        val opusEnd = mimes.indexOfLast { it == "opus" }
        val speex = mimes.filter { it.contains("speex") }
        assert(speex.isNotEmpty()) { "expected speex entries after opus" }
        assert(mimes.indexOf(speex.first()) > opusEnd) { "speex must come after opus, got $mimes" }
    }
}
```
Run: `./gradlew :app:testDebugUnitTest --tests "org.linphone.CodecDefaultsTest"` — expect FAIL (no sections yet).

- [ ] **Step 3: Append ordered codec sections**

Append to `app/src/main/assets/linphonerc_default` (all `enabled=1`):
```
[audio_codec_0]
mime=opus
rate=48000
channels=2
enabled=1

[audio_codec_1]
mime=speex
rate=32000
channels=1
enabled=1

[audio_codec_2]
mime=speex
rate=16000
channels=1
enabled=1

[audio_codec_3]
mime=speex
rate=8000
channels=1
enabled=1
```
(Keep remaining codecs at SDK defaults below these; first-launch copy in `CorePreferences.copyAssetsFromPackage` plants the order, user toggles persist after.)

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "org.linphone.CodecDefaultsTest"` — expect PASS. Then `./gradlew :app:assembleDebug` — expect success. Manual: clean install, dump `.linphonerc`, confirm opus-first ordering live.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/assets/linphonerc_default app/src/test/java/org/linphone/CodecDefaultsTest.kt
git commit -m "feat: default audio codec order opus then speex"
```
