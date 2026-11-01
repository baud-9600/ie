# 家 ie — Home

An app for the [Mudita Kompakt](https://mudita.com/products/kompakt/) that switches lights and
other devices in a Home Assistant instance, and controls its media players. It is built with
Mudita's [MMD](https://github.com/mudita/MMD) design system and installs on any device with
Android 11 or later.

*Ie* is 家 — a house, and the plain word for home. The app is called **Home** in English and
**Zuhause** only in German; elsewhere, Japanese included, it keeps the English name. Its
application id is `de.asdfghasdfgh.ie`.

## What it does

- **Switches, lights, fans and helpers**, on or off with one press on the row. The switch at the
  end says which way it stands, and the state is read back from Home Assistant after the press.
- **Media players**: quieter, louder, play, pause and stop. What the player is doing, its volume
  and whether it is muted are shown above the buttons.
- **Only the devices you choose.** Settings → Select devices lists everything Home Assistant
  offers in the four supported kinds; untick what should stay off the main screen. Until a choice
  is saved, everything is shown. The choice stays on the phone.
- **Any Home Assistant address**: on the local network, through a reverse proxy with a path, or
  from outside over HTTPS. The address is typed in on the phone and checked before it is kept.
- **An optional server address** for a name that resolves differently inside and outside the
  house: the app connects to the address given there, while the certificate is still checked
  against the name above.
- **Current states** while the app is on screen: every five seconds the entities are read again,
  so a change made elsewhere appears without a press.
- **English and German** for everything but the launcher name; the name is *Home* in English and
  *Zuhause* in German.

It shows no dashboards, automations or sensors, and has no tabs. The one list is what the Kompakt
can read at a glance and switch with a thumb.

## The devices it shows

| Home Assistant domain | What it is | What it does here |
|---|---|---|
| `light` | a light | on or off |
| `switch` | a switch | on or off |
| `input_boolean` | a helper | on or off |
| `fan` | a fan | on or off |
| `media_player` | a media player | quieter, louder, play, pause, stop |

An entity disabled in Home Assistant is not published by its API, so it cannot appear here. One
that is merely unavailable is listed and greyed until it comes back.

## Connecting

1. In Home Assistant, open **Profile → Security → Long-lived access tokens** and create a token.
2. Open the app and enter the address, for example `http://homeassistant.local:8123` or
   `https://my-instance.ui.nabu.casa`.
3. Paste the token. If the name reaches a different server from the phone, enter the address that
   does reach Home Assistant under **Server address**; the name above is still the one whose
   certificate is checked.
4. Press **Connect**. The app asks Home Assistant who it is before it keeps anything.

Once connected, the address, server address and token move behind **Settings → Instance**, so the
main settings screen holds only the instance, the devices and the way out. Changing them is the
same form, reached one step further in.

A Home Assistant user of its own is worth making for this app: the token has exactly that user's
rights. It needs access only to the entities that should be switched or played here.

## Privacy

The app talks to the Home Assistant address that was entered, and to nothing else. There are no
accounts of the app's own, no analytics and no adverts.

The access token is kept **encrypted**: an AES-GCM key is generated in the Android Keystore and
never leaves it, and the preferences hold only the ciphertext and its random IV. A copy of the
preferences is not a copy of the token. The token can be revoked and replaced in Home Assistant
at any time.

The app asks for **Internet only**. There is no location, camera, microphone, contacts or file
access. `android:allowBackup="false"` keeps the preferences out of a system backup.

For a Home Assistant reached over plain `http`, the manifest allows cleartext traffic. An
instance reached only over HTTPS can do without it; remove `android:usesCleartextTraffic="true"`
from `app/src/main/AndroidManifest.xml`. Self-signed HTTPS certificates are not accepted.

## Building

```
./gradlew assembleDebug
```

The APK is at `app/build/outputs/apk/debug/app-debug.apk`.

A release build needs a keystore at `signing/signing.keystore` with a matching
`signing/signing.properties`. There is no fallback key in this repository: without one, a release
build comes out unsigned rather than wrongly signed.

```
./gradlew testDebugUnitTest
```

runs the tests: addresses with and without a scheme, a server address that overrides DNS while
the URL host stays the same, the entities read from `/api/states`, and the media-player volume.

## Getting it

There is no published APK yet. Build it as above and sideload it:

```
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Releases, when there are any, will be at <https://github.com/baud-9600/ie/releases>.

## Licence

GPL-3.0-only. See [LICENSE](LICENSE).

Copyright (C) 2026 Manuel Kuklinski

This program is free software: you can redistribute it and/or modify it under the terms of the
GNU General Public License as published by the Free Software Foundation, version 3.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See
the GNU General Public License for more details.

You should have received a copy of the GNU General Public License along with this program. If
not, see <https://www.gnu.org/licenses/>.

Mudita's MMD is Apache 2.0; see its [repository](https://github.com/mudita/MMD).
