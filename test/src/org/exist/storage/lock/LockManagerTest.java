package org.exist.storage.lock;

import net.jcip.annotations.ThreadSafe;
import org.exist.util.LockException;
import org.exist.xmldb.XmldbURI;
import org.junit.Test;

import java.util.Stack;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static org.junit.Assert.*;

/**
 * Tests to ensure the correct behaviour of the LockManager
 *
 * @author Adam Retter <adam.retter@googlemail.com>
 */
public class LockManagerTest {

    private static final int CONCURRENCY_LEVEL = 100;

    @Test
    public void getCollectionLock_isStripedByPath() {
        final LockManager lockManager = new LockManager(CONCURRENCY_LEVEL);

        final ReentrantReadWriteLock dbLock1 = lockManager.getCollectionLock("/db");
        assertNotNull(dbLock1);

        final ReentrantReadWriteLock dbLock2 = lockManager.getCollectionLock("/db");
        assertNotNull(dbLock2);

        assertTrue(dbLock1 == dbLock2);

        final ReentrantReadWriteLock abcLock = lockManager.getCollectionLock("/db/a/b/c");
        assertNotNull(abcLock);
        assertFalse(dbLock1 == abcLock);

        final ReentrantReadWriteLock defLock = lockManager.getCollectionLock("/db/d/e/f");
        assertNotNull(defLock);
        assertFalse(dbLock1 == defLock);

        assertFalse(abcLock == defLock);
    }

    /**
     * When acquiring a READ lock on the root Collection
     * ensure that we only take a single READ lock on the
     * root Collection
     */
    @Test
    public void acquireCollectionReadLock_root() throws LockException {
        final LockTable lockTable = LockTable.getInstance();

        final LockEventRecordingListener lockEventRecordingListener = new LockEventRecordingListener();
        lockTable.registerListener(lockEventRecordingListener);

        final LockManager lockManager = new LockManager(CONCURRENCY_LEVEL);
        try(final ManagedCollectionLock rootLock
                    = lockManager.acquireCollectionReadLock(XmldbURI.ROOT_COLLECTION_URI)) {
            assertNotNull(rootLock);
        }

        lockTable.deregisterListener(lockEventRecordingListener);

        // wait for the listener to be deregistered
        while(lockEventRecordingListener.isRegistered()) {}

        final Stack<LockTable.LockAction> events = lockEventRecordingListener.getEvents();
        assertEquals(3, events.size());
        final LockTable.LockAction event3 = events.pop();
        final LockTable.LockAction event2 = events.pop();
        final LockTable.LockAction event1 = events.pop();

        assertEquals(LockTable.LockAction.Action.Attempt, event1.action);
        assertEquals(XmldbURI.ROOT_COLLECTION, event1.id);
        assertEquals(Lock.LockMode.READ_LOCK, event1.mode);

        assertEquals(LockTable.LockAction.Action.Acquired, event2.action);
        assertEquals(XmldbURI.ROOT_COLLECTION, event2.id);
        assertEquals(Lock.LockMode.READ_LOCK, event2.mode);

        // we now expect to release the lock on /db (as the managed lock was closed)
        assertEquals(LockTable.LockAction.Action.Released, event3.action);
        assertEquals(XmldbURI.ROOT_COLLECTION, event3.id);
        assertEquals(Lock.LockMode.READ_LOCK, event3.mode);
    }

