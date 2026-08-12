# MoveOps Driver — Android

Native Kotlin driver app for the MoveOps logistics system. Companion to a
PHP/MySQL web app; this app talks to it over a REST API.

## Stack
- Native Kotlin, ViewBinding enabled (no Compose, no data binding)
- minSdk 26, targetSdk 37, compileSdk 37
- Retrofit + Gson, **CALLBACKS not coroutines** — match the existing style exactly
- FusedLocationProviderClient for location
- Material3 theme parent
- Package: com.lvms.driver (do not rename — "lvms" stays everywhere in code)

## Do not touch
ApiClient, SessionManager, AuthApi, TripApi, GpsApi, GpsTrackingService.
This layer works and has passed a one-hour background GPS stress test.
Only change it when a task explicitly says to.

## Hard-won constraints — do not "fix" these
- GpsTrackingService uses START_REDELIVER_INTENT, NOT START_STICKY.
  START_STICKY delivers a null Intent on OS-triggered restarts, which
  silently loses trip_id.
- GPS points are filtered to 50m accuracy before posting, to keep the
  admin live map clean.
- Background GPS works WITHOUT ACCESS_BACKGROUND_LOCATION because the
  service starts while the app is foregrounded. Confirmed empirically.
  Do not add that permission.
- API routing quirk: every endpoint goes through index.php?url=... with the
  route passed as an explicit @Query("url") parameter. These are NOT clean
  REST paths and NOT @Path templating. Follow the existing pattern.

## Known gap (accepted, not a bug to fix)
Force-stop or swipe-from-Recents kills tracking with no auto-restart.

## Environment
- BASE_URL in ApiClient.kt points at the live Hostinger site over https.
  If you find an http:// IP address there, it has not been migrated yet —
  do not "fix" it outside a task that says to.
- android:usesCleartextTraffic must be removed from AndroidManifest.xml
  once BASE_URL is https. It is a security finding if left in.
- Testing is on a physical device. Every change costs a Gradle build +
  install cycle, so batch related edits.

## Style
- Match existing conventions in the file you're editing.
- Do not refactor unrelated code.
- Do not add dependencies unless the task says to.
- At the end of a task, report: files changed, what to test. Nothing else.

## Trip lifecycle (server-side rules this app depends on)
- A trip row is only created AFTER its gatepass is approved. Every trip
  visible to a driver therefore has an approved gatepass.
- 'cancelled' is a TERMINAL trip status alongside 'completed'. A cancelled
  trip cannot be started, completed, or tracked.
- The server rejects POST /api/gps with HTTP 409 when a trip is not
  in_progress. That is not an error to retry — it means the trip ended.

## Out of scope — do not add
- Incident reporting. Incidents are reported by web users only. The
  /api/incidents route is deliberately commented out in api/index.php.
- Push notifications / FCM.
- Approving anything. Drivers execute trips; they do not authorize them.