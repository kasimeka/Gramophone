# Gramophone

User-facing domain language for Gramophone's playback and collection features.

## Language

**Queue**:
A durable, named, ordered list of songs with its own bookmark and repeat mode. Every queue persists; exactly one is active at a time.
_Avoid_: Playlist, mix, list

**Active queue**:
The queue currently loaded in the player.
_Avoid_: Current playlist, now playing list

**Inactive queue**:
A persisted queue that is not loaded in the player. It keeps its order, bookmark, repeat mode, and can be edited.
_Avoid_: Background queue, stored playlist

**Queue order**:
The single order in which a queue's rows are stored and played. Shuffle is a reordering of this order, not a parallel order.
_Avoid_: Canonical order, shuffle order, media-item order

**Shuffle**:
A one-shot action that reorders a queue's single order. It moves the anchor row to the front and randomly reorders every other row after it. It is not a mode and has no off state.
_Avoid_: Shuffle mode, shuffle toggle, random mode

**Play next**:
Move an existing row in a queue so that it becomes the row immediately after the anchor row. It never duplicates the song.
_Avoid_: Queue next, play after, insert next

**Bookmark**:
The row and playback position where a queue last stopped. Only that queue's own play button resumes from it.
_Avoid_: Resume state, last position

**Source**:
The library collection a queue was created from, such as an album, artist, folder, genre, favourites, recently added, all songs, or search results. It determines the queue's generated title, not its identity.
_Avoid_: Origin, seed

**Numbered queue**:
A queue created without a library source, titled `new queue #N`.
_Avoid_: Scratch queue, temporary queue

**Playlist**:
Removed. Gramophone no longer has playlists; the old collection concept, its UI, and its import path are gone.
_Avoid_: Using the word for a Queue

## Playback surfaces

**Mini player**:
The collapsed now-playing surface pinned to the bottom of the content area, above the tab bar when one is present, while an active queue exists.
_Avoid_: Now playing banner, preview player

**Full player**:
The expanded player surface opened from the mini player.
_Avoid_: Now playing screen

**Tab bar**:
The horizontally scrollable row of library section chips. Its sections and their order are user-configurable, and it is hidden when fewer than two sections are shown.
_Avoid_: Bottom navigation, nav bar, tabs

**Queues tab**:
The bottom-anchored list of every queue, with the active queue pinned last.
_Avoid_: Playlists tab

**Resume state**:
The last active queue and its bookmark, restored paused when the app launches.
_Avoid_: Last played, saved session
