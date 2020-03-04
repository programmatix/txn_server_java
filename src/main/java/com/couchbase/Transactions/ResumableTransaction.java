package com.couchbase.Transactions;

import com.couchbase.Logging.LogUtil;
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

import java.util.Optional;
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

    // This is used to wait for the result of a single operation (insert a doc, etc.)
    private CountDownLatch waitForCommandResult;

    // This is used to wait for the transaction to fully complete (e.g. return or throw)
    private CountDownLatch waitForOverallResult = new CountDownLatch(1);

    private volatile Optional<TransactionResult> transactionResult = Optional.empty();
    private volatile Optional<Exception> exception = Optional.empty();
    private volatile boolean commandResult = false;
    ResumableTransactionCommand lastCommand;

    public ResumableTransaction(Transactions transactionsFactory,
                                String transactionRef) {
        this.transactionRef = transactionRef;

        Consumer<AttemptContext> lambda = (ctx) -> {
            // We're now inside a regular transaction lambda.  This logic is going to wait for commands and execute them
            // one at a time.
            boolean done = false;

            while (!done) {
                logger.trace("Waiting for next command");
                ResumableTransactionCommand next = queue.poll();

                if (next != null ) {
                    // Received a command - insert a doc, commit the txn, etc
                    // Whatever happens next, must call waitForCommandResult.countDown(), as the sender of the command is waiting for
                    // that response

                    logger.info("Received command: " + next);

                    try {
                        next.execute(ctx);
                        logger.info("Command was successful");
                        commandResult = true;
                        waitForCommandResult.countDown();

                        // Some commands signal the txn is expected to be done at this point, and drop us out of the
                        // loop
                        done = next.isTransactionFinished();
                    } catch (Exception e) {
                        logger.info("Command threw getClass: "+ e.getMessage());
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
        };

        // The lambda needs to be running in its own thread in the background
        handle = new Thread(() -> {
            try {
                logger.info("Starting txn in separate thread");
                transactionResult = Optional.of(transactionsFactory.run(lambda));
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

    public boolean executeCommandBlocking(ResumableTransactionCommand cmd) {
        logger.info("Running command " + cmd);

        // TODO probably a neater way of waiting for results
        waitForCommandResult = new CountDownLatch(1);
        queue.add(cmd);
        try {
            waitForCommandResult.await();
            logger.info("Finished command " + cmd + " result= "+ commandResult);
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
        logger.error("shutdownSuccess Status: "+shutdownSuccess);
        if (!shutdownSuccess) {
            throw new IllegalStateException("Failed to close down transaction neatly");
        }

        logger.info("Waiting for transaction to finish");
        waitForOverallResult.countDown();
        logger.info("Transaction is finished");

        TxnServer.TransactionResultObject.Builder response =
            TxnServer.TransactionResultObject.getDefaultInstance().newBuilderForType();

        exception.ifPresent(ex -> {
            // TODO need to map this more generically across C++ and Java
            response.setExceptionName(ex.getMessage());
        });

        transactionResult.ifPresent(tr -> {

            TransactionAttempt mostRecent = tr.attempts().get(tr.attempts().size() - 1);

            response.setMutationTokensSize(tr.mutationTokens().size())
                .setAtrCollection(mostRecent.atrCollection()
                    .map(ReactiveCollection::name).orElse("not available"))
                .setAtrId(mostRecent.atrId().orElse("not available"));

            for(int i = 0; i < tr.attempts().size(); i ++) {
                TransactionAttempt ta = tr.attempts().get(i);

                TxnServer.AttemptStates attemptState;
                switch (ta.finalState()) {
                    case ABORTED:
                        attemptState = TxnServer.AttemptStates.ABORTED;
                        break;
                    case COMMITTED:
                        attemptState = TxnServer.AttemptStates.COMMITTED;
                        break;
                    case NOT_STARTED:
                        attemptState = TxnServer.AttemptStates.NOT_STARTED;
                        break;
                    case COMPLETED:
                        attemptState = TxnServer.AttemptStates.COMPLETED;
                        break;
                    case PENDING:
                        attemptState = TxnServer.AttemptStates.PENDING;
                        break;
                    case ROLLED_BACK:
                        attemptState = TxnServer.AttemptStates.ROLLED_BACK;
                        break;
                    default:
                        throw new IllegalStateException("Bad state " + ta.finalState());
                }

                response.addAttempts(TxnServer.TransactionAttempt.newBuilder()
                    .setState(attemptState)
                    .setAttemptId(ta.attemptId())
                    .build());
            }

            // Force that log redaction has been enabled
            LogRedaction.setRedactionLevel(RedactionLevel.PARTIAL);

            tr.log().logs().forEach(l ->
                response.addLog(l.toString()));
        });

        return response.build();
    }
}