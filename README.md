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
  - Uses bundled ML Kit Barcode Scanning (`com.google.mlkit:barcode-scanning`, `Barcode.FORMAT_QR_CODE` only) with zero network dependency.
  - Throttled YUV_420_888 `ImageReader` (`maxImages = 2`) with an atomic frame-drop guard for smooth performance on 4 GB RAM phones.
  - Supports Android Photo Picker (`PickVisualMedia`) for gallery/screenshot QR decoding with memory-safe bitmap downsampling and no broad storage permissions.

- **UPI & Safe Link Inspection (`upi/`, `links/`)**:
  - Parses `upi://pay?...` URIs (Payee Name, UPI ID, Amount, Currency, Note, Merchant Code, Reference) and launches installed UPI apps via Android's system chooser without altering payment parameters or automating PIN/approval.
  - Inspects web URLs for suspicious traits (HTTP, `@` userinfo spoofing, Punycode `xn--`, raw IP hosts, non-standard ports, dangerous schemes `javascript:`/`file:`/`intent:`/`data:`).
  - Optional, user-initiated redirect inspection (`RedirectResolver`) with `followRedirects(false)`, 5s timeouts, max 6 hops, loop detection, `HEAD` with minimal `Range: bytes=0-0` `GET` fallback, and blocking of dangerous redirect schemes.

- **Diagnostics & Settings (`ui/`)**:
  - Built-in **Camera Diagnostics** screen lists every discovered logical and physical sensor and lets users test live Camera2 previews on each accessible camera directly.
  - **Settings** provides System / Light / Dark appearance (DataStore), Diagnostics shortcut, and Privacy / About information.

## Build Instructions

1. Open the project in Android Studio (AGP 9.1.1 / Kotlin 2.4.21 / Compile SDK 36 / Min SDK 24) or run Gradle from CLI:
   - Debug APK: `gradle :app:assembleDebug`
   - Release APK: `gradle :app:assembleRelease` (configured via signing environment variables `KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_PASSWORD`)
   - Unit & Robolectric Tests: `gradle :app:testDebugUnitTest`

## Device Compatibility & Honest Hardware Limitations (Samsung Galaxy A12 & Similar Budget Devices)

- **No Physical Device Testing in Cloud Environment**: Verification in this build environment uses JVM unit tests and Robolectric (`@Config(sdk = [36])`); live Camera2 enumeration on a physical Samsung Galaxy A12 could not be executed in the container because no physical device or ADB emulator is attached.
- **OEM Firmware Restrictions**: On some budget Android devices (including certain Samsung Galaxy A12 / MediaTek & Exynos firmware builds), the OEM camera HAL restricts the secondary ultra-wide sensor to the preinstalled stock camera app and does not expose it in `CameraManager.getCameraIdList()` or `CameraCharacteristics.physicalCameraIds`.
- If a device's firmware does not expose the ultra-wide sensor via public Camera2 APIs, Android prohibits third-party apps from opening it without root or proprietary OEM SDKs. In that case, WideQR will honestly display **No ultra-wide camera found** or **Ultra-wide not accessible** and guide the user to **Camera Diagnostics** or **Gallery** scanning rather than faking a wide view or silently using the blurry 1x main camera.
