package ncsa.xml.extractor;
 
import ncsa.xml.sax.Namespaces;
import ncsa.xml.saxfilter.SAXFilteredReader;
import ncsa.xml.saxfilter.SAXFilterContentHandler;
import ncsa.xml.saxfilter.OnDemandParser;
import ncsa.xml.saxfilter.OnDemandParserDelegate;
import ncsa.xml.saxfilter.SAXFilterFlowControl;
import ncsa.xml.saxfilter.CharContentLocator;

import java.io.Reader;
import java.io.PushbackReader;
import java.io.IOException;
import java.util.HashSet;
import java.util.StringTokenizer;
import java.util.Properties;
import java.util.Stack;
import java.util.Enumeration;

import org.xml.sax.SAXException;
import org.xml.sax.ContentHandler;
import org.xml.sax.Attributes;
import org.xml.sax.Locator;

/**
 * an XML parser that will extract out desired nodes from a XML document so
 * that can be saved as separate documents.  This parser makes sure that all
 * the necessary namespace definitions get carried along, regardless of where 
 * it was defined in the original document.  
 * 
 * <p>
 * The easiest way to extract elements is to tell the parser which ones you 
 * want with a call to one of the {@link extractElement(String) 
 * extractElement()} methods.  Next, a call to the {@link #nextNode()} method 
 * will return a Reader that will extract the first element with one of 
 * the configured names encountered in the document along with all its 
 * contents (which may include elements of the same name.  The next 
 * call to {@link #nextNode()} will return the next desired element 
 * encountered after the end of the last extracted node.  
 * 
 * <p>
 * Nodes can also be selected for extraction dynamically (i.e. based on the 
 * contents read from the document) by passing an 
 * {@link ExtractingContentHandler} as a 
 * {@link #setContentHandler(ContentHandler) content handler}.  Within its 
 * implementation of the SAX interface, can call the 
 * {@link ExportController#exportNode() ExportController's exportNode()} 
 * method to indicate that the node currently being parsed should be extracted.
 * 
 * @see #setContentHandler(ContentHandler)
 * @see ExtractingContentHandler
 * @see ExportController
 */
public class ExtractingParser implements Extractor {
    private SAXFilteredReader rdr = null;
    private PushbackReader extrdr = null;
    private ExportingHandler ech = null;
    private SAXFilterFlowControl flow = null;
    private HashSet exportElements = null;
    private HashSet ignore = new HashSet();
    private int exportDepth = 0;
    private char eofchar = '\004';
    private boolean standalone = true;

    /**
     * construct a parser for a given XML document
     * @param source   a reader for an opened XML document
     */
    public ExtractingParser(Reader source) {
        ech = new ExportingHandler();
        rdr = new SAXFilteredReader(source, ech);
    }

    /**
     * construct a parser for a given XML document that will use the given
     * SAX content handler.  See {@link setContentHandler(ContentHandler) 
     * setContentHandler()} for more details.
     * @param source   a reader for an opened XML document
     * @param handler  the content handler to use
     */
    public ExtractingParser(Reader source, ContentHandler handler) {
        this(source);
        setContentHandler(handler);
    }

    /**
     * set a SAX content handler.  This handler is allowed to do anything
     * in the course of the parsing; however, it can in particular make calls
     * to this parser to change its behavior based on the contents of the 
     * document.  In particular, if the content handler is specifically an
     * {@link ExtractingContentHandler}, it can tell the parser whic nodes to 
     * extract.  When a {@link ExtractingContentHandler} is passed to this 
     * method, it will call the handler's 
     * {@link ExtractingContentHandler#setExportController(ExportController)
     * setExportController()} method, passing in an {@link ExportController}
     * object.  When the handler encounters a node to be exported (e.g. within
     * its startElement() method), it can call the 
     * {@link ExportController#exportNode() ExportController's exportNode()} 
     * method.  
     */
    public void setContentHandler(ContentHandler ch) {
        ech.setScanner(ch);
    }

    /**
     * return a Reader set at the start of the next desired node.  It is 
     * important to note that requesting another Reader implicitly closes
     * the previous Reader.
     */
    public Reader nextNode() throws IOException {
        if (extrdr != null) {
            extrdr.close();
//            long eof = rdr.nextPauseMarker();
//            if (eof > rdr.readCount()) rdr.skip(eof - rdr.readCount());
        }

        int taste = rdr.read();
        if (taste < 0) return null;

        extrdr = new NodeReader(rdr);
        extrdr.unread(taste);
        return extrdr;
    }

