package ncsa.horizon.utils;

import ncsa.horizon.util.CmdLine;

import java.util.Enumeration;
import java.util.Stack;

import org.junit.Before;
import org.junit.Test;
import org.junit.Assert;

public class CmdLineTest {

    private static final String config = "f:puqx-";
    private static final String opts = "fpuqx";
    private static final String[] args = "-quq -q -f file.txt -x -hello world".split(" ");
    private CmdLine cl = null;

    @Before
    public void setup() {
        cl = new CmdLine(config);
    }

    @Test 
    public void testGetConfig() {
        Assert.assertEquals("Incorrect config", config, cl.getConfig());
    }

    @Test 
    public void testSetConfig() {
        cl.setConfig("re");
        Assert.assertFalse(cl.isAnOption(opts.charAt(0)));
        Assert.assertTrue(cl.isAnOption('r'));
    }

    @Test public void testSetFlags() {
        Assert.assertEquals(CmdLine.NULLFLAG, cl.getFlags());

        cl.setFlags(CmdLine.RELAX|CmdLine.WARN);
        Assert.assertEquals((CmdLine.RELAX|CmdLine.WARN), cl.getFlags());
        cl.addFlags(CmdLine.USRWARN);
        Assert.assertEquals((CmdLine.RELAX|CmdLine.WARN|CmdLine.USRWARN), cl.getFlags());

        cl.setFlags(CmdLine.NULLFLAG);
        Assert.assertEquals(CmdLine.NULLFLAG, cl.getFlags());
    }

    @Test public void testOptionsDef() {
        StringBuilder set = new StringBuilder();
        for(Enumeration<Character> e=cl.options(); e.hasMoreElements();) {
            Character opt = e.nextElement();
            Assert.assertTrue(String.format("unexpected option: %c", opt),
                       opts.indexOf(opt) >= 0);
            set.append(opt);
        }
        String optset = set.toString();
        for(int i=0; i < optset.length(); i++) {
            char opt = optset.charAt(i);
            Assert.assertTrue(String.format("option not set: %c", opt),
                       opts.indexOf(opt) >= 0);
            Assert.assertTrue(String.format("option not set: %c", opt),
                       cl.isAnOption(opt));
        }
        Assert.assertEquals(optset.length(), opts.length());

        Assert.assertTrue("Misconfigured option: p", cl.isSwitched('p'));
        Assert.assertTrue("Misconfigured option: u", cl.isSwitched('u'));
        Assert.assertTrue("Misconfigured option: q", cl.isSwitched('q'));
        Assert.assertTrue("Misconfigured option: x", cl.isSwitched('x'));
        Assert.assertFalse("Misconfigured option: f", cl.isSwitched('f'));
    }

    @Test
    public void testNoArgsGetNumSet() {
        Assert.assertEquals(0, cl.getNumSet('f'));
    }

    @Test
    public void testNoArgsGetValue() {
        Assert.assertNull(cl.getValue('f'));
    }

    @Test
    public void testNoArgsIsSet() {
        Assert.assertFalse(cl.isSet('q'));
        Assert.assertFalse(cl.isSet('f'));
        Assert.assertFalse(cl.isSet('x'));
    }

    @Test
    public void testNoArgsGetAllValues() {
        Stack<String> vals = cl.getAllValues('f');
        Assert.assertEquals("Non-empty list of option values on no-arg",
                0, vals.size());
    }

    @Test public void testEmptyCmdLine() {
        String[] args = new String[0];
        try {
            cl.setCmdLine(args);
        } catch (CmdLine.UnrecognizedOptionException ex) {
            Assert.fail("inadvertently threw UnrecognizedOptionException");
        }

        for(int i=0; i < opts.length(); i++) {
            Assert.assertFalse(cl.isSet(opts.charAt(i)));
        }
        Assert.assertEquals(0, cl.getNumArgs());
    }

    @Test public void testBadOption() throws Exception {
        String[] args = "-re".split(" ");
        try {
            cl.setCmdLine(args);
            Assert.fail("Failed to detected unrecognized option");
        } catch (CmdLine.UnrecognizedOptionException ex) {
            // ignore
        }

        cl.setFlags(CmdLine.RELAX);
        cl.setCmdLine(args);

        cl.addFlags(CmdLine.USRWARN);
        cl.setCmdLine(args);
    }

    private void setCmdLine() throws Exception {
        cl.setFlags(CmdLine.RELAX);
        cl.setCmdLine(args);
    }

    @Test public void testSetCmdLine() throws Exception {
        setCmdLine();

        Assert.assertEquals("Wrong number of arguments detected",
                2, cl.getNumArgs());
        Assert.assertEquals("Wrong number of arguments detected",
                2, cl.getNumArgs());
    }

    @Test public void testOptions() throws Exception {
        setCmdLine();

        Assert.assertTrue(cl.isSet('q'));
        Assert.assertTrue(cl.isSet('u'));
        Assert.assertTrue(cl.isSet('x'));
        Assert.assertFalse(cl.isSet('p'));
        Assert.assertFalse(cl.isSet('e'));
        Assert.assertFalse(cl.isSet('r'));

        Assert.assertEquals("Bad option occurrence count", 3, cl.getNumSet('q'));

        Assert.assertEquals("Bad option arg", "file.txt", cl.getValue('f'));
    }

    @Test public void testArguments() throws Exception {
        String[] expected = "-hello world".split(" ");

        setCmdLine();
        int i=0;
        StringBuilder extra = new StringBuilder();
        for(Enumeration<String> e = cl.arguments(); e.hasMoreElements(); i++){
            String s = e.nextElement();
            if (i < expected.length)
                Assert.assertEquals(String.format("Argument out of order: %s", s), s, expected[i]);
            else
                extra.append(' ').append(s);
        }
        Assert.assertTrue(String.format("extra arguments found:%s",extra),
                   i <= expected.length);
        if (i < expected.length) {
            for(; i < expected.length; i++) 
                extra.append(' ').append(expected[i]);
            Assert.fail(String.format("missing arguments:%s",extra));
        }
    }
}
    
