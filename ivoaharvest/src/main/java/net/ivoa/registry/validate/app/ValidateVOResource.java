package net.ivoa.registry.validate.app;

import ncsa.horizon.util.CmdLine;
import ncsa.xml.validation.SchemaLocation;
import org.nvo.service.validation.TestingException;
import org.nvo.service.validation.ConfigurationException;
import org.nvo.service.validation.ProcessingException;
import org.nvo.service.validation.ResultTypes;
import net.ivoa.registry.validate.VOResourceValidater;

import java.io.FileReader;
import java.io.File;
import java.io.Writer;
import java.io.OutputStreamWriter;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.util.Enumeration;

import org.w3c.dom.Element;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException; 
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.Templates;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamSource;
import javax.xml.transform.stream.StreamResult;

/**
 * an application that will validate a VOResource record.
 */
public class ValidateVOResource {

    final VOResourceValidater validater;
    final DocumentBuilderFactory df = DocumentBuilderFactory.newInstance();
    final TransformerFactory tf = TransformerFactory.newInstance();
    String rootElementName = "ValidateVOResource";

    public static void main(String[] args) {
        CmdLine cl = new CmdLine("qvxvhS:X:t:r:f:i:");
        try {
            cl.setCmdLine(args);
        }
        catch (CmdLine.UnrecognizedOptionException ex) {
            ex.printStackTrace(System.err);
            usage(System.err);
            System.exit(2);
        }

        if (cl.isSet('h')) {
            usage(System.err);
            System.exit(0);
        }

        SchemaLocation sl = null;
        String tssheet = null, relem = null, felem = null;

        if (cl.isSet('r')) relem = cl.getValue('r');
        if (cl.isSet('f')) felem = cl.getValue('f');

        String cwd = System.getProperty("user.dir");
        if (cwd == null) 
            System.err.println("Warning: can't determine current directory!");

        if (cl.isSet('t')) {
            // stylesheet containing extra tests
            File f = new File(cwd, cl.getValue('t'));
            tssheet = f.getAbsolutePath();
        }
        if (cl.isSet('S')) {
            // schema location file specifying local cache of schema files
            File f = new File(cwd, cl.getValue('S'));
            try {
                sl = new SchemaLocation();
                sl.load(f);
            }
            catch (FileNotFoundException ex) {
                System.err.println("Schema location file not found: " + 
                                   f.toString());
                System.exit(2);
            }
            catch (IOException ex) {
                System.err.println("Trouble reading schema location file: " +
                                   ex.getMessage() + ": " + f.toString());
                System.exit(2);
            }
        }

        ValidateVOResource validater = new ValidateVOResource(sl, tssheet, relem, felem);

        Writer out = null;
        if (! cl.isSet('q')) 
            out = new OutputStreamWriter(System.out);

        if (cl.isSet('i')) {
            String code = cl.getValue('i');
            validater.setResultTypes(0);
            if (code.indexOf('p')>=0) validater.addResultTypes(ResultTypes.PASS);
            if (code.indexOf('f')>=0) validater.addResultTypes(ResultTypes.FAIL);
            if (code.indexOf('w')>=0) validater.addResultTypes(ResultTypes.WARN);
            if (code.indexOf('r')>=0) validater.addResultTypes(ResultTypes.REC);
        }
        else if (cl.isSet('v')) {
            validater.addResultTypes(ResultTypes.PASS);
        }
        else {
            validater.addResultTypes(ResultTypes.ADVICE);
        }

        String[] files = new String[cl.getNumArgs()];
        Enumeration<String> e = cl.arguments();
        for(int i=0; i < files.length && e.hasMoreElements(); i++) 
            files[i] = e.nextElement();

        try {
            if (cl.isSet('X')) {
                validater.validate(out, files, cl.getValue('X'));
            }
            else if (cl.isSet('x')) {
                validater.validate(out, files, null);
            }
            else {
                validater.validate(out, files, true);
            }
        }
        catch (Exception ex) {
            System.err.println("Failed to validate: " + ex.getMessage());
            ex.printStackTrace(System.err);
        }
    }

    public static void usage(PrintStream out) {
        out.println("validate [options] vorfile ...");
        out.println("Options:");
        out.println("  -h       print this usage (ignore all other input)");
        out.println("  -q       print nothing to standard out; only set " + 
                               "the exit code");
        out.println("  -v       verbose: report on passed tests as well " + 
                               "failed ones");
        out.println("  -i code  include the types of test results given by " +
                               "code, where");
        out.println("              p=passes, w=warnings, f=failures, " +
                                  "r=recommendations");
        out.println("              (overrides -v; default: wer).  Order is " +
                                  "not important.");
        out.println("  -x       write the result in XML format; ignored if " + 
                               "-X is set");
        out.println("  -X file  filter XML results format through " +
                               "this stylesheet.");
        out.println("  -f name  use this as the element name for the XML " +
                    "results from one file ");
        out.println("              (ignored unless -X or -x is used)");
        out.println("  -r name  use this as the element name for the XML " +
                               "results (ignored unless");
        out.println("              -X or -x is used)");
        out.println("  -t file  use this stylesheet to apply the " +
                    "extra-schema compliance tests.");
        out.println("  -S file  set the schema cache via this schema " +
                               "location file");
        out.println("Each line in a schemaLocFile gives a namespace, a space, " +
                    "and local file path.");
        out.println("The file path is the location of the Schema (.xsd) document"
                    + " for that namespace.");
    }

