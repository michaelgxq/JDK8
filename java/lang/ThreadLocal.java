/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 */

package java.lang;
import java.lang.ref.*;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * This class provides thread-local variables.  These variables differ from
 * their normal counterparts in that each thread that accesses one (via its
 * {@code get} or {@code set} method) has its own, independently initialized
 * copy of the variable.  {@code ThreadLocal} instances are typically private
 * static fields in classes that wish to associate state with a thread (e.g.,
 * a user ID or Transaction ID).
 *
 * <p>For example, the class below generates unique identifiers local to each
 * thread.
 * A thread's id is assigned the first time it invokes {@code ThreadId.get()}
 * and remains unchanged on subsequent calls.
 * <pre>
 * import java.util.concurrent.atomic.AtomicInteger;
 *
 * public class ThreadId {
 *     // Atomic integer containing the next thread ID to be assigned
 *     private static final AtomicInteger nextId = new AtomicInteger(0);
 *
 *     // Thread local variable containing each thread's ID
 *     private static final ThreadLocal&lt;Integer&gt; threadId =
 *         new ThreadLocal&lt;Integer&gt;() {
 *             &#64;Override protected Integer initialValue() {
 *                 return nextId.getAndIncrement();
 *         }
 *     };
 *
 *     // Returns the current thread's unique ID, assigning it if necessary
 *     public static int get() {
 *         return threadId.get();
 *     }
 * }
 * </pre>
 * <p>Each thread holds an implicit reference to its copy of a thread-local
 * variable as long as the thread is alive and the {@code ThreadLocal}
 * instance is accessible; after a thread goes away, all of its copies of
 * thread-local instances are subject to garbage collection (unless other
 * references to these copies exist).
 *
 * @author  Josh Bloch and Doug Lea
 * @since   1.2
 */
public class ThreadLocal<T> {
    /**
     * ThreadLocals rely on per-thread linear-probe hash maps attached
     * to each thread (Thread.threadLocals and
     * inheritableThreadLocals).  The ThreadLocal objects act as keys,
     * searched via threadLocalHashCode.  This is a custom hash code
     * (useful only within ThreadLocalMaps) that eliminates collisions
     * in the common case where consecutively constructed ThreadLocals
     * are used by the same threads, while remaining well-behaved in
     * less common cases.
     */
    private final int threadLocalHashCode = nextHashCode();

    /**
     * The next hash code to be given out. Updated atomically. Starts at
     * zero.
     *
     * 该成员变量是一个静态成员变量，因此，该成员变量的值是当前线程中所有 ThreadLocal 类使用共用的
     */
    private static AtomicInteger nextHashCode =
        new AtomicInteger();

    /**
     * 该成员变量为用于生成每一个 ThreadLocal 类实例的 ID 值的一个魔数
     *
     * The difference between successively generated hash codes - turns
     * implicit sequential thread-local IDs into near-optimally spread
     * multiplicative hash values for power-of-two-sized tables.
     *
     * 上面这段注释的含义为
     * 两个连续生成的哈希 code 的差
     *（即 两个连续生成的哈希 code 相减后的差就是该成员变量的值，因为该类的哈希 code 的生成规则就是使用该成员变量连续相加（见下面的 nextHashCode() 方法））
     * 把隐式连续（因为光从这些数（即 ID 值看，我们看不出它们是连续的））的 ThreadLocal 类实例的 ID 值转换成能够在大小为 2 的幂的数组中近乎最优分布的乘法哈希值
     *（即 使用该成员变量计算（即 相加）得到的 ThreadLocal 类实例的 ID 值经过哈希计算之后的值，能够基本上完全散布在大小为 2 的幂的数组中，而不产生冲突）
     */
    private static final int HASH_INCREMENT = 0x61c88647;

    /**
     * Returns the next hash code.
     */
    private static int nextHashCode() {
        return nextHashCode.getAndAdd(HASH_INCREMENT);
    }

    /**
     * Returns the current thread's "initial value" for this
     * thread-local variable.  This method will be invoked the first
     * time a thread accesses the variable with the {@link #get}
     * method, unless the thread previously invoked the {@link #set}
     * method, in which case the {@code initialValue} method will not
     * be invoked for the thread.  Normally, this method is invoked at
     * most once per thread, but it may be invoked again in case of
     * subsequent invocations of {@link #remove} followed by {@link #get}.
     *
     * <p>This implementation simply returns {@code null}; if the
     * programmer desires thread-local variables to have an initial
     * value other than {@code null}, {@code ThreadLocal} must be
     * subclassed, and this method overridden.  Typically, an
     * anonymous inner class will be used.
     *
     * @return the initial value for this thread-local
     */
    protected T initialValue() {
        return null;
    }

    /**
     * Creates a thread local variable. The initial value of the variable is
     * determined by invoking the {@code get} method on the {@code Supplier}.
     *
     * @param <S> the type of the thread local's value
     * @param supplier the supplier to be used to determine the initial value
     * @return a new thread local variable
     * @throws NullPointerException if the specified supplier is null
     * @since 1.8
     */
    public static <S> ThreadLocal<S> withInitial(Supplier<? extends S> supplier) {
        return new SuppliedThreadLocal<>(supplier);
    }

