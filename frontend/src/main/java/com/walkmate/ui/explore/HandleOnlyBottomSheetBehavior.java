package com.walkmate.ui.explore;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import com.google.android.material.bottomsheet.BottomSheetBehavior;

/**
 * A BottomSheetBehavior whose sheet can ONLY be moved by a gesture that
 * starts on the designated drag-handle view.
 *
 * ─── Why two paths must be handled ──────────────────────────────────────
 *
 * The sheet can be driven by two entirely independent touch mechanisms:
 *
 *  PATH A – ViewDragHelper (onInterceptTouchEvent / onTouchEvent)
 *    The CoordinatorLayout intercepts raw MotionEvents and forwards them to
 *    a ViewDragHelper, which physically moves the sheet. We gate this on the
 *    handle rect so only ACTION_DOWN events that land on the handle enter the
 *    ViewDragHelper.
 *
 *  PATH B – NestedScroll (onNestedPreScroll / onNestedScroll / onStopNestedScroll)
 *    When a NestedScrollView (or RecyclerView) inside the sheet scrolls and
 *    reaches its boundary it calls requestDisallowInterceptTouchEvent(true) —
 *    which silences PATH A — then dispatches leftover displacement through
 *    the NestedScrollingParent3 contract. The default BottomSheetBehavior
 *    consumes this to move the sheet, completely bypassing PATH A.
 *
 *    Because the handle itself is INSIDE the NestedScrollView, a drag that
 *    STARTS on the handle also travels through PATH B: the NestedScrollView
 *    wins the intercept race and starts a nested-scroll sequence. Therefore:
 *
 *    • If the gesture started on the handle  → forward all nested-scroll
 *      callbacks to super so the sheet moves and snaps normally (PATH B
 *      acts like PATH A for this gesture).
 *    • If the gesture started elsewhere     → no-op all nested-scroll
 *      callbacks so the sheet never moves and the content scrolls freely.
 *
 * ─── Threading ───────────────────────────────────────────────────────────
 *
 *   lastDownOnHandle      – set on every ACTION_DOWN; records whether that
 *                           DOWN landed on the handle rect.
 *   dragAllowed           – same value, used for PATH A (ViewDragHelper gate).
 *   nestedScrollFromHandle– latched in onStartNestedScroll from
 *                           lastDownOnHandle; governs PATH B for the full
 *                           duration of that nested-scroll sequence.
 */
public class HandleOnlyBottomSheetBehavior<
    V extends View
