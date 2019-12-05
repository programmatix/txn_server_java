/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.couchbase.sdkd.util;

import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.Collection;

/**
 * Wrapper class around a CouchbaseClient. This allows us to do
 * things like handle caching.
 * @author mnunberg
 */

public class SharedHandle implements Comparable<SharedHandle> {
    private int refCount = 0;
    private Bucket handle = null;
    private Cluster cluster = null;
    private Collection defaultBucketCollection = null;

    @Override
    public int compareTo(SharedHandle other) {
        if (refCount == other.refCount) {
            return 0;
        } else if (refCount > other.refCount) {
            return 1;
        } else {
            return -1;
        }
    }

    public Bucket getHandle() {
        return handle;
    }

    public Collection getDefaultHandleCollection() {
        return defaultBucketCollection;
    }

    public Cluster getCluster(){
        return cluster;
    }

    public Collection getBucketCollection(String name){
        return getHandle().collection(name);
    }

    void setHandle(Bucket cli) {
        handle = cli;
    }

    void setDefaultHandleCollection(Collection collection) {
        this.defaultBucketCollection = collection;
    }

    void setCluster(Cluster cluster){
        this.cluster = cluster;
    }

    int ref() {
        refCount++;
        return refCount;
    }

    int unref() {
        refCount--;
        return refCount;
    }

    int getRefCount() {
        return refCount;
    }
}