    /**
     * Creates a thread local variable.
     * @see #withInitial(java.util.function.Supplier)
     *
     * 该构造方法是一个空构造方法
     *（因为 ThreadLocal 类的成员变量都是有设置初始值的，所以就无需通过构造方法给这些成员变量设置值了，因此，只要有一个空构造方法即可）
     *
     */
    public ThreadLocal() {
    }

    /**
     * Returns the value in the current thread's copy of this
     * thread-local variable.  If the variable has no value for the
     * current thread, it is first initialized to the value returned
     * by an invocation of the {@link #initialValue} method.
     *
     * @return the current thread's value of this thread-local
     */
    public T get() {
        // 调用 Thread 类中的 currentThread() 方法，获取当前线程
        Thread t = Thread.currentThread();

        // 获取当前线程（即 Thread 类实例）中的数据类型为 ThreadLocalMap 类的成员变量 threadLocals
        //（该 threadLocals 即为存放 Entry 类实例的容器（可以等价的理解为存放 ThreadLocal 类实例的容器）
        //（具体见 ThreadLocal 笔记中的 “ThreadLocal<T> 类的结构图”）
        ThreadLocalMap map = getMap(t);

        // 如果该成员变量 threadLocals 不为空
        // 表示此时当前线程中已经有使用过 ThreadLocal 来存放变量了
        if (map != null) {
            // 调用 ThreadLocalMap 类的 getEntry() 方法，从 Thread 类中的成员变量 threadLocals（即 Entry 类实例的容器）中
            // 获取该 ThreadLocal 类实例所对应的 Entry 类实例
            // 从而获取存放在该 Entry 类实例中的变量（即 所谓的，存放在 ThreadLocal 中的变量）
            // 并返回该变量
            ThreadLocalMap.Entry e = map.getEntry(this);
            if (e != null) {
                @SuppressWarnings("unchecked")
                T result = (T)e.value;
                return result;
            }
        }

        // 如果为空，表示此时是第一次在当前线程中使用 ThreadLocal 类实例存放变量
        // 此时
        // 就调用 setInitialValue() 方法设置初始化值
        return setInitialValue();
    }

    /**
     * Variant of set() to establish initialValue. Used instead
     * of set() in case user has overridden the set() method.
     *
     * @return the initial value
     */
    private T setInitialValue() {
        // 调用 initialValue() 方法获取初始值（即 需要存放在 ThreadLocal 中的变量的初始值）
        //（该 initialValue() 方法就是我们会重写的方法）
        T value = initialValue();

        // 调用 Thread 类中的 currentThread() 方法，获取当前线程
        Thread t = Thread.currentThread();

        // 获取当前线程（即 Thread 类实例）中的数据类型为 ThreadLocalMap 类的成员变量 threadLocals
        //（该 threadLocals 即为存放 Entry 类实例的容器（可以等价的理解为存放 ThreadLocal 类实例的容器）
        //（具体见 ThreadLocal 笔记中的 “ThreadLocal<T> 类的结构图”）
        ThreadLocalMap map = getMap(t);


        // 如果该成员变量 threadLocals 不为空
        // 表示此时当前线程中已经有使用过 ThreadLocal 来存放变量了
        if (map != null)
            // 调用 ThreadLocalMap 类中的 set() 方法来设置当前这个需要存放到 ThreadLocal 中的变量（即 形参 value）
            // 其实
            // 该方法内部就是往数据类型为 Entry 类的数组中存放元素
            //（该元素（即 Entry 类实例）即为 Key 为 ThreadLocal 类实例，Value 为当前这个变量（即 形参 value）的键值对）
            //（Entry 类的介绍见 ThreadLocal 笔记）

            // 注意
            // 这里的实参 this 就是该 ThreadLocal 类实例
            map.set(this, value);
        else
            // 如果为空，表示此时是第一次在当前线程中使用 ThreadLocal 类实例存放变量
            // 因此
            // 就要创建 ThreadLocalMap 类实例这个容器，并把该容器赋值给 Thread 类中的成员变量 threadLocals
            //（即 相当于初始化当前线程（即 Thread 类实例）的成员变量 threadLocals）
            // 然后
            // 往该容器中存放对当前这个变量（即 当前要往 ThreadLocal 中存放的变量）
            createMap(t, value);

        return value;
    }

    /**
     * Sets the current thread's copy of this thread-local variable
     * to the specified value.  Most subclasses will have no need to
     * override this method, relying solely on the {@link #initialValue}
     * method to set the values of thread-locals.
     *
     * @param value the value to be stored in the current thread's copy of
     *        this thread-local.
     */
    public void set(T value) {
        // 调用 Thread 类中的 currentThread() 方法，获取当前线程
        Thread t = Thread.currentThread();

        // 获取当前线程（即 Thread 类实例）中的数据类型为 ThreadLocalMap 类的成员变量 threadLocals
        ThreadLocalMap map = getMap(t);

        // 如果当前该 ThreadLocalMap 类实例不为空
        // 此时
        // 表明当前线程中已经有使用过 ThreadLocal 来存放变量了，因为 ThreadLocalMap 这个容器中已经有了值
        //（具体可见 ThreadLocal 笔记中的 “ThreadLocal<T> 类的结构图”）
        if (map != null)
            // 调用 ThreadLocalMap 类中的 set() 方法来设置当前这个需要存放到 ThreadLocal 中的变量（即 形参 value）
            // 其实
            // 该方法内部就是往数据类型为 Entry 类的数组中存放元素
            //（该元素（即 Entry 类实例）即为 Key 为 ThreadLocal 类实例，Value 为当前这个变量（即 形参 value）的键值对）
            //（Entry 类的介绍见 ThreadLocal 笔记）

            // 注意
            // 这里的实参 this 就是该 ThreadLocal 类实例
            map.set(this, value);
        else
            // 如果为空，表示此时是第一次在当前线程中使用 ThreadLocal 类实例存放变量
            // 因此
            // 就要创建 ThreadLocalMap 类实例这个容器，并把该容器赋值给 Thread 类中的成员变量 threadLocals
            //（即 相当于初始化当前线程（即 Thread 类实例）的成员变量 threadLocals）
            // 然后
            // 往该容器中存放对当前这个变量（即 当前要往 ThreadLocal 中存放的变量）
            createMap(t, value);
    }

