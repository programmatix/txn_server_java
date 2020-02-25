package com.couchbase.Tests.Transactions.Utils;

import com.couchbase.client.java.Collection;
import com.couchbase.transactions.AttemptContext;
import com.couchbase.transactions.TransactionResult;

/**
 * Inserts a doc, in a running {@link ResumableTransaction}
 */
public class ResumableTransactionDelete implements ResumableTransactionCommand {
    private final Collection collection;
    private final String id;
    private String name="delete";

    public ResumableTransactionDelete(Collection collection,
                                      String id) {
        this.collection = collection;
        this.id = id;
    }

    @Override
    public void execute(AttemptContext ctx) {
        ctx.remove(ctx.get(collection,id));
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Delete{");
        sb.append("id=");
        sb.append(id);
        sb.append("}");
        return sb.toString();
    }

    @Override
    public boolean assertions(TransactionResult transactionResult, Exception e){ return true; }

    @Override
    public  String getname(){
        return name;
    }

    @Override
    public boolean assertions(){
        return true;
    }
}