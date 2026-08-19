# Remote LauncherPrefs — `docs/remote-config.json`

The whole file is the value of **one** Remote LauncherPrefs parameter, not one parameter
per key:

| Build | Parameter |
|---|---|
| release | `GET_DATA_LIST` |
| debug (`BuildConfig.DEBUG`) | `DEBUG_GET_DATA_LIST` |

Both are read as a string and parsed as JSON — `PromoAnchorActivity.setResponceInPref`
at splash, `LiveConfigListener` again whenever Realtime Remote LauncherPrefs pushes a
change. To publish, paste the file's contents into that parameter's value in the
Firebase console for **caller-id-phone-home** and publish.

## Audience split

The top level is `marketing` / `organic`. `PromoConfigLoader.audienceRoot` picks one
using the install-referrer verdict (`OnMaketing`), falling back to the other
audience and then to the flat top level. Both blocks must carry the same key set —
the ingest reads whichever block it lands in and nothing merges them.

## `ScreenAds` — which keys are legal

`FrameActivity.onCreate` calls `PerScreenPromo.showAd(this::class.java.simpleName, …)`,
so a key is legal exactly when it is the simple name of a `FrameActivity` subclass:

```
AppHomeActivity     TorchToolActivity    BlockCenterActivity  CallDetailActivity
BatteryToolActivity  CountryPickActivity ToolboxActivity     LanguageSelectActivity
StopwatchActivity    LightMeterActivity   ShellSurfaceScreen     CompassToolActivity
DialPadActivity      SpeedToolActivity    LevelToolActivity   SettingsHubActivity
SlideIntroActivity      CountdownActivity   LookupResultActivity LookupHistoryActivity
SimInfoActivity      NoiseToolActivity   LaunchGateActivity     ConsentGateActivity
```

Plus the `default` entry, which supplies ids when `screen_wise_default` is true and
supplies `show` for any screen without its own entry.

Notes:

- `CallPanel` draws its banner under the key `AppHomeActivity`
  (`BANNER_SCREEN_KEY`), deliberately sharing that screen's entry rather than
  taking one of its own.
- **`HomeBoardActivity` is not legal here.** It extends `ShellBaseActivity`, which
  never calls `showAd`, so an entry for it is inert — the launcher home's ads come
  from `launcher_ads` instead. An entry was removed for this reason; adding one
  back has no effect.
- `show` is honoured per screen even when `screen_wise_ad` is false; only the
  ad-unit ids are gated on it.

## Keys deliberately absent

`PromoConfigLoader` reads a few keys this file does not carry. That is correct, not an
omission:

| Key(s) | Why absent |
|---|---|
| `MarketInterCounter`, `MarketBackCounter`, `MarketNativeCounter`, `MarketBannerCounter`, `MarketAppopenCounter` | Only promoted into the live counters when the config is **not** audience-split (`PromoAnchorActivity`: `if (isMarketingOn && !isSplitConfig)`). This config is split, so the `marketing` block already holds the final counters; adding the `Market*` keys would zero them. |
| `NativeBgColor`, `NativebtnColor`, `NativetxtColor`, `NativebtntxtColor` | Derived from `NativeTheme` by `applyNativeTheme`, which runs after the string ingest and overwrites them. Setting them at top level is a legacy path that the theme wins over — and if `NativeTheme` were ever absent, empty strings here would be used as real colours. |
| `Perm_Sheet_Show`, `Perm_Sheet_Mode` | Superseded by `intro_display.permission_sheet` (`enabled` + `prompt_frequency`); see `RevealConfig.permissionSheet`. Nothing outside the ingest list reads them. `Perm_Sheet_Interval_Days` is still carried but is likewise superseded by `prompt_interval`. |

`permission_engine` is the reverse case: the ads ingest does not read it, but
`PermitSource` and `FsiSettings` do — either from a dedicated
`permission_engine` parameter or from this blob. Keeping it here avoids a second
parameter.

## Must be replaced before release

| Field | Current | Blocked on |
|---|---|---|
| `googleS_Inter`, `googleBackInter`, `googleInter`, `googleAppopen`, `googleNative`, `googleBanner`, `googleRewarded`, `HD_VBC_Native_ID`, `HD_VBC_Banner_ID`, `ScreenAds.*.banner`/`native` | Google's test unit ids | D6 — real AdMob units |
| `PrivacyPolicy`, `TermLink` | the original app's `sites.google.com/view/calleridphonelookup` | D8 |
| `DirectLink`, `MarketLink`, `launcher_ads.app_click.fallback_link` | blank | the affiliate destination for **this** listing. They previously carried the source app's `980.mark.qureka.com/intro`, which would have sent this app's fallback traffic to that listing. Blank is safe: every read guards on `isNullOrBlank`, and `IsCustomADS` is false. |
| `FbAppId`, `FbClientToken` | blank | real Meta credentials, if FAN is ever enabled. Blank makes `FacebookKeys.usable` false, so the SDK is skipped cleanly; the previous `"dfgd"` / `"fgfgfd"` would have initialised it with junk. |
| `custom_ads[].icon` / `.bannerImage` | `appdata.blr1.digitaloceanspaces.com/pja/caller_id/…` | confirm those assets belong to this listing. Inert while `IsCustomADS` is false. |

Kept on purpose: `api_base_url` (`callerid.kpeworld.com`, your own backend — D1) and
`CountryList_Counter_NShow` / `CountryList_Marketing_Counter_NShow` = `Indore`, which
is matched against country, region **or** city, so a home city here is intentional —
it turns the HD_VBC house banner off on your own devices.