    /**
     * Removes the current thread's value for this thread-local
     * variable.  If this thread-local variable is subsequently
     * {@linkplain #get read} by the current thread, its value will be
     * reinitialized by invoking its {@link #initialValue} method,
     * unless its value is {@linkplain #set set} by the current thread
     * in the interim.  This may result in multiple invocations of the
     * {@code initialValue} method in the current thread.
     *
     * @since 1.5
     */
     public void remove() {
         ThreadLocalMap m = getMap(Thread.currentThread());
         if (m != null)
             m.remove(this);
     }

    /**
     * 此方法用于获取 Thread 类实例（即 线程）中的成员变量 threadLocals
     * 该成员变量的数据类型就是 ThreadLocalMap（具体见 ThreadLocal 类笔记）
     *
     * Get the map associated with a ThreadLocal. Overridden in
     * InheritableThreadLocal.
     *
     * @param  t the current thread
     * @return the map
     */
    ThreadLocalMap getMap(Thread t) {
        return t.threadLocals;
    }

    /**
     * Create the map associated with a ThreadLocal. Overridden in
     * InheritableThreadLocal.
     *
     * @param t the current thread
     * @param firstValue value for the initial entry of the map
     */
    void createMap(Thread t, T firstValue) {
        // 调用 ThreadLocalMap 类的构造方法，用于存放 ThreadLocal 类实例，以及对应的需要在当前线程中保存的变量副本
        // 其实
        // 该方法内部就是往数据类型为 Entry 类的数组（该数组即为 ThreadLocalMap 的成员变量 table）中存放元素（即 Entry 类实例）
        //（该 Entry 类实例，即为 Key 为 ThreadLocal 类实例，Value 为当前这个变量（即 形参 value）的键值对）
        //（Entry 类的介绍见 ThreadLocal 笔记）
        // 这里的实参 this 就是当前这个 ThreadLocal 类实例
        t.threadLocals = new ThreadLocalMap(this, firstValue);
    }

    /**
     * Factory method to create map of inherited thread locals.
     * Designed to be called only from Thread constructor.
     *
     * @param  parentMap the map associated with parent thread
     * @return a map containing the parent's inheritable bindings
     */
    static ThreadLocalMap createInheritedMap(ThreadLocalMap parentMap) {
        return new ThreadLocalMap(parentMap);
    }

    /**
     * Method childValue is visibly defined in subclass
     * InheritableThreadLocal, but is internally defined here for the
     * sake of providing createInheritedMap factory method without
     * needing to subclass the map class in InheritableThreadLocal.
     * This technique is preferable to the alternative of embedding
     * instanceof tests in methods.
     */
    T childValue(T parentValue) {
        throw new UnsupportedOperationException();
    }

    /**
     * An extension of ThreadLocal that obtains its initial value from
     * the specified {@code Supplier}.
     */
    static final class SuppliedThreadLocal<T> extends ThreadLocal<T> {

        private final Supplier<? extends T> supplier;

        SuppliedThreadLocal(Supplier<? extends T> supplier) {
            this.supplier = Objects.requireNonNull(supplier);
        }

        @Override
        protected T initialValue() {
            return supplier.get();
        }
    }

    /**
     * ThreadLocalMap is a customized hash map suitable only for
     * maintaining thread local values. No operations are exported
     * outside of the ThreadLocal class. The class is package private to
     * allow declaration of fields in class Thread.  To help deal with
     * very large and long-lived usages, the hash table entries use
     * WeakReferences for keys. However, since reference queues are not
     * used, stale entries are guaranteed to be removed only when
     * the table starts running out of space.
     */
    static class ThreadLocalMap {

        /**
         * The entries in this hash map extend WeakReference, using
         * its main ref field as the key (which is always a
         * ThreadLocal object).  Note that null keys (i.e. entry.get()
         * == null) mean that the key is no longer referenced, so the
         * entry can be expunged from table.  Such entries are referred to
         * as "stale entries" in the code that follows.
         */
        static class Entry extends WeakReference<ThreadLocal<?>> {
            /** The value associated with this ThreadLocal. */
            // 该成员变量指向的就是我们存放在 ThreadLocal 中的值
            //（即 我们所谓的把值存放到 ThreadLocal 中，就是指把值赋值给它）
            Object value;

            Entry(ThreadLocal<?> k, Object v) {
                super(k);
                value = v;
            }
        }

