/* 
 * eXist Open Source Native XML Database
 * 
 * Copyright (C) 2000-04,  Wolfgang Meier (wolfgang@exist-db.org)
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 * 
 * $Id$
 */

package org.exist.dom;

import org.exist.collections.Collection;
import org.exist.numbering.NodeId;
import org.exist.security.Permission;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock;
import org.exist.storage.lock.LockedDocumentMap;
import org.exist.util.LockException;
import org.exist.util.hashtable.Int2ObjectHashMap;
import org.exist.xmldb.XmldbURI;
import org.w3c.dom.Node;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.TreeSet;
import org.exist.security.PermissionDeniedException;

/**
 * Manages a set of documents.
 * 
 * This class implements the NodeList interface for a collection of documents.
 * It also contains methods to retrieve the collections these documents
 * belong to.
 * 
 * @author wolf
 */
public class DefaultDocumentSet extends Int2ObjectHashMap implements MutableDocumentSet {

    private ArrayList<DocumentImpl> list = null;
    private TreeSet<Collection> collections = new TreeSet<Collection>();

    public DefaultDocumentSet() {
        super(29, 1.75);
    }

    public DefaultDocumentSet(int initialSize) {
        super(initialSize, 1.75);
    }

    @Override
    public void clear() {
        super.clear();
        collections = new TreeSet<Collection>();
        list = null;
    }

    @Override
    public void add(DocumentImpl doc) {
        add(doc, true);
    }

    @Override
    public void add(DocumentImpl doc, boolean checkDuplicates) {
        final int docId = doc.getDocId();
        if (checkDuplicates && containsKey(docId))
            return;
        put(docId, doc);
        if (list != null)
            list.add(doc);
        if (doc.getCollection() != null && (!collections.contains(doc.getCollection())))
            collections.add(doc.getCollection());
    }

    public void add(Node node) {
        if (!(node instanceof DocumentImpl))
            throw new RuntimeException("wrong implementation");
        add((DocumentImpl) node);
    }

    @Override
    public void addAll(DocumentSet other) {
        for (int i = 0; i < other.getDocumentCount(); i++)
            add(other.getDocumentAt(i));
    }

    /**
     * Fast method to add a bunch of documents from a
     * Java collection.
     * 
     * The method assumes that no duplicate entries are
     * in the input collection.
     */
    @Override
    public void addAll(DBBroker broker, Collection collection, String[] paths) {
        DocumentImpl doc;
        for(String path : paths) {
            try {
                doc = collection.getDocumentNoLock(broker, path);
            } catch (PermissionDeniedException pde) {
                continue;
            }
            
            if (doc == null) {
                continue;
            }
            
            // WM: we don't have a lock on the document, so we should not change its broker:
            // doc.setBroker(broker);
            put(doc.getDocId(), doc);
        }
    }

    /**
     * Fast method to add a bunch of documents from a Java collection.
     * A lock will be acquired on each document. The locked document is added to the
     * specified LockedDocumentMap in order to keep track of the locks..
     *
     * @param broker
     * @param collection
     * @param paths
     * @param lockMap
     * @param lockType
     * @throws LockException
     */
    @Override
    public void addAll(DBBroker broker, Collection collection, String[] paths, LockedDocumentMap lockMap, int lockType) throws LockException {
        DocumentImpl doc;
        Lock lock;
        for(String path : paths) {
            
            try {
                doc = collection.getDocumentNoLock(broker, path);
            } catch(PermissionDeniedException pde) {
                continue;
            }
            
            if(doc == null) {
                continue;
            }
            
            if (doc.getPermissions().validate(broker.getSubject(), Permission.WRITE)) {
                lock = doc.getUpdateLock();

                lock.acquire(Lock.WRITE_LOCK);
                put(doc.getDocId(), doc);
                lockMap.add(doc);
            }
        }
    }

    @Override
    public void addCollection(Collection collection) {
        collections.add(collection);
    }

    @Override
    public Iterator<DocumentImpl> getDocumentIterator() {
        return valueIterator();
    }

    @Override
    public Iterator<Collection> getCollectionIterator() {
        return collections.iterator();
    }

    @Override
    public int getDocumentCount() {
        return size();
    }

    public int getCollectionCount() {
        return collections.size();
    }

    @Override
    public DocumentImpl getDocumentAt(int pos) {
        //UNDERSTAND: do we need that list??? (shabanovd)
        if (list == null) {
            list = new ArrayList<DocumentImpl>();
            for(Iterator<DocumentImpl> i = getDocumentIterator(); i.hasNext(); )
                list.add(i.next());
        }
        return list.get(pos);
    }

    @Override
    public DocumentImpl getDoc(int docId) {
        return (DocumentImpl) get(docId);
    }

