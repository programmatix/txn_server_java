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
package com.couchbase.Utils;

import com.couchbase.InternalDriverFailure;
import com.couchbase.Logging.LogUtil;
import com.couchbase.client.core.error.TemporaryFailureException;
import com.couchbase.client.java.Collection;
import com.couchbase.client.java.codec.RawJsonTranscoder;
import com.couchbase.client.java.kv.UpsertOptions;
import com.couchbase.grpc.protocol.TxnServer;
import com.couchbase.transactions.AttemptContextReactive;
import com.couchbase.transactions.error.internal.AbortedAsRequested;
import com.couchbase.transactions.error.internal.AbortedAsRequestedNoRollbackNoCleanup;
import com.couchbase.transactions.support.AttemptContextFactory;
import com.couchbase.transactions.support.DefaultAttemptContextFactory;
import com.couchbase.transactions.util.TestAttemptContextFactory;
import com.couchbase.transactions.util.TransactionMock;
import org.slf4j.Logger;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Utility routines related to configuring transaction hooks.
 */
public class HooksUtil {
    private HooksUtil() {
    }

    static Logger logger = LogUtil.getLogger(HooksUtil.class);

    private static Mono<Integer> configureHook(final AtomicInteger callCount,
                                               final TxnServer.Hook hook,
                                               final AttemptContextReactive ctx,
                                               final ClusterConnection clusterConn) {

        return Mono.defer(() -> {
            Mono<Integer> out;
            // This action may or may not be taken, depending on the hook conditionals
            Mono<Integer> action;
            switch (hook.getHookAction()) {
                case FAIL_NO_ROLLBACK:
                    action = Mono.error(new AbortedAsRequestedNoRollbackNoCleanup());
                    break;
                case FAIL_ROLLBACK:
                    action = Mono.error(new AbortedAsRequested());
                    break;
                case FAIL_RETRY:
                    action = Mono.error(new TemporaryFailureException(null));
                    break;
                case MUTATE_DOC:
                    // In format "bucket-name/collection-name/doc-id"
                    try {
                        String docLocation = hook.getHookActionParam1();
                        String[] splits = docLocation.split("/");
                        String bucketName = splits[0];
                        String collectionName = splits[1];
                        String docId = splits[2];
                        Collection coll = clusterConn.getCluster().bucket(bucketName).collection(collectionName);
                        String content = hook.getHookActionParam2();

                        action = coll.reactive().upsert(docId, content,
                            UpsertOptions.upsertOptions().transcoder(RawJsonTranscoder.INSTANCE))
                            .doOnSubscribe(v -> logger.info("Executing hook to mutate doc {} with" +
                                " content {}", docId, content))
                            .thenReturn(0);
                    } catch (RuntimeException err) {
                        throw new InternalDriverFailure(err);
                    }

                    break;
                default:
                    throw new InternalDriverFailure(
                        new IllegalStateException("Cannot handle hook error " + hook.getHookAction()));
            }

            switch (hook.getHookCondition()) {
                case ON_CALL:
                    final int onCallNumber = hook.getHookConditionParam();
                    out = Mono.defer(() -> {
                        int callNumber = callCount.get();
                        logger.info("Evaluating whether to execute ON_CALL hook: call count={} desired={} call = {}",
                            callNumber, onCallNumber, System.identityHashCode(callCount));
                        if (callNumber == onCallNumber) {
                            return action;
                        } else {
                            return Mono.just(1);
                        }
                    });
                    break;

                case ALWAYS:
                    out = action.doOnSubscribe(v ->
                        logger.info("Executing hook ALWAYS"));
                    break;

                default:
                    throw new InternalDriverFailure(
                        new IllegalStateException("Cannot handle hook condition " + hook.getHookCondition()));
            }

            // Make sure callCount gets incremented each time this is called
            return Mono.fromRunnable(() -> callCount.incrementAndGet())
                .then(out);
        });
    }

    public static AttemptContextFactory configureHooks(TxnServer.TransactionsFactoryCreateRequest request,
                                                       ClusterConnection clusterConn) {
        if (request.getHookCount() != 0) {
            TransactionMock mock = new TransactionMock();

            for (int i = 0; i < request.getHookCount(); i++) {
                TxnServer.Hook hook = request.getHook(i);

                // Should get one callCount per ResumableTransaction, per hook
                final AtomicInteger callCount = new AtomicInteger(0);

                switch (hook.getHookPoint()) {
                    case BEFORE_ATR_COMMIT:
                        mock.beforeAtrCommit = (ctx) -> configureHook(callCount, hook, ctx, clusterConn);
                        break;

                    case AFTER_GET_COMPLETE:
                        mock.afterGetComplete = (ctx, id) -> configureHook(callCount, hook, ctx, clusterConn);
                        break;

                    default:
                        throw new InternalDriverFailure(
                            new IllegalStateException("Cannot handle hook point " + hook.getHookPoint()));
                }
            }

            return new TestAttemptContextFactory(mock);
        }

        return new DefaultAttemptContextFactory();
    }
}
