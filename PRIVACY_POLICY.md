# Privacy Policy

**Last Updated: June 9, 2026**

This Privacy Policy describes how the **SaveTo** application (hereafter "the App") handles your information.

## 1. Zero Data Collection

The App is designed as a Free and Open Source Software (FOSS) utility. 

* **No Personal Data**: The App does not collect, record, request, or store any personal information, usage metrics, device identifiers, or analytics.
* **No Transmission**: The App does not declare the `android.permission.INTERNET` permission. This means it is technically impossible for the App to transmit any data off your device. All operations are performed strictly offline and locally.

## 2. Permissions & Storage Access

The App integrates with Android's system-wide sharing sheet to save shared content:
* **Storage Access Framework (SAF)**: The App utilizes standard Android APIs (`Intent.ACTION_CREATE_DOCUMENT` and `OpenDocumentTree`) to let you choose where shared files are saved.
* **No Direct File Access**: The App does not request dangerous read/write storage permissions (`READ_EXTERNAL_STORAGE` or `WRITE_EXTERNAL_STORAGE`). It only has transient access to the specific files you explicitly choose to share and save.

## 3. Third-Party Services & Analytics

* **No Ads**: The App is completely ad-free.
* **No Trackers**: The App does not integrate with any third-party SDKs, telemetry, crash-reporting (such as Firebase Crashlytics), or analytics suites.

## 4. Changes to This Privacy Policy

This policy may be updated from time to time. Any changes will be reflected by updating this document in the public repository.

## 5. Contact & Open Source Code

The App's source code is entirely open and auditable. You can inspect the code, build it from source, or report issues directly on the repository:

* **Repository**: [github.com/sudo-py-dev/SaveTo](https://github.com/sudo-py-dev/SaveTo)
