/*
 * NCSA BIMAgrid
 * Radio Astronomy Imaging Group
 * National Center for Supercomputing Applications
 * University of Illinois at Urbana-Champaign
 * 605 E. Springfield, Champaign IL 61820
 * rai@ncsa.uiuc.edu
 * 
 *-------------------------------------------------------------------------
 * History: 
 *  2001/07/26  rlp  initial version
 *  2001/09/24  rlp  added support for setPause().
 *  2007/11/16  rlp  reconstructed from jad-decompiled class file
 */
package ncsa.xml.saxfilter;

import java.io.*;
import java.util.*;
import ncsa.xml.sax.NamespaceMap;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

/**
 * a reader that filters its content via SAX.   <p>
 *
 * The purpose of this reader is to allow one to open an XML document
 * with an attached XML ContentHandler that can react to the XML data
 * while passing the contents as if from any other Reader.  In particular,
 * the ContentHandler has the ability to filter what data gets passed.
 * For example, it could cause certain nodes to be skipped over or have
 * the contents of another document inserted at an appropriate location.
 * The ability to control the flow requires a special type of 
 * ContentHandler, namely a {@link SAXFilterContentHandler}.
 * <p>
 * Note that the text that is read from this classes Reader interface is 
 * not a re-serialization of the XML data, but the original character
 * stream (apart from the changes made by the content handler), including 
 * the original spacing, namespace prefixes, etc.
 * <p>
 * It is intended that this class will evolve to fully support SAX parsing.
 */
public class SAXFilteredReader extends Reader {
    private ContentHandler chandler = null;
    private PushbackReader src = null;
    private final TextBuffer buf = new TextBuffer();
    private final OnDemandParserDelegate evts = new OnDemandParserDelegate();
    private final CharLocator loc = new CharLocator();
    private boolean strict = false;
    private boolean eof = false;
    private boolean started = false;
    private boolean parseAhead = false;

    // the trail of breadcrumbs indicating where flow should be turned on/off
    private final SkipSchedule skip = new SkipSchedule(false);

    // the stack of input sources 
    Stack<PushbackReader> srcstack = null;

    // the position relative to the start of the document of the beginning
    // of the characters in the TextBuffer
    private long cpos = 0;

    // sent = the number of characters from the beginning of the TextBuffer
    //        that have already been sent.  sent >= 0
    // parsed = the number of characters from the beginning of the TextBuffer
    //        that have been parsed.  parsed >= sent
    // cursor = the position from the start of the TextBuffer where 
    //        unparsed characters were last retrieved.  
    private int sent=0, parsed=0, cursor=0;


    private ParseRequestMgr prq = null;
    private final NamespaceMap namespaces = new NamespaceMap();

    private PauseMarkers pmarks = null;
    private char pausechar = '\004';
    private final char[] cb = new char[1];
    private boolean addpause = false;

    /**
     * create a "closed" reader.  No source reader is set, and so it behaves
     * as if it is closed.  It can be opened by calling the setSource()
     * method.
     */
    public SAXFilteredReader() {
        super();
    }

    /**
     * create a reader that retrieves text from a given reader.
     * @param source   the source Reader
     */
    public SAXFilteredReader(Reader source) {
        this();
        setSource(source);
    }

    /**
     * create a reader that retrieves text from a given reader.
     * @param source   the source Reader
     * @param ch       a content handler for filtering the XML data
     */
    public SAXFilteredReader(Reader source, ContentHandler ch) {
        this(source);
        setContentHandler(ch);
    }

    /**
     * return true if IOExceptions will be thrown when bad XML is 
     * encountered
     */
    public boolean isStrict() { return strict; }

    /**
     * set whether IOExceptions should be thrown when bad XML is 
     * encountered
     * @param val   if true, IOExceptions will be thrown
     */
    public void setStrict(boolean val) { strict = val; }

    /**
     * set the source of the text
     * @param source    the source Reader
     */
    public void setSource(Reader source) {
        src = new PushbackReader(source, 128);
        eof = false;
    }

    /**
     * return the current source of this Reader
     * null is returned if none has been set.
     */
    public Reader getSource() { return src; }

    /**
     * set the content handler that will receive SAX events
     */
    public void setContentHandler(ContentHandler handler) {
        chandler = handler;
        if (chandler != null && (chandler instanceof SAXFilterContentHandler)) {
            SAXFilterContentHandler sfhandler = 
                (SAXFilterContentHandler) handler;
            sfhandler.setParseRequestMgr(getPrm());
            sfhandler.setFlowController(getPrm());
            sfhandler.setNamespaces(namespaces);
        }
    }

    /**
     * set the character that will be used as a pause marker.  This should 
     * be a character that is not normally expected to be encounter in the
     * stream.
     */
    public void setPauseChar(char c) { pausechar = c; }

    /**
     * return the character that will be used as a pause marker.  The default 
     * is \004.  
     */
    public char getPauseChar() { return pausechar; }

    /**
     * schedule the insertion of a marker at the position where we should 
     * pause in the parsing
     */
    public void addPauseMarker(long pos) {
        if (pmarks == null)
            pmarks = new PauseMarkers();
        pmarks.setMark(pos);
    }

    private ParseRequestMgr getPrm() {
        if (prq == null) prq = new ParseRequestMgr();
        return prq;
    }

    /**
     * return the content handler that is set to receive SAX events.
     * null is returned if none has been set.
     */
    public ContentHandler getContentHandler() { return chandler; }

    /**
     * close this stream.  The source stream will in turn be closed and 
     * set to null.  To reopen this stream, pass an open stream to 
     * setSource().
     */
    public void close() throws IOException {
        if (src != null) {
            src.close();
            src = null;
        }
    }

