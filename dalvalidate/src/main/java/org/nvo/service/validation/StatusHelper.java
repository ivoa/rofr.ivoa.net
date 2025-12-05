package org.nvo.service.validation;

import java.util.HashMap;
import java.util.Map;

/**
 * a helper class for managing Validation status.
 */
public class StatusHelper {

    static long nextID = 1;
    final static String empty = "";

    /**
     * create a StatusHelper
     */
    public StatusHelper() { }

    /**
     * return a new status Hashtable container
     */
    public Map<String, Object> newStatus() {

        Integer zero = 0;
        Map<String, Object> out = new HashMap<>(10);

        out.put("id", newID());
        out.put("done", Boolean.FALSE);
        out.put("ok", Boolean.TRUE);
        out.put("message", empty);
        out.put("nextQueryName", empty);
        out.put("nextQueryDescription", empty);
        out.put("lastQueryName", empty);
        out.put("lastQueryDescription", empty);
        out.put("queryTestCount", zero);
        out.put("queryTestCount", zero);
        out.put("queryCount", zero);
        out.put("totalQueryCount", zero);
        out.put("totalTestCount", zero);

        return out;
    }

    /**
     * return a status identifier
     */
    public static String newID() { return Long.toString(nextID++); }

    /**
     * set the name and description for the next query into the given 
     * status object.  Before these are set for the next query, the 
     * previous values are moved to "lastQueryName" and "lastQueryDescription".
     * @param status    the status container to update
     * @param name      the name of the next query to be run (can be null).
     * @param desc      the description for the next query (can be null).  
     */
    public void setNextQuery(Map<String, Object> status, String name, String desc) {
        if (name == null) name = empty;
        if (desc == null) desc = empty;

        synchronized (status) {
            String oldName = (String) status.get("nextQueryName");
            if (oldName != null && !oldName.isEmpty()) {
                status.put("lastQueryName", oldName);
                String oldDesc = (String) status.get("nextQueryDescription");
                if (oldDesc == null) oldDesc = empty;
                status.put("lastQueryDescription", oldName);
            }

            status.put("nextQueryName", name);
            status.put("nextQueryDescription", desc);
        }
    }

}
 
