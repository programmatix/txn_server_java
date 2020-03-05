/*
 * Copyright (c) 2020 Couchbase, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.couchbase;

import com.couchbase.Logging.LogUtil;
import com.couchbase.Transactions.ResumableTransaction;
import com.couchbase.Transactions.ResumableTransactionUtil;
import com.couchbase.Utils.ClusterConnection;
import com.couchbase.Utils.HooksUtil;
import com.couchbase.block.BlockTransactions;
import com.couchbase.grpc.protocol.BlockTransactionServiceGrpc;
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

public class BlockTransactionService extends BlockTransactionServiceGrpc.BlockTransactionServiceImplBase {
    private ConcurrentHashMap<String, Transactions> transactionsFactories = new ConcurrentHashMap<>();
    private static Logger logger = LogUtil.getLogger(BlockTransactionService.class);;
    private ClusterConnection connection;

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
        try {
            Transactions transactions = transactionsFactories.get(request.getTransactionsFactoryRef());
            transactions.close();
            transactionsFactories.remove(request.getTransactionsFactoryRef());

            TxnServer.TransactionGenericResponse.Builder response =
                TxnServer.TransactionGenericResponse.getDefaultInstance().newBuilderForType();
            // TODO Not really sure we need this, onError is used to signal failure
            response.setSuccess(true);
            responseObserver.onNext(response.build());
            responseObserver.onCompleted();

        } catch (RuntimeException err) {
            logger.error("Operation failed during transactionsFactoryClose due to : " + err.getMessage());
            responseObserver.onError(err);
        }

    }

    @Override
    public void transactionCreate(TxnServer.BlockTransactionCreateRequest request,
                                  StreamObserver<TxnServer.TransactionResultObject> responseObserver) {
        try {
            logger.info("Creating new transaction");
            Transactions transactions = transactionsFactories.get(request.getTransactionsFactoryRef());

            TxnServer.TransactionResultObject response = BlockTransactions.run(transactions, connection, request);

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (RuntimeException err) {
            logger.error("Operation failed during transactionCreate due to :  " + err.getMessage());
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
        Server server = ServerBuilder.forPort(8060)
            .addService(new BlockTransactionService())
            .build();
        server.start();
        logger.info("Server Started at {}", server.getPort());
        server.awaitTermination();
    }

}
