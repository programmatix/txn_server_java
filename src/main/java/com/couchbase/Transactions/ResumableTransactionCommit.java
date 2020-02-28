package com.couchbase.Transactions;
import com.couchbase.transactions.AttemptContext;
import com.couchbase.transactions.TransactionResult;

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

    @Override
    public boolean assertions(TransactionResult transactionResult, Exception e){
       /* if(isEmptyTxn)
        {
            assertEquals(AttemptStates.COMPLETED ,transactionResult.attempts().stream().findFirst().get().finalState());
            assertFalse(transactionResult.attempts().stream().findFirst().get().atrCollection().isPresent());
            assertFalse(transactionResult.attempts().stream().findFirst().get().atrId().isPresent());
            assertEquals(0, transactionResult.mutationTokens().size());
        }*/
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
