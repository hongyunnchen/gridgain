// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.lang.utils;

import org.gridgain.grid.lang.*;
import org.gridgain.grid.typedef.*;
import org.gridgain.grid.typedef.internal.*;
import org.gridgain.grid.util.tostring.*;
import org.jetbrains.annotations.*;
import java.lang.ref.*;
import java.util.*;

/**
 * Concurrent weak hash set implementation.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.1.1c.19062011
 */
public class GridConcurrentWeakHashSet<E> implements Set<E> {
    /** Empty array. */
    private static final Object[] EMPTY_ARR = new Object[0];

    /** Reference store. */
    @GridToStringInclude
    private GridConcurrentHashSet<WeakReferenceElement<E>> store;

    /** Reference queue. */
    @GridToStringExclude
    private final ReferenceQueue<E> gcQ = new ReferenceQueue<E>();

    /** Reference factory. */
    private final GridClosure<E, WeakReferenceElement<E>> fact = new GridClosure<E, WeakReferenceElement<E>>() {
        @Override public WeakReferenceElement<E> apply(E e) {
            assert e != null;

            return new WeakReferenceElement<E>(e, gcQ);
        }
    };

    /**
     * Creates a new, empty set with a default initial capacity,
     * load factor, and concurrencyLevel.
     */
    public GridConcurrentWeakHashSet() {
        store = new GridConcurrentHashSet<WeakReferenceElement<E>>();
    }

    /**
     * Creates a new, empty set with the specified initial
     * capacity, and with default load factor and concurrencyLevel.
     *
     * @param initCap The initial capacity. The implementation
     *      performs internal sizing to accommodate this many elements.
     * @throws IllegalArgumentException if the initial capacity of
     *      elements is negative.
     */
    public GridConcurrentWeakHashSet(int initCap) {
        store = new GridConcurrentHashSet<WeakReferenceElement<E>>(initCap);
    }

    /**
     * Creates a new, empty set with the specified initial
     * capacity, load factor, and concurrency level.
     *
     * @param initCap The initial capacity. The implementation
     *      performs internal sizing to accommodate this many elements.
     * @param loadFactor The load factor threshold, used to control resizing.
     *      Resizing may be performed when the average number of elements per
     *      bin exceeds this threshold.
     * @param conLevel The estimated number of concurrently
     *      updating threads. The implementation performs internal sizing
     *      to try to accommodate this many threads.
     * @throws IllegalArgumentException if the initial capacity is
     *      negative or the load factor or concurrency level are
     *      non-positive.
     */
    public GridConcurrentWeakHashSet(int initCap, float loadFactor, int conLevel) {
        store = new GridConcurrentHashSet<WeakReferenceElement<E>>(initCap, loadFactor, conLevel);
    }

