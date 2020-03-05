package com.couchbase.Transactions;
import com.couchbase.client.java.Collection;
import com.couchbase.grpc.protocol.TxnServer;
import com.couchbase.transactions.AttemptContext;

/**
 * Inserts a doc, in a running {@link ResumableTransaction}
 */
public class ResumableTransactionDelete implements ResumableTransactionCommand {
    private final Collection collection;
    private final String id;
    private final TxnServer.ExpectedResult expectedResult;

    public ResumableTransactionDelete(Collection collection,
                                      String id,
                                      TxnServer.ExpectedResult expectedResult) {
        this.collection = collection;
        this.id = id;
        this.expectedResult = expectedResult;
    }

    @Override
    public void execute(AttemptContext ctx) {
        ctx.remove(ctx.get(collection,id));
    }

    @Override
    public boolean isSuccessExpected() {
        return expectedResult == TxnServer.ExpectedResult.EXPECT_SUCCESS;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Delete{");
        sb.append("id=");
        sb.append(id);
        sb.append(",expected=");
        sb.append(expectedResult);
        sb.append("}");
        return sb.toString();
    }
}
