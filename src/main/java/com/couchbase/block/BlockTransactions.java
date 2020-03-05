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
package com.couchbase.block;

import com.couchbase.InternalDriverFailure;
import com.couchbase.Logging.LogUtil;
import com.couchbase.Transactions.ResumableTransaction;
import com.couchbase.Transactions.ResumableTransactionInsert;
import com.couchbase.Utils.ClusterConnection;
import com.couchbase.Utils.ResultsUtil;
import com.couchbase.client.java.Collection;
import com.couchbase.client.java.json.JsonObject;
import com.couchbase.grpc.protocol.TxnServer;
import com.couchbase.transactions.AttemptContext;
import com.couchbase.transactions.TransactionGetResult;
import com.couchbase.transactions.TransactionResult;
import com.couchbase.transactions.Transactions;
import com.couchbase.transactions.error.TransactionFailed;
import org.slf4j.Logger;

import java.util.Optional;


/**
 * Runs a transaction.
 */
public class BlockTransactions {
    private static final Logger logger = LogUtil.getLogger(BlockTransactions.class);

    private static void dump(AttemptContext ctx) {
        logger.warn("Dumping logs so far for debugging:");
        ctx.logger().logs().forEach(l ->
            logger.info("    " + l.toString()));
    }

    public static TxnServer.TransactionResultObject run(
        Transactions transactionsFactory,
        ClusterConnection connection,
        TxnServer.BlockTransactionCreateRequest req) {

        try {
            TransactionResult result = transactionsFactory.run((ctx) -> {
                logger.info("Starting attempt {}", ctx.attemptId());

                for (int i = 0; i < req.getOperationsCount(); i++) {
                    TxnServer.BlockTransactionOperation op = req.getOperations(i);

                    performOperation(connection, ctx, op);
                }
            });

            return ResultsUtil.createResult(Optional.empty(), Optional.of(result));
        } catch (TransactionFailed err) {
            return ResultsUtil.createResult(Optional.of(err), Optional.of(err.result()));
        }
    }

    private static void performOperation(ClusterConnection connection, AttemptContext ctx,
                                         TxnServer.BlockTransactionOperation op) {
        if (op.hasInsert()) {
            TxnServer.TransactionInsertRequest request = op.getInsert();
            JsonObject content = JsonObject.fromJson(request.getContentJson());

            performOperation(ctx, request.getExpectedResult(),
                () -> {
                    logger.info("Performing insert operation on {}",
                        request.getDocId());
                    ctx.insert(connection.getBucket().defaultCollection(),
                        request.getDocId(),
                        content);
                });
        } else if (op.hasReplace()) {
            TxnServer.TransactionUpdateRequest request = op.getReplace();
            JsonObject content = JsonObject.fromJson(request.getContentJson());
            Collection collection = connection.getBucket().defaultCollection();

            performOperation(ctx, request.getExpectedResult(),
                () -> {
                    logger.info("Performing replace operation {} to {}",
                        request.getDocId(), request.getContentJson());
                    TransactionGetResult r = ctx.get(collection, request.getDocId());
                    ctx.replace(r, content);
                });
        } else if (op.hasRemove()) {
            TxnServer.TransactionDeleteRequest request = op.getRemove();
            Collection collection = connection.getBucket().defaultCollection();

            performOperation(ctx, request.getExpectedResult(),
                () -> {
                    logger.info("Performing remove operation on {}",
                        request.getDocId());
                    TransactionGetResult r = ctx.get(collection, request.getDocId());
                    ctx.remove(r);
                });
        } else if (op.hasCommit()) {
            TxnServer.TransactionCommitRequest request = op.getCommit();

            performOperation(ctx, request.getExpectedResult(),
                () -> {
                    logger.info("Performing commit operation");
                    ctx.commit();
                });
        } else if (op.hasRollback()) {
            TxnServer.TransactionRollbackRequest request = op.getRollback();

            performOperation(ctx, request.getExpectedResult(),
                () -> {
                    logger.info("Performing rollback operation");
                    ctx.rollback();
                });
        } else {
            throw new InternalDriverFailure(new IllegalArgumentException("Unknown operation"));
        }
    }

    private static void performOperation(AttemptContext ctx,
                                         TxnServer.ExpectedResult expectedResult,
                                         Runnable op) {
        try {
            op.run();

            if (expectedResult != TxnServer.ExpectedResult.EXPECT_SUCCESS) {
                logger.warn("Operation succeeded, unexpectedly");
                dump(ctx);
            }
        } catch (RuntimeException err) {
            if (expectedResult != TxnServer.ExpectedResult.THROWS) {
                logger.warn("Operation failed unexpectedly with {}", err);
                dump(ctx);
            } else {
                logger.info("Operation failed, as expected.  Err={}", err);
            }

            throw err;
        }
    }
}
