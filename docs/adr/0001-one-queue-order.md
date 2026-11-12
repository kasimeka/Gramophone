# One queue order; shuffle rewrites it

**Status:** accepted, implemented

A queue has exactly one order: the order it is shown in, played in, edited in, and exported in. Shuffle is not a parallel playback order that sits on top of a canonical one; shuffling replaces the queue order (currently by moving the playing row to the front and shuffling every other row after it). Because there is nothing to restore, shuffle becomes a one-shot reorder action rather than an on/off mode; both “shuffle upcoming” and “shuffle everything” are intended scopes. m3u8 exports are snapshots of the queue order at export time, not continuously updated files.

This was chosen over keeping a canonical order plus a separate shuffle order because the two orders drift apart: reordering the visible list then no longer reliably determines what plays next, and the queue stops being a truthful record of play order. The accepted cost is that turning shuffle off cannot recover the pre-shuffle order, and the existing shuffle toggle/state model must be replaced when the change is implemented.

**Implementation scope:** the first slice builds shuffle everything only; shuffle upcoming is deferred. Repeat-all replays the existing order and no longer reshuffles at wrap. Shuffle is available on inactive queue previews and anchors on that queue's stored resume row. No migration is implemented; existing shuffle data is discarded with the old app data.
