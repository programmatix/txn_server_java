package com.couchbase.sdkd.transactions;

import com.couchbase.transactions.AttemptContext;
import com.couchbase.transactions.TransactionResult;
import com.couchbase.transactions.Transactions;
import com.couchbase.transactions.config.PerTransactionConfigBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;

/**
 * Represents a single transaction that's in an ongoing state.
 * <p>
 * Allows interacting with a transaction - mutation and reading documents, committing, etc. - during a test.
 */
public class ResumableTransaction {
    private final Logger logger = LogManager.getLogger(ResumableTransaction.class);
    private final Thread handle;
    private final ConcurrentLinkedQueue<ResumableTransactionCommand> queue = new ConcurrentLinkedQueue<>();
    private final String transactionRef;
    private CountDownLatch waitForResult;
    private volatile boolean result = false;

    public ResumableTransaction(Transactions transactionsFactory,
                                String transactionRef) {
        this.transactionRef = transactionRef;

        Consumer<AttemptContext> lambda = (ctx) -> {
            boolean done = false;

            while (!done) {
                logger.trace("Waiting for next command");

                ResumableTransactionCommand next = queue.poll();

                if (next != null) {

                    logger.info("Received command " + next);

                    try {
                        next.execute(ctx);

                        logger.info("Command was successful");
                        result = true;
                        waitForResult.countDown();

                        done = next.isTransactionFinished();

                    } catch (RuntimeException err) {
                        logger.info("Command threw: ", err);
                        result = false;
                        waitForResult.countDown();
                        throw err;
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

                TransactionResult result = transactionsFactory.run(lambda);

                logger.info("Txn has finished");
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

            logger.info("Finished command " + cmd + " result=", result);

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
}
