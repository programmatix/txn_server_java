package com.couchbase.Transactions;

import com.couchbase.transactions.AttemptContext;

/**
 * Inserts a doc, in a running {@link ResumableTransaction}
 */
public class ResumableTransactionRollback implements ResumableTransactionCommand {

    @Override
    public void execute(AttemptContext ctx) {
        ctx.rollback();
    }


    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Rollback Transaction");
        return sb.toString();
    }
}
