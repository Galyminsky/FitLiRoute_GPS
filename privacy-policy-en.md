# Privacy Policy for FITLiROUTE GPS

**Last updated: November 10, 2026**

## 1. Introduction

This Privacy Policy explains how **FITLiROUTE GPS** (the “App”) accesses, uses, stores, and protects user data.

FITLiROUTE GPS is designed to record GPS routes, display movement on a map, calculate distance, duration, and speed, and provide access to saved route history.

For privacy-related inquiries, contact **galyaminsky@gmail.com**.

## 2. Location Data

The App requests access to precise device location to provide its core functionality.

Location data is used to:

- determine the user's current location;
- record traveled routes;
- calculate distance and speed;
- display routes on a map;
- save route history locally on the device.

Route recording is initiated by the user through the **START** button.

While recording is active, the App may continue accessing location data when minimized or when the device screen is locked. This functionality uses an Android location Foreground Service with a system notification.

Recording ends when stopped by the user or under other conditions defined by the App.

Users may revoke location permissions at any time through Android settings. GPS recording features will not be available without the required permission.

## 3. Local Route Storage

Recorded GPS routes, including coordinates, route names, and related statistics, are stored locally in the device's internal application storage.

Completed routes are stored using a Room database. Unfinished recordings may be temporarily preserved in local application files.

FITLiROUTE GPS does not provide its own user account system or cloud route synchronization service.

The App does not include a developer-operated feature that uploads saved route history to a proprietary server.

Route data is excluded from standard application backups under the current App configuration.

## 4. Analytics Data

The App uses **Google Firebase Analytics** to obtain statistical information about App usage and operation.

Firebase Analytics may automatically process information such as:

- application launch and usage events;
- application instance identifiers;
- device information, operating system information, and App version;
- diagnostic and technical information supported by the SDK.

Such data may be transmitted to and processed by Google under its applicable terms and privacy policies.

FITLiROUTE GPS does not implement custom transmission of recorded route history or route coordinates to Firebase Analytics. However, data processing performed by the SDK depends on its version and configuration.

For more information, see https://policies.google.com/privacy.

## 5. Map Services

The App uses the **osmdroid** library and OpenStreetMap map data to display maps.

When map tiles are loaded, network requests are made to the configured map tile provider.

The provider may receive technical request information, including the device's IP address and information about the requested map area.

Map tile requests do not constitute publishing or cloud storage of recorded route history.

For more information, see https://osmfoundation.org/wiki/Privacy_Policy.

## 6. Third-Party Services

The App uses third-party services for specific functions:

- **Google Firebase Analytics** for application usage analytics;
- **OpenStreetMap map tile provider** for loading map images.

These providers process information under their own applicable privacy policies and agreements.

The App does not provide a feature to publicly publish or share routes through a developer-operated server.

## 7. Data Security

Local route information is stored in the App's internal storage using Android application isolation mechanisms.

The App relies on standard Android platform security features and HTTPS connections for supported external services.

Nevertheless, no method of storage or electronic transmission can be guaranteed to be completely secure.

## 8. Data Retention and Deletion

Saved routes remain on the device until the user deletes them in the App or removes the App's data.

Users can delete saved routes through the FITLiROUTE GPS interface.

Clearing the App's data through Android settings or uninstalling the App generally removes its local database and files.

Analytics and technical information processed by Google Firebase Analytics and map service providers may be retained according to those providers' own retention policies.

For inquiries about data processing, contact **galyaminsky@gmail.com**.

## 9. Permission Management

Users can manage the App's location permissions through Android settings.

Denying or revoking location permissions limits the features that require location access but does not automatically delete previously saved routes.

## 10. Changes to This Policy

This Privacy Policy may be updated to reflect changes to FITLiROUTE GPS, its technologies, or applicable requirements.

The current version will be available on the publicly accessible Privacy Policy page. The latest update date will be shown at the beginning of this document.

## 11. Contact Information

**Application:** FITLiROUTE GPS

**Privacy contact:** galyaminsky@gmail.com
