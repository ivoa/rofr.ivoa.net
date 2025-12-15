package org.nvo.service.validation;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;

import javax.xml.parsers.DocumentBuilder; 
import javax.xml.parsers.DocumentBuilderFactory; 
import javax.xml.parsers.ParserConfigurationException; 

import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * a tester that will parse a service response into a DOM tree and retain that
 * tree in memory so that it might be reused.  If a DOM Document is provided
 * (either at construction time or via setDocument()), applyTests() will 
 * assume that that Document contains the service response and apply the query 
 * tests to it.  Similarly, it inherits its parent class's ability to use a 
 * file as a cache of the service response: if no DOM Document is provided but
 * an existing file is, the file will be opened and parsed as if it contains 
 * the XML response from the service.  If neither a DOM nor a File is provided, 
 * applyTests() will invoke the service itself parse it into a DOM tree and 
 * apply the tests.  In this case, if a non-existant file was provided, the 
 * response will be cached to a file by that name.  
 */
public class CachingDOMTester extends CachingTester {

    protected DocumentBuilderFactory df = null;
    Document resp = null;
    XMLResponseEvaluator xmleval = null;

    /**
     * create the tester
     * @param tq   the TestQuery that will be invoked
     * @param te   the Evaluator that will be used to evaluate the results
     * @param response    a preparsed DOM document containing the service
     *                      response.  If null, one will be created from 
     *                      either the cachefile or the service directly.
     * @param cachefile   the file to use to access and/or store the result.
     *                      If null, the results will not be cached. 
     * @exception NullPointerException  if the given either of the TestQuery or 
     *              Evaluator input is null
     */
    public CachingDOMTester(TestQuery tq, Evaluator te, Document response, 
                            File cachefile) 
    {
        this(tq, te, response, cachefile, true);
    }

    /**
     * create the tester
     * @param tq   the TestQuery that will be invoked
     * @param te   the Evaluator that will be used to evaluate the results
     * @param response    a preparsed DOM document containing the service
     *                      response.  If null, one will be created from 
     *                      either the cachefile or the service directly.
     * @exception NullPointerException  if the given either of the TestQuery or 
     *              Evaluator input is null
     */
    public CachingDOMTester(TestQuery tq, Evaluator te, Document response) {
        this(tq, te, response, null, true);
    }

    /**
     * create the tester
     * @param tq   the TestQuery that will be invoked
     * @param te   the Evaluator that will be used to evaluate the results
     * @param cachefile   the file to use to access and/or store the result.
     *                      If null, the results will not be cached. 
     * @exception NullPointerException  if the given either of the TestQuery or 
     *              Evaluator input is null
     */
    public CachingDOMTester(TestQuery tq, Evaluator te, File cachefile) {
        this(tq, te, null, cachefile, true);
    }

    /**
     * create the tester.  
     * @param tq   the TestQuery that will be invoked
     * @param te   the Evaluator that will be used to evaluate the results
     * @param response        a preparsed DOM document containing the service
     *                          response.  If null, one will be created from 
     *                          either the cachefile or the service directly.
     * @param cachefile       the file to use to access and/or store the result.
     * @param ensureNonNull   if true, make sure the other inputs are non-null
     * @exception NullPointerException  if either input is null
     */
    protected CachingDOMTester(TestQuery tq, Evaluator te, Document response, 
                               File cachefile, boolean ensureNonNull) 
    {
        super(tq, te, cachefile, ensureNonNull);
        if (te instanceof XMLResponseEvaluator) 
            xmleval = (XMLResponseEvaluator) te;
        resp = response;
    }

    /**
     * apply the tests against the response from the attached TestQuery.  
     * The query will be invoke and managed by the attached Evaluator 
     * through its applyTests(TestQuery, Element) method.  
     * @param addTo   the element to append the XML-encoded results into.
     * @return int    the number of test results written into addTo.  Note
     *                  that the actual number executed may have been higher.
     * @exception ConfigurationException  if a SAX parser configuration error was
     *                  encountered
     * @exception ProcessingException  if a (SAX) XML processing exception is 
     *                  encountered.  Normally, exceptions specifically related 
     *                  to parsing (which might internally throw a 
     *                  SAXParsingException) should not occur if the 
     *                  setParsingErrorHandler() will return a non-null object.
     * @exception TestingIOException   if an IO error is encounterd while 
     *                  reading the service stream.
     * @exception InterruptedException   if an interrupt signal was sent to 
     *                to this Tester telling it to stop testing.
     */
    public int applyTests(Element addTo) 
         throws TestingException, InterruptedException 
    {
        int count = 0;

        // if we don't have an XMLEvaluator, just act like a CachingTester
        if (xmleval == null)
            return super.applyTests(addTo);

        // if we already have a DOM, applyTests to it.
        if (resp != null) 
            return xmleval.applyTests(resp, tquery, addTo);

        // there's no DOM yet, but we have a file
        InputStream is = null;
        if (cache != null) {
            synchronized (cache) {
                try {
                    if (! cache.exists()) {
                        // call the service and cache the response to the file
                        cacheResponse(cache, tquery.getTimeout());
                    }
                    is = new FileInputStream(cache);
                }
                catch (IOException ex) {
                    if (is != null) { 
                        try { is.close(); } catch (IOException e) { }
                    }
                    throw new TestingIOException(ex);
                }
            }
        }
        else {
            // there is no cache, we will read directly from the service into 
            // a DOM
            try {
                QueryConnection conn = getReadyConnection(tquery.getTimeout());
                is = conn.getStream();
            }
            catch (IOException ex) {
                if (is != null) {
                    try { is.close(); } catch (IOException e) { }
                }
                throw new TestingIOException(ex);
            }
        }

        if (df == null) df = DocumentBuilderFactory.newInstance();
        DocumentBuilder db = null;
        ParsingErrors pe = null;

        try {
            db = df.newDocumentBuilder();
            pe = xmleval.applyParsingErrorHandler(db, tquery);

            Document respdoc = db.parse(new InputSource(is));
            resp = respdoc;
        }
        catch (ParserConfigurationException ex) {
            throw new ConfigurationException(ex);
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

        if (resp != null) 
            count = xmleval.applyTests(resp, tquery, addTo);

        Document outdoc = addTo.getOwnerDocument();
        if (pe != null)
            pe.insertErrors(addTo, null, outdoc.createTextNode("\n    "));

        return count;
    }

    /**
     * get the parsed version of the service response, or null if a response
     * has not yet been parsed.
     */
    public Document getDocument() {  return resp;  }

    /**
     * provide a preparsed version of the service response.  This has the 
     * side effect of resetting the cache File to null if that file exists.
     * (This response, though, will not be cached to the file if the file 
     * doesn't exist.)
     */
    public void setDocument(Document response) {
        resp = response;
        if (cache.exists()) {
            cache.delete();
        }
    }

    /**
     * set the DocumentBuilderFactory that should be used should it be needed
     * to parse a service response.  If input is null, a default factory will 
     * be created if necessary.
     */
    public void setDocumentBuilderFactory(DocumentBuilderFactory fact) {
        df = fact;
    }

    /**
     * return the DocumentBuilderFactory that will be used should it be needed
     * to parse a service response.  A null return value indicates a default 
     * factory will be created if necessary.  A non-null return value may 
     * the default value that was previously generated internally to parse
     * a service response.  
     */
    public DocumentBuilderFactory getDocumentBuilderFactory() { return df; }

}
