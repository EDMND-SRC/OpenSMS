# Surviving a reboot

The phone has to come back without anyone touching it, so the app starts itself at boot.

`app/src/android/BootReceiver.java` is what does it.
It is a manifest receiver for `BOOT_COMPLETED`, `QUICKBOOT_POWERON` and `MY_PACKAGE_REPLACED`, and it starts the generated service, which is what owns the SMS receivers and the HTTP API.
It is Java because Python cannot be woken by a broadcast on its own, and it reaches the generated `Service...` class by reflection so that it does not depend on the class name this build happens to generate.

The `<receiver>` has to be a child of `<application>`, which p4a only learned to write on 2026-09-10 (`--extra-manifest-application-xml`), so it travels through `p4a.extra_args` in `buildozer.spec`.
See `app/p4a-patches/` for the p4a checkout that has it.

## What the phone does, measured on the Nokia C02 (Android 12)

- At the first unlock after a reboot the system delivers `BOOT_COMPLETED`, the receiver starts the service, and the API is listening again about forty seconds later.
  Android does not send it before that unlock, so a phone with a lock credential needs a person to unlock it once.
- An app update sends `MY_PACKAGE_REPLACED`, and the same path brings the service back.
- The service starts its API server on its own: `request.json` in the app's config directory still says `{"server": "start"}` from the last time the app ran, and nothing writes that file at boot.

## When it does not come back

`boot.log` is the record, written both to the app's internal files and to its external files, the second of which `adb` can read without root:

    adb shell cat /sdcard/Android/data/in.daslearning.opensms/files/boot.log

Two things stop a boot start, and both are silent:

- Android's *stopped* state.
  A force-stopped app, whether from Settings or `adb shell am force-stop`, receives no broadcasts at all until it is opened once, so force-stopping the gateway means it stays down until someone opens it.
- The foreground start being refused.
  Since Android 12 a background start must be a foreground start, and the boot broadcasts are among the exemptions that allow one.
  A refusal is recorded in `boot.log` with the exception class that caused it.

This phone's ROM never surfaces the app's own logcat output, which is the reason the file exists.

## The network, which the app does not control

Boot autostart only helps if the phone is back on the network, and that is the Wi-Fi stack's job, not this app's.
On a Nokia C02 running Android 12 the first connection after a boot to a WPA3 transition-mode access point failed on every boot tested (`connectionResult=1`, `networkType=TYPE_WPA3`, `isFirstConnectionAfterBoot=true`), and the phone either stayed off the network or rejoined a few minutes later depending on the boot.
That combination is awkward by nature: a transition-mode access point advertises one SSID as both WPA2 and WPA3, the phone saves an entry for each, and a failed SAE handshake can leave it wedged on the wrong one. In the captured failure the phone had targeted its WPA2 entry and the association still went out as WPA3, which points at the access point rather than at the entry the phone chose.
The fix therefore belongs to the access point, by setting that SSID to WPA2-PSK only. Forgetting the network on the phone is unlikely to be enough on its own, for the reason above.