    /**
     * Tell whether this stream is ready to be read without blocking.
     * Note that if no source is currently set, true is returned (but
     * any subsequent read will throw an IOException).
     */
    public boolean ready() throws IOException {
        return (src == null || src.ready());
    }

    /**
     * read a single character
     */
    public int read() throws IOException {
        int n=0;
        while(n == 0) { 
            n = read(cb, 0, 1);
            if (n == 0 && cb[0] == pausechar) break;
        }

        if (n < 0)
            return -1;
        else
            return cb[0];
    }

    /**
     * Read characters into a portion of an array.  If a content handler 
     * is set, the characters will trigger SAX events.
     */
    public synchronized int read(char[] chars, int off, int len)
        throws IOException
    {
        if (src == null)
            throw new IOException("read on closed input stream");
//        if (eof) throw new EOFException();

        int sendable;
        int need = len;
        int[] tilleof = new int[1];

        if (addpause) {
            chars[off] = pausechar;
            addpause = false;
            return 0;
        }

        try {
            while (need > 0) {
                sendable = parseBuffer(need);
                if (sendable <= 0) {
                    if (need >= len) return -1;
                    break;
                }

                sendable = Math.min(need, sendable);
                sendable = skipLimit(sendable);

                // check to see if we need to pause
                if (pmarks != null && sendable > 0) {
                    addpause = pmarks.applyPause(sendable, tilleof);
                    if (tilleof[0] < sendable) sendable = tilleof[0];
                }

                if (sendable > 0) {
                    buf.copy(sent, chars, off, sendable);
                    sent += sendable;
                    off += sendable;
                    need -= sendable;
                    skip.popTo(cpos+sent);
                }

                if (addpause && off < chars.length) {
                    chars[off] = pausechar;
                    addpause = false;
                    break;
                }
            }

//            if (need == len) throw new EOFException();
//            else if (need > 0) eof = true;

            if (eof && chandler != null && evts.isEnabled(OnDemandParser.DOCUMENT)) {
                chandler.endDocument();
            }
        } catch (SAXException ex) {
            if (ex instanceof IOinSAXException &&
                ((IOinSAXException)ex).getIOException() != null)
            {
                throw ((IOinSAXException)ex).getIOException();
            } else {
                throw new IOException("SAX processing error: " + 
                                      ex.getMessage());
            }
        }

        sendable = len-need;
        return sendable;
    }

    /**
     * return the number of characters read
     */
    public long readCount() {
        return sent + cpos;
    }

    /**
     * return the position of the next pause marker
     */
    public long nextPauseMarker() {
        return ((pmarks != null) ? pmarks.nextMark() : -1);
    }

    // a buffer for reading characters from the source stream
    private final char[] cbuf = new char[128];

    public static final String COMMENT_START = "<!--";
    public static final String COMMENT_END = "-->";
    public static final String PROC_INSTR_START = "<?";
    public static final String PROC_INSTR_END = "?>";
    public static final String CDATA_START = "<![CDATA[";
    public static final String CDATA_END = "]]>";
    public static final String EMPTYSTR = "";

    private static final String XSI = 
        "http://www.w3.org/2001/XMLSchema-instance";