> extends BottomSheetBehavior<V> {

    private View handleView;

    /** True when the ACTION_DOWN that started the current gesture was on the handle. */
    private boolean lastDownOnHandle = false;

    /** True while a PATH A (ViewDragHelper) gesture is in-flight from the handle. */
    private boolean dragAllowed = false;

    /** True while a PATH B (NestedScroll) sequence that originated on the handle. */
    private boolean nestedScrollFromHandle = false;

    // ── Constructors ─────────────────────────────────────────────────────

    public HandleOnlyBottomSheetBehavior() {}

    /** Used by the XML inflation path (app:layout_behavior="…"). */
    public HandleOnlyBottomSheetBehavior(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    // ── Public API ───────────────────────────────────────────────────────

    /** Must be called once after layout inflation. */
    public void setHandleView(@NonNull View handle) {
        this.handleView = handle;
    }

    // ════════════════════════════════════════════════════════════════════
    // PATH A — ViewDragHelper gate
    //
    // We check getRawX/Y against the handle's screen rect on each
    // ACTION_DOWN. If the DOWN did not land on the handle we return false
    // immediately without forwarding to super, so ViewDragHelper never
    // enters drag mode for this gesture.
    // ════════════════════════════════════════════════════════════════════

    @Override
    public boolean onInterceptTouchEvent(
        @NonNull CoordinatorLayout parent,
        @NonNull V child,
        @NonNull MotionEvent event
    ) {
        if (!isDraggable() || handleView == null) {
            return super.onInterceptTouchEvent(parent, child, event);
        }

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                Rect rect = new Rect();
                handleView.getGlobalVisibleRect(rect);
                lastDownOnHandle = rect.contains(
                    (int) event.getRawX(),
                    (int) event.getRawY()
                );
                dragAllowed = lastDownOnHandle;
                break;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                dragAllowed = false;
                break;
            default:
                break;
        }

        if (!dragAllowed) return false;
        return super.onInterceptTouchEvent(parent, child, event);
    }

    // ════════════════════════════════════════════════════════════════════
    // PATH B — NestedScroll gate
    //
    // onStartNestedScroll latches whether the sequence came from a handle
    // touch. All subsequent PATH B callbacks are either fully forwarded to
    // super (handle gesture → sheet moves + snaps) or fully suppressed
    // (non-handle gesture → content scrolls freely, sheet stays put).
    // ════════════════════════════════════════════════════════════════════

    @Override
    public boolean onStartNestedScroll(
        @NonNull CoordinatorLayout coordinatorLayout,
        @NonNull V child,
        @NonNull View directTargetChild,
        @NonNull View target,
        int axes,
        int type
    ) {
        // Capture whether this scroll sequence started on the handle
        // BEFORE calling super (super may reset internal state).
        nestedScrollFromHandle = lastDownOnHandle;
        return super.onStartNestedScroll(
            coordinatorLayout,
            child,
            directTargetChild,
            target,
            axes,
            type
        );
    }

    @Override
    public void onNestedScrollAccepted(
        @NonNull CoordinatorLayout coordinatorLayout,
        @NonNull V child,
        @NonNull View directTargetChild,
        @NonNull View target,
        int axes,
        int type
    ) {
        // Always forward: this initialises the behavior's internal velocity
        // tracker, which is needed for correct fling-to-snap on handle drags.
        super.onNestedScrollAccepted(
            coordinatorLayout,
            child,
            directTargetChild,
            target,
            axes,
            type
        );
    }

    // Path: frontend/src/main/java/com/walkmate/ui/explore/HandleOnlyBottomSheetBehavior.java

    @Override
    public void onNestedPreScroll(
        @NonNull CoordinatorLayout coordinatorLayout,
        @NonNull V child,
        @NonNull View target,
        int dx,
        int dy,
        @NonNull int[] consumed,
        int type
    ) {
        // dy < 0: Người dùng vuốt ngón tay XUỐNG (kéo sheet xuống).
        // dy > 0: Người dùng vuốt ngón tay LÊN (kéo sheet lên).

        if (dy < 0 || nestedScrollFromHandle) {
            // Cho phép super xử lý nếu là kéo xuống (mọi nơi) HOẶC kéo lên từ handle.
            super.onNestedPreScroll(
                coordinatorLayout,
                child,
                target,
                dx,
                dy,
                consumed,
                type
            );
        } else {
            // Kéo lên từ vùng trống: consume nothing để NestedScrollView tự scroll nội dung.
            consumed[1] = 0;
        }
    }

    @Override
    public void onNestedScroll(
        @NonNull CoordinatorLayout coordinatorLayout,
        @NonNull V child,
        @NonNull View target,
        int dxConsumed,
        int dyConsumed,
        int dxUnconsumed,
        int dyUnconsumed,
        int type,
        @NonNull int[] consumed
    ) {
        // Tương tự, cho phép super xử lý overflow nếu kéo xuống hoặc từ handle.
        if (dyUnconsumed < 0 || nestedScrollFromHandle) {
            super.onNestedScroll(
                coordinatorLayout,
                child,
                target,
                dxConsumed,
                dyConsumed,
                dxUnconsumed,
                dyUnconsumed,
                type,
                consumed
            );
        }
    }

    @Override
    public void onStopNestedScroll(
        @NonNull CoordinatorLayout coordinatorLayout,
        @NonNull V child,
        @NonNull View target,
        int type
    ) {
        // QUAN TRỌNG: Luôn gọi super ở đây để Behavior tính toán lực vuốt (velocity)
        // và tự động "hít" (snap) về các nấc COLLAPSED / EXPANDED / HIDDEN.
        super.onStopNestedScroll(coordinatorLayout, child, target, type);
        nestedScrollFromHandle = false;
    }
}
