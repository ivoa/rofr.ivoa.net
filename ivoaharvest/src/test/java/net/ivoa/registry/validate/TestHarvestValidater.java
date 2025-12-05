package net.ivoa.registry.validate;

import net.ivoa.util.Configuration;
import org.nvo.service.validation.HTTPGetTestQuery;
import org.nvo.service.validation.ResultTypes;
import org.nvo.service.validation.ValidaterListener;
import org.nvo.service.validation.ConfigurationException;

import org.w3c.dom.Document;

import java.io.File;

import javax.xml.transform.TransformerFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.dom.DOMSource;

public class TestHarvestValidater {

    /**
     * test the {@link HarvestValidater} class.
     * <p>
     * Arguments:
     * <ol>
     * <li> <i>configfile</i>  -- the configuration file (see 
     * conf/TestValidateHarvest.xml) </li>
     * <li> <i>baseURL</i> -- the base URL of the OAI interface to test </li>
     * <li> <i>cacheDir</i> -- a cache directory (optional). </li>
     * </ol>
     */
    public static void main(String[] args) {

        try {
            if (args.length < 1) 
                throw new IllegalArgumentException("missing config file");
            String configfile = args[0];

            String urlbase = null;
            if (args.length > 1) urlbase = args[1];

            Configuration config = new Configuration(configfile);
            if (urlbase == null) {
                Configuration tqconfig = 
                    HTTPGetTestQuery.getTestQueryConfig(config, "httpget");
                if (tqconfig == null) throw 
                    new ConfigurationException("No testQuery configuration " +
                                               "found");
                urlbase = HTTPGetTestQuery.getBaseURL(tqconfig, null);
            }
            if (urlbase == null) throw 
                new IllegalArgumentException("No base URL configured; " +
                                             "please provide a non-null " +
                                             "value explicitly");

            File cacheDir = null;
            if (args.length > 2) cacheDir = new File(args[2]);
        
            HarvestValidater validater = 
                new HarvestValidater(config, urlbase, cacheDir, false);
            validater.addDefaultResultTypes(ResultTypes.ADVICE);

            ValidaterListener monitor = (id, done, status) -> {
                Boolean ok = (Boolean) status.get("ok");
                if (ok == null)
                    System.err.print("?? ");
                else
                    System.err.print(ok ? "OK " : "Ack! ");

                String message = (String) status.get("message");
                System.err.println(status.get("query") + ": " + message);

                if ((Boolean) status.get("done"))
                    System.err.println("testing complete.");
            };

            Document out = validater.validate(5, monitor);

            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer printer = tf.newTransformer();
            printer.transform(new DOMSource(out), 
                              new StreamResult(System.out));
        }
        catch (Exception ex) {
            System.err.println("Error: " + ex.getMessage());
            ex.printStackTrace(System.err);
            System.exit(1);
        }
    }
}