    /**
     * create the validater 
     * @param sl            the SchemaLocation instance locates schema files.
     *                        If null, a default one will be used.  
     * @param testsheet     the path to the stylesheet containing the extra 
     *                        VOResource tests.  If null, a default one will 
     *                        be used.
     * @param rootElemName  name to give the root element of the XML-format
     *                        results.  
     * @param fileElemName  name to give the element that contains the results
     *                        of one file.  If testsheet is provided, it must
     *                        support "resultsRootElement" as an input 
     *                        parameter for this contructor parameter to be 
     *                        honored.
     */
    public ValidateVOResource(SchemaLocation sl, String testsheet, 
                              String rootElemName, String fileElemName) 
    {
        if (sl == null) 
            sl = new SchemaLocation(VOResourceValidater.class);

        validater = new VOResourceValidater(sl, testsheet, getClass());
        if (fileElemName != null) validater.setResponseRootName(fileElemName);
        if (rootElemName != null) rootElementName = rootElemName;
    }

    /**
     * validate the given list of files and write out the results
     * @param out         the stream to write the output to
     * @param files       an array of files to validate
     * @param stylesheet  use this stylesheet to format the results.  If null,
     *                      XML format will be written.
     * @exception FileNotFoundException  if the internally configured 
     *                      stylesheet cannot be found
     */
    public boolean validate(Writer out, String[] files, String stylesheet) 
        throws TestingException, FileNotFoundException
    {
        Document results = null; 
        try {
            results = df.newDocumentBuilder().newDocument();
        } 
        catch (ParserConfigurationException ex) {
            throw new ConfigurationException("Unable to create default " + 
                                             "DocumentBuilder");
        }
        Element root = results.createElement(rootElementName);
        results.appendChild(root);
        root.appendChild(results.createTextNode("\n"));
        // results.appendChild(results.createTextNode("\n"));

        for (final String file : files) {
            try {
                validater.validate(new FileReader(file), root, file);
                root.appendChild(results.createTextNode("\n"));
            } catch (IOException ex) {
                System.err.println(file + ": Read failure: " +
                        ex.getMessage());
            } catch (TestingException ex) {
                System.err.println(file + ": Processing failure: " +
                        ex.getMessage());
            }
        }

        final Transformer printer;
        if (stylesheet != null) {
            try {
                Templates ssheet = tf.newTemplates(new StreamSource(stylesheet));
                printer = ssheet.newTransformer();
            }
            catch (TransformerConfigurationException ex) {
                throw new ProcessingException("Failure while loading  " +
                                              "stylesheet: " + ex.getMessage());
            }
        }
        else {
            try {
                printer = tf.newTransformer();
            }
            catch (TransformerConfigurationException ex) {
                throw new ConfigurationException("Failure creating  " +
                                                 "transformer: " + 
                                                 ex.getMessage());
            }
        }

        try {
            printer.transform(new DOMSource(results), new StreamResult(out));
        }
        catch (TransformerException ex) {
            throw new ProcessingException("Problem printing results: " + 
                                          ex.getMessage());
        }

        return true;
    }

    /**
     * validate the given list of files and write out the results
     * @param out         the stream to write the output to
     * @param files       an array of files to validate
     * @param asText      if true, the results will be transformed into 
     *                      plain text.  The transformation will used an
     *                      internally configured stylesheet.
     * @exception FileNotFoundException  if the internally configured 
     *                      stylesheet cannot be found
     */
    public boolean validate(Writer out, String[] files, boolean asText) 
        throws TestingException, FileNotFoundException
    {
        if (! asText) 
            return validate(out, files, null);

        // We're going to optimize text output
        String ssfile = "textFormat.xsl";
        InputStream ssheet = getClass().getResourceAsStream(ssfile);
        if (ssheet == null) 
            throw new FileNotFoundException("Can't locate stylesheet as " +
                                            "resource: " + ssfile);

        final Templates stylesheet;
        try {
            stylesheet = tf.newTemplates(new StreamSource(ssheet));
        }
        catch (TransformerConfigurationException ex) {
            throw new ConfigurationException("Failure loading testing " + 
                                             "stylesheet: " + ex.getMessage());
        }
        for (String file : files) {
            try {
                Document results = df.newDocumentBuilder().newDocument();
                Element root = results.createElement(rootElementName);
                results.appendChild(root);
                root.appendChild(results.createTextNode("\n"));
                // results.appendChild(results.createTextNode("\n"));

                validater.validate(new FileReader(file), root);
                root.appendChild(results.createTextNode("\n"));

                Transformer printer = stylesheet.newTransformer();
                printer.transform(new DOMSource(results),
                        new StreamResult(out));
                out.flush();
            } catch (ParserConfigurationException ex) {
                throw new ConfigurationException("Failure creating results " +
                        "document: " + ex.getMessage());
            } catch (TransformerConfigurationException ex) {
                throw new ConfigurationException("Failure reloading templates: "
                        + ex.getMessage());
            } catch (TransformerException ex) {
                System.err.println("Problem printing results for " + file +
                        ": " + ex.getMessage());
            } catch (FileNotFoundException ex) {
                System.err.println(file + ": file not found");
            } catch (IOException ex) {
                System.err.println(file + ": Write failure: " +
                        ex.getMessage());
            } catch (TestingException ex) {
                System.err.println(file + ": Processing failure: " +
                        ex.getMessage());
            }
        }

        return true;
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
        validater.addResultTypes(type);
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
        validater.setResultTypes(type);
    }

}