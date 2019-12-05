/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.couchbase.sdkd.cbclient;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.logging.Logger;

import com.couchbase.client.core.env.IoConfig;
import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.Collection;
import com.couchbase.grpc.protocol.TxnServer;
import com.couchbase.sdkd.util.ProtocolException;
import com.couchbase.sdkd.util.SdkdConfig;
import com.couchbase.sdkd.util.SharedHandle;

/**
 * This class converts
 * @todo integrate this with SharedHandle
 * @author mnunberg
 */
public class Handle {


    private SharedHandle sh;
    private String bucket;
    private String passwd;
    private String username;
    List<String> l;
    private static final Logger handleLog = SdkdConfig.getLogger("sdk.handle");


    public Handle( TxnServer.conn_info reqData) throws ProtocolException {
        System.out.println("Inside handle");
        String bucketName = reqData.getHandleBucket();
        String seedNode = reqData.getHandleHostname();
        passwd = reqData.getHandlePassword();
        username = reqData.getHandleUsername();
        boolean sslEnabled  = reqData.getHandleSsl();
        boolean clientCert = false;
        SdkdConfig.autoFailoverTimeoutMs = reqData.getHandleAutofailoverMs();
        System.out.println("Done getting request params");

        try {
             sh = SdkdConfig.getClient(seedNode, bucketName, passwd, clientCert);
            if (SdkdConfig.autoFailoverTimeoutMs != 0) {
                IoConfig.configPollInterval(Duration.ofMillis(SdkdConfig.autoFailoverTimeoutMs/2));
            }
            bucket = bucketName;
        } catch (IOException exc) {
            System.out.println("Exeception creating a Handle :"+exc);
        }
    }

    public void shutdown() {
        if (sh != null) {
            SdkdConfig.releaseClient(sh);
        }
        sh = null;
    }

    public Bucket getSdk() {
        if (sh == null) {
            return null;
        }
        return sh.getHandle();
    }


    public Collection getDefaultBucketCollection(){
        if(sh == null){
            return null;
        }
        return sh.getHandle().defaultCollection();
    }

    public String getBucketName() {
        return bucket;
    }

    public String getBucketPassword() {
        return passwd;
    }


    public List<String> getHosts() {
        return l;
    }



    public Cluster getCluster(){
        if(sh == null){
            return null;
        }
        return sh.getCluster();
    }
}
