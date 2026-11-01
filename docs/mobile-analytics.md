# Battly Mobile analytics

Analytics is optional and disabled by default. Enable it in Settings > Share usage statistics. Revocation disables Firebase collection and resets its local analytics data; local usage history and authenticated social playtime are separate features. Offline mode suppresses analytics. No account ID is assigned to Firebase; profile names are no longer event parameters.

Events:
- launch_requested: version_id; launcher-side request, not proof that Minecraft started.
- version_download: version_id, result, error_type.
- game_launch: version_id, java_version, renderer; VM launch preparation, not proof a world loaded.
- game_exit: version_id, game_duration_seconds, exit_code, reason, unexpected. Duration uses elapsed realtime from launch preparation. Force-killed games may have no exit; do not treat this as complete playing time.
- Existing content installation and notification events remain subject to analytics consent.

Minecraft runs in a separate process without Firebase. Game events pass through an explicit, non-exported TelemetryService in :launcher. The receiver checks consent/offline state; Firebase is never initialized in the game process. Relay failure drops the event and never prevents playing.

GA4 setup: register event-scoped custom dimensions version_id, java_version, renderer, reason. The report reader in BattlyAPIV3 already discovers customEvent:version_id; it uses launch_requested consistently for version/hour rankings. Country comes from GA4, not a client locale or an added location permission. Existing aggregate/unset country rows are excluded from winners.

The production service account could read analytics but registering dimensions was blocked on 16 October 2026 because Google Analytics Admin API is disabled in project 490291541450. Enable that API and give the service account sufficient property permissions, then run scripts/analytics/register-mobile-dimensions.cjs in BattlyAPIV3. This is an idempotent, explicit setup command, never run automatically on API startup. No historical versions are fabricated; Google reporting propagation must be checked after setup.

Verification: :app_pojavlauncher:testDebugUnitTest and :app_pojavlauncher:compileDebugJavaWithJavac passed. No ADB device was attached, so game-process relay, native Settings UI and Firebase DebugView delivery remain unverified on hardware. No APK was released by this change.

References:
- https://firebase.google.com/docs/analytics/android/configure-data-collection
- https://developers.google.com/analytics/devguides/config/admin/v1/rest/v1beta/properties.customDimensions

Update 16 October: all four event dimensions were registered using the owner's Google Analytics browser session. The service account retains read-only access. Production Data API now discovers customEvent:version_id and readShare(today) succeeds, but returns an empty version value until reporting processes new data. Empty/unset version buckets are excluded from both version winners and distinct counts. Hardware receipt remains unverified.
