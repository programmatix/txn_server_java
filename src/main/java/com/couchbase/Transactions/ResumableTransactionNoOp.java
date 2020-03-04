package com.couchbase.Transactions;
import com.couchbase.transactions.AttemptContext;

/**
 * Does nothing, just ensures that a {@link ResumableTransaction} has got to the point
 * where it's waiting for commands.
 *
 * Used for checking state, e.g. attempt count.
 */
public class ResumableTransactionNoOp implements ResumableTransactionCommand {
    @Override
    public void execute(AttemptContext ctx) {
        // no-op
    }

    @Override
    public boolean isSuccessExpected() {
        return true;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("NoOp");
        return sb.toString();
    }
}