    /**
     * When acquiring a READ lock on a sub-collection of the root
     * Collection ensure that we hold a single READ lock on the
     * sub-collection and perform top-down lock-coupling on the
     * collection hierarchy to get there
     */
    @Test
    public void acquireCollectionReadLock_depth2() throws LockException {
        final LockTable lockTable = LockTable.getInstance();

        final LockEventRecordingListener lockEventRecordingListener = new LockEventRecordingListener();
        lockTable.registerListener(lockEventRecordingListener);

        final String collectionPath = "/db/colA";

        final LockManager lockManager = new LockManager(CONCURRENCY_LEVEL);
        try(final ManagedCollectionLock colALock
                    = lockManager.acquireCollectionReadLock(XmldbURI.create(collectionPath))) {
            assertNotNull(colALock);
        }

        lockTable.deregisterListener(lockEventRecordingListener);

        // wait for the listener to be deregistered
        while(lockEventRecordingListener.isRegistered()) {}

        final Stack<LockTable.LockAction> events = lockEventRecordingListener.getEvents();
        assertEquals(6, events.size());
        final LockTable.LockAction event6 = events.pop();
        final LockTable.LockAction event5 = events.pop();
        final LockTable.LockAction event4 = events.pop();
        final LockTable.LockAction event3 = events.pop();
        final LockTable.LockAction event2 = events.pop();
        final LockTable.LockAction event1 = events.pop();

        assertEquals(LockTable.LockAction.Action.Attempt, event1.action);
        assertEquals(XmldbURI.ROOT_COLLECTION, event1.id);
        assertEquals(Lock.LockMode.READ_LOCK, event1.mode);

        assertEquals(LockTable.LockAction.Action.Acquired, event2.action);
        assertEquals(XmldbURI.ROOT_COLLECTION, event2.id);
        assertEquals(Lock.LockMode.READ_LOCK, event2.mode);

        // we now expect to couple /db with /db/colA by acquiring /db/colA whilst holding /db
        assertEquals(LockTable.LockAction.Action.Attempt, event3.action);
        assertEquals(collectionPath, event3.id);
        assertEquals(Lock.LockMode.READ_LOCK, event3.mode);

        assertEquals(LockTable.LockAction.Action.Acquired, event4.action);
        assertEquals(collectionPath, event4.id);
        assertEquals(Lock.LockMode.READ_LOCK, event4.mode);

        // we now expect to release the lock on /db
        assertEquals(LockTable.LockAction.Action.Released, event5.action);
        assertEquals(XmldbURI.ROOT_COLLECTION, event5.id);
        assertEquals(Lock.LockMode.READ_LOCK, event5.mode);

        // we now expect to release the lock on /db/colA (as the managed lock was closed)
        assertEquals(LockTable.LockAction.Action.Released, event6.action);
        assertEquals(collectionPath, event6.id);
        assertEquals(Lock.LockMode.READ_LOCK, event6.mode);
    }