        /**
         * The initial capacity -- MUST be a power of two.
         *
         * 该成员变量为该下面成员变量 table （即 存放 Entry 类实例的数组（容器））的初始容量（该值 16 为 2 的幂）
         */
        private static final int INITIAL_CAPACITY = 16;

        /**
         * The table, resized as necessary.
         * table.length MUST always be a power of two.
         *
         * 该成员变量就是用于存放 Entry 类实例（见 Entry 类笔记）的数组（即 容器）
         * 即
         * 该数组（即 容器）就是真正存放那些需要线程隔离的数据的地方
         * 该数组（即 容器）的大小永远为 2 的幂
         *（因为该容器的初始大小为 16（它就是 2 的 4 次幂），之后每一次扩容都是在原来大小的基础上 * 2）
         *（具体见 resize() 方法）
         */
        private Entry[] table;

        /**
         * The number of entries in the table.
         *
         * 该成员变量用于记录容器（即 成员变量 table）的大小
         */
        private int size = 0;

        /**
         * The next size value at which to resize.
         *
         * 该成员变量用于记录用于容器（即 成员变量 table）扩容的阈值
         *（这个阈值本质上就是当然容器的容量 * 2 / 3（见下面的 setThreshold() 方法））
         */
        private int threshold; // Default to 0

        /**
         * Set the resize threshold to maintain at worst a 2/3 load factor.
         */
        private void setThreshold(int len) {
            threshold = len * 2 / 3;
        }

        /**
         * Increment i modulo len.
         *
         * 该方法用于获取成员变量 table（数据类型为 Entry 类的数组）的下一个下标（即 槽位）
         * 从该三目运算符可以看出，该数组是一个逻辑上的环形数组
         *（因为 如果下一个下标越界了，那么就取 0（也就是数组第一个下标））
         */
        private static int nextIndex(int i, int len) {
            return ((i + 1 < len) ? i + 1 : 0);
        }

        /**
         * Decrement i modulo len.
         */
        private static int prevIndex(int i, int len) {
            return ((i - 1 >= 0) ? i - 1 : len - 1);
        }

        /**
         * Construct a new map initially containing (firstKey, firstValue).
         * ThreadLocalMaps are constructed lazily, so we only create
         * one when we have at least one entry to put in it.
         */
        ThreadLocalMap(ThreadLocal<?> firstKey, Object firstValue) {
            table = new Entry[INITIAL_CAPACITY];
            int i = firstKey.threadLocalHashCode & (INITIAL_CAPACITY - 1);
            table[i] = new Entry(firstKey, firstValue);
            size = 1;
            setThreshold(INITIAL_CAPACITY);
        }

        /**
         * Construct a new map including all Inheritable ThreadLocals
         * from given parent map. Called only by createInheritedMap.
         *
         * @param parentMap the map associated with parent thread.
         */
        private ThreadLocalMap(ThreadLocalMap parentMap) {
            Entry[] parentTable = parentMap.table;
            int len = parentTable.length;
            setThreshold(len);
            table = new Entry[len];

            for (int j = 0; j < len; j++) {
                Entry e = parentTable[j];
                if (e != null) {
                    @SuppressWarnings("unchecked")
                    ThreadLocal<Object> key = (ThreadLocal<Object>) e.get();
                    if (key != null) {
                        Object value = key.childValue(e.value);
                        Entry c = new Entry(key, value);
                        int h = key.threadLocalHashCode & (len - 1);
                        while (table[h] != null)
                            h = nextIndex(h, len);
                        table[h] = c;
                        size++;
                    }
                }
            }
        }

        /**
         * Get the entry associated with key.  This method
         * itself handles only the fast path: a direct hit of existing
         * key. It otherwise relays to getEntryAfterMiss.  This is
         * designed to maximize performance for direct hits, in part
         * by making this method readily inlinable.
         *
         * @param  key the thread local object
         * @return the entry associated with key, or null if no such
         */
        private Entry getEntry(ThreadLocal<?> key) {
            int i = key.threadLocalHashCode & (table.length - 1);
            Entry e = table[i];
            if (e != null && e.get() == key)
                return e;
            else
                return getEntryAfterMiss(key, i, e);
        }

        /**
         * Version of getEntry method for use when key is not found in
         * its direct hash slot.
         *
         * @param  key the thread local object
         * @param  i the table index for key's hash code
         * @param  e the entry at table[i]
         * @return the entry associated with key, or null if no such
         */
        private Entry getEntryAfterMiss(ThreadLocal<?> key, int i, Entry e) {
            Entry[] tab = table;
            int len = tab.length;

            while (e != null) {
                ThreadLocal<?> k = e.get();
                if (k == key)
                    return e;
                if (k == null)
                    expungeStaleEntry(i);
                else
                    i = nextIndex(i, len);
                e = tab[i];
            }
            return null;
        }

