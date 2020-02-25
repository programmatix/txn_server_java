package com.couchbase.Tests.Transactions.Utils;

import com.couchbase.Logging.LogUtil;
import com.couchbase.transactions.AttemptContext;
import com.couchbase.transactions.TransactionResult;
import com.couchbase.transactions.Transactions;
import org.slf4j.Logger;

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
                    lastCommand=next;
                    logger.info("Received command: " + next);

                    try {
                        next.execute(ctx);
                        result = lastCommand.assertions();
                        logger.info("Command was successful");
                        waitForResult.countDown();
                        done = next.isTransactionFinished();
                    }catch (Exception e) {
                        logger.info("Command threw Exception: "+ e.getMessage());
                        result = lastCommand.assertions(null,e);
                        waitForResult.countDown();
                        done=true;
                        // throw err;
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
                lastCommand.assertions();
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

    public TransactionResult gettransactionResult(){return transactionResult;}


}
