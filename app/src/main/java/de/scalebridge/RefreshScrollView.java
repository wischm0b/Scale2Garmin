package de.scalebridge;

import android.content.Context;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.ScrollView;

/** Pull down from the top to start a new, bounded reception attempt. */
public final class RefreshScrollView extends ScrollView {
    private final Runnable refresh;
    private float startX,startY,distance;
    private boolean eligible,pulling;
    private boolean multiTouch;
    public RefreshScrollView(Context context,Runnable refresh){super(context);this.refresh=refresh;setOverScrollMode(OVER_SCROLL_NEVER);}
    private float threshold(){return 64*getResources().getDisplayMetrics().density;}
    private void track(MotionEvent event){
        if(event.getActionMasked()==MotionEvent.ACTION_DOWN){startX=event.getX();startY=event.getY();eligible=!canScrollVertically(-1);pulling=false;distance=0;multiTouch=false;}
        if(event.getPointerCount()>1){multiTouch=true;eligible=false;pulling=false;distance=0;if(getChildCount()>0)getChildAt(0).setTranslationY(0);}
        if(multiTouch||!eligible||event.getActionMasked()!=MotionEvent.ACTION_MOVE)return;
        float dy=event.getY()-startY,dx=Math.abs(event.getX()-startX);
        int slop=ViewConfiguration.get(getContext()).getScaledTouchSlop();
        // Tiny sideways/upward jitter must not reject an otherwise downward swipe.
        if(!pulling&&Math.max(Math.abs(dy),dx)>slop){
            if(dy>dx){pulling=true;}else eligible=false;
        }
        if(pulling)distance=Math.max(0,dy);
    }
    @Override public boolean onInterceptTouchEvent(MotionEvent event){
        track(event);
        if(multiTouch)return false;
        if(pulling)return true;
        if(eligible&&event.getActionMasked()==MotionEvent.ACTION_MOVE)return false;
        return super.onInterceptTouchEvent(event);
    }
    @Override public boolean onTouchEvent(MotionEvent event){
        // Non-clickable labels hand DOWN directly to ScrollView. Subsequent events
        // bypass interception, so recognition must also run on this delivery path.
        boolean wasPulling=pulling;track(event);
        if(pulling&&!wasPulling){MotionEvent cancel=MotionEvent.obtain(event);cancel.setAction(MotionEvent.ACTION_CANCEL);super.onTouchEvent(cancel);cancel.recycle();}
        if(event.getPointerCount()>1||multiTouch){multiTouch=true;eligible=false;pulling=false;distance=0;if(getChildCount()>0)getChildAt(0).setTranslationY(0);return true;}
        if(!pulling)return super.onTouchEvent(event);
        if(event.getActionMasked()==MotionEvent.ACTION_MOVE){distance=Math.max(0,event.getY()-startY);if(getChildCount()>0)getChildAt(0).setTranslationY(Math.min(threshold(),distance*.4f));return true;}
        if(event.getActionMasked()==MotionEvent.ACTION_UP||event.getActionMasked()==MotionEvent.ACTION_CANCEL){
            boolean trigger=event.getActionMasked()==MotionEvent.ACTION_UP&&distance>=threshold();pulling=false;eligible=false;
            if(getChildCount()>0)getChildAt(0).setTranslationY(0);if(trigger)refresh.run();return true;
        }
        return true;
    }
}
