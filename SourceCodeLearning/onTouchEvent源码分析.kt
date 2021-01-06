
View.kt
public boolean onTouchEvent(MotionEvent event) {
        final float x = event.getX();
        final float y = event.getY();
        final int viewFlags = mViewFlags;
        final int action = event.getAction();

        final boolean clickable = ((viewFlags & CLICKABLE) == CLICKABLE
                || (viewFlags & LONG_CLICKABLE) == LONG_CLICKABLE)
                || (viewFlags & CONTEXT_CLICKABLE) == CONTEXT_CLICKABLE;

        if ((viewFlags & ENABLED_MASK) == DISABLED) {
            if (action == MotionEvent.ACTION_UP && (mPrivateFlags & PFLAG_PRESSED) != 0) {
                setPressed(false);
            }
            mPrivateFlags3 &= ~PFLAG3_FINGER_DOWN;
            // A disabled view that is clickable still consumes the touch
            // events, it just doesn't respond to them.
            return clickable;
        }
        if (mTouchDelegate != null) {
            if (mTouchDelegate.onTouchEvent(event)) {
                return true;
            }
        }

        //Tooltip 这是一个解释工具，长按会出现提示文字
        if (clickable || (viewFlags & TOOLTIP) == TOOLTIP) {
            switch (action) {
                case MotionEvent.ACTION_UP: //触发点击，以及预点击下的按下抬起，以及各种擦屁股（清除 tooltip 和长按回调等）
                    mPrivateFlags3 &= ~PFLAG3_FINGER_DOWN;
                    //这里是跟 tooltip 有关，松手之后隐藏 Tooltip，默认是松开 1.5s 之后消失
                    if ((viewFlags & TOOLTIP) == TOOLTIP) {
                        handleTooltipUp();
                    }
                    //如果不可点击，即只支持 Tooltip 
                    if (!clickable) {
                        removeTapCallback();
                        removeLongPressCallback();
                        mInContextButtonPress = false;
                        mHasPerformedLongPress = false;
                        mIgnoreNextUpEvent = false;
                        break;
                    }
                    boolean prepressed = (mPrivateFlags & PFLAG_PREPRESSED) != 0;
                    //如果是按下或者预按下状态
                    if ((mPrivateFlags & PFLAG_PRESSED) != 0 || prepressed) {
                        // take focus if we don't have it already and we should in
                        // touch mode.
                        boolean focusTaken = false;
                        //如果可以获取焦点，并且在触摸模式下也可以获取焦点（例如 EditText），且当前是没有焦点的。
                        if (isFocusable() && isFocusableInTouchMode() && !isFocused()) {
                            focusTaken = requestFocus();
                        }

                        //如果是预按下状态
                        if (prepressed) {
                            //将 view 设置为按下状态，也就是在手指抬起之后才开始出发点击事件，指定时间之后会将状态设置为抬起
                            setPressed(true, x, y);
                        }

                        //如果不是预按下，也不是长按，就会触发点击事件
                        if (!mHasPerformedLongPress && !mIgnoreNextUpEvent) {
                            removeLongPressCallback();

                            if (!focusTaken) {
                                if (mPerformClick == null) {
                                    mPerformClick = new PerformClick();
                                }
                                if (!post(mPerformClick)) {
                                    //触发点击
                                    performClickInternal();
                                }
                            }
                        }

                        if (mUnsetPressedState == null) {
                            mUnsetPressedState = new UnsetPressedState();
                        }

                        //预按下状态之下的点击事件是在手指抬起才将状态设置为按下的，并且需要马上将其设置为抬起，下面的代码就是处理按下之后的延迟指定时间后抬起，这个延迟时间为 ViewConfiguration.getPressedStateDuration()，为 64 毫秒
                        if (prepressed) {
                            postDelayed(mUnsetPressedState,
                                    ViewConfiguration.getPressedStateDuration());
                        } else if (!post(mUnsetPressedState)) {
                            // If the post failed, unpress right now
                            mUnsetPressedState.run();
                        }

                        removeTapCallback();
                    }
                    mIgnoreNextUpEvent = false;
                    break;

                case MotionEvent.ACTION_DOWN://这里核心做两件事，记录按下状态，设置长按等待器
                    //是否触摸到屏幕，这是为了在手指触摸屏幕的时候，一些提示文字离触摸点产生一些距离，使文字不被手指挡住，这是为 Tooltip 设计的
                    if (event.getSource() == InputDevice.SOURCE_TOUCHSCREEN) {
                        mPrivateFlags3 |= PFLAG3_FINGER_DOWN;
                    }
                    mHasPerformedLongPress = false;

                    //这个判断也是为了 Tooltip 设计的，如果不可点击，就只需检查有没有 Tooltip 就行了
                    if (!clickable) {
                        checkForLongClick(
                                ViewConfiguration.getLongPressTimeout(),
                                x,
                                y,
                                TOUCH_GESTURE_CLASSIFIED__CLASSIFICATION__LONG_PRESS);
                        break;
                    }

                    //判断是否是鼠标右键点击，如果是，则显示上下文菜单
                    if (performButtonActionOnTouchDown(event)) {
                        break;
                    }

                    //判断是否是在一个滑动控件中，递归的调用父 View 的 shouldDelayChildPressedState() 方法，如果该方法返回 true，则表示是处于滑动控件中的
                    //一般重写 ViewGroup 的时候，如果不是打算写成滑动控件，那么是需要重写 shouldDelayChildPressedState 方法，并返回 false，否则在 ViewGroup 按下默认是会有一个延迟的。
                    boolean isInScrollingContainer = isInScrollingContainer();

                    //如果在一个滑动控件中，在滑动控件中，一开始系统是不知道你是想要滑动还是想要按下，或者是要操作子 View 还是操作父 View
                    if (isInScrollingContainer) {
                        //设置一个预按下的标志，也就是将要按下但是还没按下的状态。
                        mPrivateFlags |= PFLAG_PREPRESSED;
                        if (mPendingCheckForTap == null) {
                            //按下等待器，这里面会清空预按下的标志位，然后设置为按下状态，并设置一个长按监听器，这里面等待长按的时间实际是默认的时间减去预按下的等待时间，即为 400
                            mPendingCheckForTap = new CheckForTap();
                        }
                        mPendingCheckForTap.x = event.getX();
                        mPendingCheckForTap.y = event.getY();
                        //预按下的等待时间是 ViewConfiguration.getTapTimeout()，100ms
                        postDelayed(mPendingCheckForTap, ViewConfiguration.getTapTimeout());
                    } else {//如果不在滑动控件中，而且处于按下状态
                        //设置状态为按下
                        setPressed(true, x, y);
                        //检查是否长按，设置一个长按等待器，如果指定时长过后，还是处于按下状态，就触发长按，等待时间为 ViewConfiguration.getLongPressTimeout()，500ms
                        checkForLongClick(
                                ViewConfiguration.getLongPressTimeout(),
                                x,
                                y,
                                TOUCH_GESTURE_CLASSIFIED__CLASSIFICATION__LONG_PRESS);
                    }
                    break;

                case MotionEvent.ACTION_CANCEL://纯擦屁股
                    if (clickable) {
                        setPressed(false);
                    }
                    removeTapCallback();
                    removeLongPressCallback();
                    mInContextButtonPress = false;
                    mHasPerformedLongPress = false;
                    mIgnoreNextUpEvent = false;
                    mPrivateFlags3 &= ~PFLAG3_FINGER_DOWN;
                    break;

                case MotionEvent.ACTION_MOVE:
                    if (clickable) {
                        //这是触摸显示的热点。Android 5.0 以后按键按下时的波纹效果就是通过这个实现的。
                        drawableHotspotChanged(x, y);
                    }

                    //------------- 这一段时 Android 10.0 的东西 长按时间加倍
                    final int motionClassification = event.getClassification();
                    //如果系统判断出来当前的是什么类型的事件
                    final boolean ambiguousGesture =
                            motionClassification == MotionEvent.CLASSIFICATION_AMBIGUOUS_GESTURE;
                    int touchSlop = mTouchSlop;
                    if (ambiguousGesture && hasPendingLongPressCallback()) {
                        //如果手指出界了
                        if (!pointInView(x, y, touchSlop)) {
                            // The default action here is to cancel long press. But instead, we
                            // just extend the timeout here, in case the classification
                            // stays ambiguous.
                            //删除原来的的长按等待器
                            removeLongPressCallback();
                            long delay = (long) (ViewConfiguration.getLongPressTimeout()
                                    * mAmbiguousGestureMultiplier);
                            // Subtract the time already spent
                            delay -= event.getEventTime() - event.getDownTime();
                            //设置一个新的长按等待器，等待时间加倍
                            checkForLongClick(
                                    delay,
                                    x,
                                    y,
                                    TOUCH_GESTURE_CLASSIFIED__CLASSIFICATION__LONG_PRESS);
                        }
                        touchSlop *= mAmbiguousGestureMultiplier;
                    }
                    //-------------------------------------------------------

                    // 如果手指出界了。touchSlop 是触摸边界的之外的边界，触摸边界主要是防止手指触摸边缘的时候有些越界导致按下事件失效。
                    if (!pointInView(x, y, touchSlop)) {
                        //删除等待器
                        removeTapCallback();
                        removeLongPressCallback();
                        //设置按下状态为false
                        if ((mPrivateFlags & PFLAG_PRESSED) != 0) {
                            setPressed(false);
                        }
                        mPrivateFlags3 &= ~PFLAG3_FINGER_DOWN;
                    }

                    //------------------------ Android 10.0
                    //如果是用力按下，直接触发长按
                    final boolean deepPress =
                            motionClassification == MotionEvent.CLASSIFICATION_DEEP_PRESS;
                    if (deepPress && hasPendingLongPressCallback()) {
                        // process the long click action immediately
                        removeLongPressCallback();
                        checkForLongClick(
                                0 /* send immediately */,
                                x,
                                y,
                                TOUCH_GESTURE_CLASSIFIED__CLASSIFICATION__DEEP_PRESS);
                    }
                    //-------------------------------------------

                    break;
            }

            return true;
        }

        return false;
}