    @Override
    public XmldbURI[] getNames() {
        XmldbURI result[] = new XmldbURI[size()];
        DocumentImpl d;
        int j = 0;
        for (Iterator<DocumentImpl> i = getDocumentIterator(); i.hasNext(); j++) {
            d = i.next();
            result[j] = d.getFileURI();
        }
        Arrays.sort(result);
        return result;
    }

    @Override
    public DocumentSet intersection(DocumentSet other) {
        DefaultDocumentSet r = new DefaultDocumentSet();
        DocumentImpl d;
        for (Iterator<DocumentImpl> i = getDocumentIterator(); i.hasNext();) {
            d = i.next();
            if (other.contains(d.getDocId()))
                r.add(d);
        }
        for (Iterator<DocumentImpl> i = other.getDocumentIterator(); i.hasNext();) {
            d = i.next();
            if (contains(d.getDocId()) && (!r.contains(d.getDocId())))
                r.add(d);
        }
        return r;
    }

    public DocumentSet union(DocumentSet other) {
        DefaultDocumentSet result = new DefaultDocumentSet();
        result.addAll(other);
        DocumentImpl d;
        for (Iterator<DocumentImpl> i = getDocumentIterator(); i.hasNext();) {
            d = i.next();
            if (!result.contains(d.getDocId()))
                result.add(d);
        }
        return result;
    }

    @Override
    public boolean contains(DocumentSet other) {
        if (other.getDocumentCount() > size())
            return false;
        DocumentImpl d;
        for (Iterator<DocumentImpl> i = other.getDocumentIterator(); i.hasNext();) {
            d = i.next();
            if (!contains(d.getDocId()))
                return false;
        }
        return true;
    }

    @Override
    public boolean contains(int id) {
        return containsKey(id);
    }

    @Override
    public NodeSet docsToNodeSet() {
        NodeSet result = new NewArrayNodeSet(getDocumentCount());
        DocumentImpl doc;
        for (Iterator<DocumentImpl> i = getDocumentIterator(); i.hasNext();) {
            doc = i.next();
            if(doc.getResourceType() == DocumentImpl.XML_FILE) {  // skip binary resources
                result.add(new NodeProxy(doc, NodeId.DOCUMENT_NODE));
            }
        }
        return result;
    }

    public int getMinDocId() {
        int min = DocumentImpl.UNKNOWN_DOCUMENT_ID; 
        DocumentImpl d;
        for (Iterator<DocumentImpl> i = getDocumentIterator(); i.hasNext();) {
            d = i.next();
            if (min == DocumentImpl.UNKNOWN_DOCUMENT_ID)
                min = d.getDocId();
            else if (d.getDocId() < min)
                min = d.getDocId();
        }
        return min;
    }

    public int getMaxDocId() {
        int max = DocumentImpl.UNKNOWN_DOCUMENT_ID;
        DocumentImpl d;
        for (Iterator<DocumentImpl> i = getDocumentIterator(); i.hasNext();) {
            d = i.next();
            if (d.getDocId() > max)
                max = d.getDocId();
        }
        return max;
    }

    @Override
    public boolean equalDocs(DocumentSet other) {
        if (this == other)
            // we are comparing the same objects
            return true;
        if (size() != other.getDocumentCount())
            return false;
        for(int idx = 0; idx < tabSize; idx++) {
            if (values[idx] == null || values[idx] == REMOVED)
                continue;
            if (!other.contains(keys[idx]))
                return false;
        }
        return true;
    }

    @Override
    public void lock(DBBroker broker, boolean exclusive, boolean checkExisting) throws LockException {
        DocumentImpl d;
        Lock dlock;
        //final Thread thread = Thread.currentThread();
        for(int idx = 0; idx < tabSize; idx++) {
            if(values[idx] == null || values[idx] == REMOVED)
                continue;
            d = (DocumentImpl)values[idx];
            dlock = d.getUpdateLock();
            //if (checkExisting && dlock.hasLock(thread))
                //continue;
            if(exclusive)
                dlock.acquire(Lock.WRITE_LOCK);
            else
                dlock.acquire(Lock.READ_LOCK);
        }
    }

    @Override
    public void unlock(boolean exclusive) {
        DocumentImpl d;
        Lock dlock;
        final Thread thread = Thread.currentThread();
        for(int idx = 0; idx < tabSize; idx++) {
            if(values[idx] == null || values[idx] == REMOVED)
                continue;
            d = (DocumentImpl)values[idx];
            dlock = d.getUpdateLock();
            if(exclusive)
                dlock.release(Lock.WRITE_LOCK);
            else if (dlock.isLockedForRead(thread))
                dlock.release(Lock.READ_LOCK);
        }
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        for( int i=0; i< getDocumentCount(); i++ ) {
            result.append(getDocumentAt(i));
            result.append(", ");
        }
        return result.toString();
    }
}
