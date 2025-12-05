/*
 * Created by Ray Plante for the National Virtual Observatory
 * as part of ivoaregistry (RI1 search library)
 * c. 2006
 * Adapted for ivoaharvester
 */
package net.ivoa.registry.vores;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NamedNodeMap;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedList;
import java.util.StringTokenizer;

/**
 * this class provides convenient access to information inside 
 * a DOM Node using XPath-like identifiers.  One can extract sub-Nodes, via 
 * {@link #getBlocks(String)}, which are themselves wrapped as Metadata 
 * instances.  Individual string values can be retrieved via 
 * {@link #getParameter(String)} or {@link #getParameters(String)}.  
 * <p>
 * This class assumes that all retrievable metadata are either wrapped in 
 * their own element tags or are attributes to elements.  Text appearing 
 * as mixed content are not available for retrieval.  
 */
public class Metadata {

    // the delegate node
    private final Node del;
    private final String parentPath;
    protected HashMap<String, String[]> cache = new HashMap<>();

    protected static final String XSI_NS = 
        "http://www.w3.org/2001/XMLSchema-instance";

    /**
     * wrap a DOM Node
     * @param el    the node to wrap
     */
    public Metadata(Node el) {
        del = el;
        parentPath = el.getNodeName();
    }

    /**
     * wrap a DOM element
     * @param el    the element to wrap
     * @param path  a path name to assume for the element
     */
    public Metadata(Node el, String path) {
        del = el;
        parentPath = path;
    }

    /** 
     * return the wrapped Node
     */
    public Node getDOMNode() { return del; }

    /**
     * return the pathname configured with this element
     */
    public String getPathName() { return parentPath; }

    static class MatchedBlocks extends LinkedList<Metadata> {
        public MatchedBlocks(Metadata first) {
            addLast(first);
        }

        public void appendNode(Node node, String base) { 
            addLast(new Metadata(node, base + "/" + node.getNodeName()));
        }
    }

    /**
     * return all metadata blocks that match a given path name as a List
     * @param path   the path to the desired XML node.  This is a 
     *                  slash-delimited string in which each field between 
     *                  slashes is usually an element name.  The last field 
     *                  may either be the name of an element or an attribute.  
     *                  If the last field begins with an "at" character (@), 
     *                  only a matching attribute will be returned.
     * @throws IllegalArgumentException  if any field except the last one 
     *             contains an "@" character
     */
    protected List<Metadata> findBlocks(String path) 
        throws IllegalArgumentException 
    {
        String name;
        Node node;
        int i;

        MatchedBlocks matched = new MatchedBlocks(this);
        StringTokenizer tok = new StringTokenizer(path, "/");
        while (tok.hasMoreTokens()) {
            boolean attrOnly = false;
            name = tok.nextToken();
            if (name.startsWith("@")) {
                if (tok.hasMoreTokens())
                    throw new IllegalArgumentException("Illegal path: " + path);
                attrOnly = true;
                name = name.substring(1);
            }

            int len = matched.size();
            for(i=0; i < len; i++) {
                Metadata candidate = matched.pop();
                boolean findElements = true;
                node = candidate.getDOMNode();
                if (! tok.hasMoreTokens() && 
                    node.getNodeType() == Node.ELEMENT_NODE) 
                {
                    // at the end of the path; check for matching attributes
                    // Axis BUG!
//                     Node att = (name.equals("xsi:type")) 
//                         ? ((Element) node).getAttributeNodeNS(XSI_NS, "type")
//                         : ((Element) node).getAttributeNode(name);
                    // workaround code:
                    NamedNodeMap attrs = node.getAttributes();
                    Node att = null;
                    if (attrs != null) {
                        att = (name.equals("xsi:type"))
                            ? attrs.getNamedItemNS(XSI_NS, "type")
                            : attrs.getNamedItem(name);
                    }
                    if (att != null) 
                        matched.appendNode(att, candidate.getPathName());
                    if (attrOnly) findElements = false;
                }

                if (findElements) {
                    for(node = node.getFirstChild(); 
                        node != null; 
                        node = node.getNextSibling())
                    {
                        if (node.getNodeType() == Node.ELEMENT_NODE && 
                            node.getNodeName().equals(name))
                        {
                            matched.appendNode(node, candidate.getPathName());
                        }
                    }
                }
            }
        }

        return matched;
    }

    /**
     * return all Metadata blocks that match a given path name.  Metadata
     * blocks are elements that contain other elements or attributes containing
     * metadata information.  
     * @param path   the path to the desired XML node.  This is a 
     *                  slash-delimited string in which each field between 
     *                  slashes is usually an element name.  The last field 
     *                  may either be the name of an element or an attribute.  
     *                  If the last field begins with an "at" character (@), 
     *                  only a matching attribute will be returned.
     * @throws IllegalArgumentException  if any field except the last one 
     *             contains an "@" character
     */
    public Metadata[] getBlocks(String path) 
         throws IllegalArgumentException 
    {
        return findBlocks(path).toArray(Metadata[]::new);
    }

