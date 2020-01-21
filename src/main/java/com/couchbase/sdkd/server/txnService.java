package com.couchbase.sdkd.server;


import com.couchbase.grpc.protocol.TxnServer;
import com.couchbase.grpc.protocol.TxnServer.APIResponse;
import com.couchbase.grpc.protocol.txnGrpc;
import com.couchbase.sdkd.cbclient.*;
import com.couchbase.sdkd.transactions.ResumableTransaction;
import com.couchbase.sdkd.transactions.ResumableTransactionCommit;
import com.couchbase.sdkd.transactions.ResumableTransactionInsert;
import com.couchbase.sdkd.transactions.ResumableTransactionUtil;
import com.couchbase.sdkd.util.SdkdConfig;
import com.couchbase.sdkd.util.SharedHandle;
import com.couchbase.transactions.Transactions;
import com.couchbase.transactions.config.TransactionConfigBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

public class txnService extends txnGrpc.txnImplBase {
    Handle connection;
    private Transactions txnFactory = null;
    TransactionCommands txnUtils;

    private ConcurrentHashMap<String, ResumableTransaction> resumableTransactions = new ConcurrentHashMap<>();
    private final Logger logger = LogManager.getLogger(txnService.class);
    private Transactions transactions = null;
    private SharedHandle sh = null;

    @Override
    public void createConn(TxnServer.conn_info request, StreamObserver<APIResponse> responseObserver) {
        APIResponse.Builder response = APIResponse.getDefaultInstance().newBuilderForType();

        if (connection != null) {
            System.out.println("Connection already exists");
            response.setAPISuccessStatus(true).setAPIStatusInfo("Connection already exists");
        } else {
            try {
                connection = new Handle(request);
            } catch (Exception e) {
                System.out.println(e);
            }
            if (connection == null) {
                System.out.println("Unable to create Connection");
                response.setAPISuccessStatus(false).setAPIStatusInfo("Unable to create Connection");
            } else {
                System.out.println("Created Connection successfully");
                response.setAPISuccessStatus(true).setAPIStatusInfo("Created Connection successfully");
            }
        }

        responseObserver.onNext(response.build());
        responseObserver.onCompleted();
    }

    @Override
    public void createTxnFactory(TxnServer.txn_req request, StreamObserver<APIResponse> responseObserver) {
        APIResponse.Builder response = APIResponse.getDefaultInstance().newBuilderForType();

        try {
            if (connection != null) {
                txnUtils = new TransactionCommands(request, connection);
                if (txnFactory != null) {
                    System.out.println("Some txn was already in use");
                    txnFactory.close();
                    System.out.println("old txn is closed  for safety reasons");

                }
                txnFactory = txnUtils.createTxnFactory();
                if (txnFactory != null) {
                    System.out.println("txn is created");
                    response.setAPISuccessStatus(true).setAPIStatusInfo("Created txn successfully");
                } else {
                    System.out.println("txn is not create");
                    response.setAPISuccessStatus(false).setAPIStatusInfo("Unable to create txn");
                }
            } else {
                System.out.println("Connection not established between server and cb server");
                response.setAPISuccessStatus(false).setAPIStatusInfo("Unable to create txn since the txn_framework " +
                    "did not yet establish connection with couchbase server");
            }
        } catch (Exception e) {
            System.out.println("Exception creating Transaction:" + e);
            response.setAPISuccessStatus(false).setAPIStatusInfo(request.getCommand() + "Exception creating " +
                "Transaction:" + e);
        }
        responseObserver.onNext(response.build());
        responseObserver.onCompleted();
    }