public boolean dispatchTouchEvent(MotionEvent event) {
    // If the event should be handled by accessibility focus first.
    if (event.isTargetAccessibilityFocus()) {
        // We don't have focus or no virtual descendant has it, do not handle the event.
        if (!isAccessibilityFocusedViewOrHost()) {
            return false;
        }
        // We have focus and got the event, then use normal event dispatch.
        event.setTargetAccessibilityFocus(false);
    }
    boolean result = false;

    if (mInputEventConsistencyVerifier != null) {
        mInputEventConsistencyVerifier.onTouchEvent(event, 0);
    }

    final int actionMasked = event.getActionMasked();
    if (actionMasked == MotionEvent.ACTION_DOWN) {
        // Defensive cleanup for new gesture
        stopNestedScroll();
    }

    if (onFilterTouchEventForSecurity(event)) {
        if ((mViewFlags & ENABLED_MASK) == ENABLED && handleScrollBarDragging(event)) {
            result = true;
        }
        //
        ListenerInfo li = mListenerInfo;
        if (li != null && li.mOnTouchListener != null
                && (mViewFlags & ENABLED_MASK) == ENABLED
                && li.mOnTouchListener.onTouch(this, event)) {
            result = true;
        }

        //核心代码
        if (!result && onTouchEvent(event)) {
            result = true;
        }
    }

    if (!result && mInputEventConsistencyVerifier != null) {
        mInputEventConsistencyVerifier.onUnhandledEvent(event, 0);
    }

    // Clean up after nested scrolls if this is the end of a gesture;
    // also cancel it if we tried an ACTION_DOWN but we didn't want the rest
    // of the gesture.
    if (actionMasked == MotionEvent.ACTION_UP ||
            actionMasked == MotionEvent.ACTION_CANCEL ||
            (actionMasked == MotionEvent.ACTION_DOWN && !result)) {
        stopNestedScroll();
    }

    return result;
}


