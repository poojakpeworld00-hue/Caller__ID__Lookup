# Clone inputs — edit the "New value" column and tell me to apply

Source: `../CallerIDLookupHome` @ `adadab0` (`com.callerid.numberlookup.home`, versionCode 1 / 1.0.0)
Clone:  this folder, branch **master**

Legend:
- **[SET]** — already applied.
- **[NEEDS YOU]** — still the source app's value, or a placeholder. It builds, but this must be real before release.
- **[KEPT]** — deliberately unchanged. Reason given.

---

## A. Identity — Stage 2 (`ab38e88`)

| # | Item | Source | Current value |
|---|---|---|---|
| A1 | applicationId / namespace **[SET]** | `com.callerid.numberlookup.home` | `com.callerid.number.lookup.home` |
| A2 | Ad-module package **[SET]** | `com.callerid.adbridge` | `com.callerid.admesh` |
| A3 | rootProject.name **[SET]** | `Caller ID Lookup Home` | `Caller ID Phone Home` |
| A4 | APK archive prefix **[SET]** | `CallerIdLookupHome` | `CallerIdPhoneHome` |
| A5 | Theme **[SET]** | `Theme.CallerIdLookupHome` | `Theme.CallerIdPhoneHome` |
| A6 | Play listing name | — | `Caller ID Phone Home` |
| A7 | versionCode / versionName **[KEPT]** | 1 / 1.0.0 | 1 / 1.0.0 |
| A8 | minSdk / targetSdk **[KEPT]** | 26 / 36 | 26 / 36 |

## B. Names shown to users — **[SET]**, all 12 locales

| # | Item | Value |
|---|---|---|
| B1 | `app_name` | `Caller ID` |
| B2 | `app_label` (launcher) | `␣␣Caller ID` — keeps the two non-breaking spaces that sort the app to the top of system lists |
| B3 | `app_name_overlay` | `Caller ID` |

## C. Brand visuals — Stage 3 visuals (`552c62a`) + icon set

| # | Item | State |
|---|---|---|
| C1 | Launcher icon **[SET]** | full adaptive icon: `ic_launcher.png` at all 5 densities (48→192 px), `ic_launcher_foreground.png` (108→432 px), `mipmap-anydpi-v26/ic_launcher.xml`, and a violet `drawable/ic_launcher_background.xml` (`#0E0BFF → #470CFF → #7705FF`). Foreground art sits at 84–347 px on the 432 px canvas — inside the 66 dp safe zone, so circular masks do not clip it |
| C2 | Palette **[SET]** | 121 tokens hue-rotated 52° off the source's blue (213° → 265° violet), lightness and alpha preserved per token; verdict colours and product greens untouched |
| C3 | Splash gradient **[SET]** | recoloured with the palette; no source blue (`#046DFF/#0A95FF/#0BD1FF`) remains in `res/` |
| C4 | Layouts **[KEPT]** | byte-identical apart from the package/theme rename |

## D. Keys and endpoints

| # | Item | Where | Status |
|---|---|---|---|
| D1 | Backend URL + API credentials | `services/HttpClientFactory.kt`, `ApiCredentials.kt` | **[KEPT]** your own backend |
| D2 | `google-services.json` | `app/` | **[SET]** real file for project `caller-id-phone-home` (`797289773894`), package matches A1 |
| D3 | LightHouse API key / base URL | `local.properties` | **[SET]** own key `sk_69w12…uagwi` (distinct from the source's), base `https://api.falconpush.com/`. Gitignored; reaches the app as an XOR'd `BuildConfig` `byte[]` decoded by `Veiled.s` |
| D4 | Remote LauncherPrefs | `docs/remote-config.json` | **[NEEDS YOU]** regenerated and verified against the ingest (see `docs/remote-config.md`), but **not yet published** — paste it into `GET_DATA_LIST` for `caller-id-phone-home` |
| D5 | AdMob app id | `AndroidManifest.xml` | **[NEEDS YOU]** Google's test id |
| D6 | Ad unit ids | `docs/remote-config.json` | **[NEEDS YOU]** all test units |
| D7 | Signing keystore | — | **[NEEDS YOU]** deliberately not copied; generate one for this listing |
| D8 | Policy / terms URLs | `strings.xml`, `docs/remote-config.json` | **[NEEDS YOU]** point at `sites.google.com/view/calleridphonelookup`, the original app's site |

## E. Deliberately left alone — **[KEPT]**

| # | Item | Why |
|---|---|---|
| E1 | `conduit.user` / `conduit.password` | `gradle.properties` private-maven credentials; the build cannot resolve the LightHouse SDK without them |
| E2 | Ad-SDK native layouts (13) | AdMob/FAN bind those views by reference; renaming breaks ad rendering |
| E3 | Ad-SDK-bound names | Still source-identical where the SDKs bind by name; everything else was renamed in Stage 3 (51 classes, 115 layouts, 332 drawables) |

---

## Still outstanding

1. **Publish Remote LauncherPrefs** to `caller-id-phone-home` (D4) — paste `docs/remote-config.json` into the `GET_DATA_LIST` parameter (`DEBUG_GET_DATA_LIST` for debug builds). The file itself is now correct; only publishing is outstanding.
2. **Real AdMob ids** (D5, D6) — app id in the manifest and every unit id in the config are Google's test values.
3. **Own signing keystore** (D7).
4. **Policy / terms URLs** (D8) — still the original app's `sites.google.com/view/calleridphonelookup`.

Done since the last revision: Stage 3 renames (`dfff28f`, `9a7121a`, `480af4c`), brand visuals and the adaptive icon (section C), and the LightHouse key (D3).
