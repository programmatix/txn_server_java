/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.couchbase.sdkd.cbclient;

import java.util.List;
import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.Collection;
import com.couchbase.grpc.protocol.TxnServer;


/**
 * @author mnunberg
 */

/**
 * This is a command context for all SDK operations. It acts as the container
 * for generating and dispatching commands to a @ref CouchbaseClient object, as
 * well as retrieving and collecting the results and statistics of these
 * operations.
 *
 * @author mnunberg
 */
public abstract class CommandContext {
    protected TxnServer.txn_req  req;
    protected Bucket sdkHandle;
    protected Collection defaultCollection;
    protected Cluster cluster;
    protected int handleId;
    protected String bucketName;
    protected String passwd;
    protected List<String> hostlist;

    public CommandContext(TxnServer.txn_req  req) {
        this.req = req;
    }

    public boolean executeCommand(Handle h,String cmd)  {
        boolean cmdsuccess=false;
        sdkHandle = h.getSdk();
        bucketName = h.getBucketName();
        passwd = h.getBucketPassword();
        hostlist = h.getHosts();
        cluster = h.getCluster();
        defaultCollection = h.getDefaultBucketCollection();
        try{
            cmdsuccess = execCommand(cmd);
        }catch(Exception Ee){
            System.out.println("Caught Exception IN EXECITR"+Ee);
        }
        return cmdsuccess;
    }

     protected boolean execCommand(String cmd){
        System.out.println("Command: "+cmd);
        return true;
     }

}