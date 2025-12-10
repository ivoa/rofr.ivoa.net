package net.ivoa.registry.harvest;

import java.io.Reader;
import java.io.Writer;
import java.io.IOException;
import java.util.Properties;
import java.util.Set;
import java.util.Date;
import java.util.ArrayList;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.text.ParseException;
import java.text.ParsePosition;


/**
 * a wrapper around the native record content extracted from an OAI-PMH 
 * envelope.  The native record content is the content inside the OAI-PMH
 * &lt;metadata&gt; element; this is made available as a readable stream 
 * of XML data.  An instance will also give access to the record's header
 * data.  
 * <p>
 * If {@link #isAvailable()} returns true, then the native record content 
 * can be read from {@link #getContentReader()}.  As a convenience, one 
 * can instead call {@link #writeContent(Writer) writeContent()} to consume 
 * the content.  {@link #isAvailable()} will return false if a content was not 
 * provided for this record; this is normally because the record is marked
 * as deleted (with {@link isDeleted()} returning true).  In this case, the
 * consumer should process this record given the information from 
 * {@link getIdentifier()} and {@link getDatestamp()}.  Other global 
 * information regarding this record is available from the general record 
 * properties.  
 */
public class HarvestedRecord {

    Reader data = null;
    Properties props = null;
    boolean deleted = false;
    ArrayList<DateFormat> parsers = null;

    public static final String IDENTIFIER_PROP = "identifier";
    public static final String DATESTAMP_PROP = "datestamp";
    public static final String STATUS_PROP = "status";
    public static final String DELETED_STATUS = "deleted";

    public HarvestedRecord(Properties recprops) {
        this(recprops, null);
    }

    public HarvestedRecord(Properties recprops, Reader content, boolean deld) {
        if (recprops == null) 
            throw new 
                IllegalArgumentException("HarvestedRecord: missing properties");
        props = recprops;
        data = content;
        deleted = deld;
    }

    public HarvestedRecord(Properties recprops, Reader content) {
        this(recprops, content, content == null);
        String status = getProperty(STATUS_PROP);
        if (status != null) 
            deleted = DELETED_STATUS.equals(status);
    }

    /**
     * wrap a record
     * @param id      the identifier for the record
     * @param dstamp  the date stamp for the time the record was last updated
     * @param content a Reader set at the start of the record content; if 
     *                   null, the content will be unavailable.
     * @param status  if "deleted", the record is assumed to be deleted; if 
     *                   null and content is also null, the record is assumed 
     *                   to be deleted.
     */
    public HarvestedRecord(String id, String dstamp, Reader content, 
                         String status)
    {
        this(makeProps(id, dstamp, status), content);
    }

    /**
     * wrap a record
     * @param id      the identifier for the record
     * @param dstamp  the date stamp for the time the record was last updated
     * @param content a Reader set at the start of the record content; if 
     *                   null, the content will be unavailable and assumed
     *                   deleted.  
     */
    public HarvestedRecord(String id, String dstamp, Reader content) {
        this(id, dstamp, content, content == null);
    }

    /**
     * wrap a record
     * @param id      the identifier for the record
     * @param dstamp  the date stamp for the time the record was last updated
     * @param content a Reader set at the start of the record content; if 
     *                   null, the content will be unavailable and assumed
     *                   deleted.  
     */
    public HarvestedRecord(String id, String dstamp, Reader content,
                           boolean deld) 
    {
        this(makeProps(id, dstamp, null), content, deld);
    }

    /**
     * wrap an unavailable record
     * @param id      the identifier for the record
     * @param dstamp  the date stamp for the time the record was last updated
     * @param deld    true if the record is marked as deleted.
     */
    public HarvestedRecord(String id, String dstamp, boolean deld) {
        this(id, dstamp, null);
        deleted = deld;
    }

    /**
     * wrap a deleted, unavailable record
     * @param id      the identifier for the record
     * @param dstamp  the date stamp for the time the record was last updated
     */
    public HarvestedRecord(String id, String dstamp) {
        this(id, dstamp, true);
    }

    static Properties makeProps(String id,String dstamp,String status) {
        Properties out = new Properties();
        if (id != null) out.setProperty(IDENTIFIER_PROP, id);
        if (dstamp != null) out.setProperty(DATESTAMP_PROP, dstamp);
        if (status != null) out.setProperty(STATUS_PROP, status);
        return out;
    }


    /**
     * return a Reader set at the start of the record content.  This can 
     * be read to EOF to get the content.
     */
    public Reader getContentReader() {
        if (data == null)
            throw new IllegalStateException("Record data not available");
        return data;
    }

    /**
     * return true if the record content is available to be read via the 
     * Reader returned by {@link #getContentReader()}.  
     */
    public boolean isAvailable() { return data != null; }

    /**
     * return true if this record has been marked deleted. This does not 
     * determine whether the content is available (use {@link isAvailable()} 
     * instead); however, if isAvailable() returns false, this function 
     * normally returns true.  
     */
    public boolean isDeleted() { return deleted; }

    /**
     * return the identifier for this record
     */
    public String getID() { return props.getProperty(IDENTIFIER_PROP); }

    /**
     * return the date that record was last updated given as the string
     * provided in the OAI-PMH envelope.
     */
    public String getDatestamp() { return props.getProperty(DATESTAMP_PROP); }

    /**
     * return the date that record was last updated given parsed into a 
     * Date object
     */
    public Date getDate() throws ParseException {
        if (parsers == null) setDateFormats();
        String datestr = getDatestamp();
        if (datestr == null) 
            throw new IllegalStateException("no datestamp available");
        datestr = datestr.trim();

        Date out = null;
        ParsePosition pos = new ParsePosition(0);
        for(DateFormat parser : parsers) {
            pos.setIndex(0);
            out = parser.parse(datestr, pos); 
            if (out != null && pos.getIndex() == datestr.length())
                break;
            out = null;
        }
        if (out == null) 
            throw new ParseException("Date does not match any OAI-allowed " +
                                     "formats: " + datestr, 0);

        return out;
    }

    protected synchronized void initDateFormats() {
        parsers = new ArrayList<DateFormat>(3);
    }

    protected synchronized void addDateFormatStr(String fmtstr) {
        if (parsers == null) initDateFormats();
        parsers.add(new SimpleDateFormat(fmtstr));
    }

    protected synchronized void setDateFormats() {
        initDateFormats();
        addDateFormatStr("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        addDateFormatStr("yyyy-MM-dd'T'HH:mm:ss'Z'");
        addDateFormatStr("yyyy-MM-dd");
    }

    /**
     * return the named property
     */
    public String getProperty(String name) { return props.getProperty(name); }

    /**
     * return the names of the available properties
     */
    public Set<String> propertyNames() {
        return props.stringPropertyNames();
    }

    /**
     * write the record out to a given writer.  
     */
    public void writeContent(Writer out) throws IOException {
        char[] buf = new char[16*1024];
        int n = 0;
        final Reader in = getContentReader();
        synchronized (in) {
            while ((n = in.read(buf)) >= 0) 
                out.write(buf, 0, n);

            out.flush();
        }
    }
}