    /**
     * parse and refresh characters sitting in the buffer until there are 
     * nchars unsent characters available.
     */
    private int parseBuffer(int nchars) throws IOException, SAXException {
        applySkip();
        if (parsed - sent > nchars) return parsed - sent;

        if (!started) {
            if (chandler != null && evts.isEnabled(OnDemandParser.DOCUMENT))
                chandler.startDocument();
            started = true;
        }

        // first shift off the characters no longer needed in the buffer
        trimBuffer();

        Substring sub = getSubstring(parsed);
        if (sub == null) return parsed - sent;

        // p = a pointer into the current Substring to a character position 
        //     of interest.  Initialized to local position of parsed.
        int p = sub.off;

        // lp = (mneumonic: local parsed) a pointer indicating the position 
        //      of parsed in the current Substring.  lp <= p.  can be < 0 if
        //      it corresponds to previously handled Substrings.
        int lp = p;

        while (parsed-sent < nchars || parseAhead) {

            // parse and process
            p = (sub != null ? sub.str().indexOf('<', lp) : 0);
            if (p >= 0) {

                // process any text nodes
                if (p > 0 && evts.isEnabled(OnDemandParser.CHARACTERS)) {
                    try {
                        handleChars(parsed, p - lp, true);
                    } catch(SAXException ex) {
                        if (strict) throw ex;
                    }

                    // handler may have changed size of buffer; use global
                    // vars (updated by handler) to reset local markers.
                    sub = getSubstring(cursor);
                    lp = parsed - cursor + (sub != null ? sub.off : 0);
                    p = lp + loc.getCharLength();
                    if (sub != null && sub.str().charAt(p) != '<')
                        throw new SAXException("stream manipulation broke " +
                                               "XML validity");
                }
                parsed += p - lp;  // move pointers past text (to '<')
                lp = p;

                if (sub != null && sub.str().startsWith(COMMENT_START, p)) {

                    // found a comment; advance to the end of it
                    p = sub.str().indexOf(COMMENT_END, p);
                    while (p < 0) {
                        lp -= sub.str().length();
                        sub = nextSubstring();
                        if (sub == null) break;
                        p = sub.str().indexOf("-->", sub.off);
                    } 
                    if (p < 0)
                        p = 0;
                    else
                        p += COMMENT_END.length();
//                    if (evts.isEnabled(COMMENTS)) {
//                        try {
//                            handleComment(parsed, p-lp);
//                        } catch (SAXException ex) {
//                          if (strict) throw ex;
// //                           throw new IOException("SAX processing error: " +
// //                                                 ex.getMessage());
//                        }
//                    }
                    parsed += p - lp;
                    lp = p;
                    if (sub == null) break;
                } 
                else if (sub != null && sub.str().startsWith(PROC_INSTR_START, p)) {

                    // found a processing instruction
                    p = sub.str().indexOf(PROC_INSTR_END, p);
                    while (p < 0) {
                        lp -= sub.str().length();
                        sub = nextSubstring();
                        if (sub == null) break;
                        p = sub.str().indexOf(PROC_INSTR_END, sub.off);
                    } 
                    if (p < 0)
                        p = 0;
                    else
                        p += PROC_INSTR_END.length();
                    if (evts.isEnabled(OnDemandParser.PROC_INSTR)) {
                        try {
                            handleProcInstr(parsed, p - lp);
                        } catch(SAXException ex) {
                            if (strict) throw ex;
//                             throw new IOException("SAX processing error: " + 
//                                                   ex.getMessage());
                        }

                        // handler may have changed size of buffer; use global
                        // vars (updated by handler) to reset local markers.
                        sub = getSubstring(cursor);
                        lp = parsed - cursor + (sub != null ? sub.off : 0);
                        p = lp + loc.getCharLength();
                    }
                    parsed += p - lp;  // move pointers past <?...?>
                    lp = p;
                    if (sub == null) break;
                } 
                else if (sub != null && sub.str().startsWith(CDATA_START, p)) {

                    // found a CDATA section instruction
                    p = sub.str().indexOf(CDATA_END, p);
                    while (p < 0) {
                        lp -= sub.str().length();
                        sub = nextSubstring();
                        if (sub == null) break;
                        p = sub.str().indexOf(CDATA_END, sub.off);
                    } 
                    if (p < 0) p = 0;
                    if (evts.isEnabled(OnDemandParser.CHARACTERS)) {
                        try {
                            handleChars(parsed+CDATA_START.length(), 
                                        p-lp, false);
                        } catch(SAXException ex) {
                            if (strict) throw ex;
//                             throw new IOException("SAX processing error: " + 
//                                                   ex.getMessage());
                        }

                        // handler may have changed size of buffer; use global
                        // vars (updated by handler) to reset local markers.
                        sub = getSubstring(cursor);
                        lp = parsed - cursor + (sub != null ? sub.off : 0);
                        p = lp + loc.getCharLength();
                    }
                    p += CDATA_END.length();
                    parsed += p - lp;
                    lp = p;
                    if (sub == null) break;
                } 
                else {

                    // found a tag; advance to the end of it
                    char tagtype = sub.str().charAt(p + 1);
                    p = sub.str().indexOf('>', p);
                    while (p < 0) {
                        lp -= sub.str().length();
                        sub = nextSubstring();
                        if (sub == null) break;
                        p = sub.str().indexOf('>', sub.off);
                    } 
                    if (p < 0)
                        p = 0;
                    else
                        p += 1;
                    if (tagtype != '!' && evts.anyEnabled(OnDemandParser.ELEMENT|
                                                          OnDemandParser.ATTRIBUTES|
                                                          OnDemandParser.NAMESPACES))
                    {
                        try {
                            if (tagtype == '/') {
                                handleEndElement(parsed, p - lp);
                            } else {
                                handleStartElement(parsed, p - lp);
                            }
                        } catch(SAXException ex) {
                            if (strict) throw ex;
                        }

                        // handler may have changed size of buffer; use global
                        // vars (updated by handler) to reset local markers.
                        sub = getSubstring(cursor);
                        lp = parsed - cursor + (sub != null ? sub.off : 0);
                        p = lp + loc.getCharLength();
                    }
                    parsed += p - lp;
                    lp = p;
                    if (sub == null) break;
                }
            } 
            else {
                if (evts.isEnabled(OnDemandParser.CHARACTERS) &&
                    lp < sub.str().length())
                {
                    try {
                        handleChars(parsed, sub.str().length() - lp, true);
                    } catch(SAXException ex) {
                        if (strict) throw ex;
                    }

                    // handler may have changed size of buffer; use global
                    // vars (updated by handler) to reset local markers.
                    sub = getSubstring(cursor);
                    lp = parsed - cursor + (sub != null ? sub.off : 0);
                }
                p = (sub != null ? sub.str().length() : 0);
                parsed += p - lp;
                lp = p;
            }

            applySkip();

            if ((parsed-sent < nchars || parseAhead) &&  // need more to send
                (sub == null || p >= sub.str().length() || p < 0)) // sub used up
            {
                lp -= (sub != null ? sub.str().length() : 0);
                sub = nextSubstring();
                if (sub == null) break;
            }
        }

        return parsed - sent;
    }

    private int applySkip() {
        int skipped = 0;
        if (skip.skipping()) {
            long swtch = skip.nextSwitch();
            skipped = (int) Math.min(parsed, swtch-cpos);
            sent = skipped;
            skip.popTo(cpos+skipped);
        }
        return skipped;
    }

    private int skipLimit(int need) {
        if (need <= 0) return 0;
        need -= applySkip();
        if (need <= 0) return 0;

        long sendable = skip.nextSwitch()-cpos-sent;
        if (sendable < 0) {
            System.err.println("Programmer warning: skip schedule out of sync");
            return need;
        }
        if (! skip.skipping && sendable < need) {
            // Note that if 0 < sendable < need, sendable will fit into an int
            need = (int)sendable;
        }

        return need;
    }

    /**
     * shift off the characters no longer needed in the buffer
     */
    private void trimBuffer() {
        if (sent > 0) {
            buf.popChars(sent);
            cpos += sent;
            parsed -= sent;
            sent = 0;
        }
    }

