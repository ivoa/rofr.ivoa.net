package net.ivoa.registry.validate;

import net.ivoa.util.Configuration;
import org.nvo.service.validation.SimpleIVOAServiceValidater;
import org.nvo.service.validation.HTTPGetTestQuery;
import org.nvo.service.validation.ResultTypes;
import org.nvo.service.validation.TestingException;
import org.nvo.service.validation.ConfigurationException;
import org.nvo.service.validation.ValidaterListener;
import org.nvo.service.validation.WrappedIOException;
import org.nvo.service.validation.webapp.ValidationException;
import org.nvo.service.validation.webapp.ValidationSession;
import org.nvo.service.validation.webapp.ValidationSessionBase;
import org.nvo.service.validation.webapp.InternalServerException;
import org.nvo.service.validation.webapp.BadRequestException;
import org.nvo.service.validation.webapp.UnavailableSessionException;

import java.nio.file.Path;
import java.util.Properties;
import java.util.Hashtable;
import java.util.HashMap;
import java.util.Map;
import java.util.Enumeration;
import java.util.Date;
import java.util.Vector;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.regex.Pattern;
import java.text.DateFormat;
import java.io.Writer;
import java.io.PrintWriter;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.io.File;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLEncoder;
import java.net.MalformedURLException;
import java.lang.reflect.Method;

import javax.xml.transform.TransformerFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.Templates;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import javax.xml.transform.dom.DOMSource;

import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServlet;

import org.xml.sax.SAXException;
import org.w3c.dom.Document;
import org.w3c.dom.DOMException;


/**
 * a validation session handler to validate a Registry's harvesting interface.  
 */
