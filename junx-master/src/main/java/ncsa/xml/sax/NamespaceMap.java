package ncsa.xml.sax;

import java.util.Properties;
import java.util.Enumeration;
import java.util.Stack;
import java.util.Set;
import java.util.HashSet;
import java.util.StringTokenizer;

/**
 * keep track of the evolving set of namespaces in use while traversing an
 * XML hierarchy.  This class provides an interface for updating the 
 * mappings while traversing or parsing an XML document (e.g. in the context
 * of a SAX parser).  
 * <p>
 * There are four functions one will use to update the set of namespaces in 
 * currency, listed here in the order that they are called:
 * <ol>
 *  <li> {@link #startPrefixMapping() startPrefixMapping()} / 
 *       {@link #setDefaultNS() setDefaultNS()} </li>
 *  <li> {@link #startElement() startElement()} </li>
 *  <li> {@link #endElement() endElement()} </li>
 * </ol>
 * If when entering an element there are no new namespaces are defined, then 
 * {@link #startPrefixMapping() startPrefixMapping()} and 
 * {@link #setDefaultNS() setDefaultNS()} need not be called.  Normally,
 * it is <em>not</em> necessary to call 
 * {@link #endPrefixMapping() endPrefixMapping()}.
 * <p>
 * This order corresponds to the order in which SAX ContentHandler calls are 
 * made with one exception: {@link #endPrefixMapping() endPrefixMapping()}
 * should not be called (it is retained for backward compatibility but is 
 * gutted of functionality).  Prefix mappings are discontinued automatically
 * via the call to {@link #endPrefixMapping() endPrefixMapping()}.  If one 
 * wishes to remove a prefix mapping prior to a call to 
 * {@link #endElement() endElement()}, one can call 
 * {@link #endPrefixMapping() endPrefixMapping()}.  
 */
public class NamespaceMap implements Namespaces, Cloneable {

    final int READY = 0;
    final int ADDING = 1;
    private final Stack<Snapshot> history = new Stack<>();
    private Snapshot cur = new Snapshot();
    private int state = READY;
    private int validityDepth = 0;
    private static int anoncounter = 0;

    /**
     * create an empty NamespaceMap
     */
    public NamespaceMap() { }

    /**
     * register a prefix mapping that will be coming into scope with entering 
     * a new element.  A call to {@link #startElement() startElement()} should
     * be made after completing all calls to this method (and 
     * {@link #setDefaultNS() setDefaultNS()}).  If one wants to add a 
     * namespace in the current element context <em>after</em> the call to 
     * {@link #startElement() startElement()}, one should call 
     * {@link #addPrefixMapping() addPrefixMapping()}.  
     */
    public synchronized void startPrefixMapping(String prefix, String uri) {
        if(state == READY) setAddingState();
        cur.addMapping(prefix, uri);
    }

    /**
     * add a prefix mapping that is not associated with the change of the
     * element context.  Normally during an XML traversal, this method 
     * would <em>not</em> be called.  It is available for adding namespaces
     * to the current context after a call to 
     * {@link #startElement() startElement()}.  
     */
    public synchronized void addPrefixMapping(String prefix, String uri) {
        cur.addMapping(prefix, uri);
    }

    void setAddingState() {
        cur.validityDepth = validityDepth;
        history.push(cur);
        cur = new Snapshot(cur);
        validityDepth = 0;
        state = ADDING;
    }

    void exitElementContext() {
        if (validityDepth <= 0 && !history.isEmpty()) {
            cur = history.pop();
            validityDepth = cur.validityDepth;
        }
        state = READY;
    }

    /**
     * @deprecated
     * declare the end of a prefix mapping.  This function is deprecated in 
     * favor of removePrefixMapping(), and this function does nothing.  
     */
    public void endPrefixMapping(String prefix) { }

    /**
     * unregister a prefix mapping that is now going out of scope.  Typically
     * it is not necessary to call this function; when endElement is called,
     * prefixes defined at that level will disappear.  This may be called if 
     * the mapping should be removed prior to an endElement() call.  
     */
    public synchronized void removePrefixMapping(String prefix) {
        cur.removeMapping(prefix);
    }

    /**
     * register the start of an Element.  This is called to help this object
     * keep track of where it is in the hierarchy and determine which prefixes
     * are in scope.
     */
    public synchronized void startElement() {
        if(state == ADDING) state = READY;
        validityDepth++;
    }

    /**
     * register the close of an Element.  This is called to help this object
     * keep track of where it is in the hierarchy and determine which prefixes
     * are in scope.
     * <p>
     * Note that when this method is called, all prefixes defined at this
     * element will go out of scope (and the default namespace may change).
     * Thus, if the caller is a SAX pareser doing prefix mapping, it should
     * instead call {@link #endElement(Set<String>)} to get the prefixes 
     * that are going out of scope.  
     */
    public void endElement() {
        endElement(null);
    }

    /**
     * register the close of an Element.  This is called to help this object
     * keep track of where it is in the hierarchy and determine which prefixes
     * are in scope.  This version of endElement() allows one to get a set of 
     * the prefixes going out of scope.  
     * <p>
     * Note that when this method is called, all prefixes defined at this
     * element will go out of scope (and the default namespace may change).
     * @param prefixes  a container into which the prefixes that are going 
     *                    out of scope will be added.  Note that, depending on 
     *                    how namespaces were declared, some of these prefixes 
     *                    may actually remain in scope, either with the same 
     *                    or different namespace mapping.
     */
    public void endElement(Set<String> prefixes) {
        if (prefixes != null && cur.prefixes != null) {
            prefixes.addAll(cur.prefixes);
        }
        validityDepth--;
        exitElementContext();
    }

