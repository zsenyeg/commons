/*
 * Copyright (c) 2004 Persistit Corporation. All Rights Reserved.
 *
 * The Java source code is the confidential and proprietary information
 * of Persistit Corporation ("Confidential Information"). You shall
 * not disclose such Confidential Information and shall use it only in
 * accordance with the terms of the license agreement you entered into
 * with Persistit Corporation.
 *
 * PERSISTIT CORPORATION MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE
 * SUITABILITY OF THE SOFTWARE, EITHER EXPRESS OR IMPLIED, INCLUDING BUT
 * NOT LIMITED TO THE IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR
 * A PARTICULAR PURPOSE, OR NON-INFRINGEMENT. PERSISTIT CORPORATION SHALL
 * NOT BE LIABLE FOR ANY DAMAGES SUFFERED BY LICENSEE AS A RESULT OF USING,
 * MODIFYING OR DISTRIBUTING THIS SOFTWARE OR ITS DERIVATIVES.
 */
 
package com.persistit;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.SimpleDateFormat;
import java.util.Date;

import com.persistit.encoding.CoderContext;
import com.persistit.encoding.KeyCoder;
import com.persistit.encoding.KeyDisplayer;
import com.persistit.encoding.KeyRenderer;
import com.persistit.encoding.KeyStringCoder;
import com.persistit.exception.ConversionException;
import com.persistit.exception.InvalidKeyException;
import com.persistit.exception.MissingKeySegmentException;

/**
 * <p>
 * Encapsulates the key used in storing, fetching or deleting a 
 * key/value pair from a Persistit&trade; database. A <tt>Key</tt>'s
 * state is represented by an array of bytes that are used to physically 
 * locate records in a <tt>Tree</tt>.  The architectural maximum length
 * of this byte array is 2,047 bytes.  Thus a <tt>Key</tt> may be used
 * to represent a significant, but not unlimited amount of information. 
 * Applications may use the <a href="#_lowLevelAPI">Low-Level API</a> 
 * methods to access and modify the key's backing byte array directly.
 * However, <tt>Key</tt> provides a recommended higher-level API that 
 * simplifies encoding and decoding Java values.
 * </p>
 * <a name="_keyOrdering">
 * <h3>Key Ordering</h3>
 * </a>
 * <p>
 * Within a <tt>Tree</tt>, keys are ordered in lexicographic order, by unsigned
 * byte value.  Thus keys encoded physically as shown will be stored in
 * the following order:
 * <blockquote><pre>
 *   1st: {0x01, 0x01}
 *   2nd: {0x01, 0x01, 0x02, 0x01}
 *   3rd: {0x01, 0x01, 0x03, 0x01}
 *   4th: {0x01, 0x01, 0x03, 0x02}
 *   4th: {0x01, 0x02, 0x01}
 *   6th: {0x01, 0x02, 0x01, 0x01}
 *   7th: {0x01, 0x02, 0x04, 0xE3}
 *   8th: {0xF7, 0x03}
 *   9th: {0xF9}
 * </pre></blockquote>
 * The ordering is important because it governs the order in which the 
 * {@link Exchange#traverse} operations visit records, and the set of keys/value pairs
 * that will be removed by the range {@link Exchange#remove} operations.  Key ordering
 * also affects the physical proxmitity of items in the <tt>Tree</tt>'s on-disk
 * representation, which can affect performance.
 * </p>
 * <p>
 * <tt>Key</tt> provides methods to <i>encode</i> and <i>decode</i> logical
 * values into and from the backing byte array.  For example, the 
 * {@link #append(int)} method encodes an int into the key in such a way that 
 * the natural ordering of integer values is preserved; that is, for integers
 * u and v if u &gt; v then the key encoded from u always follows that encoded
 * from v in lexicographic order.  The <tt>append</tt> method is overloaded to 
 * support each primitive Java type and <tt>Object</tt>.  The ordering 
 * of key values within each primitive type follows the natural ordering 
 * for that type (see <a href="#_keyStringEncoding">String Encoding</a> for 
 * information on collation of Strings).
 * </p>
 * <p>
 * There is also an implicit ordering between different encoded types.  
 * The ordering of key values with differing types is as follows:
 * <blockquote><pre>
 *  BEFORE &lt; null &lt; boolean &lt; byte &lt; short &lt; 
 *  char &lt; int &lt; long &lt; float &lt; double &lt; 
 *  java.math.BigInteger &lt; java.math.BigDecimal &lt; 
 *  byte[] &lt; java.lang.String &lt; java.util.Date &lt; 
 *  <i>custom-encoded types</i> &lt; AFTER
 * </pre></blockquote>
 * (Note: {@link #BEFORE} and {@link #AFTER} are special pseudo-values that
 * allow traversal from the first and last keys in a tree.)
 * </p>
 * <h4>Equivalence of Wrapped and Primitive Types</h3>
 * <p>
 * By default <tt>Key</tt> encodes objects of type <tt>Boolean</tt>, 
 * <tt>Byte</tt>, <tt>Short</tt>, <tt>Character</tt>, <tt>Integer</tt>,
 * <tt>Long</tt>, <tt>Float</tt> and <tt>Double</tt> exactly the same as their
 * primitive counterparts.  Thus the {@link #decode} method, which returns
 * an Object, can be used uniformly to decode values that were appended as
 * either primitives or their wrapped equivalents.
 * </p>
 * <a name="_keyStringEncoding">
 * <h3>String Encoding</h3>
 * </a>
 * <p>
 * By default Strings are encoded in a modified UTF-8 format.  This encoding
 * preserves alphabetic ordering of all 7-bit ASCII strings.  Character codes
 * above 128 are translated into two- or three-byte sequences according to
 * the <a href="http://www.ietf.org/rfc/rfc3629.txt">UTF-8</a> specification.
 * The protocol is modified for the NUL character (code 0), because encoded key
 * values are terminated by NUL.  Persistit encodes a NUL embedded within a 
 * String with the two-byte sequence (0x01, 0x20), and encodes SOH
 * (code 1) with the sequence (0x01, 0x21).
 * </p>
 * <p>
 * The default string-encoding algorithm does not support localized collation.
 * Specialized collation can be added through a custom
 * {@link com.persistit.encoding.KeyStringCoder}.  To use a
 * <tt>KeyStringEncoder</tt>, attach it to the <tt>Key</tt> with the
 * {@link #setKeyStringCoder setKeyStringCoder} method.  Note that once 
 * custom-coded Strings have been inserted as keys into the Persistit 
 * database, the same <tt>KeyStringCoder</tt> must be attached to 
 * the <tt>Key</tt> for proper decoding.
 * </p>
 * <a name="_ObjectEncoding">
 * <h3>Object Encoding</h3>
 * </a>
 * <p>
 * Persistit offers built-in support for encoding and decoding of a few commonly
 * used Object types.  These include:
 * <blockquote><pre>
 *      java.lang.String
 *      java.math.BigInteger
 *      java.math.BigDecimal
 *      java.util.Date
 *      byte[]
 * </blockquote></pre>
 * (Note that for byte array, a zero-valued bytes are converted to two-byte
 * sequences, as described above for strings.)
 * </p>
 * <p>
 * The default encoding for these types, plus any additional Object types that
 * are required to be used as key values, can be overridden by a custom
 * {@link com.persistit.encoding.KeyCoder}.  For consistency, the application
 * should register all custom <tt>KeyCoder</tt> objects immediately after
 * initializing Persistit, for example:
 * <blockquote><pre>
 *      Persistit.initialize();
 *      KeyCoder coder = new MyKeyCoder();
 *      Persistit.getInstance().getCoderManager()
 *          .registerKeyCoder(MyClass.class, coder);
 * </blockquote></pre>
 * All overridden object types sort <i>after</i> all other value types.
 * Ordering among various custom types is determined by the custom encoding 
 * algorithm's implementation. See {@link com.persistit.encoding.CoderManager}
 * for details.
 * </p>
 * <p>
 * While it is technically possible to override default String encoding 
 * using a <tt>KeyCoder</tt>, <tt>KeyStringCoder</tt> should be
 * used for this purpose. A <tt>KeyCoder</tt> is concerned with 
 * application logic, whereas <tt>KeyStringCoder</tt> supports
 * localization.  It is preferable to separate these concerns. 
 * </p>
 * <a name="_keySegments">
 * <h3>Key Segments</h3>
 * </a>
 * <p>
 * An application may append multiple values to a <tt>Key</tt>, each of which
 * is called a key <i>segment</i>.  Applications use multiple segments to
 * form concatenated keys.  A concatenated key uniquely identifies a particular
 * record by a combination of data values rather than one simple value.  The
 * number of segments in a Persistit concatenated key is bounded only by the
 * architectural limitation on the length of the underlying byte array.
 * </p>
 * Each key segment is encoded as a sequence of non-zero bytes. Segments
 * are separated by zero-valued bytes. A <tt>Key</tt> encodes strings,
 * byte arrays, and all other data types that might naturally contain a zero-
 * valued byte by inserting escape sequences in place of the zero values.
 * Specifically, NUL (character code 0) in a string, or a zero in a byte array
 * element is replaced by the two-byte sequence (0x01, 0x20). An SOH 
 * (character code 1) or a one in a byte array element is replaced by the 
 * two-byte sequence (0x01, 0x021). This scheme is handled automatically, 
 * and only those applications that manipulate the raw byte buffer using the
 * low-level API need to be aware of it.  This encoding ensures that the 
 * key orderering preserves the natural ordering of the underlying values.
 * For example, the encoded forms of the two string "AB" and "ABC" are 
 * (0x80, 0x41, 0x42, 0x00) and (0x80, 0x41, 0x42, 0x43, 0x00), respectively.
 * The two encodings differ in the third byte (the zero that terminates the
 * shorter string) which correctly causes the shorter string to collate
 * before the longer one.
 * <p>
 * Segments fall naturally into the ordering scheme. If two keys are different,
 * then the first segment that differs between the two keys controls their
 * ordering.  For example, the code fragment
 * <blockquote><pre><tt>
 *      key1.clear().append(1).append(1);
 *      key2.clear().append(1).append(2);
 *      key3.clear().append(2).append(1);
 *      key4.clear().append(2).append(1).append(0);
 * </tt></blockquote></pre>
 * 
 * sets the four keys so that their ordering sequence is 
 * <tt>key1 &lt; key2 &lt; key3 &lt; key4.</tt>
 * </p>
 * <a name="_keyChildren">
 * <h3>Logical Key Children and Siblings</h3>
 * </a>
 * <p>
 * The ability to append multiple key segments to a <tt>Key</tt> supports a
 * method of grouping records logically by hierarchical key values. Much as a
 * paper filing system uses cabinets, drawers, and folders to organize documents,
 * segmented keys can be used to impose a hierarchical organization on data.
 * For example, the key for a purchase order record might have a single segment
 * representing the purchase order number. The purchase order's subsidiary line 
 * items might then be stored with keys that are logical children of the 
 * purchase order number, as suggested in this code snippet:
 * <blockquote><pre>
 *  PurchaseOrderSummary poSummary = ...
 *  List poLineItems = ...
 *  ...
 *  exchange.getValue().put(poSummary)
 *  exchange.clear().append(poSummary.getPurchaseOrderNumber()).store();
 *  ..
 *  exchange.append(Key.BEFORE);
 *  for (Iterator items = poLineItems.iterator();
 *       items.hasMore();)
 *  {
 *      LineItem item = (LineItem)lineItems.next();
 *      exchange.getValue().set(item);
 *      exchange.clear().to(item.getLineItemId()).store();
 *  }
 *  ... 
 * </pre></blockquote>
 * This example would store the PurchaseOrderSummary under a key containing
 * just the purchase order number, and then store each of the line items from
 * the poLineItems List in a key containing the purchase order number and
 * the line item number as separate segments. (The {@link #to(Object)} method is a 
 * convenience method that replaces the final segment of the key with a new
 * value.) 
 * </p>
 * A key value is a <i>logical child</i> of another key value if it can
 * be formed by appending one or more key segments to that other key.  
 * For example, the key value containing purchase order number and line item
 * number is a logical child of the key for purchase order summary, which 
 * reflects the real-world concept that the line item is logically a child of 
 * the purchase order.  A <i>logical sibling</i> of key value is one that can be
 * formed by replacing the last segment of that key.  For example, any two
 * purchase order summary records are logical siblings.  Any two line items
 * are logical siblings only if they are logical children of key values formed
 * from the same purchase order number.
 * </p>
 * <p>
 * Logical child relationships between keys are represented solely by the way
 * in which keys are encoded and ordered within the physical tree; there is
 * no direct physical representation of the logical hierarchy.  However, 
 * because of the way keys are physically ordered within a <tt>Tree</tt>, 
 * logical child keys fall closer to their parents in key sort order than 
 * other keys, and are therefore more likely to be located on physical 
 * database pages that have already been read into the buffer pool.
 * </p>
 * <p>
 * Two families of methods of methods in {@link Exchange} incorporate logic to
 * handle logical child keys in a special way:
 * <ul>
 * <li> 
 * The {@link Exchange#traverse(Key.Direction, boolean)} method, and its variants 
 * {@link Exchange#next(boolean)} and {@link Exchange#previous(boolean)} are capable
 * of either <i>deep</i> or <i>shallow</i> traversal. If deep traversal is
 * requested then the result is the next (or previous) physical key in the 
 * <tt>Tree</tt>, regardless of whether it is a logical child key.  However, 
 * if shallow traversal is requested, all logical children are skipped
 * and the result is the next (or previous) logical sibling key value.
 * </li>
 * <li>
 * The {@link Exchange#remove(Key.Direction)} method optionally removes the
 * key/value pair specified by the current key value and/or the logical
 * children of that key value. Continuing the purchase order example, the
 * <tt>remove</tt> method can remove just the line items, just the purchase
 * order summary, or both.
 * </li>
 * </ul>
 * </p>
 * <a name="_stringRepresentation">
 * <h3>String Representation of a Key value</h3>
 * </a>
 * <p>
 * At times it is convenient to represent a Key value as a String, for example,
 * to display it or enter it for editing.  This class provides an implementation
 * of {@link #toString} that creates a canonical String representation of a
 * key. The {@link KeyParser#parseKey} method provides the inverse functionality,
 * parsing a canonical string representation to create a key value.
 * </p>
 * <p>
 * The String representation is of the form:
 * <pre><blockquote>
 *  { <i>segment</i>,... }
 * </blockquote></pre>
 * where each segment value is one of the following:
 * <ul>
 * <li>
 *  <tt>null</tt>
 * </li>
 * <li>
 *  <tt>false</tt>
 * </li>
 * <li>
 *  <tt>true</tt>
 * </li>
 * <li>
 *  a String literal, enclosed in quotes as in Java
 * </li>
 * <li>
 *  a numeric literal, interpreted as an int if no decimal point, otherwise as a double
 * </li>
 * <li>
 *  a <i>cast segment value</i> consisting of a parenthesized class name 
 *  followed by a String representation of a value of that class.
 * </li>
 * </ul>
 * For example, the following code excerpt
 * <pre><code><blockquote>
 *   Key key = new Key();
 *   key.append("xyz").append(1.23).append((long)456).append(new Date());
 *   System.out.println(key.toString());
 * </blockquote></code></pre> 
 * would produce a string representation such as
 * <pre><blockquote>
 *   {"xyz",1.23,(long)456,(java.util.Date)20040901114722.563+0500}
 * </blockquote></pre>
 * All numeric types other than <tt>double</tt> and <tt>int</tt> use a cast
 * segment representation so to permit exact translation to and from the String
 * representation and the underlying internal key value.  
 * The canonical representation of a Date is designed to allow exact
 * translation to and from the internal segment value while being somewhat 
 * legible.
 * </p>
 * <a name="_puttingTogether">
 * <h3>Putting It All Together - How to Use the Key API</h3>
 * </a>
 * <p>
 * Applications do two fundamental things with <tt>Key</tt>s:
 * <ol>
 * <li>
 * Applications <i>construct</i> key values when fetching, storing or removing
 * key/value pairs.
 * </li>
 * <li>
 * Applications <i>decode</i> key values when traversing key/value pairs.
 * </li>
 * </ol>
 * </p>
 * <p>
 * Methods used to construct key values are {@link #clear}, {@link #setDepth},
 * {@link #cut}, {@link #append(boolean)}, {@link #append(byte)},
 * {@link #append(short)} ... {@link #append(Object)},
 * {@link #to(boolean)}, {@link #to(byte)} {@link #to(short)} ... 
 * {@link #to(Object)}.  These methods all modify the current state of the
 * key. As a convenience, these methods all return the <tt>Key</tt> so support
 * method call chaining.
 * </p>
 * <p>
 * Methods used to decode key values are {@link #reset}, {@link #indexTo},
 * {@link #decodeBoolean}, {@link #decodeByte}, {@link #decodeShort} ... 
 * {@link #decode}. These methods do not modify the value represented by the 
 * key.  Each <tt>decode<i>TTTT</i></tt> method returns a value of type 
 * <i>TTTT</i>. The {@link #decode} method returns a value of type 
 * <tt>Object</tt>. The <tt>reset</tt> and <tt>indexTo</tt> control which 
 * segment the next value will be decoded from.
 * </p>
 * <a name="_lowLevelAPI">
 * <h3>Low-Level API</h3>
 * </a>
 * <p>
 * The low-level API allows an application to bypass the encoding and
 * decoding operations described above and instead to operate directly
 * on the byte array used as the physical B-Tree key. This might be appropriate 
 * for an existing application that has already implemented its own
 * serialization mechanisms, for example, or to accommodate special key
 * manipulation requirements.  Applications should use these methods only
 * if there is a compelling requirement.
 * </p>
 * <p> 
 * The low-level API methods are:
 * <blockquote><pre>
 * 
 *      byte[] {@link #getEncodedBytes}
 *      int {@link #getEncodedSize}
 *      void {@link #setEncodedSize(int)}
 * 
 * </blockquote></pre>
 * </p>
 *
 * @version 1.0
 */
