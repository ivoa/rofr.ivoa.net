package net.ivoa.registry.harvest;

import ncsa.xml.extractor.ExtractingParser;
import ncsa.xml.extractor.ExtractingContentHandler;
import ncsa.xml.extractor.ExportingNotReadyException;
import ncsa.xml.extractor.ExportController;
import ncsa.xml.sax.Namespaces;
import net.ivoa.registry.std.RIStandard;

import java.io.Reader;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.NoSuchElementException;
import java.util.LinkedList;
import java.util.HashSet;
import java.util.Collection;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import java.io.Writer;
import java.io.File;
import java.io.FileReader;
import java.io.OutputStreamWriter;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.ContentHandler;
import org.xml.sax.helpers.DefaultHandler;

/**
 * a class that will extract harvested records from an OAI ListRecords (or 
 * GetRecord) response and pass them to a wrapped HarvestConsumer.  The 
 * {@link #process(InputStream,int) process()} method initiates processing 
 * on a stream which returns a {@link ResumptionToken} if one was in the 
 * response.
 */
public class RecordResponseProcessor {
    HarvestConsumer consumer = null;
    ExtractingParser parser = null;
    ResumptionToken resume = null;
    HarvestingException failure = null;
    String oains = null;
    Properties harvestInfo = null;
    LinkedList<Properties> recordInfos = null;
    int page = 1;
    LinkedList<HarvestListener> listeners = new LinkedList<HarvestListener>();
    int count = 0;

    /**
     * create an instance that will extract records from the given stream
     * @param consumer the consumer of records
     */
    public RecordResponseProcessor(HarvestConsumer consumer) {
        this.consumer = consumer;
        Properties ristd = RIStandard.getDefaultDefinitions();
        oains = ristd.getProperty(RIStandard.OAI_NAMESPACE);
    }

    /**
     * create an instance that will extract records from the given stream
     * @param consumer the consumer of records
     * @param harvestProps if non-null, extracted harvest data will be inserted 
     *                 into a "harvestInfo" processing instruction just after 
     *                 the XML declaration within each output record.  The 
     *                 properties given here will be included.  
     */
    public RecordResponseProcessor(HarvestConsumer consumer,
                                   Properties harvestProps) 
    {
        this(consumer);
        if (harvestProps != null)
            harvestInfo = new Properties(harvestProps);
    }

    /**
     * set a property that should be included as a harvestInfo property.
     * These properties will be inserted into a processing-instruction
     * called "harvestInfo" just after the XML declaration within each 
     * output.  If this class was not instantiated with 
     * withHarvestInfo=true, added properties are ignored.
     */
    public void addHarvestProperty(String name, String value) {
        if (harvestInfo != null) harvestInfo.setProperty(name, value);
    }

    /**
     * add a HarvestListener to receive select OAI-PMH data from the stream.
     */
    public void addListener(HarvestListener listener) {
        listeners.add(listener);
    }

    /**
     * add a collection of listeners
     */
    public void addListeners(Collection<HarvestListener> morelisteners) {
        if (morelisteners == null) return;
        for(HarvestListener listener : morelisteners) {
            addListener(listener);
        }
    }

    /**
     * return the resumption token encoded in the response.  Null is 
     * returned if no resumption token element was specified.  Because
     * the resumptionToken element is found at the end of the ListRecords
     * response, this should normally should only be called after all 
     * documents have been read via 
     * {@link #process(InputStream, int) process()}.  
     */
    public ResumptionToken getResumptionToken() { return resume; }

    /**
     * return true if a resumption token element was specific and includes 
     * an actual token.
     */
    public boolean shouldResume() { 
        return (resume != null && resume.moreRecords()); 
    }

    /**
     * return the number of records processed so far by this class
     */
    public int getProcessedCount() { return count; }

    /**
     * process the records in the wrapped response stream
     */
    public ResumptionToken process(InputStream is, int page) 
        throws HarvestingException, IOException 
    {
        return process(new InputStreamReader(is), page);
    }

