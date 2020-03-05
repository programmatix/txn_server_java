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
import com.couchbase.client.core.logging.LogRedaction;
import com.couchbase.client.core.logging.RedactionLevel;
import com.couchbase.client.java.ReactiveCollection;
import com.couchbase.grpc.protocol.TxnServer;
import com.couchbase.transactions.TransactionAttempt;
import com.couchbase.transactions.TransactionResult;

import java.util.Optional;

public class ResultsUtil {
    private ResultsUtil() {}

    public static TxnServer.TransactionResultObject createResult(Optional<Exception> exception,
                                                                 Optional<TransactionResult> transactionResult) {
        TxnServer.TransactionResultObject.Builder response =
            TxnServer.TransactionResultObject.getDefaultInstance().newBuilderForType();

        exception.ifPresent(ex -> {
            // TODO need to map this more generically across C++ and Java
            response.setExceptionName(ex.getMessage());
        });

        transactionResult.ifPresent(tr -> {

            TransactionAttempt mostRecent = tr.attempts().get(tr.attempts().size() - 1);

            response.setMutationTokensSize(tr.mutationTokens().size())
                .setAtrCollection(mostRecent.atrCollection()
                    .map(ReactiveCollection::name).orElse("not available"))
                .setAtrId(mostRecent.atrId().orElse("not available"));

            for(int i = 0; i < tr.attempts().size(); i ++) {
                TransactionAttempt ta = tr.attempts().get(i);

                TxnServer.AttemptStates attemptState;
                switch (ta.finalState()) {
                    case ABORTED:
                        attemptState = TxnServer.AttemptStates.ABORTED;
                        break;
                    case COMMITTED:
                        attemptState = TxnServer.AttemptStates.COMMITTED;
                        break;
                    case NOT_STARTED:
                        attemptState = TxnServer.AttemptStates.NOT_STARTED;
                        break;
                    case COMPLETED:
                        attemptState = TxnServer.AttemptStates.COMPLETED;
                        break;
                    case PENDING:
                        attemptState = TxnServer.AttemptStates.PENDING;
                        break;
                    case ROLLED_BACK:
                        attemptState = TxnServer.AttemptStates.ROLLED_BACK;
                        break;
                    default:
                        throw new IllegalStateException("Bad state " + ta.finalState());
                }

                response.addAttempts(TxnServer.TransactionAttempt.newBuilder()
                    .setState(attemptState)
                    .setAttemptId(ta.attemptId())
                    .build());
            }

            // Force that log redaction has been enabled
            LogRedaction.setRedactionLevel(RedactionLevel.PARTIAL);

            tr.log().logs().forEach(l ->
                response.addLog(l.toString()));
        });

        return response.build();
    }
}
