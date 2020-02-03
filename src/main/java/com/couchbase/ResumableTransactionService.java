package com.couchbase;

import com.couchbase.Tests.Transactions.Utils.ResumableTransaction;
import com.couchbase.Tests.Transactions.Utils.ResumableTransactionCommit;
import com.couchbase.Tests.Transactions.Utils.ResumableTransactionInsert;
import com.couchbase.Tests.Transactions.Utils.ResumableTransactionUtil;
import com.couchbase.Utils.ClusterConnection;
import com.couchbase.client.core.error.TemporaryFailureException;
import com.couchbase.grpc.protocol.ResumableTransactionServiceGrpc;
import com.couchbase.grpc.protocol.TxnServer;
import com.couchbase.transactions.TransactionDurabilityLevel;
import com.couchbase.transactions.Transactions;
import com.couchbase.transactions.config.TransactionConfigBuilder;
import com.couchbase.transactions.error.internal.AbortedAsRequested;
import com.couchbase.transactions.error.internal.AbortedAsRequestedNoRollbackNoCleanup;
import com.couchbase.transactions.support.AttemptContextFactory;
import com.couchbase.transactions.util.TestAttemptContextFactory;
import com.couchbase.transactions.util.TransactionMock;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ResumableTransactionService extends ResumableTransactionServiceGrpc.ResumableTransactionServiceImplBase {
    private ConcurrentHashMap<String, ResumableTransaction> resumableTransactions = new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, Transactions> transactionsFactories = new ConcurrentHashMap<>();
    private final Logger logger = LogManager.getLogger(ResumableTransactionService.class);

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
                System.out.println(e);
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
            AttemptContextFactory factory = null;

            if (request.getHookCount() != 0) {
                TransactionMock mock = new TransactionMock();

                for (int i = 0; i < request.getHookCount(); i++) {
                    final int x = i;

                    RuntimeException err;

                    switch (request.getHookErrorToRaise(x)) {
                        case FAIL_NO_ROLLBACK:
                            err = new AbortedAsRequestedNoRollbackNoCleanup();
                            break;
                        case FAIL_ROLLBACK:
                            err = new AbortedAsRequested();
                            break;
                        case FAIL_RETRY:
                            err = new TemporaryFailureException(null);
                            break;
                        default:
                            throw new IllegalStateException("Cannot handle hook error " + request.getHookErrorToRaise(x));
                    }

                    switch (request.getHook(i)) {
                        case BEFORE_ATR_COMMIT:
                            mock.beforeAtrCommit = (ctx) -> {
                                switch (request.getHookCondition(x)) {
                                    case ALWAYS:
                                        return Mono.error(err);
                                }
                                return Mono.just(1);
                            };
                    }
                }

                factory = new TestAttemptContextFactory(mock);
            }

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
            logger.info("Operation failed with error " + err.getMessage());
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
            logger.info("Operation failed with error " + err.getMessage());
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
            logger.info("Operation failed with error " + err.getMessage());
            response.setSuccess(false);
        }

        responseObserver.onNext(response.build());
        responseObserver.onCompleted();
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
                request.getContentJson());
            boolean result = txn.executeCommandBlocking(cmd);

            response.setSuccess(result);
        } catch (RuntimeException err) {
            logger.info("Operation failed with error " + err.getMessage());
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
            logger.info("Operation failed with error " + err.getMessage());
            response.setSuccess(false);
        }
        responseObserver.onNext(response.build());
        responseObserver.onCompleted();
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        Server server = ServerBuilder.forPort(8050)
            .addService(new ResumableTransactionService())
            .build();
        server.start();
        System.out.println("Server Started at 8050.");
        server.awaitTermination();
    }
}
