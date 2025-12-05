package net.ivoa.registry.harvest;

import java.io.Reader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.File;
import java.io.FileWriter;
import java.io.FileReader;
import java.util.Objects;
import java.util.Properties;
import java.util.Date;
import java.text.ParseException;

import org.junit.Test;
import static org.junit.Assert.*;

import org.apache.commons.io.IOUtils;

public class HarvestedRecordTest {

    static final String TEST_RECORD = "/vores.xml";
    static final String testout = "vores-harvested.xml";
    static final String tmppath = System.getProperty("test.tmpdir", System.getProperty("java.io.tmpdir"));

    Reader openTestRec() {
        return new InputStreamReader(Objects.requireNonNull(HarvestedRecordTest.class.getResourceAsStream(TEST_RECORD)));
    }

    @Test 
    public void testMakeProps() {
        Properties p = HarvestedRecord.makeProps("ivo://goob", "2013","active");
        assertEquals("ivo://goob",
                     p.getProperty(HarvestedRecord.IDENTIFIER_PROP));
        assertEquals("2013", p.getProperty(HarvestedRecord.DATESTAMP_PROP));
        assertEquals("active", p.getProperty(HarvestedRecord.STATUS_PROP));

        p = HarvestedRecord.makeProps("ivo://goob", "2013", null);
        assertEquals("ivo://goob",
                     p.getProperty(HarvestedRecord.IDENTIFIER_PROP));
        assertEquals("2013", p.getProperty(HarvestedRecord.DATESTAMP_PROP));
        assertNull(p.getProperty(HarvestedRecord.STATUS_PROP));

        p = HarvestedRecord.makeProps("ivo://goob", null, null);
        assertEquals("ivo://goob",
                     p.getProperty(HarvestedRecord.IDENTIFIER_PROP));
        assertNull(p.getProperty(HarvestedRecord.DATESTAMP_PROP));
        assertNull(p.getProperty(HarvestedRecord.STATUS_PROP));
    }

    @Test public void testPropCtor() {
        Properties p = HarvestedRecord.makeProps("ivo://goob", "2013","active");
        HarvestedRecord rec = new HarvestedRecord(p);
        assertFalse(rec.isAvailable());
        assertFalse(rec.isDeleted());
        assertEquals("ivo://goob",rec.getID());
        assertEquals("2013", rec.getDatestamp());

        p = HarvestedRecord.makeProps("ivo://goob", "2013", null);
        rec = new HarvestedRecord(p);
        assertFalse(rec.isAvailable());
        assertTrue(rec.isDeleted());
        assertEquals("ivo://goob",rec.getID());
        assertEquals("2013", rec.getDatestamp());

        p = HarvestedRecord.makeProps("ivo://goob", null, null);
        rec = new HarvestedRecord(p);
        assertFalse(rec.isAvailable());
        assertTrue(rec.isDeleted());
        assertEquals("ivo://goob",rec.getID());
        assertNull(rec.getDatestamp());
    }

    @Test public void testPRDCtor() {
        Properties p = HarvestedRecord.makeProps("ivo://goob", "2013","active");
        Reader r = openTestRec();
        assertNotNull(r);
        HarvestedRecord rec = new HarvestedRecord(p, r, false);
        assertTrue(rec.isAvailable());
        assertFalse(rec.isDeleted());
        assertEquals("ivo://goob",rec.getID());
        assertEquals("2013", rec.getDatestamp());

        rec = new HarvestedRecord(p, r, true);
        assertTrue(rec.isAvailable());
        assertTrue(rec.isDeleted());
        assertEquals("ivo://goob",rec.getID());
        assertEquals("2013", rec.getDatestamp());

        rec = new HarvestedRecord(p, null, false);
        assertFalse(rec.isAvailable());
        assertFalse(rec.isDeleted());
        assertEquals("ivo://goob",rec.getID());
        assertEquals("2013", rec.getDatestamp());
    }