    /**
     * process the records in the wrapped response stream
     */
    public ResumptionToken process(Reader is, int page) 
        throws HarvestingException, IOException 
    {
        resume = null;
        failure = null;
        this.page = page;
        recordInfos = new LinkedList<>();
        ExtractingParser parser = initParser(is);
        Reader rawrec;

        // consume records in input stream.  The parser draws the input 
        // (OAI-PMH) xml data through the parser and feeds it to us via
        // a series of Readers, each delivering a VOResource record.  The 
        // data is pulled through by asking for the next Reader.  This 
        // may also draw queue up deleted records that do not have VOResource
        // data associated with them; we'll consume any pending ones 
        // to maintain the order they appear in the input.  
        while ((rawrec = parser.nextNode()) != null) {
            // we have one or more available records to consume.
            Properties nextRecInfo = recordInfos.remove();
            while ("none".equals(nextRecInfo.getProperty("content"))) {
                // these are generally deleted records
                consumer.consume(new HarvestedRecord(nextRecInfo));
                count++;
                nextRecInfo = recordInfos.remove();
            }

            // content goes with this next one
            consumer.consume(new HarvestedRecord(nextRecInfo, rawrec));
            count++;
        }
        if (failure != null)
            throw failure;

        while (!recordInfos.isEmpty()) {
            // we've got some more deleted records left to process
            consumer.consume(new HarvestedRecord(recordInfos.remove()));
            count++;
        }

        return resume;
    }

    /**
     * this will be called when the OAI-PMH header element is encountered
     * to prep for incoming record data
     */
    protected void newRecord() {
        // initialize the record info that will go into and put it on queue
        recordInfos.add(new Properties(harvestInfo));
    }

    class IVOVORParser extends ExtractingParser {
        public IVOVORParser(Reader is) {
            super(is);
        }
        public String getExportProlog() {
            StringBuilder buf = new StringBuilder("<?harvestInfo ");
            for(Enumeration e=harvestInfo.propertyNames(); e.hasMoreElements();){
                String name = (String) e.nextElement();
                buf.append(name).append("=\"");
                buf.append(harvestInfo.getProperty(name,"")).append("\" ");
            }
            buf.append("?>\n");
            return buf.toString();
        }
    }

    protected ExtractingParser createParser(Reader instrm) {
        ExtractingParser out = null;
        if (harvestInfo != null) { 
            out = new IVOVORParser(instrm);
        }
        else {
            out = new ExtractingParser(instrm);
        }
        return out;
    }

    protected ExtractingParser initParser(Reader instrm) {
        MultiContentHandler ch = new MultiContentHandler();
        ch.addHandler(new RecordDetector());
        ch.addHandler(new ResumptionFinder());
        ch.addHandler(new Snooper());
        ch.addHandler(new OAIErrorDetector());

        ExtractingParser parser = createParser(instrm);
        parser.setContentHandler(ch);
        parser.ignoreNamespace(oains);
        // parser.ignoreNamespace(ristd.getProperty(OAI_DC_NAMESPACE));
        return parser;
    }

    /*
     * This ContentHandler looks for the OAI resumption token
     */
    class ResumptionFinder extends DefaultHandler {
        ResumptionToken tok = null;
        final String oaiuri = oains;
        final String resumption = "resumptionToken";

        public void startDocument() {
            tok = null;
        }

        public void startElement(String uri, String localname, String qName,
                                 Attributes atts) 
            throws SAXException
        {
            if (localname.equals(resumption) /* && oaiuri.equals(uri) */) {
                tok = new ResumptionToken("", atts.getValue("expirationDate"), 
                                          -1, -1);
                String val = null;
                try {
                    val = atts.getValue("completeListSize");
                    if (val != null) tok.size = Integer.parseInt(val);
                }
                catch (NumberFormatException ex) { /* be tolerant */ }
                try {
                    val = atts.getValue("cursor");
                    if (val != null) tok.curs = Integer.parseInt(val);
                }
                catch (NumberFormatException ex) { /* be tolerant */ }
            }
        }

        public void characters(char[] text, int start, int length) {
            if (tok != null) {
                String rt = new String(text);
                tok.tok = (tok.tok == null) ? rt : tok.tok+rt;
            }
        }

        public void endElement(String uri, String localname, String qName)
            throws SAXException
        {
            if (tok != null && 
                localname.equals(resumption) && oaiuri.equals(uri)) 
            {
                resume = tok;
                tok = null;
            }
        }
    }

