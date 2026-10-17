#!/bin/sh
# Prepares the python-for-android checkout this app needs, and prints the one line to
# add to buildozer.spec so the build uses it.
#
# Two things the app depends on are in p4a's develop branch but not in the fork the spec
# names: the Android API level maturin needs for a 32-bit wheel (kivy/python-for-android#3396,
# still open, applied from this directory) and --extra-manifest-application-xml
# (kivy/python-for-android#3377, merged 2026-09-10). Pinning the commit below means a fresh
# machine reproduces the APK instead of trusting whatever develop happens to be that day.
#
# Usage: sh app/p4a-patches/setup-p4a.sh [target-directory]
set -eu

TARGET="${1:-$HOME/.cache/opensms/p4a}"
# kivy/python-for-android develop, as of 2026-10-17. Move it forward deliberately: the
# patch below is written against this revision's context lines.
PIN=8ef9d1db
PATCHES="$(cd "$(dirname "$0")" && pwd)"

if [ -d "$TARGET/.git" ]; then
    git -C "$TARGET" fetch --quiet origin
else
    git clone --quiet --filter=blob:none \
        https://github.com/kivy/python-for-android.git "$TARGET"
fi
git -C "$TARGET" checkout --quiet "$PIN"

for patch in "$PATCHES"/*.patch; do
    if git -C "$TARGET" apply --check "$patch" 2>/dev/null; then
        git -C "$TARGET" apply "$patch"
        echo "applied $(basename "$patch")"
    else
        echo "skipped $(basename "$patch") (already applied, or written against another revision)"
    fi
done

cat <<EOF

p4a is ready at $TARGET

Add this line to buildozer.spec, beside the other p4a settings:

    p4a.source_dir = $TARGET

p4a.source_dir wins over p4a.url, so a build will use this checkout rather than
the fork's develop branch.
EOF
