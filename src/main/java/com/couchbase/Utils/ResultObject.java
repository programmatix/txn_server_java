package com.couchbase.Utils;


import com.couchbase.transactions.log.LogDefer;
import com.couchbase.transactions.support.AttemptStates;

import java.util.ArrayList;
import java.util.List;

public class ResultObject {
    public int mutationTokensSize;
    public int txnAttemptsSize;
    public String attemptFinalState;
    public  boolean atrCollectionPresent;
    public boolean atrIdPresent;
    public String exceptionName;
    public List<String> logs= new ArrayList<String>();

    public ResultObject(int mutationTokensSize, int txnAttemptsSize, AttemptStates attemptFinalState, boolean atrCollectionPresent, boolean atrIdPresent, String exceptionName , List<LogDefer> logs){
        this.mutationTokensSize=mutationTokensSize;
        this.txnAttemptsSize=txnAttemptsSize;
        this.attemptFinalState=attemptFinalState.name();
        this.atrCollectionPresent=atrCollectionPresent;
        this.atrIdPresent=atrIdPresent;
        this.exceptionName=exceptionName;

        logs.forEach(l -> {
            if(l!=null){
                this.logs.add(l.toString());
            }
        });
    }
}