        /**
         * Set the value associated with key.
         *
         * @param key the thread local object
         *            形参 key 为当前这个 ThreadLocal 类实例
         *           （即 ThreadLocal 类中的 set() 方法调用该 set() 方法时，传入的 this 指针）
         *
         * @param value the value to be set
         *              形参 value 就是当前要往 ThreadLocal 中存放的变量（即 需要线程隔离的变量）
         */
        private void set(ThreadLocal<?> key, Object value) {

            // We don't use a fast path as with get() because it is at
            // least as common to use set() to create new entries as
            // it is to replace existing ones, in which case, a fast
            // path would fail more often than not.

            // 获取成员变量 table（数据类型为 Entry 类的数组）（即 存放 ThreadLocal\<T> 类实例的容器）
            Entry[] tab = table;
            int len = tab.length;

            // 对当前这个 ThreadLocal 类实例进行哈希运算获取对应的槽位（即 数组的下标）
            //（即 以当前容器的大小为模，使用当前 ThreadLocal 类实例的 ID 值进行取模）
            int i = key.threadLocalHashCode & (len-1);

            // 从当前ThreadLocal 类实例槽位（即 数组的下标）开始循环遍历数组，直到找到合适的槽位，放入 Entry 类实例
            // 从该 for 循环中的两个 if 判断可以看出，该 for 循环主要做两件事
            // 1. 第一个 if 判断
            //    判断当前槽位是否就是该 ThreadLocal 类实例所在的槽位
            //   （即 当前调用该 set() 方法的目的是为了覆盖原先存放在该 ThreadLocal 中的变量）
            //    如果是
            //    就直接用新值（即 当前调用该 set() 方法需要设置的值（即 当要存放到 ThreadLocal 中的值））覆盖它对应的旧值
            //
            // 2. 第二个 if 判断
            //    判断当前槽位所对应的 Entry 类实例是否已经“不新鲜”（即 下面 replaceStaleEntry() 方法名中的 stale）
            //   （“不新鲜” 是指该 Entry 类实例所指向的 ThreadLocal 类实例已经被 GC 回收）
            //    如果是
            //    说明此时该槽位所对应的 Entry 类实例已经没有用了
            //    此时
            //    就需要 “清理” 该 “不新鲜” 的 Entry 类实例（即 把该槽位置为 null）
            //    并且
            //    继续往后遍历，判断之后遍历到的槽位是否该 ThreadLocal 类实例所在的槽位
            //   （“清空” 和 继续往后遍历的动作是在 replaceStaleEntry() 方法中完成的）


            // 注意
            // 如果我们是第一次往当前这个 ThreadLocal 类实例中存放变量
            // 并且
            // 当前槽位没有因为哈希冲突而存放其他 ThreadLocal 类实例对应的 Entry 类实例
            // 那么
            // 代码是不会走到这个 for 循环中去的
            //（因为该槽位直接就不符合该 for 循环的执行条件 --- e != null）
            // 因此
            // 这整个 for 循环就是处理哈希冲突的逻辑
            for (Entry e = tab[i];
                 e != null; // for 循环的执行条件（即 遇到槽位上的元素为 null（即 槽位上没有元素）时，停止循环）
                 e = tab[i = nextIndex(i, len)]) {
                // 获取当前 Entry 类实例所指向的 ThreadLocal 类实例
                ThreadLocal<?> k = e.get();

                // 如果该 ThreadLocal 类实例就是当前这个 ThreadLocal 类实例
                // 说明
                // 此时调用这个 set() 方法是为了替换原先存放在这个 ThreadLocal 中的变量
                // 那么
                // 就直接用新值（即 当前调用该 set() 方法需要设置的值（即 当要存放到 ThreadLocal 中的值））覆盖它对应的旧值

                // 注意
                // 在两种情况下，会走到这个 if 判断
                // 1. 这个 for 循环一进来就走进这个 if 判断
                //    说明当前调用这个 set() 方法是为了替换原先存放在这个 ThreadLocal 中的变量
                //    并且
                //    当前这个 ThreadLocal 类实例对应的 Entry 类实例 "没有哈希冲突"（因为它就在自己经过哈希运算后的槽位上）
                // 2. 这个 for 循环运行了几次之后才进这个 if 判断
                //    说明当前调用这个 set() 方法是为了替换原先存放在这个 ThreadLocal 中的变量
                //    但是
                //    当前这个 ThreadLocal 类实例对应的 Entry 类实例 "有哈希冲突"，从而被放到了其他槽位上了
                if (k == key) {
                    // 使用新值（即 当前调用该 set() 方法需要设置的值（即 当要存放到 ThreadLocal 中的值））覆盖旧值
                    e.value = value;

                    // 完成后直接返回
                    return;
                }

                // 如果当前 Entry 类实例所指向的 ThreadLocal 类实例为 null
                // 那么
                // 就表示该 ThreadLocal 类实例以及被 GC 回收了（即 该 ThreadLocal 类实例所在的 Entry 类实例已经不新鲜了）
                // 此时
                // 就可以使用新的值（即当前调用该 set() 方法所要设置的值）转换成一个新的 Entry 类实例替换掉 “不新鲜” 的 Entry 类实例
                // 通过这种方式来解决哈希冲突

                // 注意
                // 代码走到这里这里，表示上面的 if 语句没走到
                // 即
                // 说明此时调用这个 set() 方法 “可能” 不是为了替换原先存放在这个 ThreadLocal 实例中的变量
                //（其实是存放在对应的 Entry 类实例中）
                // 之所以说是 “可能“
                // 因为
                // 当前这个 ThreadLocal 类实例所对应的 Entry 类实例之前在存放到容器（即 成员变量 table）中时，可能遇到了哈希冲突
                // 从而通过 “线性探测法” 被放到了其他的槽位上了
                // 并且
                // 此时容器 table 还没遍历完
                // 所以
                // 这时可能就还没遇到其他槽位上的这个 ThreadLocal 类实例对应的 Entry 类实例
                // 这也就是为什么下面调用的 replaceStaleEntry() 方法中会有和上面这个 if 语句相同逻辑的代码
                // 就是为了在遍历容器 table 中的剩余元素时，对这种可能性再次进行验证
                //（即 判断当前 ThreadLocal 类实例对应的 Entry 类实例是否被放到了其他槽位上了）
                if (k == null) {
                    // 把新的值（即当前调用该 set() 方法所要设置的值（即 当要存放到 ThreadLocal 中的值））转换成一个新的 Entry 类实例
                    // 替换掉 “不新鲜” 的 Entry 类实例
                    // 通过这种方式来解决哈希冲突
                    replaceStaleEntry(key, value, i);

                    // 完成后直接返回
                    return;
                }
            }

            // 在两种情况下，代码会走到这里
            // 1. 上面的 for 循环直接没走
            //    这表示我们是第一次使用当前这个 ThreadLocal 类实例来存放遍历
            //    并且
            //    这个槽位没有哈希冲突
            //
            // 2. 上面的 for 循环走完了（即 上面 for 循环遍历到的元素都不符合 for 循环中的两个 if 判断）
            //    这表示当前这个 ThreadLocal 类实例遇到了哈希冲突
            //    但是
            //    在进行 “线性探测” 的时候，没有遇到存放着 “不新鲜” 的 Entry 类实例的槽位来存放它
            //    从而导致
            //    在遍历到一个 “空槽位”（即 该槽位中的元素为 null）后，终止了上面的 for 循环
            //
            // 无论是上面哪种情况
            // 我们此时都是直接往该 “空槽位” （即 该槽位中的元素为 null）中存放 Entry 类实例即可
            tab[i] = new Entry(key, value);
            int sz = ++size;


            if (!cleanSomeSlots(i, sz) && sz >= threshold)
                rehash();
        }

