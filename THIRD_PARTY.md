# Third-party notices

Scale2Garmin is licensed under GPL-3.0-or-later because it includes adapted openScale
drivers. Full license texts are stored in `licenses/` and bundled with the APK.

## Main sources

- [openScale](https://github.com/oliexdev/openScale), GPL-3.0-or-later, pinned at
  `ac5753b062112e4d74772e6b50b084cd68c59175`. See
  [integration notes](scale-drivers/UPSTREAM.md).
- [Garth](https://github.com/matin/garth), Copyright © 2023 Matin Tamizi, MIT.
  The Garmin sign-in flow is based on Garth's protocol work.
- [Garmin FIT SDK](https://github.com/garmin/fit-java-sdk). Used only as a test
  dependency to validate the independent FIT encoder; SDK code is not bundled.

Fit2Garmin and HC2Garmin were reviewed for concepts but their code is not included.

## Runtime libraries

- blessed-kotlin 3.0.12 — MIT
- Kotlin 2.2.10 and kotlinx.coroutines 1.10.2 — Apache-2.0
- AndroidX and Health Connect client 1.1.0 — Apache-2.0
- Protocol Buffers from Health Connect — BSD-3-Clause
- Bouncy Castle 1.80 — Bouncy Castle licence
- Timber 5.0.1 — Apache-2.0

The Garmin integration uses unofficial endpoints. Scale2Garmin is not provided,
endorsed or certified by Garmin or Medisana.
