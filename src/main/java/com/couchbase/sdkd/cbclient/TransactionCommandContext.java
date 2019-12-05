package com.couchbase.sdkd.cbclient;

import com.couchbase.client.java.Collection;
import com.couchbase.client.java.json.JsonObject;
import com.couchbase.grpc.protocol.TxnServer;
import com.couchbase.sdkd.util.ProtocolException;
import com.couchbase.sdkd.util.SimpleTransaction;
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





public class TransactionCommandContext extends CommandContext {
    private static Transactions txn=null;
    private static List<String> txnKeys = new LinkedList<>();
    private List<String> UpdateKeys = new ArrayList<>();
    private List<String> DeleteKeys = new ArrayList<>();
    private List<Collection> ll =  new LinkedList<>();
    private  static JsonObject docContent= null;


    @Override
    public boolean execCommand(String cmd) {
        ll.add(defaultCollection);
        boolean commandSuccess= false;

        if(cmd.equals("TXN_CREATE")){
            System.out.println("Creating Transaction");
            try{
                //Create Transaction
                int timeout = req.getTxnTimeout();
                int durability=req.getTxnDurability();
                SimpleTransaction st = new SimpleTransaction();
                TransactionConfig txnConfig= st.createTransactionConfig(timeout,durability);
                txn = st.createTansaction(cluster,txnConfig);
                if(txn!=null){
                    commandSuccess=true;
                }
            }
            catch(Exception e){
                System.out.println("Exception Creating a txn:"+e);
            }
        }
        else if(cmd.equals("TXN_DATA_LOAD")){
            System.out.println("Loading documents");

            int numThreads=req.getNumThreads();
            int batchsize = req.getNumDocs()/req.getNumThreads();
            docContent = JsonObject.create().put("mutated", 0);
            JsonObject body = JsonObject.create().put("firstname", "James");
            body = body.put("age", 22);
            docContent = docContent.put("body", body);

            ExecutorService svc = Executors.newFixedThreadPool(numThreads);
            Set<Callable<String>> callables = new HashSet<Callable<String>>();

            try{
                for(int i =0;i<numThreads;i++){
                    callables.add((new Callable<String>() {
                        @Override
                        public String call() throws Exception {
                            generateTxnDocuments(txn,Thread.currentThread().getName(),req.getCommit(),req.getSync(),batchsize);
                            return null;
                        }
                    }
                    ));
                }
                List<Future<String>> futures = svc.invokeAll(callables);
                for(Future<String> future : futures){
                    future.get();
                }

                System.out.println("Completed Loading documents. Proceeding with verifying the documents");
                commandSuccess = verifyDocuments(txnKeys, docContent, true);
            }catch(Exception e)
            {
                System.out.println("Exception loading documents"+e);
            }

        }
        else if(cmd.equals("TXN_DATA_UPDATE")){
            System.out.println("Updating Documents");

            try {
                int i = 0;
                while (i < req.getNumDocs()) {
                    UpdateKeys.add(txnKeys.get(i));
                    i++;
                }
                DeleteKeys = new ArrayList<>();
                UpdateTxnDocuments(txn, UpdateKeys, DeleteKeys,req.getCommit(),req.getSync());
                System.out.println("Completed Updating documents. Proceeding with verifying the documents");

                JsonObject newContent = docContent.put("mutated", 1);
                commandSuccess= verifyDocuments(UpdateKeys, newContent, true);
            } catch (Exception e) {
                System.out.println("Exception Updating the documents: " + e);
            }

        }
        else if(cmd.equals("TXN_DATA_DELETE")){
            System.out.println("Deleting Documents");

            try {
                int i = txnKeys.size();
                while (i > txnKeys.size()-req.getNumDocs()) {
                    DeleteKeys.add(txnKeys.get(i-1));
                    i--;
                }
                UpdateKeys = new ArrayList<>();
                UpdateTxnDocuments(txn, UpdateKeys, DeleteKeys,req.getCommit(),req.getSync());
                System.out.println("Completed Deleting documents. Proceeding with verifying the documents");

                commandSuccess = verifyDocuments(DeleteKeys, null, false);
                System.out.println("Verification successful for the Deleted documents");

            } catch (Exception e) {
                System.out.println("Exception Deleting the documents: " + e);
            }

        } else if(cmd.equals("continuousFailOnSecondInsertMidCommit")){
            System.out.println("continuousFailOnSecondInsertMidCommit");
            JsonObject initial = JsonObject.create().put("val", "TXN");
            List<String> docs=new LinkedList<>();;
            List<Tuple2<String,JsonObject>> createKeys = new LinkedList<>();
            String docId="";

            for(int i = 0 ;i<3;i++){
                docId  = "Test"+i+"Batch"+i;
                docs.add(docId);
                Tuple2<String, JsonObject> pair = Tuples.of(docId,initial);
                createKeys.add(pair);
            }

            SimpleTransaction st = new SimpleTransaction();
            List<String> dummyKeys = new LinkedList<>();
            List<LogDefer> logdefer = new ArrayList<LogDefer>();
            try{
                logdefer= st.MockRunTransaction(cluster,txn,defaultCollection,createKeys,dummyKeys,dummyKeys,req.getCommit(),"afterDocCommitted",docs.get(docs.size()-1));
                System.out.println("Completed Mock Txn");
                for (LogDefer e : logdefer) {
                    System.out.println("LogDefer: "+e);
                }
            }
            catch (Exception e) {
                System.out.println("Useful exception from continuousFailOnSecondInsertMidCommit : "+e);
                // The transaction half committed before expiring
                assertEquals("TXN", defaultCollection.get(docs.get(0)).contentAs(JsonObject.class).getString("val"));
                System.out.println("docId assert success");

                assertEquals("TXN", defaultCollection.get(docs.get(1)).contentAs(JsonObject.class).getString("val"));
                System.out.println("docId2 assert success");

                assertEquals(0, defaultCollection.get(docs.get(2)).contentAs(JsonObject.class).getNames().size());
                System.out.println("docId3 assert success");
            }
            return true;
        }
        else {
            System.out.println("Invalid command");
        }

        return commandSuccess;
    }



