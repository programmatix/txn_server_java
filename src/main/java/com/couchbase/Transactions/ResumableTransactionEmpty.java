package com.couchbase.Transactions;
import com.couchbase.Logging.LogUtil;
import com.couchbase.transactions.AttemptContext;
import com.couchbase.transactions.TransactionResult;
import org.slf4j.Logger;

/**
 * Inserts a doc, in a running {@link ResumableTransaction}
 */
public class ResumableTransactionEmpty implements ResumableTransactionCommand {
    private final Logger logger = LogUtil.getLogger(ResumableTransaction.class);
    private String name="empty";
    boolean isTransactionFinished;

    public ResumableTransactionEmpty(boolean isTransactionFinished){
        this.isTransactionFinished = isTransactionFinished;
    }

    @Override
    public boolean isTransactionFinished() {
        return isTransactionFinished;
    }

    @Override
    public void execute(AttemptContext ctx) {
        logger.error("Completed Empty Transaction");
    }


    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(" Empty Transaction");
        return sb.toString();
    }

    @Override
    public boolean assertions(TransactionResult transactionResult, Exception e){return true; }

    @Override
    public  String getname(){
        return name;
    }

    @Override
    public boolean assertions(){
        return true;
    }
}
