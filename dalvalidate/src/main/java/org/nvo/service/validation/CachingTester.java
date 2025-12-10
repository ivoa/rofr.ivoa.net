package org.nvo.service.validation;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import org.w3c.dom.Element;

/**
 * a tester that will save the service response to a file so that it can 
 * be reused in future testing.  Caching is enabled by passing a filename to 
 * the constructor.  If the file exists, it will be assumed to hold the 
 * response from the TestQuery, and calling applyTests() will apply the tests
 * to the contents of the file.  If the file does not exist, then applyTests()
 * will cause the service to be invoked and the response cached to the file 
 * before applying the tests.  If no filename is provided, the service will be
 * invoked, evaluated, and thrown away.
 */
public class CachingTester extends SimpleTester {

    protected final File cache;

    /**
     * create the tester
     * @param tq   the TestQuery that will be invoked
     * @param te   the Evaluator that will be used to evaluate the results
     * @param cachefile   the file to use to access and/or store the result.
     *                      If null, the results will not be cached. 
     * @exception NullPointerException  if the given either of the TestQuery or 
     *              Evaluator input is null
     */
    public CachingTester(TestQuery tq, Evaluator te, File cachefile) {
        this(tq, te, cachefile, true);
    }

    /**
     * create the tester.  
     * @param tq   the TestQuery that will be invoked
     * @param te   the Evaluator that will be used to evaluate the results
     * @param cachefile       the file to use to access and/or store the result.
     * @param ensureNonNull   if true, make sure the other inputs are non-null
     * @exception NullPointerException  if either input is null
     */
    protected CachingTester(TestQuery tq, Evaluator te, File cachefile, 
                           boolean ensureNonNull) 
    {
        super(tq, te, ensureNonNull);
        cache = cachefile;
    }

    /**
     * apply the tests against the response from the attached TestQuery.  
     * The query will be invoked and managed by the attached Evaluator
     * through its applyTests(TestQuery, Element) method.  
     * @param addTo   the element to append the XML-encoded results into.
     * @return int    the number of test results written into addTo.  Note
     *                  that the actual number executed may have been higher.
     * @exception InterruptedException   if an interrupt signal was sent to 
     *                to this tester telling it to stop testing.
     * @exception TestingException   if any other non-recoverable error is 
     *                encountered while applying tests.
     */
    public int applyTests(Element addTo) 
         throws TestingException, InterruptedException 
    {
        if (cache == null) {
            return evaluator.applyTests(tquery, addTo);
        }

        synchronized (cache) {
            try {
                if (! cache.exists()) {
                    // call the service and cache the response to the file
                    // System.err.println("Caching data");
                    cacheResponse(cache, tquery.getTimeout());
                }
                try (final InputStream is = new FileInputStream(cache)) {
                    return evaluator.applyTests(is, tquery, addTo);
                }
            }
            catch (IOException ex) {
                throw new TestingIOException(ex);
            }
        }
    }

    /**
     * invoke the query and cache the response to the given file.
     * @param out      the file to write the response to
     * @param timeout  maximum time to wait for a result.  If < 0, the 
     *                   recommended timeout time will be taken from the 
     *                   TestQuery itself.  If = 0, then the timeout time 
     *                   will be set by default to a long time (60 mins).  
     */
    public void cacheResponse(File out, long timeout) 
         throws IOException, TimeoutException, InterruptedException
    {
        QueryConnection conn = getReadyConnection(timeout);

        try (InputStream is = conn.getStream(); FileOutputStream os = new FileOutputStream(out)) {
            byte[] buf = new byte[8 * 1024];
            int n;
            while ((n = is.read(buf)) >= 0) {
                os.write(buf, 0, n);
            }
        }
    }

    /**
     * invoke the service and return a connection with a ready InputStream
     * @param timeout  maximum time to wait for a result.  If < 0, the 
     *                   recommended timeout time will be taken from the 
     *                   TestQuery itself.  If = 0, then the timeout time 
     *                   will be set by default to a long time (60 mins).  
     * @exception InterruptedException   if an interrupt signal (unrelated to
     *                the given timeout limit) was sent to this tester telling 
     *                it to stop testing, 
     */
    protected QueryConnection getReadyConnection(long timeout) 
         throws TimeoutException, InterruptedException, IOException
    {
        QueryConnection conn = tquery.invoke();
        if (timeout < 0) timeout = 600000;  // ten minutes
        if (timeout == 0) timeout = 3600000;  // not quite forever (1 hour)
        if (! conn.waitUntilReady(timeout)) {
            try {
                conn.shutdown();
            } catch (IOException ex) {  /* ignore this */ }
            throw new TimeoutException(timeout);
        }

        return conn;
    }
}
