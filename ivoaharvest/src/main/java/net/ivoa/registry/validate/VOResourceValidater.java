package net.ivoa.registry.validate;

import ncsa.xml.validation.SchemaLocation;
import ncsa.xml.validation.ValidationUtils;

import org.nvo.service.validation.EvaluatorBase;
import org.nvo.service.validation.XSLEvaluator;
import org.nvo.service.validation.ParsingErrors;
import org.nvo.service.validation.ResultTypes;
import org.nvo.service.validation.TemplateTestQuery;
import org.nvo.service.validation.TestingException;
import org.nvo.service.validation.TestingIOException;
import org.nvo.service.validation.ProcessingException;

import javax.xml.parsers.DocumentBuilder; 
import javax.xml.parsers.DocumentBuilderFactory; 
import javax.xml.parsers.ParserConfigurationException; 
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.xml.sax.SAXParseException;
import org.xml.sax.SAXException;
import org.xml.sax.InputSource;
import org.w3c.dom.Element;
import org.w3c.dom.Document;

import java.io.Reader;
import java.io.FileReader;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.util.Date;
import java.util.TimeZone;
import java.text.SimpleDateFormat;

/**
 * a class that will validate a single VOResource document.  
 * <p>
 * This class can be instantiated once and the resulting instance used 
 * repeatedly to validate a series of VOResource documents.  This class is 
 * not thread-safe (as it keeps state while validating). 
 * <p>
 * The {@link #validate(Reader, Element) validate()} functions carry out the 
 * validation on a record available via an input Reader.  These functions 
 * record the validation results as a series of XML DOM Elements inserted 
 * as children of a given parent element.  
 * <p>
 * This class provides some control over what the XML container looks like.
 * For a more "user-friendly" interface, see also {@link VOResourceAssayer}.
 */
public class VOResourceValidater {

    protected final TransformerFactory tfact;
    protected DocumentBuilderFactory df = DocumentBuilderFactory.newInstance();
    protected SchemaLocation sl = null;
    protected final XSLEvaluator eval;
    protected VOResourceParsingErrors pe = new VOResourceParsingErrors();
    protected ResultTypes resultTypes = new ResultTypes(ResultTypes.ADVICE);

    /**
     * create a validator that can test multiple VOResource documents
     * @param schemaLoc    the mapping of schema namespaces to files
     * @param stylesheetfile   the stylesheet to use to apply the 
     *                       extra-schema tests.
     * @param resClass     the class to use to look up the location of
     *                       the stylesheet as resources.  May be null.  
     * @param transfact    a TransformerFactory to reuse.  May be null.
     */
    public VOResourceValidater(SchemaLocation schemaLoc,
                               String stylesheetfile, Class<?> resClass,
                               TransformerFactory transfact)
    {
        tfact = transfact == null ? TransformerFactory.newInstance() : transfact;
        eval = new XSLEvaluator(tfact, (resClass == null) ? getClass() : resClass);
        eval.setDefaultResponseType("");
        eval.setParsingErrorHandler(pe);

        setSchemaLocation(schemaLoc);
        if (stylesheetfile != null) stylesheetfile = "checkVOResource.xsl";
        setTestStylesheet(stylesheetfile);
    }

    /**
     * create a validator that can test multiple VOResource documents.
     * A built-in stylesheet (checkVOResource.xsl) will be used to apply
     * extra-schema tests.
     * @param schemaLoc   the mapping of schema namespaces to files
     */
    public VOResourceValidater(SchemaLocation schemaLoc) {
        this(schemaLoc, null);
    }

    /**
     * create a validator that can test multiple VOResource documents
     * @param schemaLoc    the mapping of schema namespaces to files
     * @param stylesheetfile   the stylesheet to use to apply the 
     *                       extra-schema tests.
     * @param resClass     the class to use to look up the location of
     *                       the stylesheet as resources.  May be null.  
     */
    public VOResourceValidater(SchemaLocation schemaLoc,
                               String stylesheetfile, Class<?> resClass)
    {
        this(schemaLoc, stylesheetfile, resClass, null);
    }

    /**
     * create a validator that can test multiple VOResource documents
     * @param schemaLoc   the mapping of schema namespaces to files
     * @param stylesheetfile   the stylesheet to use to apply the 
     *                       extra-schema tests.
     */
    public VOResourceValidater(SchemaLocation schemaLoc, String stylesheetfile) {
        this(schemaLoc, stylesheetfile, null, null);
    }

    /**
     * create a validator that can test multiple VOResource documents.  Built-in
     * copies of VOResource schemas will be used to do schema validation, and
     * a built-in stylesheet (checkVOResource.xsl) will be used to apply
     * extra-schema tests.
     * @param transfact    a TransformerFactory to reuse.  May be null.
     */
    public VOResourceValidater(TransformerFactory transfact) {
        this(new SchemaLocation(VOResourceValidater.class), 
             "checkVOResource.xsl", null, transfact);
    }

