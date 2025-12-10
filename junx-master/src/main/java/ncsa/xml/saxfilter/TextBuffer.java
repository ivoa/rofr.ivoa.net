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
 *  2001/07/26  rlp  initial version
 */
package ncsa.xml.saxfilter;

import java.util.LinkedList;
import java.util.ListIterator;
import java.util.NoSuchElementException;

public class TextBuffer {
    LinkedList deque = new LinkedList();
    int start = 0;  // characters before that at this pos have been discarded
    int length = 0; // the total length the valid data

    public TextBuffer() { }

    /**
     * return the number of characters currently held in the buffer
     */
    public int size() { return length; }

    /**
     * append a String to the buffer
     * @param s   the string to append
     */
    public TextBuffer append(String s) {
        if (s.length() > 0) {
            deque.add(new Substring(s));
            length += s.length();
        }
        return this;
    }

    public String toString() {
        StringBuffer sb = new StringBuffer();
        ListIterator i = deque.listIterator();
        if (! i.hasNext()) return sb.toString();

        Substring ss = (Substring) i.next();
        int n = start;
        while (n >= ss.str().length() && i.hasNext()) {
            n -= ss.str().length();
            ss = (Substring) i.next();
        }
        if (n >= ss.str().length()) return sb.toString();

        if (n > 0)
            sb.append(ss.str().substring(n));
        else
            sb.append(ss.str());
        while (i.hasNext()) 
            sb.append(((Substring) i.next()).str());

        return sb.toString();
    }

    /**
     * return the Substring containing the i-th character as an iterator
     * @param i  the index of the desired character
     * @return Iter   an iterator positioned such that the next
     *                element it returns will be the requested 
     *                Substring.  The Substring's start will be
     *                set at the position of the requested character.
     *                null is returned if the position is out of 
     *                range.
     */
    public Iter getSubstring(int i) {
        if (i < 0 || i >= length) return null;

        ListIterator out = deque.listIterator();
        Substring sub = null;
        int p = -start;
        while(out.hasNext()) 
        {
            sub = (Substring)out.next();
            p += sub.string.length();
            if (p > i) {
                sub.off = sub.string.length() - (p - i);
                out.previous();
                return new Iter(out);
            }
        }

        return null;
    }

    /**
     * insert a string at a given position
     * @param s    the string to insert
     * @param pos  the position to insert the string at
     * @throws StringIndexOutOfBounds  if pos is out of bounds
     */
    public TextBuffer insert(String s, int pos) {
        if (pos == length) 
            return append(s);
        if (pos < 0 || pos > length)
            throw new StringIndexOutOfBoundsException(pos);

        // get substring containing insert position
        Substring sub = (Substring)getSubstring(pos).next();

        // create a new string with the inserted chacters
        char newstr[] = new char[sub.string.length()+s.length()];
        if (sub.off > 0) sub.string.getChars(0, sub.off, newstr, 0);
        s.getChars(0, s.length(), newstr, sub.off);
        sub.string.getChars(sub.off, sub.string.length(), newstr, 
                            sub.off + s.length());

        // replace the substring's contents; any iterators pointing to this
        // substring remains valid.
        sub.string = new String(newstr);
        sub.len = sub.string.length() - sub.off;

        // update the total size of our buffer
        length += s.length();
        return this;
    }

    /**
     * substitute in a string at a given position.  
     * @param s    the string to substitute in
     * @param pos  the position to begin the substitution
     * @param len  the number of characters to pull out starting with pos
     * @throws StringIndexOutOfBounds  if pos is out of bounds
     */
    public TextBuffer substitute(String s, int pos, int len) {
        if (pos == length)
            return append(s);
        if (pos < 0 || pos > length)
            throw new StringIndexOutOfBoundsException(pos);
        if (pos+len > length) len = length-pos;

        // get the substring containing start of substitution 
        Iter iter = getSubstring(pos);
        Substring sub = (Substring)iter.next();

        length += s.length() - len;

        StringBuffer newstr = new StringBuffer();
        if (sub.off > 0)
            newstr.append(sub.string.substring(0, sub.off));
        newstr.append(s);
        if (sub.off + len < sub.string.length())
            newstr.append(sub.string.substring(sub.off + len));
        len -= sub.string.length() - sub.off;

        sub.string = newstr.toString();
        sub.len = sub.string.length() - sub.off;

        while(len > 0 && iter.hasNext())  {
            sub = (Substring)iter.next();
            if (sub.string.length() <= len) {
                len -= sub.string.length();
                sub.string = new String();
                iter.remove();
            } 
            else {
                sub.string = sub.string.substring(len);
                len = 0;
            }
        }

        return this;
    }