    /**
     * return the Substring containing the character at a given position,
     * resetting the iteration handled by nextSubstring().
     */
    private Substring getSubstring(int p) throws IOException {
        TextBuffer.Iter li = null;

        if (p < buf.size() || fillBuffer(p + 1) > p)
            li = buf.getSubstring(p);

//         if (li == null) {
//             System.err.println("position " + p + " out of range");
//      }

        if (li != null && li.hasNext()) {
            cursor = p;
            return (Substring) li.next();
        }
        cursor = buf.size();
        return null;
    }

    /**
     * get the next Substring
     */
    private Substring nextSubstring() throws IOException {
        Substring out = null;
        TextBuffer.Iter li = null;

        if (cursor < buf.size()) {
            li = buf.getSubstring(cursor);
            if (li.hasNext()) {
                // get the substring we got last time
                out = (Substring)li.next();  
                if (li.hasNext()) {
                    // now go to next one
                    cursor += out.str().length() - out.off;
                    return (Substring)li.next();
                }
            }
        }

        // need to fetch more characters; return next Substring
        int upto = buf.size() + 1;
        int got = fillBuffer(upto);
        out = getSubstring(upto-1);
        if (out != null) {
            out.off = 0;
            out.len = out.string.length();
        }
        return out;
    }

    /**
     * fill the buffer until contains at least p characters
     * @return int   the number of characters in the buffer as a result of the
     *               call to this method.
     */
    private synchronized int fillBuffer(int p) throws IOException {
        int n, nl;
        StringBuffer sb = new StringBuffer(Math.max(cbuf.length, 
                                                    p-buf.size()));
        while (p > buf.size()) {

            // add text to the buffer.  Each piece of new text should be 
            // terminated with a newline character to ensure easy parsing.
            nl = -1;
            n = cbuf.length;
            while (nl < 0) {
                n = src.read(cbuf, 0, cbuf.length);
                if (n < 0 && srcstack != null) {
                    while (n < 0 && !srcstack.isEmpty()) {
                        src.close();
                        src = srcstack.pop();
                        n = src.read(cbuf, 0, cbuf.length);
                    }
                }
                if (n < 0) break;

                // find the last newline 
                nl = n-1;
                while (nl >= 0 && cbuf[nl] != '\n') nl--;
                
                if (nl < 0) 
                    sb.append(cbuf, 0, n);     // no newline found; add it all
                else 
                    sb.append(cbuf, 0, nl+1);  // append up to end of line
            }

            buf.append(sb.toString());
            if (nl >= 0) {
                src.unread(cbuf, nl+1, n-nl-1);
            } else {
                eof = true;
                break;
            }
        }

        return buf.size();
    }

    /**
     * handle a text node.  This method updates the locator.
     * @param start   the starting position of the data relative to the 
     *                   start of the text buffer
     * @param len     the number characters making up the data
     * @param whiteIgnorable  any surrounding white space can be ignored.  
     */
    void handleChars(int start, int len, boolean whiteIgnorable)
        throws SAXException
    {
        if (chandler == null) return;
        int l, m;

        loc.setChars(cpos + (long)start, len);

        // copy characters into an array
        char[] sb = new char[len];
        ListIterator<Substring> li = buf.getSubstring(start);
        Substring sub = li.next();
        start = 0;
        l = Math.min(len, sub.str().length() - sub.off);
        while (len > 0) {
            sub.str().getChars(sub.off, sub.off + l, sb, start);
            len -= l;
            start += l;
            if (len > 0) sub = li.next();
            l = Math.min(sub.off+len, sub.str().length());
        }

        l = 0;
        m = sb.length;
        if (whiteIgnorable) {

            // look for leading white space
            for(l = 0; l < sb.length && Character.isWhitespace(sb[l]); l++);

            if (l > 0 && evts.isEnabled(OnDemandParser.IGNORE_WHITE_SPACE))
                chandler.ignorableWhitespace(sb, 0, l);

            // look for trailing white space
            for(m = sb.length; m > 0 && Character.isWhitespace(sb[m-1]); m--);
        }

        if (l < m) {
            chandler.characters(sb, l, m - l);
            if (m < sb.length && evts.isEnabled(OnDemandParser.IGNORE_WHITE_SPACE))
                chandler.ignorableWhitespace(sb, m, sb.length - m);
        }
    }

    void handleProcInstr(int start, int len) throws SAXException, IOException {
        if (chandler == null) return;
        int l, m;
        loc.setChars(cpos+start, len);

        ListIterator<Substring> li = buf.getSubstring(start);
        Substring sub = li.next();
        m = sub.off+PROC_INSTR_START.length();
        for(l=m+1; 
            l < sub.str().length() && 
                ! Character.isWhitespace(sub.str().charAt(l)); 
            l++);
        String target = sub.str().substring(m, l);
        len -= l-sub.off;

        StringBuilder sb = new StringBuilder();
        while (len > 0)  {
            m = Math.min(sub.str().length(), l+len);
            len -= m - l;
            if (len <= 0) m -= PROC_INSTR_END.length();
            sb.append(sub.str(), l, m);
        }

        chandler.processingInstruction(target, sb.toString());
    }

