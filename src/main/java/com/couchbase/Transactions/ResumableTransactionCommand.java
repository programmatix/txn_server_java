package com.couchbase.Transactions;
import com.couchbase.transactions.AttemptContext;

public interface ResumableTransactionCommand {

    void execute(AttemptContext ctx);

    // Whether to break out of the command loop inside the transaction lambda.  Note this doesn't include
    // commit/rollback, to allow us to test user error like double-committing.
    default boolean finishWaitingForCommands() {
        return false;
    }

    // If the test expects this command to succeed
    boolean isSuccessExpected();
}