    /**
     * create a validater that can test multiple VOResource documents.  Built-in
     * copies of VOResource schemas will be used to do schema validation, and
     * a built-in stylesheet (checkVOResource.xsl) will be used to apply
     * extra-schema tests.
     */
    public VOResourceValidater() { this((TransformerFactory) null); }

    /**
     * set the local schema document cache
     * @param schemaLoc   the mapping of schema namespaces to files
     */
    public void setSchemaLocation(SchemaLocation schemaLoc) {
        sl = schemaLoc;
        df = DocumentBuilderFactory.newInstance();
        ValidationUtils.setForXMLValidation(df, sl);
    }

    /**
     * set the stylesheet used to apply the extra-schema tests.
     * The VOResource standard makes compliance requirements that are not
     * captured in the VOResource schema; extensions may as well.  This
     * stylesheet is used to apply the additional tests.  
     */
    public void setTestStylesheet(String stylesheetfile) {
        eval.useStylesheet("", stylesheetfile);
    }

    /**
     * include particular types of test result (FAIL, WARN, REC, or PASS, 
     * usually) by default in the validation results.  
     * @param type   the type to include OR-ed together.  These are usually 
     *                 taken from the defined constants from 
     *                 {@link org.nvo.service.validation.ResultTypes ResultType},
     *                 but it can cover user-defined values.
     */
    public void addResultTypes(int type) {
        resultTypes.addTypes(type);
    }

    /**
     * set the  particular types of test result (FAIL, WARN, REC, or PASS, 
     * usually) by default in the validation results.  
     * @param type   the types to include OR-ed together.  These are usually 
     *                 one of the defined constants from 
     *                 {@link org.nvo.service.validation.ResultTypes ResultType},
     *                 but it can cover user-defined values.
     */
    public void setResultTypes(int type) {
        resultTypes.setTypes(type);
    }

    /**
     * set the name to be used for the element containing test results.
     * @param name   the element name to use
     */
    public void setResponseRootName(String name) {
        eval.setResponseRootName(name);
    }

    /**
     * return the name to be used for the element containing test results.
     */
    public String getResponseRootName() {  return eval.getResponseRootName();  }

    /**
     * return the ParsingErrors instance used by this validater.  It will 
     * contain the XML parsing errors recorded from the last VOResource
     * record validated.  
     */
    public VOResourceParsingErrors getParsingErrors() { return pe; }

    /**
     * validate a VOResource document and append the results to a given 
     * XML parent element.
     * @param document   the input VOResource document.  This stream must
     *                      contain a complete VOResource document.  
     * @param appendTo   the DOM Element to append the validation result to 
     *                      as children.  That is, the test results will be
     *                      placed inside this element.  
     * @return int       the number of tests applied
     */
    public int validate(Reader document, Element appendTo) 
        throws TestingException
    {
        return validate(document, appendTo, null);
    }

    /**
     * validate a VOResource document and append the results to a given 
     * XML parent element.
     * @param document   the input VOResource document.  This stream must
     *                      contain a complete VOResource document.  
     * @param appendTo   the DOM Element to append the validation result to 
     *                      as children.  That is, the test results will be
     *                      placed inside this element.  
     * @param docname    the name of the input document being validated.  This
     *                      name will be noted in the validation results element
     *                      appended to the given element as the "recordName" 
     *                      attribute.  If null, the name will not be specified.
     * @return int       the number of tests applied
     */
    public int validate(Reader document, Element appendTo, String docname) 
        throws TestingException
    {
        int[] nt = new int[1];
        validate(document, appendTo, docname, nt);
        return nt[0];
    }

    /**
     * validate a VOResource document and append the results to a given 
     * XML parent element.
     * @param document   the input VOResource document.  This stream must
     *                      contain a complete VOResource document.  
     * @param appendTo   the DOM Element to append the validation result to 
     *                      as children.  That is, the test results will be
     *                      placed inside this element.  
     * @param docname    the name of the input document being validated.  This
     *                      name will be noted in the validation results element
     *                      appended to the given element as the "recordName" 
     *                      attribute.  If null, the name will not be specified.
     * @return Document  the parsed record
     */
    public Document parseAndValidate(Reader document, Element appendTo, 
                                     String docname) 
        throws TestingException
    {
        return validate(document, appendTo, docname, null);
    }

    /**
     * validate a VOResource document and append the results to a given 
     * XML parent element.
     * @param document   the input VOResource document.  This stream must
     *                      contain a complete VOResource document.  
     * @param appendTo   the DOM Element to append the validation result to 
     *                      as children.  That is, the test results will be
     *                      placed inside this element.  
     * @return Document  the parsed record
     */
    public Document parseAndValidate(Reader document, Element appendTo)
        throws TestingException
    {
        return parseAndValidate(document, appendTo, null);
    }

