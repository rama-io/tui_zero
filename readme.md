# Tui Zero

A Java port of **Tui**, a minimal, privacy-first local music player that skips metadata entirely.

## Permissions
| Permission | Why |
|---|---|
| Read media / external storage, All files access | List and play your music, including SD cards |
| Notifications | The playback notification that keeps playback running |
| Foreground service (media playback), Wake lock | Keep playing with the screen locked |

If playback still stops on your device, use **Settings → System → Allow background playback** to exempt the app from battery optimization.

## Visualizer

### Basic
```
$t: 1..45, inifinite, alternate
$u: 1..10, infinite, alternate
$x: cos($t*8*pi/180)*$u
$y: sin($t*8*pi/180)*$u
```
### Circular waveform
```
$t: 1..45, inifinite, alternate
$u: 1..10, infinite, alternate
$x: cos(t*8*pi/180)*(u+2sin(3t*pi/180))
$y: sin(t*8*pi/180)*(u+2sin(3t*pi/180))
```
### Mandala :: 3
```
$t: 1..360, inifinite, alternate
$x: cos(t*8*pi/180)*(cos(3t*pi/180))
$y: sin(t*8*pi/180)*(cos(3t*pi/180))
```
### Mandala :: 5
```
$t: 1..360, inifinite, alternate
$x: cos(t*8*pi/180)*(cos(5t*pi/180))
$y: sin(t*8*pi/180)*(cos(5t*pi/180))
```
### Mandala :: 7
```
$t: 1..360, inifinite, alternate
$x: cos(t*8*pi/180)*(cos(7t*pi/180))
$y: sin(t*8*pi/180)*(cos(7t*pi/180))
```
### Spiral
```
$t: 1..360, inifinite, alternate
$x: cos(t*8*pi/180)*(1+0.2*t)
$y: sin(t*8*pi/180)*(1+0.2*t)
```