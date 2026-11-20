# WideQR — Ultra-Wide Lens QR Scanner & UPI Link Handler

WideQR is a lightweight, privacy-first native Android application (Kotlin + Jetpack Compose + Camera2 + local ML Kit QR decoding) built for devices where the main 1x camera suffers from autofocus issues while the physical ultra-wide lens produces sharp images.

## Key Features & Architecture

- **Real Camera2 Hardware Discovery (`camera/`)**:
  - Enumerates `CameraManager.getCameraIdList()` and API 28+ `CameraCharacteristics.physicalCameraIds` on `LOGICAL_MULTI_CAMERA` devices.
  - Extracts `LENS_INFO_AVAILABLE_FOCAL_LENGTHS`, `SENSOR_INFO_PHYSICAL_SIZE`, `SCALER_STREAM_CONFIGURATION_MAP`, `CONTROL_AF_AVAILABLE_MODES`, `LENS_INFO_MINIMUM_FOCUS_DISTANCE`, `INFO_SUPPORTED_HARDWARE_LEVEL`, and `FLASH_INFO_AVAILABLE`.
  - Computes horizontal/vertical/diagonal Field of View (FOV) and an estimated 35mm-equivalent focal length to classify Main, Ultra-Wide, Telephoto, Front, and low-resolution auxiliary sensors (preventing 2MP macro/depth sensors from being misclassified as ultra-wide).
  - Supports both standalone openable Camera2 IDs and API 28+ logical-camera physical streams (`OutputConfiguration.setPhysicalCameraId`).
  - **Honest Verification States**: `VERIFIED_ULTRA_WIDE`, `LIKELY_ULTRA_WIDE`, `WIDE_CAMERA_DETECTED_UNVERIFIED`, `ULTRA_WIDE_NOT_ACCESSIBLE`, `NO_ULTRA_WIDE_CAMERA_FOUND`, and `CAMERA_ACCESS_ERROR`.
  - **Never fakes ultra-wide**: Never uses digital zoom (`0.5x`) or AI upscaling, and never silently switches to a blurry 1x main lens if the ultra-wide lens is unavailable.

- **Local QR Decoding (`scanner/`)**:
  - Uses bundled ML Kit Barcode Scanning (`com.google.mlkit:barcode-scanning`) with zero network dependency.
  - Defaults to `Barcode.FORMAT_QR_CODE` only, which keeps per-frame decode latency low on 4 GB RAM devices like the Galaxy A12. The opt-in **Settings → Scan product & 1D barcodes** toggle adds EAN-13/EAN-8/UPC-A/UPC-E/Code 128/Code 39/ITF/Data Matrix/Aztec and recreates the ML Kit client on change.
  - Throttled YUV_420_888 `ImageReader` (`maxImages = 2`) with an atomic frame-drop guard for smooth performance on 4 GB RAM phones.
  - Supports Android Photo Picker (`PickVisualMedia`) for gallery/screenshot QR decoding with memory-safe bitmap downsampling and no broad storage permissions.

- **UPI & Safe Link Inspection (`upi/`, `links/`)**:
  - Parses `upi://pay?...` URIs (Payee Name, UPI ID, Amount, Currency, Note, Merchant Code, Reference) and launches installed UPI apps via Android's system chooser without altering payment parameters or automating PIN/approval.
  - Inspects web URLs for suspicious traits (HTTP, `@` userinfo spoofing, Punycode `xn--`, raw IP hosts, non-standard ports, dangerous schemes `javascript:`/`file:`/`intent:`/`data:`).
  - Optional, user-initiated redirect inspection (`RedirectResolver`) with `followRedirects(false)`, 5s timeouts, max 6 hops, loop detection, `HEAD` with minimal `Range: bytes=0-0` `GET` fallback, and blocking of dangerous redirect schemes.
  - **SSRF guard**: a custom OkHttp `Dns` rejects loopback, RFC1918, link-local (169.254/16, fe80::/10), CGNAT (100.64/10), IPv4-mapped IPv6, ULA (fc00::/7), multicast and unspecified addresses before any socket is opened, so a short link can never be used to probe the user's LAN or a cloud metadata endpoint (DNS-rebinding resistant).

- **Share into WideQR (`MainActivity`)**:
  - Registers a plain `ACTION_SEND` / `text/plain` share-sheet receiver (`launchMode="singleTop"`, handled in `onNewIntent`) so links or UPI strings from a browser or chat app can be parsed by the same pipeline as a camera scan.
  - Deliberately **no** `upi://pay` `<intent-filter>`: WideQR must never appear in Android's "Open UPI App" chooser as a payment handler. `AndroidManifest.xml` keeps only `<queries>` entries so `UPIIntentHandler` can *launch* real payment apps.

