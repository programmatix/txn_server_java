/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.couchbase.Utils;


import java.util.List;
import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.Cluster;
import com.couchbase.grpc.protocol.TxnServer;


public class ClusterConnection {
    private Cluster cluster;
    private Bucket bucket;
    List<String> l;


    public ClusterConnection( TxnServer.conn_info reqData)  {
        cluster = Cluster.connect(reqData.getHandleHostname(), reqData.getHandleUsername(), reqData.getHandlePassword());
        bucket = cluster.bucket(reqData.getHandleBucket());
    }

    public Bucket getBucket() {
        return bucket;
    }

    public Cluster getCluster(){
        return cluster;
    }
}