    /**
     * When acquiring a READ lock on a descendant-collection of the root
     * Collection ensure that we hold a single READ lock on the
     * descendant-collection and perform top-down lock-coupling on the
     * collection hierarchy to get there
     */
    @Test
    public void acquireCollectionReadLock_depth3() throws LockException {
        final LockTable lockTable = LockTable.getInstance();

        final LockEventRecordingListener lockEventRecordingListener = new LockEventRecordingListener();
        lockTable.registerListener(lockEventRecordingListener);

        final String collectionAPath = "/db/colA";
        final String collectionBPath = collectionAPath + "/colB";

        final LockManager lockManager = new LockManager(CONCURRENCY_LEVEL);
        try(final ManagedCollectionLock colBLock
                    = lockManager.acquireCollectionReadLock(XmldbURI.create(collectionBPath))) {
            assertNotNull(colBLock);
        }

        lockTable.deregisterListener(lockEventRecordingListener);

        // wait for the listener to be deregistered
        while(lockEventRecordingListener.isRegistered()) {}

        final Stack<LockTable.LockAction> events = lockEventRecordingListener.getEvents();
        assertEquals(9, events.size());
        final LockTable.LockAction event9 = events.pop();
        final LockTable.LockAction event8 = events.pop();
        final LockTable.LockAction event7 = events.pop();
        final LockTable.LockAction event6 = events.pop();
        final LockTable.LockAction event5 = events.pop();
        final LockTable.LockAction event4 = events.pop();
        final LockTable.LockAction event3 = events.pop();
        final LockTable.LockAction event2 = events.pop();
        final LockTable.LockAction event1 = events.pop();

        assertEquals(LockTable.LockAction.Action.Attempt, event1.action);
        assertEquals(XmldbURI.ROOT_COLLECTION, event1.id);
        assertEquals(Lock.LockMode.READ_LOCK, event1.mode);

        assertEquals(LockTable.LockAction.Action.Acquired, event2.action);
        assertEquals(XmldbURI.ROOT_COLLECTION, event2.id);
        assertEquals(Lock.LockMode.READ_LOCK, event2.mode);

        // we now expect to couple /db with /db/colA by acquiring /db/colA whilst holding /db
        assertEquals(LockTable.LockAction.Action.Attempt, event3.action);
        assertEquals(collectionAPath, event3.id);
        assertEquals(Lock.LockMode.READ_LOCK, event3.mode);

        assertEquals(LockTable.LockAction.Action.Acquired, event4.action);
        assertEquals(collectionAPath, event4.id);
        assertEquals(Lock.LockMode.READ_LOCK, event4.mode);

        // we now expect to release the lock on /db
        assertEquals(LockTable.LockAction.Action.Released, event5.action);
        assertEquals(XmldbURI.ROOT_COLLECTION, event5.id);
        assertEquals(Lock.LockMode.READ_LOCK, event5.mode);

        // we now expect to couple /db/colA with /db/colA/colB by acquiring /db/colA/colB whilst holding /db/colA
        assertEquals(LockTable.LockAction.Action.Attempt, event6.action);
        assertEquals(collectionBPath, event6.id);
        assertEquals(Lock.LockMode.READ_LOCK, event6.mode);

        assertEquals(LockTable.LockAction.Action.Acquired, event7.action);
        assertEquals(collectionBPath, event7.id);
        assertEquals(Lock.LockMode.READ_LOCK, event7.mode);

        // we now expect to release the lock on /db/colA
        assertEquals(LockTable.LockAction.Action.Released, event8.action);
        assertEquals(collectionAPath, event8.id);
        assertEquals(Lock.LockMode.READ_LOCK, event8.mode);

        // we now expect to release the lock on /db/colA/colB (as the managed lock was closed)
        assertEquals(LockTable.LockAction.Action.Released, event9.action);
        assertEquals(collectionBPath, event9.id);
        assertEquals(Lock.LockMode.READ_LOCK, event9.mode);
    }

    /**
     * When acquiring a WRITE lock on the root Collection
     * ensure that we only take a single WRITE lock on the
     * root Collection
     */
    @Test
    public void acquireCollectionWriteLock_root_withoutLockParent() throws LockException {
        acquireCollectionWriteLock_root(false);
    }

    /**
     * When acquiring a WRITE lock on the root Collection
     * ensure that we only take a single WRITE lock on the
     * root Collection... even when lockParent is set
     */
    @Test
    public void acquireCollectionWriteLock_root_withLockParent() throws LockException {
        acquireCollectionWriteLock_root(true);
    }

    private void acquireCollectionWriteLock_root(final boolean lockParent) throws LockException {
        final LockTable lockTable = LockTable.getInstance();

        final LockEventRecordingListener lockEventRecordingListener = new LockEventRecordingListener();
        lockTable.registerListener(lockEventRecordingListener);

        final LockManager lockManager = new LockManager(CONCURRENCY_LEVEL);
        try(final ManagedCollectionLock rootLock
                    = lockManager.acquireCollectionWriteLock(XmldbURI.ROOT_COLLECTION_URI, lockParent)) {
            assertNotNull(rootLock);
        }

        lockTable.deregisterListener(lockEventRecordingListener);

        // wait for the listener to be deregistered
        while(lockEventRecordingListener.isRegistered()) {}

        final Stack<LockTable.LockAction> events = lockEventRecordingListener.getEvents();
        assertEquals(3, events.size());
        final LockTable.LockAction event3 = events.pop();
        final LockTable.LockAction event2 = events.pop();
        final LockTable.LockAction event1 = events.pop();

        assertEquals(LockTable.LockAction.Action.Attempt, event1.action);
        assertEquals(XmldbURI.ROOT_COLLECTION, event1.id);
        assertEquals(Lock.LockMode.WRITE_LOCK, event1.mode);

        assertEquals(LockTable.LockAction.Action.Acquired, event2.action);
        assertEquals(XmldbURI.ROOT_COLLECTION, event2.id);
        assertEquals(Lock.LockMode.WRITE_LOCK, event2.mode);

        // we now expect to release the lock on /db (as the managed lock was closed)
        assertEquals(LockTable.LockAction.Action.Released, event3.action);
        assertEquals(XmldbURI.ROOT_COLLECTION, event3.id);
        assertEquals(Lock.LockMode.WRITE_LOCK, event3.mode);
    }

