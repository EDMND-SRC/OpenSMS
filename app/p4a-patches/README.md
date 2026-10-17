# p4a patches

What this app needs from python-for-android beyond the branch named in `buildozer.spec`
(`p4a.url` / `p4a.branch`, currently `daslearning-org/p4a-unofficial` `develop`), and how
to get a build that has it.

The fork's `develop` predates both of these. It adds nothing to p4a's recipes, only some
documentation, so a build can use kivy's `develop` directly with nothing lost.

## Prepare a checkout

    sh app/p4a-patches/setup-p4a.sh

It clones kivy's `develop` at the revision this app was built against, applies the patch
below, and prints the `p4a.source_dir = ...` line to add to `buildozer.spec`.
`p4a.source_dir` takes precedence over `p4a.url`, so that line is what makes the build use
the prepared checkout.

## Why a checkout is needed

**The Android API level that maturin needs.** `pydantic-core` builds under maturin, which
needs `ANDROID_API_LEVEL` in its environment to tag the wheel it produces. p4a exports it
for the recipes that handle this case by hand (`recipes/watchfiles/__init__.py`) but not
for `RustCompiledComponentsRecipe`, which every maturin-based recipe subclasses, so an
armeabi-v7a build fails inside maturin a long way from the missing variable. Filed upstream
as [kivy/python-for-android#3396](https://github.com/kivy/python-for-android/pull/3396).

**A receiver inside `<application>`.** The boot receiver that brings the gateway back after
a reboot has to be a child of `<application>`. Until 2026-09-10 the only ways to add to the
manifest were `--extra-manifest-xml` (children of `<manifest>`, where aapt rejects a
receiver) and `--extra-manifest-application-arguments` (attributes only). Merged upstream as
[kivy/python-for-android#3377](https://github.com/kivy/python-for-android/pull/3377) as
`--extra-manifest-application-xml`, which is now in kivy's `develop`. `buildozer.spec`
passes it through `p4a.extra_args` because buildozer has no setting of its own for it.

## 0001: the Android API level for maturin

Delete this file, and the note beside `p4a.branch` in `buildozer.spec`, once #3396 is
merged and a release carries it.

Its context lines come from commit `8ef9d1db` of kivy's `develop`. When that moves, update
the patch here rather than carrying the edit by hand in a build container, so anybody can
reproduce the APK.