    public void executeTxn(TxnServer.txn_req request, StreamObserver<APIResponse> responseObserver) {
        APIResponse.Builder response = APIResponse.getDefaultInstance().newBuilderForType();
        boolean txn_resp = false;
        try {
            if (txnFactory != null) {
                txnUtils = new TransactionCommands(request, connection);
                if (request.getCommand().equals("TXN_DATA_INSERT")) {
                    txn_resp = txnUtils.txnInsert(txnFactory, true);
                } else if (request.getCommand().equals("TXN_DATA_UPDATE")) {
                    txn_resp = txnUtils.txnUpdate(txnFactory);
                } else if (request.getCommand().equals("TXN_DATA_DELETE")) {
                    txn_resp = txnUtils.txnDelete(txnFactory);
                } else if (request.getCommand().equals("TXN_COMMIT")) {
                    txn_resp = txnUtils.txnCommit(txnFactory, true);
                } else if (request.getCommand().equals("TXN_ROLLBACK")) {
                    txn_resp = txnUtils.txnCommit(txnFactory, false);
                } else if (request.getCommand().equals("TXN_CLOSE")) {
                    txn_resp = txnUtils.txnClose(txnFactory);
                } else {
                    System.out.println("Invalid command");
                }
            } else {
                System.out.println("Txn does not exist. Hence unable to load data");
                response.setAPISuccessStatus(false).setAPIStatusInfo(request.getCommand() + " has failed since txn " +
                    "was not created earlier");
            }

            if (txn_resp) {
                System.out.println(request.getCommand() + " completed successfully");
                response.setAPISuccessStatus(true).setAPIStatusInfo(request.getCommand() + " completed successfully");
            } else {
                System.out.println(request.getCommand() + " has failed");
                response.setAPISuccessStatus(false).setAPIStatusInfo(request.getCommand() + " has failed while " +
                    "executing txn");
            }
        } catch (Exception e) {
            System.out.println("Exception creating Transaction:" + e);
            response.setAPISuccessStatus(false).setAPIStatusInfo(request.getCommand() + "Exception creating " +
                "Transaction:" + e);
        }
        responseObserver.onNext(response.build());
        responseObserver.onCompleted();
    }

    public void transactionCreate(TxnServer.TransactionCreateRequest request,
                                  StreamObserver<TxnServer.TransactionCreateResponse> responseObserver) {
        TxnServer.TransactionCreateResponse.Builder response =
            TxnServer.TransactionCreateResponse.getDefaultInstance().newBuilderForType();

        try {
            logger.info("Creating new ResumableTransaction");

            // TODO this isn't thread-safe
            if (sh == null) {
                // TODO don't hardcode these
                sh = SdkdConfig.getClient("localhost", "default", "password", false);
            }

            // TODO this isn't thread-safe
            if (transactions == null) {
                transactions = Transactions.create(sh.getCluster(),
                    TransactionConfigBuilder.create()
                        // TODO support hooks
                        // Give time for debugging
                        .expirationTime(Duration.ofSeconds(120)));
            }

            ResumableTransaction txn = ResumableTransactionUtil.create(transactions);
            resumableTransactions.put(txn.transactionRef(), txn);

            logger.info("Created new ResumableTransaction with ref " + txn.transactionRef());

            response
                .setSuccess(true)
                .setTransactionRef(txn.transactionRef());
        } catch (RuntimeException err) {
            logger.info("Operation failed with error " + err.getMessage());
            response.setSuccess(false);
        } catch (IOException e) {
            e.printStackTrace();
        }
        responseObserver.onNext(response.build());
        responseObserver.onCompleted();
    }

    public void transactionInsert(TxnServer.TransactionInsertRequest request,
                                  StreamObserver<TxnServer.TransactionGenericResponse> responseObserver) {
        TxnServer.TransactionGenericResponse.Builder response =
            TxnServer.TransactionGenericResponse.getDefaultInstance().newBuilderForType();

        try {
            ResumableTransaction txn = resumableTransactions.get(request.getTransactionRef());

            ResumableTransactionInsert cmd = new ResumableTransactionInsert(sh.getDefaultHandleCollection(),
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
        Server server = ServerBuilder.forPort(8050).addService(new txnService()).build();
        server.start();
        System.out.println("Server Started at 8050.");
        server.awaitTermination();
    }


}