    /**
     * Constructs a new set containing the elements in the specified
     * collection, with default load factor and an initial
     * capacity sufficient to contain the elements in the specified collection.
     *
     * @param c Collection to add.
     */
    public GridConcurrentWeakHashSet(Collection<E> c) {
        this(c.size());

        addAll(c);
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"SimplifiableIfStatement"})
    @Override public boolean add(E e) {
        A.notNull(e, "e");

        removeStale();

        if (!contains(e)) {
            return store.add(fact.apply(e));
        }

        return false;
    }

    /** {@inheritDoc} */
    @Override public boolean addAll(@Nullable Collection<? extends E> c) {
        boolean res = false;

        if (!F.isEmpty(c)) {
            assert c != null;

            for (E e : c) {
                res |= add(e);
            }
        }

        return res;
    }

    /** {@inheritDoc} */
    @Override public boolean retainAll(@Nullable Collection<?> c) {
        removeStale();

        boolean res = false;

        if (!F.isEmpty(c)) {
            assert c != null;

            Iterator<WeakReferenceElement<E>> iter = store.iterator();

            while (iter.hasNext()) {
                if (!c.contains(iter.next().get())) {
                    iter.remove();

                    res = true;
                }
            }
        }

        return res;
    }

    /** {@inheritDoc} */
    @Override public int size() {
        removeStale();

        return store.size();
    }

    /** {@inheritDoc} */
    @Override public boolean isEmpty() {
        removeStale();

        return store.isEmpty();
    }

    /** {@inheritDoc} */
    @Override public boolean contains(@Nullable Object o) {
        removeStale();

        if (!store.isEmpty() && o != null) {
            for (WeakReferenceElement ref : store) {
                Object reft = ref.get();

                if (reft != null && reft.equals(o)) {
                    return true;
                }
            }
        }

        return false;
    }

    /** {@inheritDoc} */
    @Override public boolean containsAll(@Nullable Collection<?> c) {
        if (F.isEmpty(c)) {
            return false;
        }

        assert c != null;

        for (Object o : c) {
            if (!contains(o)) {
                return false;
            }
        }

        return true;
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"ToArrayCallWithZeroLengthArrayArgument"})
    @Override public Object[] toArray() {
        return toArray(EMPTY_ARR);
    }

    /** {@inheritDoc} */
    @SuppressWarnings({"SuspiciousToArrayCall"})
    @Override public <T> T[] toArray(T[] a) {
        removeStale();

        Collection<E> elems = new LinkedList<E>();

        for (WeakReferenceElement<E> ref : store) {
            E e = ref.get();

            if (e != null) {
                elems.add(e);
            }
        }

        return elems.toArray(a);
    }

    /** {@inheritDoc} */
    @Override public Iterator<E> iterator() {
        removeStale();

        return new Iterator<E>() {
            /** Storage iterator. */
            private Iterator<WeakReferenceElement<E>> iter = store.iterator();

            /** Current element. */
            private E elem;

            /** {@inheritDoc} */
            @Override public boolean hasNext() {
                if (elem == null) {
                    while (iter.hasNext()) {
                        WeakReferenceElement<E> ref = iter.next();

                        E e;

                        if (ref != null && (e = ref.get()) != null) {
                            elem = e;

                            break;
                        }
                        else {
                            removeStale();
                        }
                    }
                }

                return elem != null;
            }

            /** {@inheritDoc} */
            @SuppressWarnings({"IteratorNextCanNotThrowNoSuchElementException"})
            @Override public E next() {
                if (elem == null) {
                    if (!hasNext()) {
                        throw new NoSuchElementException();
                    }
                }

                E res = elem;

                elem = null;

                return res;
            }

            /** {@inheritDoc} */
            @Override public void remove() {
                iter.remove();
            }
        };
    }

    /** {@inheritDoc} */
    @Override public void clear() {
        store.clear();
    }

    /** {@inheritDoc} */
    @Override public boolean remove(@Nullable Object o) {
        removeStale();

        if (o != null) {
            for (Iterator<WeakReferenceElement<E>> iter = store.iterator(); iter.hasNext();) {
                Object reft = iter.next().get();

                if (reft != null && reft.equals(o)) {
                    iter.remove();

                    return true;
                }
            }
        }

        return false;
    }

    /** {@inheritDoc} */
    @Override public boolean removeAll(@Nullable Collection<?> c) {
        boolean res = false;

        if (!F.isEmpty(c)) {
            assert c != null;

            for (Object o : c) {
                res |= remove(o);
            }
        }

        return res;
    }

    /** {@inheritDoc} */
    @Override public boolean equals(@Nullable Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof GridConcurrentWeakHashSet)) {
            return false;
        }

        GridConcurrentWeakHashSet that = (GridConcurrentWeakHashSet)o;

        return store.equals(that.store);
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {
        return store.hashCode();
    }

    /**
     * Removes stale references.
     */
    private void removeStale() {
        WeakReferenceElement<E> ref;

        while ((ref = (WeakReferenceElement<E>) gcQ.poll()) != null) {
            store.remove(ref);

            onGc(ref.get());
        }
    }

    /**
     * This method is called on every element when it gets GC-ed.
     *
     * @param e Element that is about to get GC-ed.
     */
    protected void onGc(E e) {
        // No-op.
    }

    /**
     * Weak reference implementation for this set.
     */
    private static class WeakReferenceElement<E> extends WeakReference<E> {
        /** Element hash code. */
        private int hashCode;

        /**
         * Creates weak reference element.
         *
         * @param reft Referent.
         * @param queue Reference queue.
         */
        private WeakReferenceElement(E reft, ReferenceQueue<? super E> queue) {
            super(reft, queue);

            hashCode = reft != null ? reft.hashCode() : 0;
        }

        /** {@inheritDoc} */
        @Override public boolean equals(Object o) {
            if (this == o) {
                return true;
            }

            if (!(o instanceof WeakReferenceElement)) {
                return false;
            }

            E thisReft = get();

            Object thatReft = ((Reference)o).get();

            return thisReft != null ? thisReft.equals(thatReft) : thatReft == null;
        }

        /** {@inheritDoc} */
        @Override public int hashCode() {
            return hashCode;
        }
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridConcurrentWeakHashSet.class, this);
    }
}
