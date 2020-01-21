package com.couchbase.sdkd.transactions;

import com.couchbase.transactions.AttemptContext;

public interface ResumableTransactionCommand {
    void execute(AttemptContext ctx);

    default boolean isTransactionFinished() {
        return false;
    }
}
