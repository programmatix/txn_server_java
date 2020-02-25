/*
 * Copyright (c) 2018 Couchbase, Inc.
 *
 * Use of this software is subject to the Couchbase Inc. Enterprise Subscription License Agreement
 * which may be found at https://www.couchbase.com/ESLA-11132015.  All rights reserved.
 */

package com.couchbase.Tests.Transactions.Utils;

import com.couchbase.client.core.logging.LogRedaction;
import com.couchbase.client.core.logging.RedactionLevel;
import com.couchbase.transactions.TransactionResult;
import com.couchbase.transactions.error.TransactionFailed;

import static org.junit.Assert.assertTrue;

public class TestUtils {
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

}