        /**
         * Remove the entry for key.
         */
        private void remove(ThreadLocal<?> key) {
            Entry[] tab = table;
            int len = tab.length;
            int i = key.threadLocalHashCode & (len-1);
            for (Entry e = tab[i];
                 e != null;
                 e = tab[i = nextIndex(i, len)]) {
                if (e.get() == key) {
                    e.clear();
                    expungeStaleEntry(i);
                    return;
                }
            }
        }

        /**
         * Replace a stale entry encountered during a set operation
         * with an entry for the specified key.  The value passed in
         * the value parameter is stored in the entry, whether or not
         * an entry already exists for the specified key.
         *
         * As a side effect, this method expunges all stale entries in the
         * "run" containing the stale entry.  (A run is a sequence of entries
         * between two null slots.)
         *
         * 上面注释的大致含义是
         *
         * 把新的值（即当前调用该 set() 方法所要设置的值（即 当要存放到 ThreadLocal 中的值））转换成一个新的 Entry 类实例
         * 替换掉 “不新鲜” 的 Entry 类实例
         * 并且
         * 该方法中还有一个 “副作用”，即会 “清理” 当前这个 run 中的所有 “不新鲜” 的 Entry 类实例
         *（这个功能其实是由该方法中的 cleanSomeSlots(expungeStaleEntry(slotToExpunge), len); 这行代码实现的）
         * 注意
         * “清理” 就是指把该 Entry 类实例所在槽位置为 null
         * 以便 GC 时回收 Entry 类实例中强引用（即 成员变量 value）所指向的那个对象
         *（也就是 “所谓” 存放在 ThreadLocal 中的变量（因为当我们理解 ThreadLocal 类之后，就知道该变量存放在 ThreadLocal 中只是给人的外部感受而已））
         * 以防止内存泄漏
         *
         *
         * 上面注释中
         * run 表示 Entry 数组（即成员变量 table）中，处于两个为 null 的槽位之间的那串子数组
         *（这里的 null 是指 Entry 类实例为 null（即 当前这个槽位（下标）没有元素）和 “不新鲜” 的 Entry 类实例不是一个概念）
         *
         * @param  key the key
         *             该形参为当前这个 ThreadLocal 类实例
         *            （即 ThreadLocalMap 类中的 set() 方法调用该方法时，传入的值（具体见 set() 方法中的形参介绍））
         *
         * @param  value the value to be associated with key
         *               该形参就是当前要往 ThreadLocal 中存放的变量（即 需要线程隔离的变量）
         *
         * @param  staleSlot index of the first stale entry encountered while
         *         searching for key.
         *                   该形参就是 ThreadLocalMap 类中的 set() 方法（即 当前方法的调用者）在变量寻找合适的槽位时
         *                   遇到的第一个 “不新鲜” 的槽位
         *                  （具体见该 set() 方法中的注释）
         */
        private void replaceStaleEntry(ThreadLocal<?> key, Object value,
                                       int staleSlot) {
            Entry[] tab = table;
            int len = tab.length;
            Entry e;

            // Back up to check for prior stale entry in current run.
            // We clean out whole runs at a time to avoid continual
            // incremental rehashing due to garbage collector freeing
            // up refs in bunches (i.e., whenever the collector runs).

            // 该变量用于记录需要清理的 “不新鲜” 的 Entry 类实例所在槽位（下标）
            int slotToExpunge = staleSlot;

            // 下面两个 for 循环的目的
            // 是为了找出以当前 “不新鲜” 的槽位（即 下标）为原点，向前，以及向后遍历容器 table（即 ThreadLocalMap 的成员变量）
            // 找出 并且 “清理” 当前 run（它的含义见该方法上面的注释）中，所有 “不新鲜” 的槽位

            // 以当前 “不新鲜” 的槽位（即 下标）为原点，向前遍历容器 table（即 ThreadLocalMap 的成员变量）
            for (int i = prevIndex(staleSlot, len);
                 (e = tab[i]) != null;
                 i = prevIndex(i, len))
                // 找到 “不新鲜” 的 Entry 类实例之后，记录下它的槽位（即 下标）
                // 如果遍历的过程中遇到多个 “不新鲜” 的 Entry 类实例
                // 那么
                // 在该 for 循环结束之后，变量 slotToExpunge 所记录的就是槽位（下标）最小的那个 “不新鲜” 的 Entry 类实例
                if (e.get() == null)
                    slotToExpunge = i;

            // Find either the key or trailing null slot of run, whichever
            // occurs first

            // 以当前 “不新鲜” 的槽位（即 下标）为原点，向后遍历容器 table（即 ThreadLocalMap 的成员变量）
            // 这一步其实是为了完成 set() 方法中那个没有完成的遍历动作
            for (int i = nextIndex(staleSlot, len);
                 (e = tab[i]) != null;
                 i = nextIndex(i, len)) {
                // 获取当前 Entry 类实例所指向的 ThreadLocal 类实例
                ThreadLocal<?> k = e.get();

                // If we find key, then we need to swap it
                // with the stale entry to maintain hash table order.
                // The newly stale slot, or any other stale slot
                // encountered above it, can then be sent to expungeStaleEntry
                // to remove or rehash all of the other entries in run.

                // 下面的 if 语句中有一段元素交换的代码，原因见上面的英文注释

                // 如果该 ThreadLocal 类实例就是当前这个 ThreadLocal 类实例
                //（说明此时调用这个 set() 方法是为了替换原先存放在这个 ThreadLocal 中的变量）
                if (k == key) {
                    // 使用新值（即 当前调用该 set() 方法需要设置的值（即 当要存放到 ThreadLocal 中的值））覆盖旧值
                    e.value = value;

                    // 就是这里进行的元素交换
                    // 交换完成之后
                    // 原先 “不新鲜” 的 Entry 类实例所在的槽位，就变成了
                    // 新的值（即当前调用该 set() 方法所要设置的值（即 当要存放到 ThreadLocal 中的值））转换成一个新的 Entry 类实例了
                    // 而当前遍历到的槽位（即 tab[i]）里存放的就变成了 “不新鲜” 的 Entry 类实例了
                    tab[i] = tab[staleSlot];
                    tab[staleSlot] = e;

                    // Start expunge at preceding stale entry if it exists
                    // 这里判断这两个变量是否相等
                    // 如果是
                    // 那么就表示上那个向前遍历的 for 循环没有找到其他的 “不新鲜” 的 Entry 类实例
                    if (slotToExpunge == staleSlot)
                        // 由于上面进行了元素交换，所以当前 i 所表示的槽位（下标）才是 “不新鲜” 的 Entry 类实例所在下标
                        // 因此
                        // 这里要把该 i 的值赋值给该 slotToExpunge 变量
                        slotToExpunge = i;

                    // 下面的代码就是要清理所有的 “不新鲜” 的 Entry 类实例了

                    // 这行代码的功能就是清理当前这个 run （它的含义见该方法上面的注释）中的所有 “不新鲜” 的 Entry 类实例
                    // 注意
                    // “清理” 就是指把该 Entry 类实例所在槽位置为 null
                    // 以便 GC 时回收 Entry 类实例中强引用（即 成员变量 value）所指向的那个对象
                    //（也就是 “所谓” 存放在 ThreadLocal 中的变量（因为当我们理解 ThreadLocal 类之后，就知道该变量存放在 ThreadLocal 中只是给人的外部感受而已））
                    // 以防止内存泄漏
                    cleanSomeSlots(expungeStaleEntry(slotToExpunge), len);

                    // 完成后直接返回
                    return;
                }

                // If we didn't find stale entry on backward scan, the
                // first stale entry seen while scanning for key is the
                // first still present in the run.
                if (k == null && slotToExpunge == staleSlot)
                    slotToExpunge = i;
            }

            // If key not found, put new entry in stale slot
            tab[staleSlot].value = null;
            tab[staleSlot] = new Entry(key, value);

            // If there are any other stale entries in run, expunge them
            if (slotToExpunge != staleSlot)
                // 这行代码的功能就是清理当前这个 run （它的含义见该方法上面的注释）中的所有 “不新鲜” 的 Entry 类实例
                // 注意
                // “清理” 就是指把该 Entry 类实例所在槽位置为 null
                // 以便 GC 时回收 Entry 类实例中强引用（即 成员变量 value）所指向的那个对象
                //（也就是 “所谓” 存放在 ThreadLocal 中的变量（因为当我们理解 ThreadLocal 类之后，就知道该变量存放在 ThreadLocal 中只是给人的外部感受而已））
                // 以防止内存泄漏
                cleanSomeSlots(expungeStaleEntry(slotToExpunge), len);
        }

