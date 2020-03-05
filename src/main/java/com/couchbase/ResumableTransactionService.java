package com.couchbase;

import com.couchbase.Logging.LogUtil;
import com.couchbase.Transactions.*;
import com.couchbase.Utils.ClusterConnection;
import com.couchbase.Utils.HooksUtil;
import com.couchbase.grpc.protocol.ResumableTransactionServiceGrpc;
import com.couchbase.grpc.protocol.TxnServer;
import com.couchbase.transactions.TransactionDurabilityLevel;
import com.couchbase.transactions.Transactions;
import com.couchbase.transactions.config.TransactionConfigBuilder;
import com.couchbase.transactions.support.AttemptContextFactory;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ResumableTransactionService extends ResumableTransactionServiceGrpc.ResumableTransactionServiceImplBase {

    private ConcurrentHashMap<String, ResumableTransaction> resumableTransactions = new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, Transactions> transactionsFactories = new ConcurrentHashMap<>();
    static Logger logger;
    ClusterConnection connection;


    @Override
    public void createConn(TxnServer.conn_info request, StreamObserver<TxnServer.APIResponse> responseObserver) {
        TxnServer.APIResponse.Builder  response = TxnServer.APIResponse.getDefaultInstance().newBuilderForType();

        if (connection != null) {
            System.out.println("Connection already exists");
            response.setAPISuccessStatus(true).setAPIStatusInfo("Connection already exists");
        }else{
            try{
                connection = new ClusterConnection(request);
            } catch (Exception e){
                logger.error("Connection creation failed with exception: "+e);
            }
            if (connection == null) {
                System.out.println("Unable to create Connection");
                response.setAPISuccessStatus(false).setAPIStatusInfo("Unable to create Connection");
            }else{
                System.out.println("Created Connection successfully");
                response.setAPISuccessStatus(true).setAPIStatusInfo("Created Connection successfully");
            }
        }

        responseObserver.onNext(response.build());
        responseObserver.onCompleted();
    }

    @Override
    public void transactionsFactoryCreate(TxnServer.TransactionsFactoryCreateRequest request,
                                          StreamObserver<TxnServer.TransactionsFactoryCreateResponse> responseObserver) {
        TxnServer.TransactionsFactoryCreateResponse.Builder response =
            TxnServer.TransactionsFactoryCreateResponse.getDefaultInstance().newBuilderForType();

        try {
            logger.info("Creating new Transactions factory");
            AttemptContextFactory factory = HooksUtil.configureHooks(request, connection);

            TransactionDurabilityLevel durabilityLevel = TransactionDurabilityLevel.MAJORITY;
            switch (request.getDurability()) {
                case NONE:
                    durabilityLevel = TransactionDurabilityLevel.NONE;
                    break;
                case MAJORITY:
                    durabilityLevel = TransactionDurabilityLevel.MAJORITY;
                    break;
                case MAJORITY_AND_PERSIST_TO_ACTIVE:
                    durabilityLevel = TransactionDurabilityLevel.MAJORITY_AND_PERSIST_TO_ACTIVE;
                    break;
                case PERSIST_TO_MAJORITY:
                    durabilityLevel = TransactionDurabilityLevel.PERSIST_TO_MAJORITY;
                    break;
            }

            TransactionConfigBuilder builder = TransactionConfigBuilder.create()
                .durabilityLevel(durabilityLevel)
                .cleanupLostAttempts(request.getCleanupLostAttempts())
                .cleanupClientAttempts(request.getCleanupClientAttempts())
                .expirationTime(Duration.ofSeconds(request.getExpirationSeconds()))
                .testFactories(factory, null, null);

            Transactions transactions = Transactions.create(connection.getCluster(), builder);

            String transactionsFactoryRef = UUID.randomUUID().toString();

            transactionsFactories.put(transactionsFactoryRef, transactions);

            response
                .setSuccess(true)
                .setTransactionsFactoryRef(transactionsFactoryRef);
        } catch (RuntimeException err) {
            logger.error("Operation failed during transactionsFactoryCreate due to : " + err.getMessage());
            response.setSuccess(false);
        }
        responseObserver.onNext(response.build());
        responseObserver.onCompleted();
    }

    @Override
    public void transactionsFactoryClose(TxnServer.TransactionsFactoryCloseRequest request,
                                         StreamObserver<TxnServer.TransactionGenericResponse> responseObserver) {
        TxnServer.TransactionGenericResponse.Builder response =
            TxnServer.TransactionGenericResponse.getDefaultInstance().newBuilderForType();

        try {
            Transactions transactions = transactionsFactories.get(request.getTransactionsFactoryRef());
            transactions.close();
            transactionsFactories.remove(request.getTransactionsFactoryRef());

            response.setSuccess(true);

        } catch (RuntimeException err) {
            logger.error("Operation failed during transactionsFactoryClose due to : " + err.getMessage());
            response.setSuccess(false);
        }

        responseObserver.onNext(response.build());
        responseObserver.onCompleted();
    }

    @Override
    public void transactionCreate(TxnServer.TransactionCreateRequest request,
                                  StreamObserver<TxnServer.TransactionCreateResponse> responseObserver) {
        TxnServer.TransactionCreateResponse.Builder response =
            TxnServer.TransactionCreateResponse.getDefaultInstance().newBuilderForType();

        try {
            logger.info("Creating new ResumableTransaction");
            Transactions transactions = transactionsFactories.get(request.getTransactionsFactoryRef());

            ResumableTransaction txn = ResumableTransactionUtil.create(transactions);

            resumableTransactions.put(txn.transactionRef(), txn);
            logger.info("Created new ResumableTransaction with ref " + txn.transactionRef());

            response
                .setSuccess(true)
                .setTransactionRef(txn.transactionRef());

        } catch (RuntimeException err) {
            logger.error("Operation failed during transactionCreate due to :  " + err.getMessage());
            response.setSuccess(false);
        }

        responseObserver.onNext(response.build());
        responseObserver.onCompleted();
    }

    @Override
    public void transactionClose(TxnServer.TransactionGenericRequest request,
                                  StreamObserver<TxnServer.TransactionResultObject> responseObserver) {
        try {
            ResumableTransaction txn = resumableTransactions.get(request.getTransactionRef());
            ResumableTransactionComplete cmd = new ResumableTransactionComplete(true);
            TxnServer.TransactionResultObject result = txn.shutdownAndVerify(cmd);

            responseObserver.onNext(result);
            responseObserver.onCompleted();

        } catch (RuntimeException | InterruptedException err) {
            logger.error("Operation failed during transactionClose due to :  " + err);
            responseObserver.onError(err);
        }
    }



    @Override
    public void transactionInsert(TxnServer.TransactionInsertRequest request,
                                  StreamObserver<TxnServer.TransactionGenericResponse> responseObserver) {
        TxnServer.TransactionGenericResponse.Builder response =
                TxnServer.TransactionGenericResponse.getDefaultInstance().newBuilderForType();

        try {
            ResumableTransaction txn = resumableTransactions.get(request.getTransactionRef());

            ResumableTransactionInsert cmd = new ResumableTransactionInsert(connection.getBucket().defaultCollection(),
                    request.getDocId(),
                    request.getContentJson(),
                    request.getExpectedResult());
            boolean result = txn.executeCommandBlocking(cmd);
            response.setSuccess(result);
        } catch (RuntimeException err) {
            logger.error("Operation failed during transactionInsert due to : " + err.getMessage());
            response.setSuccess(false);
        }
        responseObserver.onNext(response.build());
        responseObserver.onCompleted();
    }

    @Override
    public void transactionRollback(TxnServer.TransactionGenericRequest request,
                                 StreamObserver<TxnServer.TransactionGenericResponse> responseObserver) {
        TxnServer.TransactionGenericResponse.Builder response =
                TxnServer.TransactionGenericResponse.getDefaultInstance().newBuilderForType();

        try {
            ResumableTransaction txn = resumableTransactions.get(request.getTransactionRef());

            ResumableTransactionRollback cmd = new ResumableTransactionRollback();
            boolean result = txn.executeCommandBlocking(cmd);
            response.setSuccess(result);
        } catch (RuntimeException err) {
            logger.error("Operation failed during transactionRollback due to : " + err.getMessage());
            response.setSuccess(false);
        }

        responseObserver.onNext(response.build());
        responseObserver.onCompleted();
    }


    @Override
    public void transactionUpdate(TxnServer.TransactionUpdateRequest request,
                                  StreamObserver<TxnServer.TransactionGenericResponse> responseObserver) {
        TxnServer.TransactionGenericResponse.Builder response =
                TxnServer.TransactionGenericResponse.getDefaultInstance().newBuilderForType();
        try {
            ResumableTransaction txn = resumableTransactions.get(request.getTransactionRef());

            ResumableTransactionUpdate cmd = new ResumableTransactionUpdate(connection.getBucket().defaultCollection(),
                    request.getDocId(),
                    request.getContentJson(),
                    request.getExpectedResult());
            boolean result = txn.executeCommandBlocking(cmd);

            response.setSuccess(result);
        } catch (RuntimeException err) {
            logger.error("Operation failed during transactionUpdate due to : " + err.getMessage());
            response.setSuccess(false);
        }
        responseObserver.onNext(response.build());
        responseObserver.onCompleted();
    }

    @Override
    public void transactionDelete(TxnServer.TransactionDeleteRequest request,
                                  StreamObserver<TxnServer.TransactionGenericResponse> responseObserver) {
        TxnServer.TransactionGenericResponse.Builder response =
                TxnServer.TransactionGenericResponse.getDefaultInstance().newBuilderForType();
        try {
            ResumableTransaction txn = resumableTransactions.get(request.getTransactionRef());

            ResumableTransactionDelete cmd = new ResumableTransactionDelete(connection.getBucket().defaultCollection(),
                    request.getDocId(),
                    request.getExpectedResult());
            boolean result = txn.executeCommandBlocking(cmd);

            response.setSuccess(result);
        } catch (RuntimeException err) {
            logger.error("Operation failed during transactionDelete due to : " + err.getMessage());
            response.setSuccess(false);
        }
        responseObserver.onNext(response.build());
        responseObserver.onCompleted();
    }



    @Override
    public void transactionCommit(TxnServer.TransactionGenericRequest request,
                                  StreamObserver<TxnServer.TransactionGenericResponse> responseObserver) {
        TxnServer.TransactionGenericResponse.Builder response =
            TxnServer.TransactionGenericResponse.getDefaultInstance().newBuilderForType();

        try {
            ResumableTransaction txn = resumableTransactions.get(request.getTransactionRef());
            ResumableTransactionCommit cmd = new ResumableTransactionCommit();

            boolean result = txn.executeCommandBlocking(cmd);
            response.setSuccess(result);
        } catch (RuntimeException err) {
            logger.error("Operation failed during transactionCommit due to : " + err.getMessage());
            response.setSuccess(false);
        }
        responseObserver.onNext(response.build());
        responseObserver.onCompleted();
    }

    public void getTransactionState(com.couchbase.grpc.protocol.TxnServer.TransactionGenericRequest request,
                                    io.grpc.stub.StreamObserver<com.couchbase.grpc.protocol.TxnServer.TransactionState> responseObserver) {
        try {
            ResumableTransaction txn = resumableTransactions.get(request.getTransactionRef());

            TxnServer.TransactionState.Builder response =
                TxnServer.TransactionState.getDefaultInstance().newBuilderForType();

            // Do a no-op command to make sure ResumableTransaction is in a 'ready for action'
            // state and hence has a correct attemptNumber
            ResumableTransactionCommand cmd = new ResumableTransactionNoOp();
            boolean result = txn.executeCommandBlocking(cmd);
            assert(result);

            response.setAttemptNumber(txn.attemptNumber());

            responseObserver.onNext(response.build());
            responseObserver.onCompleted();
        } catch (RuntimeException err) {
            logger.error("Operation failed during getTransactionState due to : " + err.getMessage());
            responseObserver.onError(err);
        }
    }


    public static void main(String[] args) throws IOException, InterruptedException {
        for(int i =0; i<args.length;i++){
            if(args[i].equals("-loglevel"))
            {
                LogUtil.setLevelFromSpec(args[i+1]);
            }
        }
        logger = LogUtil.getLogger(ResumableTransactionService.class);
        Server server = ServerBuilder.forPort(8050)
            .addService(new ResumableTransactionService())
            .build();
        server.start();
        logger.info("Server Started at 8050.");
        server.awaitTermination();
    }
}
