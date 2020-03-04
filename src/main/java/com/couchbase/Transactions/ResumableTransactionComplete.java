package com.couchbase.Transactions;
import com.couchbase.Logging.LogUtil;
import com.couchbase.transactions.AttemptContext;
import org.slf4j.Logger;

/**
 * Completes a running {@link ResumableTransaction}.  This doesn't relate to a transactions API operation, it's
 * to do with finishing up driver resources.
 *
 * That is, it waits for the transaction to complete successfully or throw an exception, and
 * returns the result of that.
 */
public class ResumableTransactionComplete implements ResumableTransactionCommand {
    private final Logger logger = LogUtil.getLogger(ResumableTransaction.class);
    boolean isTransactionFinished;

    public ResumableTransactionComplete(boolean isTransactionFinished){
        this.isTransactionFinished = isTransactionFinished;
    }

    @Override
    public boolean finishWaitingForCommands() {
        return isTransactionFinished;
    }

    @Override
    public boolean isSuccessExpected() {
        return true;
    }

    @Override
    public void execute(AttemptContext ctx) {
        logger.info("Completed Empty Transaction");
    }


    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(" Empty Transaction");
        return sb.toString();
    }
}
