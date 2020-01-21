package com.couchbase.sdkd.cbclient;

import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.Collection;
import com.couchbase.client.java.json.JsonObject;
import com.couchbase.grpc.protocol.TxnServer;
import com.couchbase.sdkd.util.SimpleTransaction;
import com.couchbase.sdkd.util.Strings;
import com.couchbase.transactions.TransactionGetResult;
import com.couchbase.transactions.Transactions;
import com.couchbase.transactions.config.TransactionConfig;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;


public class TransactionCommands {
    private static List<String> txnKeys = new LinkedList<>();
    private List<String> UpdateKeys = new ArrayList<>();
    private List<String> DeleteKeys = new ArrayList<>();
    private List<Collection> ll = new LinkedList<>();
    private static JsonObject docContent = null;
    protected TxnServer.txn_req req;
    protected Bucket sdkHandle;
    protected Collection defaultCollection;
    protected Cluster cluster;
    protected int handleId;
    protected String bucketName;
    protected String passwd;
    protected List<String> hostlist;

    public TransactionCommands(TxnServer.txn_req req, Handle h) {
        this.req = req;
        sdkHandle = h.getSdk();
        bucketName = h.getBucketName();
        passwd = h.getBucketPassword();
        hostlist = h.getHosts();
        cluster = h.getCluster();
        defaultCollection = h.getDefaultBucketCollection();
        ll.add(defaultCollection);
    }


    public Transactions createTxnFactory() {
        System.out.println("Creating Transaction Factory");
        Transactions txn=null ;
        try {
            SimpleTransaction st = new SimpleTransaction();
            TransactionConfig txnConfig = st.createTransactionConfig(req.getTxnTimeout(), req.getTxnDurability());
            if(req.getMock()){
                    txn = st.createMockTansactionFactory(cluster, txnConfig,req.getMockOperation(), "Test"+req.getDocNum());
            }else{
                txn= st.createTansactionFactory(cluster, txnConfig);
            }
        } catch (Exception e) {
            System.out.println("Exception Creating a txn:" + e);
        }
        return txn;
    }

    public boolean txnInsert(Transactions txn,boolean force){
        System.out.println("Inserting documents");
        try{
            docContent = JsonObject.create().put(Strings.CONTENT_NAME, Strings.DEFAULT_CONTENT_VALUE);
            for (int i = 0; i < req.getNumDocs(); i++) {
                txnKeys.add(Strings.DEFAULT_KEY+i);
                if(force){
                    txnDelete(txn,Strings.DEFAULT_KEY+i);
                }
                txnInsert( txn,Strings.DEFAULT_KEY+i);
            }
            return true;
        }
        catch(Exception e){
            System.out.println("Exception inserting documents: "+e);
            return false;
        }
    }

    public boolean txnInsert(Transactions txn,String docId){
        try{
                txn.run(ctx->ctx.insert(defaultCollection, docId, docContent));
            return true;
        }
        catch(Exception e){
            System.out.println("Exception inserting documents: "+e);
            return false;
        }
    }

    public boolean txnUpdate(Transactions txn){
        System.out.println("Updating Documents");
        try {
            for(int i =0;i<req.getNumDocs();i++){
                txnUpdate( txn, txnKeys.get(i));
            }
            return true;
        } catch (Exception e) {
            System.out.println("Exception Updating the documents: " + e);
            return false;
        }
    }

    public boolean txnUpdate(Transactions txn,String docID){
        try {
                txn.run(ctx->{
                    TransactionGetResult doc2=ctx.getOptional(defaultCollection, docID).get();
                    JsonObject content = doc2.contentAs(JsonObject.class);
                    content.put(Strings.CONTENT_NAME, Strings.UPDATED_CONTENT_VALUE);
                    ctx.replace(doc2, content);
                });
            return true;
        } catch (Exception e) {
            System.out.println("Exception Updating the documents: " + e);
            return false;
        }
    }

    public boolean txnDelete(Transactions txn){
        System.out.println("Deleting Documents");
        try {
            int i = txnKeys.size();
            while (i > txnKeys.size() - req.getNumDocs()) {
                txnDelete(txn, txnKeys.get(i-1));
                i--;
            }
            return true;
        } catch (Exception e) {
            System.out.println("Exception Deleting the documents: " + e);
            return false;
        }
    }

    public boolean txnDelete(Transactions txn,String docId){
        try {
                txn.run(ctx->{
                    TransactionGetResult doc=ctx.getOptional(defaultCollection, docId).get();
                    ctx.remove(doc);
                });
            return true;
        } catch (Exception e) {
            System.out.println("Exception Deleting the documents: " + e);
            return false;
        }
    }

    public boolean txnCommit(Transactions txn,boolean commit){
        try{
            if(commit){
                txn.run(ctx->ctx.commit());
            }else{
                txn.run(ctx->ctx.rollback());
            }
            return true;
        }catch(Exception e){
            System.out.println("Exception while commiting: "+e);
            return false;
        }

    }

    public boolean txnClose(Transactions txn){
        try{
            txn.close();
            return true;
        }catch(Exception e){
            System.out.println("Exception while commiting: "+e);
            return false;
        }

    }






}
