# Noor Al-Salhi – Ride Analyzer

Android Studio project for a driver-side ride-offer analyzer. It does not accept/reject rides or automate taps.

## Project configuration

- Kotlin + Jetpack Compose
- `compileSdk = 36`, `targetSdk = 36`, `minSdk = 26`
- AccessibilityService is the primary reader.
- MediaProjection + Tesseract4Android is the optional live OCR fallback.
- Room stores analyzed offers locally; DataStore stores configuration locally.
- Floating window uses `TYPE_APPLICATION_OVERLAY` and requires the user's explicit overlay permission.
- OCR models (`ara` + `eng`) are bundled in `app/src/main/assets/tessdata/`.

## Opening/building

1. Open this folder in Android Studio with Android SDK Platform 36 installed. The included `gradlew` / `gradlew.bat` launchers download Gradle 8.13 on first use if needed.
2. Allow Gradle sync to download dependencies (including Tesseract4Android from JitPack).
3. Build > Make Project, then Build > Build APK(s).
4. Debug APK output: `app/build/outputs/apk/debug/app-debug.apk`.

The project archive supplied with this response is source code; no APK was compiled in this environment because Android SDK, Gradle and a connected Android test device were not available here.

## First-time setup on the phone

1. Install the debug APK.
2. Open Noor Al-Salhi and press **تفعيل القراءة**. Enable the Noor Accessibility service in Android settings.
3. Grant **الظهور فوق التطبيقات** when requested.
4. In Settings, set the exact package-name prefix of the Baly Driver application if `app.baly.driver` is the default exact package identifier. The app currently filters events by package-name substring to avoid reading unrelated apps.
5. Turn on ride analysis, open Baly Driver and wait for a real offer. Confirm that fare and distance are present in the extracted accessibility text before relying on the profit result.
6. Use **تشغيل OCR الاحتياطي** only if Baly Driver renders its offer content in a way Accessibility cannot expose. Android shows its own screen-capture consent prompt for every new MediaProjection session. Stop OCR from the home screen when not needed.

## Important acceptance gate: Baly Driver must be tested on the actual phone

Accessibility can only read content that Baly Driver exposes in the accessibility node tree. It cannot guarantee extraction from text drawn into a canvas, protected surfaces, or inaccessible custom views. The OCR fallback is user-consented and restricted to times when the Accessibility service reports the selected Baly Driver package as foreground. It supports configurable crop percentages for the screen region. It processes frames in memory; it does not save screenshots or upload screen data. Device/OEM behavior and Android background-service limits still require on-device testing.

Before treating the app as production-ready, validate:

- The exact Baly Driver package identifier and actual offer layout on the target Android phone.
- Both fare and distance extracted from a real incoming offer, without pressing an analyze button.
- That the overlay appears above Baly Driver and can be dragged/hid without blocking offer controls.
- OCR text quality for the exact Arabic/English digits and screen scale used by Baly Driver.
- Battery behavior and sustained operation on the phone's OEM firmware.

## Profit calculation

`net = fare - commission - fuel - maintenance - tyres - depreciation - reserve - extra`

Fuel is calculated either from litre price + litres/100 km OR direct fuel cost/100 km (never both). Kilometres include a pickup distance only when the selected mode is `PICKUP_PLUS_TRIP`; a detected pickup distance is preferred, otherwise the configured default is used. An optional fixed distance addition and percentage buffer can also be applied. Unknown pickup distance is never guessed. The default `TRIP_ONLY` mode deliberately marks its estimate as estimated.

Default rating boundaries:
- `< 500 IQD`: Bad
- `500–999 IQD`: Average
- `1,000–1,499 IQD`: Good
- `>= 1,500 IQD`: Excellent

All cost fields and thresholds can be edited in Settings. All cost fields default to zero because no vehicle fuel/maintenance data was provided; the app refuses to analyze until a valid fuel profile is entered. Enter the car's actual maintenance, tyre, depreciation and reserve costs (or intentionally leave any at zero) before trusting the estimate.

## Known unfinished/verification items

- The package identifier is configured as the exact value `app.baly.driver`; the live offer layout and selectors still require on-device validation.
- The accessibility parser is intentionally conservative. If the Baly Driver UI uses unlabeled price numbers, the parser will reject the offer instead of inventing a fare.
- OCR currently captures frames at a throttled interval while the target package is marked foreground. The entire frame is processed in memory; fine-grained offer-card cropping needs calibration against a real screenshot/layout.
- No claim is made that the Baly Driver integration has passed device testing. An actual device with Baly Driver and a sample offer is needed to finish that validation.
- Release signing is not configured. Do not distribute a release build until a private signing key is created and stored securely.

## Privacy

No network permission is declared. Ride text and history remain in local Room/DataStore storage. The accessibility service ignores password nodes and ignores other packages. OCR does not persist frame images. The user can delete ride history inside the app; uninstalling the app removes its local private database/preferences.
