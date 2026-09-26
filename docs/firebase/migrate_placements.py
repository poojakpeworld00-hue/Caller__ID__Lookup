#!/usr/bin/env python3
"""
Moves the flat per-placement ad keys (`drawer_DirectLink`, `onboarding_welcome_ads_on`, …) of a
GET_DATA_LIST / DEBUG_GET_DATA_LIST value into one nested `placements` object, then proves the move
changed nothing: every placement x every setting is resolved the way the app resolves it
(LauncherPlacementAds.resolve / placementEnabled) against the old and the new config, and must match.

    python3 migrate_placements.py <in.json> [<out.json>]      # prints the report; writes out.json if given

The input is the parameter's value: { "marketing": {...}, "organic": {...} } (either audience may be
missing). Global keys (googleInter, link_first_then, inter_fallback, …) are left where they are.
Keep PLACEMENTS / SUFFIXES / NOT_PLACEMENT_KEYS in step with LauncherPlacementAds.kt.
"""
import copy
import json
import sys

PLACEMENTS = ["leftPanel", "rightPanel", "drawer", "onboarding", "recent", "appExit", "unlock",
              "splash", "back", "appOpen", "install", "uninstall", "charge", "discharge"]
SUFFIXES = ["ads_on", "DirectLink", "link_first_then", "link_first_show_all", "link_open_in",
            "inter_fallback", "ad_flow", "ad_flow_show_all", "RewardedAds",
            "googleInter", "googleFullNative", "googleNative", "googleAppopen", "googleRewarded"]
NOT_PLACEMENT_KEYS = {"recent_playstore", "recent_playstore_window_sec", "onboarding_home"}
ONBOARDING_SCREENS = ["set_default", "welcome", "language", "intro"]


def as_text(v):
    """How the app sees a value: org.json optString / toString."""
    if v is None:
        return None
    if isinstance(v, bool):
        return "true" if v else "false"
    if isinstance(v, (list, dict)):
        return json.dumps(v, separators=(",", ":"))
    return str(v)


def split_flat(key):
    """'onboarding_welcome_DirectLink' -> ('onboarding_welcome', 'DirectLink'), or None."""
    if key in NOT_PLACEMENT_KEYS:
        return None
    for suffix in sorted(SUFFIXES, key=len, reverse=True):
        if key.endswith("_" + suffix):
            name = key[: -len(suffix) - 1]
            if any(name == p or name.startswith(p + "_") for p in PLACEMENTS):
                return name, suffix
    return None


def migrate_audience(aud):
    out = copy.deepcopy(aud)
    block = out.setdefault("placements", {})
    moved = []
    for key in list(out.keys()):
        hit = split_flat(key)
        if hit:
            name, suffix = hit
            block.setdefault(name, {})[suffix] = out.pop(key)
            moved.append(key)
    if not block:
        out.pop("placements")
    return out, moved


# ---- the app's resolution, for the equivalence check ----------------------------------------

def names(placement):
    return [placement, "onboarding"] if placement.startswith("onboarding_") else [placement]


def own(aud, name, key):
    nested = (aud.get("placements") or {}).get(name, {})
    v = as_text(nested.get(key)) if isinstance(nested, dict) else None
    if v and v.strip():
        return v
    flat = as_text(aud.get(f"{name}_{key}"))
    return flat if flat and flat.strip() else None


def resolve(aud, placement, key):
    for n in names(placement):
        v = own(aud, n, key)
        if v is not None:
            return v
    return as_text(aud.get(key)) or ""


def enabled(aud, placement):
    v = next((own(aud, n, "ads_on") for n in names(placement) if own(aud, n, "ads_on") is not None), None)
    return (v or "").strip().lower() != "false"


def check(old, new):
    problems = []
    placements = PLACEMENTS + [f"onboarding_{s}" for s in ONBOARDING_SCREENS]
    for p in placements:
        for key in SUFFIXES:
            a, b = resolve(old, p, key), resolve(new, p, key)
            if a != b:
                problems.append(f"{p}.{key}: {a!r} -> {b!r}")
        if enabled(old, p) != enabled(new, p):
            problems.append(f"{p}.ads_on changed")
    for key in set(old) | set(new):
        if key == "placements" or split_flat(key):
            continue
        if old.get(key) != new.get(key):
            problems.append(f"non-placement key changed: {key}")
    return problems


def main():
    src = json.load(open(sys.argv[1]))
    result, ok = {}, True
    for audience, aud in src.items():
        new, moved = migrate_audience(aud)
        problems = check(aud, new)
        result[audience] = new
        print(f"{audience}: moved {len(moved)} keys into placements -> {sorted((new.get('placements') or {}).keys())}")
        for p in problems:
            print(f"  MISMATCH {p}")
        ok = ok and not problems
    print("VERIFIED: every placement resolves identically" if ok else "FAILED: see mismatches above")
    if ok and len(sys.argv) > 2:
        json.dump(result, open(sys.argv[2], "w"), indent=2, ensure_ascii=False)
        open(sys.argv[2], "a").write("\n")
        print(f"wrote {sys.argv[2]}")
    sys.exit(0 if ok else 1)


if __name__ == "__main__":
    main()
