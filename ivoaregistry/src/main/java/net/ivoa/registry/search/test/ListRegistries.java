/*
 * Created by Ray Plante for the National Virtual Observatory
 * and the International Virtual Observatory Alliance
 */
package net.ivoa.registry.search.test;

import java.io.OutputStream;
import java.io.StringReader;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.Objects;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import jakarta.xml.soap.SOAPException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import net.ivoa.registry.RegistryCommException;
import net.ivoa.registry.RegistryServiceException;
import net.ivoa.registry.search.ParseException;
import net.ivoa.registry.search.SOAPSearchClient;
import net.ivoa.registry.search.Where2DOM;

/**
 * a Java application that will list known registries by querying a 
 * searchable registry.
 */
public class ListRegistries {
    public URL endpoint;

    /**
     * construct the application to use the registry at the given URL service
     * endpoint.  
     */
    public ListRegistries(URL regEndpoint) {
        endpoint = regEndpoint;
    }

    Element createWhere() {
        String adqlwhere = "where @xsi:type like '%:Registry'";
        Where2DOM p = new Where2DOM(new StringReader(adqlwhere));
        try {
            return p.parseWhere();
        }
        catch (ParseException ex) {
            throw new InternalError("Failed to parse query: " + ex.getMessage());
        }
    }

    /**
     * return the list of registries.
     *
     * This function will submit the query and return the list as an XML 
     * element (named VOResources) whose children are the XML/VOResource 
     * descriptions of each of the found registries.  
     * 
     * @throws RegistryServiceException  if this client fails to connect to the
     *     registry service
     * @throws RegistryCommException     if the server fails to return a valid 
     *     response
     */
    public Element getList() 
        throws RegistryServiceException, RegistryCommException 
    {
        try {
            NodeList reslist;

            SOAPSearchClient client = new SOAPSearchClient(endpoint);
            Element root = client.search(createWhere(), 0, 50, false);

            if (root.getLocalName() != "VOResources" && 
                (reslist = root.getElementsByTagName("VOResources")).getLength() > 0) 
              root = (Element) reslist.item(0);
            return root;
        }
        catch (SOAPException | DOMException ex) {
            throw new RegistryCommException(ex);
        }
    }

    /**
     * Print the list of resources out to a given output stream in XML format.  
     *
     * The query is sent to the searchable registry (via getList()) and converted
     * XML text.  
     */
    public void printList(OutputStream out) 
        throws RegistryServiceException, RegistryCommException, 
               TransformerException 
    {
        try {
            Document resdoc = 
                DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
            Element list;

            try {
                list = (Element) resdoc.importNode(this.getList(), true);
            }
            catch (DOMException ex) {
                throw new InternalError("DOM import error: " + ex.getMessage());
            }
            resdoc.appendChild(list);

            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer printer = tf.newTransformer();
            printer.transform(new DOMSource(resdoc), new StreamResult(out));
        }
        catch (ParserConfigurationException ex) {
            throw new InternalError("DOM config error: " + ex.getMessage());
        }
    }

    /**
     * Launch this application: query a searchable registry for other known
     * registries and print the returned list of registry descriptions to 
     * standard out.  
     */
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Registry endpoint URL required");
            System.err.println("Usage: listregistries URL");
            System.exit(1);
        }

        URL endpoint;
        try {
            endpoint = URI.create(args[0]).toURL();
        }
        catch (MalformedURLException ex) {
            System.err.println("Bad URL: " + args[0] + "(" + 
                               ex.getMessage() + ")");
            System.exit(2);
            endpoint = null;
        }

        ListRegistries lr = new ListRegistries(endpoint);
        try {
            lr.printList(System.out);
        }
        catch (RegistryServiceException ex) {
            System.err.println("Registry server failure: " + ex.getMessage());
            System.exit(1);
        }
        catch (RegistryCommException ex) {
            System.err.println("Search communication error: " + ex.getMessage());
            Exception wrapped = ex.getTargetException();
            Objects.requireNonNullElse(wrapped, ex).printStackTrace(System.err);
            System.exit(1);
        }
        catch (Exception ex) {
            System.err.println("Search failed: " + ex.getMessage());
            ex.printStackTrace(System.err);
            System.exit(1);
        }
        System.exit(0);
    }
}