    /**
     * When acquiring a WRITE lock on a sub-collection of the root (without locking the parent)
     * Collection ensure that we hold a single WRITE lock on the
     * sub-collection and perform top-down lock-coupling with READ locks on the
     * collection hierarchy to get there
     */
    @Test
    public void acquireCollectionWriteLock_depth2_withoutLockParent() throws LockException {
        final LockTable lockTable = LockTable.getInstance();

        final LockEventRecordingListener lockEventRecordingListener = new LockEventRecordingListener();
        lockTable.registerListener(lockEventRecordingListener);

        final String collectionPath = "/db/colA";

        final LockManager lockManager = new LockManager(CONCURRENCY_LEVEL);
        final boolean lockParent = false;
        try(final ManagedCollectionLock colALock
                    = lockManager.acquireCollectionWriteLock(XmldbURI.create(collectionPath), lockParent)) {
            assertNotNull(colALock);
        }

        lockTable.deregisterListener(lockEventRecordingListener);

        // wait for the listener to be deregistered
        while(lockEventRecordingListener.isRegistered()) {}

        final Stack<LockTable.LockAction> events = lockEventRecordingListener.getEvents();
        assertEquals(6, events.size());
        final LockTable.LockAction event6 = events.pop();
        final LockTable.LockAction event5 = events.pop();
        final LockTable.LockAction event4 = events.pop();
        final LockTable.LockAction event3 = events.pop();
        final LockTable.LockAction event2 = events.pop();
        final LockTable.LockAction event1 = events.pop();

        assertEquals(LockTable.LockAction.Action.Attempt, event1.action);
        assertEquals(XmldbURI.ROOT_COLLECTION, event1.id);
        assertEquals(Lock.LockMode.READ_LOCK, event1.mode);

        assertEquals(LockTable.LockAction.Action.Acquired, event2.action);
        assertEquals(XmldbURI.ROOT_COLLECTION, event2.id);
        assertEquals(Lock.LockMode.READ_LOCK, event2.mode);

        // we now expect to couple /db with /db/colA by acquiring /db/colA whilst holding /db
        assertEquals(LockTable.LockAction.Action.Attempt, event3.action);
        assertEquals(collectionPath, event3.id);
        assertEquals(Lock.LockMode.WRITE_LOCK, event3.mode);

        assertEquals(LockTable.LockAction.Action.Acquired, event4.action);
        assertEquals(collectionPath, event4.id);
        assertEquals(Lock.LockMode.WRITE_LOCK, event4.mode);

        // we now expect to release the lock on /db
        assertEquals(LockTable.LockAction.Action.Released, event5.action);
        assertEquals(XmldbURI.ROOT_COLLECTION, event5.id);
        assertEquals(Lock.LockMode.READ_LOCK, event5.mode);

        // we now expect to release the lock on /db/colA (as the managed lock was closed)
        assertEquals(LockTable.LockAction.Action.Released, event6.action);
        assertEquals(collectionPath, event6.id);
        assertEquals(Lock.LockMode.WRITE_LOCK, event6.mode);
    }