- **Tap-to-Meter (`camera/CameraPreviewController.focusOnTap`)**:
  - Tapping the preview maps to a `MeteringRectangle` on `SENSOR_INFO_ACTIVE_ARRAY_SIZE` and always sets `CONTROL_AE_REGIONS` — the only meaningful control on the fixed-focus ultra-wides common on budget devices (`CONTROL_AF_MODE_OFF`).
  - `CONTROL_AF_REGIONS` + `AF_TRIGGER_START` are applied only when the lens reports autofocus support and is opened as a standalone camera, then released back to continuous AF after 2.5 s.

- **Opt-in Main Camera Fallback (`models/ScannerTarget`)**:
  - Ultra-wide is still always preferred. Only when the ultra-wide is unusable **and** the user enabled **Settings → Allow 1x main camera fallback** does the scanner drive the back 1x camera.
  - Fallback sessions are labelled with a persistent **Main camera fallback** badge plus "less sharp" copy, and scan results record `Main camera 1x fallback (ID: …)` as the source. Nothing auto-switches silently.

- **Opt-in Local Scan History (`history/`, Room)**:
  - Off by default, matching the Privacy copy. When enabled, results are stored in an on-device Room database only and can be wiped with **Clear scan history**.
  - The Privacy section text now states the toggle, the on-device scope, and the clear control instead of promising "no scan history is saved".

- **Diagnostics & Settings (`ui/`)**:
  - Built-in **Camera Diagnostics** screen lists every discovered logical and physical sensor and lets users test live Camera2 previews on each accessible camera directly.
  - **Settings** provides System / Light / Dark appearance (DataStore), Diagnostics shortcut, Scanner Behaviour toggles (main-camera fallback, product/1D barcodes, scan history + clear), and Privacy / About information.

- **Clipboard Hardening (`ui/SecureClipboard`)**:
  - UPI IDs, payment links, Wi-Fi passwords and Wi-Fi credentials are copied with `ClipData` extras `android.content.extra.IS_SENSITIVE` (honoured on Android 13+, where the platform also suppresses clip previews) and are auto-cleared after 60 s if the clipboard still holds that value.
  - Release builds run R8 with `isMinifyEnabled = true` + `isShrinkResources = true`; `proguard-rules.pro` keeps kotlinx.serialization serializers, Compose runtime/UI, Camera2, ML Kit, Room entities/DAOs and OkHttp.

## Build Instructions

1. Open the project in Android Studio (AGP 9.1.1 / Kotlin 2.4.21 / Compile SDK 36 / Min SDK 24) or run Gradle from CLI:
   - Debug APK: `gradle :app:assembleDebug`
   - Release APK: `gradle :app:assembleRelease` (configured via signing environment variables `KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_PASSWORD`)
   - Unit & Robolectric Tests: `gradle :app:testDebugUnitTest`

## Device Compatibility & Honest Hardware Limitations (Samsung Galaxy A12 & Similar Budget Devices)

- **No Physical Device Testing in Cloud Environment**: Verification in this build environment uses JVM unit tests and Robolectric (`@Config(sdk = [36])`); live Camera2 enumeration on a physical Samsung Galaxy A12 could not be executed in the container because no physical device or ADB emulator is attached.
- **OEM Firmware Restrictions**: On some budget Android devices (including certain Samsung Galaxy A12 / MediaTek & Exynos firmware builds), the OEM camera HAL restricts the secondary ultra-wide sensor to the preinstalled stock camera app and does not expose it in `CameraManager.getCameraIdList()` or `CameraCharacteristics.physicalCameraIds`.
- If a device's firmware does not expose the ultra-wide sensor via public Camera2 APIs, Android prohibits third-party apps from opening it without root or proprietary OEM SDKs. In that case, WideQR will honestly display **No ultra-wide camera found** or **Ultra-wide not accessible** and guide the user to **Camera Diagnostics** or **Gallery** scanning rather than faking a wide view.
- The only way WideQR will ever use the 1x camera is through the explicit, off-by-default **Allow 1x main camera fallback** toggle, and that session is always badged **Main camera fallback** so a blurry read is never mistaken for ultra-wide scanning.
- **Verification note**: this build environment has no JDK or Android SDK, so `isMinifyEnabled = true`, the Room/KSP wiring, DataStore toggles and Camera2 changes could not be compiled here. Run `gradle :app:assembleDebug :app:testDebugUnitTest` and one release `assembleRelease` in Android Studio before shipping; also confirm KSP 2.3.12 resolves against Kotlin 2.4.21 with the pinned Room version.