    /**
     * pop off a given number of characters from the start of this buffer
     */
    public void popChars(int nchars) {
        if (nchars <= 0) return;

        Substring sub = null;
        start += nchars; 
        while (deque.size() > 0 && 
               start >= ((Substring)deque.getFirst()).string.length())
        {
            sub = (Substring)deque.removeFirst();
            start -= sub.string.length();
        }
        length -= nchars;
        if (length < 0) length = 0;
    }

    /**
     * copy characters from this text buffer into a given character array
     * @param srcBegin   the index of the first character from this buffer
     *                       to copy
     * @param chars      the array to copy into
     * @param destBegin  the index into the output array to copy the first 
     *                       character to 
     * @param len        the number of characters to copy
     */
    public void copy(int srcBegin, char chars[], int destBegin, int len) {
        int n;
        ListIterator li = getSubstring(srcBegin);
        if (li == null) throw new ArrayIndexOutOfBoundsException(srcBegin);
        Substring sub = (Substring) li.next();
        try {
            while (len > 0) {
                n = Math.min(sub.str().length(), sub.off+len);
                sub.str().getChars(sub.off, n, chars, destBegin);
                n -= sub.off;
                destBegin += n;
                srcBegin += n;
                len -= n;
                if (len > 0) {
                    sub = (Substring)li.next();
                    sub.off = 0;
                }
            }
        }
        catch(NoSuchElementException ex) {
            throw new ArrayIndexOutOfBoundsException(srcBegin);
        }
    }

    /**
     * return the first Substring in this buffer
     */
    public Substring getFirst() {
        Substring out = (Substring)deque.getFirst();
        out.off = start;
        out.len = out.string.length() - out.off;
        return out;
    }

    /**
     * return the first Substring in this buffer
     */
    public Substring getLast() {
        return (Substring)deque.getLast();
    }

    /**
     * return an iterator positioned to return the last Substring with
     * the first call to previous()
     */
    public Iter iterAtLast() {
        return new Iter(deque.listIterator(deque.size()));
    }

    void replace(Substring sub, String replacement) {
        if (sub == getFirst()) {
            length += start;
            start = 0;
        }
        length -= sub.str().length();
        sub.string = replacement;
        length += replacement.length();
    }

    public class Iter implements ListIterator<Substring> {
        private final ListIterator<Substring> li;
        Substring last = null;

        Iter(ListIterator<Substring> li) { this.li = li; }

        void unsupp(String op) {
            throw new UnsupportedOperationException(op);
        }

        void privAdd(Substring s) { li.add(s); }
        void privSet(Substring s) { li.set(s); }

        public final void add(Substring o) { unsupp("add"); }
        public final void set(Substring o) { unsupp("set"); }
        public final boolean hasNext() { return li.hasNext(); }
        public final boolean hasPrevious() { return li.hasPrevious(); }
        public final int nextIndex() { return li.nextIndex(); }
        public final int previousIndex() { return li.previousIndex(); }

        public final Substring next() {
            last = li.next();
            return last;
        }
        public final Substring previous() {
            last = li.previous();
            return last;
        }
        public final Substring current() { return last; }

        public final void remove() {
            li.remove();
            if (last != null) {
                if (li.hasPrevious() && start > 0) {
                    length += start;
                    start = 0;
                }
                length -= last.str().length();
            }
        }
    }

}
