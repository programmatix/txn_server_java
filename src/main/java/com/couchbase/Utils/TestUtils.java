/*
 * Copyright (c) 2018 Couchbase, Inc.
 *
 * Use of this software is subject to the Couchbase Inc. Enterprise Subscription License Agreement
 * which may be found at https://www.couchbase.com/ESLA-11132015.  All rights reserved.
 */

package com.couchbase.Utils;

import com.couchbase.client.core.logging.LogRedaction;
import com.couchbase.client.core.logging.RedactionLevel;
import com.couchbase.transactions.TransactionAttempt;
import com.couchbase.transactions.TransactionResult;
import com.couchbase.transactions.error.TransactionFailed;
import com.couchbase.transactions.support.AttemptStates;
import org.junit.Assert;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

import static org.junit.Assert.*;

public class TestUtils {
    public static final Duration timeout = Duration.of(2500, ChronoUnit.MILLIS);
    public static Throwable assertTransactionFailed(Throwable e) {
        assertTrue(e instanceof TransactionFailed);
        TransactionFailed err = (TransactionFailed) e;
        return err.getCause();
    }

    public static void checkLogRedactionIfEnabled(TransactionResult result, String id) {
        LogRedaction.setRedactionLevel(RedactionLevel.PARTIAL);

        result.log().logs().forEach(l -> {
            String s = l.toString();
            System.out.println(s);
            if (s.contains(id)) {
                assertTrue(l.toString().contains("<ud>" + id + "</ud>"));
            }
        });
    }

    /*public static void assertion(com.couchbase.Tests.Transactions.Utils.ResumableTransactionCommand cmd, String scenario){

    }
*/
   /* public static long numAtrs(Collection collection, TransactionConfig config, Span span) {
        return getExistingATRs(collection, config, span)
                .count()
                .block();
    }

    public static Flux<ATR> getExistingATRs(Collection collection, TransactionConfig config, Span span) {
        return Flux.fromIterable(Transactions.allAtrs(config.numAtrs()))
                // Introducing this check reduces time taken by an order of magnitude
                .flatMap(atrId -> collection.reactive().get(atrId)

                        .flatMap(atrDoc -> {
                            return ActiveTransactionRecord.getAtr(collection.reactive(), atrId, timeout, config,
                                    TestUtils.from(span));
                        })

                        .onErrorResume(err -> {
                            if (err instanceof DocumentNotFoundException) return Mono.empty();
                            else return Mono.error(err);
                        })

                        .flatMap(v -> {
                            if (v.isPresent()) return Mono.just(v.get());
                            else return Mono.empty();
                        }));
    }

    public static SpanWrapper from(Span span) {
        // Once OpenTelemetry is available, this can do something again
        return new SpanWrapper();
    }
    */


    public static void assertEmptyTxn(TransactionResult result, AttemptStates expectedState) {
        assertEquals(1, result.attempts().size());
        TransactionAttempt attempt = result.attempts().stream().findFirst().get();
        Assert.assertEquals(expectedState, attempt.finalState());
        assertFalse(attempt.atrCollection().isPresent());
        assertFalse(attempt.atrId().isPresent());
    }
}
