package com.couchbase.Transactions;
import com.couchbase.transactions.AttemptContext;
import com.couchbase.transactions.TransactionResult;

public interface ResumableTransactionCommand {

    void execute(AttemptContext ctx);
    default boolean isTransactionFinished() {
        return false;
    }
    boolean assertions(TransactionResult transactionResult, Exception e);
    default String getname(){ return "genericcommand";}
    boolean assertions();
}
