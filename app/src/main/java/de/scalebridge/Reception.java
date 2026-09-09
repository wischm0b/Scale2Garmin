package de.scalebridge;

/** Transient progress belongs to one reception attempt, never to the stored history. */
public final class Reception {
    private static volatile String measurementId;
    public static void begin(){measurementId=null;}
    public static void received(Measurement m){measurementId=m.id();}
    public static String measurementId(){return measurementId;}
}
