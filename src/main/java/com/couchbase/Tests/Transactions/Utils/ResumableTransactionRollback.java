package com.couchbase.Tests.Transactions.Utils;

import com.couchbase.Logging.LogUtil;
import com.couchbase.transactions.AttemptContext;
import com.couchbase.transactions.TransactionResult;
import com.couchbase.transactions.error.attempts.AttemptException;
import org.slf4j.Logger;

import static org.junit.Assert.assertTrue;

/**
 * Inserts a doc, in a running {@link ResumableTransaction}
 */
public class ResumableTransactionRollback implements ResumableTransactionCommand {
    private final Logger logger = LogUtil.getLogger(ResumableTransaction.class);
    private String name="rollback";
    @Override
    public void execute(AttemptContext ctx) {
        ctx.rollback();
    }

    @Override
    public boolean isTransactionFinished() {
        return true;
    }



    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Rollback Transaction");
        return sb.toString();
    }

    @Override
    public boolean assertions(TransactionResult transactionResult, Exception e){
        if(e!=null){
            logger.info("Performing assertions from rollback for exception:"+e.getMessage());
            assertTrue(e instanceof AttemptException);
            //TODO BELOW checkLogRedactionIfEnabled LATER
            //checkLogRedactionIfEnabled(e.result(), docId);
        }else{
            return false;
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
