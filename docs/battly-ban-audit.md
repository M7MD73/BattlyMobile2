# Battly and Battly Worlds ban audit - 2026-10-22

## Verified Contract

- General Battly bans are evaluated by the shared API for account, trusted client
  IP and an app-scoped device claim. IP proxy handling and IPv4-mapped addresses
  have regression coverage. Microsoft/local credentials are not sent as Battly tokens.
- Device claims are SHA-256 hashes of ANDROID_ID, sent only after the existing
  privacy notice is accepted. The server stores an HMAC pseudonym under a stable
  secret. No IMEI or serial number is accessed.
- Registration requires a verified, permitted Battly account. Matching is limited
  to its explicitly registered devices; other accounts' unrelated devices are not
  transitively banned. Revoking the originating ban releases its device restriction.
- Both legacy test.bans records and admin-panel data.isBanned records now affect
  registered devices. Expiry and zero/epoch indefinite values match account policy.
- Worlds sanctions are separate, with worlds/host/join/public/invite/voice scopes.
  A voice-only sanction leaves the game room connected. Hosting sanctions close
  the hosted room and signal connected guests; joining that expired room is refused.

## Corrected Mobile Paths

- Worlds HTTP calls and its Socket.IO handshake include the consented device claim.
  A missing claim is omitted, not sent as an invalid empty identifier.
- A confirmed general ban blocks the UI, sends Escape when gameplay is grabbed,
  disconnects Worlds/Terracotta and voice, stops public-room heartbeats and clears
  the active room. The JVM is not killed abruptly; no world or account file is deleted.
- FCM is only a wake-up. A package-scoped, nonexported receiver makes the game
  process revalidate access rather than relying on another process's preference cache.
- The foreground access session rechecks on socket connection and every 30 seconds
  as a single-flight fallback. Closing it unregisters the receiver and cancels checks.
- room_closed now disconnects the Mobile room and voice. Scope matching does not
  depend on a nonempty reason; malformed scope elements do not crash the client.
- Pending Worlds authorization results are invalidated on restrictions/disconnect,
  so an earlier allow response cannot reconnect a room after it has been closed.

## Production Deployment

Read-only inspection through the saved Battly_API SSH profile confirmed that the
API lacked BATTLY_DEVICE_HASH_SECRET in both its file and running process. A normal
anonymous access check returned 200; adding a valid device claim returned 503.
There were zero accounts with registered mobileDeviceHashes before initialization.

The user approved deployment. lib/mobileDevices.js, lib/battlyBans.js and the
stable secret configuration were activated. The API index.js also receives only
the mountAccessStatus(app, battlyBans) wiring change, staged from the actual remote
file rather than overwriting it with an unrelated local entrypoint. The secret was generated on the server,
never printed or copied into this repository. The PM2 API was reloaded. Module
checksums and the runtime secret presence were verified afterward. Backups are in
/var/backups/battly-api/mobile-bans-*; these contain private configuration and must
not be published. Do not rotate or delete the device secret during future updates.

Production anonymous access and device-aware access now return 200 with the correct
deviceChecked flag. The production /access Socket.IO handshake succeeds using its
polling transport. A forced WebSocket-only attempt from this workstation timed out;
WebSocket upgrade itself is not marked verified. Long-polling Socket.IO is a live
connection, not the 30-second access fallback.

MongoDB in production is standalone, so Change Streams are unavailable. Direct
DB updates (such as the Web admin panel) otherwise rely on the 30-second fallback.
The API now exposes POST /api/v2/launcher/access/reconcile: only a currently verified
administrator can trigger fresh authoritative checks and immediate socket events.
Unauthenticated and ordinary-account requests are rejected, and caller-supplied
ban decisions/roles are ignored. The actual Web panel has a corresponding local
patch that notifies this endpoint after individual/bulk ban/unban operations;
deploying that Web change still requires the separate requested approval.

## Test Evidence

- 35 API tests pass, including real local HTTP/Socket.IO connections and isolated
  MongoDB for shared-device propagation, live account revocation, unban and expiry.
- Host-sanction, guest-sanction and voice-only live socket tests pass.
- An authenticated panel HTTP notification revokes an idle socket with the periodic
  timer set to one hour, proving it does not wait for polling. Two Web notifier
  tests pass for credentials, pinned HTTPS, redirect refusal and network failure.
- 303 Android unit tests pass, including blank-reason and malformed-scope cases.
- Three Android instrumentation tests pass on Hytale API35: scoped ban persistence,
  trusted-host/consent device headers, and the actual blocking/retry UI.
- Signed 2.2.8 APK/AAB and debug/test APKs build. R8 remains disabled.
- The ten news feeds, English fallback and localization audit still pass; these
  ban changes do not add untranslated user-visible strings.

API sources are C:/JS_Projects/Battly/BattlyAPIV3. Production-safe verification:
node scripts/verify-mobile-ban-access.cjs. Full local tests use the explicit test
files listed in build/ban-api-final.log and BATTLY_TEST_MONGOD for the isolated DB.
No real account was banned, unbanned, or otherwise edited for testing.

## Boundaries

API-issued bans and reconciled panel updates notify connected honest clients immediately;
there is no universal zero-latency guarantee. Until the Web hook is deployed,
direct Web-panel database updates retain their 30-second fallback. If events are missed, Mobile checks
again every 30 seconds plus request/network latency. Existing installations need
the rebuilt Mobile app for the new client enforcement paths.

New bans cannot be delivered to a genuinely offline device. Previously confirmed
restrictions remain cached; outages do not manufacture a new ban or release a
confirmed device ban without an explicit device-checked allow response.

ANDROID_ID is scoped to signing key, Android user and device. Factory resets or a
different signing key/user can change it. Claims from a modified client are not
hardware attestation. Thus this is useful device-ban enforcement, not an immutable,
unforgeable hardware blacklist. No Play Integrity enforcement was added.

This audit does not claim a real production user was banned during a live Minecraft
match: active revocation was tested with isolated accounts and real local sockets,
and the Mobile blocking UI was tested separately on Android.

Android reference: https://developer.android.com/reference/android/provider/Settings.Secure#ANDROID_ID
