package net.ivoa.registry.harvest;

import net.ivoa.registry.util.ResourcePeeker;
import net.ivoa.registry.util.ResourceSummary;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.File;
import java.io.InputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.HashSet;

import org.junit.Test;
import static org.junit.Assert.*;

public class RecordResponseProcessorTest {

    static final String listrec1 = "/first-listrecs.xml";
    static final String listrec2 = "/rofr-listrecs.xml";
    static final String getrec = "/rofr-getrecs.xml";
    static final String tmppath = System.getProperty("java.io.tmpdir", "/tmp");
    static final File tmpdir = new File(tmppath);

    @Test
    public void testProcess() throws HarvestingException, IOException {
        Consumer consumer = new Consumer();
        final RecordResponseProcessor p = new RecordResponseProcessor(consumer);
        final InputStream inputStream =
                Objects.requireNonNull(RecordResponseProcessorTest.class.getResourceAsStream(listrec1),
                        "Input stream for listrec1 is null");
        ResumptionToken r = p.process(inputStream, 1);
        assertEquals(2, consumer.deleted.size());
        assertEquals(2, consumer.updated.size());
        assertNotNull(r);
        assertTrue(r.moreRecords());
        assertEquals("ivo_managed!!!ivo_vor!1", r.getTokenValue());
        assertEquals("ivo_managed!!!ivo_vor!1", r.toString());
        assertEquals("2013-05-06T16:39:59.021847Z", r.getExpirationDate());
        assertEquals(881, r.getCompleteListSize());
        assertEquals(0, r.getCursor());
    }

    @Test
    public void testHarvestInfo() throws HarvestingException, IOException {
        Consumer consumer = new Consumer(true);
        Properties hinfo = new Properties();
        hinfo.setProperty("from.ep", "http://stsci/oai");
        RecordResponseProcessor p = new RecordResponseProcessor(consumer,hinfo);
        ResumptionToken r = 
            p.process(getClass().getResourceAsStream(listrec1), 1);

        assertEquals(2, consumer.deleted.size());
        assertEquals(2, consumer.updated.size());
        assertNotNull(r);
        assertTrue(r.moreRecords());
        assertEquals("ivo_managed!!!ivo_vor!1", r.getTokenValue());
        assertEquals("ivo_managed!!!ivo_vor!1", r.toString());
        assertEquals("2013-05-06T16:39:59.021847Z", r.getExpirationDate());
        assertEquals(881, r.getCompleteListSize());
        assertEquals(0, r.getCursor());
    }

    @Test
    public void testNoResume() throws HarvestingException, IOException {
        Consumer consumer = new Consumer();
        RecordResponseProcessor p = new RecordResponseProcessor(consumer);
        ResumptionToken r = 
            p.process(getClass().getResourceAsStream(listrec2), 1);
        assertEquals(0, consumer.deleted.size());
        assertTrue("ivo://ivoa.net/std/StandardsRegExt", 
                   consumer.updated.contains("ivo://ivoa.net/std/StandardsRegExt"));
        assertTrue("ivo://ivoa.net/std/RM", 
                   consumer.updated.contains("ivo://ivoa.net/std/RM"));
        assertTrue("ivo://ivoa.net/std/ConeSearch", 
                   consumer.updated.contains("ivo://ivoa.net/std/ConeSearch"));
        assertTrue("ivo://ivoa.net/IVOA", 
                   consumer.updated.contains("ivo://ivoa.net/IVOA"));
        assertTrue("ivo://ivoa.net", 
                   consumer.updated.contains("ivo://ivoa.net"));
        assertEquals(13, consumer.updated.size());
        assertNotNull(r);
        assertFalse(r.moreRecords());
        String tok = r.getTokenValue();
        assertNull("Expected null token; got: "+tok, tok);
        assertEquals("", r.toString());
        assertEquals(13, r.getCompleteListSize());
        assertEquals(0, r.getCursor());
    }