    public void generateTxnDocuments(Transactions txn,String name,boolean commit, boolean sync,int batchsize) throws ProtocolException {
        SimpleTransaction st = new SimpleTransaction();
        List<Tuple2<String,JsonObject>> createKeys = new LinkedList<>();

        for(int i = 0 ;i<batchsize;i++){
            String docId = "Test"+name+"Batch"+i;
            synchronized(this){
                txnKeys.add(docId);
            }
            Tuple2<String, JsonObject> pair = Tuples.of(docId,docContent);
            createKeys.add(pair);
        }
        st.RunTransaction(txn,  ll,createKeys,UpdateKeys,DeleteKeys, commit, sync, 0);
    }


    public void UpdateTxnDocuments(Transactions txn,List<String>updateKeys, List<String>deleteKeys,boolean commit, boolean sync){
        SimpleTransaction st = new SimpleTransaction();
        List<Tuple2<String,JsonObject>> dummycreateKeys = new LinkedList<>();
        try{
            st.RunTransaction(txn,  ll,dummycreateKeys,updateKeys,deleteKeys, commit, sync, 1);
        }catch(Exception e ){
            System.out.println("Exception updating the docs:"+e);
        }
    }


    public boolean  verifyDocuments(List<String> keys, JsonObject docContent, boolean docExists){
        for(String key: keys){
            if(docExists){
                JsonObject body =  defaultCollection.get(key).contentAs(JsonObject.class);
                assertEquals(docContent,body);
            }else{
                boolean doc_Exists=  defaultCollection.exists(key).exists();
                assertEquals(docExists,doc_Exists);
            }
        }
        return true;
    }




    public TransactionCommandContext(TxnServer.txn_req  req) {
        super(req);
    }

}