    /**
     * set whether the Readers will be returning nodes as if they were 
     * complete documents.  This means that they will start with 
     * an XML declaration (i.e. "<?xml ...?>").  
     */
    public void setReturnsDoc(boolean yes) { standalone = yes; }

    /**
     * return true if the Readers will be returning them as if they were 
     * complete documents.  This means that they will start with 
     * an XML declaration (i.e. "<?xml ...?>").  
     */
    public boolean returnsDoc() { return standalone; }

    /**
     * tell the parser to ignore a given namespace.  That is, if it is defined
     * outside one of the desired node and is still in scope where the node
     * starts, the parser will not carry forward the namespace definition to 
     * the Readers returned by {@link nextNode()}.  
     * @param uri   the namespace to ignore given by its URI
     */
    public void ignoreNamespace(String uri) {
        if (uri != null) ignore.add(uri);
    }

    /**
     * tell the parser to look for elements of the given name and return 
     * Readers for extracting them via {@link nextNode()}.
     * @param nsuri    the element's namespace URI
     * @param locname  the element's local name
     */
    public void extractElement(String nsuri, String locname) {
        if (exportElements == null)
            exportElements = new HashSet();
        exportElements.add("{" + nsuri + "}" + locname);
    }

    /**
     * tell the parser to look for elements of the given name and return 
     * Readers for extracting them via {@link nextNode()}.
     * @param qname  the element's name, possibly qualified with a prefix
     */
    public void extractElement(String qname) {
        if (exportElements == null)
            exportElements = new HashSet();
        exportElements.add(qname);
    }

    /**
     * return a prolog to insert after the XML Declaration.  This may 
     * be different with each call.  This default implementation returns 
     * null, indicating that no prolog should be inserted.  This can be 
     * overridden by subclasses to specialize the wrapping of the output 
     * documents.  
     */
    protected String getExportProlog() { return null;  }

    /**
     * return a epilog append after the end of the closing element.  This may 
     * be different with each call.  This default implementation returns 
     * null, indicating that no epilog should be appended.  This can be 
     * overridden by subclasses to specialize the wrapping of the output 
     * documents.  
     */
    protected String getExportEpilog() { return null;  }

    class FlowECBridge implements ExportController {
        public FlowECBridge() { }

        public void exportNode() throws ExportingNotReadyException {
            if (exportDepth > 0)
                throw new ExportingNotReadyException("already exporting");
            if (ech.nodeType != 1) throw new 
                ExportingNotReadyException("Non-element node not exportable");

            exportDepth = 1;
            return;
        }

        public boolean isExporting() {
            return exportDepth > 0;
        }
    }

    

    class ExportingHandler implements SAXFilterContentHandler {
        OnDemandParser prm = null;
        OnDemandParserDelegate clientprm = null;
        ContentHandler scnr = null;
        Namespaces nsm = null;
        short nodeType = 0;
        String xmldecl = null;
        private static final String XSI = 
            "http://www.w3.org/2001/XMLSchema-instance";

        public ExportingHandler() { }

        public void setParseRequestMgr(OnDemandParser prm) {
            this.prm = prm;
            OnDemandParser _tmp = prm;
            prm.enableEvents(511);
        }

        public void setFlowController(SAXFilterFlowControl control) {
            flow = control;
            eofchar = flow.getPauseChar();
            flow.skipFrom(0L);
        }

        public void setNamespaces(Namespaces ns) { nsm = ns; }

        public void setScanner(ContentHandler ch) {
            scnr = ch;
            if (ch != null && (ch instanceof ExtractingContentHandler)) {
                ExtractingContentHandler ech = (ExtractingContentHandler)ch;
                if (flow != null)
                    ech.setExportController(new FlowECBridge());
                if (nsm != null)
                    ech.setNamespaces(nsm);
            }
        }

        public void startPrefixMapping(String prefix, String uri)
            throws SAXException 
        {
            if (scnr != null) scnr.startPrefixMapping(prefix, uri);
        }

        public void endPrefixMapping(String prefix) throws SAXException {
            if (scnr != null) scnr.endPrefixMapping(prefix);
        }