public final class Key
implements Comparable
{
    /**
     * Indicates for {@link Exchange#traverse traverse} that the next larger key
     * is to be returned.  Indicates for {@link Exchange#remove remove} that a
     * range of keys following but not including the specified key is to
     * be removed.
     */
    public static final Direction GT = new Direction("GT", 3);

    /**
     * Indicates for {@link Exchange#traverse traverse} that the specified key, 
     * if it exists or else the next larger key is to be returned.
     * Indicates for {@link Exchange#remove remove} that a range of keys 
     * including and following the specified key is to be removed.
     */
    public static final Direction GTEQ = new Direction("GTEQ", 4);
    
    /**
     * Indicates for {@link Exchange#traverse travers} that the specified key, 
     * if it exists, is to be returned. Indicates for {@link Exchange#remove remove}
     * that only the specified key is to be removed.
     */
    public static final Direction EQ = new Direction("EQ", 0);
    
    /**
     * Indicates for {@link Exchange#traverse traverse} that the current key, if it
     * exists or else the next smaller key is to be returned.
     * Not valid for {@link Exchange#remove remove}.
     */
    public static final Direction LTEQ = new Direction("LTEQ", 2);
    /**
     * Indicates for {@link Exchange#traverse traverse} that the next smaller key
     * is to be returned. Not valid for {@link Exchange#remove remove}.
     */
    public static final Direction LT = new Direction("LT", 1);
    /**
     * Key that always occupies the left edge of any <tt>Tree</tt>
     */
    public final static Key LEFT_GUARD_KEY = new Key(null, 1);
    /**
     * Key that always occupies the right edge of any <tt>Tree</tt>
     */
    public final static Key RIGHT_GUARD_KEY = new Key(null, 1);

    private final static int END = 0;
    
    private final static int LEFT_GUARD = 4;
    private final static int RIGHT_GUARD = 5;

    /**
     * Absolute architectural maximum number of bytes in the encoding
     * of a key. 
     */
    public final static int MAX_KEY_LENGTH = 2047;
    
    /**
     * A <tt>Key</tt> segment value that collates before any actual key in a
     * <tt>Tree</tt>. A <tt>Key</tt> may include an instance of this value
     * as its final segment, but the {@link Exchange#store store} and 
     * {@link Exchange#fetch fetch} methods will not permit such a key as inputs.
     * Use <tt>BEFORE</tt> to seed a {@link Exchange#traverse traverse} loop, as
     * shown in this code fragment:
     * <blockquote><pre>
     *      key.to(BEFORE);
     *      while (exchange.next(key))
     *      {
     *          ... do actions
     *      }
     * </pre></blockquote>
     */
    public final static EdgeValue BEFORE = new EdgeValue(false);
    /**
     * A <tt>Key</tt> segment value that collates after any actual key in a
     * <tt>Tree</tt>. A <tt>Key</tt> may include an instance of this value
     * as its final segment, but the {@link Exchange#store store} and 
     * {@link Exchange#fetch fetch} methods will not permit such a key as inputs.
     * Use <tt>AFTER</tt> to seed a {@link Exchange#traverse traverse} loop, 
     * as shown in this code fragment:
     * <blockquote><pre>
     *      key.to(AFTER);
     *      while (exchange.previous(key))
     *      {
     *          ... do actions
     *      }
     * </pre></blockquote>
     */
    public final static EdgeValue AFTER = new EdgeValue(true);
    
    /**
     * The <tt>java.text.SimpleDateFormat</tt> used in formatting <tt>Date</tt>-
     * valued keys in the {@link #decodeDisplayable} methods.  This format
     * also governs the conversion of dates for the <tt>toString</tt> method.
     * This format provides millisecond resolution so that the precise stored
     * representation of a date used as a key can be represented exactly, but
     * readably, in the displayable version. 
     */
    public final static SimpleDateFormat SDF =
        new SimpleDateFormat("yyyyMMddHHmmss.SSSZ");
    
    /**
     * Displayable prefix for boolean values
     * (optional for input, implied and supressed on output)
     */
    public final static String PREFIX_BOOLEAN = "(boolean)";
    /**
     * Displayable prefix for byte values
     */
    public final static String PREFIX_BYTE = "(byte)";
    /**
     * Displayable prefix for short values
     */
    public final static String PREFIX_SHORT = "(short)";
    /**
     * Displayable prefix for char values
     */
    public final static String PREFIX_CHAR = "(char)";
    /**
     * Displayable prefix for int values
     * (optional for input, implied and supressed on output)
     */
    public final static String PREFIX_INT = "(int)";
    /**
     * Displayable prefix for long values
     */
    public final static String PREFIX_LONG = "(long)";
    /**
     * Displayable prefix for float values
     */
    public final static String PREFIX_FLOAT = "(float)";
    /**
     * Displayable prefix for double values
     * (optional for input, implied and supressed on output)
     */
    public final static String PREFIX_DOUBLE = "(double)";
    /**
     * Displayable prefix for String values
     * (optional for input, implied and supressed on output)
     */
    public final static String PREFIX_STRING = "(java.lang.String)";
    public final static String PREFIX_STRING0 = "(String)";
    /**
     * Displayable prefix for BigInteger values
     */
    public final static String PREFIX_BIG_INTEGER = "(java.math.BigInteger)";
    public final static String PREFIX_BIG_INTEGER0 = "(BigInteger)";
    /**
     * Displayable prefix for BigDecimal values
     */
    public final static String PREFIX_BIG_DECIMAL = "(java.math.BigDecimal)";
    public final static String PREFIX_BIG_DECIMAL0 = "(BigDecimal)";
    /**
     * Displayable prefix for Date values
     */
    public final static String PREFIX_DATE = "(java.util.Date)";
    public final static String PREFIX_DATE0 = "(Date)";
    /**
     * Displayable prefix for byte array values
     */
    public final static String PREFIX_BYTE_ARRAY = "(byte[])";
    
    /**
     * Used by journaling subsystem.
     */
    public final static Direction[] DIRECTIONS = {EQ, LT, LTEQ, GT, GTEQ};
    
    private final static int TYPE_LEFT_EDGE = 0;
    private final static int TYPE_BEFORE = 1;   //    1
    
    private final static int TYPE_NULL = 2;    //    2
    private final static int TYPE_BOOLEAN_FALSE = 3;
    private final static int TYPE_BOOLEAN_TRUE = 4;
    private final static int TYPE_BYTE = 6;     //  6-8
    private final static int TYPE_UBYTE = 10;   // 10-13 .Net unsigned byte
    private final static int TYPE_SHORT = 15;   // 15-21
    private final static int TYPE_USHORT = 22;  // 22-25 .Net unsigned short - prefix for char
    private final static int TYPE_CHAR = 26;    // 26-29
    private final static int TYPE_INT = 31;     // 31-41
    private final static int TYPE_UINT = 43;    // 43-53 .Net unsigned int
    private final static int TYPE_LONG = 55;    // 55-73
    private final static int TYPE_ULONG = 76;   // 75-93 .Net unsigned long
    private final static int TYPE_DECIMAL = 97; //    97 .Net 128-bit decimal
    private final static int TYPE_FLOAT = 98;   //    98
    private final static int TYPE_DOUBLE = 99;  //    99
    
    private final static int TYPE_BIG_INTEGER = 110;  // 110
    private final static int TYPE_BIG_DECIMAL = 111;  // 111
    
    private final static int TYPE_BYTE_ARRAY = 126;
    private final static int TYPE_CHAR_ARRAY = 127; //not implemented
    private final static int TYPE_STRING = 128;
    private final static int TYPE_DATE = 129;   //   129
    
    private final static int TYPE_CODER_MIN = 192;
    private final static int TYPE_CODER1 = 192;
    private final static int TYPE_CODER2 = 200;
    private final static int TYPE_CODER3 = 208;
    private final static int TYPE_CODER6 = 216;
    private final static int TYPE_CODER_MAX = 219;
    
    private final static int TYPE_AFTER = 254;
    private final static int TYPE_RIGHT_EDGE = 255;
    
    private final static int EWIDTH_BYTE = 1;
    private final static int EWIDTH_SHORT = 3;
    private final static int EWIDTH_CHAR = 3;
    private final static int EWIDTH_INT = 5;
    private final static int EWIDTH_UINT = 6;
    private final static int EWIDTH_LONG = 9;
    private final static int EWIDTH_ULONG = 10;
    
    private final static Class[] CLASS_PER_TYPE =
    {
        /*   0 */    null,
        /*   1 */    EdgeValue.class,
        /*   2 */    Object.class,
        /*   3 */    Boolean.class,
        /*   4 */    Boolean.class,
        /*   5 */    null,
        /*   6 */    Byte.class,
        /*   7 */    Byte.class,
        /*   8 */    Byte.class,
        /*   9 */    null,
        /*  10 */    null,
        /*  11 */    null,
        /*  12 */    null,
        /*  13 */    null,
        /*  14 */    null,
        /*  15 */    Short.class,
        /*  16 */    Short.class,
        /*  17 */    Short.class,
        /*  18 */    Short.class,
        /*  19 */    Short.class,
        /*  20 */    Short.class,
        /*  21 */    Short.class,
        /*  22 */    null,
        /*  23 */    null,
        /*  24 */    null,
        /*  25 */    null,
        /*  26 */    Character.class,
        /*  27 */    Character.class,
        /*  28 */    Character.class,
        /*  29 */    Character.class,
        /*  30 */    null,
        /*  31 */    Integer.class,
        /*  32 */    Integer.class,
        /*  33 */    Integer.class,
        /*  34 */    Integer.class,
        /*  35 */    Integer.class,
        /*  36 */    Integer.class,
        /*  37 */    Integer.class,
        /*  38 */    Integer.class,
        /*  39 */    Integer.class,
        /*  40 */    Integer.class,
        /*  41 */    Integer.class,
        /*  42 */    null,
        /*  43 */    null,
        /*  44 */    null,
        /*  45 */    null,
        /*  46 */    null,
        /*  47 */    null,
        /*  48 */    null,
        /*  49 */    null,
        /*  50 */    null,
        /*  51 */    null,
        /*  52 */    null,
        /*  53 */    null,
        /*  54 */    null,
        /*  55 */    Long.class,
        /*  56 */    Long.class,
        /*  57 */    Long.class,
        /*  58 */    Long.class,
        /*  59 */    Long.class,
        /*  60 */    Long.class,
        /*  61 */    Long.class,
        /*  62 */    Long.class,
        /*  63 */    Long.class,
        /*  64 */    Long.class,
        /*  65 */    Long.class,
        /*  66 */    Long.class,
        /*  67 */    Long.class,
        /*  68 */    Long.class,
        /*  69 */    Long.class,
        /*  70 */    Long.class,
        /*  71 */    Long.class,
        /*  72 */    Long.class,
        /*  73 */    Long.class,
        /*  74 */    null,
        /*  75 */    null,
        /*  76 */    null,
        /*  77 */    null,
        /*  78 */    null,
        /*  79 */    null,
        /*  80 */    null,
        /*  81 */    null,
        /*  82 */    null,
        /*  83 */    null,
        /*  84 */    null,
        /*  85 */    null,
        /*  86 */    null,
        /*  87 */    null,
        /*  88 */    null,
        /*  89 */    null,
        /*  90 */    null,
        /*  91 */    null,
        /*  92 */    null,
        /*  93 */    null,
        /*  94 */    null,
        /*  95 */    null,
        /*  96 */    null,
        /*  97 */    null,
        /*  98 */    Float.class,
        /*  99 */    Double.class,
        /* 100 */    null,
        /* 101 */    null,
        /* 102 */    null,
        /* 103 */    null,
        /* 104 */    null,
        /* 105 */    null,
        /* 106 */    null,
        /* 107 */    null,
        /* 108 */    null,
        /* 109 */    null,
        /* 110 */    BigInteger.class,
        /* 111 */    BigDecimal.class,
        /* 112 */    null,
        /* 113 */    null,
        /* 114 */    null,
        /* 115 */    null,
        /* 116 */    null,
        /* 117 */    null,
        /* 118 */    null,
        /* 119 */    null,
        /* 120 */    null,
        /* 121 */    null,
        /* 122 */    null,
        /* 123 */    null,
        /* 124 */    null,
        /* 125 */    null,
        /* 126 */    byte[].class,
        /* 127 */    char[].class,
        /* 128 */    String.class,
        /* 129 */    Date.class,
        /* 130 */    null,
        /* 131 */    null,
        /* 132 */    null,
        /* 133 */    null,
        /* 134 */    null,
        /* 135 */    null,
        /* 136 */    null,
        /* 137 */    null,
        /* 138 */    null,
        /* 139 */    null,
        /* 140 */    null,
        /* 141 */    null,
        /* 142 */    null,
        /* 143 */    null,
        /* 144 */    null,
        /* 145 */    null,
        /* 146 */    null,
        /* 147 */    null,
        /* 148 */    null,
        /* 149 */    null,
        /* 150 */    null,
        /* 151 */    null,
        /* 152 */    null,
        /* 153 */    null,
        /* 154 */    null,
        /* 155 */    null,
        /* 156 */    null,
        /* 157 */    null,
        /* 158 */    null,
        /* 159 */    null,
        /* 160 */    null,
        /* 161 */    null,
        /* 162 */    null,
        /* 163 */    null,
        /* 164 */    null,
        /* 165 */    null,
        /* 166 */    null,
        /* 167 */    null,
        /* 168 */    null,
        /* 169 */    null,
        /* 170 */    null,
        /* 171 */    null,
        /* 172 */    null,
        /* 173 */    null,
        /* 174 */    null,
        /* 175 */    null,
        /* 176 */    null,
        /* 177 */    null,
        /* 178 */    null,
        /* 179 */    null,
        /* 180 */    null,
        /* 181 */    null,
        /* 182 */    null,
        /* 183 */    null,
        /* 184 */    null,
        /* 185 */    null,
        /* 186 */    null,
        /* 187 */    null,
        /* 188 */    null,
        /* 189 */    null,
        /* 190 */    null,
        /* 191 */    null,
        /* 192 */    null,
        /* 193 */    null,
        /* 194 */    null,
        /* 195 */    null,
        /* 196 */    null,
        /* 197 */    null,
        /* 198 */    null,
        /* 199 */    null,
        /* 200 */    null,
        /* 201 */    null,
        /* 202 */    null,
        /* 203 */    null,
        /* 204 */    null,
        /* 205 */    null,
        /* 206 */    null,
        /* 207 */    null,
        /* 208 */    null,
        /* 209 */    null,
        /* 210 */    null,
        /* 211 */    null,
        /* 212 */    null,
        /* 213 */    null,
        /* 214 */    null,
        /* 215 */    null,
        /* 216 */    null,
        /* 217 */    null,
        /* 218 */    null,
        /* 219 */    null,
        /* 220 */    null,
        /* 221 */    null,
        /* 222 */    null,
        /* 223 */    null,
        /* 224 */    null,
        /* 225 */    null,
        /* 226 */    null,
        /* 227 */    null,
        /* 228 */    null,
        /* 229 */    null,
        /* 230 */    null,
        /* 231 */    null,
        /* 232 */    null,
        /* 233 */    null,
        /* 234 */    null,
        /* 235 */    null,
        /* 236 */    null,
        /* 237 */    null,
        /* 238 */    null,
        /* 239 */    null,
        /* 240 */    null,
        /* 241 */    null,
        /* 242 */    null,
        /* 243 */    null,
        /* 244 */    null,
        /* 245 */    null,
        /* 246 */    null,
        /* 247 */    null,
        /* 248 */    null,
        /* 249 */    null,
        /* 250 */    null,
        /* 251 */    null,
        /* 252 */    null,
        /* 253 */    null,
        /* 254 */    EdgeValue.class,
        /* 255 */    null,
    };

    // The constants are used in converting BigInteger and BigDecimal values.
    //
    private final static BigInteger BIG_INT_DIVISOR = 
        BigInteger.valueOf(10000000000000000L);
    
    static
    {
        LEFT_GUARD_KEY.setLeftEdge();
        RIGHT_GUARD_KEY.setRightEdge();
    };
    
    private byte[] _bytes;
    private int _size;
    private int _index;
    private int _depth;
    private int _maxSize;
    private long _generation;
    private KeyStringCoder _stringCoder;
    private Persistit _persistit;

    /**
     * Enumeration of possible qualifiers for the
     * {@link Exchange#traverse traverse} and 
     * {@link Exchange#remove(Key.Direction) remove} methods.
     * Values include {@link #EQ}, {@link #GT}, {@link #GTEQ}, 
     * {@link #LT} and {@link #LTEQ}.
     */
    public static class Direction
    implements Serializable
    {
        public static final long serialVersionUID = -6405708619069695188L;
        
        private String _description;
        private int _index;
        
        private Direction(String description, int index)
        {
            _description = description;
            _index = index;
        }
        
        public String toString()
        {
            return _description;
        }
        
        int getIndex()
        {
            return _index;
        }
        
        public Object readResolve()
        {
            return DIRECTIONS[_index];
        }
    }
    
    /**
     * Enumeration of special key segment values used to traverse from the
     * first or last key.  At most one <tt>EdgeValue</tt> may be appended 
     * to a key, but a key so constructed is not valid for the
     * {@link Exchange#store store} or {@link Exchange#fetch fetch} operations.
     * See {@link #BEFORE} and {@link #AFTER}. 
     */
    public static class EdgeValue
    implements Serializable
    {
        public static final long serialVersionUID = -502106634184636556L;
        
        boolean _after;
        
        private EdgeValue(boolean after)
        {
            _after = after;
        }
        
        public String toString()
        {
            return _after ? "{after}" : "{before}";
        }
        
        public Object readResolve()
        {
            return _after ? AFTER : BEFORE;
        }
    }
    
    
    /**
     * Copies all state information from this to the supplied <code>Key</code>.
     * To create a new <code>Key</code> with state identical to this one, the
     * preferred mechanism for use outside of this package is this the copy
     * constructor:
     * <blockquote><pre>
     *      Key copiedKey = new Key(originalKey);
     * </pre></blockquote>
     * @param key   The <tt>Key</tt> to copy.
     */
    public void copyTo(Key key)
    {
        if (key == this) return;
        if (key._maxSize != _maxSize)
        {
            key._bytes = new byte[_maxSize + 1];
            key._maxSize = _maxSize;
        }
        
        if (_size > 0)
        {
            System.arraycopy(_bytes, 0, key._bytes, 0, _size);
        }
        key._size = _size;
        key._index = _index;
        key._depth = _depth;
        key._stringCoder = _stringCoder;
        key._persistit = _persistit;
        key.bumpGeneration();
    }
    
    public Key(final Persistit persistit) {
    	this(persistit, MAX_KEY_LENGTH);
    }
    /**
     * Constructs a <tt>Key</tt> with the specified maximum length.
     * @param   maxLength   The maximum length
     */
    private Key(final Persistit persistit, final int maxLength)
    {
    	_persistit = persistit;
        if (maxLength <= 0)
        {
            throw new IllegalArgumentException("Key length must be positive");
        }
        if (maxLength > MAX_KEY_LENGTH)
        {
            throw new IllegalArgumentException(
                "Key length must be less than " + MAX_KEY_LENGTH);
        }
        _maxSize = maxLength;
        _bytes = new byte[maxLength + 1];
        _size = 0;
        _depth = 0;
    }
    
    /**
     * Constructs a <tt>Key</tt> which duplicates the state of the supplied
     * <tt>Key</tt>.
     * @param source    The <tt>Key</tt> to copy
     */
    public Key(Key source)
    {
        source.copyTo(this);
    }

    /**
     * Returns the maximum number of bytes permitting in the backing byte
     * array for this <tt>Key</tt>.
     * @return  The maximum physical size
     */
    public int getMaximumSize()
    {
        return _maxSize;
    }
    
    /**
     * Returns the byte array that backs this <tt>Key</tt>.
     * This method is part of the <a href="#_lowLevelAPI">Low-Level API</a>.
     * @return  The backing byte array
     */
    public byte[] getEncodedBytes()
    {
        return _bytes;
    }
    
    /**
     * Count of encoded bytes in the backing byte array.
     * This method is part of the <a href="#_lowLevelAPI">Low-Level API</a>.
     * @return  The count
     */
    public int getEncodedSize()
    {
        return _size;
    }
    
    /**
     * Sets the count of valid encoded bytes in the backing array for this
     * <tt>Key</tt>.
     * This method is part of the <a href="#_lowLevelAPI">Low-Level API</a>.
     */
    public void setEncodedSize(int size)
    {
        notLeftOrRightGuard();
        if (size < 0 || size > _maxSize)
        {
            throw new IllegalArgumentException("Invalid size=" + size);
        }
        _size = size;
        _depth = -1;
        _index = 0;
        bumpGeneration();
    }
    
    /**
     * The number of key segments in this <tt>Key</tt>.  For example,
     * the code
     * <blockquote><pre>
     *      key.clear().append("a").append("b").append("c");
     * </pre></blockquote>
     * results in a depth of 3.
     * @return  The number of key segments.
     */
    public int getDepth()
    {
        if (_depth == -1) recomputeCurrentDepth();
        return _depth;
    }
    
    /**
     * The index is the next position in the backing byte array from which
     * a segment value will be decoded.  Applications should usually use the
     * {@link #indexTo} method to set the index to a valid location. 
     * @param index
     * @return  This <tt>Key</tt>, to permit method call chaining
     */
    public Key setIndex(int index)
    {
        notLeftOrRightGuard();
        if (index < 0 || index > _size)
        {
            throw new IllegalArgumentException(
                "index=" + index + " _size=" + _size);
        }
        _index = index;
        return this;
    }
    
    
    /**
     * Returns the current <tt>KeyStringCoder</tt>.
     * @return  The <tt>KeyStringCoder</tt>
     */
    public KeyStringCoder getKeyStringCoder()
    {
        return _stringCoder;
    }
    
    /**
     * Replaces the current <tt>KeyStringCoder</tt>.
     * @param coder The new <tt>KeyStringCoder</tt>
     */
    public void setKeyStringCoder(KeyStringCoder coder)
    {
        _stringCoder = coder;
    }
    
    /**
     * Computes a hash code for key value currently represented by
     * this <tt>Key</tt>.  Changing the key will result in a different
     * hashCode value.  A {@link KeyState} holds an immutable 
     * copy of the state of a <tt>Key</tt> for use in maps.  The
     * <tt>hashCode</tt> and <tt>equals</tt> methods of <tt>Key</tt>
     * and <tt>KeyState</tt> are compatible so that an application can
     * perform a map lookup using a <tt>Key</tt> when the map's key is actually
     * a <tt>KeyState</tt>.
     * @return The hash code   
     */
    public int hashCode()
    {
        int hashCode = 0;
        for (int index = 0; index < _size; index++)
        {
            hashCode = (hashCode * 17) ^ (_bytes[index] & 0xFF); 
        }
        return hashCode & 0x7FFFFFFF;
    }
    
    /**
     * Compares this <tt>Key</tt> to another <tt>Key</tt> or 
     * {@link KeyState}.  A <tt>KeyState</tt> holds an immutable 
     * copy of the state of a <tt>Key</tt> for use in maps.  The
     * <tt>hashCode</tt> and <tt>equals</tt> methods of <tt>Key</tt>
     * and <tt>KeyState</tt> are compatible so that an application can
     * perform a map lookup using a <tt>Key</tt> when the map's key is actually
     * a <tt>KeyState</tt>.
     * @return  <tt>true</tt> if the target represents the same <tt>Key</tt>
     *          value
     */
    public boolean equals(Object target)
    {
        if (target instanceof Key) return compareTo(target) == 0;
        else if (target instanceof KeyState)
        {
            return ((KeyState)target).equals(this);
        }
        else return false;
    }
    
    /**
     * Returns a positive integer if this <tt>Key</tt> is larger than the
     * supplied <tt>Key</tt>, a negative integer if it is smaller, 
     * or zero if they are equal.
     * @return  The comparison result
     */
    public int compareTo(Object target)
    {
        Key key2 = (Key)target;
        if (key2 == this) return 0;
        int size1 = this._size;
        int size2 = key2.getEncodedSize();
        
        byte[] bytes1 = this._bytes;
        byte[] bytes2 = key2.getEncodedBytes();
        int size = size1;
        if (size2 < size1) size = size2;
        for (int i = 0; i < size; i++)
        {
            int b1 = bytes1[i] & 0xFF;
            int b2 = bytes2[i] & 0xFF;
            if (b1 != b2) return b1 - b2;
        }
        if (size1 > size) return Integer.MAX_VALUE;
        if (size2 > size1) return Integer.MIN_VALUE;
        return 0;
    }

    /**
     * Compares a bounded subarray of the backing byte array for this
     * <tt>Key</tt> with another <tt>Key</tt>.  This method is intended
     * for use within the library and is generally not useful for applications.
     * @param key           The <tt>Key</tt> to compare.
     * @param fragmentStart Index of first byte to compare
     * @param fragmentSize  The number of bytes to compare.
     * @return              A positive value if this <tt>Key</tt>'s fragment
     *                      is larger than, a negative value if this 
     *                      <tt>Key</tt>'s fragment is smaller than, or 0 if
     *                      this <tt>Key</tt>'s fragment is equal to the
     *                      corresponding fragment of the supplied <tt>Key</tt>.
     */
    public int compareKeyFragment(Key key, int fragmentStart, int fragmentSize)
    {
        if (key == this) return 0;
        int size1= this._size;
        int size2 = key.getEncodedSize();
        byte[] bytes1 = this.getEncodedBytes();
        byte[] bytes2 = key.getEncodedBytes();
         
        int size = size1;
        if (size2 < size) size = size2;
        if (size > fragmentSize + fragmentStart)
        {
            size = fragmentSize + fragmentStart;
        } 
        for (int i = fragmentStart; i < size; i++)
        {
            int b1 = bytes1[i] & 0xFF;
            int b2 = bytes2[i] & 0xFF;
            if (b1 != b2) return b1 - b2;
        }
        if (size == fragmentSize + fragmentStart) return 0;
        if (size1 > size) return Integer.MAX_VALUE;
        if (size2 > size) return Integer.MIN_VALUE;
        return 0;
    }
    
    /**
     * Returns the index of the first encoded byte of this key that is different
     * than the corresponding byte of the supplied <tt>Key</tt>.  If all bytes
     * match, then returns the encoded length of the shorter key.
     * 
     * @param key   The code on which to count matching bytes
     * 
     * @return      The index of the first byte of this key that is unequal
     *              to the byte in the corresponding position of the supplied
     *              <tt>Key</tt>.
     */
    public int firstUniqueByteIndex(Key key)
    {
        int end = _size;
        if (end > key._size) end = key._size;
        for (int index = 0; index < end; index++)
        {
            if (key._bytes[index] != _bytes[index]) return index;
        }
        return end;
    }
    
    /**
     * Sets the current location and size of this <tt>Key</tt> to zero,
     * effectively removing any previously appended key segments.
     * @return  This <tt>Key</tt>, to permit method call chaining
     */
    public Key clear()
    {
        _size = 0;
        _depth = 0;
        _index = 0;
        bumpGeneration();
        return this;
    }
    
    void clear(boolean secure)
    {
        if (secure) Util.clearBytes(_bytes, 0, _bytes.length);
        clear();
    }

    /**
     * Truncates this <tt>Key</tt> to the specified <tt>depth</tt>.  
     * If <tt>depth</tt> is 0 then this method is equivalent to 
     * {@link #clear}.  If <tt>depth</tt> is positive, then this 
     * <tt>Key</tt> is truncated so that it has no more than <tt>depth</tt>
     * key segments.  If <tt>depth</tt> is negative, then up to
     * <tt>-depth</tt> segments are removed from the end.  For example,
     * <blockquote><pre>
     *      key.clear().append("a").append("b").append("c").setDepth(-1);
     * </pre></blockquote>
     * results in a key with two segment, "a" and "b".
     * @param   depth   The depth, as defined above.
     * @return  This <tt>Key</tt>, to permit method call chaining
     */
    public Key setDepth(int depth)
    {
        if (depth == 0) return clear();
        indexTo(depth);
        _size = _index;
        _index = 0;
        _depth = -1;
        bumpGeneration();
        return this;
    }
    
    /**
     * Sets the index to 0. (The <i>index</i>
     * is the location within the backing byte array from which the next
     * {@link #decode} operation will decode a segment value.)  This method
     * is equivalent to {@link #indexTo(int) indexTo(0)}.
     * @return  This <tt>Key</tt>, to permit method call chaining
     */
    public Key reset()
    {
        _index = 0;
        return this;
    }
    
    /**
     * Sets the index to a specified <tt>depth</tt>.  (The <i>index</i>
     * is the location within the backing byte array from which the next
     * {@link #decode} operation will decode a segment value.)
     * If <tt>depth</tt> is 0 then this method is equivalent to 
     * {@link #reset}.  If <tt>depth</tt> is positive, then the index is 
     * set to point to the <tt>depth</tt>th key segment.
     * If <tt>depth</tt> is negative, then the index is set to point to
     * a key segment <tt>-depth</tt> segments from the end.  For example,
     * <blockquote><pre>
     *      key.clear().append("a").append("b").append("c");
     *      return key.indexTo(2).indexTo(-1).decode();
     * </pre></blockquote>
     * returns "b".
     * @param   depth   The depth, as defined above.
     * @return  This <tt>Key</tt>, to permit method call chaining
     */
    public Key indexTo(int depth)
    {
        int index = 0;
        if (depth < 0)
        {
            index = -1;
            for (int i = 0; i > depth; i--)
            {
                if ((index = previousElementIndex(index)) == -1) break;
            }
            if (index == -1) _index = 0;
            else _index = index;
        }
        else if (depth > 0)
        {
            for (int i = 0; i < depth && index != -1; i++)
            {
                index = nextElementIndex(index);
            }
            if (index == -1) _index = _size;
            else _index = index;
        }
        else _index = 0;
        return this;
    }

    /**
     * Returns the index of from which the next invocation of {@link #decode}
     * will decode a key segment.
     * @return      The index
     */    
    public int getIndex()
    {
        return _index;
    }
    
    /**
     * Remove one key segment value from the end of this <tt>Key</tt>.  If
     * the key is empty this method does nothing.
     * @return  This <tt>Key</tt>, to permit method call chaining
     */
    public Key cut()
    {
        return cut(1);
    }
    
    /**
     * Remove up to <tt>count</tt> key segment values from the end of this
     * <tt>Key</tt>.  For example, the code fragment
     * <blockquote><pre>
     *      key.clear();
     *      key.append("a").append("b").append("c");
     *      key.cut(2).append("d");
     * </pre></blockquote>
     * leaves a key with the just the segment values {"a","d"}.
     * @param count     The number of key segments to cut.
     * @return  This <tt>Key</tt>, to permit method call chaining
     */
    public Key cut(int count)
    {
        int index = _size;
        for (int i = 0; i < count && index > 0; i++)
        {
            index = previousElementIndex(index);
            if (index == -1)
            {
                throw new IllegalArgumentException(
                    "Attempting to remove missing elements");
            }
        }
        if (index <= 0)
        {
            clear();
        }
        else
        {
            _size = index;
            if (_depth == -1) recomputeCurrentDepth();
            else _depth -= count;
        }
        bumpGeneration();
        return this;
    }
    
    /**
     * Returns a displayable String representation of the content of this
     * <tt>Key</tt>
     * @return  A displayable String
     */
    public String toString()
    {
        if (_size == 0)
        {
            return "{}";
        }
        StringBuffer sb = new StringBuffer("{");
        int index = _index;
        _index = 0;
        try
        {
            for (int depth = 0; _index < _size; depth++)
            {
                if (depth > 0) sb.append(",");
                decodeDisplayable(true, sb);
            }
            sb.append("}");
            return sb.toString();
        }
        catch(Exception e)
        {
            return e + "(size=" + _size + ") " +
                   Util.hexDump(_bytes, 0, _size);
        }
        finally
        {
            _index = index;
        }
    }
    
    /**
     * Encodes and appends a boolean value to the key.
     * @param v     The boolean value to append
     * @return  This <tt>Key</tt>, to permit method call chaining
     */
    public Key append(boolean v)
    {
        testValidForAppend();
        int size = _size;
        _bytes[size++] = v ? (byte)TYPE_BOOLEAN_TRUE : (byte)TYPE_BOOLEAN_FALSE;
        
        return endSegment(size);
    }
    
    /*
     * Encoding of integers:
     * 
     * For each primitive signed integer type (byte, short, int, long) we
     * want all the collation order of the encoded keys to match the 
     * natural numeric order.  We also want to elide leading zeros 
     * so that the encoding is compact for small integers.  Unlike the former
     * implementation, the encoded form preserves the original type information.
     * This means that if you encode a short into a key, it's natural decoding
     * is as a short.  (The decoding methods perform type conversions as
     * needed.)  Where this really matters is when the key value is treated
     * as an object.  If you encode a Short into the key, and then decode
     * an Object value, the decoded value will be a Short.  This permits
     * a correct implementation of the Map interface. 
     * 
     * For each of these types, the first byte contains the type, the
     * sign and the "scale" of the integer.  We'll call this the TSS byte.
     * Following this lead-in byte there will be zero or more bytes containing
     * the bits of the integer (except the sign bit) in an encoded form.
     * 
     * Here's how the encoding works.  Starting from the least significant bit
     * of the integer, group the N-1 non-sign bits into seven-bit fields.
     * For example, an integer has 32 bits, of which the most significant bit
     * (b31) is the sign bit.  So we divide bits b0-b30 into 7 bit fields as
     * follows:
     * 
     *      b0-b6   field0
     *      b7-b13  field1
     *      b14-20  field2
     *      b21-27  field3
     *      b28-31  field4
     * 
     * We then determine the highest numbered field that is non-zero.  For
     * example, if the integer value is 123, then only the bits in b0-b6 are
     * non-zero, so fields 1, 2, 3 and 4 are all zero.  We will call the number
     * of zero-valued fields the "scale", and this value is represented in
     * the TSS byte.  Following the TSS bytes are bytes containing the field
     * values, OR'ed with 0x80 so that no field byte is zero.  (For
     * negative integers, the field encoding is 0x80 OR'ed with the ones
     * complement of the field value.
     * 
     * The TSS is encoded as follows:
     * 
     *      TYPE value + (nn * EWIDTH) + scaleEncoding
     * 
     * where
     * 
     *      - TYPE is a different constant for each integer width,
     *      - nn is 1 for non-negative integers and zero for negative integers,
     *      - EWIDTH is a constant that varies by the integer width
     *          (wider integers require a larger value of EWIDTH)
     *      - scaleEncoding encodes the scale value in such a
     *          way that negative and positive integers collate correctly -
     * 
     *          nn=1:    EWIDTH - scale
     *          nn=0:    scale
     * 
     * For example, the TYPE and EWIDTH for an integer are 16 and 5,
     * respectively, so the encoded TSS value for 123 is:
     *
     *      nn = 1, field4...field1 = 0, field0 is non-zero, so scale is 4.
     * 
     * Therefore the TSS encoding is:
     * 
     *      16 + (1 * 5) + (5 - 4) = 22.
     * 
     * For -123 it is:
     * 
     *      nn = 0, field4...field1 = 0, field0 is non-zero, so scale is 4.
     * 
     * Therefore the TSS encoding is:
     * 
     *      16 + (0 * 5) + 4 = 20.
     * 
     * Note that negative integers of larger magnitude will have a smaller TSS
     * value and will therefore collate earlier, while positive integers of
     * greater magnitude will have greater TSS values and will therefore
     * collate after smaller positive integers.
     * 
     * type     TYPE        EWIDTH      TSS range
     * ----     ----        ------      ---------
     * byte     4           1           4...5       
     * short    6           3           6...11
     * char     9           3           12...15
     * int      16          5           16...25
     * long     26          9           26...43
     */
    /**
     * Encodes and appends a byte value to the key.
     * @param v     The byte value to append
     * @return  This <tt>Key</tt>, to permit method call chaining
     */
    public Key append(byte v)
    {
        testValidForAppend();
        int size = _size;
        if (v > 0)
        {
            _bytes[size++] = TYPE_BYTE + EWIDTH_BYTE + 1;
            _bytes[size++] = (byte)(0x80 | v);
        }
        else if (v < 0)
        {
            _bytes[size++] = TYPE_BYTE;
            _bytes[size++] = (byte)(0x80 | v);
        }
        else    //v == 0
        {
            _bytes[size++] = TYPE_BYTE + EWIDTH_BYTE;
        }
        // Close out the segment.
        
        return endSegment(size);
    }
    
    /**
     * Encodes and appends a short value to the key.
     * @param v     The short value to append
     * @return  This <tt>Key</tt>, to permit method call chaining
     */
    public Key append(short v)
    {
        testValidForAppend();
        int size = _size;
        if (v >= 0)
        {
            int scale = 3;
            if      (v > 0x3FFF) scale = 0;
            else if (v > 0x007F) scale = 1;
            else if (v > 0x0000) scale = 2;
            _bytes[size++] = (byte)(TYPE_SHORT + EWIDTH_SHORT * 2 - scale);
            switch(scale)
            {
                // control falls through intentionally
                case 0: _bytes[size++] = (byte)(0x80 | (v >>> 14));
                case 1: _bytes[size++] = (byte)(0x80 | (v >>> 7));
                case 2: _bytes[size++] = (byte)(0x80 |  v);
            }
        }
        else
        {
            int scale = 2;
            if      (v < -0x3FFF) scale = 0;
            else if (v < -0x007F) scale = 1;
            _bytes[size++] = (byte)(TYPE_SHORT + scale);
            switch(scale)
            {
                // control falls through intentionally
                case 0: _bytes[size++] = (byte)(0x80 | (v >>> 14));
                case 1: _bytes[size++] = (byte)(0x80 | (v >>> 7));
                case 2: _bytes[size++] = (byte)(0x80 |  v);
            }
        }
        // Close out the segment.
        
        return endSegment(size);
    }

    /**
     * Encodes and appends a char value to the key.
     * @param v     The char value to append
     * @return  This <tt>Key</tt>, to permit method call chaining
     */
    public Key append(char v)
    {
        testValidForAppend();
        int size = _size;
        int scale = 3;
        if      (v > 0x3FFF) scale = 0;
        else if (v > 0x007F) scale = 1;
        else if (v > 0x0000) scale = 2;
        _bytes[size++] = (byte)(TYPE_CHAR + EWIDTH_CHAR - scale);
        switch(scale)
        {
            // control falls through intentionally
            case 0: _bytes[size++] = (byte)(0x80 | (v >>> 14));
            case 1: _bytes[size++] = (byte)(0x80 | (v >>> 7));
            case 2: _bytes[size++] = (byte)(0x80 |  v);
        }
        
        return endSegment(size);
    }

    /**
     * Encodes and appends an int value to the key.
     * @param v     The int value to append
     * @return  This <tt>Key</tt>, to permit method call chaining
     */
    public Key append(int v)
    {
        testValidForAppend();
        int size = appendIntInternal(v);
        // Close out the segment.
        
        return endSegment(size);
    }
    
    /**
     * Encodes an int into a supplied byte array.
     * @param v
     * @param bytes
     * @param offset
     * @return
     */
    private int appendIntInternal(int v)
    {
        int size = _size;
        if (v >= 0)
        {
            int scale;
            if      (v > 0x0FFFFFFF) scale = 0;
            else if (v > 0x001FFFFF) scale = 1;
            else if (v > 0x00003FFF) scale = 2;
            else if (v > 0x0000007F) scale = 3;
            else if (v > 0x00000000) scale = 4;
            else scale = 5;
            _bytes[size++] = (byte)(TYPE_INT + EWIDTH_INT * 2 - scale);
            switch(scale)
            {
                // control falls through intentionally
                case 0: _bytes[size++] = (byte)(0x80 | (v >>> 28));
                case 1: _bytes[size++] = (byte)(0x80 | (v >>> 21));
                case 2: _bytes[size++] = (byte)(0x80 | (v >>> 14));
                case 3: _bytes[size++] = (byte)(0x80 | (v >>>  7));
                case 4: _bytes[size++] = (byte)(0x80 |  v);
                case 5:
            }
        }
        else    //v < 0
        {
            int scale;
            if      (v < -0x0FFFFFFF) scale = 0;
            else if (v < -0x001FFFFF) scale = 1;
            else if (v < -0x00003FFF) scale = 2;
            else if (v < -0x0000007F) scale = 3;
            else scale = 4;
            _bytes[size++] = (byte)(TYPE_INT + scale);
            switch(scale)
            {
                // control falls through intentionally
                case 0: _bytes[size++] = (byte)(0x80 | (v >>> 28));
                case 1: _bytes[size++] = (byte)(0x80 | (v >>> 21));
                case 2: _bytes[size++] = (byte)(0x80 | (v >>> 14));
                case 3: _bytes[size++] = (byte)(0x80 | (v >>>  7));
                case 4: _bytes[size++] = (byte)(0x80 |  v);
            }
        }
        return size;
    }
    
    /**
     * Encodes and appends a long value to the key.
     * @param v     The long value to append
     * @return  This <tt>Key</tt>, to permit method call chaining
     */
    public Key append(long v)
    {
        testValidForAppend();
        int size = appendLongInternal(v);
        return endSegment(size);
    }

    private int appendLongInternal(long v)
    {
        int size = _size;
        if (v >= 0)
        {
            int scale = 9;
            if      (v > 0x00FFFFFFFFFFFFFFL) scale = 0;
            else if (v > 0x0001FFFFFFFFFFFFL) scale = 1;
            else if (v > 0x000003FFFFFFFFFFL) scale = 2;
            else if (v > 0x00000007FFFFFFFFL) scale = 3;
            else if (v > 0x000000000FFFFFFFL) scale = 4;
            else if (v > 0x00000000001FFFFFL) scale = 5;
            else if (v > 0x0000000000003FFFL) scale = 6;
            else if (v > 0x000000000000007FL) scale = 7;
            else if (v > 0x00000000000000000) scale = 8;
            _bytes[size++] = (byte)(TYPE_LONG + EWIDTH_LONG * 2 - scale);
            switch(scale)
            {
                // control falls through intentionally
                case 0: _bytes[size++] = (byte)(0x80 | (v >>> 56));
                case 1: _bytes[size++] = (byte)(0x80 | (v >>> 49));
                case 2: _bytes[size++] = (byte)(0x80 | (v >>> 42));
                case 3: _bytes[size++] = (byte)(0x80 | (v >>> 35));
                case 4: _bytes[size++] = (byte)(0x80 | (v >>> 28));
                case 5: _bytes[size++] = (byte)(0x80 | (v >>> 21));
                case 6: _bytes[size++] = (byte)(0x80 | (v >>> 14));
                case 7: _bytes[size++] = (byte)(0x80 | (v >>>  7));
                case 8: _bytes[size++] = (byte)(0x80 |  v);
            }
        }
        else
        {
            int scale = 8;
            if      (v < -0x00FFFFFFFFFFFFFFL) scale = 0;
            else if (v < -0x0001FFFFFFFFFFFFL) scale = 1;
            else if (v < -0x000003FFFFFFFFFFL) scale = 2;
            else if (v < -0x00000007FFFFFFFFL) scale = 3;
            else if (v < -0x000000000FFFFFFFL) scale = 4;
            else if (v < -0x00000000001FFFFFL) scale = 5;
            else if (v < -0x0000000000003FFFL) scale = 6;
            else if (v < -0x000000000000007FL) scale = 7;
            _bytes[size++] = (byte)(TYPE_LONG + scale);
            switch(scale)
            {
                case 0: _bytes[size++] = (byte)(0x80 | (v >>> 56));
                case 1: _bytes[size++] = (byte)(0x80 | (v >>> 49));
                case 2: _bytes[size++] = (byte)(0x80 | (v >>> 42));
                case 3: _bytes[size++] = (byte)(0x80 | (v >>> 35));
                case 4: _bytes[size++] = (byte)(0x80 | (v >>> 28));
                case 5: _bytes[size++] = (byte)(0x80 | (v >>> 21));
                case 6: _bytes[size++] = (byte)(0x80 | (v >>> 14));
                case 7: _bytes[size++] = (byte)(0x80 | (v >>>  7));
                case 8: _bytes[size++] = (byte)(0x80 |  v);
            }
        }
        return size;
    }
    
    /**
     * Encodes and appends a float value to the key.
     * @param v     The float value to append
     * @return  This <tt>Key</tt>, to permit method call chaining
     */
    public Key append(float v)
    {
        testValidForAppend();
        int bits = Float.floatToIntBits(v);
        int size = _size;
        _bytes[size++] = TYPE_FLOAT;
        if (bits < 0)
        {
            bits = ~bits;
        }
        else
        {
            bits ^= 0x80000000;
        }
        while (bits != 0)
        {
            _bytes[size++] = (byte)(0x80 | (bits >> 25));
            bits <<= 7; 
        }
        
        return endSegment(size);
    }
    
    /**
     * Encodes and appends a double value to the key.
     * @param v     The double value to append
     * @return  This <tt>Key</tt>, to permit method call chaining
     */
    public Key append(double v)
    {
        testValidForAppend();
        long bits = Double.doubleToLongBits(v);
        int size = _size;
        _bytes[size++] = TYPE_DOUBLE;
        if (bits < 0)
        {
            bits = ~bits;
        }
        else
        {
            bits ^= 0x8000000000000000L;
        }
        while (bits != 0)
        {
            _bytes[size++] = (byte)(0x80 | (bits >> 57)) ;
            bits <<= 7; 
        }
        
        return endSegment(size);
    }
    
    /**
     * Encodes and appends an <tt>Object</tt> value to the key.  Only objects
     * of certain classes can be encoded in a <tt>Key</tt> (see
     * <a href="#_ObjectEncoding">Object Encoding</a> for details). This method
     * throws a <tt>ConversionException</tt> for unsupported types.
     *  
     * @param object  The object to append
     * 
     * @return  This <tt>Key</tt>, to permit method call chaining
     * 
     * @throws  ConversionException if the supplied object is not an
     *          implicitly supported type and does not have a {@link KeyCoder}.
     */
    public Key append(Object object)
    {
        return append(object, null);
    }
    
    /**
     * Encodes and appends an <tt>Object</tt> value to the key.  Only objects
     * of certain classes can be encoded in a <tt>Key</tt> (see
     * <a href="#_ObjectEncoding">Object Encoding</a> for details). This method
     * throws a <tt>ConversionException</tt> for unsupported types.
     *  
     * @param object    The object to append
     * 
     * @param context   An application-specified value that may assist a
     *                  {@link KeyCoder}.  The context is passed to the
     *                  {@link KeyCoder#appendKeySegment} method.
     *  
     * @return  This <tt>Key</tt>, to permit method call chaining
     * 
     * @throws  ConversionException if the supplied object is not an
     *          implicitly supported type and does not have a {@link KeyCoder}.
     */
    public Key append(Object object, CoderContext context)
    {
        testValidForAppend();
        if (object == null)
        {
            return appendNull();
        }
        else if (object == BEFORE)
        {
            return appendBefore();
        }
        else if (object == AFTER)
        {
            return appendAfter();
        }
        
        Class cl = object.getClass();
        
        if (cl == String.class)
        {
            return appendString((String)object, context);
        }
        
        if (cl == StringBuffer.class)
        {
            return appendString((StringBuffer)object, context);
        }
        
        if (cl == Boolean.class)
        {
            return append(((Boolean)object).booleanValue());
        }
        
        if (cl == Byte.class)
        {
            return append(((Byte)object).byteValue());
        }
        
        if (cl == Short.class)
        {
            return append(((Short)object).shortValue());
        }
        
        if (cl == Character.class)
        {
            return append(((Character)object).charValue());
        }
        
        if (cl == Integer.class)
        {
            return append(((Integer)object).intValue());
        }
        
        if (cl == Long.class)
        {
            return append(((Long)object).longValue());
        }
        
        if (cl == Float.class)
        {
            return append(((Float)object).floatValue());
        }
        
        if (cl == Double.class)
        {
            return append(((Double)object).doubleValue());
        }
        
        if (cl == byte[].class)
        {
            return appendByteArray((byte[])object, 0, ((byte[])object).length);
        }
        
        if (cl == Date.class)
        {
            return appendDate((Date)object);
        }

        if (cl == BigInteger.class)
        {
            return appendBigInteger((BigInteger)object);
        }

        if (cl == BigDecimal.class)
        {
            return appendBigDecimal((BigDecimal)object);
        }
        
        KeyCoder coder = _persistit.lookupKeyCoder(cl);
        {
            if (coder != null)
            {
                return appendByKeyCoder(object, cl, coder, context);
            }
        }
        
        throw new ConversionException(
            "Object class " + object.getClass().getName() + 
            " can't be used in a Key");
    }
    
    /**
     * Replaces the final key segment with the supplied boolean
     * value. If the key is currently empty, this method simply appends the
     * value. This method is equivalent to invoking {@link #cut} and then
     * {@link #append(boolean)}.
     * @param v     The boolean value to append
     * @return  This <tt>Key</tt>, to permit method call chaining
     */
    public Key to(boolean v)
    {
        cut();
        return append(v);
    }
    
    /**
     * Replaces the final key segment with the supplied byte
     * value. If the key is currently empty, this method simply appends the
     * value. This method is equivalent to invoking {@link #cut} and then
     * {@link #append(byte)}.
     * @param v     The byte value to append
     * @return  This <tt>Key</tt>, to permit method call chaining
     */
    public Key to(byte v)
    {
        cut();
        return append(v);
    }
    
    /**
     * Replaces the final key segment with the supplied short
     * value. If the key is currently empty, this method simply appends the
     * value. This method is equivalent to invoking {@link #cut} and then
     * {@link #append(short)}.
     * @param v     The short value to append
     * @return  This <tt>Key</tt>, to permit method call chaining
     */
    public Key to(short v)
    {
        cut();
        return append(v);
    }
    
    /**
     * Replaces the final key segment with the supplied char
     * value. If the key is currently empty, this method simply appends the
     * value. This method is equivalent to invoking {@link #cut} and then
     * {@link #append(char)}.
     * @param v     The char value to append
     * @return  This <tt>Key</tt>, to permit method call chaining
     */
    public Key to(char v)
    {
        cut();
        return append(v);
    }
    
    /**
     * Replaces the final key segment with the supplied int
     * value. If the key is currently empty, this method simply appends the
     * value. This method is equivalent to invoking {@link #cut} and then
     * {@link #append(int)}.
     * @param v     The int value to append
     * @return  This <tt>Key</tt>, to permit method call chaining
     */
    public Key to(int v)
    {
        cut();
        return append(v);
    }
    
    /**
     * Replaces the final key segment with the supplied long
     * value. If the key is currently empty, this method simply appends the
     * value. This method is equivalent to invoking {@link #cut} and then
     * {@link #append(long)}.
     * @param v     The long value to append
     * @return  This <tt>Key</tt>, to permit method call chaining
     */
    public Key to(long v)
    {
        cut();
        return append(v);
    }
    
    /**
     * Replaces the final key segment with the supplied float
     * value. If the key is currently empty, this method simply appends the
     * value. This method is equivalent to invoking {@link #cut} and then
     * {@link #append(float)}.
     * @param v     The float value to append
     * @return  This <tt>Key</tt>, to permit method call chaining
     */
    public Key to(float v)
    {
        cut();
        return append(v);
    }
    
    /**
     * Replaces the final key segment with the supplied double
     * value. If the key is currently empty, this method simply appends the
     * value. This method is equivalent to invoking {@link #cut} and then
     * {@link #append(double)}.
     * @param v     The double value to append
     * @return  This <tt>Key</tt>, to permit method call chaining
     */
    public Key to(double v)
    {
        cut();
        return append(v);
    }
    
    /**
     * Replaces the final key segment with the supplied <tt>Object</tt>
     * value. If the key is currently empty, this method simply appends the
     * value. This method is equivalent to invoking {@link #cut} and then
     * {@link #append(Object)}. See {@link #append(Object)} for restrictions
     * on permissible Object values.
     * @param v     The Object value to append
     * @return  This <tt>Key</tt>, to permit method call chaining
     */
    public Key to(Object v)
    {
        cut();
        return append(v);
    }
    
    /**
     * Decodes the next key segment as a primitive boolean, advances the index
     * to the next key segment and returns the result.
     * @return  The boolean value
     * @throws  ConversionException if the next key segment value is not a
     *          boolean.
     */
    public boolean decodeBoolean()
    {
        int type = getTypeCode();
        boolean v;
        if (type == TYPE_BOOLEAN_FALSE)
        {
            v = false;
        }
        else if (type == TYPE_BOOLEAN_TRUE)
        {
            v = true;
        }
        else throw new ConversionException("Invalid boolean type " + type);
        _index = decodeEnd(_index + 1);
        return v;
    }
    
    /**
     * Decodes the next key segment as a primitive byte, advances the index
     * to the next key segment and returns the result.
     * @return  The byte value
     * @throws  ConversionException if the next key segment value is not a
     *          byte.
     */
    public byte decodeByte()
    {
        int type = getTypeCode();
        if (type >= TYPE_BYTE && type <= TYPE_BYTE + (EWIDTH_BYTE * 2))
        {
            return (byte)decodeInt();
        }
        throw new ConversionException("Invalid byte type " + type);
    }
    
    /**
     * Decodes the next key segment as a primitive short, advances the index
     * to the next key segment and returns the result.
     * @return  The short value
     * @throws  ConversionException if the next key segment value is not a
     *          short.
     */
    public short decodeShort()
    {
        int type = getTypeCode();
        if (type >= TYPE_BYTE && type <= TYPE_SHORT + (EWIDTH_SHORT * 2))
        {
            return (short)decodeInt();
        }
        throw new ConversionException("Invalid short type " + type);
    }
    
    /**
     * Decodes the next key segment as a primitive char, advances the index
     * to the next key segment and returns the result.
     * @return  The char value
     * @throws  ConversionException if the next key segment value is not a
     *          char.
     */
    public char decodeChar()
    {
        int type = getTypeCode();
        if (type >= TYPE_CHAR && type <= TYPE_CHAR + EWIDTH_CHAR)
        {
            return (char)decodeInt();
        }
        throw new ConversionException("Invalid char type " + type);
    }
    
    /**
     * Decodes the next key segment as a primitive int, advances the index
     * to the next key segment and returns the result.
     * @return  The int value
     * @throws  ConversionException if the next key segment value is not a
     *          int.
     */
    public int decodeInt()
    {
        int index = _index;
        try
        {
            int result = decodeIntInternal();
            index = decodeEnd(_index);
            return result;
        }
        finally
        {
            _index = index;
        }
    }
    
    /**
     * Decodes the next key segment as a primitive int and returns the result, 
     * but does not advance the index to the next key segment.
     * @return  The int value
     * @throws  ConversionException if the next key segment value is not a
     *          int.
     */
    private int decodeIntInternal()
    {
        int type = getTypeCode();
        int index = _index + 1;
        int result = 0;
        switch(type)
        {
            case TYPE_BYTE + 0:
                result = (~_bytes[index++] & 0x7F);
                result = ~result;
                break;
                
            case TYPE_BYTE + EWIDTH_BYTE * 2 - 0:
                result = _bytes[index++] & 0x7F;
                // intentionally falls through; 
            case TYPE_BYTE + EWIDTH_BYTE * 2 - 1:
                break;
                
            case TYPE_SHORT + 0:
                result |= (~_bytes[index++] & 0x7F) << 14;
                // intentionally falls through; 
            case TYPE_SHORT + 1:
                result |= (~_bytes[index++] & 0x7F) << 7;
                // intentionally falls through; 
            case TYPE_SHORT + 2:
                result |= (~_bytes[index++] & 0x7F);
                result = ~result;
                break;
                
            case TYPE_SHORT + EWIDTH_SHORT * 2 - 0:
                result |= (_bytes[index++] & 0x7F) << 14;
                // intentionally falls through; 
            case TYPE_SHORT + EWIDTH_SHORT *2 - 1:
                result |= (_bytes[index++] & 0x7F) << 7;
                // intentionally falls through; 
            case TYPE_SHORT + EWIDTH_SHORT * 2 - 2:
                result |= (_bytes[index++] & 0x7F);
                // intentionally falls through; 
            case TYPE_SHORT + EWIDTH_SHORT * 2 - 3:
                break;
                
            case TYPE_CHAR + EWIDTH_CHAR - 0:
                result |= (_bytes[index++] & 0x7F) << 14;
                // intentionally falls through; 
            case TYPE_CHAR + EWIDTH_CHAR - 1:
                result |= (_bytes[index++] & 0x7F) << 7;
                // intentionally falls through; 
            case TYPE_CHAR + EWIDTH_CHAR - 2:
                result |= (_bytes[index++] & 0x7F);
                // intentionally falls through; 
            case TYPE_CHAR + EWIDTH_CHAR - 3:
                break;
                
            case TYPE_INT + 0:
                result |= (~_bytes[index++] & 0x7F) <<28;
                // intentionally falls through; 
            case TYPE_INT + 1:
                result |= (~_bytes[index++] & 0x7F) <<21;
                // intentionally falls through; 
            case TYPE_INT + 2:
                result |= (~_bytes[index++] & 0x7F) <<14;
                // intentionally falls through; 
            case TYPE_INT + 3:
                result |= (~_bytes[index++] & 0x7F) << 7;
                // intentionally falls through; 
            case TYPE_INT + 4:
                result |= (~_bytes[index++] & 0x7F);
                result = ~result;
                break;
                 
            case TYPE_INT + EWIDTH_INT * 2 - 0:
                result |= (_bytes[index++] & 0x7F) << 28;
                // intentionally falls through; 
            case TYPE_INT + EWIDTH_INT * 2 - 1:
                result |= (_bytes[index++] & 0x7F) << 21;
                // intentionally falls through; 
            case TYPE_INT + EWIDTH_INT * 2 - 2:
                result |= (_bytes[index++] & 0x7F) << 14;
                // intentionally falls through; 
            case TYPE_INT + EWIDTH_INT * 2 - 3:
                result |= (_bytes[index++] & 0x7F) <<  7;
                // intentionally falls through; 
            case TYPE_INT + EWIDTH_INT * 2 - 4:
                result |= (_bytes[index++] & 0x7F);
                // intentionally falls through; 
            case TYPE_INT + EWIDTH_INT * 2 - 5:
                break; 
            
            default:
                throw new ConversionException("Invalid integer type " + type);
        }
        
        _index = index;
        return result;
    }
    
    
    /**
     * Decodes the next key segment as a primitive long, advances the index
     * to the next key segment and returns the result.
     * @return  The long value
     * @throws  ConversionException if the next key segment value is not a
     *          long.
     */
    public long decodeLong()
    {
        int index = _index;
        try
        {
            long result = decodeLongInternal();
            index = decodeEnd(_index);
            return result;
        }
        finally
        {
            _index = index;
        }
    }
    
    /**
     * Decodes the next key segment as a primitive long and returns the result, 
     * but does not advance the index to the next key segment.
     * @return  The int value
     * @throws  ConversionException if the next key segment value is not a
     *          int.
     */
    private long decodeLongInternal()
    {
        int type = getTypeCode();
        int index = _index + 1;
        if (type >= TYPE_BYTE && type < TYPE_LONG) return decodeIntInternal();
        long result = 0;
        switch(type)
        {
            case TYPE_LONG + 0:
                result |= (~_bytes[index++] & 0x7FL) << 56;
                // intentionally falls through; 
            case TYPE_LONG + 1:
                result |= (~_bytes[index++] & 0x7FL) << 49;
                // intentionally falls through; 
            case TYPE_LONG + 2:
                result |= (~_bytes[index++] & 0x7FL) << 42;
                // intentionally falls through; 
            case TYPE_LONG + 3:
                result |= (~_bytes[index++] & 0x7FL) << 35;
                // intentionally falls through; 
            case TYPE_LONG + 4:
                result |= (~_bytes[index++] & 0x7FL) << 28;
                // intentionally falls through; 
            case TYPE_LONG + 5:
                result |= (~_bytes[index++] & 0x7FL) << 21;
                // intentionally falls through; 
            case TYPE_LONG + 6:
                result |= (~_bytes[index++] & 0x7FL) << 14;
                // intentionally falls through; 
            case TYPE_LONG + 7:
                result |= (~_bytes[index++] & 0x7FL) <<  7;
                // intentionally falls through; 
            case TYPE_LONG + 8:
                result |= (~_bytes[index++] & 0x7FL);
                result = ~result;
                break;
                 
            case TYPE_LONG + EWIDTH_LONG * 2 - 0:
                result |= (_bytes[index++] & 0x7FL) << 56;
                // intentionally falls through; 
            case TYPE_LONG + EWIDTH_LONG * 2 - 1:
                result |= (_bytes[index++] & 0x7FL) << 49;
                // intentionally falls through; 
            case TYPE_LONG + EWIDTH_LONG * 2 - 2:
                result |= (_bytes[index++] & 0x7FL) << 42;
                // intentionally falls through; 
            case TYPE_LONG + EWIDTH_LONG * 2 - 3:
                result |= (_bytes[index++] & 0x7FL) << 35;
                // intentionally falls through; 
            case TYPE_LONG + EWIDTH_LONG * 2 - 4:
                result |= (_bytes[index++] & 0x7FL) << 28;
                // intentionally falls through; 
            case TYPE_LONG + EWIDTH_LONG * 2 - 5:
                result |= (_bytes[index++] & 0x7FL) << 21;
                // intentionally falls through; 
            case TYPE_LONG + EWIDTH_LONG * 2 - 6:
                result |= (_bytes[index++] & 0x7FL) << 14;
                // intentionally falls through; 
            case TYPE_LONG + EWIDTH_LONG * 2 - 7:
                result |= (_bytes[index++] & 0x7FL) <<  7;
                // intentionally falls through; 
            case TYPE_LONG + EWIDTH_LONG * 2 - 8:
                result |= (_bytes[index++] & 0x7FL);
                // intentionally falls through; 
            case TYPE_LONG + EWIDTH_LONG * 2 - 9:
                break; 
            
            default:
                throw new ConversionException("Invalid long type " + type);
        }
        _index = index;
        return result;
    }
    
    /**
     * Decodes the next key segment as a primitive float, advances the index
     * to the next key segment and returns the result.
     * @return  The float value
     * @throws  ConversionException if the next key segment value is not a
     *          float.
     */
    public float decodeFloat()
    {
        float result = 0.0F;
        int type = getTypeCode();
        int index = _index + 1;
        if (type == TYPE_FLOAT)
        {
            int bits = 0;
            int shift = 32;
            for(;;)
            {
                int b = (_bytes[index++]) & 0xFF;
                if (b == 0) break;
                if (shift == 0)
                {
                    throw new ConversionException(
                        "Unexpected float byte value " + b + 
                        " at " + (index - 1));
                }
                if (shift > 7)
                {
                    bits = (bits << 7) | (b & 0x7F);
                    shift -= 7;
                }
                else
                {
                    bits = (bits << shift) | ((b & 0x7F) >> (7 - shift));
                    shift = 0;
                }
            }
            if (shift > 0) bits <<= shift;
            if ((bits & 0x80000000) != 0)
            {
                bits ^=-0x80000000;
            }
            else
            {
                bits = ~bits;
            }
            result = Float.intBitsToFloat(bits);
        }
        else
        {
            throw new ConversionException(
                "Mismatched type " + type + " is not TYPE_FLOAT at " + _index);
        }
        _index = index;
        return result;
    }
    
    /**
     * Decodess the next key segment as a primitive double, advance the index
     * to the next key segment and returns the result.
     * @return  The double value
     * @throws  ConversionException if the next key segment value is not a
     *          double.
     */
    public double decodeDouble()
    {
        double result = 0.0F;
        int type = getTypeCode();
        int index = _index + 1;
        if (type == TYPE_FLOAT) return decodeFloat();
        if (type == TYPE_DOUBLE)
        {
            long bits = 0;
            int shift = 64;
            for(;;)
            {
                long b = (_bytes[index++]) & 0xFF;
                if (b == 0) break;
                if (shift == 0)
                {
                    throw new ConversionException(
                        "Unexpected float byte value " + b + 
                        " at " + (index - 1));
                }
                if (shift > 7)
                {
                    bits = (bits << 7) | (b & 0x7FL);
                    shift -= 7;
                }
                else
                {
                    bits = (bits << shift) | ((b & 0x7FL) >> (7 - shift));
                    shift = 0;
                }
            }
            if (shift > 0) bits <<= shift;
            if ((bits & 0x8000000000000000L) != 0)
            {
                bits ^=-0x8000000000000000L;
            }
            else
            {
                bits = ~bits;
            }
            result = Double.longBitsToDouble(bits);
        }
        else
        {
            throw new ConversionException(
                "Mismatched type " + type + " is not TYPE_DOUBLE at " + _index);
        }
        _index = index;
        return result;
    }
    
    /**
     * Decodes the next key segment as a <tt>String</tt>, advances the index
     * to the next key segment and returns the result. This method uses the
     * {@link KeyStringCoder} if there is one.
     * @return  The String value
     * @throws  ConversionException if the next key segment value is not a
     *          String.
     */
    public String decodeString()
    {
        StringBuffer sb = new StringBuffer();
        decodeString(false, sb, null);
        return sb.toString();
    }
    /**
     * Decodes the next key segment as a <tt>String</tt>, advances the index
     * to the next key segment and returns the result. This method uses the
     * {@link KeyStringCoder} if there is one.
     * @param context   An application-specified value that may assist a
     *                  {@link KeyStringCoder}.  The context is passed to the
     *                  {@link KeyStringCoder#decodeKeySegment} method. 
     * @return The String value
     * @throws ConversionException if the next key segment value is not a
     *          String.
     */
    public String decodeString(CoderContext context)
    {
        StringBuffer sb = new StringBuffer();
        decodeString(false, sb, context);
        return sb.toString();
    }
    
    /**
     * Decodes the next key segment as a <tt>String</tt>, appends the result
     * to the supplied <tt>StringBuffer</tt> and advance the index to the 
     * next key segment. This method uses the <tt>Key</tt>'s 
     * {@link KeyStringCoder} if there is one.
     * 
     * @param sb    The <tt>StringBuffer</tt>
     * 
     * @throws  ConversionException if the next key segment value is not a
     *          String.
     */
    public void decodeString(StringBuffer sb)
    {
        decodeString(false, sb, null);
    }
    
    /**
     * Decodes the next key segment as a <tt>String</tt>, appends the result
     * to the supplied <tt>StringBuffer</tt> and advances the index to the
     * next key segment. This method uses the <tt>Key</tt>'s 
     * {@link KeyStringCoder} if there is one.
     * 
     * @param sb        The <tt>StringBuffer</tt>
     * 
     * @param context   An application-specified value that may assist a
     *                  {@link KeyStringCoder}.  The context is passed to the
     *                  {@link KeyStringCoder#decodeKeySegment} method.
     *  
     * @throws  ConversionException if the next key segment value is not a
     *          String.
     */
    public void decodeString(StringBuffer sb, CoderContext context)
    {
        decodeString(false, sb, context);
    }
    
    /**
     * Decodes the next key segment as a <tt>java.util.Date</tt>, advances the 
     * index to the next key segment and returns the result.
     * @return  The Date value
     * @throws  ConversionException if the next key segment value is not a
     *          Date.
     */
    public Date decodeDate()
    {
        int index = _index;
        try
        {
            int type = getTypeCode();
            _index++;
            if (type != TYPE_DATE)
            {
                throw new ConversionException(
                    "Invalid Date lead-in byte (" + type +
                    ") at position " + _index + " in key");
            }
            Date result = new Date(decodeLongInternal());
            index = decodeEnd(_index);
            return result;
        }
        finally
        {
            _index = index;
        }
    }
    
    /**
     * Decodes the next key segment as a <tt>java.math.BigInteger</tt>, advances the 
     * index to the next key segment and returns the result.
     * @return  The BigInteger value
     * @throws  ConversionException if the next key segment value is not a
     *          BigInteger.
     */
    public BigInteger decodeBigInteger()
    {
        int index = _index;
        int type = getTypeCode();
        if (type != TYPE_BIG_INTEGER)
        {
            throw new ConversionException(
                "Invalid String lead-in byte (" + type +
                ") at position " + _index + " in key");
        }
        _index++;
        try
        {
            BigIntegerStruct bis = new BigIntegerStruct();
            decodeBigInteger(bis);
            index = decodeEnd(_index);
            return bis._bigInteger;
        }
        finally
        {
            _index = index;
        }
    }
    /**
     * Decodes the next key segment as a <tt>java.math.BigDecimal</tt>, advances the 
     * index to the next key segment and returns the result.
     * @return  The BigDecimal value
     * @throws  ConversionException if the next key segment value is not a
     *          BigDecimal.
     */
    public BigDecimal decodeBigDecimal()
    {
        int index = _index;
        int type = getTypeCode();
        if (type != TYPE_BIG_DECIMAL)
        {
            throw new ConversionException(
                "Invalid String lead-in byte (" + type +
                ") at position " + _index + " in key");
        }
        _index++;
        try
        {
            BigIntegerStruct bis = new BigIntegerStruct();
            decodeBigInteger(bis);
            index = decodeEnd(_index);
            BigDecimal result = new BigDecimal(bis._bigInteger, bis._scale);
            int rescale = bis._scale - bis._zeroCount;
            if (bis._zeroCount > 0 && rescale > 0)
            {
                result = result.setScale(rescale);
            }
            return result;
        }
        finally
        {
            _index = index;
        }
    }
    /**
     * Decodes the next key segment as an array of bytes, advances the index
     * to the next key segment and returns the result.
     * @return  The boolean value
     * @throws  ConversionException if the next key segment value is not a
     *          boolean.
     */
    public byte[] decodeByteArray()
    {
        int index = _index;
        int type = getTypeCode();
        try
        {
            if (type != TYPE_BYTE_ARRAY)
            {
                throw new ConversionException(
                    "Invalid String lead-in byte (" + type +
                    ") at position " + _index + " in key");
            }
            int size = unquoteNulls(index + 1);
            byte[] result = new byte[size];
            System.arraycopy(_bytes, index  + 1, result, 0, size);
            index += quoteNulls(index + 1, size) + 2;        
            return result;
        }
        finally
        {
            _index = index;
        }
    }
    
    /**
     * Decodes the next key segment as an <tt>Object</tt>, advances the index
     * to the next key segment and returns the result.
     * @return  The Object value, or <tt>null</tt> if the encoded value is null.
     * @throws  ConversionException if the next key segment value is not a
     *          boolean.
     */
    public Object decode()
    {
        return decode(null, null);
    }
    
    /**
     * <p>
     * Decodes the next key segment as an <tt>Object</tt>, advances the index
     * to the next key segment and returns the result. 
     * </p>
     * If the value encoded in this <tt>Value</tt> is null then this
     * method returns <tt>null</tt>. Otherwise this method returns
     * either (a) a new object instance, or (b) the target object.
     * Specifically, if the supplied target is non-null, and if the class of
     * the object encoded in the <tt>Value</tt> is supported by a registered
     * {@link KeyRenderer}, then the target object will be returned after the
     * <tt>KeyRenderer</tt> has populated its state. Otherwise the target 
     * object will be ignored and this method will return a newly
     * created object instance.
     * </p> 
     * @param target    A mutable object into which this method may attempt to
     *                  decode the value
     * @return          An <tt>Object</tt> representing the key segment encoded
     *                  in the <tt>Key</tt>, or <tt>null</tt> if the encoded
     *                  value is null.
     */
    public Object decode(Object target)
    {
        return decode(target, null);
    }
    
    /**
     * <p>
     * Decodes the next key segment as an <tt>Object</tt>, advances the index
     * to the next key segment and returns the result. 
     * </p>
     * If the value encoded in this <tt>Value</tt> is null then this
     * method returns <tt>null</tt>. Otherwise this method returns
     * either (a) a new object instance, or (b) the target object.
     * Specifically, if the supplied target is non-null, and if the class of
     * the object encoded in the <tt>Value</tt> is supported by a registered
     * {@link KeyRenderer}, then the target object will be returned after the
     * <tt>KeyRenderer</tt> has populated its state. Otherwise the target 
     * object will be ignored and this method will return a newly
     * created object instance.
     * </p> 
     * @param target    A mutable object into which this method may attempt to
     *                  decode the value
     * 
     * @param context   An application-specified value that may assist a
     *                  {@link KeyCoder}.  The context is passed to the
     *                  {@link KeyCoder#decodeKeySegment} method.
     *  
     * @return          An <tt>Object</tt> representing the key segment encoded
     *                  in the <tt>Key</tt>, or <tt>null</tt> if the encoded
     *                  value is null.
     */
    public Object decode(Object target, CoderContext context)
    {
        int index = _index;
        int type = getTypeCode();

        if (type == TYPE_NULL)
        {
            return decodeNull();
        }
        
        if (type == TYPE_BOOLEAN_FALSE || type == TYPE_BOOLEAN_TRUE)
        {
            return decodeBoolean() ? Boolean.TRUE : Boolean.FALSE;
        }
        
        if (type >= TYPE_BYTE && type < TYPE_SHORT)
        {
            return new Byte((byte)decodeInt());
        }
        
        if (type >= TYPE_SHORT && type < TYPE_CHAR)
        {
            return new Short((short)decodeInt());
        }
        
        if (type >= TYPE_CHAR && type < TYPE_INT)
        {
            return new Character((char)decodeInt());
        }
        
        if (type >= TYPE_INT && type < TYPE_LONG)
        {
            return new Integer((int)decodeInt());
        }
        
        if (type >= TYPE_LONG && type < TYPE_FLOAT)
        {
            return new Long(decodeLong());
        }
        
        if (type == TYPE_FLOAT)
        {
            return new Float(decodeFloat());
        }
        
        if (type == TYPE_DOUBLE)
        {
            return new Double(decodeDouble());
        }
        
        if (type == TYPE_BYTE_ARRAY)
        {
            return decodeByteArray();
        }
        
        if (type == TYPE_STRING)
        {
            return decodeString();
        }
        
        if (type == TYPE_DATE)
        {
            return decodeDate();
        }
        
        if (type == TYPE_BIG_INTEGER)
        {
            return decodeBigInteger();
        }
        
        if (type == TYPE_BIG_DECIMAL)
        {
            return decodeBigDecimal();
        }
        
        if (type == TYPE_BEFORE || type == TYPE_AFTER)
        {
            return decodeBeforeAfter();
        }
        
        if (type >= TYPE_CODER_MIN && type <= TYPE_CODER_MAX)
        {
            return decodeByKeyCoder(target, context);
        }
        
        throw new ConversionException(
            "Invalid type " + type + " at index " + (index - 1));
    }
    
    /**
     * Decodes the <tt>Class</tt> of the next key segment.
     * @return  The <tt>Class</tt> of the next key segment
     * @throws  ConversionException if the encoded value is malformed.
     */
    public Class decodeType()
    {
        int type = getTypeCode();
        
        if (type >= TYPE_CODER_MIN && type <= TYPE_CODER_MAX)
        {
            int index = _index;
            try
            {
                int handle = decodeHandle();
                return
                    _persistit
                        .getClassIndex()
                        .lookupByHandle(handle)
                        .getDescribedClass();
            }
            finally
            {
                _index = index;
            }
        }
        
        return CLASS_PER_TYPE[type];
    }
    
    /**
     * Decodes the next key segment as a displayable String, advances the index
     * to the next key segment and returns the result.  This method is intended
     * to generate a reasonably legible String representation for any type of
     * key segment value.
     * @return  The boolean value
     * @throws  ConversionException if the next key segment value is not a
     *          boolean.
     */
    public String decodeDisplayable(boolean quoted)
    {
        StringBuffer sb = new StringBuffer();
        decodeDisplayable(quoted, sb);
        return sb.toString();
    }
    
    /**
     * Decodes the next key segment as a displayable String and advances the
     * index to the next key segment.    This method appends the decoded String
     * value to the supplied StringBuffer.  If <tt>quoted</tt> is true, and if
     * the segment value is a String, then the String value is surrounded by 
     * quote (") characters, and backslashes are inserted into the display 
     * string to quote any embedded any backslash or quote characters.  This 
     * method is intended to generate a human-readable, canonical String 
     * representation for any type of key segment value.
     * @param   quoted      <tt>true</tt> if the resulting string is to be
     *                      quoted.
     * @param   sb          The <tt>StringBuffer</tt> to which the displayable
     *                      String is to be appended.    
     * @throws  ConversionException if the next key segment value is not a
     *          boolean.
     */
    public void decodeDisplayable(boolean quoted, StringBuffer sb)
    {
        decodeDisplayable(quoted, sb, null);
    }
    
    /**
     * Decodes the next key segment as a displayable String and advances the
     * index to the next key segment.    This method appends the decoded String
     * value to the supplied StringBuffer.  If <tt>quoted</tt> is true, and if
     * the segment value is a String, then the String value is surrounded by 
     * quote (") characters, and backslashes are inserted into the display 
     * string to quote any embedded any backslash or quote characters.  This 
     * method is intended to generate a human-readable, canonical String 
     * representation for any type of key segment value.
     * @param quoted    <tt>true</tt> if the resulting string is to be
     *                  quoted.
     * @param sb        The <tt>StringBuffer</tt> to which the displayable
     *                  string is to be appended.
     * @param context   An application-specified value that may assist a
     *                  {@link KeyStringCoder}.  The context is passed to the
     *                  {@link KeyStringCoder#renderKeySegment} method. 
     * @throws  ConversionException if the next key segment value is not a
     *          boolean.
     */
    public void decodeDisplayable(boolean quoted, StringBuffer sb, CoderContext context)
    {
        if (_index >= _size) return;
        
        int type = _bytes[_index] & 0xFF;
        
        // Handle special types.
        switch(type)
        {
            case TYPE_LEFT_EDGE:
                sb.append("{left edge}");
                _index++;
                return;
                    
            case TYPE_RIGHT_EDGE:
                sb.append("{right edge}");
                _index++;
                return;
                
            case TYPE_BEFORE:
            case TYPE_AFTER:
                sb.append(decodeBeforeAfter());
                _index++;
                return;
                
            case TYPE_STRING:
                if (quoted) sb.append('\"');
                decodeString(quoted, sb, context);
                if (quoted) sb.append('\"');
                return;
                
            case TYPE_NULL:
                sb.append(decodeNull()); // let system define the string form
                return;
                
            case TYPE_BOOLEAN_FALSE:
            case TYPE_BOOLEAN_TRUE:
                sb.append(decodeBoolean()); // let system define the string form
                return;
                
                
            default:
        }
        
        if (type >= TYPE_CODER_MIN && type <= TYPE_CODER_MAX)
        {
            decodeDisplayableByKeyCoder(quoted, sb, context);
            return;
        }
                
        Class cl = CLASS_PER_TYPE[type];

        if (cl == Byte.class)
        {
            sb.append(PREFIX_BYTE);
            sb.append((int)decodeByte());
        }
        
        else if (cl == Short.class)
        {
            sb.append(PREFIX_SHORT);
            sb.append((int)decodeShort());
        }
        
        else if (cl == Character.class)
        {
//          PDB 20050728 - makes char elements prettier in the string
//                          representation, but needs changes in parser.
//            if (quoted)
//            {
//                sb.append('\'');
//                Util.appendQuotedChar(sb, decodeChar());
//                sb.append('\'');
//                return;
//            }
//            else
//            {
                sb.append(PREFIX_CHAR);
                sb.append((int)decodeChar());
//            }
        }

        else if (cl == Integer.class)
        {
            sb.append(decodeInt());
        }

        else if (cl == Long.class)
        {
            sb.append(PREFIX_LONG);
            sb.append(decodeLong());
        }
        
        else if (cl == Float.class)
        {
            sb.append(PREFIX_FLOAT);
            sb.append(decodeFloat());
        }
         
        else if (cl == Double.class)
        {
            sb.append(decodeDouble());
        }
        
        else if (cl == BigInteger.class)
        {
            sb.append(PREFIX_BIG_INTEGER);
            sb.append(decode());
        }

        else if (cl == BigDecimal.class)
        {
            sb.append(PREFIX_BIG_DECIMAL);
            sb.append(decode());
        }
        
        else if (cl == Boolean.class)
        {
            sb.append(decodeBoolean());
        }
         
        else if (cl == Date.class)
        {
            sb.append(PREFIX_DATE);
            sb.append(SDF.format(decodeDate()));
        }
        
        else if (cl == byte[].class)
        {
            sb.append(PREFIX_BYTE_ARRAY);
            sb.append(Util.bytesToHex(decodeByteArray()));
        }

        else
        {
            sb.append("(?)");
            Util.bytesToHex(sb, _bytes, _index, _size - _index);
            _index = _size;
        }
    }
    
    /**
     * An integer that is incremented every time the content of this Key
     * changes.  If the generation number is unchanged after an operation
     * this is a reliable indication that the <tt>Key</tt>'s value
     * did not change.  
     * @return  The generation
     */
    public long getGeneration()
    {
        return _generation;
    }
    
    /**
     * Determine if this is the special <tt>Key</tt> value that is stored
     * at the left edge of a <tt>Tree</tt>.  If so then there is no other
     * <tt>Key</tt> before this one in the <tt>Tree</tt>.
     * @return  <tt>true</tt> if the content of this <tt>Key</tt> represents the
     *      special left edge key of a <tt>Tree</tt>
     */
    public boolean isLeftEdge()
    {
        return _size == 1 && _bytes[0] == 0;
    }
    
    /**
     * Determine if this is the special <tt>Key</tt> value that is stored
     * at the right edge of a <tt>Tree</tt>.  If so then there is no other
     * <tt>Key</tt> after this one in the <tt>Tree</tt>.
     * @return  <tt>true</tt> if the content of this <tt>Key</tt> represents the
     *      special right edge key of a <tt>Tree</tt>
     */
    public boolean isRightEdge()
    {
        return _size == 1 && _bytes[0] == (byte)255;
    }
    
    
    /**
     * Advance the index to the beginning of the next key segment. If the
     * index is already at the end of the key, then wrap it to the beginning and
     * return -1.
     * @return  The new index
     */
    int nextElementIndex()
    {
        _depth = -1;
        _index = nextElementIndex(_index);
        return _index;
    }
    
    /**
     * Move the index to the beginning of the previous segment. If the
     * index is already at the beginning of the key, then wrap it to the
     * end and return -1.
     * @return  The new index
     */
    int previousElementIndex()
    {
        _index = previousElementIndex(_index);
        _depth = -1;
        return _index;
    }
    
    /**
     * Find the beginning of the next key segment and return its index.
     * Return -1 if the supplied index is at or past the end
     * of the key.
     * @param index The index to start from
     * @return  The resulting index
     */
    int nextElementIndex(int index)
    {
        if (index < 0) index = 0;
        for (int i = index; i < _size; i++)
        {
            if (_bytes[i] == 0) 
            {
                return i < _size ? i + 1 : -1;
            } 
        }
//        return index < _size ? _size : -1;    
        return -1;    
    }
    
    /**
     * Find the beginning of the previous key segment and return its index.
     * Return -1 if the supplied index is at or before the beginning
     * of the key.
     * @param index The index to start from
     * @return  The resulting index
     */
    int previousElementIndex(int index)
    {
        if (index < 0 || index > _size) index = _size;
        for (int i = index - 1; --i >= 0;)
        {
            if (_bytes[i] == 0)
            {
                return i + 1;
            } 
        }
        return index == 0 ? -1 : 0;
    }
    
    void bumpGeneration()
    {
        _generation++;
    }
    
    void testValidForAppend()
    {
        if (_size == 0 ||
            _size > 1 && _bytes[_size - 1] == 0) return;
        
        byte b = _bytes[_size - 1];
        if (_size == 1)
        {
            if (b == TYPE_LEFT_EDGE)
            {
                throw new IllegalArgumentException(
                    "append to LEFT_EDGE key");
            }
            if (b == TYPE_RIGHT_EDGE)
            {
                throw new IllegalArgumentException(
                    "append to RIGHT_EDGE key");
            }
        }
        if (b == TYPE_BEFORE || b == TYPE_AFTER)
        {
            throw new IllegalArgumentException(
                "append to BEFORE key or AFTER");
        }
        else
        {
            throw new IllegalArgumentException(
                "append to invalid final key segment: " + (b & 0xFF));
        }
    }
    
    void testValidForStore(int bufferSize)
    throws InvalidKeyException
    {
        if (_size == 0)
        {
            throw new InvalidKeyException(
                "Empty Key not permitted");
        }
        else if (_size > maxStorableKeySize(bufferSize))
        {
            throw new InvalidKeyException(
                "Key too long for buffer: " + _size);
        }
        byte b = _bytes[_size - 1];
        if (b == TYPE_BEFORE || b == TYPE_AFTER)
        {
            throw new InvalidKeyException(
                "BEFORE key or AFTER key not permitted");
        }
        else if (b != 0)
        {
            throw new InvalidKeyException(
                "Invalid key segment terminator " + (b & 0xFF));
        }
    }
    
    static int maxStorableKeySize(int bufferSize)
    {
        int result = bufferSize / 4;
        if (result > MAX_KEY_LENGTH) result = MAX_KEY_LENGTH;
        return result;
    }
    
    boolean isBefore()
    {
        return _size > 0 && (_bytes[_size - 1] & 0xFF) == TYPE_BEFORE;
    }
    
    boolean isAfter()
    {
        return _size > 0 && (_bytes[_size - 1] & 0xFF) == TYPE_AFTER;
    }
    
    void nudgeDown()
    {
        if (_size > 0 && (_bytes[_size - 1] & 0xFF) == 0)
        {
            _size--;
            bumpGeneration();
        }
    }
    
    void nudgeUp()
    {
        if (_size == 0)
        {
            _bytes[_size++] = 0;
            bumpGeneration();
        }
        else if ((_bytes[_size - 1] & 0xFF) == 0)
        {
            _bytes[_size - 1] = (byte)1;
            bumpGeneration();
        }
    }
    
    void nudgeUp2()
    {
        if (_size < MAX_KEY_LENGTH)
        {
            _bytes[_size++] = 0;
            bumpGeneration();
        }
    }
    
    /**
     * Encode a String value into this Key using a modified UTF-8 format.
     * Character values 0x0000 and 0x0001 in the String are represented by a
     * two byte sequence.  For character value c <= 0x0001, the encoding is
     * the two byte sequence (0x01, 0x20 + (byte)c)  This ensures that all
     * bytes in the encoded form of the String are non-zero but still collate
     * correctly.
     * <p>
     * This encoding does not provide localized collection capability.  It
     * merely collates Strings by the numeric values of their character codes.
     * <p>
     * Note: this code is paraphrased from java.io.DataOutputStream.
     * @param s     the String to encode and append to the key.
     * @return  This <tt>Key</tt>, to permit method call chaining
     */
    private Key appendString(String s, CoderContext context)
    {
        notLeftOrRightGuard();
        testValidForAppend();
        int strlen = s.length();
        int size = _size;
        _bytes[size++] = (byte)TYPE_STRING;
        if (_stringCoder != null)
        {
            try
            {
                _size = size;
                size = size - 1;
                _stringCoder.appendKeySegment(this, s, context);
                size = _size;
            }
            finally
            {
                _size = size;
            }
        }
        else
        {
            for (int i = 0; i < strlen; i++) {
                int c = s.charAt(i);
                if (c <= 0x0001)
                {
                    _bytes[size++] = (byte)(0x01);
                    _bytes[size++] = (byte)(c + 0x0020);
                }
                else if (c <= 0x007F)
                {
                    _bytes[size++] = (byte)c;
                }
                else if (c <= 0x07FF)
                {
                    _bytes[size++] = (byte)(0xC0 | ((c >>  6) & 0x1F));
                    _bytes[size++] = (byte)(0x80 | ((c >>  0) & 0x3F));
                }
                else
                {
                    _bytes[size++] = (byte)(0xE0 | ((c >> 12) & 0x0F));
                    _bytes[size++] = (byte)(0x80 | ((c >>  6) & 0x3F));
                    _bytes[size++] = (byte)(0x80 | ((c >>  0) & 0x3F));
                }
            }
        }
        
        return endSegment(size);
    }

    /**
     * Encode a String value into this Key using a modified UTF-8 format.
     * Character values 0x0000 and 0x0001 in the String are represented by a
     * two byte sequence.  For character value c <= 0x0001, the encoding is
     * the two byte sequence (0x01, 0x20 + (byte)c)  This ensures that all
     * bytes in the encoded form of the String are non-zero but still collate
     * correctly.
     * <p>
     * This encoding does not provide localized collection capability.  It
     * merely collates Strings by the numeric values of their character codes.
     * <p>
     * <p>
     * Note: this code is paraphrased from java.io.DataOutputStream.
     * @param s     a StringBuffer containing the String to encode and append
     *              to the key.
     * @return  This <tt>Key</tt>, to permit method call chaining
     */
    private Key appendString(StringBuffer s, CoderContext context)
    {
        notLeftOrRightGuard();
        int strlen = s.length();
        int size = _size;
        _bytes[size++] = (byte)TYPE_STRING;
        if (_stringCoder != null)
        {
            try
            {
                _size = size;
                size = size - 1;
                _stringCoder.appendKeySegment(this, s, context);
                size = _size;
            }
            finally
            {
                _size = size;
            }
        }
        else
        {
    
            for (int i = 0; i < strlen; i++) {
                int c = s.charAt(i);
                if (c <= 0x0001)
                {
                    _bytes[size++] = (byte)(0x01);
                    _bytes[size++] = (byte)(c + 0x0020);
                }
                else if (c <= 0x007F)
                {
                    _bytes[size++] = (byte)c;
                }
                else if (c <= 0x07FF)
                {
                    _bytes[size++] = (byte)(0xC0 | ((c >>  6) & 0x1F));
                    _bytes[size++] = (byte)(0x80 | ((c >>  0) & 0x3F));
                }
                else
                {
                    _bytes[size++] = (byte)(0xE0 | ((c >> 12) & 0x0F));
                    _bytes[size++] = (byte)(0x80 | ((c >>  6) & 0x3F));
                    _bytes[size++] = (byte)(0x80 | ((c >>  0) & 0x3F));
                }
            }
        }
        return endSegment(size);
    }

    /**
     * Encodes and appends the value <i>null</i> to the key.
     * @return  This <tt>Key</tt>, to permit method call chaining
     */
    private Key appendNull()
    {
        int size = _size;
        _bytes[size++] = TYPE_NULL;
        
        return endSegment(size);
    }

    private Key appendByKeyCoder(Object object, Class cl, KeyCoder coder, CoderContext context)
    {
        int size = _size;
        try
        {
            int handle =
                _persistit
                    .getClassIndex()
                    .lookupByClass(cl)
                    .getHandle();
 
            _size += encodeHandle(handle);
            int begin = _size;
            byte saveByte = _bytes[begin - 1];
            _bytes[begin - 1] = 0;
            coder.appendKeySegment(this, object, context);
            _bytes[begin - 1] = saveByte;
            quoteNulls(begin, _size - begin);
            endSegment(_size);
            size = _size;
            return this;
        }
        finally
        {
            _size = size;
        }
    } 

    private Object decodeByKeyCoder(Object target, CoderContext context)
    {
        int index = _index;
        int size = _size;
        Class clazz = Object.class;
        int segmentSize = 0;
        boolean unquoted = false;
        try
        {
            int handle = decodeHandle();
            clazz = _persistit.classForHandle(handle);
            if (clazz == null)
            {
                throw new ConversionException(
                    "No class information for handle " + handle);
            }
            KeyCoder coder = _persistit.lookupKeyCoder(clazz);
            if (coder == null)
            {
                throw new ConversionException(
                "No KeyCoder for class " + clazz.getName());
            }
            segmentSize = unquoteNulls(index);
            unquoted = true;
            size = _size;
            _size = index + segmentSize;
            if (target == null)
            {
                return coder.decodeKeySegment(this, clazz, context);
            }
            else
            {
                if (coder instanceof KeyRenderer)
                {
                    ((KeyRenderer)coder).renderKeySegment(
                        this, target, clazz, context);
                    return target;
                }
                else
                {
                    throw new ConversionException(
                        "No KeyRenderer for class " + clazz.getName());
                }
            }
        }
        finally
        {
            _size = size;
            if (unquoted)
            {
                index += quoteNulls(index, segmentSize);
                index = decodeEnd(index);
            }
            _index = index;
        }
    }
    
    private void decodeDisplayableByKeyCoder(boolean quoted, StringBuffer sb, CoderContext context)
    {
        int index = _index;
        int size = _size;
        Class clazz;
        int segmentSize = 0;
        boolean unquoted = false;
        try
        {
            int handle = decodeHandle();
            clazz = _persistit.classForHandle(handle);
            KeyCoder coder = null;
            if (clazz == null)
            {
                sb.append("(?handle=");
                sb.append(handle);
                sb.append(")");
            }
            else
            {
                sb.append("(");
                sb.append(clazz.getName());
                sb.append(")");
                coder = _persistit.lookupKeyCoder(clazz);
            }
            segmentSize = unquoteNulls(index);
            size = _size;
            unquoted = true;
            
            _size = index + segmentSize;
            if (coder instanceof KeyDisplayer)
            {
                ((KeyDisplayer)coder).displayKeySegment(
                    this, sb, clazz, context);
            }
            else
            {
                sb.append("{");
                for (int depth = 0; _index < _size; depth++)
                {
                    if (depth > 0) sb.append(",");
                    decodeDisplayable(quoted, sb, context);
                }
                sb.append("}");
            }
        }
        finally
        {
            _size = size;
            if (unquoted)
            {
                index += quoteNulls(index, segmentSize);
                index = decodeEnd(index);
            }
            _index = index;
        }
    }

    /**
     * Append a key segment that represents the left edge of the subtree at
     * this level.  The result key value should only be used in traversal
     * operations.
     */
    private Key appendBefore()
    {
        int size = _size;
        _bytes[size++] = TYPE_BEFORE;
        _bytes[size] = 0;
        _size = size;
        if (_depth != -1) _depth++;
        bumpGeneration();
        return this;
    }

    /**
     * Append a key segment that represents the right edge of the subtree at
     * this level.  The result key value should only be used in traversal
     * operations.
     */
    private Key appendAfter()
    {
        int size = _size;
        _bytes[size++] = (byte)TYPE_AFTER;
        _bytes[size] = 0;
        _size = size;
        if (_depth != -1) _depth++;
        bumpGeneration();
        return this;
    }

    private Key appendDate(Date v)
    {
        _bytes[_size++] = (byte)TYPE_DATE;
        int size = appendLongInternal(v.getTime());
        return endSegment(size);
    }

    private Key appendBigInteger(BigInteger v)
    {
        _bytes[_size++] = TYPE_BIG_INTEGER;
        appendBigInteger(v, 0);
        endSegment(_size);
        return this;
    }
    
    private Key appendBigDecimal(BigDecimal v)
    {
        _bytes[_size++] = TYPE_BIG_DECIMAL;
        appendBigInteger(v.unscaledValue(), v.scale());
        endSegment(_size);
        return this;
    }
    
    /**
     * Append a key segment that encodes a subarray from a supplied array of
     * bytes.  In the encoded form all NUL (0) and SOH (1) bytes are converted
     * to escape sequences so that the entire array is recognized as a single
     * key segment.
     * @param bytes     The byte array from which the key segment is created
     * @param offset    Offset of first byte of subarray
     * @param size      Size of subarray
     * @return          This <tt>Key</tt>, to permit method call chaining
     */
    public Key appendByteArray(byte[] bytes, int offset, int size)
    {
        _bytes[_size++] = TYPE_BYTE_ARRAY;
        int keySize = _size;
        System.arraycopy(bytes, offset, _bytes, keySize, size);
        _size += size;
        keySize += quoteNulls(keySize, size);
        return endSegment(keySize);
    }

    private int getTypeCode()
    {
        if (_index >= _size)
        {
            throw new MissingKeySegmentException("index=" + _index + " size=" + _size);
        }
        return _bytes[_index] & 0xFF;
    }

    /**
     * Implementation of String decoding
     * @param quoted
     * @param sb
     */
    private void decodeString(boolean quoted, StringBuffer sb, CoderContext context)
    {
        int index = _index;
        int c1 = _bytes[index++] & 0xFF;
        if (c1 != TYPE_STRING)
        {
            throw new ConversionException(
                "Invalid String lead-in byte (" + c1 +
                ") at position " + (index-1) + " in key");
        }
        if (_stringCoder != null)
        {
            try
            {
                _index = index;
                index--;
                _stringCoder.renderKeySegment(this, sb, StringBuffer.class, context);
                if (_bytes[_index++] != 0)
                {
                    throw new ConversionException(
                        _stringCoder.getClass().getName() +
                        " returned incorrect length");
                }
                index = _index;
            }
            finally
            {
                _index = index;
            }
        }
        else
        {
            while ((c1 = _bytes[index++] & 0xFF) != 0)
            {
                char c = 0;
                // Hande encoded NUL and SOH bytes
                if (c1 == 0x01)
                {
                    int c2 = _bytes[index++] & 0xFF;
                    if (c2 >= 0x0020 && c2 <= 0x0021)
                    {
                        c =(char)(c2 - 0x0020);
                    }
                    else
                    {
                        throw new ConversionException(
                            "String decoding exception at position " +
                            (index - 1));
                    }
                }
                
                // 7-bit ASCII
                else if (c1 <= 0x7F)
                {
                    c = (char)c1;
                } 
    
                else if (c1 > 0xC0 && c1 <= 0xDF)
                {
                    int c2 = _bytes[index++] & 0xFF;
                    if (c2 >= 0x80 && c2 <= 0xBF)
                    {
                       c = (char)
                            (((c1 & 0x1F) << 6) |
                             ((c2 & 0x3F) << 0));
                    }
                    else
                    {
                        throw new ConversionException(
                            "String decoding exception at position " +
                            (index - 1));
                    }
                }
                else if (c1 >= 0xE0 && c1 <= 0xEF)
                {
                    int c2 = _bytes[index++] & 0xFF;
                    int c3 = _bytes[index++] & 0xFF;
                    if (c2 >= 0x80 && c2 <= 0xBF && c3 >= 0x80 && c3 <= 0xBF)
                    {
                        c = (char)
                            (((c1 & 0x0F) << 12) |
                             ((c2 & 0x3F) << 6) |
                             ((c3 & 0x3F) << 0));
                    }
                }
                else
                {
                    throw new ConversionException(
                        "String decoding exception at position " + (index - 1));
                }
                if (quoted)
                {
                    Util.appendQuotedChar(sb, c);
                }
                else
                {
                    sb.append(c);
                }
            }
        }
        _index = index;
    }
    
    private Key endSegment(int size)
    {
        _bytes[size++] = 0;
        _size = size;
        if (_depth != -1) _depth++;
        bumpGeneration();
        return this;
    }

    private Key setRightEdge()
    {
        _bytes[0] = (byte)255;
        _size = 1;
        return this;
    }
    
    private Key setLeftEdge()
    {
        _bytes[0] = (byte)0;
        _size = 1;
        return this;
    }
    
    private int encodeHandle(int handle)
    {
        int size = _size;
        
        if (handle < 0x00000007)
        {
            _bytes[size++] = (byte)(TYPE_CODER1 | ((handle      ) & 0x7));
            return 1;
        }
        
        if (handle < 0x000001FF)
        {
            _bytes[size++] = (byte)(TYPE_CODER2 | ((handle >>> 6) & 0x7));
            _bytes[size++] = (byte)(0x80 | ((handle  ) & 0x3F));
            return 2;
        }
        
        if (handle < 0x00007FFF)
        {
            _bytes[size++] = (byte)(TYPE_CODER3 | ((handle >>> 12) & 0x7));
            _bytes[size++] = (byte)(0xC0 | ((handle >>> 6 ) & 0x3F));
            _bytes[size++] = (byte)(0x80 | ((handle       ) & 0x3F));
            return 3;
        }
        else
        {
            _bytes[size++] = (byte)(TYPE_CODER6 | ((handle >>> 30) & 0x7));
            _bytes[size++] = (byte)(0xC0 | ((handle >>> 24 ) & 0x3F));
            _bytes[size++] = (byte)(0xC0 | ((handle >>> 18 ) & 0x3F));
            _bytes[size++] = (byte)(0xC0 | ((handle >>> 12 ) & 0x3F));
            _bytes[size++] = (byte)(0xC0 | ((handle >>>  6 ) & 0x3F));
            _bytes[size++] = (byte)(0x80 | ((handle        ) & 0x3F));
            return 3;
        }
    }
    
    private int decodeHandle()
    {
        int index = _index;
        int base = _bytes[index++] & 0xFF;
        
        int result = base & 0x00000007;
        int v;
        
        switch (base & TYPE_CODER1)
        {
            case TYPE_CODER6:
                v = _bytes[index++] & 0xFF;
                if ((v & 0xC0) != 0xC0) break;
                result = result << 6 | (v & 0x3F);
                
                v = _bytes[index++] & 0xFF;
                if ((v & 0xC0) != 0xC0) break;
                result = result << 6 | (v & 0x3F);
                
                v = _bytes[index++] & 0xFF;
                if ((v & 0xC0) != 0xC0) break;
                result = result << 6 | (v & 0x3F);
                
                // Intentionally falls through
                
            case TYPE_CODER3:
                v = _bytes[index++] & 0xFF;
                if ((v & 0xC0) != 0xC0) break;
                result = result << 6 | (v & 0x3F);

                // Intentionally falls through

            case TYPE_CODER2:
                v = _bytes[index++] & 0xFF;
                if ((v & 0xC0) != 0xC0) break;
                result = result << 6 | (v & 0x3F);

                // Intentionally falls through

            case TYPE_CODER1:
                v = _bytes[index++] & 0xFF;
                if ((v & 0xC0) != 0x80) break;
                result = result << 6 | (v & 0x3F);

                // Intentionally falls through

                _index = index;
                return result;
                
            default:
        }
        throw new ConversionException("Invalid KeyCoder handle at " + _index);
    }
    
    private int decodeEnd(int index)
    {
        if (_bytes[index] != 0)
        {
            throw new ConversionException("Invalid end byte at " + (index + 1));
        }
        return index + 1;
    }

    private Object decodeNull()
    {
        int type = _bytes[_index] & 0xFF;
        if (type != TYPE_NULL) throw new ConversionException("Expected null type");
        _index = decodeEnd(_index + 1);
        return null;
    }
    
    private Object decodeBeforeAfter()
    {
        int type = _bytes[_index] & 0xFF;
        EdgeValue v = null;
        if (type == TYPE_BEFORE)
        {
            v = BEFORE;
        }
        else if (type == TYPE_AFTER)
        {
            v = AFTER;
        }
        else
        {
            throw new ConversionException("Invalid BEFORE type " + type);
        }
        _index = decodeEnd(_index + 1);
        return v;
    }

    /**
     * Converts a key segment from raw byte array to quoted form.  In the quoted
     * form, all NUL (0) and SOH (1) bytes are replaced by an escape sequence
     * (1, C) where C is 32 for NUL and 33 for SOH.
     * @param index     Offet to first byte to convert
     * @param size      Length of raw segment, in bytes.
     * @return          Length of the quoted segment
     */    
    private int quoteNulls(int index, int size)
    {
        for (int i = index; i < index + size; i++)
        {
            int c = _bytes[i] & 0xFF;
            if (c == 0 || c == 1)
            {
                System.arraycopy(_bytes, i + 1, _bytes, i + 2, _size - i - 1);
                _bytes[i] = 1;
                _bytes[i + 1] = (byte)(c + 32);
                _size++;
                size++;
                i++;
            }
        }
        return size;
    }
    
    /**
     * Converts a key segment in quoted form to raw form.  In quoted form,
     * the bytes NUL and SOH (0 and 1) are represented by the two-byte
     * sequence (1, C) where C is 32 for NUL or 33 for SOH.
     * @param   index
     * @return  The unquoted length of the array.
     */
    private int unquoteNulls(int index)
    {
        for (int i = index; i < _size; i++)
        {
            int c = _bytes[i] & 0xFF;
            if (c == 0) return i - index;
            if (c == 1 && i + 1 < _size)
            {
                c = _bytes[i + 1];
                if (c == 32 || c == 33)
                {
                    _bytes[i] = (byte)(c - 32);
                    System.arraycopy(_bytes, i + 2, _bytes, i + 1, _size - (i + 2));
                    _size--;
                }
            }
        }
        return _size - index;
    }

    /**
     * Recomputes the number of segments in this <tt>Key</tt>
     */
    private void recomputeCurrentDepth()
    {
        int index = -1;
        for (int depth = 0; ; depth++)
        {
            if ((index = nextElementIndex(index)) == -1)
            {
                _depth = depth;
                break;
            }
        }
    }

    private void notLeftOrRightGuard()
    {
        if (this == LEFT_GUARD_KEY || this == RIGHT_GUARD_KEY)
        {
            throw new IllegalArgumentException(
                "Operation not permitted on a guard key");
        }
    }
    
    private static class BigIntegerStruct
    {
        BigInteger _bigInteger;
        int _scale;
        int _zeroCount = 0;
    }
    
    
    private void appendBigInteger(
            BigInteger bigInt, 
            int scale)
    {
        int signum = bigInt.signum();
        if (signum == 0)
        {
            _bytes[_size++] = 0x40;
            return;
        }
        boolean neg = signum < 0;
        //
        // Estimated number of ints needed for buffering the BCD result
        //
        int length = bigInt.bitLength();
        //
        // Estimated number of bits left in bigInt
        //
        int rLength = length + 1;
        //
        // Estimated number of ints needed to hold all those bits.
        // This is guaranteed to be more than enough.  Each four-bit field of
        // each int will hold a decimal digit, and therefore will hold slightly
        // more than 3 bits worth of information.
        // 
        int iLength = (bigInt.bitLength() / 24) + 1;
        int[] buffer = new int[iLength];
        int index = iLength - 1;
        int digitCount = 0;
        int zeroCount = 0;
        //
        // Will become true if the most significant byte has a zero in it
        // high nibble.
        //
        boolean low4 = false;
        //
        // First step is to scrape the bits out.
        //
        while (rLength > 0)
        {
            BigInteger remainder = bigInt.remainder(BIG_INT_DIVISOR);
            long lowBits = remainder.longValue();
            if (neg) lowBits = -lowBits;
            for (int j = 0; j < 2 && index >= 0; j++)
            {
                if (index < 0) break;
                for (int k = 0; k < 4; k++)
                {
                    int hundred = (int)(lowBits % 100);
                    int bcd = (hundred / 10) * 16 + (hundred % 10);
                    buffer[index] |= bcd << (k * 8);
                    lowBits /= 100;
                    if (hundred > 0)
                    {
                        low4 = hundred < 10;
                        if (digitCount == 0)
                        {
                            if (hundred % 10 == 0) zeroCount++;
                        }
                        digitCount =
                            (iLength - index - 1) * 8 + 
                            (k * 2) + 2;
                    }
                    else if (digitCount == 0) zeroCount += 2;
                }
                index--;
            }
            //
            // Each division reduces the number of significant bits by
            // somewhate more than 50.  When this number goes below zero
            // we can be sure that the division will yield zero, so we can
            // avoid doing it in that case.
            //
            rLength -= 50;
            if (rLength > 0)
            {
                bigInt = bigInt.divide(BIG_INT_DIVISOR);
            }
        }
        if (index < 0) index = 0;
        //
        // If the most significant BCD pair has a zero in the upper half
        // then we need to shift everything left.
        //
        if (low4)
        {
            for (int i = index; i < iLength; i++)
            {
                int low = i + 1 == iLength ? 0 : buffer[i + 1];
                buffer[i] = buffer[i] << 4 | ((low >>> 28) & 0xF);
            }
            zeroCount++;
        }
        //
        // Compute the base-10 exponent.  The number is stored as an
        // exponent and a fraction, where the high field of the most sigificant
        // BCD pair is non-zero and has an implied decimal point immediately
        // to its left.  The actual number being represented is
        //
        //                  fraction * 10 ** exponent.
        //
        int exp = digitCount - scale - (low4 ? 1 : 0);
        //
        // Lay down the encoded signum.  This will be 0x3F for negative
        // values, or 0x41 for positive values.  (0x40 is reserved for zero.)
        //
        _bytes[_size++] = (byte)(0x40 + signum);
        //
        // For negative numbers, the ordering by exponent needs to be reversed.
        // That is, -1234 is less than -123, so the larger exponent value
        // needs to become more negative.
        //
        if (neg) exp = -exp;
        //
        // Lay down the exponent
        //
        _size = appendIntInternal(exp);
        //
        // Now lay the bytes into the byte array starting at offset.
        // For positive values, we add 0x11 to each BCD pair so that the
        // encoded byte is never 0.  For negative values, we subtract each
        // pair from 0xAA.   Thus +99 is encoded as 0xAA, and -99 is
        // encoded as 0x11.
        //
        int shift = (digitCount % 8) * 4;
        if (shift == 0) shift = 32;
        int digits = digitCount - zeroCount;
        
        index = iLength - ((digitCount + 7) / 8);
        for (int k = index; k < iLength; k++)
        {
            int word = buffer[k];
            for (; (shift -= 8) >= 0;)
            {
                byte b = (byte)(word >>> shift);
                if (neg) b = (byte)(0xAA - b);
                else b = (byte)(b + 0x11);
                _bytes[_size++] = b;
                digits -= 2;
                if (digits <= 0) break;
            }
            if (digits <= 0) break;
            shift = 32;
        }
        //
        // Ordering guard byte for negative numbers.  Will be ignored
        // during decoding.
        //
        if (neg) _bytes[_size++] = (byte)0xFF;
    }
    
    private void decodeBigInteger(BigIntegerStruct bis)
    {
        int start = _index;
        int signum = (_bytes[_index++] & 0xFF) - 0x40;
        if (signum < -1 || signum > 1)
        {
            throw new ConversionException(
                "Invalid BigInteger signum at offset " + (start));
        }
        if (signum == 0)
        {
            bis._bigInteger = BigInteger.ZERO;
            bis._scale = 0;
            return;
        }
        boolean neg = signum < 0;
        //
        // Decode the exponent
        //
        int exp = decodeIntInternal();
        if (neg) exp = -exp;
        //
        // Reset the fields of the BigIntegerStruct
        //
        bis._bigInteger = null;
        bis._scale = 0;
        bis._zeroCount = 0;
        
        boolean done = false;
        int lastDigit = 0;
        boolean leftOverDigit = false;
        boolean lastDigitZero = false;
        int zeroCount = 0;
        while (!done)
        {
            long lowBits = leftOverDigit ? lastDigit : 0;
            //
            // Number of digits that will be accumulated in lowBits before
            // adding to the accumulator.
            //
            int chunk = 0;
            if (exp > 0)
            {
                chunk = exp % 16;
                if (chunk == 0) chunk = 16;
            }
            else
            {
                chunk = 16;
            }
            exp -= chunk;
            if (leftOverDigit) chunk--;
            boolean dontAccumulate = false;
            while (chunk > 0 && !done)
            {
                int b = _bytes[_index++] & 0xFF;
                if (b == 0)
                {
                    done = true;
                    _index--;
                    if (chunk >= 15 && exp == -16 && lowBits == 0)
                    {
                        exp += 16;
                        dontAccumulate = true;
                    }
                    else
                    {
                        if (leftOverDigit) lastDigitZero = (lastDigit == 0);
                        if (lastDigitZero) zeroCount++;
                        for (; --chunk >= 0;)
                        {
                            zeroCount++;
                            lowBits *= 10;
                        }
                    }
                }
                else if (b != 0xFF)
                {
                    if (neg) b = 0xAA - b;
                    else b = b - 0x11;
                    if (((b >>> 4) & 0x0F) > 9 || (b & 0x0F) > 9)
                    {
                        throw new ConversionException(
                            "Invalid BigInteger encoding at index " + 
                            (_index - 1));
                    }
                    if (chunk == 1)
                    {
                        int digit = (b >>> 4) & 0x0F;
                        lowBits = lowBits * 10 + digit;
                        lastDigit = b & 0x0F;
                        lastDigitZero = (digit == 0);
                        leftOverDigit = true;
                        chunk--;
                    }
                    else
                    {
                        int digit0 = (b & 0x0F);
                        int digit1 = ((b >>> 4) & 0x0F);
                        lowBits = lowBits * 100 + digit1 * 10 + digit0;
                        chunk -= 2;
                        lastDigitZero = (digit0 == 0);
                        leftOverDigit = false;
                    }
                }
            }
            if (neg) lowBits = -lowBits;
            if (bis._bigInteger == null)
            {
                bis._bigInteger = BigInteger.valueOf(lowBits);
            }
            else if (!dontAccumulate)
            {
                bis._bigInteger =
                    bis._bigInteger.multiply(BIG_INT_DIVISOR)
                        .add(BigInteger.valueOf(lowBits));
            }
        }
        while (exp > 0)
        {
            bis._bigInteger = bis._bigInteger.multiply(BIG_INT_DIVISOR);
            exp -= 16;
        }
        bis._zeroCount = zeroCount;
        bis._scale = -exp;
    }
}