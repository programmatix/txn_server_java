package com.couchbase.Tests.Transactions.Utils;

import com.couchbase.transactions.AttemptContext;

/**
 * Commits a running {@link ResumableTransaction}
 */
public class ResumableTransactionCommit implements ResumableTransactionCommand {
    public ResumableTransactionCommit() {
    }

    @Override
    public boolean isTransactionFinished() {
        return true;
    }

    @Override
    public void execute(AttemptContext ctx) {
        ctx.commit();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Commit{}");
        return sb.toString();
    }
}
