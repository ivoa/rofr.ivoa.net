package net.ivoa.registry.validate;

import org.nvo.service.validation.webapp.InternalServerException;
import org.nvo.service.validation.ResultTypes;
import org.nvo.service.validation.TestingException;
import org.nvo.service.validation.TestingIOException;
import net.ivoa.util.Configuration;
import ncsa.xml.validation.SchemaLocation;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.fileupload.FileUploadException;

import javax.xml.parsers.DocumentBuilder; 
import javax.xml.parsers.DocumentBuilderFactory; 
import javax.xml.parsers.ParserConfigurationException; 
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.Templates;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamSource;
import javax.xml.transform.stream.StreamResult;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletException;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Properties;
import java.util.Hashtable;
import java.util.StringTokenizer;
import java.util.Iterator;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.io.Writer;
import java.io.File;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.MalformedURLException;
import org.w3c.dom.Element;
import org.w3c.dom.Document;
import org.w3c.dom.DOMException;
import org.xml.sax.SAXException;

/**
 * a simple synchronous web app that can validate VOResource records.
 * <p>
 *
 */
public class VOResourceValidaterWebApp extends HttpServlet {

    /*
     * The replicated code in this class indicates that some refactoring 
     * would be in order.
     */

    TransformerFactory tf = null;
    Configuration config = null;
    SchemaLocation sl = null;
    String testStylesheet = null;
    String rootElemName = "ValidateVOResource";
    String fileElemName = null;
    int fileSizeThreshold = -1, maxFileSize=15000000, maxRequestSize = 20000000;
    int maxNumFiles = 10;
    File cacheHome = null;

    Properties ctypes = new Properties();
    Properties formatStylesheets = new Properties();
    final Hashtable<String, Templates> formatTemplates = new Hashtable<>();
    DocumentBuilder builder = null;
    
    /**
     * create the servlet with default configuration
     */
    public VOResourceValidaterWebApp() { 
        this(null, null);
    }

    /**
     * create the servlet
     * @param config   the configuration data for this instance.  If null, 
     *                     a default configuration will be loaded from a file 
     *                     called "config.xml" located in one of the default 
     *                     locations searched by the 
     *                     {@link net.ivoa.util.Configuration} class.  
     * @param tf       the XML TransformerFactory to use to create Transform
     *                     instances from.  If null, a new one will be created.
     */
    public VOResourceValidaterWebApp(Configuration config, 
                                     TransformerFactory tf)
    { 
        super(); 
        this.config = config;
        if (tf == null) tf = TransformerFactory.newInstance();
        this.tf = tf;

        ctypes.setProperty("text", "text/plain");
        ctypes.setProperty("html", "text/html");
        ctypes.setProperty("xml", "application/xml");
    }

    /**
     * initialize the servlet
     */
    public void init() throws ServletException {
        try {
            // create the DocumentBuilder we need for creating XML results
            builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();

            if (config == null) 
                config = new Configuration("vorconfig.xml", getClass());
        } 
        catch (ParserConfigurationException ex) {
            throw new ServletException("Trouble setting up XML handling: " +
                                       ex.getMessage(), ex);
        }
        catch (IOException ex) {
            throw new ServletException("Trouble reading servlet configuration: "
                                       + ex.getMessage(), ex);
        }
        catch (SAXException ex) {
            throw new ServletException("Trouble parsing servlet configuration: "
                                       + ex.getMessage(), ex);
        }

        // create the validator inputs from the configuration parameters
        Configuration econfig = config.getConfiguration("evaluator", "name",
                                                        "voresource");
        testStylesheet = getStylesheetFileName(econfig);
        if (testStylesheet == null) 
            throw new 
                ServletException("No stylesheet specified in configuration");
        sl = new SchemaLocation(getClass());

        // prep support for output
        loadFormatStylesheets(config);
        Configuration nconfig = config.getConfiguration("names", null, null);
        String name = nconfig.getParameter("rootElem");
        if (name != null && !name.isEmpty()) rootElemName = name;
        name = nconfig.getParameter("recordElem");
        if (name != null && !name.isEmpty()) fileElemName = name;

        // get upload limits
        String cache = config.getParameter("cacheHome");
        if (cache != null) {
            cacheHome = new File(cache);

            Configuration limitConfig =
                config.getConfiguration("limits", null, null);
            if (limitConfig != null) {
                int val = getIntParam(limitConfig, "fileSizeThreshold");
                if (val > 0) fileSizeThreshold = val;
                val = getIntParam(limitConfig, "maxFileSize");
                if (val > 0) maxFileSize = val;
                val = getIntParam(limitConfig, "maxRequestSize");
                if (val > 0) maxRequestSize = val;
                val = getIntParam(limitConfig, "maxNumFiles");
                if (val > 0) maxNumFiles = val;
            }
        }
        else {
            System.err.println("VOResourceValidaterWebApp: Warnning: upload " + 
                               "not supported");
        }
    }