    @Test public void testPRCtor() {
        Properties p = HarvestedRecord.makeProps("ivo://goob", "2013","active");
        Reader r = openTestRec();
        assertNotNull(r);
        HarvestedRecord rec = new HarvestedRecord(p, r);
        assertTrue(rec.isAvailable());
        assertFalse(rec.isDeleted());
        assertEquals("ivo://goob",rec.getID());
        assertEquals("2013", rec.getDatestamp());

        p = HarvestedRecord.makeProps("ivo://goob", "2013", "deleted");
        rec = new HarvestedRecord(p, r);
        assertTrue(rec.isAvailable());
        assertTrue(rec.isDeleted());
        assertEquals("ivo://goob",rec.getID());
        assertEquals("2013", rec.getDatestamp());

        rec = new HarvestedRecord(p, null);
        assertFalse(rec.isAvailable());
        assertTrue(rec.isDeleted());
        assertEquals("ivo://goob",rec.getID());
        assertEquals("2013", rec.getDatestamp());

        p = HarvestedRecord.makeProps("ivo://goob", "2013", null);
        rec = new HarvestedRecord(p, r);
        assertTrue(rec.isAvailable());
        assertFalse(rec.isDeleted());
        assertEquals("ivo://goob",rec.getID());
        assertEquals("2013", rec.getDatestamp());

        rec = new HarvestedRecord(p, null);
        assertFalse(rec.isAvailable());
        assertTrue(rec.isDeleted());
        assertEquals("ivo://goob",rec.getID());
        assertEquals("2013", rec.getDatestamp());
    }

    @Test public void testSSRSCtor() {
        Reader r = openTestRec();
        assertNotNull(r);
        HarvestedRecord rec =new HarvestedRecord("ivo://goob", "2013", r, null);
        assertTrue(rec.isAvailable());
        assertFalse(rec.isDeleted());
        assertEquals("ivo://goob",rec.getID());
        assertEquals("2013", rec.getDatestamp());

        rec = new HarvestedRecord("ivo://goob", "2013", r, "deleted");
        assertTrue(rec.isAvailable());
        assertTrue(rec.isDeleted());
        assertEquals("ivo://goob",rec.getID());
        assertEquals("2013", rec.getDatestamp());

        rec = new HarvestedRecord("ivo://goob", "2013", null, "deleted");
        assertFalse(rec.isAvailable());
        assertTrue(rec.isDeleted());
        assertEquals("ivo://goob",rec.getID());
        assertEquals("2013", rec.getDatestamp());

        rec = new HarvestedRecord("ivo://goob", "2013", null, null);
        assertFalse(rec.isAvailable());
        assertTrue(rec.isDeleted());
        assertEquals("ivo://goob",rec.getID());
        assertEquals("2013", rec.getDatestamp());

        rec = new HarvestedRecord("ivo://goob", "2013", null, "active");
        assertFalse(rec.isAvailable());
        assertFalse(rec.isDeleted());
        assertEquals("ivo://goob",rec.getID());
        assertEquals("2013", rec.getDatestamp());
    }

    @Test public void testSSRCtor() {
        Reader r = openTestRec();
        assertNotNull(r);
        HarvestedRecord rec = new HarvestedRecord("ivo://goob", "2013", r);
        assertTrue(rec.isAvailable());
        assertFalse(rec.isDeleted());
        assertEquals("ivo://goob",rec.getID());
        assertEquals("2013", rec.getDatestamp());

        rec = new HarvestedRecord("ivo://goob", "2013", null);
        assertFalse(rec.isAvailable());
        assertTrue(rec.isDeleted());
        assertEquals("ivo://goob",rec.getID());
        assertEquals("2013", rec.getDatestamp());
    }