        public void startElement(String namespaceURI, String localName, 
                                 String qName, Attributes atts)
            throws SAXException
        {
            nodeType = 1;
            if (exportDepth > 0) exportDepth++;

            if (scnr != null) 
                scnr.startElement(namespaceURI, localName, qName, atts);

            if (exportDepth <= 0 && exportElements != null && 
                (exportElements.contains(qName) || 
                 exportElements.contains(localName) || 
                 exportElements.contains("{" + namespaceURI + "}" + localName)))
            {
                exportDepth = 1;
            }

            if (exportDepth == 1) {
                try {
                    if (standalone)
                        updateNamespaces(namespaceURI, localName, qName, atts);
                    startExport();
                }
                catch(IOException ex) {
                    throw new SAXException(ex);
                }
            }
            nodeType = 0;
        }

        private String prettyAttSpace(String el, int extra) {
            StringBuffer sb = new StringBuffer(el.length() + extra + 3);
            sb.append("\n  ");
            for(int i = el.length() + extra; i > 0; i--) sb.append(' ');

            return sb.toString();
        }

        protected void updateNamespaces(String namespaceURI, String localName, 
                                        String qName, Attributes atts)
            throws IOException
        {
            CharContentLocator cloc = flow.getCharLocator();
            String val = null;
            String space = null;
            String content = cloc.getContent();
            int end;
            for(end = content.length() - 1; 
                end > 0 && content.charAt(end) != '>'; 
                end--);
            if (content.charAt(end - 1) == '/') end--;
                
            long ins = cloc.getCharNumber() + end;
            int sl = atts.getIndex("http://www.w3.org/2001/XMLSchema-instance", 
                                   "schemaLocation");

            if (sl < 0) {
                StringBuffer locs = new StringBuffer();
                space = prettyAttSpace(qName, 20);
                Enumeration e = nsm.locatedNamespaces();
                while (e.hasMoreElements()) {
                    val = (String)e.nextElement();

                    if (!ignore.contains(val)) {
                        locs.append(val).append(space);
                        locs.append(nsm.getLocation(val));
                        if (e.hasMoreElements()) locs.append(space);
                    }
                } 

                if (locs.length() > 0) {
                    StringBuffer slatt = 
                        new StringBuffer(prettyAttSpace(qName, 0));
                    slatt.append(nsm.getPrefix(XSI));
                    slatt.append(":schemaLocation=\"");
                    slatt.append(locs.toString()).append('"');
                    flow.insert(slatt.toString(), ins);
                }
            } 
            else {
                Properties sloc = new Properties();
                StringTokenizer st = new StringTokenizer(atts.getValue(sl));
                while (st.hasMoreTokens()) {
                    val = st.nextToken();
                    if (st.hasMoreTokens()) 
                        sloc.setProperty(val, st.nextToken());
                } 

                space = prettyAttSpace(qName, atts.getQName(sl).length() + 2);
                StringBuffer slatt = new StringBuffer();
                Enumeration e = nsm.locatedNamespaces();
                while (e.hasMoreElements()) {
                    val = (String)e.nextElement();
                    if (!ignore.contains(val) && !sloc.containsKey(val)) {
                        if (slatt.length() > 0) slatt.append(space);
                        slatt.append(val).append(space);
                        slatt.append(nsm.getLocation(val));
                    }
                } 

                if (slatt.length() > 0) {
                    int p = content.indexOf(":schemaLocation=");
                    if (p >= 0 && p < content.length() - 1) {
                        p += ":schemaLocation=".length();
                        if (content.charAt(p) == '"' || 
                            content.charAt(p) == '\'')
                        {
                            p = content.indexOf(content.charAt(p), p + 1);
                        }
                        else {
                            p = content.length();
                        }
                        flow.insert(space + slatt.toString(), 
                                    cloc.getCharNumber() + (long)p);
                    }
                }
            }

            if (sl >= 0)
                ins = cloc.getCharNumber() + content.indexOf(atts.getQName(sl));

            if (atts.getIndex("xmlns") < 0 && 
                nsm.getDefaultNS().length() > 0 && 
                !ignore.contains(nsm.getDefaultNS()))
            {
                space = prettyAttSpace(qName, 0);
                StringBuffer xmlns = 
                    new StringBuffer(8 + nsm.getDefaultNS().length() + 
                                     space.length());

                if (sl < 0) xmlns.append(space);
                xmlns.append("xmlns=\"");
                xmlns.append(nsm.getDefaultNS()).append('"');

                if (sl >= 0)
                    xmlns.append(space);
                flow.insert(xmlns.toString(), ins);
            }

            String pref = null;
            space = prettyAttSpace(qName, 0);
            StringBuffer xmlns = new StringBuffer();
            Enumeration p = nsm.prefixes();
            while (p.hasMoreElements()) {
                pref = (String)p.nextElement();
                if (pref.length() > 0 && atts.getIndex("xmlns:" + pref) < 0 && 
                    !ignore.contains(nsm.getURI(pref)))
                {
                    if (sl < 0) xmlns.append(space);
                    xmlns.append("xmlns:").append(pref).append("=\"");
                    xmlns.append(nsm.getURI(pref)).append('"');
                    if (sl >= 0) xmlns.append(space);
                }
            } 

            if (xmlns.length() > 0) flow.insert(xmlns.toString(), ins);
        }