    /*
     * load the stylesheet filenames configured for different formats
     */
    private void loadFormatStylesheets(Configuration config) {
        Configuration[] sheets = config.getBlocks("resultStylesheet");
        String format, sheet, empty="";
        for (Configuration configuration : sheets) {
            format = configuration.getParameter("@format");
            if (format == null || format.isEmpty()) {
                System.err.println("VOResourceValidaterWebApp: " +
                        "configuration problem ignored: " +
                        "format not specified for resultStylesheet");
                continue;
            }
            sheet = configuration.getParameter(empty);
            if (sheet != null && !sheet.isEmpty())
                formatStylesheets.put(format, sheet);
        }
    }

    /*
     * extract out the stylesheet file name from the given "evaluator"
     * configuration node.
     */
    private String getStylesheetFileName(Configuration config) {
        String empty="";
        String defaultResponseType = config.getParameter("defaultResponseType");
        if (defaultResponseType == null) defaultResponseType = empty;

        File stylesheetDir = null;
        String ssdir = config.getParameter("stylesheetDir");
        if (ssdir != null && ssdir.isEmpty()) ssdir = null;
        if (ssdir != null) 
            stylesheetDir = new File(ssdir);

        int i;
        String responseType = null, sheet = null, first = null;
        Configuration[] sheets = config.getBlocks("stylesheet");
        for(i=0; i < sheets.length; i++) {
            responseType = sheets[i].getParameter("@responseType");
            if (responseType == null) responseType = empty;
            sheet = sheets[i].getParameter(empty);
            if (sheet != null && !sheet.isEmpty()) {
                if (stylesheetDir != null) 
                    sheet = (new File(stylesheetDir, sheet)).getAbsolutePath();
                if (responseType.equals(defaultResponseType))
                    return sheet;
            }
            if (i == 0) first = sheet;
        }
        if (sheets.length > 0) return first;
        return null;
    }

