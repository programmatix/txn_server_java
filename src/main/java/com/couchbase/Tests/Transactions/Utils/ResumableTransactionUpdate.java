package com.couchbase.Tests.Transactions.Utils;

import com.couchbase.client.java.Collection;
import com.couchbase.client.java.json.JsonObject;
import com.couchbase.transactions.AttemptContext;
import com.couchbase.transactions.TransactionResult;

/**
 * Inserts a doc, in a running {@link ResumableTransaction}
 */
public class ResumableTransactionUpdate implements ResumableTransactionCommand {
    private final Collection collection;
    private final String id;
    private final String contentJson;
    private String name="update";

    public ResumableTransactionUpdate(Collection collection,
                                      String id,
                                      String contentJson) {
        this.collection = collection;
        this.id = id;
        this.contentJson = contentJson;
    }


    @Override
    public void execute(AttemptContext ctx) {
        JsonObject content = JsonObject.fromJson(contentJson);
        ctx.replace(ctx.get(collection,id),content);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Update{");
        sb.append("id=");
        sb.append(id);
        sb.append("with new content = ");
        sb.append(contentJson);
        sb.append("}");
        return sb.toString();
    }

    @Override
    public boolean assertions(TransactionResult transactionResult,Exception e){ return true;}

    @Override
    public  String getname(){
        return name;
    }

    @Override
    public boolean assertions(){
        return true;
    }
}
