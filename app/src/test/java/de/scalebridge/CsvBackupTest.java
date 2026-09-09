package de.scalebridge;
import android.content.Context;
import java.io.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=35)
public class CsvBackupTest {
 private Context context(){return RuntimeEnvironment.getApplication();}
 @Before public void clean(){context().deleteDatabase("measurements.db");}
 @Test public void scale2GarminBackupCanBeImportedAndPreservesStateAndQuotedNotes() throws Exception {
  Measurement m=new Measurement("AA;BB",1700000000L-Bs444Protocol.EPOCH,2,82.4,23.1,51.2,38.4,3.2,1750);
  String csv;try(Store db=new Store(context())){assertTrue(db.insert(m));db.state(m.id(),"sent","Zeile; mit \"Zitat\"\nund Umbruch");StringWriter out=new StringWriter();assertEquals(1,CsvBackup.write(db,out));csv=out.toString();}
  assertTrue(csv.startsWith("\ufefftimestamp;user;weight_kg;body_fat_percent"));
  context().deleteDatabase("measurements.db");try(Store db=new Store(context())){var first=CsvBackup.read(db,new StringReader(csv),1);assertEquals(1,first.imported());assertEquals(0,first.duplicates());var row=db.allMeasurements().get(0);assertEquals(m.id(),row.measurement().id());assertEquals("sent",row.state());assertEquals("Zeile; mit \"Zitat\"\nund Umbruch",row.note());var again=CsvBackup.read(db,new StringReader(csv),1);assertEquals(0,again.imported());assertEquals(1,again.duplicates());}
 }
 @Test public void invalidRowRollsBackWholeImport() throws Exception {
  String csv="timestamp;user;weight_kg\n2026-01-01T12:00:00Z;1;82.0\nkaputt;1;83.0\n";
  try(Store db=new Store(context())){try{CsvBackup.read(db,new StringReader(csv),1);fail();}catch(IOException expected){assertTrue(expected.getMessage().contains("Zeile 3"));}assertTrue(db.allMeasurements().isEmpty());}
 }
 @Test public void unknownVendorFormatExplainsRequiredHeaders() throws Exception {try(Store db=new Store(context())){try{CsvBackup.read(db,new StringReader("Datum;Gewicht\n1.1.2026;80,2"),1);fail();}catch(IOException expected){assertTrue(expected.getMessage().contains("timestamp"));}}}
 @Test public void vitaDockReportPreambleDecimalCommaAndPercentBoneAreImported() throws Exception {
  String csv="Ihre Körpergewicht-Messdaten vom 01.01.2010 bis 01.09.2026\n;Maximalwert;Minimalwert;Durchschnitt;Gesamtzahl;\nKörpergewicht (kg):;96,6;84,1;90,64;2\n\nDatum - Uhrzeit;Körpergewicht (kg);Knochenmasse (%);Körperfett (%);Körperwasser (%);Muskelmasse (%);BMI;Aktivität;Stimmung;Mahlzeit;Anmerkungen\n22.01.2020 - 22:34;86,7;3,9;19,5;59,6;43;25,6;2;0;0;Abends\n05.02.2020 - 21:59;88,6;3,8;20,3;58,9;42,5;26,2;3;0;1;\n";
  java.util.TimeZone old=java.util.TimeZone.getDefault();java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Europe/Berlin"));try(Store db=new Store(context())){var result=CsvBackup.read(db,new StringReader(csv),4);assertEquals(2,result.imported());var rows=db.allMeasurements();Measurement first=rows.get(0).measurement();assertEquals(4,first.user);assertEquals("vitadock-import",first.address);assertEquals(86.7,first.weight,0);assertEquals(19.5,first.fat,0);assertEquals(59.6,first.water,0);assertEquals(43,first.musclePercent,0);assertEquals(86.7*.039,first.bone,0.00001);assertTrue(Double.isNaN(first.basal));assertEquals("Abends",rows.get(0).note());assertEquals(java.time.ZonedDateTime.of(2020,1,22,22,34,0,0,java.time.ZoneId.of("Europe/Berlin")).toEpochSecond(),first.unixSeconds);}finally{java.util.TimeZone.setDefault(old);}
 }
 @Test public void malformedVitaDockRowRollsBack() throws Exception {
  String csv="Datum - Uhrzeit;Körpergewicht (kg);Knochenmasse (%)\n22.01.2020 - 22:34;86,7;3,9\nkein Datum;88;3,8\n";try(Store db=new Store(context())){try{CsvBackup.read(db,new StringReader(csv),1);fail();}catch(IOException expected){assertTrue(expected.getMessage().contains("VitaDock+-Zeile 3"));}assertTrue(db.allMeasurements().isEmpty());}
 }
}
