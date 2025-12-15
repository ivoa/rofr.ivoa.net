/*
 * NCSA Horizon Image Browser
 * Project Horizon
 * National Center for Supercomputing Applications
 * University of Illinois at Urbana-Champaign
 * 605 E. Springfield, Champaign IL 61820
 * horizon@ncsa.uiuc.edu
 *
 * Copyright (C) 1996-7, Board of Trustees of the University of Illinois
 *
 * NCSA Horizon software, both binary and source (hereafter, Software) is
 * copyrighted by The Board of Trustees of the University of Illinois
 * (UI), and ownership remains with the UI.
 *
 * You should have received a full statement of copyright and
 * conditions for use with this package; if not, a copy may be
 * obtained from the above address.  Please see this statement
 * for more details.
 *
 */
package ncsa.horizon.util;

import java.util.*;
import java.util.logging.Logger;

/**
 * a command line processor that parses out UNIX-like options.  <p>
 * 
 * An option is specified on the command line by a single letter preceeded 
 * by a dash.  Several options may be specified in a single argument that 
 * is preceeded by a single dash.  Option arguments follow the option 
 * (assuming the option was configured to take an argument) with or without 
 * a space.  This class can be used to process command line options supported
 * by a Java application.  <p>
 * 
 * The class is usually configured to for a set of supported switches at 
 * construction.  The configuration is given in the form of a string (using
 * a syntax similar to the Perl package Getopts.pl) in which each letter
 * in the string is a command line switch supported by the application.  A
 * character followed by a colon (:) indicates that the switch takes an 
 * argument.  A character followed by a dash (-) indicates that the option
 * is to be interpreted as a processing-stop switch; all characters beyond 
 * this switch (in that word and in all those after it) are taken to make up
 * literal arguments and not additional switches.  This allows one to provide
 * literal arguments that begin with a dash.  <p>
 *
 * One can pass the actual argument list to this class either via its 
 * constructor or after instantiation via the setCmdLine(String[]) method,
 * which examines the arguments, seperating out the switches from the literal
 * arguments.  One can then check which options were set with the isSet(),
 * getValue(), or the options().  One can also enumerate through 
 * the arguments in the order they appeared on the command line via the 
 * arguments() method.  <p>
 *
 * For example, suppose you would like to support the following command
 * line:
 *
 * <pre>
 *     java MyApplication -qu -p -f afile.txt -x -Hello- world
 * </pre>
 * 
 * You intend this command line to support 4 basic switches, one of which
 * takes an argument, plus the -x option as a stop switch.  The stop switch
 * will prevent the "-Hello-" from being interpreted as a string of options.
 * You can configure your CmdLine object to support this line in this way:
 * 
 * <pre>
 *     CmdLine cl = new CmdLine("f:puqx-", (CmdLine.RELAX & CmdLine.USRWARN));
 * </pre>
 *
 * Note that it doesn't matter what order the options are list in the 
 * command line.  The <code>RELAX</code> flag tells the class not to 
 * throw an exception if an unrecognized option is encountered, while the
 * the <code>USRWARN</code> flag says to inform the user of such errors
 * via a message sent to <code>System.err</code>.  One can then process the 
 * command line with the following code (where args is the String[] passed
 * to main()):
 *
 * <pre>
 *     // parse the command line
 *     try {
 *	   cl.setCmdLine(args); 
 *     } catch (CmdLine.UnrecognizedOptionException ex) {
 *         // this exception won't be thrown if RELAX is given as a flag;
 *         // however, if it isn't given, you can put your bail-out code
 *         // here
 *     }
 *
 *     // check for options
 *     if (cl.isSet('q')) ...
 *     if (cl.isSet('f')) filearg = cl.getValue('f');
 *     ...
 *
 *     // get arguments
 *     String arg;
 *     for(Enumeration e = cl.arguments(); e.hasMoreElements();) {
 *         arg = (String) e.nextElement();
 *         ...
 *     }
 * </pre>
 *
 * @author Raymond L. Plante
 * @author Horizon team, University of Illinois at Urbana-Champaign
 * @version $Id: CmdLine.java,v 1.1 2005/08/16 16:18:38 rplante Exp $
 */
public class CmdLine {
    private static final Logger LOGGER = Logger.getLogger(CmdLine.class.getName());

