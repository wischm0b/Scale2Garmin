# openScale driver integration

Source: [oliexdev/openScale](https://github.com/oliexdev/openScale)  
Pinned commit: `ac5753b062112e4d74772e6b50b084cd68c59175`  
License: GPL-3.0-or-later

This module contains 64 registered handler variants plus their protocol libraries and
BLE GATT, BLE broadcast and Classic SPP adapters. A separate openScale installation is
not required.

Integration changes:

- Removed Compose configuration screens and connected drivers to the native Scale2Garmin UI.
- Replaced openScale storage dependencies with app profiles and private per-device settings.
- Kept unit conversions while limiting stored values to weight, fat, water, muscle, bone and basal metabolic rate.
- Disabled protocol and measurement logging.
- Added deterministic session cleanup.
- Retained the relevant upstream protocol and registry tests.

`scripts/import_openscale.py` documents the original import; it is not an updater.

Hardware and firmware revisions can differ. Some scales require prior Android pairing,
profile data or device-specific keys. See the [supported scale list](../docs/SUPPORTED_SCALES.md).
