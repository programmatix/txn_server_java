package com.couchbase.Tests.Transactions.Utils;

import com.couchbase.transactions.AttemptContext;
import com.couchbase.transactions.TransactionResult;
import com.couchbase.transactions.support.AttemptStates;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * Commits a running {@link ResumableTransaction}
 */
public class ResumableTransactionCommit implements ResumableTransactionCommand {
    boolean isEmpty;
    boolean isTransactionFinished=true;
    private String name="commit";

    public ResumableTransactionCommit(boolean isEmpty, boolean isTransactionFinished ) {
        this.isEmpty=isEmpty;
        this.isTransactionFinished =isTransactionFinished;
    }

    @Override
    public boolean isTransactionFinished() {
        return isTransactionFinished;
    }

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

    @Override
    public boolean assertions(TransactionResult transactionResult, Exception e){
        if(isEmpty)
        {
            assertEquals(AttemptStates.COMPLETED ,transactionResult.attempts().stream().findFirst().get().finalState());
            assertFalse(transactionResult.attempts().stream().findFirst().get().atrCollection().isPresent());
            assertFalse(transactionResult.attempts().stream().findFirst().get().atrId().isPresent());
            assertEquals(0, transactionResult.mutationTokens().size());
        }
        return true;
    }

    @Override
    public  String getname(){
        return name;
    }

    @Override
    public boolean assertions(){
        return true;
    }

}