    void handleStartElement(int start, int len)
        throws SAXException
    {
        if (chandler == null) return;

        loc.setChars(cpos+start, len);

        boolean empty = false;

        // load element tag contents
        ListIterator<Substring> li = buf.getSubstring(start);
        Substring sub = li.next();
        StringBuilder sb = new StringBuilder();
        int use = 0, suboff = sub.off+1;
        len--;
        while (len > 0) {
            use = Math.min(len, sub.str().length() - suboff);
            len -= use;
            if (len <= 0) {
                if (sub.str().charAt((suboff + use) - 2) == '/') {
                    empty = true;
                    use--;
                }
                use--;
            }
            sb.append(sub.str(), suboff, suboff + use);
            if (len > 0) sub = li.next();
            suboff = 0;
        }
        StringTokenizer st = new StringTokenizer(sb.toString(), 
                                                 " \t\n\r\f", true);

        // get tag name
        if (!st.hasMoreTokens()) {
            if (strict)
                throw new SAXException("Element Tag without a name: " + 
                                       sb);
            return;
        }
        String elname = st.nextToken();
        String qelname = elname;
        String prefix = EMPTYSTR;
        String namesp = EMPTYSTR;
        {
            int p;
            if ((p = elname.indexOf(':')) >= 0) {
                prefix = qelname.substring(0, p);
                elname = qelname.substring(p + 1);
            }
        }
        if (st.hasMoreTokens())
            st.nextToken();

        // load attributes as desired
        AttributesImpl tmplist = new AttributesImpl();
        AttributesImpl attrlist = tmplist;
        StringBuilder aval;
        if (evts.anyEnabled(OnDemandParser.ATTRIBUTES | OnDemandParser.NAMESPACES)) {
            String attr, qname, nsname, pname, lname;
            int p, q;

            while (st.hasMoreTokens()) {
                attr = st.nextToken();
                if (Character.isWhitespace(attr.charAt(0))) continue;

                // parse attribute name and value
                p = attr.indexOf('=');
                if (p < 1 || p > attr.length()-3) {
                    if (strict)
                        throw new SAXException("bad attribute syntax: " + attr);
                    if (st.hasMoreTokens()) {
                        // being lenient for errant spaces around = in attributes
                        // (for HEASARC)
                        String tmp = st.nextToken();
                        while (st.hasMoreTokens() && 
                               (tmp.isEmpty() || tmp.charAt(0) == ' '))
                            tmp = st.nextToken();
                        p = tmp.indexOf('=');
                        if (p == 0) {
                            // ignore space before =
                            p = attr.length();
                            attr += tmp;
                            if (tmp.length() == 1) {
                                // ignore space after =
                                if (! st.hasMoreTokens()) continue;
                                tmp = st.nextToken();
                                while (st.hasMoreTokens() && 
                                       (tmp.isEmpty() || tmp.charAt(0)==' '))
                                    tmp = st.nextToken();
                                if (tmp.isEmpty() ||
                                    Character.isWhitespace(tmp.charAt(0)))
                                  continue;
                                attr += tmp;
                            }
                        }
                        else if (p < 0 || p > tmp.length()-3) {
                            // give up
                            continue;
                        }
                        else {
                            attr = tmp;
                        }
                    }
                    else {
                        continue;
                    }
                } 
                lname = attr.substring(0, p);
                qname = EMPTYSTR;
                nsname = EMPTYSTR;

                if (attr.charAt(++p) != '"' && attr.charAt(p) != '\'') {
                  if (strict)
                    throw new SAXException("bad attribute syntax: no quotes: "
                                           + attr);
                  p--;
                }
                aval = new StringBuilder(attr.substring(p + 1));
                if (attr.charAt(p) != '=') {
                    // we're being strict, so find the ending quote that 
                    // matches the quote character at position p
                    q = p + 1;
                    String tmp = attr;
                    while(st.hasMoreTokens() && 
                          (q = tmp.indexOf(attr.charAt(p), q)) < 0)
                    {
                        tmp = st.nextToken();
                        aval.append(tmp);
                        q = 0;
                    }
                }
                if (st.hasMoreTokens()) st.nextToken();

                // lop off possible trailing '>' and '/'
                if (aval.charAt(aval.length() - 1) == '>') {
                    aval.deleteCharAt(aval.length() - 1);
                    if (aval.charAt(aval.length() - 1) == '/')
                            aval.deleteCharAt(aval.length() - 1);
                }

                // lop off the trailing quote
                if (aval.charAt(aval.length() - 1) == '"' || 
                    aval.charAt(aval.length() - 1) == '\'')
                  aval.deleteCharAt(aval.length() - 1);

                String value = aval.toString();    // the parsed attribute val

                if (evts.isEnabled(OnDemandParser.NAMESPACES)) {
                    // check to see if attribute has a namespace prefix
                    qname = lname;
                    if ((q = qname.indexOf(':')) >= 0) {
                        lname = qname.substring(q + 1);
                        pname = qname.substring(0, q);

                        if (evts.isEnabled(OnDemandParser.PREFIX_MAPPING)) {
                            if (pname.equals("xmlns")) {
                                // register a namespace definition
                                namespaces.startPrefixMapping(lname, value);
                                chandler.startPrefixMapping(lname, value);
                            } 
                            else if (namespaces.getURI(pname) != null) {
                                nsname = namespaces.getURI(pname);
                            }
                        }
                    }
                    else if (evts.isEnabled(OnDemandParser.PREFIX_MAPPING) &&
                             qname.equals("xmlns"))
                    {
                        nsname = value;
                        namespaces.setDefaultNS(nsname);
                        chandler.startPrefixMapping(EMPTYSTR, value);
                    }
                    if (XSI.equals(nsname) && lname.equals("schemaLocation"))
                        namespaces.addLocations(value);
                }
                tmplist.addAttribute(nsname, lname, qname, "CDATA", value);
            } 

            if (evts.isEnabled(OnDemandParser.PREFIX_MAPPING)) {
                if (!prefix.isEmpty()) {
                    namesp = namespaces.getURI(prefix);
                    if (namesp == null) namesp = EMPTYSTR;
                } 
                else {
                    namesp = namespaces.getDefaultNS();
                }
            }

            if (evts.isEnabled(OnDemandParser.ATTRIBUTES)) {
                Set<String> wantatts = null;
                if (!namesp.isEmpty())
                    wantatts = evts.attributesFor(namesp + elname);
                if (wantatts == null && !qelname.isEmpty())
                    wantatts = evts.attributesFor(qelname);
                if (wantatts == null)
                    wantatts = evts.attributesFor(elname);
                if (wantatts == null)
                    wantatts = evts.attributesFor(EMPTYSTR);

                if (wantatts != null) {
                    attrlist = new AttributesImpl();
                    for(int i = 0; i < tmplist.getLength(); i++) {
                        if (wantatts.contains(tmplist.getQName(i)))
                            attrlist.addAttribute(tmplist.getURI(i), 
                                                  tmplist.getLocalName(i), 
                                                  tmplist.getQName(i), 
                                                  tmplist.getType(i), 
                                                  tmplist.getValue(i));
                    }
                }
            } 
            else {
                attrlist = new AttributesImpl();
            }
        }

        namespaces.startElement();
        chandler.startElement(namesp, elname, qelname, attrlist);
        if (empty) {
            HashSet<String> prefixes = null;
            if (evts.anyEnabled(OnDemandParser.PREFIX_MAPPING | OnDemandParser.NAMESPACES))
                prefixes = new HashSet<String>();
            chandler.endElement(namesp, elname, qelname);
            namespaces.endElement(prefixes);

            if (evts.anyEnabled(OnDemandParser.PREFIX_MAPPING | OnDemandParser.NAMESPACES) &&
                prefixes != null)
            {
                if (evts.anyEnabled(OnDemandParser.PREFIX_MAPPING)) {
                    for(Iterator it = prefixes.iterator(); 
                        it.hasNext(); 
                        chandler.endPrefixMapping((String)it.next()));
                }
            }
        }
    }

