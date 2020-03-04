package com.couchbase.Transactions;
import com.couchbase.transactions.AttemptContext;

/**
 * Commits a running {@link ResumableTransaction}
 */
public class ResumableTransactionCommit implements ResumableTransactionCommand {
    @Override
    public void execute(AttemptContext ctx) {
        ctx.commit();
    }

    @Override
    public boolean isSuccessExpected() {
        // TODO would be useful for some tests like commit-after-rollback to not hardcode this
        return true;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Commit");
        return sb.toString();
    }
}