    /**
     * validate a VOResource document and append the results to a given 
     * XML parent element.
     * @param document   the input VOResource document.  This stream must
     *                      contain a complete VOResource document.  
     * @param appendTo   the DOM Element to append the validation result to 
     *                      as children.  That is, the test results will be
     *                      placed inside this element.  
     * @param docname    the name of the input document being validated.  This
     *                      name will be noted in the validation results element
     *                      appended to the given element as the "recordName" 
     *                      attribute.  If null, the name will not be specified.
     * @return Document  the parsed record
     */
    Document validate(Reader document, Element appendTo, String docname, int[] ntestsout)
        throws TestingException
    {
        pe.clear();
        Document out = schemaValidate(document, null);
        int nt = applyExtraTests(out, appendTo);
        if (ntestsout != null && ntestsout.length > 0) ntestsout[0] = nt;

        // get the element that applyExtraTests() added to appendTo; we 
        // will insert our parsing errors into that 
        Element added = EvaluatorBase.getLastChildElement(appendTo);
        if (added == null) added = appendTo;

        // add the parsing errors
        pe.setIncludePass((resultTypes.getTypes() & ResultTypes.PASS) > 0);
        pe.insertErrors(added, added.getFirstChild(), 
                        added.getOwnerDocument().createTextNode("\n    "));

        if (docname != null) 
            added.setAttribute("recordName", docname);
        return out;
    }

    /**
     * parse the XML document with a schema-validating parser and return 
     * the resulting DOM Document.  
     * @param document   a VOResource document 
     * @param appendTo   the DOM Element to append the validation result to 
     *                      as children.  That is, the test results will be
     *                      placed inside this element.  If null, the results
     *                      can be retrieved via the ParsingErrors object 
     *                      returned by the getParsingErrors() method.  
     * @return Document   the parsed VOResource document
     */
    public Document schemaValidate(Reader document, Element appendTo) 
        throws TestingException
    {
        DocumentBuilder db = null;
        ParsingErrors pe = null;
        Document respdoc = null;
        try {
            ValidationUtils.setForXMLValidation(df, sl);
            db = df.newDocumentBuilder();
            pe = eval.applyParsingErrorHandler(db, null);

            respdoc = db.parse(new InputSource(document));
        }
        catch (ParserConfigurationException ex) {
            // this really shouldn't happen unless there's something wrong 
            // with DocumentBuilderFactory in use.
            throw new ProcessingException(ex);
        }
        catch (SAXParseException ex) {
            // System.err.println(ex.getMessage());
            // this should have been caught by the ParsingErrors object;
            // only deal with it here if we don't have a ParsingErrors object
            if (pe == null) {
                throw new ProcessingException(ex);
            }
        }
        catch (SAXException ex) {
            throw new ProcessingException(ex);
        }
        catch (IOException ex) {
            throw new TestingIOException(ex);
        }

        if (appendTo != null && pe != null) {
           pe.insertErrors(appendTo, null, 
                           appendTo.getOwnerDocument().createTextNode("\n    "));
        }

        return respdoc;
    }

    /**
     * apply extra compliance tests that are not covered in schema validation.
     * This is done by applying the stylesheet attached to this class either 
     * at compile time or via 
     * {@link #setTestStylesheet(String) setTestStylesheet()}.  
     * @param document   the input VOResource document.  This stream must
     *                      contain a complete VOResource document.  
     * @param appendTo   the DOM Element to append the validation result to 
     *                      as children.  That is, the test results will be
     *                      placed inside this element.  
     * @return int        the number of tests applied
     */
    public int applyExtraTests(Document document, Element appendTo) 
        throws TestingException
    {
        TemplateTestQuery tq = new TemplateTestQuery(resultTypes, null);        
        tq.setEvalProperty("rightnow", rightNow());

        try {
            return eval.applyTests(document, tq, appendTo);
        }
        catch (InterruptedException ex) {
            throw new TestingException("Testing externally interrupted");
        }
    }

    private String rightNow() {
        Date now = new Date();
        TimeZone tz = TimeZone.getDefault();
        now.setTime(now.getTime() - tz.getOffset(now.getTime()));
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
        return sdf.format(now);
    }

    /**
     * validate a VOResource record.  This method is provided mainly for 
     * diagnostic purposes.  For full, convenient command-line use, use 
     * {@link net.ivoa.registry.validate.app.ValidateVOResource} instead.
     */
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("VOResourceValidater: no VOResource file given");
            System.exit(1);
        }

        TransformerFactory tFactory = TransformerFactory.newInstance();
        tFactory.setAttribute("indent-number", 2);
        VOResourceValidater validater = new VOResourceValidater(tFactory);
        validater.setResultTypes(ResultTypes.ALL);

        FileReader rdr = null;
        try {
            rdr = new FileReader(args[0]);
        } catch (FileNotFoundException ex) {
            System.err.println("VOResourceValidater: " + args[0] + 
                               ": file not found");
            System.exit(1);
        }

        try {
            final DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            final Document out = builder.newDocument();
            out.setXmlStandalone(true);
            out.setXmlVersion("1.0");
            Element root = out.createElement("vorvalidate");
            out.appendChild(root);

            validater.validate(rdr, root);

            Transformer transformer = tFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", 
                                          "2");

            DOMSource source = new DOMSource(out);
            StreamResult result = new StreamResult(System.out);
            transformer.transform(source, result); 
            System.out.println();
        }
        catch (Exception ex) {
            ex.printStackTrace(System.err);
        }
    }
}