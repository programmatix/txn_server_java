package com.couchbase.Transactions;
import com.couchbase.transactions.AttemptContext;

/**
 * Commits a running {@link ResumableTransaction}
 */
public class ResumableTransactionCommit implements ResumableTransactionCommand {
    private String name="commit";

    @Override
    public void execute(AttemptContext ctx) {
        ctx.commit();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Commit");
        return sb.toString();
    }
}