    @Test
    public void testListener() throws HarvestingException, IOException {
        Consumer consumer = new Consumer();
        RecordResponseProcessor p = new RecordResponseProcessor(consumer);
        Listener listener = new Listener();
        p.addListener(listener);
        ResumptionToken r = 
            p.process(getClass().getResourceAsStream(listrec2), 1);

        assertEquals(0, consumer.deleted.size());
        assertEquals(13, consumer.updated.size());

        assertEquals(0, listener.deleted.size());
        assertEquals(13, listener.current.size());
        assertEquals("2013-05-06T05:32:56Z", listener.respdate);
        assertNull(listener.url);
        assertNull(listener.name);
        assertNull(listener.completed);
        assertEquals(1, listener.page);
    }

    static class Listener implements HarvestListener {
        Set<String> current = new HashSet<>();
        Set<String> deleted = new HashSet<>();
        String respdate = null;
        String url = null;
        String name = null;
        int page = 0;
        String completed = null;

        Listener() { }
        public void tellWantedItems(Set<String> names) {
            names.add(RECORD_STATUS);
            names.add(HARVEST_COMPLETE);
            names.add(REGISTRY_NAME);
            names.add(REQUEST_URL);
            names.add(RESPONSE_DATE);
        }
        public void harvestInfo(int page,String id,String itemName,String value){
            if (page > this.page) this.page = page;
            switch (itemName) {
                case RESPONSE_DATE -> respdate = value;
                case REQUEST_URL -> url = value;
                case REGISTRY_NAME -> name = value;
                case HARVEST_COMPLETE -> completed = value;
                case RECORD_STATUS -> {
                    if (value.equals("deleted"))
                        deleted.add(id);
                    else
                        current.add(id);
                }
            }
        }
    }

    @Test
    public void testGetRecord() throws HarvestingException, IOException {
        Consumer consumer = new Consumer();
        RecordResponseProcessor p = new RecordResponseProcessor(consumer);
        ResumptionToken r = 
            p.process(getClass().getResourceAsStream(getrec), 1);
        assertEquals(0, consumer.deleted.size());
        assertEquals(1, consumer.updated.size());
        assertTrue("unexpected rec id from get",
                     consumer.updated.contains("ivo://aip.gavo.org/__system__/services/registry"));
        assertNull(r);
    }

    static class Consumer implements HarvestConsumer {
        Set<String> updated = new HashSet<>();
        Set<String> deleted = new HashSet<>();
        ResourcePeeker peeker = null;

        Consumer() { this(false); }
        Consumer(boolean hinfo) { 
            if (hinfo) peeker = new ResourcePeeker(); 
        }

        @Override
        public void harvestStarting() {}

        @Override
        public void consume(HarvestedRecord record) throws IOException {
            if (record.isDeleted()) 
                deleted.add(record.getID());
            else
                updated.add(record.getID());

            if (peeker != null && record.isAvailable()) {
                File out = File.createTempFile("hrec",".xml", tmpdir);
                // out.deleteOnExit();
                FileWriter fwtr = new FileWriter(out);
                record.writeContent(fwtr);
                fwtr.close();

                BufferedReader rdr = new BufferedReader(new FileReader(out));
                rdr.readLine();
                String line = rdr.readLine();
                assertTrue("Missing <?harvestInfo?>",
                           line.startsWith("<?harvestInfo "));
                assertTrue("Missing harvestInfo content in:\n" + line,
                           line.contains("http://stsci/oai"));
                rdr.close();

                ResourceSummary res = peeker.load(out);
                assertEquals("http://stsci/oai", 
                             res.getHarvestedFromEndPoint());
                assertEquals("HarvestedRecord wrapping wrong data: ",
                             record.getID(), res.getID());
                if (record.isDeleted()) 
                    assertEquals(record.getID(), "deleted", res.getStatus());
                else if (! "active".equals(res.getStatus()) &&
                         ! "inactive".equals(res.getStatus()))
                    fail("Expected (in)active status; got <" + 
                         res.getStatus() + ">");
            }
        }

        @Override
        public void harvestDone(boolean successfully) { }
        @Override
        public boolean canRestart() { return false; }
    }
}