    /**
     * handle a GET request.  The validation is executed synchronously, and the
     * results are returned.  
     */
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) 
         throws ServletException, IOException
    {
        Properties args = new Properties();
        String qs = req.getQueryString();
        if (qs == null) 
            throw new ServletException("Missing arguments");

        String recurls = args.getProperty("recordURL");
        if (recurls == null || recurls.isEmpty())
            throw new ServletException("missing resource record url " +
                                       "(recordURL)");

        doValidate(resp, args, null);
    }

    /**
     * handle a POST request.  This is used for uploading records directly 
     * from the user's local disk.  The validation is executed synchronously,
     * and the results are returned.  
     */
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) 
         throws ServletException, IOException
    {
        Properties args = new Properties();
        
        // Set up the cache for uploaded files (larger than 10 kB)
        
        DiskFileItemFactory factory = new DiskFileItemFactory();
        if (fileSizeThreshold > 0) 
            factory.setSizeThreshold(fileSizeThreshold);  
        factory.setRepository(cacheHome);

        // Create a new file upload handler
        ServletFileUpload upload = new ServletFileUpload(factory);
        upload.setFileSizeMax(maxFileSize);   // 15 MB, by default
        upload.setSizeMax(maxRequestSize);    // 20 MB, by default

        // Parse the request
        List<FileItem> params;
        try {
            params = upload.parseRequest(req);
        } catch (FileUploadException ex) {  throw new ServletException(ex);  }
        Iterator<FileItem> it = params.iterator();
        String name = null, val = null;
        List<FileItem> files = new ArrayList<>(2);
        while (it.hasNext()) {
            FileItem param =  it.next();

            name = param.getFieldName();
            // System.err.println("field: " + name);
            if (param.isFormField()) {
                // simple parameter
                val = param.getString();
                String old = args.getProperty(name);
                if (old != null) 
                    args.setProperty(name, old + " " + val);
                else 
                    args.setProperty(name, val);
            } 
            else if (name.equals("record")) {
                // uploaded file
                // System.err.println("Found file: " + param.getName());
                files.add(param);
            }
        }

        doValidate(resp, args, files);
    }

    /**
     * handle a request.  This is used for uploading records directly 
     * from the user's local disk.  The validation is executed synchronously,
     * and the results are returned.  
     */
    void doValidate(HttpServletResponse resp, Properties args, List<FileItem> files)
         throws ServletException, IOException
    {
        // determine output format
        String format = args.getProperty("format");
        final Transformer printer;
        try {
            printer = getTransformerForFormat(format);
        } catch (InternalServerException ex) {
            ex.printStackTrace(System.err);
            throw new ServletException("Internal Config. Error while " +
                                       "supporting requested output format");
        }
        if (printer == null) 
            throw new ServletException("Unsupported format: " + format);

        VOResourceValidater validator =
            new VOResourceValidater(sl, testStylesheet, getClass(), tf);
        if (fileElemName != null) validator.setResponseRootName(fileElemName);
        validator.setResultTypes(getResultTypes(args));

        // prep XML results container
        Document results = builder.newDocument();
        Element root = results.createElement(rootElemName);
        root.setAttribute("showStatus", 
                          args.getProperty("show", "fail warn rec"));
        results.appendChild(root);
        root.appendChild(results.createTextNode("\n"));

        // run the validation on each uploaded file (via POST)
        String docname = null;
        int max = (maxNumFiles > 0) ? maxNumFiles : 20;
        int i=0;
        if (files != null) {
          // System.err.println("checking "+files.size()+" files...");
          Iterator<FileItem> fi = files.iterator(); 
          while (fi.hasNext() && i < max) {

            FileItem file = fi.next();
            docname = file.getName().trim();
            // System.err.println("checking file: " + docname);
            if (docname.isEmpty()) continue;
            i++;
            
            try {
                InputStream is = file.getInputStream();
                validator.validate(new InputStreamReader(is), root, docname);
                is.close();
                root.appendChild(results.createTextNode("\n"));                
            }
            catch (IOException | TestingIOException ex) {
                addTestingFailure(root, docname, "comm.2",
                                  "Apparent error reading supplied URL: " +
                                  ex.getMessage());
            }
            catch (TestingException ex) {
                addTestingFailure(root, docname, "internal",
                                  "Failed to process document for apparent internal errors; contact service provider for help");
                ex.printStackTrace(System.err);
                break;
            }
          }
        }

        // run the validation on each file specified
        String recurls = args.getProperty("recordURL");
        recurls = (recurls == null) ? "" : recurls.trim();
        if ((files == null || files.isEmpty()) && recurls.isEmpty())
            throw new ServletException("No files or URLs provided to check");
        if (!recurls.isEmpty()) {
          StringTokenizer st = new StringTokenizer(recurls);
          while (st.hasMoreTokens() && i < max) {
            docname = st.nextToken();
            try {
                final URL url = URI.create(docname).toURL();

                if (Objects.equals(url.getProtocol(), "file")) {
                    addTestingFailure(root, docname, "comm.2", 
                                      "Unsupported URL protocol");
                    continue;
                }

                InputStream is = url.openStream();
                validator.validate(new InputStreamReader(is), root, docname);
                is.close();
            }
            catch (MalformedURLException ex) {
                // unable to read document due to bad URL
                addTestingFailure(root, docname, "comm.1", 
                                  "unable to access VOResource record from URL: " + 
                                  ex.getMessage());
            }
            catch (IOException | TestingIOException ex) {
                addTestingFailure(root, docname, "comm.2",
                                  "Apparent error reading supplied URL: " +
                                  ex.getMessage());
            }
            catch (TestingException ex) {
                addTestingFailure(root, docname, "internal",
                                  "Failed to process document for apparent internal errors; contact service provider for help");
                ex.printStackTrace(System.err);
                break;
            }
            root.appendChild(results.createTextNode("\n"));                
            i++;
          }
        }

        resp.setContentType(getContentTypeForFormat(format));
        Writer client = resp.getWriter();
        try {
            printer.transform(new DOMSource(results), new StreamResult(client));
        }
        catch (TransformerException ex) {
            throw new ServletException("Failure converting to output format" +
                                       ex.getMessage(), ex);
        }
        client.close();
    }

    /*
     * based on the arguments in the given Properties, return the desired
     * ResultTypes
     */
    private int getResultTypes(Properties args) {
        ResultTypes rt = new ResultTypes();
        String show = args.getProperty("show");
        if (show == null) 
            // default to ADVICE
            return ResultTypes.ADVICE;
        rt.setTypes(show);
        return rt.getTypes();
    }

    private int getIntParam(Configuration config, String name) {
        if (config == null) return -1;
        String sval = config.getParameter(name);
        if (sval == null) return -1;
        try {
            return Integer.parseInt(sval);
        }
        catch (NumberFormatException ex) {
            System.err.println("Configuration value type error: " + name + 
                               ": not an integer: " + sval);
            return -1;
        }
    }

    /**
     * return the MIME-type string to use as the content type for the given
     * output format.
     */
    protected String getContentTypeForFormat(String format) {
        return ctypes.getProperty(format);
    }

    /**
     * return a transformer (possibly cached) appropriate for converting
     * to the given format.
     */
    protected Transformer getTransformerForFormat(String format) 
         throws InternalServerException
    {
        if (format == null || format.isEmpty())
            format = (formatStylesheets.containsKey("html")) ? "html" : "xml";

        try {
            Templates stylesheet = formatTemplates.get(format);
            if (stylesheet == null) {
                String ssfile = config.getParameter(Path.of("resultStylesheet", "format",
                                                    format).toString());
                if (ssfile == null && "xml".equals(format)) ssfile = "";
                if (ssfile == null) return null;

                if (ssfile.isEmpty()) return tf.newTransformer();

                stylesheet = tf.newTemplates(
                    new StreamSource(Configuration.openFile(ssfile, 
                                                            this.getClass()))
                );
                formatTemplates.put(format, stylesheet);
            }

            return stylesheet.newTransformer();
        } 
        catch (Exception ex) {
            throw new InternalServerException(ex);
        }
    }

    /**
     * add a test result to a given XML document reporting an exception-generating problem.
     * @param root      the element to add the result node to
     * @param name  the name of the VOResource record
     * @param label     the test item name to give to the test node that 
     *                    captures the failure
     * @param message   the description of the failure
     */
    protected void addTestingFailure(Element root, String name, String label,
                                     String message)
         throws DOMException
    {
        Document doc = root.getOwnerDocument();

        Element battery = doc.createElement(fileElemName);
        battery.setAttribute("recordName", name);

        Element test = doc.createElement("test");
        test.setAttribute("item", label);
        test.setAttribute("status", "fail");

        test.appendChild(doc.createTextNode(message));
        battery.appendChild(test);
        root.appendChild(battery);
    }

    /**
     * parse the URL Get arguments into key-value pairs.  This method will 
     * look for a special argument called "runid" representing an identifier 
     * for a particular validation request; the value of this argument is 
     * returned.  If the argument is not set, null is returned.  This will
     * also set a "queryString" property which is the input querystring with
     * the runid and op parameters removed.  
     * @param querystring   the query string--everything after the ? in the 
     *                       GET URL--to parse.
     * @param out           the properties object to put the results into
     */
    public String parseArgs(String querystring, Properties out) {
        String name = null, value = null, old = null;
        String runid = null;
        int eq;
        StringTokenizer st = new StringTokenizer(querystring, "&");
        StringBuilder outqs = new StringBuilder();
        while (st.hasMoreTokens()) {
            String arg = st.nextToken();
            eq = arg.indexOf("=");
            if (eq > 0 && eq < arg.length()-1) {
                name = decode(arg.substring(0,eq).trim());
                value = decode(arg.substring(eq+1).trim());
                if (name.equals("runid")) {
                    runid = value;
                    out.setProperty(name, value);
                }
                else {
                    if (! name.equals("op")) {
                        outqs.append(name).append('=');
                        outqs.append(encode(value)).append('&');
                    }
                    old = out.getProperty(name);
                    if (old != null)
                        out.setProperty(name, old + " " + value);
                    else 
                        out.setProperty(name, value);
                }
            }
        }
        if (!outqs.isEmpty()) outqs.deleteCharAt(outqs.length()-1);
        out.setProperty("queryString", outqs.toString());

        return runid;
    }

    private static String decode(String in) {
        return URLDecoder.decode(in, StandardCharsets.UTF_8);
    }

    private static String encode(String in) {
        return URLEncoder.encode(in, StandardCharsets.UTF_8);
    }

    

}

