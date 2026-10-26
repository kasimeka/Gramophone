# Durable queues, mirrored to m3u8

**Status:** accepted, planned

Gramophone is folding Playlists into Queues. Playlists were durable and Queues were not, so the queue became the durable entity: every queue lives in a SQLite database with its own order, bookmark, and repeat mode, and a portable `.m3u8` mirror named `<slug>_<uuid>.m3u8` is written asynchronously to `Android/media/<applicationId>/Queues/` on every change. The database is canonical; the mirror is for moving queues to other players, not for import. There is no migration and no compatibility path for playlists or the old `QueueBoard`/`LastPlayedManager` state.

This was chosen over keeping playlists as the durable store and instantiating queues from them, because that preserves two collection concepts and two vocabularies, and it was chosen over making the m3u8 files canonical because resume state, titles, and rename-safe identity need a structured store and watching external files invites drift and corruption. The accepted costs are that the exported files can lag behind the database, that hand-edited files are not read back, and that the upgrade starts with an empty queue list. `QueueBoard`'s in-memory queue list, pinning, expiry, and `isOriginal` replacement heuristics are retired: only the listener deletes a queue.