    /**
     * the configuration string.
     */
    protected String config = null;

    /**
     * the list of options
     */
    protected final Hashtable<Character, Object> options = new Hashtable<>();

    /**
     * the list of normal arguments
     */
    protected final Vector<String> argList = new Vector<>();

    /**
     * flags that control reaction to arguments
     */
    protected int flags = 0;

    /**
     * the option letter that stops option processing
     */
    protected Character stopchar = null;

    /**
     * the null flag
     */
    public final static int NULLFLAG = 0;

    /**
     * do not throw UnrecognizedOptionException; instead just ignore the 
     * unrecognized options
     */
    public final static int RELAX = 1;

    /**
     * print a warning message to System.err if user specifies an 
     * unrecognized option (when RELAX is also set) or when an option 
     * parameter appears to be missing from the end of the command line.
     */
    public final static int USRWARN = 2;

    /**
     * print various warning messages when unexpected conditions are 
     * encountered, including any during configuration.  This flag also 
     * sets the USRWARN flag;
     */
    public final static int WARN = 6;

    /**
     * construct using a given configuration, flags, and input command line
     */
    public CmdLine(String configuration, int flags, String[] args) throws UnrecognizedOptionException
    {
        this(configuration, flags);

        // process the command line
        if (args != null) setCmdLine(args);
    }

    public CmdLine(String configuration, int flags) {

        // set flags
        this.flags = flags;

        // set and parse config string
        setConfig(configuration);
    }

    public CmdLine(String configuration) { this(configuration, 0); }
    public CmdLine(String configuration, String[] args) throws UnrecognizedOptionException
    {
        this(configuration, 0, args);
    }

    /**
     * set and parse the configuration string
     */
    public synchronized void setConfig(String configuration) {
        if (config != null && (flags&WARN) > 0) 
          System.err.println("Warning: Resetting Command Line configuration");

        config = configuration;
        options.clear();

        // parse the config string
        char[] conchr = config.toCharArray();

        LOGGER.info("Resetting Command Line configuration to '" + Arrays.toString(conchr) + "'");

        Object state;
        final int zero = 0;

        for(int i=0; i < conchr.length; i++) {
            final char chr = conchr[i];
            if (! Character.isLetterOrDigit(chr) && (flags&WARN) > 0) {
                System.err.println("Warning: ignoring non-letter in " +
                                   "configuration string: " + config + 
                                   " ('" + chr + "' at pos. " + i +
                                   ").");
                continue;
            }

            state = null;

            if (options.containsKey(chr) && (flags&WARN) > 0) {
                LOGGER.warning("Warning: option redefined: " + chr);
            }

            if (i+1 < conchr.length) { 
                if (conchr[i+1] == '-') {
                    stopchar = chr;
                    i++;
                }
                else if (conchr[i+1] == ':') {
                    state = new Stack<>();
                    i++;
                }
            }
            if (state == null) {
                state = zero;
            }

            options.put(chr, state);
        }

        LOGGER.info("Reset options to '" + options.entrySet() + "'");
    }

    /**
     * parse the command line given as an array of Strings
     */
    public synchronized void setCmdLine(String[] args) 
        throws UnrecognizedOptionException 
    {

        if (args == null) return;
        boolean more = true;
        StringBuilder unrec = new StringBuilder();
        char[] argary = null;
        Character opt = null;
        Object state = null;

        for(int i=0; i < args.length; i++) {

            if (args[i] == null) continue;
            if (more && ! args[i].equals("-") && args[i].startsWith("-")) {

                // we have a list of options
                argary = args[i].toCharArray();
                for(int j=1; j < argary.length; j++) {
                    opt = argary[j];

                    // do we recognize it?
                    if (! options.containsKey(opt)) {
                        if ((flags&RELAX) == 0) 
                            throw new UnrecognizedOptionException(opt);
                        if ((flags&USRWARN) > 0) {
                            String told = unrec.toString();
                            if (told.indexOf(argary[j]) < 0) 
                                System.err.println("Warning: unrecognized " +
                                                   "option: -" + opt +
                                                   "; ignoring");
                        }
                        unrec.append(opt);
                        continue;
                    }

                    // we recognize it, so process it
                    state = options.get(opt);
                    if (state instanceof Integer) {

                        // it is a boolean switch; keep a count of number 
                        // of times it has been set in this command line
                        state =  ((Integer)state) + 1;
                        options.put(opt, state);
                    }
                    else if (state instanceof Stack) {

                        // it is a parameter switch; add argument to stack
                        // of parameters for this switch
                        Stack<String> list = (Stack<String>) state;
                        if (j+1 < argary.length) {

                            // the parameter is in this argument
                            list.push(args[i].substring(j+1));
                            j = argary.length;
                        }
                        else { 

                            // it's in the next argument
                            if (i+1 < args.length) {
                                list.push(args[++i]);
                            } else if ((flags&USRWARN) > 0) {
                                System.err.println(
                                    "Warning: missing argument to -" + opt +
                                    " option; ignoring.");
                            }
                        }
                    }

                    if (opt.equals(stopchar)) more = false;
                }
            }
            else {
                argList.addElement(args[i]);
            }
        }
    }

