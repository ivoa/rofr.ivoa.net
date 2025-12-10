package ncsa.xml.validation;

import javax.xml.parsers.DocumentBuilder; 
import javax.xml.parsers.DocumentBuilderFactory; 

/**
 * a class of static functions for managing validation using the JAXP interface.
 * <p>
 * One of the motivations for this class is the fact that different techniques 
 * are required to do validation between Java 1.4 and Java 1.5.  
 * <p>
 * Note that Xerces JAXP implementation for Java 1.5 (and maybe 1.6) is broken
 * in that validation will only work if they are loaded in a particular order:
 * when schemas include other schemas, the inner most included schemas must 
 * loaded first.  This requirement is passed onto the SchemaLocation object.  
 */
public class ValidationUtils {
    final static String SCHEMA_VALIDATION_FEATURE_ID =
        "http://apache.org/xml/features/validation/schema";
    final static String SCHEMA_FULL_CHECKING_FEATURE_ID = 
        "http://apache.org/xml/features/validation/schema-full-checking";
    final static String EXTERNAL_SCHEMA_LOCATION = 
        "http://apache.org/xml/properties/schema/external-schemaLocation";
    final static String NAMESPACES_FEATURE_ID = 
        "http://xml.org/sax/features/namespaces";
    final static String VALIDATION_FEATURE_ID = 
        "http://xml.org/sax/features/validation";

    /**
     * turn on XML Schema validation for the given factory.  The SchemaLocation
     * object can be provided to register the location of local XML Schema 
     * (xsd) documents.  If none is provided, validation will rely on 
     * the <code>xsi:schemaLocation</code> attribute in the XML documents 
     * being validated.  
     * @param fact   the document builder factory
     * @param sl     the lookup table of XML Schema documents.  If null, none
     *                  will be registered via this method.
     */
    public static void setForXMLValidation(DocumentBuilderFactory fact,
                                           SchemaLocation sl) 
    {
        fact.setNamespaceAware(true);
        fact.setValidating(true);
        ValidationUtils.setForSchemaCacheXerces(fact, sl);
    }

    private static void setForSchemaCacheXerces(DocumentBuilderFactory df,
                                                SchemaLocation sl) 
    {
        try {
            df.setAttribute(NAMESPACES_FEATURE_ID, Boolean.TRUE);
            df.setAttribute(VALIDATION_FEATURE_ID, Boolean.TRUE);
            df.setAttribute(SCHEMA_VALIDATION_FEATURE_ID, Boolean.TRUE);
            df.setAttribute(SCHEMA_FULL_CHECKING_FEATURE_ID, Boolean.TRUE);
            if (sl != null) {
                df.setAttribute(EXTERNAL_SCHEMA_LOCATION, sl.getSchemaLocation());
            }
        }
        catch (IllegalArgumentException ex) {
          throw new RuntimeException("Configuration error: Xerces features not supported", ex);
        }
    }
}

