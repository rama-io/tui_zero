# Tui Zero

A Java port of **Tui**, a minimal, privacy-first local music player that skips metadata entirely. Built in the style of mako_zero: plain `android.app.Activity`, no libraries, no network.

## Differences from the Kotlin version
- Java, no dependencies, no `bohio` submodule
- Catppuccin themes only, plus a random Mocha option
- Flat music list, folders are not collapsible
- No rename/delete (track settings) dialog, so no write permission
- Playback is kept alive by a permanent media notification (foreground service + wake lock)

## Permissions
| Permission | Why |
|---|---|
| Read media / external storage, All files access | List and play your music, including SD cards |
| Notifications | The playback notification that keeps playback running |
| Foreground service (media playback), Wake lock | Keep playing with the screen locked |

If playback still stops on your device, use **Settings → System → Allow background playback** to exempt the app from battery optimization.

## Build
`./gradlew assembleDebug`

## License
GPL 3.0