    void handleEndElement(int start, int len)
        throws SAXException
    {
        if (chandler == null) return;
        int p;
        String prefix, elname, qelname, uri;
        HashSet<String> prefixes = null;
        loc.setChars(cpos + (long)start, len);

        ListIterator<Substring> li = buf.getSubstring(start);
        Substring sub = li.next();

        int suboff = sub.off + 2;
        len -= 3;
        if (suboff+len > sub.str().length())
            len = sub.str().length()-suboff;
        elname = sub.str().substring(suboff, suboff + len);
        qelname = elname;
        prefix = EMPTYSTR;

        if ((p = qelname.indexOf(':')) >= 0) {
            prefix = qelname.substring(0, p);
            elname = qelname.substring(p + 1);
        }

        if (evts.anyEnabled(OnDemandParser.NAMESPACES | OnDemandParser.PREFIX_MAPPING))
            prefixes = new HashSet<String>();
        uri = namespaces.getURI(prefix);
        if (uri == null) uri = EMPTYSTR;
        chandler.endElement(uri, elname, qelname);
        namespaces.endElement(prefixes);

        // update the scope of namespace prefixes as necessary
        if (prefixes != null && 
            evts.anyEnabled(OnDemandParser.NAMESPACES | OnDemandParser.PREFIX_MAPPING))
        {
            if (evts.isEnabled(OnDemandParser.PREFIX_MAPPING)) {
                for(Iterator<String> i = prefixes.iterator();
                    i.hasNext(); 
                    chandler.endPrefixMapping((String)i.next()));
            }
        }
    }

    class PauseMarkers {
        private final TreeSet<Long> marks = new TreeSet<>();

        public PauseMarkers() {
        }

        public PauseMarkers(long mark) {
            setMark(mark);
        }

        public void setMark(long pos) {
            marks.add(pos);
        }

        public long nextMark() {
            for (Long next : marks) {
                if (next - cpos - ((long) sent) >= 0)
                    return next;
            }

            return -1;
        }

        public boolean applyPause(int want, int[] send) {
            Long next = null;
            long diff = 0;

            send[0] = want;
            while(!marks.isEmpty())  {
                next = marks.first();
                diff = next - cpos - (long)sent;
                if (diff > (long) want) return false;
                    
                marks.remove(next);
                if (diff >= 0L) {
                    send[0] = (int) diff;
                    return true;
                }
            }

            return false;
        }
    }
    
