# PSBDx DevBrowser

A developer-focused Android browser (Kotlin + Jetpack Compose, Material 3)
that injects [Eruda](https://github.com/liriliri/eruda) into any web page
for an on-device console, DOM inspector, network logger, and script
runner — plus a source viewer, snippet manager, storage cleaner, and
element picker, all tucked into a Chrome-style toolbar so the page keeps
the full viewport (no permanent bottom bar).

- **License:** GPLv3 (see `LICENSE`)
- **Package:** `com.devbrowser.psbdx`
- **Min/Target SDK:** 24 / 34

## UI & privacy features

- **Chrome-style toolbar only.** A single top bar (security icon, address
  bar, tab count, three-dot menu) — no bottom bar, so small mobile
  viewports aren't squeezed further. Every dev tool and setting lives in
  the overflow menu next to the tab-count button.
- **Security indicator.** The address bar shows a lock icon for HTTPS or
  a warning icon for HTTP. Tapping it opens **Site info**: connection
  status, a "delete cookies for this site" action, a per-site
  third-party-cookies toggle (blocked by default), and per-site
  Camera/Microphone/Location toggles (denied by default).
- **Default search engine.** Settings (from the overflow menu) lets you
  pick Google (default), DuckDuckGo, Bing, or Brave Search, each shown
  with a plain colored-monogram icon rather than a trademarked logo.
- **Block API requests toggle.** Also in Settings — when enabled, it
  patches `fetch()` and `XMLHttpRequest` on every page so REST/API calls
  a site makes are rejected before they reach the network. Normal page
  loads, images, scripts and styles are unaffected.
- **Third-party cookies blocked by default**, everywhere, unless you
  explicitly allow them for one specific site from that site's info
  panel.
- **PWA misdetection fix.** The mobile user agent is a standard
  Chrome-for-Android string with the "; wv" WebView marker removed. Many
  sites use that token to guess they're being shown inside an installed
  PWA/TWA wrapper; since this app provides its own full browser chrome
  rather than a bare app shell, presenting a normal browser UA avoids
  that misdetection.

## Eruda bundle

`app/src/main/assets/eruda.min.js` contains the genuine Eruda release
build (webpack UMD bundle), with the MIT copyright header prepended
above it:

```
Copyright (c) 2016-present, liriliri (https://github.com/liriliri)
Licensed under the MIT license.
```

If you ever need to update it to a newer Eruda release, keep that same
header intact above the new bundle — the MIT License's attribution
clause requires it wherever the software is redistributed:

```bash
curl -L -o /tmp/eruda.min.js \
  https://github.com/liriliri/eruda/releases/latest/download/eruda.min.js
cat > app/src/main/assets/eruda.min.js << 'EOF'
/*!
 * eruda
 * https://github.com/liriliri/eruda
 *
 * Copyright (c) 2016-present, liriliri (https://github.com/liriliri)
 *
 * Licensed under the MIT license.
 * https://github.com/liriliri/eruda/blob/master/LICENSE
 */
EOF
cat /tmp/eruda.min.js >> app/src/main/assets/eruda.min.js
```

## Local build

```bash
gradle wrapper --gradle-version 8.7   # generates gradlew + gradle-wrapper.jar
chmod +x gradlew
./gradlew assembleDebug
```

The `gradlew` and `gradlew.bat` scripts are included; only the
`gradle-wrapper.jar` binary is generated on demand (by you locally, or by
CI) rather than committed to the repo.

## Distribution flavors & self-update

There are two build flavors, controlling whether the direct-APK
self-updater exists at all:

- **`github`** — built for direct download from this repo's GitHub
  Releases. Checks `https://api.github.com/repos/m-farhan-hamim/PSBDx-DevBrowser/releases/latest`
  at most once every 24 hours, compares its `tag_name` (SemVer, `v`
  prefix stripped) against `BuildConfig.VERSION_NAME`, and if newer,
  shows a dismissible banner under the toolbar. Tapping **Update**
  downloads the release's `.apk` asset into the app's private cache,
  then hands it to the system Package Installer via a `FileProvider`
  `content://` URI — the user still sees and confirms that system
  install prompt themselves; nothing installs silently.
- **`fdroid`** — has the update checker compiled out entirely
  (`BuildConfig.IS_UPDATE_CHECK_ENABLED = false`), per F-Droid's
  requirement that apps it distributes never self-update outside of
  F-Droid's own mechanism.

Belt-and-suspenders: even a `github`-flavor build refuses to check for
updates at runtime if it detects (via `PackageManager.getInstallSourceInfo`
/ `getInstallerPackageName`) that it was actually installed through the
F-Droid client (`org.fdroid.fdroid` or `org.fdroid.fdroid.privileged`).

Build a specific flavor with `./gradlew assembleGithubRelease` or
`./gradlew assembleFdroidRelease` (see `.github/workflows/build.yml`,
which builds the `github` flavor for its release artifacts).

### Self-install via PackageInstaller (not a plain ACTION_VIEW)

Tapping "Update" installs the downloaded APK through the
[`PackageInstaller`](https://developer.android.com/reference/android/content/pm/PackageInstaller)
Session API (`update/PackageInstallerHelper.kt`), not a plain
`ACTION_VIEW` intent handed off to the generic system installer. The
system still shows its own install-confirmation UI either way — the
difference is *who gets recorded as the installer* of the result. Using
the Session API makes this app itself the recorded installer, the same
way F-Droid's client and Obtainium do their own self-updates.

That in turn is what makes Settings → Apps → PSBDx DevBrowser → (Store
section) → **App details** open something useful after a self-update,
via `AppDetailsRedirectActivity` handling `android.intent.action.SHOW_APP_INFO`.
This is the same hand-off Play Store, F-Droid, and Aurora Store use to
show their own listing page for apps installed through them — it does
**not** touch or replace anything else on that screen. Permissions,
Battery usage, Manage notifications, Storage usage, and the Uninstall
control all stay exactly as Android provides them; this only supplies
the destination for one supplementary "where did this app come from"
link, and only when this app is genuinely the recorded installer (i.e.
after a self-update — a first-time sideload via a file manager or ADB
still shows that tool's own info there instead, honestly).

## CI/CD (GitHub Actions)

`.github/workflows/build.yml` builds the `github` flavor's Debug and
signed Release APKs on every push/PR to `main` (plus a `fdroid`-flavor
debug build, just so CI catches any flavor-specific compile errors —
F-Droid's own build server is what actually produces the APK F-Droid
distributes), and uploads them as workflow artifacts:
`PSBDx-DevBrowser-Debug-APK` and `PSBDx-DevBrowser-Release-APK`.

Set these repository secrets to enable release signing (the Release
build falls back to debug signing if they're absent, so the workflow
never fails on a fork without secrets):

| Secret                    | Description                                  |
|---------------------------|-----------------------------------------------|
| `KEYSTORE_BASE64`          | `base64 -w0 your.keystore` output             |
| `RELEASE_STORE_PASSWORD`   | Keystore password                             |
| `RELEASE_KEY_PASSWORD`     | Key password                                  |
| `RELEASE_KEYALIAS`         | Key alias                                     |

## F-Droid compliance notes

- No Google Play Services, Firebase, Crashlytics, AdMob, or other
  proprietary SDKs anywhere in the dependency graph.
- The `fdroid` build flavor has the self-updater compiled out entirely
  — see "Distribution flavors & self-update" above.
- Permissions: `INTERNET` / `ACCESS_NETWORK_STATE` for browsing,
  `POST_NOTIFICATIONS` (Android 13+) and `ACCESS_FINE_LOCATION`
  requested once at launch, plus `CAMERA` / `RECORD_AUDIO` so the
  browser is *able* to grant a website's camera/mic/location request —
  but nothing is ever auto-granted. Every origin starts fully denied
  for all three until the user explicitly allows that specific site
  from the address bar's Site info panel. `REQUEST_INSTALL_PACKAGES`
  exists only for the `github` flavor's self-updater, and only ever
  triggers the system installer for a file the user tapped "Update"
  to download themselves.
- Eruda is bundled as a local asset (`assets/eruda.min.js`, MIT header
  intact) and evaluated in-page; the app never fetches executable JS
  from a remote CDN at runtime.
- No analytics, telemetry, or tracking of any kind. The only persisted
  data (bookmarks, history, snippets, and the settings described above)
  stays in the app's local Room database / SharedPreferences.

## Project structure

```
app/src/main/java/com/devbrowser/psbdx/
├── MainActivity.kt                 # Entry point; requests site-permission OS grants once
├── DevBrowserApplication.kt
├── data/AppDatabase.kt              # Room: bookmarks, history, JS snippets
├── data/SettingsRepository.kt       # SharedPreferences: search engine, API blocking,
│                                     # per-site third-party cookies & permissions
├── viewmodel/BrowserViewModel.kt    # MVVM state holder
├── webview/DevWebView.kt            # Compose WebView wrapper + controller (UA, cookies, perms)
├── webview/ErudaWebClient.kt        # Injects assets/eruda.min.js + API-block shim per navigation
└── ui/MainScreen.kt, AboutDialog.kt, SettingsDialog.kt, SiteInfoDialog.kt
```
