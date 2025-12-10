/*
 * NCSA BIMAgrid
 * Radio Astronomy Imaging Group
 * National Center for Supercomputing Applications
 * University of Illinois at Urbana-Champaign
 * 605 E. Springfield, Champaign IL 61820
 * rai@ncsa.uiuc.edu
 *
 *-------------------------------------------------------------------------
 * History: 
 *  2001/09/17  rlp  initial version
 *  2007/11/16  rlp  reconstructed from jad-decompiled class file
 */
package ncsa.xml.saxfilter;

import java.util.Vector;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.SortedMap;
import java.util.Iterator;
import java.util.NoSuchElementException;

public class SkipSchedule {
    final TreeMap<Long, Boolean> sched = new TreeMap<>();
    boolean skipping;

    public SkipSchedule(boolean startskip) {
        skipping = startskip;
    }

    public synchronized void skipFrom(long pos) {
        if(pos <= 0L)
            skipping = true;
        else
            sched.put(pos, Boolean.TRUE);
    }

    public synchronized void skipTo(long pos) {
        if(pos <= 0L)
            skipping = false;
        else
            sched.put(pos, Boolean.FALSE);
    }

    public synchronized long nextSwitch() {
        Set<Long> keys = sched.keySet();
        for (Long pos : keys) {
            if (sched.get(pos) != skipping) {
                return pos;
            }
        }

        return 0x7fffffffffffffffL;
    }

    public final boolean skipping() {
        return skipping;
    }

    public synchronized void popTo(long pos) {
        Boolean last = null;
        Set<Long> remove = sched.headMap(pos + 1L).keySet();
        for(Iterator<Long> iter = remove.iterator(); iter.hasNext(); iter.remove())
            last = sched.get(iter.next());

        if(last != null)
            skipping = last;
    }

    public synchronized void insert(long start, long change) {
        if(sched.size() == 0 || change == 0L)
            return;
        Map.Entry pair = null;
        Object[] newpair = null;
        Long pos = null;
        Vector<Object> newentries = new Vector<>(sched.size());
        Iterator iter = sched.entrySet().iterator();
        do
        {
            if(!iter.hasNext())
                break;
            pair = (Map.Entry)iter.next();
            pos = (Long)pair.getKey();
            if(start < pos.longValue())
            {
                newpair = (new Object[] {
                    pos + change, pair.getValue()
                });
                newentries.addElement(newpair);
                iter.remove();
            }
        } while(true);
        for(iter = newentries.iterator(); iter.hasNext(); sched.put((Long) newpair[0], (Boolean) newpair[1]))
            newpair = (Object[])iter.next();

    }
}
