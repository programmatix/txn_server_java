package com.couchbase.sdkd.cbclient;

import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.Collection;
import com.couchbase.client.java.json.JsonObject;
import com.couchbase.grpc.protocol.TxnServer;
import com.couchbase.sdkd.util.ProtocolException;
import com.couchbase.sdkd.util.SimpleTransaction;
import com.couchbase.transactions.TransactionGetResult;
import com.couchbase.transactions.Transactions;
import com.couchbase.transactions.config.TransactionConfig;
import com.couchbase.transactions.log.LogDefer;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.Assert.assertEquals;





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

    public boolean txnInsert(Transactions txn){
        System.out.println("Inserting documents");
        try{
            docContent = JsonObject.create().put("mutated", 0);
            for (int i = 0; i < req.getNumDocs(); i++) {
                txnInsert( txn,"Test"+i);
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
                txn.run(ctx->ctx.insert(defaultCollection, "Test" + docId, docContent));
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
            int i = 0;
            while (i < req.getNumDocs()) {
                txnUpdate( txn, txnKeys.get(i));
                i++;
            }
            return true;
        } catch (Exception e) {
            System.out.println("Exception Updating the documents: " + e);
            return false;
        }
    }

    public boolean txnUpdate(Transactions txn,String docID){
        System.out.println("Updating Documents");
        try {
                txn.run(ctx->{
                    TransactionGetResult doc2=ctx.getOptional(defaultCollection, docID).get();
                    JsonObject content = doc2.contentAs(JsonObject.class);
                    content.put("mutated", "newVal");
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



    public void generateTxnDocuments(Transactions txn, String name, boolean commit, boolean sync, int batchsize) throws ProtocolException {
        SimpleTransaction st = new SimpleTransaction();
        List<Tuple2<String, JsonObject>> createKeys = new LinkedList<>();

        for (int i = 0; i < batchsize; i++) {
            String docId = "Test" + name + "Batch" + i;
            synchronized (this) {
                txnKeys.add(docId);
            }
            Tuple2<String, JsonObject> pair = Tuples.of(docId, docContent);
            createKeys.add(pair);
        }
        st.RunTransaction(txn, ll, createKeys, UpdateKeys, DeleteKeys, commit, sync, 0);
    }


    public void UpdateTxnDocuments(Transactions txn, List<String> updateKeys, List<String> deleteKeys) {
        SimpleTransaction st = new SimpleTransaction();
        List<Tuple2<String, JsonObject>> dummycreateKeys = new LinkedList<>();
        try {
            st.RunTransaction(txn, ll, dummycreateKeys, updateKeys, deleteKeys, false, false, 1);
        } catch (Exception e) {
            System.out.println("Exception updating the docs:" + e);
        }
    }




}
