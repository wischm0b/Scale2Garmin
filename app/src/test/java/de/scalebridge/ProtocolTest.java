package de.scalebridge;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;

public class ProtocolTest {
    static byte[] hex(String text){String[] parts=text.split(":");byte[] b=new byte[parts.length];for(int i=0;i<b.length;i++)b[i]=(byte)Integer.parseInt(parts[i],16);return b;}
    static byte[] weight(){return hex("1d:80:25:00:fe:80:0f:f4:0d:6a:17:00:ff:01:09:00:00:00:00");}
    static byte[] body(){return hex("6f:80:0f:f4:0d:01:21:0c:57:f1:e3:f1:5b:f1:23:f0:00:00:00");}
    static Measurement sample(){Bs444Protocol p=new Bs444Protocol();p.accept("AA:BB:CC:DD:EE:FF",Bs444Protocol.WEIGHT,weight());return p.accept("AA:BB:CC:DD:EE:FF",Bs444Protocol.BODY,body());}
    @Test public void documentedCaptureDecodes(){Measurement m=sample();assertEquals(96,m.weight,0);assertEquals(34.3,m.fat,0.0001);assertEquals(48.3,m.water,0.0001);assertEquals(34.7,m.musclePercent,0.0001);assertEquals(3.5,m.bone,0.0001);assertEquals(1,m.user);assertEquals(33.312,m.muscleKg(),0.00001);assertEquals(0x0df40f80L+Bs444Protocol.EPOCH,m.unixSeconds);}
    @Test public void outOfOrderFramesPair(){Bs444Protocol p=new Bs444Protocol();assertNull(p.accept("A",Bs444Protocol.BODY,body()));assertNotNull(p.accept("A",Bs444Protocol.WEIGHT,weight()));}
    @Test public void differentUsersNeverPair(){Bs444Protocol p=new Bs444Protocol();p.accept("A",Bs444Protocol.WEIGHT,weight());byte[] other=body();other[5]=2;assertNull(p.accept("A",Bs444Protocol.BODY,other));assertNotNull(p.accept("A",Bs444Protocol.BODY,body()));}
    @Test public void differentTimestampsNeverPair(){Bs444Protocol p=new Bs444Protocol();p.accept("A",Bs444Protocol.WEIGHT,weight());byte[] other=body();other[1]++;assertNull(p.accept("A",Bs444Protocol.BODY,other));assertNotNull(p.accept("A",Bs444Protocol.BODY,body()));}
    @Test public void missingTimestampCannotShiftBodyColumnsIntoAnotherMeasurement(){Bs444Protocol p=new Bs444Protocol();byte[] w=weight(),b=body();Arrays.fill(w,5,9,(byte)0);assertNull(p.accept("A",Bs444Protocol.WEIGHT,w));assertNull(p.accept("A",Bs444Protocol.BODY,b));}
    @Test(expected=IllegalArgumentException.class) public void truncatedPacketsRejected(){new Bs444Protocol().accept("A",Bs444Protocol.WEIGHT,new byte[8]);}
    @Test(expected=IllegalArgumentException.class) public void poundsNotMistakenForKilograms(){byte[] w=weight();w[0]=0x3d;new Bs444Protocol().accept("A",Bs444Protocol.WEIGHT,w);}
    @Test public void unknownCompositionIsNotInvented(){Bs444Protocol p=new Bs444Protocol();p.accept("A",Bs444Protocol.WEIGHT,weight());byte[] b=body();b[8]=(byte)255;b[9]=(byte)255;assertTrue(Double.isNaN(p.accept("A",Bs444Protocol.BODY,b).fat));}
    @Test public void timeCommandUses2010Epoch(){assertArrayEquals(hex("02:80:0f:f4:0d"),Bs444Protocol.timeCommand(0x0df40f80L+Bs444Protocol.EPOCH));}
}
