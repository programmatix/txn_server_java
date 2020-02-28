package com.couchbase.Transactions;

import com.couchbase.Logging.LogUtil;
import com.couchbase.Utils.TestUtils;
import com.couchbase.transactions.AttemptContext;
import com.couchbase.transactions.TransactionResult;
import com.couchbase.transactions.support.AttemptStates;
import org.slf4j.Logger;

import static org.junit.Assert.assertEquals;

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
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Rollback Transaction");
        return sb.toString();
    }

    @Override
    public boolean assertions(TransactionResult transactionResult, Exception e){
        /*if(e!=null){
            if(expectFailure){
                logger.info("Performing assertions from rollback for exception:"+e.getMessage());
                assertTrue(e instanceof AttemptException);
                //TODO BELOW checkLogRedactionIfEnabled LATER
                //checkLogRedactionIfEnabled(e.result(), docId);
                return true;
            }else return false;
        }else */return true;
    }

    public boolean assertionsempty(TransactionResult result){
       // assertEquals(0, TestUtils.numAtrs(collection, transactions.config(), span));
        TestUtils.assertEmptyTxn(result, AttemptStates.ROLLED_BACK);
        assertEquals(0, result.mutationTokens().size());
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
