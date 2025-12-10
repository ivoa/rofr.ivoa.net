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
 *  2007/11/16  rlp  reconstructed from jad-decompiled class file
 */
package ncsa.xml.saxfilter;

import java.util.Set;

/**
 * an interface for instructing a parser the types of events to send to an
 * XML ContentHandler.  <p>
 *
 * This interface is usually used by a SAX ContentHandler (in particular,
 * a SAXFilterContentHandler) to tell a SAX parser what events it is 
 * interested in.  The parser can use this information to skip parsing
 * and preparation of XML components that are not of interest to the 
 * ContentHandler.  This interface can be passed to a SAXFilterContentHandler
 * via its setParseRequestMgr() method prior to the start of parsing.
 * <p>
 * Note that the parser is not required to not send events the handler is not 
 * interested in.  That is, if a handler uses this interface to say it is 
 * not interested in a particular event, the parser may send the event anyway.
 */
public interface OnDemandParser {

    public static final int CHARACTERS = 1;
    public static final int DOCUMENT = 2;
    public static final int ELEMENT = 4;
    public static final int ATTRIBUTES = 8;
    public static final int IGNORE_WHITE_SPACE = 16;
    public static final int SKIPPED_ENTITY = 32;
    public static final int PROC_INSTR = 64;
    public static final int NAMESPACES = 128;
    public static final int PREFIX_MAPPING = 256;
    public static final int ALL_EVENTS = 511;

    /**
     * enable the given OR-ed collection of events
     * @param events   the set of event codes defined by this interface,
     *                 OR-ed together
     */
    public void enableEvents(int events);

    /**
     * disable the given OR-ed collection of events
     * @param events   the set of event codes defined by this interface,
     *                 OR-ed together
     */
    public void disableEvents(int events);

    /**
     * start or stop sending character events to the ContentHandler
     * @param yes   if true, this event will be enabled; otherwise it will 
     *                 be disabled.
     */
    public void enableCharacters(boolean yes);

    /**
     * start or stop sending start/end-document events to the ContentHandler
     * @param yes   if true, this event will be enabled; otherwise it will 
     *                 be disabled.
     */
    public void enableDocument(boolean yes);

    /**
     * start or stop namespace parsing.  When processing is disabled, all
     * element and attribute names are passed as local names which may or may
     * not contain a namespace qualifier, and the qualified name will be an
     * empty string.
     * @param yes   if true, namespace parsing will be enabled; otherwise it 
     *                 will be disabled.
     */
    public void enableNamespaces(boolean yes);

    /**
     * start or stop sending start/end-Prefix events to the ContentHandler.
     * This event requires that namespace parsing is also enabled.  
     * @param yes   if true, this event will be enabled; otherwise it will 
     *                 be disabled.
     */
    public void enablePrefix(boolean yes);

    /**
     * start or stop sending start/end-Element events to the ContentHandler
     * @param yes   if true, this event will be enabled; otherwise it will 
     *                 be disabled.
     */
    public void enableElement(boolean yes);

    /**
     * start or stop sending processing instruction events to the 
     * ContentHandler
     * @param yes   if true, this event will be enabled; otherwise it will 
     *                 be disabled.
     */
    public void enableProcInstr(boolean yes);

    /**
     * start or stop sending skipped entity events to the ContentHandler
     * @param yes   if true, this event will be enabled; otherwise it will 
     *                 be disabled.
     */
    public void enableSkippedEntity(boolean yes);

    /**
     * start or stop loading Attributes into element events.  This only has
     * an effect when element events are enabled.
     * @param yes   if true, this event will be enabled; otherwise it will 
     *                 be disabled.
     * @see OnDemandParser#loadAttributes(Set)
     */
    public void enableAttributes(boolean yes);

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
    public void loadAttributes(String s, Set<String> set);

    /**
     * resume loading all attributes during element events.  This discards
     * all previous attribute selections made with loadAttributes().  
     */
    public void loadAllAttributes();

    /**
     * return true if given events are enabled
     * @param events   the OR-ed set of events to check
     */
    public boolean isEnabled(int events);

    /**
     * return true if any of the given events are enabled
     * @param events   the OR-ed set of events to check
     */
    public boolean anyEnabled(int events);

}