    /*
     * this ContentHandler feeds information to HarvestListeners.  
     */
    class OAIErrorDetector extends DefaultHandler {
        MultiContentHandler container = null;
        String code = null;
        String message = null;
        final String oaiuri = oains;
        final String errorEl = "error";
        final String listRecordsEl = "ListRecords";
        public OAIErrorDetector() { this(null); }
        public OAIErrorDetector(MultiContentHandler c) { 
            container = c;
        }
        public void startDocument() { code = null; message = null; }
        public void startElement(String uri, String localname, String qName,
                                 Attributes atts) 
        {
            // some servers (HEASARC, I'm looking at you) are not qualifying
            // the error element properly
            // if (localname.equals(errorEl) && oaiuri.equals(uri)) {
            if (localname.equals(errorEl) && atts.getValue("code") != null) {
                code = atts.getValue("code");
                if (code == null || code.length() == 0) 
                    code = "unknown";
                else if (code.equals("noRecordsMatch")) 
                    code = null;
            }
            else if (localname.equals(listRecordsEl) && oaiuri.equals(uri)) {
                if (container != null) container.removeHandler(this);
            }
        }
        public void characters(char[] text, int start, int length) {
            if (code != null) {
                String txt = new String(text);
                message = (message == null) ? txt : message+txt;
            }
        }
        public void endElement(String uri, String localname, String qName)
            throws SAXException
        {
            if (code != null) {
                if (localname.equals(errorEl)) {
                    failure = new OAIPMHException(code, message);
                    if (container != null) container.removeHandler(this);
                }
            }
        }
    }


    /*
     * this ContentHandler feeds information to HarvestListeners.  
     */
    class Snooper extends DefaultHandler {
        LinkedList<HarvestListener> listeners = 
            new LinkedList<HarvestListener>();
        String id = null, name = null, value = null;
        final String oaiuri = oains;
        final String responseDate = "responseDate";
        boolean oai = false, header = false, deleted = false;

        public Snooper() {
            HashSet<String> need = new HashSet<String>();
            for(HarvestListener listener : RecordResponseProcessor.this.listeners) {
                need.clear();
                listener.tellWantedItems(need);
                if (need.contains(HarvestListener.RESPONSE_DATE) ||
                    need.contains(HarvestListener.RECORD_STATUS))
                    listeners.add(listener);
            }
        }
        public void startDocument() { name = null; }
        public void startElement(String uri, String localname, String qName,
                                 Attributes atts) 
        {
            if (! oai && uri.equals(oains)) oai = true;

            // check for RESPONSE_DATE
            if (oai) {
                if (localname.equals(responseDate)) 
                    name = HarvestListener.RESPONSE_DATE;
                else if (localname.equals("header")) {
                    header = true;
                    id = null;
                    if ("deleted".equals(atts.getValue("","status")))
                        deleted = true;
                }
                else if (header && localname.equals("identifier")) 
                    name = "identifier";
                else if (header && localname.equals("datestamp")) 
                    name = "datestamp";
                else if (localname.equals("metadata")) 
                    oai = false;
            }
        }
        public void characters(char[] text, int start, int length) {
            if (name != null) {
                String txt = new String(text);
                value = (value == null) ? txt : value+txt;
            }
        }
        public void endElement(String uri, String localname, String qName)
            throws SAXException
        {
            if (! oai && uri.equals(oains)) oai = true;

            if (oai) {
                if (name != null) {
                    if (localname.equals(responseDate)) {
                        // fix the date for those who get it wrong
                        value = fixDate(value);

                        // save this for the harvestInfo prolog
                        if (harvestInfo != null) 
                            harvestInfo.setProperty("date", value);
                    }
                    else if (localname.equals("identifier")) {
                        recordInfos.peekLast().setProperty(
                                       HarvestedRecord.IDENTIFIER_PROP, value);
                        id = value;
                        name = null;
                    }
                    else if (localname.equals("datestamp")) {
                        recordInfos.peekLast().setProperty(
                                       HarvestedRecord.DATESTAMP_PROP, value);
                        name = null;
                    }
                }
                else if (localname.equals("header")) {
                    header = false;
                    name = HarvestListener.RECORD_STATUS;
                    value = (deleted) ? "deleted" : "current";
                }

                if (name != null) {
                    for(HarvestListener listener : listeners) 
                        listener.harvestInfo(page, id, name, value);
                }
                name = null;
                value = null;
            }
        }