        /**
         * Expunge a stale entry by rehashing any possibly colliding entries
         * lying between staleSlot and the next null slot.  This also expunges
         * any other stale entries encountered before the trailing null.  See
         * Knuth, Section 6.4
         *
         * @param staleSlot index of slot known to have null key
         * @return the index of the next null slot after staleSlot
         * (all between staleSlot and this slot will have been checked
         * for expunging).
         */
        private int expungeStaleEntry(int staleSlot) {
            Entry[] tab = table;
            int len = tab.length;

            // expunge entry at staleSlot
            // “清理” 当前 “不新鲜” 的 Entry 类实例
            // 即
            // 把它的 Value（即 存放在 ThreadLocal 中的那个变量）置为 null（以便后期 GC 时回收该对象）
            // 并且
            // 把该槽位置为 null
            tab[staleSlot].value = null;
            tab[staleSlot] = null;
            size--;

            // Rehash until we encounter null
            Entry e;
            int i;

            // 该 for 循环用于遍历从当前槽位（即 形参 staleSlot 所对应的下标）开始，到下一个元素为 null 的槽位之间的子数组中的所有元素
            // 清理该范围内的所有 “不新鲜” 的 Entry 类实例
            for (i = nextIndex(staleSlot, len); (e = tab[i]) != null; i = nextIndex(i, len)) {
                // 获取当前 Entry 类实例所指向的 ThreadLocal 类实例
                ThreadLocal<?> k = e.get();

                // 如果 ThreadLocal 类实例为 null，即表示当前这个 Entry 类实例是 “不新鲜” 的
                // 那么
                // 就 “清理” 当前 “不新鲜” 的 Entry 类实例
                // 即
                // 把它的 Value（即 存放在 ThreadLocal 中的那个变量）置为 null（以便后期 GC 时回收该对象）
                // 并且
                // 把该槽位置为 null
                if (k == null) {
                    e.value = null;
                    tab[i] = null;
                    size--;
                }
                else {
                    // 如果 ThreadLocal 类实例不为 null，即表示当前这个 Entry 类实例是 “新鲜” 的
                    // 那么
                    // 此时就要对该元素（即 当前槽位中的 Entry 类实例）进行一次 rehash
                    int h = k.threadLocalHashCode & (len - 1);
                    if (h != i) {
                        tab[i] = null;

                        // Unlike Knuth 6.4 Algorithm R, we must scan until
                        // null because multiple entries could have been stale.
                        while (tab[h] != null)
                            h = nextIndex(h, len);
                        tab[h] = e;
                    }
                }
            }
            return i;
        }