    /**
     * When acquiring a WRITE lock on a sub-collection of the root (with parent locking)
     * Collection ensure that we hold a single WRITE lock on the
     * sub-collection and a single WRITE lock on the parent, by performing top-down lock-coupling
     * with READ locks (unless as in this-case the parent is the root, then WRITE locks) on the
     * collection hierarchy to get there
     */
    @Test
    public void acquireCollectionWriteLock_depth2_withLockParent() throws LockException {
        final LockTable lockTable = LockTable.getInstance();

        final LockEventRecordingListener lockEventRecordingListener = new LockEventRecordingListener();
        lockTable.registerListener(lockEventRecordingListener);

        final String collectionPath = "/db/colA";

        final LockManager lockManager = new LockManager(CONCURRENCY_LEVEL);
        final boolean lockParent = true;
        try(final ManagedCollectionLock colALock
                    = lockManager.acquireCollectionWriteLock(XmldbURI.create(collectionPath), lockParent)) {
            assertNotNull(colALock);
        }

        lockTable.deregisterListener(lockEventRecordingListener);

        // wait for the listener to be deregistered
        while(lockEventRecordingListener.isRegistered()) {}

        final Stack<LockTable.LockAction> events = lockEventRecordingListener.getEvents();
        assertEquals(6, events.size());
        final LockTable.LockAction event6 = events.pop();
        final LockTable.LockAction event5 = events.pop();
        final LockTable.LockAction event4 = events.pop();
        final LockTable.LockAction event3 = events.pop();
        final LockTable.LockAction event2 = events.pop();
        final LockTable.LockAction event1 = events.pop();

        assertEquals(LockTable.LockAction.Action.Attempt, event1.action);
        assertEquals(XmldbURI.ROOT_COLLECTION, event1.id);
        assertEquals(Lock.LockMode.WRITE_LOCK, event1.mode);

        assertEquals(LockTable.LockAction.Action.Acquired, event2.action);
        assertEquals(XmldbURI.ROOT_COLLECTION, event2.id);
        assertEquals(Lock.LockMode.WRITE_LOCK, event2.mode);

        // we now expect to couple /db with /db/colA by acquiring /db/colA whilst holding /db
        assertEquals(LockTable.LockAction.Action.Attempt, event3.action);
        assertEquals(collectionPath, event3.id);
        assertEquals(Lock.LockMode.WRITE_LOCK, event3.mode);

        assertEquals(LockTable.LockAction.Action.Acquired, event4.action);
        assertEquals(collectionPath, event4.id);
        assertEquals(Lock.LockMode.WRITE_LOCK, event4.mode);

        // we now expect to release the lock on /db/colA and then /db (as the managed lock (of both locks) was closed)
        assertEquals(LockTable.LockAction.Action.Released, event5.action);
        assertEquals(collectionPath, event5.id);
        assertEquals(Lock.LockMode.WRITE_LOCK, event5.mode);

        assertEquals(LockTable.LockAction.Action.Released, event6.action);
        assertEquals(XmldbURI.ROOT_COLLECTION, event6.id);
        assertEquals(Lock.LockMode.WRITE_LOCK, event6.mode);
    }