        final Pattern tz = Pattern.compile("\\s*[+\\-](\\d\\d:?\\d\\d)Z?$");
        final Pattern subsec = Pattern.compile("\\.\\d+Z?$");
        final Pattern dateof = Pattern.compile("^\\d{4}\\-\\d{2}-\\d{2}");
        final Pattern noTsep = Pattern.compile("(?<=\\-\\d{2})\\s+(?=\\d{2}:)");
        final Pattern correct = 
            Pattern.compile("^\\d{4}\\-\\d\\d-\\d\\d(T\\d\\d:\\d\\d:\\d\\dZ)?$");
        public String fixDate(String date) {
            if (correct.matcher(date).find())
                // Yeah!
                return date;
            if (! dateof.matcher(date).find()) 
                // oh well, what can we do?
                return date;

            // look for a timezone.  It's suppose to be in UTC; however,
            // we will assume that if they gave a local time, they're 
            // assuming local time as the from= argument.  
            Matcher m = tz.matcher(date);
            if (m.find()) date = m.replaceFirst("");

            m = subsec.matcher(date);
            if (m.find()) date = m.replaceFirst("Z");

            if (date.charAt(date.length()-1) != 'Z')
                date += "Z";

            m = noTsep.matcher(date);
            if (m.find()) date = m.replaceFirst("T");

            return date;
        }
    }

    /*
     * this ContentHandler looks for empty deleted records
     */
    class RecordDetector extends DefaultHandler 
        implements ExtractingContentHandler 
    {
        boolean oai = false;
        boolean deleted = false;
        boolean extractNext = false;
        boolean extracting = false;
        boolean detected = false;
        ExportController control = null;

        public void startElement(String uri, String localname, String qName,
                                 Attributes atts) 
        {
            if (! oai && uri.equals(oains)) oai = true;

            if (oai) {
                // look for status
                if (localname.equals("header")) {
                    detected = true;  // guards against <record/>
                    newRecord();
                    if ("deleted".equals(atts.getValue("","status"))) 
                        // record is deleted; Even though OAI-PMH suggests 
                        // that there should be no native record included,
                        // we'll look for one anyway.
                        recordInfos.peekLast().setProperty(
                                        HarvestedRecord.STATUS_PROP, "deleted");
                }
                else if (localname.equals("metadata")) {
                    // record was included
                    extractNext = true;
                    oai = false;
                }
            }
            else if (extractNext) {
                try { 
                    if (control != null) control.exportNode();
                } catch (ExportingNotReadyException ex) {
                    throw new IllegalStateException(ex.getMessage(), ex);
                }
                recordInfos.peekLast().setProperty("content", "avail");
                extracting = true;
                extractNext = false;
            }
        }

        public void endElement(String uri, String localname, String qName)
            throws SAXException
        {
            if (! oai && uri.equals(oains)) oai = true;

            if (oai) {
                if (localname.equals("record")) {
                    if (detected && ! extracting)
                        // i.e. if a native record was not included
                        recordInfos.peekLast().setProperty("content", "none");
                    extracting = false;
                    detected = false;
                }
            }
        }        

        public void setExportController(ExportController exportcontroller) {
            control = exportcontroller;
        }
        public void setNamespaces(Namespaces namespaces) { }
    }

    /*
     * this ContentHandler multiplexes to multiple delegate ContentHandlers
     */
    class MultiContentHandler extends DefaultHandler 
        implements ExtractingContentHandler 
    {
        LinkedList<ContentHandler> handlers = new LinkedList<ContentHandler>();
        public void addHandler(ContentHandler ch) { handlers.add(ch); }
        public void removeHandler(ContentHandler ch) { handlers.remove(ch); }
        public void startDocument() throws SAXException {
            for(ContentHandler handler : handlers) 
                handler.startDocument();
        }
        public void startElement(String uri, String localname, String qName,
                                 Attributes atts) 
            throws SAXException
        {
            for(ContentHandler handler : handlers) 
                handler.startElement(uri, localname, qName, atts);
        }
        public void characters(char[] text, int start, int length) 
            throws SAXException
        {
            for(ContentHandler handler : handlers) 
                handler.characters(text, start, length);
        }
        public void endElement(String uri, String localname, String qName)
            throws SAXException
        {
            for(ContentHandler handler : handlers) 
                handler.endElement(uri, localname, qName);
        }
        public void setExportController(ExportController exportcontroller) {
            for(ContentHandler handler : handlers) {
                if (handler instanceof ExtractingContentHandler)
                    ((ExtractingContentHandler) handler)
                        .setExportController(exportcontroller);
            }
        }
        public void setNamespaces(Namespaces namespaces) {
            for(ContentHandler handler : handlers) {
                if (handler instanceof ExtractingContentHandler)
                    ((ExtractingContentHandler) handler)
                        .setNamespaces(namespaces);
            }
        }
    }

}
