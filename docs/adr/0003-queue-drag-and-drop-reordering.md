# Queue drag-and-drop reordering

**Status:** accepted

Queue song drag-and-drop must reorder incrementally during the drag, keeping the dragged row under the finger and the visual list position aligned with the logical queue order at every swap. On drop there is no extra reconciliation: the item stays where it was released because visual and logical positions never diverged. This applies to the single queue / queue sheet, and any Compose/LazyColumn reimplementation must preserve the same behavior.

## Upstream reference (`upstream/beta`, e.g. `e6fa468f`)

Upstream implements the queue sheet on `RecyclerView` + `LinearLayoutManager` with `ItemTouchHelper`. The adapter exposes an incremental move contract (`onRowMoved` must call `notifyItemMoved` if it actually moves), and the callback forwards every `ItemTouchHelper.onMove` to it immediately. Drag starts from the handle; edge auto-scroll is provided by `ItemTouchHelper` defaults. There is no manual scroll or offset compensation on drop.

`app/src/main/java/org/akanework/gramophone/ui/components/EditSongAdapter.kt`:

```kotlin
// Like SongAdapter, but without layouts, sorting, or flows; instead supporting drag, swipe & remove
abstract class EditSongAdapter(...) : MyRecyclerView.Adapter<EditSongAdapter.ViewHolder>() {
    // ...
    @SuppressLint("ClickableViewAccessibility")
    holder.dragHandle.setOnTouchListener { _, ev ->
        if (ev.action == MotionEvent.ACTION_DOWN)
            startDrag(holder)
        false
    }

    abstract fun startDrag(holder: ViewHolder)
    abstract fun onRowMoved(from: Int, to: Int) // must call notifyItemMoved() if actually moving
    abstract fun removeItem(pos: Int) // must call notifyItemRemoved() if actually removing

    inner class PlaylistCardMoveCallback :
        ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            ItemTouchHelper.START or ItemTouchHelper.END
        ) {
        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
        ): Boolean {
            val vhBap = viewHolder.bindingAdapterPosition
            val tBap = target.bindingAdapterPosition
            if (vhBap != RecyclerView.NO_POSITION && tBap != RecyclerView.NO_POSITION) {
                onRowMoved(vhBap, tBap)
                return true
            }
            return false
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
            val vhBap = viewHolder.bindingAdapterPosition
            if (vhBap != RecyclerView.NO_POSITION) {
                removeItem(vhBap)
            }
        }
    }
}
```

`app/src/main/java/org/akanework/gramophone/ui/components/PlaylistQueueSheet.kt` (wiring, anchoring, and per-move reorder):

```kotlin
recyclerView = findViewById<MyRecyclerView>(R.id.recyclerview)!!

playlistAdapter = PlaylistCardAdapter()
val callback = playlistAdapter.PlaylistCardMoveCallback()
touchHelper = ItemTouchHelper(callback)
touchHelper.attachToRecyclerView(recyclerView)
recyclerView.layoutManager = LinearLayoutManager(context)
recyclerView.adapter = playlistAdapter
recyclerView.fastScroll(null, null)

inner class PlaylistCardAdapter : EditSongAdapter(activity, true, lockEdit) {
    // ...
    override fun onRowMoved(from: Int, to: Int) {
        val mediaController = activity.getPlayer()
        val from1 = mqState!!.playlist.first.removeAt(from)
        mqState!!.playlist.first.replaceAllSupport { if (it > from1) it - 1 else it }
        val movedItem = mqState!!.playlist.second.removeAt(from1)
        val to1 = if (to > 0) mqState!!.playlist.first[to - 1] + 1 else 0
        mqState!!.playlist.first.replaceAllSupport { if (it >= to1) it + 1 else it }
        mqState!!.playlist.first.add(to, to1)
        mqState!!.playlist.second.add(to1, movedItem)
        mediaController?.moveMediaItem(from1, to1)
        notifyItemMoved(from, to)
        val currentIndex = currentMediaItemIndex
        if (currentIndex != null) {
            if (currentIndex == from)
                currentMediaItemIndex = to
            else if (from < to && from < currentIndex && currentIndex <= to)
                currentMediaItemIndex = currentIndex - 1
            else if (from > to && to <= currentIndex && currentIndex < from)
                currentMediaItemIndex = currentIndex + 1
        }
        mqState!!.updateTimer() // TODO: this could be more efficient
    }

    override fun startDrag(holder: ViewHolder) {
        touchHelper.startDrag(holder)
    }
}
```

Key properties to preserve: `LinearLayoutManager` anchors the list, `ItemTouchHelper` is attached to the same `RecyclerView`, each accepted `onMove` performs the logical reorder (`playlist` + player `moveMediaItem`) and `notifyItemMoved(from, to)` immediately, edge auto-scroll comes from `ItemTouchHelper` defaults, and `onRowMoved` does not perform manual scroll/offset compensation on drop. The visual slot and logical/player order advance together, so the drop position is final.

## Failure modes to avoid (Compose/LazyColumn reimplementation)

- **Drop snap.** Reordering the backing list only on drop while the dragged item is rendered with a visual translation, then resetting that translation or forcing `scrollToItem` compensation, makes logical and visual positions diverge during the drag. The visible result is a jump/snap on release. Avoid by swapping/moving data and emitting the move for each crossed slot during the drag so visual and logical positions stay aligned (or by keeping a single source of truth for the dragged item's slot that both layout and data follow continuously), never by drop-time reconciliation.
- **Top/first item pinned / finger drift.** With incremental in-drag reordering, the top item can stay pinned at the top and never drag down into the middle if drag offset, auto-scroll, or boundary math is wrong. Common causes: accumulating drag offset at index 0 with no compensating swap/scroll, failing to clamp/renormalize the dragged offset relative to the item's current slot and any scroll distance consumed by auto-scroll, or fighting viewport anchoring at list bounds. The finger then drifts away from the item and items appear to "fly" incorrectly. The correct implementation must keep the dragged item under the finger across swaps and edge auto-scroll (especially at list bounds) with no drop-time snap.

## Context

Consistent with the singular queue order (ADR 0001) and durable queue state (ADR 0002), each drag swap is an immediate mutation of the one queue order, not a temporary visual overlay: the shown order, play order, and persisted/mirrored order must agree as the drag progresses.