    /**
     * When acquiring a WRITE lock on a descendant-collection of the root (without locking the parent)
     * Collection ensure that we hold a single WRITE lock on the
     * descendant-collection and perform top-down lock-coupling with READ locks on the
     * collection hierarchy to get there
     */
    @Test
    public void acquireCollectionWriteLock_depth3_withoutLockParent() throws LockException {
        final LockTable lockTable = LockTable.getInstance();

        final LockEventRecordingListener lockEventRecordingListener = new LockEventRecordingListener();
        lockTable.registerListener(lockEventRecordingListener);

        final String collectionAPath = "/db/colA";
        final String collectionBPath = collectionAPath + "/colB";

        final LockManager lockManager = new LockManager(CONCURRENCY_LEVEL);
        final boolean lockParent = false;
        try(final ManagedCollectionLock colBLock
                    = lockManager.acquireCollectionWriteLock(XmldbURI.create(collectionBPath), lockParent)) {
            assertNotNull(colBLock);
        }

        lockTable.deregisterListener(lockEventRecordingListener);

        // wait for the listener to be deregistered
        while(lockEventRecordingListener.isRegistered()) {}

        final Stack<LockTable.LockAction> events = lockEventRecordingListener.getEvents();
        assertEquals(9, events.size());
        final LockTable.LockAction event9 = events.pop();
        final LockTable.LockAction event8 = events.pop();
        final LockTable.LockAction event7 = events.pop();
        final LockTable.LockAction event6 = events.pop();
        final LockTable.LockAction event5 = events.pop();
        final LockTable.LockAction event4 = events.pop();
        final LockTable.LockAction event3 = events.pop();
        final LockTable.LockAction event2 = events.pop();
        final LockTable.LockAction event1 = events.pop();

        assertEquals(LockTable.LockAction.Action.Attempt, event1.action);
        assertEquals(XmldbURI.ROOT_COLLECTION, event1.id);
        assertEquals(Lock.LockMode.READ_LOCK, event1.mode);

        assertEquals(LockTable.LockAction.Action.Acquired, event2.action);
        assertEquals(XmldbURI.ROOT_COLLECTION, event2.id);
        assertEquals(Lock.LockMode.READ_LOCK, event2.mode);

        // we now expect to couple /db with /db/colA by acquiring /db/colA whilst holding /db
        assertEquals(LockTable.LockAction.Action.Attempt, event3.action);
        assertEquals(collectionAPath, event3.id);
        assertEquals(Lock.LockMode.READ_LOCK, event3.mode);

        assertEquals(LockTable.LockAction.Action.Acquired, event4.action);
        assertEquals(collectionAPath, event4.id);
        assertEquals(Lock.LockMode.READ_LOCK, event4.mode);

        // we now expect to release the lock on /db
        assertEquals(LockTable.LockAction.Action.Released, event5.action);
        assertEquals(XmldbURI.ROOT_COLLECTION, event5.id);
        assertEquals(Lock.LockMode.READ_LOCK, event5.mode);

        // we now expect to couple /db/colA with /db/colA/colB by acquiring /db/colA/colB whilst holding /db/colA
        assertEquals(LockTable.LockAction.Action.Attempt, event6.action);
        assertEquals(collectionBPath, event6.id);
        assertEquals(Lock.LockMode.WRITE_LOCK, event6.mode);

        assertEquals(LockTable.LockAction.Action.Acquired, event7.action);
        assertEquals(collectionBPath, event7.id);
        assertEquals(Lock.LockMode.WRITE_LOCK, event7.mode);

        // we now expect to release the lock on /db/colA
        assertEquals(LockTable.LockAction.Action.Released, event8.action);
        assertEquals(collectionAPath, event8.id);
        assertEquals(Lock.LockMode.READ_LOCK, event8.mode);

        // we now expect to release the lock on /db/colA/colB (as the managed lock was closed)
        assertEquals(LockTable.LockAction.Action.Released, event9.action);
        assertEquals(collectionBPath, event9.id);
        assertEquals(Lock.LockMode.WRITE_LOCK, event9.mode);
    }