    @Test public void testSSRbCtor() {
        Reader r = openTestRec();
        assertNotNull(r);
        HarvestedRecord rec=new HarvestedRecord("ivo://goob", "2013", r, false);
        assertTrue(rec.isAvailable());
        assertFalse(rec.isDeleted());
        assertEquals("ivo://goob",rec.getID());
        assertEquals("2013", rec.getDatestamp());

        rec = new HarvestedRecord("ivo://goob", "2013", r, true);
        assertTrue(rec.isAvailable());
        assertTrue(rec.isDeleted());
        assertEquals("ivo://goob",rec.getID());
        assertEquals("2013", rec.getDatestamp());

        rec = new HarvestedRecord("ivo://goob", "2013", null, true);
        assertFalse(rec.isAvailable());
        assertTrue(rec.isDeleted());
        assertEquals("ivo://goob",rec.getID());
        assertEquals("2013", rec.getDatestamp());

        rec = new HarvestedRecord("ivo://goob", "2013", null, null);
        assertFalse(rec.isAvailable());
        assertTrue(rec.isDeleted());
        assertEquals("ivo://goob",rec.getID());
        assertEquals("2013", rec.getDatestamp());

        rec = new HarvestedRecord("ivo://goob", "2013", null, false);
        assertFalse(rec.isAvailable());
        assertFalse(rec.isDeleted());
        assertEquals("ivo://goob",rec.getID());
        assertEquals("2013", rec.getDatestamp());
    }

    @Test public void testSSbCtor() {
        Reader r = openTestRec();
        assertNotNull(r);
        HarvestedRecord rec = new HarvestedRecord("ivo://goob", "2013", true);
        assertFalse(rec.isAvailable());
        assertTrue(rec.isDeleted());
        assertEquals("ivo://goob",rec.getID());
        assertEquals("2013", rec.getDatestamp());

        rec = new HarvestedRecord("ivo://goob", "2013", false);
        assertFalse(rec.isAvailable());
        assertFalse(rec.isDeleted());
        assertEquals("ivo://goob",rec.getID());
        assertEquals("2013", rec.getDatestamp());
    }

    @Test public void testSSCtor() {
        Reader r = openTestRec();
        assertNotNull(r);
        HarvestedRecord rec = new HarvestedRecord("ivo://goob", "2013");
        assertFalse(rec.isAvailable());
        assertTrue(rec.isDeleted());
        assertEquals("ivo://goob",rec.getID());
        assertEquals("2013", rec.getDatestamp());
    }

    @Test public void testGetDate() {
        // test parsing
        Date date = null;
        HarvestedRecord rec = new HarvestedRecord("ivo://goob", 
                                              "2013-01-01T03:24:27Z");
        try { 
            date = rec.getDate(); 

            rec = new HarvestedRecord("ivo://goob", "2013-01-01T03:24:27.32Z");
            date = rec.getDate();

            rec = new HarvestedRecord("ivo://goob", "2013-01-01T03:24:27.0Z");
            date = rec.getDate();
        }
        catch (ParseException ex) { 
            fail("Legit date failed to parse: " + rec.getDatestamp()); 
        }

        rec = new HarvestedRecord("ivo://goob", "2013-01-01T03:24:27");
        try {
            date = rec.getDate();
            fail("date parsing failed to catch missing Z: " + date);
        } catch (ParseException ex) { }

    }

    @Test
    public void testGetProperty() {
        Properties p = HarvestedRecord.makeProps("ivo://goob", "2013", "active");
        p.setProperty("ep", "zub");
        HarvestedRecord rec = new HarvestedRecord(p);
        assertEquals("ivo://goob", 
                     rec.getProperty(HarvestedRecord.IDENTIFIER_PROP));
        assertEquals("2013", rec.getProperty(HarvestedRecord.DATESTAMP_PROP));
        assertEquals("active", rec.getProperty(HarvestedRecord.STATUS_PROP));
        assertEquals("zub", rec.getProperty("ep"));
    }

    @Test
    public void testWriteContent() throws Exception {
        Reader r = openTestRec();
        assertNotNull(r);
        HarvestedRecord rec = new HarvestedRecord("ivo://goob", "2013", r);

        assertNotNull("Neither test.tmpdir nor java.io.tmpdir sys property is set", tmppath);
        File tmpdir = new File(tmppath);
        assertTrue("test.tmpdir: "+tmpdir+": does not exist", tmpdir.exists());
        File outfile = new File(tmpdir, testout);

        try {
            // invoke the method
            FileWriter writer = new FileWriter(outfile);
            rec.writeContent(writer);
            writer.close();

            // now check the content
            Reader from = openTestRec();
            Reader to = new FileReader(outfile);
            assertTrue(IOUtils.contentEquals(from, to));
        }
        catch (IOException ex) {
            throw new RuntimeException(ex);
        }
        finally {
            outfile.delete();
        }

    }


}
