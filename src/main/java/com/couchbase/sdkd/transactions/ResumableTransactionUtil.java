package com.couchbase.sdkd.transactions;

import com.couchbase.transactions.Transactions;

import java.util.UUID;

/**
 * Utility methods for dealing with {@link ResumableTransaction}
 */
public class ResumableTransactionUtil {
    private ResumableTransactionUtil() {}

    public static ResumableTransaction create(Transactions transactionsFactory) {
        String uuid = UUID.randomUUID().toString();
        return new ResumableTransaction(transactionsFactory, uuid);
    }
}

