# :playback:service

The main service doing media playback.

`Media3PlaybackService` is the active implementation, built on AndroidX Media3/ExoPlayer.
`PlaybackService` is legacy and will throw an exception if started — it exists only during the transition period.

External callers should interact with the service through `PlaybackController`, which provides a
`bindToMedia3Service()` helper that connects a `MediaController` and runs a callback on it.
The `MediaController` exposes the standard Media3 `Player` interface: `seekTo(positionMs)`,
`play()`, `pause()`, `getCurrentPosition()`, `getPlaybackParameters()`, etc.
Each call to `bindToMedia3Service()` creates a short-lived connection that is released after the
callback returns.

Media buttons reach the service as `Intent.ACTION_MEDIA_BUTTON` intents that carry an
`EXTRA_KEY_EVENT`, which `MediaLibrarySessionCallback.onMediaButtonEvent` turns into player calls.
Build them with `MediaButtonStarter`: `createIntent()` for a headset or lock screen button and
`createPendingIntent()` for the home screen widget. The callback tells the two apart, so that the
widget skips the episode while the headset performs the action the user configured.

The buttons of the notification and of the lock screen are `CommandButton`s that the session
publishes through `setMediaButtonPreferences`. Each of them carries either a `Player` command or
one of the `SessionCommand`s declared in `MediaLibrarySessionCallback`, so a connected
`MediaController` can read the current buttons with `getMediaButtonPreferences()` and send the
command of a button instead of hard coding its identifier.
