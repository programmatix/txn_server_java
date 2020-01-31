package com.couchbase.Tests.Transactions.Utils;

import com.couchbase.transactions.AttemptContext;

public interface ResumableTransactionCommand {
    void execute(AttemptContext ctx);

    default boolean isTransactionFinished() {
        return false;
    }
}
