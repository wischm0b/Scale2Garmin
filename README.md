# Scale2Garmin

Android app for sending measurements from supported Bluetooth scales directly to
Garmin Connect. Health Connect export is optional. Android 12 or newer is required.

[Download the latest APK](https://github.com/wischm0b/Scale2Garmin/releases/latest)

## Features

- Receives weight and body composition data from supported BLE and Classic SPP scales.
- Uploads weight, body fat, body water, muscle mass, bone mass and basal metabolic rate to Garmin when available.
- Optionally exports supported values to Health Connect.
- Shows multiple chart series, trends, forecasts and a target-weight estimate.
- Imports VitaDock+ CSV exports and Scale2Garmin backups.
- Writes an automatic CSV backup after each new measurement.
- Includes German and English interfaces with light and dark themes.

Scale support comes from a pinned openScale driver set. See the
[supported scale list](docs/SUPPORTED_SCALES.md). A listed driver does not guarantee
compatibility with every firmware revision. The Medisana BS440/BS444 path has been
tested with real hardware; most other drivers are covered by imported protocol tests.

## Setup

1. Install the APK.
2. Open **Settings**, scan for a scale and select its user profile.
3. Connect a Garmin account.
4. Enable automatic Garmin, Health Connect or CSV synchronization as needed.
5. Pull down on the main screen or tap the connection icon to receive a measurement.

Some scales require profile data, prior Android pairing or device-specific keys.
Scale2Garmin does not obtain those keys.

## Data and backups

Measurements stay in the app's private database. Garmin session tokens are encrypted
with Android Keystore; Garmin passwords are not stored. The app contains no ads or
analytics.

Automatic backup writes a complete, restorable `Scale2Garmin-Backup.csv` after a new
measurement and replaces the previous file. An optional setting keeps one dated file
per day. Both formats can be imported by Scale2Garmin. VitaDock+ weight exports are
detected separately.

Garmin credentials and measurements are sent only to Garmin. On first sign-in, public
OAuth client parameters are fetched from Garth's S3 endpoint. The Garmin integration
uses unofficial endpoints and may break when Garmin changes them.

## Build

Requirements: JDK 17 and Android SDK 36.1.

```bash
./gradlew testDebugUnitTest assembleRelease
```

The release build uses the local debug certificate to remain compatible with earlier
direct installs. A Play Store release requires a dedicated signing key.

## License

GPL-3.0-or-later. See [LICENSE](LICENSE), [third-party notices](THIRD_PARTY.md) and the
[openScale integration notes](scale-drivers/UPSTREAM.md).

Scale2Garmin is not provided, endorsed or certified by Garmin or Medisana.
