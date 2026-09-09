package de.scalebridge;

import org.junit.Test;
import static org.junit.Assert.*;
import java.io.ByteArrayInputStream;
import java.util.concurrent.atomic.AtomicReference;
import com.garmin.fit.*;

public class FitWriterTest {
    private WeightScaleMesg decode(byte[] bytes){
        Decode decoder=new Decode();assertTrue("Official FIT SDK integrity check",decoder.checkFileIntegrity(new ByteArrayInputStream(bytes)));
        AtomicReference<WeightScaleMesg> result=new AtomicReference<>();MesgBroadcaster broadcaster=new MesgBroadcaster(decoder);
        broadcaster.addListener((WeightScaleMesgListener)result::set);assertTrue(decoder.read(new ByteArrayInputStream(bytes),broadcaster,broadcaster));assertNotNull(result.get());return result.get();
    }
    @Test public void officialSdkReadsWeightAndComposition(){Measurement source=ProtocolTest.sample();WeightScaleMesg m=decode(FitWriter.encode(source,true));assertEquals(96,m.getWeight(),0.001);assertEquals(34.3,m.getPercentFat(),0.001);assertEquals(48.3,m.getPercentHydration(),0.001);assertEquals(3.5,m.getBoneMass(),0.001);assertEquals(33.31,m.getMuscleMass(),0.001);assertEquals(source.unixSeconds*1000,m.getTimestamp().getDate().getTime());}
    @Test public void muscleOptOutIsInvalidFieldNotZero(){assertNull(decode(FitWriter.encode(ProtocolTest.sample(),false)).getMuscleMass());}
    @Test public void missingFatIsNotZero(){Measurement s=ProtocolTest.sample();Measurement missing=new Measurement(s.address,s.scaleSeconds,s.user,s.weight,Double.NaN,s.water,s.musclePercent,s.bone,s.basal);assertNull(decode(FitWriter.encode(missing,false)).getPercentFat());}
    @Test public void corruptionDetected(){byte[] bytes=FitWriter.encode(ProtocolTest.sample(),true);bytes[bytes.length-5]^=1;assertFalse(new Decode().checkFileIntegrity(new ByteArrayInputStream(bytes)));}
}
