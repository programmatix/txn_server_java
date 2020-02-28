package com.couchbase.Transactions;

import com.couchbase.Logging.LogUtil;
import com.couchbase.Utils.ResultObject;
import com.couchbase.transactions.AttemptContext;
import com.couchbase.transactions.TransactionAttempt;
import com.couchbase.transactions.TransactionResult;
import com.couchbase.transactions.Transactions;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;

/**
 * Represents a single transaction that's in an ongoing state.
 * <p>
 * Allows interacting with a transaction - mutation and reading documents, committing, etc. - during a test.
 */
public class ResumableTransaction {
    private final Logger logger = LogUtil.getLogger(ResumableTransaction.class);
    private final Thread handle;
    private final ConcurrentLinkedQueue<ResumableTransactionCommand> queue = new ConcurrentLinkedQueue<>();
    private final String transactionRef;
    private CountDownLatch waitForResult;
    private  TransactionResult transactionResult;
    private Exception exception;
    private volatile boolean result = false;
    ResumableTransactionCommand lastCommand;

    public ResumableTransaction(Transactions transactionsFactory,
                                String transactionRef) {
        this.transactionRef = transactionRef;

        Consumer<AttemptContext> lambda = (ctx) -> {
             boolean done = false;

            while (!done) {
                logger.trace("Waiting for next command");
                ResumableTransactionCommand next = queue.poll();

                if (next != null ) {
                    logger.info("Received command: " + next);

                    try {
                        next.execute(ctx);
                        logger.info("Command was successful");
                        result=true;
                        waitForResult.countDown();

                        done = next.isTransactionFinished();
                    }catch (Exception e) {
                        logger.info("Command threw getClass: "+ e.getMessage());
                        result=false;
                        exception=e;
                        waitForResult.countDown();
                       // throw e;
                    }
                }
                else {
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        };

        handle = new Thread(() -> {
            try {
                logger.info("Starting txn in separate thread");
                transactionResult = transactionsFactory.run(lambda);
                logger.info("transactionResult:"+transactionResult);

            } catch (RuntimeException err) {
                logger.info("Txn has failed with error " + err.getMessage());
            }
        });

        handle.start();
    }

    public boolean executeCommandBlocking(ResumableTransactionCommand cmd) {
        logger.info("Running command " + cmd);

        // TODO probably a neater way of waiting for results
        waitForResult = new CountDownLatch(1);
        queue.add(cmd);
        try {
            waitForResult.await();
          //  result  = cmd.assertions();
            logger.info("Finished command " + cmd + " result= "+ result);
            return result;
        } catch (InterruptedException e) {
            logger.warn("Interrupted");
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Uniquely identifies this.
     *
     * Note that this is distinct from the transaction id and attempt id, used by the transactions library itself.
     */
    public String transactionRef() {
        return transactionRef;
    }


    public ResultObject shutdownandverify(ResumableTransactionCommand cmd) throws InterruptedException {
        boolean shutdownsuccess = executeCommandBlocking(cmd);

        //TODO Better way for handling below rather than sleep
        Thread.sleep(1000);
        if(shutdownsuccess){
            return  generateResultObject(transactionResult);
        }else {
            logger.info("Issue with the txn service shutdown");
            return null;
        }

    }


    public ResultObject generateResultObject(TransactionResult result){
        TransactionAttempt attempt = result.attempts().stream().findFirst().get();
        List<String> logs = new ArrayList<String>();
        String exceptionName="";

       /* logger.info("result.mutationTokens().size():"+result.mutationTokens().size());
        logger.info("result.attempts().size():"+result.attempts().size());
        logger.info("attempt.finalState():"+attempt.finalState());
        logger.info("attempt.atrCollection().isPresent():"+attempt.atrCollection().isPresent());
        logger.info("attempt.atrId().isPresent():"+attempt.atrId().isPresent());*/

        if(exception!=null){
            exceptionName=exception.getClass().getName();
            logger.info("exceptionName:"+exceptionName);
        }

        return new ResultObject(result.mutationTokens().size(),result.attempts().size(),attempt.finalState(),attempt.atrCollection().isPresent(),attempt.atrId().isPresent(),exceptionName,result.log().logs());
    }


}