    /**
     * create, register, and return a prefix to associate with a given 
     * namespace.
     */
    public String createMapping(String uri) {
        String prefix = "ns" + ++anoncounter;
        startPrefixMapping(prefix, uri);
        return prefix;
    }

    public String getURI(String prefix)
    {
        return cur.pre2ns.getProperty(prefix);
    }

    /**
     * return the preferred prefix for a given namespace.  
     */
    public synchronized String getPrefix(String uri) {
        String prefix = cur.ns2pre.getProperty(uri);
        if(prefix == null || !uri.equals(cur.pre2ns.getProperty(prefix)))
            return null;
        else
            return prefix;
    }

    /**
     * return an enumeration of the currently defined prefixes
     */
    public Enumeration<?> prefixes() {
        return cur.pre2ns.propertyNames();
    }

    /**
     * return (a copy of) the set of prefixes that will go out of scope
     * when endElement is called next.  Any prefixes that have been undefined
     * via {@link #} will not be included.  
     */
    public Set<String> pendingPrefixes() {
        return cur.getPrefixes();
    }

    /**
     * return an enumeration of the currently defined namespaces
     */
    public Enumeration<?> uris() {
        return cur.ns2pre.propertyNames();
    }

    /**
     * return the current default namespace
     */
    public String getDefaultNS() {
        return getURI("");
    }

    /**
     * return the current default namespace
     */
    public synchronized void setDefaultNS(String uri) {
        if(uri != null)
            startPrefixMapping("", uri);
    }

    /**
     * associated a physical location for the schema identified by the given
     * namespace.
     * @param ns   the namespace having a location
     * @param loc  the location URL
     */
    public void addLocation(String ns, String loc) {
        if(state == READY)
            setAddingState();
        cur.addLocation(ns, loc);
    }

    /**
     * register a set of namespace-location pairs.  The format of the 
     * input string is the same as an xsi:schemaLocation attribute 
     */
    public void addLocations(String schemaLocations) {
        StringTokenizer st = new StringTokenizer(schemaLocations);
        String ns = null;
        String loc = null;
        do
        {
            if(!st.hasMoreTokens())
                break;
            ns = st.nextToken();
            if(!st.hasMoreTokens())
                break;
            loc = st.nextToken();
            addLocation(ns, loc);
        } while(true);
    }

    /**
     * return the location for a given namespace or null if it is not known
     */
    public String getLocation(String uri) {
        return cur.ns2loc.getProperty(uri);
    }

    /**
     * return an enumeration of the namespaces for which a location is known
     */
    public Enumeration<?> locatedNamespaces() {
        return cur.ns2loc.propertyNames();
    }

    /**
     * create a copy of the an ancestor's Namespaces object.  This is 
     * equivalent to ancestor().
     */
    public NamespaceMap cloneAncestor(int steps)
    {
        NamespaceMap out = (NamespaceMap)clone();
        for(; steps > 0; steps--)
        {
            out.validityDepth--;
            if(out.validityDepth <= 0 && !out.history.isEmpty())
            {
                out.cur = out.history.pop();
                out.validityDepth = out.cur.validityDepth;
            }
        }

        return out;
    }

    /**
     * create an instance of a Namespaces object that is applicable to an
     * ancestor XML node.  
     * @param steps   the number of steps upward in the hierarchy to the 
     *                   desired ancestor element.  
     */
    public Namespaces ancestor(int steps) {
        return cloneAncestor(steps);
    }

    /**
     * deeply copy this object
     */
    public Object clone()  {
        NamespaceMap out = (NamespaceMap)clone();
        out.history.clear();
        for(Enumeration<Snapshot> e = history.elements(); e.hasMoreElements();)
            out.history.push( (Snapshot) e.nextElement().clone() );
        out.cur = (Snapshot) cur.clone();
        return out;
    }

    static class Snapshot implements Cloneable {

        public Properties pre2ns = null;
        public Properties ns2pre = null;
        public Properties ns2loc = null;
        public HashSet<String> prefixes = new HashSet<>();
        public int validityDepth = 0;

        public Snapshot() {
            pre2ns = new Properties();
            pre2ns.setProperty("", "");
            ns2pre = new Properties();
            ns2pre.setProperty("", "");
            ns2loc = new Properties();
        }

        public Snapshot(Snapshot parent) {
            pre2ns = new Properties(parent.pre2ns);
            ns2pre = new Properties(parent.ns2pre);
            ns2loc = new Properties(parent.ns2loc);
        }

        public Object clone() {
            Snapshot out;
            try {
                out = (Snapshot)super.clone();
                out.pre2ns = (Properties)pre2ns.clone();
                out.ns2pre = (Properties)ns2pre.clone();
                out.ns2loc = (Properties)ns2loc.clone();
                return out;
            } catch (CloneNotSupportedException ex) {
                throw new InternalError("programmer clone error");
            }
        }

        public void addMapping(String prefix, String uri) {
            if(uri == null)
                throw new NullPointerException("Null URI given");
            if(prefix == null) 
                throw new NullPointerException("Null prefix given");

            prefixes.add(prefix);
            pre2ns.setProperty(prefix, uri);
            ns2pre.setProperty(uri, prefix);
        }

        public Set<String> getPrefixes() {
            return new HashSet<>(prefixes);
        }

        public void addLocation(String namespace, String loc)
        {
            ns2loc.setProperty(namespace, loc);
        }

        public void removeMapping(String prefix)
        {
            if(prefix == null)
                throw new NullPointerException("Null prefix given");
            String uri = (String)pre2ns.remove(prefix);
            if(uri != null)
                ns2pre.remove(uri);
            if (prefixes != null) prefixes.remove(prefix);
        }

    }
}