//ViewGroup.java
@Override
public boolean dispatchTouchEvent(MotionEvent ev) {
    if (mInputEventConsistencyVerifier != null) {
        mInputEventConsistencyVerifier.onTouchEvent(ev, 1);
    }

    if (ev.isTargetAccessibilityFocus() && isAccessibilityFocusedViewOrHost()) {
        ev.setTargetAccessibilityFocus(false);
    }

    boolean handled = false;
    if (onFilterTouchEventForSecurity(ev)) {
        final int action = ev.getAction();
        final int actionMasked = action & MotionEvent.ACTION_MASK;

        // 清空状态，这里会清空两个状态。，
        if (actionMasked == MotionEvent.ACTION_DOWN) {
            //第一个是 TouchTarget（清理之前已经接收过 down 事件的子 View）
            cancelAndClearTouchTargets(ev);
            //重置子view之前通过 requestDisallowInterceptTouchEvent 申请父 View 不要拦截的状态。重置之后，父 View 才可以正常拦截下一次事件序列。
            resetTouchState();
        }

        //拦截处理。mFirstTouchTarget，TouchTarget 就是只被触摸到的子 View
        final boolean intercepted;
        //如果有子 View 被按下了。只有在出现 down 事件，或者当不是 down 事件，但是当前有子 View 接受了最开始的 down，事件才会继续往下排法
        if (actionMasked == MotionEvent.ACTION_DOWN
                || mFirstTouchTarget != null) {
            final boolean disallowIntercept = (mGroupFlags & FLAG_DISALLOW_INTERCEPT) != 0;
            //如果子 View 通知父 View 不要拦截当前事件序列，intercepted 直接为 false ，即不拦截。否则会调用 onInterceptTouchEvent 方法确认是否拦截
            if (!disallowIntercept) {
                intercepted = onInterceptTouchEvent(ev);
                ev.setAction(action); // restore action in case it was changed
            } else {
                intercepted = false;
            }
        } else {
            // There are no touch targets and this action is not an initial down
            // so this view group continues to intercept touches.
            intercepted = true;
        }

        // If intercepted, start normal event dispatch. Also if there is already
        // a view that is handling the gesture, do normal event dispatch.
        if (intercepted || mFirstTouchTarget != null) {
            ev.setTargetAccessibilityFocus(false);
        }

        // Check for cancelation.
        final boolean canceled = resetCancelNextUpFlag(this)
                || actionMasked == MotionEvent.ACTION_CANCEL;

        // Update list of touch targets for pointer down, if needed.
        final boolean isMouseEvent = ev.getSource() == InputDevice.SOURCE_MOUSE;
        final boolean split = (mGroupFlags & FLAG_SPLIT_MOTION_EVENTS) != 0
                && !isMouseEvent;
        TouchTarget newTouchTarget = null;
        boolean alreadyDispatchedToNewTouchTarget = false;
        //事件没有被取消，而且当前不拦截，将事件向子 View 派发
        if (!canceled && !intercepted) {
            View childWithAccessibilityFocus = ev.isTargetAccessibilityFocus()
                    ? findChildWithAccessibilityFocus() : null;

            //
            if (actionMasked == MotionEvent.ACTION_DOWN
                    || (split && actionMasked == MotionEvent.ACTION_POINTER_DOWN)//多点触控
                    || actionMasked == MotionEvent.ACTION_HOVER_MOVE) {
                final int actionIndex = ev.getActionIndex(); // always 0 for down
                final int idBitsToAssign = split ? 1 << ev.getPointerId(actionIndex)
                        : TouchTarget.ALL_POINTER_IDS;

                // Clean up earlier touch targets for this pointer id in case they
                // have become out of sync.
                removePointersFromTouchTargets(idBitsToAssign);

                final int childrenCount = mChildrenCount;
                if (newTouchTarget == null && childrenCount != 0) {
                    final float x =
                            isMouseEvent ? ev.getXCursorPosition() : ev.getX(actionIndex);
                    final float y =
                            isMouseEvent ? ev.getYCursorPosition() : ev.getY(actionIndex);
                    // Find a child that can receive the event.
                    // Scan children from front to back.
                    final ArrayList<View> preorderedList = buildTouchDispatchChildList();
                    final boolean customOrder = preorderedList == null
                            && isChildrenDrawingOrderEnabled();
                    final View[] children = mChildren;
                    for (int i = childrenCount - 1; i >= 0; i--) {
                        final int childIndex = getAndVerifyPreorderedIndex(
                                childrenCount, i, customOrder);
                        final View child = getAndVerifyPreorderedView(
                                preorderedList, children, childIndex);
                        if (!child.canReceivePointerEvents()
                                || !isTransformedTouchPointInView(x, y, child, null)) {
                            continue;
                        }
                        //判断当前有没有子 View 接收 down 事件
                        newTouchTarget = getTouchTarget(child);
                        if (newTouchTarget != null) {
                            //并入触摸点，用于多点触控的。一个父 View ，它可以同时有多个子 View 被触摸，一个子 View 可以同时被多个手指头触摸，
                            newTouchTarget.pointerIdBits |= idBitsToAssign;
                            break;
                        }

                        resetCancelNextUpFlag(child);
                        //寻找有没有新的子 View 接收这个事件，转换事件类型以及触摸点的位置信息。
                        if (dispatchTransformedTouchEvent(ev, false, child, idBitsToAssign)) {
                            mLastTouchDownTime = ev.getDownTime();
                            if (preorderedList != null) {
                                // childIndex points into presorted list, find original index
                                for (int j = 0; j < childrenCount; j++) {
                                    if (children[childIndex] == mChildren[j]) {
                                        mLastTouchDownIndex = j;
                                        break;
                                    }
                                }
                            } else {
                                mLastTouchDownIndex = childIndex;
                            }
                            mLastTouchDownX = ev.getX();
                            mLastTouchDownY = ev.getY();
                            //将这个子 View 添加到 TouchTarget 中
                            newTouchTarget = addTouchTarget(child, idBitsToAssign);
                            alreadyDispatchedToNewTouchTarget = true;
                            break;
                        }

                        // The accessibility focus didn't handle the event, so clear
                        // the flag and do a normal dispatch to all children.
                        ev.setTargetAccessibilityFocus(false);
                    }
                    if (preorderedList != null) preorderedList.clear();
                }

                if (newTouchTarget == null && mFirstTouchTarget != null) {
                    // Did not find a child to receive the event.
                    // Assign the pointer to the least recently added target.
                    newTouchTarget = mFirstTouchTarget;
                    while (newTouchTarget.next != null) {
                        newTouchTarget = newTouchTarget.next;
                    }
                    newTouchTarget.pointerIdBits |= idBitsToAssign;
                }
            }
        }

        //没有子View接收事件
        if (mFirstTouchTarget == null) {
            //相当于调用 onTouchEvent
            handled = dispatchTransformedTouchEvent(ev, canceled, null,
                    TouchTarget.ALL_POINTER_IDS);
        } else {
            // Dispatch to touch targets, excluding the new touch target if we already
            // dispatched to it.  Cancel touch targets if necessary.
            TouchTarget predecessor = null;
            TouchTarget target = mFirstTouchTarget;
            //遍历子 View
            while (target != null) {
                final TouchTarget next = target.next;
                if (alreadyDispatchedToNewTouchTarget && target == newTouchTarget) {
                    handled = true;
                } else {
                    final boolean cancelChild = resetCancelNextUpFlag(target.child)
                            || intercepted;
                    //转换位置和事件类型
                    if (dispatchTransformedTouchEvent(ev, cancelChild,
                            target.child, target.pointerIdBits)) {
                        handled = true;
                    }
                    if (cancelChild) {
                        if (predecessor == null) {
                            mFirstTouchTarget = next;
                        } else {
                            predecessor.next = next;
                        }
                        target.recycle();
                        target = next;
                        continue;
                    }
                }
                predecessor = target;
                target = next;
            }
        }

        // 擦屁股
        if (canceled
                || actionMasked == MotionEvent.ACTION_UP
                || actionMasked == MotionEvent.ACTION_HOVER_MOVE) {
            resetTouchState();
        } else if (split && actionMasked == MotionEvent.ACTION_POINTER_UP) {
            final int actionIndex = ev.getActionIndex();
            final int idBitsToRemove = 1 << ev.getPointerId(actionIndex);
            removePointersFromTouchTargets(idBitsToRemove);
        }
    }

    if (!handled && mInputEventConsistencyVerifier != null) {
        mInputEventConsistencyVerifier.onUnhandledEvent(ev, 1);
    }
    return handled;
}
