package com.couchbase.Tests.Transactions.Utils;

import com.couchbase.client.java.Collection;
import com.couchbase.client.java.json.JsonObject;
import com.couchbase.transactions.AttemptContext;

/**
 * Inserts a doc, in a running {@link ResumableTransaction}
 */
public class ResumableTransactionInsert implements ResumableTransactionCommand {
    private final Collection collection;
    private final String id;
    private final String contentJson;

    public ResumableTransactionInsert(Collection collection,
                                      String id,
                                      String contentJson) {
        this.collection = collection;
        this.id = id;
        this.contentJson = contentJson;
    }

    @Override
    public void execute(AttemptContext ctx) {
        JsonObject content = JsonObject.fromJson(contentJson);

        ctx.insert(collection, id, content);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Insert{");
        sb.append("id=");
        sb.append(id);
        sb.append("}");
        return sb.toString();
    }
}
