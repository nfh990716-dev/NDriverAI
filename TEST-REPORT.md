# Noor Al-Salhi – Test and Verification Report

Report date: 22 November 2026

## Verified in this build environment

| Area | Result | Evidence / notes |
|---|---|---|
| Pure Kotlin domain compilation | PASS | `RideModels`, `ArabicNumberNormalizer`, `RideTextParser`, `ProfitCalculator`, and settings model compile with the local Kotlin compiler. |
| Arabic/English digits | PASS | Parser accepts Arabic-Indic, Eastern Arabic-Indic, and Latin digits. |
| Fare + distance parsing | PASS | Examples `Fare: 5,000 IQD / Distance: 8 km` and `الأجرة: ٥٬٠٠٠ د.ع / المسافة: ٨ كم` parse to 5,000 IQD and 8 km. |
| Ambiguous/random numbers | PASS | Unlabelled text such as an order number, clock time, and street number is rejected. |
| Pickup + trip distance | PASS | Explicit pickup distance is separated from trip distance; pickup-only text without a trip distance is rejected. |
| Rating boundaries | PASS | 499=BAD, 500=AVERAGE, 999=AVERAGE, 1,000=GOOD, 1,499=GOOD, 1,500=EXCELLENT. |
| Example profit calculation | PASS | With fare 5,000, commission 20%, fuel 1,000 IQD/100 km, 8 km, and configured per-km costs (maintenance 50, tyres 20, depreciation 30, reserve 10), costs=1,960 and net=3,040 IQD. |
| XML well-formedness | PASS | Manifest, accessibility service config, styles, and strings XML parse successfully. |
| OCR model assets | PRESENT | Arabic (`ara`) and English (`eng`) Tesseract trained-data assets are bundled. |

## Not verified – do not treat as production-complete yet

| Area | Status | Reason / required next test |
|---|---|---|
| Full Android Gradle build | NOT RUN | This environment has no Android SDK and no installed Gradle distribution. The project declares compile/target API 36; build with Android Studio and Android SDK Platform 36. |
| Debug APK | NOT GENERATED | Requires the Android Gradle build above. |
| Release APK/signing | NOT GENERATED | No signing key was supplied or created. |
| Baly Driver package identifier | CONFIGURED, DEVICE NOT VERIFIED | Exact package configured as `app.baly.driver`; the live app still needs testing on the target phone. |
| Accessibility extraction from live Baly Driver offer | NOT VERIFIED | Requires a real Baly Driver offer on the target Android device. Baly Driver may expose text nodes or may render content in inaccessible custom views. |
| OCR extraction from live Baly Driver offer | NOT VERIFIED | Requires per-session MediaProjection approval and real-device calibration of the OCR crop region and text parser. |
| Floating window over Baly Driver | NOT VERIFIED | Requires granting overlay permission and testing with the actual Baly Driver UI and phone firmware. |
| Android 15/16 background behavior | NOT VERIFIED | Must be checked on the target device/OEM firmware, including battery restrictions and screen-capture session lifecycle. |
| Battery, RAM, sequential offers | NOT VERIFIED | Requires sustained on-device profiling with multiple incoming offers. |

## Exact acceptance steps for the device test

1. Build and install the debug APK from Android Studio.
2. Enable Noor Al-Salhi in Android Accessibility settings and grant Display over other apps.
3. Configure the exact Baly Driver package and all real cost/fuel inputs.
4. Open Baly Driver and wait for a new offer without pressing an analyze button.
5. Inspect the Read Test screen: it must show the extracted fare and distance; if not, do not rely on a profit result.
6. Confirm that the floating overlay appears above Baly Driver, can be dragged, hidden, and restored.
7. If Accessibility cannot expose the offer text, explicitly start OCR and accept Android's screen-capture prompt; calibrate the crop region and repeat the live test.
8. Check sequential offers, duplicate suppression, battery use, and the history totals.

**Conclusion:** This archive contains an Android Studio source project and implemented components, plus verified domain/parser tests. It is not a compiled APK and the Baly Driver-specific live reader/overlay acceptance test has not yet passed. Do not use the result for real ride decisions until those device tests succeed and vehicle cost settings are accurate.