    /**
     * When acquiring a WRITE lock on a descendant-collection of the root (with parent locking)
     * Collection ensure that we hold a single WRITE lock on the
     * descendant-collection and a single WRITE lock on the parent, by performing top-down lock-coupling
     * with READ locks (apart from the parent which takes a WRITE lock) on the
     * collection hierarchy to get there
     */
    @Test
    public void acquireCollectionWriteLock_depth3_withLockParent() throws LockException {
        final LockTable lockTable = LockTable.getInstance();

        final LockEventRecordingListener lockEventRecordingListener = new LockEventRecordingListener();
        lockTable.registerListener(lockEventRecordingListener);

        final String collectionAPath = "/db/colA";
        final String collectionBPath = collectionAPath + "/colB";

        final LockManager lockManager = new LockManager(CONCURRENCY_LEVEL);
        final boolean lockParent = true;
        try(final ManagedCollectionLock colBLock
                    = lockManager.acquireCollectionWriteLock(XmldbURI.create(collectionBPath), lockParent)) {
            assertNotNull(colBLock);
        }

        lockTable.deregisterListener(lockEventRecordingListener);

        // wait for the listener to be deregistered
        while(lockEventRecordingListener.isRegistered()) {}

        final Stack<LockTable.LockAction> events = lockEventRecordingListener.getEvents();
        assertEquals(9, events.size());
        final LockTable.LockAction event9 = events.pop();
        final LockTable.LockAction event8 = events.pop();
        final LockTable.LockAction event7 = events.pop();
        final LockTable.LockAction event6 = events.pop();
        final LockTable.LockAction event5 = events.pop();
        final LockTable.LockAction event4 = events.pop();
        final LockTable.LockAction event3 = events.pop();
        final LockTable.LockAction event2 = events.pop();
        final LockTable.LockAction event1 = events.pop();

        assertEquals(LockTable.LockAction.Action.Attempt, event1.action);
        assertEquals(XmldbURI.ROOT_COLLECTION, event1.id);
        assertEquals(Lock.LockMode.READ_LOCK, event1.mode);

        assertEquals(LockTable.LockAction.Action.Acquired, event2.action);
        assertEquals(XmldbURI.ROOT_COLLECTION, event2.id);
        assertEquals(Lock.LockMode.READ_LOCK, event2.mode);

        // we now expect to couple /db with /db/colA by acquiring /db/colA whilst holding /db
        assertEquals(LockTable.LockAction.Action.Attempt, event3.action);
        assertEquals(collectionAPath, event3.id);
        assertEquals(Lock.LockMode.WRITE_LOCK, event3.mode);

        assertEquals(LockTable.LockAction.Action.Acquired, event4.action);
        assertEquals(collectionAPath, event4.id);
        assertEquals(Lock.LockMode.WRITE_LOCK, event4.mode);

        // we now expect to release the lock on /db
        assertEquals(LockTable.LockAction.Action.Released, event5.action);
        assertEquals(XmldbURI.ROOT_COLLECTION, event5.id);
        assertEquals(Lock.LockMode.READ_LOCK, event5.mode);

        // we now expect to couple /db/colA with /db/colA/colB by acquiring /db/colA/colB whilst holding /db/colA
        assertEquals(LockTable.LockAction.Action.Attempt, event6.action);
        assertEquals(collectionBPath, event6.id);
        assertEquals(Lock.LockMode.WRITE_LOCK, event6.mode);

        assertEquals(LockTable.LockAction.Action.Acquired, event7.action);
        assertEquals(collectionBPath, event7.id);
        assertEquals(Lock.LockMode.WRITE_LOCK, event7.mode);

        // we now expect to release the lock on /db/colA/colB and then /db/colA (as the managed lock (of both locks) was closed)
        assertEquals(LockTable.LockAction.Action.Released, event8.action);
        assertEquals(collectionBPath, event8.id);
        assertEquals(Lock.LockMode.WRITE_LOCK, event8.mode);

        // we now expect to release the lock on /db/colA
        assertEquals(LockTable.LockAction.Action.Released, event9.action);
        assertEquals(collectionAPath, event9.id);
        assertEquals(Lock.LockMode.WRITE_LOCK, event9.mode);
    }

    @ThreadSafe
    private static class LockEventRecordingListener implements LockTable.LockEventListener {
        private final Stack<LockTable.LockAction> events = new Stack<>();
        private final AtomicBoolean registered = new AtomicBoolean();

        @Override
        public void registered() {
            registered.compareAndSet(false, true);
        }

        @Override
        public void unregistered() {
            registered.compareAndSet(true, false);
        }

        public boolean isRegistered() {
            return registered.get();
        }

        @Override
        public void accept(final LockTable.LockAction lockAction) {
            events.push(lockAction);
        }

        public Stack<LockTable.LockAction> getEvents() {
            return events;
        }
    }
}