    /**
     * return values of all parameters with a given name
     * @param path   the path to the desired parameter.  This is a 
     *                  slash-delimited string in which each field between 
     *                  slashes is usually an element name.  The last field 
     *                  may either be the name of an element or an attribute.  
     *                  If the last field begins with an "at" character (@), 
     *                  only a matching attribute will be returned.
     * @return String[]  the values found at the given path or an empty array
     *                  if not values were found
     * @throws IllegalArgumentException  if any field except the last one 
     *             contains an "@" character
     */
    public String[] getParameters(String path) {
        String[] out = cache.get(path);
        if (out != null) return out;

        List<Metadata> matched = findBlocks(path);

        LinkedList<String> values = new LinkedList<>();
        for (Metadata metadata : matched) {
            Node node = metadata.getDOMNode();
            if (node.getNodeType() == Node.ATTRIBUTE_NODE) {
                values.addLast(node.getNodeValue());
            } else if (node.getNodeType() == Node.ELEMENT_NODE) {
                String use = getElementValue(node);
                if (use != null) values.addLast(use);
            }
        }

        out = values.toArray(new String[0]);
        cache.put(path, out);

        return out;
    }

    /**
     * return the text value of an element
     */
    protected String getTrimmedElementValue(Node el) {
        String out = getElementValue(el);
        if (out != null) out = out.trim();
        return out;
    }

    /**
     * return the text value of an element
     */
    protected String getElementValue(Node el) {
        StringBuilder out = new StringBuilder();
        for (el = el.getFirstChild();
             el != null && el.getNodeType() == Node.TEXT_NODE;
             el = el.getNextSibling()) 
        { 
            out.append(el.getNodeValue());
        }
        if (el != null && el.getNodeType() == Node.ELEMENT_NODE)
            return null;

        return out.toString();
    }

    /**
     * return the first value matching the given parameter name or null
     * if the parameter is not found. 
     * @param path   the path to the desired parameter.  This is a 
     *                  slash-delimited string in which each field between 
     *                  slashes is usually an element name.  The last field 
     *                  may either be the name of an element or an attribute.  
     *                  If the last field begins with an "at" character (@), 
     *                  only a matching attribute will be returned.
     * @throws IllegalArgumentException  if any field except the last one 
     *             contains an "@" character
     */
    public String getParameter(String path) {
        String[] out = getParameters(path);
        return ((out != null && out.length > 0) ? out[0] : null);
    }

    /**
     * return the value of the xsi:type attribute if it exists.  
     * <p>
     * Note that for this to work, the XML must have parsed with namespace
     * awareness.  See 
     * {@link javax.xml.parsers.DocumentBuilderFactory#setNamespaceAware(boolean) DocumentBuilderFactory.setNamespaceAware()}.
     * @return String  the xsi:type with the namespace prefix removed
     */
    public String getXSIType() {
        if (del.getNodeType() != Node.ELEMENT_NODE) return null;
        String out = ((Element) del).getAttributeNS(XSI_NS, "type");
        int c = out.indexOf(":");
        if (c >= 0) out = out.substring(c+1);
        return out;
    }

    /**
     * clear the internal parameter cache.  Call this if the underlying 
     * DOM model has been updated.  
     */
    public void clearCache() {
        cache.clear();
    }

    /**
     * return all validation levels attached to the given metadata node.
     * This common implementation is used by VOResource and Capability
     */
    static Map<String, Integer> getValidationLevels(Metadata node) {
        final List<Metadata> vls = node.findBlocks("validationLevel");
        final Map<String, Integer> out = new HashMap<>(vls.size());

        String who;
        int level;
        for (Metadata md : vls) {
          try {
            level = Integer.parseInt(md.getTrimmedElementValue(md.getDOMNode()));
            who = md.getParameter("validatedBy");
          }
          catch (NumberFormatException | NullPointerException ex) {  continue;  }

            if (who != null)
              out.put(who, level);
        }

        return out;
    }

    /**
     * return validation levels attached to this node.
     * Null is returned if the specified registry did not set a 
     * legal value.
     * @param who   the IVOA Identifier for the registry that set the validation
     *                 level
     */
    static Integer getValidationLevelBy(Metadata node, String who) {
        if (who == null) 
            throw new NullPointerException("getValidationLevelBy(): null who");
        List<Metadata> vls = node.findBlocks("validationLevel");

        try {
            for(Metadata md : vls) {
                if (who.equals(md.getParameter("validatedBy"))) 
                  return Integer.parseInt(md.getTrimmedElementValue(md.getDOMNode()));
            }
        }
        catch (NumberFormatException | NullPointerException ex) {
            // ignore invalid entries
        }

        return null;
    }


}