    /**
     * return the configuration string
     */
    public synchronized String getConfig() { return config; }

    /**
     * set the flags
     */
    public void setFlags(int flags) { this.flags = flags; }

    /**
     * merge the flags with our current flags
     */
    public void addFlags(int flags) { this.flags |= flags; }

    /**
     * return the current flag settings 
     */
    public int getFlags() { return flags; }

    /**
     * return true if the option given by the input character has been
     * set.
     */
    public boolean isSet(char c) {  return (getNumSet(c) > 0); }

    /**
     * return true if the input is recognized as a configured option
     */
    public boolean isAnOption(char c) {
        return (getNumSet(c) >= 0);
    }

    /** 
     * return true if the input is a switched option
     */
    public synchronized boolean isSwitched(char c) {  
        Object state = options.get(c);

        if (state == null) return false;

        return (state instanceof Integer);
    }

    /**
     * return the number of times the option was set or specified.  This 
     * works for both switches and parameters.  If the input is not
     * configured option, this method returns -1.
     */
    public synchronized int getNumSet(char c) {
        Object state = options.get(c);

        return switch (state) {
            case Integer i -> i;
            case Stack<?> stack -> stack.size();
            case null, default -> -1;
        };

    }

    /**
     * return the String value of the option if the option is set, or null
     * if it is not.  If the value was set multiple times, return the last
     * value it was set to.  If the option is a switch (i.e. does not take an
     * argument), the string returned will be the value of Boolean.toString()
     * (i.e. "true" or "false").  
     */
    public synchronized String getValue(char c) {
        Object state = options.get(c);

        switch (state) {
            case null -> {
                return null;
            }
            case Integer i -> {
                boolean yes = i > 0;
                return Boolean.toString(yes);
            }
            case Stack<?> vals -> {
                return ((!vals.isEmpty()) ? (String) vals.lastElement() : null);
            }
            default -> {
            }
        }

        return null;
    }

    /**
     * return a Stack of the String values associated with the requested
     * option.  The Stack will be empty if the option was not set.  If the
     * requested option is a switched option that is set, the Stack will
     * contain the number of elements equal to the number of times the
     * option in the stack with each element being the same string: "true"
     * (admittedly not useful, but nevertheless symmetric).
     */
    public synchronized Stack<String> getAllValues(char c) {
        Object state = options.get(c);

        if (state == null) return new Stack<>();

        Stack<String> out = new Stack<>();
        if (state instanceof Integer) {
            int n = ((Integer)state);
            out.ensureCapacity(n);
            for(int i=0; i < n; i++) out.push(Boolean.toString(true));
        }
        else if (state instanceof Stack) {
            return ((Stack<String>) ((Stack<String>) state).clone());
        }

        return new Stack<>();
    }

    /**
     * return the options that this object is configured to look for 
     * in the form of an Enumeration.  Each element is a Character type. 
     */
    public synchronized Enumeration<Character> options() {
        return options.keys();
    }

    /**
     * return the arguments found in this command line in the form of 
     * an Enumeration
     * @throws IllegalStateException  if class has not yet been initialized 
     * with a set of arguments yet (either via constructor or setCmdLine()).
     */
    public synchronized Enumeration<String> arguments() {
        return argList.elements();
    }

