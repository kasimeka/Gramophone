# Gramophone

User-facing domain language for Gramophone's playback and collection features.

## Language

**Queue**:
A named ordered list of songs with its own playback position and repeat mode. Only the active queue survives an app restart.
_Avoid_: Playlist, mix, list

**Playlist**:
A legacy library collection. It is being folded into Queue; do not introduce the word for new features.
_Avoid_: Queue (when the legacy concept is meant)

**Active queue**:
The queue currently loaded in the player.
_Avoid_: Current playlist, now playing list

**Inactive queue**:
A queue that is not currently loaded in the player. It may be previewed without being played, but it does not survive an app restart.
_Avoid_: Background queue, stored playlist

**Queue order**:
The single order in which a queue's rows are stored and played. Shuffle is a reordering of this order, not a parallel order.
_Avoid_: Canonical order, shuffle order, media-item order

**Shuffle**:
A one-shot action that reorders a queue's single order. It moves the playing row to the front and randomly reorders every other row after it. It is not a mode and has no off state.
_Avoid_: Shuffle mode, shuffle toggle, random mode

**Play next**:
Move an existing row in a queue so that it becomes the row immediately after the currently playing row. It never duplicates the song.
_Avoid_: Queue next, play after, insert next

## Playback surfaces

**Mini player**:
The collapsed now-playing surface pinned above the content while an active queue exists.
_Avoid_: Now playing banner, preview player

**Full player**:
The expanded player surface opened from the mini player.
_Avoid_: Now playing screen

**Resume state**:
The saved active queue, current row, playback position, repeat mode, and speed/pitch restored when the app launches.
_Avoid_: Last played, saved session