        /**
         * Heuristically scan some cells looking for stale entries.
         * This is invoked when either a new element is added, or
         * another stale one has been expunged. It performs a
         * logarithmic number of scans, as a balance between no
         * scanning (fast but retains garbage) and a number of scans
         * proportional to number of elements, that would find all
         * garbage but would cause some insertions to take O(n) time.
         *
         * @param i a position known NOT to hold a stale entry. The
         * scan starts at the element after i.
         *
         * @param n scan control: {@code log2(n)} cells are scanned,
         * unless a stale entry is found, in which case
         * {@code log2(table.length)-1} additional cells are scanned.
         * When called from insertions, this parameter is the number
         * of elements, but when from replaceStaleEntry, it is the
         * table length. (Note: all this could be changed to be either
         * more or less aggressive by weighting n instead of just
         * using straight log n. But this version is simple, fast, and
         * seems to work well.)
         *
         * @return true if any stale entries have been removed.
         */
        private boolean cleanSomeSlots(int i, int n) {
            boolean removed = false;
            Entry[] tab = table;
            int len = tab.length;
            do {
                i = nextIndex(i, len);
                Entry e = tab[i];
                if (e != null && e.get() == null) {
                    n = len;
                    removed = true;
                    i = expungeStaleEntry(i);
                }
            } while ( (n >>>= 1) != 0);
            return removed;
        }

        /**
         * Re-pack and/or re-size the table. First scan the entire
         * table removing stale entries. If this doesn't sufficiently
         * shrink the size of the table, double the table size.
         */
        private void rehash() {
            expungeStaleEntries();

            // Use lower threshold for doubling to avoid hysteresis
            if (size >= threshold - threshold / 4)
                resize();
        }

        /**
         * Double the capacity of the table.
         */
        private void resize() {
            Entry[] oldTab = table;
            int oldLen = oldTab.length;
            int newLen = oldLen * 2;
            Entry[] newTab = new Entry[newLen];
            int count = 0;

            for (int j = 0; j < oldLen; ++j) {
                Entry e = oldTab[j];
                if (e != null) {
                    ThreadLocal<?> k = e.get();
                    if (k == null) {
                        e.value = null; // Help the GC
                    } else {
                        int h = k.threadLocalHashCode & (newLen - 1);
                        while (newTab[h] != null)
                            h = nextIndex(h, newLen);
                        newTab[h] = e;
                        count++;
                    }
                }
            }

            setThreshold(newLen);
            size = count;
            table = newTab;
        }

        /**
         * Expunge all stale entries in the table.
         */
        private void expungeStaleEntries() {
            Entry[] tab = table;
            int len = tab.length;
            for (int j = 0; j < len; j++) {
                Entry e = tab[j];
                if (e != null && e.get() == null)
                    expungeStaleEntry(j);
            }
        }
    }
}
