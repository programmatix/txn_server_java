package com.couchbase.Transactions;

import com.couchbase.InternalDriverFailure;
import com.couchbase.Logging.LogUtil;
import com.couchbase.Utils.ResultsUtil;
import com.couchbase.client.core.logging.LogRedaction;
import com.couchbase.client.core.logging.RedactionLevel;
import com.couchbase.client.java.ReactiveCollection;
import com.couchbase.grpc.protocol.TxnServer;
import com.couchbase.transactions.AttemptContext;
import com.couchbase.transactions.TransactionAttempt;
import com.couchbase.transactions.TransactionResult;
import com.couchbase.transactions.Transactions;
import com.couchbase.transactions.error.TransactionFailed;
import org.slf4j.Logger;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;

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

    // This is used to wait for the result of a single operation (insert a doc, etc.)
    private CountDownLatch waitForCommandResult;

    // This is used to wait for the transaction to fully complete (e.g. return or throw)
    private CountDownLatch waitForOverallResult = new CountDownLatch(1);

    private volatile Optional<TransactionResult> transactionResult = Optional.empty();
    private volatile Optional<Exception> exception = Optional.empty();
    private volatile boolean commandResult = false;
    // Some internal driver error has happened on this transaction.  Reserve this solely for
    // driver problems - e.g. that it doesn't recognize a particular hook or command.
    private volatile Optional<RuntimeException> commandFatalError = Optional.empty();
    private volatile Set<String> attemptsSeen = new HashSet<>();

    private void transactionLogic(AttemptContext ctx) {
        // We're now inside a regular transaction lambda.  This logic is going to wait for commands and execute them
        // one at a time.
        boolean done = false;

        while (!done) {
            attemptsSeen.add(ctx.attemptId());
            String bp = attemptNumber() + ": ";

            logger.trace(bp + "Waiting for next command");
            ResumableTransactionCommand next = queue.poll();

            if (next != null ) {
                // Received a command - insert a doc, commit the txn, etc
                // Whatever happens next, must call waitForCommandResult.countDown(), as the sender of the command is waiting for
                // that response

                logger.info(bp + "Received command: " + next);

                try {
                    next.execute(ctx);
                    logger.info(bp + "Command was successful");
                    commandResult = true;
                    waitForCommandResult.countDown();

                    // Some commands signal the txn is expected to be done at this point, and drop us out of the
                    // loop.  Note this doesn't include commit/rollback, to allow us to test user error like
                    // double-committing.
                    done = next.finishWaitingForCommands();

                    if (!next.isSuccessExpected()) {
                        logger.warn(bp + "Command succeeded but was meant to fail!");
                        dump(ctx);
                    }
                } catch (RuntimeException e) {
                    if (e instanceof InternalDriverFailure) {
                        logger.error("Internal error thrown {}, bailing out", e);
                        commandFatalError = Optional.of(e);
                        done = true;
                        dump(ctx);
                    }
                    else {
                        if (next.isSuccessExpected()) {
                            logger.warn(bp + "Command threw an unexpected error: " + e.getMessage());
                            dump(ctx);
                        } else {
                            logger.info(bp + "Command threw an error (which was expected): " + e.getMessage());
                        }
                    }

                    commandResult = false;
                    waitForCommandResult.countDown();

                    // Always rethrow e, or transactions won't work
                    // Why do we need to throw. If we throw e, we will not be able to test negative test cases like rollback after commit etc.
                    throw e;
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
    }

    public ResumableTransaction(Transactions transactionsFactory,
                                String transactionRef) {
        this.transactionRef = transactionRef;

        // The lambda needs to be running in its own thread in the background
        handle = new Thread(() -> {
            try {
                logger.info("Starting txn in separate thread");

                // This starts a transaction
                transactionResult = Optional.of(transactionsFactory.run(this::transactionLogic));

                logger.info("transactionResult:"+transactionResult);

            } catch (TransactionFailed err) {
                logger.info("Txn has failed with error " + err.getMessage());
                exception = Optional.of(err);
                transactionResult = Optional.of(err.result());
            } catch (RuntimeException err) {
                logger.info("Txn has failed with a non-TransacionFailed error!");
                // Should never get here
                System.exit(-1);
            }

            waitForOverallResult.countDown();
        });

        handle.start();
    }

    private void dump(AttemptContext ctx) {
        logger.warn("Dumping logs so far for debugging:");
        ctx.logger().logs().forEach(l ->
            logger.info("    " + l.toString()));
    }

    public int attemptNumber() {
        return attemptsSeen.size() - 1;
    }

    public boolean executeCommandBlocking(ResumableTransactionCommand cmd) {
        logger.info("Running command " + cmd);

        commandFatalError.ifPresent(err -> {
            logger.error("Transaction has previously failed fatally with internal error, no further ops allowed");
            throw err;
        });

        waitForCommandResult = new CountDownLatch(1);
        queue.add(cmd);
        try {
            waitForCommandResult.await();
            logger.info("Finished command " + cmd + " result= "+ commandResult);

            commandFatalError.ifPresent(err -> {
                throw err;
            });

            return commandResult;
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


    public TxnServer.TransactionResultObject shutdownAndVerify(ResumableTransactionCommand cmd) throws InterruptedException {
        boolean shutdownSuccess = executeCommandBlocking(cmd);
        logger.info("shutdownSuccess Status: " + shutdownSuccess);
        if (!shutdownSuccess) {
            throw new IllegalStateException("Failed to close down transaction neatly");
        }

        logger.info("Waiting for transaction to finish");
        waitForOverallResult.countDown();
        logger.info("Transaction is finished, exception={}, result={}",
            exception, transactionResult);

        return ResultsUtil.createResult(exception, transactionResult);
    }

}