    /**
     * return the number of arguments found in this argument list 
     * @throws IllegalStateException  if class has not yet been initialized 
     * with a set of arguments yet (either via constructor or setCmdLine()).
     */
    public synchronized int getNumArgs() { 
        return argList.size();
    }

    /**
     * break a String containing a list of items into an array of 
     * Strings
     * @param input  the string to break up
     * @param delim  the string containing delimiter characters; same
     *               as the delimiter string given to the StringTokenizer
     *               constructor
     */
    public static String[] parseStringList(String input, String delim) {
        StringTokenizer t = new StringTokenizer(input, delim);
        Vector<String> parts = new Vector<>(t.countTokens());
        while (t.hasMoreTokens()) {
            parts.addElement(t.nextToken());
        }

        String[] out = new String[parts.size()];
        parts.copyInto(out);
        return out;
    }

    static public void main(String[] args) {
        if (args.length == 0) return;

        String[] use = new String[args.length - 1];
        if (use.length > 0) 
            System.arraycopy(args, 1, use, 0, use.length);

        // First determine if the -X flag is set indicating that a 
        // bad option should throw an exception.
        CmdLine cl = null;
        int flags = RELAX;
        try {
            cl = new CmdLine("XS", flags, use);
        } 
        catch (UnrecognizedOptionException e) {
            // should not happen with RELAX flag
            throw new InternalError();
        }

        // reset the flags according to the users wishes
        flags = 0;
        if (! cl.isSet('X')) flags = RELAX;
        if (! cl.isSet('S')) flags |= WARN;
        
        // now reconfigure our command line processor, using the user's 
        // configuration.  In this example, the first argument is always 
        // the configuration.
        cl.setFlags(flags);
        cl.setConfig("XS" + args[0]);
        try {
            cl.setCmdLine(use);
        } 
        catch (UnrecognizedOptionException ex) {
            System.err.println("Error: " + ex.getMessage());
            return;
        }

        String type = null, switched = "switch", parm = "parameter", id = null;
        Character o = null;
        Enumeration<Character> e=cl.options();
        while(e.hasMoreElements()) {
            o = e.nextElement();
            type = (cl.isSwitched(o)) ? switched : parm;
            id = "-" + o + " (" + type + "): ";
            
            if (! cl.isSet(o)) {
                System.out.println(id + "not set");
            }
            else {
                if (cl.isSwitched(o)) {
                    int n = cl.getNumSet(o);
                    System.out.println(id + "set " + n + " times");
                }
                else {
                    Stack<String> parms = cl.getAllValues(o);
                    int n = parms.size();
                    StringBuffer line = new StringBuffer(id + n + " arguments");
                    for(; n > 0; n--) {
                        String item = parms.pop();
                        line.append("\n  \"").append(item).append("\"");
                        if (item.indexOf(',') >= 0) {
                            String[] list = parseStringList(item, ", ");
                            line.append(" => ");
                            if (list.length > 0) 
                                line.append("\"").append(list[0]).append("\"");
                            for(int i=1; i<list.length; i++)
                                line.append(", \"").append(list[i]).append("\"");
                        }
                    }
                    System.out.println(line);
                }
            }
        }

        System.out.println("Non-option arguments (" + cl.getNumArgs() + "): ");
        while (cl.arguments().hasMoreElements()) {
            System.out.println("    \"" + e.nextElement() + "\"");
        }
    }

    /**
     * an Exception that can be thrown if an unrecognized option has been
     * encountered.  See the CmdLine class for details.
     */
    public static class UnrecognizedOptionException extends Exception {

        private Character c;

        /**
         * create an exception indicating that an unrecognized option
         * was encountered
         */
        public UnrecognizedOptionException() { super(); }

        /**
         * create an exception indicating that an unrecognized option
         * was encountered
         */
        public UnrecognizedOptionException(char c) { 
            super(); 
            this.c = c;
        }

        /**
         * create an exception indicating that an unrecognized option
         * was encountered
         */
        public UnrecognizedOptionException(Character C) { 
            super(); 
            this.c = C;
        }

        /**
         * create an exception indicating that an unrecognized option
         * was encountered
         */
        public UnrecognizedOptionException(String str) { super(str); }

        public String toString() { return getMessage(); }

        public String getMessage() { 
            if (c == null) 
                return super.getMessage();
            else 
                return "Unrecognized option: -" + c;
        }
    }
}

