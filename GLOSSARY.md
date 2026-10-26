# Gramophone

User-facing domain language for Gramophone's playback and collection features.

## Language

**Queue**:
A named, automatically saved ordered list of songs with its own playback position and repeat mode.
_Avoid_: Playlist, mix, list

**Playlist**:
A legacy library collection. It is being folded into Queue; do not introduce the word for new features.
_Avoid_: Queue (when the legacy concept is meant)

**Active queue**:
The queue currently loaded in the player.
_Avoid_: Current playlist, now playing list

**Inactive queue**:
A saved queue that is not currently loaded in the player. It may be previewed without being played.
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