        public void endElement(String namespaceURI, String localName, 
                               String qName)
            throws SAXException
        {
            nodeType = 0;
            if (scnr != null) scnr.endElement(namespaceURI, localName, qName);
            if (exportDepth > 0) {
                exportDepth--;
                if (exportDepth <= 0) {
                    try {  endExport(); }
                    catch (IOException ex) { throw new SAXException(ex);  }
                }
            }
        }

        public void setDocumentLocator(Locator locator) {
            if (scnr != null) scnr.setDocumentLocator(locator);
        }

        public void startDocument() throws SAXException {
            nodeType = 9;
            if (scnr != null) scnr.startDocument();
        }

        public void endDocument() throws SAXException {
            nodeType = 0;
            if (scnr != null) scnr.endDocument();
        }

        public void characters(char ch[], int start, int length)
            throws SAXException
        {
            nodeType = 3;
            if (scnr != null) scnr.characters(ch, start, length);
        }

        public void ignorableWhitespace(char ch[], int start, int length)
            throws SAXException
        {
            nodeType = 3;
            if (scnr != null) scnr.ignorableWhitespace(ch, start, length);
        }

        public void processingInstruction(String target, String data)
            throws SAXException
        {
            nodeType = 7;
            if (standalone && "xml".equals(target))
                xmldecl = flow.getCharLocator().getContent() + "\n";
            if (scnr != null)
                scnr.processingInstruction(target, data);
        }

        public void skippedEntity(String name) throws SAXException {
            if (scnr != null) scnr.skippedEntity(name);
        }

        protected void startExport() throws IOException {
            flow.resumeFrom(flow.getCharLocator().getCharNumber());
            if (standalone && xmldecl != null)
                flow.insert(xmldecl, flow.getCharLocator().getCharNumber());

            // add a preamble (after the XML Decl.), if available
            String preamble = getExportProlog();
            if (preamble != null && preamble.length() > 0)
                flow.insert(preamble, flow.getCharLocator().getCharNumber());
        }

        protected void endExport() throws IOException {
            long end = flow.getCharLocator().getCharNumber() + 
                flow.getCharLocator().getCharLength();
            flow.skipFrom(end);
            flow.addPauseMarker(end);

            // add an epilog, if available
            String epilog = getExportEpilog();
            if (epilog != null && epilog.length() > 0)
                flow.insert(epilog, flow.getCharLocator().getCharNumber());
        }
    }

    class NodeReader extends PushbackReader {
        boolean atend = false;
        boolean closed = false;

        public NodeReader(Reader reader) { 
            super(reader, 1);
        }

        public int read(char cbuf[], int off, int len)
            throws IOException
        {
            if (closed) throw new IOException("Stream closed");
            if (atend) return -1;

            int n = super.read(cbuf, off, len);
            if (n >= 0 && n < len && cbuf[n + off] == eofchar) atend = true;
            return n;
        }

        public int read() throws IOException {
            if (closed) throw new IOException("Stream closed");
            if (atend) return -1;

            int c = super.read();
            if (c == eofchar) {
                atend = true;
                return -1;
            } 

            return c;
        }

        public void close() {
            try {
                if (! atend) {
                    while (read() >= 0);
                }
            } catch (IOException ex) { }
            atend = true;
            closed = true;
        }
    }
}
