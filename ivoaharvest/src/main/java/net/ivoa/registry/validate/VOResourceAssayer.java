package net.ivoa.registry.validate;

import net.ivoa.registry.validate.VOResourceValidater;
import org.nvo.service.validation.ResultTypes;
import org.nvo.service.validation.TestingException;
import ncsa.xml.validation.SchemaLocation;

import java.io.Reader;
import java.io.FileReader;
import java.io.File;
import java.io.IOException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerConfigurationException;
import org.w3c.dom.Element;
import org.w3c.dom.Document;
import org.w3c.dom.DOMException;

/**
 * A class that will validate a VOResource document and return a listing (or
 * <em>assay</em>) of the results.  This provides an interface on top of
 * {@link VOResourceValidater} that is simpler to use in a programming,
 * in-memory context.  
 */
public class VOResourceAssayer {

    VOResourceValidater validater = null;
    boolean includeDoc = false;
    DocumentBuilder db = null;
    Transformer trans = null;

    public final String VALIDATE_ROOT_ELEMENT = "validate";
    public final String VALIDATE_RESULTS_ELEMENT = "VOResourceValidate";

    /**
     * create a validater that can test multiple VOResource documents
     * @param schemaLoc    the mapping of schema namespaces to files
     * @param transfact    a TransformerFactory to reuse.  May be null.
     */
    public VOResourceAssayer(SchemaLocation schemaLoc,
                             DocumentBuilderFactory dbfact,
                             TransformerFactory transfact)
    {
        try {
            if (dbfact == null) dbfact = DocumentBuilderFactory.newInstance();
            db = dbfact.newDocumentBuilder();
            if (transfact == null) transfact = TransformerFactory.newInstance();
            trans = transfact.newTransformer();
        }
        catch (ParserConfigurationException ex) {
            throw new InternalError("XML setup failure: " + ex.getMessage());
        }
        catch (TransformerConfigurationException ex) {
            throw new InternalError("XML setup failure: " + ex.getMessage());
        }

        validater = new VOResourceValidater(schemaLoc, null, null, transfact);
        validater.setResultTypes(ResultTypes.ADVICE);
        validater.setResponseRootName(VALIDATE_RESULTS_ELEMENT);
    }

    /**
     * create a validater that can test multiple VOResource documents
     * @param schemaLoc    the mapping of schema namespaces to files
     * @param transfact    a TransformerFactory to reuse.  May be null.
     */
    public VOResourceAssayer(SchemaLocation schemaLoc) {
        this(schemaLoc, null, null);
    }

    /**
     * create a validater that can test multiple VOResource documents.  Built-in
     * copies of VOResource schemas will be used to do schema validation, and
     * a built-in stylesheet (checkVOResource.xsl) will be used to apply
     * extra-schema tests.
     * @param transfact    a TransformerFactory to reuse.  May be null.
     */
    public VOResourceAssayer(DocumentBuilderFactory dbfact,
                              TransformerFactory transfact) 
    {
        this(new SchemaLocation(VOResourceValidater.class), dbfact, transfact);
    }

    /**
     * set the local schema document cache
     * @param schemaLoc   the mapping of schema namespaces to files
     */
    public void setSchemaLocation(SchemaLocation schemaLoc) {
        validater.setSchemaLocation(schemaLoc);
    }

    /**
     * return whether a reference to the parsed VOResource record should 
     * be included in a returned Assay.
     */
    public void setIncludeDoc(boolean yes) { includeDoc = yes; }


    /**
     * validate the given VOResource document and return an Assay of 
     * the results.
     */
    public Assay assess(Reader resource) 
        throws TestingException 
    {
        return assess(resource, null);
    }

    /**
     * validate the given VOResource document and return an Assay of 
     * the results.
     */
    public Assay assess(Reader resource, String docname) 
        throws TestingException 
    {
        // results document
        Document val = db.newDocument();
        val.setXmlStandalone(true);
        val.setXmlVersion("1.0");
        Element root = val.createElement(VALIDATE_ROOT_ELEMENT);
        val.appendChild(root);

        // do the validation
        int[] nt = new int[1];
        Document parsed = validater.validate(resource, root, docname, nt);

        Assay out = new Assay(val, nt[0], trans);
        if (includeDoc) out.doc = parsed;
        return out;
    }

    /**
     * validate the given VOResource document and return an Assay of 
     * the results.
     * @param resource   the VOResource document to validate
     * @throws TestingException  if the validater fails to complete the tests
     * @throws IOException   if the file is not found or otherwise cannot 
     *                        be opened
     */
    public Assay assay(File resource) 
        throws TestingException, IOException 
    {
        Reader input = null;
        try {
            input = new FileReader(resource);
            return assess(input);
        }
        finally {
            if (input != null) input.close();
        }
    }

}