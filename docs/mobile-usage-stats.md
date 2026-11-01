# Mobile Usage History

The native panel is available in Battly Social through the statistics button.
It provides 7/30/90-day filters, active days, daily game/launcher duration,
first/last activity and hourly usage. The selected day also separates local
worlds, servers, Battly Worlds and game menus. Battly Worlds is a subset of
server time, not an additional duration to add to it.

The daily view uses animated cyan/amber line and area charts for Minecraft and
launcher time. The hourly view uses paired columns. Selecting a date on the
chart synchronizes the day selector; selecting an hour shows exact durations.
Keyboard arrows and accessibility scroll actions provide an alternative to
touch selection. Animations run once for 650 ms, respect the system animation
setting and are cancelled when the view is hidden or detached. Charts are native
Android Canvas views; no WebView or persistent animation loop is introduced.

## API Deployment

Deploy these files from BattlyAPIV3 together and restart the API:

- `routes/social/mobileGameStats.js`
- `routes/social/mobileUsageHistory.js` (new)
- `routes/social/getUserStats.get.js`

`routes/social/index.js` must already contain the `mobileGameStats` module
registration from the initial 2.2.7 statistics implementation. No change to the
root `index.js` is needed for the daily history extension.

POST `/api/v2/users/game-stats/mobile/heartbeat` accepts the existing session
payload plus `offsetMinutes` (minutes east of UTC). Server receipt time determines
credited duration; client timestamps cannot manufacture time. The heartbeat is
sent every 20 seconds and gaps are capped at 90 seconds. Long offline periods are
not reconstructed. Final packets remain as closed session records until their
existing seven-day TTL expires, preventing late retries from reopening a session.

GET `/api/v2/users/getUserStats?days=90&offsetMinutes=120` preserves all existing
totals and adds `data.history`. Both endpoints require the account token and use
the authenticated user ID, never a user ID from the request body.

The `test.mobile_game_daily` collection has one record per user/local date and
24 sparse hourly counters. A unique `(userId, date)` index prevents duplicate
day records; a TTL index expires history after 366 days. The API returns at most
90 days. All-time Mobile counters remain separate from desktop counters and
continue to be combined in the existing total fields. Daily history is Mobile
only; desktop totals contain no recoverable hourly history.

Dates/hours represent local time when the activity occurred. Past dates are not
relabelled when a user travels. First/last activity are boundaries, not a claim
of uninterrupted play between them. Hourly counters reveal gaps. Multiple
devices accumulate device time rather than deduplicating simultaneous use.

The Android client only tracks the currently selected Battly account and binds
each outgoing packet to its captured token. A saved Battly account does not
cause Microsoft or offline-account activity to be attributed to that account.
Offline mode continues to suppress heartbeats.

Older API responses remain readable: the panel displays an unavailable-history
message and keeps showing all-time totals. Old totals are never backfilled into
fabricated daily history. Deploy the backend before releasing the new APK.

## Verification

API (no production writes; in-memory persistence at the database boundary):

```text
node --test test/mobileGameStats.test.js test/mobileUsageHistory.test.js test/mobileStatsRoutes.test.js
```

Android model and on-device native panel:

```text
gradlew :app_pojavlauncher:testDebugUnitTest :app_pojavlauncher:assembleDebug :app_pojavlauncher:assembleDebugAndroidTest
adb shell am instrument -w -e class net.kdt.pojavlaunch.battlysocial.UsageHistoryPanelTest com.tecnobros.battlylauncher.debug.test/androidx.test.runner.AndroidJUnitRunner
```

The instrumentation test uses fixture history without sending fabricated playtime
to production. Complete onboarding before running it. Its screenshot is written
under the debug app's external files directory as `usage-history-ui.png`.
Runner setup follows the [Android instrumentation documentation](https://developer.android.com/training/testing/instrumented-tests/androidx-test-libraries/runner).
