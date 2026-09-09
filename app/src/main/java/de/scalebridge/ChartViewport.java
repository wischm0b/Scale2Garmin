package de.scalebridge;

/** Bounded horizontal time window. Gesture focus is preserved while zooming and panning. */
public final class ChartViewport {
    public final double lower,upper,homeLeft,homeRight;
    private double left,right;
    public ChartViewport(double lower,double upper,double homeLeft,double homeRight){
        this.lower=lower;this.upper=Math.max(lower+60,upper);
        this.homeLeft=homeLeft;this.homeRight=homeRight;set(homeLeft,homeRight);
    }
    public double left(){return left;} public double right(){return right;} public double span(){return right-left;}
    public void reset(){set(homeLeft,homeRight);}
    public void set(double a,double b){
        if(!Double.isFinite(a)||!Double.isFinite(b)||b<=a)return;
        double span=Math.max(Math.min(60,upper-lower),Math.min(upper-lower,b-a));
        left=Math.max(lower,Math.min(upper-span,a));right=left+span;
    }
    public void transform(double oldFocus,double newFocus,double zoom){
        if(!Double.isFinite(zoom)||zoom<=0)return;
        double oldSpan=span(),next=Math.max(60,Math.min(upper-lower,oldSpan/zoom));
        double a=left+oldSpan*oldFocus-next*newFocus;set(a,a+next);
    }
}