    /**
     * an interface for controlling this Reader.  An instance of this 
     * class is usually passed to compatible ContentHandlers allowing
     * them to control the flow of characters from this Reader.
     */
    public class ParseRequestMgr implements OnDemandParser, 
        SAXFilterFlowControl
    {
        /**
         * create the controller attached the parent Reader
         */
        public ParseRequestMgr() { }

        /**
         * stop sending characters from the source stream starting at the 
         * given character position.  
         * @param pos   the zero-based character index relative to the start 
         *                  of the file.
         */
        public void skipFrom(long pos) {
            skip.skipFrom(pos);
        }

        /**
         * pause the sending of characters so that more input data can be 
         * parsed until further notice.  This does not cause any data to be 
         * skipped; however, by parsing ahead one can issue a skip request 
         * starting at a previously parsed position and guarantee that it is 
         * honored.
         */
        public void setPause(boolean pause) {
            parseAhead = pause;
        }

        /**
         * resume sending characters from the source stream starting at the 
         * given character position.  
         * @param pos   the zero-based character index relative to the start 
         *                  of the file.
         */
        public void resumeFrom(long pos) {
            skip.skipTo(pos);
        }

        /**
         * insert a string at the the given position in the stream.
         * @param chars  the character data to insert into the reader's stream.
         * @param pos    the zero-based character index relative to the start
         *                   of the file to insert the string at.  
         * @throws IllegalStateException  if the indicated position has already
         *            been received by the reader.  (That is, you can't go too 
         *            far back in time).
         */
        public void insert(String chars, long pos) throws IOException {

            // can't insert before characters already sent out to Reader
            if (pos < cpos + (long)sent)
                throw new IllegalStateException("Too late for insert");

            int bpos = (int)(pos - cpos);  // position within buffer
            if (bpos == buf.size()) {
                buf.append(chars);
            } 
            else if (bpos > buf.size()) {
                // FIXME: allow this in the future
                throw new IllegalStateException("Too soon for insert");
            }
            else {
                buf.insert(chars, bpos);
            }

            // adjust our markers.  If bpos (the insert position relative 
            // to the the start of the buffer) is at the parsed position or 
            // before, we will interpret the new text as already parsed.
            if (bpos <= parsed) parsed += chars.length();
            if (bpos < cursor) cursor += chars.length();
            if (pos <= loc.getCharNumber()) {
                loc.setChars(loc.getCharNumber()+chars.length(), 
                             loc.getCharLength());
            }
            else if (pos < loc.getCharNumber()+loc.getCharLength()) {
                loc.setChars(loc.getCharNumber(), 
                             loc.getCharLength()+chars.length());
            }

            // update the skip schedule
            skip.insert(pos, chars.length());
        }

        /**
         * substitute in a string for a range of characters in the stream.  
         * This method will adjust the parser's markers and buffer iterator
         * accordingly. 
         * @param chars  the character data to substitute into the reader's 
         *                   stream
         * @param pos    the zero-based character index relative to the start
         *                   of the file to begin the substitution at. 
         * @param len    the number of characters to pull out of the stream 
         *                   beginning at the pos position.
         */
        public void substitute(String chars, long pos, int len)
            throws IOException
        {
            // can't insert before characters already sent out to Reader
            if (pos < cpos+sent)
                throw new IllegalStateException("Too late for substitution");

            int bpos = (int) (pos - cpos);  // position within buffer
            if (bpos == buf.size()) {
                buf.append(chars);
            } 
            else if ((bpos + len) - 1 > buf.size()) {
                // FIXME: allow this in the future
                throw new IllegalStateException("Too soon for substitution");
            }
            else {
                buf.substitute(chars, bpos, len);

                // adjust the parsed marker position
                if (bpos < parsed) {
                    if (bpos + len <= parsed) {
                        // the new text falls before the parsed marker: just
                        // push marker up the appropriate amount
                        parsed += chars.length()-len;
                    }
                    else {
                        // the new text strattles the parsed marker;
                        // move marker to end of new text.  
                        // (Probabably a dangerous request.)
                        parsed = bpos + len;
                    }
                }

                if (bpos+len <= cursor)
                    // if the end position of what we took out is before the 
                    // cursor, advance the cursor by the # of extra characters
                    // added.  (If net loss, cursor is reduced.)
                    cursor += chars.length() - len;
                else if (bpos+len > cursor && bpos+chars.length() <= cursor)
                    // if the range we took out straddled the cursor but 
                    // the range put in fell below the cursor, move cursor to 
                    // end of range of characters added.  
                    cursor = bpos + chars.length();

                // update the locator position
                if (pos + (long)len <= loc.getCharNumber()) {
                    // substitution range is prior to locator start position:
                    // just push locator range up.
                    loc.setChars(loc.getCharNumber()+chars.length()-len, 
                                 loc.getCharLength());
                } 
                else if (pos+len < loc.getCharNumber() + loc.getCharLength()) {
                    // substitution range is prior to locator end position:
                    // extend range of locator by the amount of text added.
                    loc.setChars(loc.getCharNumber(), 
                                 (int) loc.getCharLength()+chars.length()-len);
                }
                else if (pos < loc.getCharNumber()+loc.getCharLength() && 
                         pos+len >= loc.getCharNumber()+loc.getCharLength() && 
                         pos+chars.length() < 
                              loc.getCharNumber()+loc.getCharLength())
                {
                    // range of characters to pull out strattles locator end 
                    // position, but new text falls within the locator range:
                    // pull the end of the locator range to the end of the 
                    // substituted text.  (probably a dangerous situation)
                    loc.setChars(loc.getCharNumber(), 
                                 (int)(pos+chars.length()-loc.getCharNumber()));
                }

                // update the skip schedule
                skip.insert(pos, chars.length() - len);

                // update the buffer iterator
//              getSubstring((int) (loc.getCharNumber() + loc.getCharLength() +
//                                  1 - cpos));
            }
        }

        /**
         * push a new input stream onto the stack of input streams.  This is 
         * used for inserting one document into another.  If the given position
         * is passed, the source will be closed and ignored.  
         */
        public void pushSource(Reader source)  {
            if (srcstack == null) srcstack = new Stack<>();

            // push our current source onto the source stack
            srcstack.push(src);

            // if our buffer has unparsed characters, push them onto the stack
            // in the form of StringReaders (wrapped in PushbackReaders).
            int pending = ((int) (loc.getCharNumber()-cpos)) + 
                                                           loc.getCharLength();
            if (buf.size() > pending) {
                ListIterator<Substring> li = buf.getSubstring(pending);
                Substring psub = (Substring)li.next();
                String head = psub.str().substring(0, psub.off);
                String tail = psub.str().substring(psub.off);

                if (!tail.isEmpty())
                    srcstack.push(
                        new PushbackReader(new StringReader(tail), 128));

                li = buf.iterAtLast();
                Substring sub = null;
                while (li.hasPrevious()) {
                    sub = (Substring) li.previous();
                    if (sub == psub) break;
                    li.remove();

                    srcstack.push(
                        new PushbackReader(new StringReader(sub.str()), 128));
                } 

                if (!head.isEmpty()) {
                    if (sub == psub)
                        buf.replace(sub, head);
                    else
                        buf.append(head);
                }
            }

            // make the new source the current source
            src = new PushbackReader(source, 128);
        }

        /**
         * schedule the insertion of a marker at the position where we should 
         * pause in the parsing
         */
        public void addPauseMarker(long pos) {
            SAXFilteredReader.this.addPauseMarker(pos);
        }

        /**
         * set the character that will be used as a pause marker
         */
        public char setPauseChar(char c) {
            char out = SAXFilteredReader.this.getPauseChar();
            SAXFilteredReader.this.setPauseChar(c);
            return out;
        }
    
        /**
         * return the character that will be used as a pause marker
         */
        public char getPauseChar() {
            return SAXFilteredReader.this.getPauseChar();
        }

        /**
         * enable the given OR-ed collection of events
         * @param events   the set of event codes defined by this interface,
         *                 OR-ed together
         */
        public void enableEvents(int events) {
            if ((events & 0x100) > 0)
            {
                events |= 0x80;
            }
            evts.enableEvents(events);
        }

        /**
         * disable the given OR-ed collection of events
         * @param events   the set of event codes defined by this interface,
         *                 OR-ed together
         */
        public void disableEvents(int events) {
            evts.disableEvents(events);
        }

        /**
         * start or stop sending character events to the ContentHandler
         * @param yes   if true, this event will be enabled; otherwise it will 
         *                 be disabled.
         */
        public void enableCharacters(boolean yes) {
            evts.enableCharacters(yes);
        }

        /**
         * start or stop sending start/end-document events to the 
         * ContentHandler
         * @param yes   if true, this event will be enabled; otherwise it will 
         *                 be disabled.
         */
        public void enableDocument(boolean yes) {
            evts.enableDocument(yes);
        }

        /**
         * start or stop namespace parsing.  When processing is disabled, all
         * element and attribute names are passed as local names which may or 
         * may not contain a namespace qualifier, and the qualified name will 
         * be an empty string.
         * @param yes   if true, namespace parsing will be enabled; otherwise 
         *                 it will be disabled.
         */
        public void enableNamespaces(boolean yes) {
            evts.enableNamespaces(yes);
        }

        /**
         * start or stop sending start/end-Prefix events to the ContentHandler
         * @param yes   if true, this event will be enabled; otherwise it will 
         *                 be disabled.
         */
        public void enablePrefix(boolean yes) {
            evts.enablePrefix(yes);
            if (yes) enableNamespaces(yes);
        }

        /**
         * start or stop sending start/end-Element events to the ContentHandler
         * @param yes   if true, this event will be enabled; otherwise it will 
         *                 be disabled.
         */
        public void enableElement(boolean yes) {
            evts.enableElement(yes);
        }

        /**
         * start or stop sending processing instruction events to the 
         * ContentHandler
         * @param yes   if true, this event will be enabled; otherwise it will 
         *                 be disabled.
         */
        public void enableProcInstr(boolean yes) {
            evts.enableProcInstr(yes);
        }

        /**
         * start or stop sending skipped entity events to the ContentHandler
         * @param yes   if true, this event will be enabled; otherwise it will 
         *                 be disabled.
         */
        public void enableSkippedEntity(boolean yes) {
            evts.enableSkippedEntity(yes);
        }

        /**
         * start or stop loading Attributes into element events.  This only has
         * an effect when element events are enabled.
         * @param yes   if true, this event will be enabled; otherwise it will 
         *                 be disabled.
         * @see OnDemandParser#loadAttributes(String, Set)
         */
        public void enableAttributes(boolean yes) {
            evts.enableAttributes(yes);
        }

        /**
         * when sending an event for a given element, pass only selected 
         * attributes.  By default, all attributes will be loaded and passed
         * to the ContentHandler during element events (assuming attributes
         * have be enabled).  The parser should compare the given 
         * <code>elname</code> first with the qualified name and then with the 
         * local name.  If <code>elname</code> is null or empty, the attribute 
         * selection applies to all, otherwise-unspecified elements.
         * @param elName  the qualified or local name for the element.  If 
         *                  null or empty, the attribute selection applies to 
         *                  all, otherwise-unspecified elements.
         * @param attrs   the attributes given as a Set of Strings.  A null 
         *                  value or empty set resets the selection such that 
         *                  all attributes will be loaded.
         */
        public void loadAttributes(String elname, Set<String> attnames) {
            evts.loadAttributes(elname, attnames);
        }

        /**
         * resume loading all attributes during element events.  This discards
         * all previous attribute selections made with loadAttributes().  
         */
        public void loadAllAttributes() {
            evts.loadAllAttributes();
        }

        /**
         * return true if given events are enabled
         * @param events   the OR-ed set of events to check
         */
        public boolean isEnabled(int events) {
            return evts.isEnabled(events);
        }

        /**
         * return true if any of the given events are enabled
         * @param events   the OR-ed set of events to check
         */
        public boolean anyEnabled(int events) {
            return evts.anyEnabled(events);
        }

        /**
         * return a reference to a CharContentLocator for obtaining character
         * positions of content events.  The same locator should be usable
         * throughout the parsing of a document; thus, this method need only 
         * be called once.  
         */
        public CharContentLocator getCharLocator() {
            return loc;
        }
    }

    class CharLocator implements CharContentLocator {
        long start = 0;
        int length = 0;

        public final long getCharNumber() { return start; }
        public final int getCharLength() { return length; }

        public final void setChars(long begin, int len) {
            start = begin;
            length = len;
        }

        public String getContent() {
            if (start < cpos)
                throw new IllegalStateException("Content missing from memory");

            StringBuilder sb = new StringBuilder(length);
            ListIterator<Substring> li = buf.getSubstring((int)(start - cpos));
            int need = length; 
            while (li.hasNext() && need > 0) {
                Substring sub = li.next();
                sub.len = Math.min(sub.str().length() - sub.off, need);
                sb.append(sub);
                need -= sub.len;
            }

            return sb.toString();
        }
    }
} 