public class HarvestValidationSession extends ValidationSessionBase 
    implements ValidaterListener
{
    static Hashtable operations = new Hashtable();
    static Hashtable templates = new Hashtable();

    static {
        loadOpMethods(HarvestValidationSession.class, operations);
    }

    TransformerFactory tf = null;
    Configuration config = null;
    HarvestValidater validater = null;

    File cacheHome = null;
    File cacheDir = null;
    boolean cleanCache = true;

    String baseURL = null;
    String statusmsg = "{ 'status': 'unavailable', 'message': " +
        "'initializing...'}";
    Properties ctypes = new Properties();
    Vector progress = new Vector();

    /**
     * create a session
     * @exception FileNotFoundException   if config is null and the default
     *                configuration file cannot be found
     */
    public HarvestValidationSession() 
         throws IOException, SAXException
    {
        this(null, null, null);
    }

    /**
     * create a session
     * @exception FileNotFoundException   if config is null and the default
     *                configuration file cannot be found
     * @exception SAXException   if there is an XML syntax error in the default
     *                configuration file 
     */
    public HarvestValidationSession(TransformerFactory tf, Class resClass) 
         throws IOException, SAXException
    {
        this(null, tf, resClass);
    }

    /**
     * create a session
     * @exception FileNotFoundException   if config is null and the default
     *                configuration file cannot be found
     * @exception SAXException   if there is an XML syntax error in the default
     *                configuration file 
     */
    public HarvestValidationSession(Configuration config, TransformerFactory tf,
                                    Class resClass) 
         throws IOException, SAXException
    {
        super();

        if (resClass == null) resClass = getClass();
        if (config == null) config = new Configuration("config.xml", resClass);
        if (tf == null) tf = TransformerFactory.newInstance();
        this.config = config;
        this.tf = tf;

        String cache = config.getParameter("cacheHome");
        if (cache != null) {
            cacheHome = new File(cache);
            if (! cacheHome.exists())
                throw new FileNotFoundException("Cache directory: "+ cacheHome);
            if (! cacheHome.isDirectory())
                throw new FileNotFoundException("Not a directory: "+ cacheHome);
            try {
                File writetest = File.createTempFile("writetest", null, 
                                                     cacheHome);
                if (writetest.exists()) writetest.delete();
            }
            catch (IOException ex) {
                throw new WrappedIOException("Unable to write to cache " +
                                             "directory (" + cacheHome + "): "+ex.getMessage(), ex);
            }
        }
//         cleanCache = Boolean.valueOf(config.getParameter("cleanCache"));

        ctypes.setProperty("text", "text/plain");
        ctypes.setProperty("html", "text/html");
        ctypes.setProperty("xml", "application/xml");
        ctypes.setProperty("json", JSON_CONTENT_TYPE);
    }

    /**
     * lookup the method to call for the given operation name.
     */
    protected Method getOpMethod(String op) {
        return (Method) operations.get(op);
    }

    /**
     * initialize the session with an endpoint and parameters.  
     *
     * @param endpoint    the endpoint of the service to be validated
     * @param params      input parameters to the validation session.  This
     *                       can be null when none are provided.
     * @exception UnavailableSessionException  if the session is in an 
     *                       unavailable state and cannot be reset and/or 
     *                       initialized, or the inputs are incorrect or 
     *                       insufficient.
     * @return String     a unique identifier for the validation request being
     *                       handled by this ValidationSession
     */
    public String initialize(String endpoint, Properties params) 
         throws UnavailableSessionException 
    {
        if (available) end(true);

        statusmsg = "{ 'status': 'initializing', 'message': 'initializing...' }";

        // set the runid so that we can setup some cache space
        baseURL = endpoint;
        runid = newRequestID(baseURL);
        cacheDir = new File(cacheHome, runid);
        if (! cacheDir.mkdir()) {
            log("Failed to create cache directory, " + cacheDir);
            throw new UnavailableSessionException("Failed to create session " +
                                                  "directory.");
        }

        try {
            boolean builtinSchemas = false;

            if (params.getProperty("builtinSchemas") != null) {
              System.err.println("builtinSchemas set");
              builtinSchemas = true;
            }
            validater = new HarvestValidater(config, baseURL, cacheDir, builtinSchemas);
        }
        catch (ConfigurationException ex) {
            throw new UnavailableSessionException(ex);
        }
        catch (IOException ex) {
            throw new UnavailableSessionException(ex);
        }

        statusmsg = 
            "{ 'status': 'ready', 'message': 'Validater initialized.' }";
        return super.initialize(endpoint, params);
    }

    private String newRequestID(String ep) throws UnavailableSessionException {
        try {
            URL url = new URL(ep);
            String name = url.getHost();
            File dir = File.createTempFile(name, null, cacheHome);
            System.err.println("Created " + dir.getAbsolutePath() + ": " + 
                               dir.exists());
            dir.delete();
            name = dir.getName();
            return name;
        }
        catch (MalformedURLException ex) {
            throw new UnavailableSessionException(ex);
        }
        catch (IOException ex) {
            throw new UnavailableSessionException(ex);
        }
    }

    /**
     * check the current validation inputs and report any problems found 
     * into the given Properties object.  The key, when appropriate, will 
     * correspond to the name of the input having an error.
     * @param errorsFound   the Properties object to write error messages 
     *                          into.  If null, errors will not be reported
     *                          but the inputs will still be checked.
     * @return boolean   true if the inputs appear correct and false, if 
     *                      problems were found. 
     */
    public boolean checkInputs(Properties errorsFound) {
        boolean ok = true;
        if (baseURL == null || baseURL.length() == 0) {
            ok = false;
            if (errorsFound != null) 
                errorsFound.put("baseURL", "No baseURL provided");
        }
        else {
            try {
                new URL(baseURL.substring(0,baseURL.length()-1));
            }
            catch (MalformedURLException ex) {
                ok = false;
                if (errorsFound != null) 
                    errorsFound.put("baseURL", 
                                    "Bad base URL: " + ex.getMessage());
            }
        }

        return ok;
    }

    /**
     * report one or more errors to the client.  This is done by formatting
     * the given message into a response document and written into the 
     * HttpServletResponse object.  Note that this method must not require
     * that this session object be initialized in order to complete 
     * successfully.
     *
     * @param errors   a set of name error messages.  If the error is 
     *                    associated with a bad input parameter, the key 
     *                    should be the name of the parameter; otherwise,
     *                    the name is implementation specific.
     * @param format   the name of the format to be used in encoding the 
     *                    error message.  The allowed names include
     *                    "html", "xml", "text", and "json".
     * @param out      the response to write the error document out to.
     */
    public void reportErrors(Properties errors, String format, 
                             HttpServletResponse out)
         throws InternalServerException, IOException
    {
        // set the status message
        StringBuffer sb = 
            new StringBuffer("{ 'status': 'unavailable', 'message': ");
        sb.append("'unable to initialize due to problem with inputs', \n");
        sb.append("'problems': {\n");
        Enumeration e = errors.propertyNames();
        while (e.hasMoreElements()) {
            String key = (String) e.nextElement();
            sb.append("  '");
            sb.append(key);
            sb.append("': '");
            sb.append(errors.getProperty(key));
            sb.append("'\n");
        }
        sb.append("}}\n");

        statusmsg = sb.toString();

//         try {
//             throw new IllegalStateException("getting stack");
//         }
//         catch (Exception ex) {
//             ex.printStackTrace();
//         }
        
        // now report to user
        PrintWriter client = null;
        if (format == null) format = "xml";

        if (format.equals("html")) {
            out.setContentType(getContentTypeForFormat(format));
            client = out.getWriter();
            printErrorsHTML(errors, client);
        }
        else if (format.equals("json")) {
            out.setContentType(getContentTypeForFormat(format));
            client = out.getWriter();
            printErrorsJSON(errors, client);
        }
        else if (format.equals("text")) {
            out.setContentType(getContentTypeForFormat(format));
            client = out.getWriter();
            printErrorsText(errors, client);
        }
        else {
            if (! format.equals("xml")) 
                errors.setProperty("errorFormat", "unsupported error format: " +
                                   format);
            out.setContentType(getContentTypeForFormat("xml"));
            client = out.getWriter();
            printErrorsXML(errors, client);
        }
        client.close();
    }

    private void printErrorsText(Properties errors, PrintWriter out) { 
        int sz = errors.size();
        out.print("Errors found in the following input parameter");
        if (sz > 0) out.print("s");
        out.println(":");

        Enumeration e = errors.propertyNames();
        while (e.hasMoreElements()) {
            String key = (String) e.nextElement();
            out.print("  ");
            out.print(key);
            out.print(":\t");
            out.println(errors.getProperty(key));
        }
    }
    
    private void printErrorsHTML(Properties errors, PrintWriter out) { 
        out.println("<html><title>Validation Errors</title><body>");
        out.println("<div id=\"content\">");
        out.println("<h1>Errors found in input arguments:</h1><dl>");

        Enumeration e = errors.propertyNames();
        while (e.hasMoreElements()) {
            String key = (String) e.nextElement();
            out.print("  <dt> ");
            out.print(key);
            out.println(" </dt>");
            out.print("  <dd> ");
            out.print(errors.getProperty(key));
            out.println(" </dd>\n");
        }
        out.println("</dl></div>");
        out.println("</body></html>");
    }
    
    private void printErrorsXML(Properties errors, PrintWriter out) { 
        String root = "RegistryValidation";
        out.println("<?xml version=\"1.0\" encoding=\"UTF-8\" " + 
                    "standalone=\"yes\" ?>");
        out.print("<");
        out.print(root);
        out.println(">");
        out.println("  <Error on=\"input\">");
        Enumeration e = errors.propertyNames();
        while (e.hasMoreElements()) {
            String key = (String) e.nextElement();
            out.print("    <message about=\"");
            out.print(key);
            out.print("\">");
            out.print(errors.getProperty(key));
            out.println("</message>");
        }
        out.print("  </Error>\n</");
        out.print(root);
        out.println(">");
    }
    
    private void printErrorsJSON(Properties errors, PrintWriter out) { 

        // assume the status message has errors in it already
        out.println(statusmsg);

//         out.println("{ 'status': 'error', 'problems': {");
//         Enumeration e = errors.propertyNames();
//         while (e.hasMoreElements()) {
//             String key = (String) e.nextElement();
//             out.print("  '");
//             out.print(key);
//             out.print("': '");
//             out.print(errors.getProperty(key));
//             out.println("'");
//         }
//         out.println("}}");
    }
    
    /**
     * return true is the input baseURL and query inputs are legal.
     */
    public boolean isOK() { 
        return (available && checkInputs(null));
    }

    public void progressUpdated(String id, boolean isdone, Map status) {
        String key = null;
        Iterator it = status.keySet().iterator();

        StringBuffer sb = new StringBuffer("{ ");
        while (it.hasNext()) {
            try {
                if (sb.length() > 2) sb.append(", ");
                key = (String) it.next();
                sb.append('\'').append(key).append("': '");
                sb.append(status.get(key)).append('\'');
            } catch (ClassCastException ex) { }
        }
        sb.append("}");

        progress.addElement(sb.toString());
    }

    /**
     * invoke the default operation.  
     * @param params  input parameters to the operation.  This can be null 
     *                   if none are required by the operation.
     * @param out     the servlet response object to write out to
     * @exception UnavailableSessionException  if the session is in an 
     *                   unavailable state, possible corrupted.  
     * @exception InternalServerException  if the validating server encounters
     *                   an internal error (by no fault of the user).  An 
     *                   HTTP servlet would typically respond to this exception
     *                   with a 500 message.
     * @exception IOException  if an I/O problem occurs while writing the 
     *                   output.
     * @return boolean   true if the session's work can be considered complete,
     *                   allowing this object to be discarded.  
     */
    public boolean invokeDefaultOp(Properties params, HttpServletResponse out)
         throws ValidationException, IOException
    {
        doValidate(params, out);
        return done;
    }

    /**
     * if validation is underway, wait until it is completed.
     */
    public void waitForValidation() {
        try {
            validater.waitForValidation(0);
        }
        catch (InterruptedException ex) {  }
    }

    /**
     * the purpose of this operation is to receive the base URL attached to 
     * this session that should be used for future operations
     */
    public void doStartSession(Properties props, HttpServletResponse out)
         throws ValidationException, IOException
    {
        String sessionBaseURL = props.getProperty("validaterBaseURL");
        if (sessionBaseURL == null) 
            throw new InternalServerException("empty validaterBaseURL");

        StringBuffer json = new StringBuffer("{ status: 'ready', sessionURL: '");
        json.append(sessionBaseURL);
        json.append("endpoint=").append(encode(baseURL)).append('&');
        json.append("' }");

        out.setContentType(getContentTypeForFormat("json"));
        PrintWriter client = out.getWriter();
        client.println(json.toString());
        client.close();
    }

    public void doValidate(Properties props, HttpServletResponse out)
         throws ValidationException, IOException
    {
        // if false (or not specified), we will wait until the results are 
        // ready and return them; otherwise, we will cache for a later call 
        // to this method.  
        boolean cache = 
            Boolean.valueOf(props.getProperty("cache", "false")).booleanValue();

        try {
          if (cache) {
            validater.cacheValidation(this);

            String baseResultURL = props.getProperty("validaterBaseURL");

            // we want the URL seen by the user to have all of the inputs 
            // from the form.  So include the queryString, minus the cache 
            // parameter.
            String queryString = props.getProperty("queryString");
            if (queryString != null) {
                Pattern cshparm = Pattern.compile("&cache=[^&]*");
                queryString = cshparm.matcher(queryString).replaceAll("");
            }

            // tell the user where to get the results
            StringBuffer json = 
                new StringBuffer("{ status: 'done', resultURL: '");
            if (baseResultURL != null) 
                json.append(out.encodeURL(baseResultURL));
            if (queryString != null)
                json.append(queryString).append('&');
            json.append("op=Validate' }");

            out.setContentType(getContentTypeForFormat("json"));
            PrintWriter client = out.getWriter();
            client.println(json.toString());
            client.close();
          }
          else {
            String format = props.getProperty("format");
            if (format == null) format = "html";

            Transformer printer = null;
            try {
                printer = getTransformerForFormat(format);
            } catch (Exception ex) {
                throw new InternalServerException(ex);
            }
            if (printer == null) 
                throw new BadRequestException("unsupported format for " +
                                              "validation results: " + format);

            Document results = validater.validate(5, this);

            out.setContentType(getContentTypeForFormat(format));
            Writer client = out.getWriter();
            printer.transform(new DOMSource(results), new StreamResult(client));
            client.close();
          }
        } catch (Exception ex) {
            log("failed to cache validation results: " + ex);
            throw new InternalServerException(ex);
        }
    }

    public void doGetStatus(Properties props, HttpServletResponse out)
         throws ValidationException, IOException
    {
        String msg = null;
        synchronized (progress) {
            if (progress.size() > 0) {
                StringBuffer sb = new StringBuffer("[ ");
                for(Enumeration e=progress.elements(); e.hasMoreElements();) {
                    sb.append(e.nextElement());
                    if (e.hasMoreElements()) sb.append(", ");
                }
                sb.append(']');
                msg = sb.toString();
                progress.removeAllElements();
            }
            else {
                msg = "[ ]";
            }
        }

        out.setContentType(getContentTypeForFormat("json"));
        PrintWriter client = out.getWriter();
        client.println(msg);
        client.close();
    }

    public void doValidateOAI(Properties props, HttpServletResponse out)
         throws ValidationException, IOException
    {
        sendValidateResults("OAI", props, out);
    }

    public void doValidateIVOA(Properties props, HttpServletResponse out)
         throws ValidationException, IOException
    {
        sendValidateResults("IVOA", props, out);
    }

    public void doValidateVOR(Properties props, HttpServletResponse out)
         throws ValidationException, IOException
    {
        sendValidateResults("VOR", props, out);
    }

    public void doGetResource(Properties props, HttpServletResponse out)
         throws ValidationException, IOException
    {
        String format = props.getProperty("format");
        if (format == null) format = "xml";

        String resource = props.getProperty("id");
        if (resource == null) {
            reportError("resource", "No resource id provided for request",
                        format, out);
            return;
        }
        sendCachedFile(resource, "voresources", format, out);
    }

    public void doValidateResource(Properties props, HttpServletResponse out)
         throws ValidationException, IOException
    {
        String resource = props.getProperty("id");
        if (resource == null) {
            reportError("resource", "No resource id provided for request",
                        props.getProperty("format"), out);
            return;
        }
        sendValidateResults(resource, props, out);
    }

    public void doRegister(Properties props, HttpServletResponse out)
         throws ValidationException, IOException
    {
        String registerurl = config.getParameter("registerURL");
        if (registerurl == null) 
            throw new InternalServerException("registerURL not configured");
        String runid = cacheDir.getName();
        URL url = new URL(registerurl + runid);
        out.setContentType(getContentTypeForFormat("xml"));
        Reader rdr = new InputStreamReader(url.openStream());
        Writer wtr = out.getWriter();

        char[] buf = new char[16*1024];
        int n = 0;
        while ((n = rdr.read(buf)) >= 0) {
            wtr.write(buf, 0, n);
        }
        rdr.close();
        wtr.close();
    }

    public void doCancel(Properties props, HttpServletResponse out)
         throws ValidationException, IOException
    {
        end(false);

        StringBuffer sb = new StringBuffer("{ 'status': ");
        if (! available) {
            sb.append("'unavailable'");
        }
        else if (validater.isValidating()) {
            sb.append("'running'");
        }
        else {
            sb.append("'done'");
        }
        sb.append(", 'message': 'validation interrupt message sent' }");

        out.setContentType(getContentTypeForFormat("json"));
        PrintWriter client = out.getWriter();
        client.println(sb.toString());
        client.close();
    }

    /**
     * write a message to standard error so that it will get recorded in the
     * servlet server's log
     */
    public void log(String message) {
        StringBuffer sb = 
            new StringBuffer(DateFormat.getDateInstance().format(new Date()));
        sb.append(": ").append(serviceType).append(" baseURL=").append(baseURL);
        sb.append("\n  ").append(message);
        System.err.println(sb.toString());
    }

    public void end(boolean asap) {
        validater.interrupt();
        super.end(asap);

        // reset our results
        done = false;
        progress.removeAllElements();

        if (cleanCache) {
            try {
                removeCacheDir(cacheDir);
            }
            catch (IOException ex) {
                log("Failed to remove cache directory, " + cacheDir +
                    ": " + ex.getMessage());
            }
        }
    }

    public static void removeCacheDir(File dir) throws IOException {
        if (! dir.exists()) return;
        if (! dir.isDirectory())
            throw new FileNotFoundException(dir.toString() + 
                                            ": not a directory");

        File[] contents = dir.listFiles();
        for(int i=0; i < contents.length; i++) {
            if (contents[i].getName().equals(".") || 
                contents[i].getName().equals("..")  ) continue;
            if (contents[i].isDirectory()) removeCacheDir(contents[i]);
            contents[i].delete();
        }
    }

    private void sendValidateResults(String phase, Properties props, 
                                     HttpServletResponse out)
        throws ValidationException
    {
        String format = props.getProperty("format");
        if (format == null) format = "html";

        Transformer printer = null;
        try {
            printer = getTransformerForFormat(format);
        } catch (Exception ex) {
            throw new InternalServerException(ex);
        }
        if (printer == null) 
            throw new BadRequestException("unsupported format for " +
                                          "validation results: " + format);

        try {
            Document results = null;
            if (phase.equals("OAI")) {
                results = validater.validateOAI(null, null);
            }
            else if (phase.equals("IVOA")) {
                results = validater.validateIVOAHarvest(null, null);
            }
            else if (phase.equals("VOR")) {
                results = validater.validateVOResources(null, null, 5);
            }
            else {
                sendCachedFile(phase, "vorvalidated", format, out);
                return;
            }

            if (results == null) {
                reportError("resource", "Requested resource records, " + phase +
                            ", not available", props.getProperty("format"), out);
                return;
            }

            out.setContentType(getContentTypeForFormat(format));
            Writer client = out.getWriter();
            printer.transform(new DOMSource(results), new StreamResult(client));
            client.close();
        }
        catch (Exception ex) {
            log("failed to send requested results: " + ex.getMessage());
            throw new InternalServerException(ex);
        }
    }

    void sendCachedFile(String id, String subdir, String format, 
                        HttpServletResponse out)
         throws ValidationException, IOException
    {
        String filename = validater.idToFilename(id);
        File vorrec = new File(new File(cacheDir,subdir), filename);
        if (! vorrec.exists()) {
            reportError("resource", "Resource is not yet available",
                        format, out);
            return;
        }

        try {
            Reader vor = new FileReader(vorrec);
            out.setContentType(getContentTypeForFormat(format));
            Writer client = out.getWriter();
            if (! "xml".equals(format)) {
                Transformer printer = null;
                try {
                    printer = getTransformerForFormat(format);
                } catch (Exception ex) {
                    throw new InternalServerException(ex);
                }
                if (printer == null) 
                    throw new BadRequestException("unsupported format for " +
                                                  "validation results: " + 
                                                  format);

                printer.transform(new StreamSource(vor), 
                                  new StreamResult(client));
            }
            else {
                char[] buffer = new char[16*1024];
                int n = 0;
                while ((n = vor.read(buffer)) >= 0) {
                    client.write(buffer, 0, n);
                }
            }
            vor.close();
            client.close();
        } 
        catch (TransformerException ex) {
            log("failed to transform a record: " + ex.getMessage());
            throw new InternalServerException(ex);
        }
    }

    private void reportError(String label, String message, String format, 
                             HttpServletResponse error) 
        throws InternalServerException, IOException
    {
        Properties errors = new Properties();
        errors.setProperty(label, message);
        reportErrors(errors, format, error);
        return;
    }

    private String encode(String in) {
        try {
            return URLEncoder.encode(in, "UTF-8");
        }
        catch (UnsupportedEncodingException ex) { return in; }
    }

    protected String getContentTypeForFormat(String format) {
        return ctypes.getProperty(format);
    }

    protected Transformer getTransformerForFormat(String format) 
         throws IOException, TransformerConfigurationException
    {
        if (format != null && format.isEmpty()) format = null;
        if (format == null) return null;

        Templates stylesheet = (Templates) templates.get(format);
        if (stylesheet == null) {
            String ssfile = config.getParameter(Path.of("resultStylesheet", "format", format).toString());
            if (ssfile == null && "xml".equals(format)) ssfile = "";
            if (ssfile == null) return null;

            if (ssfile.isEmpty()) return tf.newTransformer();

            stylesheet = tf.newTemplates(
               new StreamSource(Configuration.openFile(ssfile, this.getClass()))
            );
        }
            
        return stylesheet.newTransformer();
    }


}
