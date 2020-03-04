package com.couchbase.Transactions;
import com.couchbase.client.java.Collection;
import com.couchbase.client.java.json.JsonObject;
import com.couchbase.grpc.protocol.TxnServer;
import com.couchbase.transactions.AttemptContext;

/**
 * Inserts a doc, in a running {@link ResumableTransaction}
 */
public class ResumableTransactionInsert implements ResumableTransactionCommand {
    private final Collection collection;
    private final String id;
    private final String contentJson;
    private final TxnServer.ExpectedResult expectedResult;

    public ResumableTransactionInsert(Collection collection,
                                      String id,
                                      String contentJson,
                                      TxnServer.ExpectedResult expectedResult) {
        this.collection = collection;
        this.id = id;
        this.contentJson = contentJson;
        this.expectedResult = expectedResult;
    }

    @Override
    public void execute(AttemptContext ctx) {
        JsonObject content = JsonObject.fromJson(contentJson);
        ctx.insert(collection, id, content);
    }

    @Override
    public boolean isSuccessExpected() {
        return expectedResult == TxnServer.ExpectedResult.SUCCESS;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Insert{");
        sb.append("id=");
        sb.append(id);
        sb.append(",expected=");
        sb.append(expectedResult);
        sb.append("}");
        return sb.toString();
    }
}
