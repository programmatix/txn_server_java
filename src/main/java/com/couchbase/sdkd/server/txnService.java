package com.couchbase.sdkd.server;


import com.couchbase.grpc.protocol.TxnServer;
import com.couchbase.grpc.protocol.TxnServer.APIResponse;
import com.couchbase.grpc.protocol.txnGrpc;
import com.couchbase.sdkd.cbclient.*;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import java.io.IOException;

public class txnService extends txnGrpc.txnImplBase{
    Handle connection;
    String bucketname = null;
    boolean txnExists=false;

    @Override
    public void createConn(TxnServer.conn_info request, StreamObserver<APIResponse> responseObserver) {
        APIResponse.Builder  response = APIResponse.getDefaultInstance().newBuilderForType();

        if (connection != null) {
            System.out.println("Connection already exists");
            response.setAPISuccessStatus(false).setAPIStatusInfo("Connection already exists");
        }else{
            try{
                connection = new Handle(request);
                bucketname = request.getHandleBucket();
            } catch (Exception e){
                System.out.println(e);
            }
            if (connection == null) {
                System.out.println("Unable to create Connection");
                response.setAPISuccessStatus(false).setAPIStatusInfo("Unable to create Connection");
            }else{
                System.out.println("Created Connection successfully");
                response.setAPISuccessStatus(true).setAPIStatusInfo("Created Connection successfully");
            }
        }

        responseObserver.onNext(response.build());
        responseObserver.onCompleted();
    }

    @Override
    public void createTxn(TxnServer.txn_req request, StreamObserver<APIResponse> responseObserver) {
        APIResponse.Builder  response = APIResponse.getDefaultInstance().newBuilderForType();

        try {
            CommandContext ctx = new TransactionCommandContext(request);
            if(connection!=null){
                if (txnExists) {
                    System.out.println("txn already exists");
                    response.setAPISuccessStatus(false).setAPIStatusInfo("txn already exists");
                }else{
                    txnExists = ctx.executeCommand(connection,request.getCommand());
                    if (txnExists) {
                        System.out.println("txn is created");
                        response.setAPISuccessStatus(true).setAPIStatusInfo("Created txn successfully");
                    }else{
                        System.out.println("txn is not create");
                        response.setAPISuccessStatus(false).setAPIStatusInfo("Unable to create txn");
                    }
                }
            }else{
                System.out.println("Connection not established between server and cb server");
                response.setAPISuccessStatus(false).setAPIStatusInfo("Unable to create txn since the txn_framework did not yet establish connection with couchbase server");
            }

            responseObserver.onNext(response.build());
            responseObserver.onCompleted();
        } catch (Exception e){
            System.out.println("Exception creating Transaction:"+e);
        }
    }


    public void executeTxn(TxnServer.txn_req request, StreamObserver<APIResponse> responseObserver) {
        APIResponse.Builder  response = APIResponse.getDefaultInstance().newBuilderForType();
        boolean txn_resp=false;

        try {
            CommandContext ctx = new TransactionCommandContext(request);
            if(txnExists){
                txn_resp = ctx.executeCommand(connection,request.getCommand());
            }else{
                System.out.println("Txn does not exist. Hence unable to load data");
                response.setAPISuccessStatus(false).setAPIStatusInfo(request.getCommand()+" has failed since txn was not created earlier");
            }

            if (txn_resp) {
                System.out.println(request.getCommand()+" completed successfully");
                response.setAPISuccessStatus(true).setAPIStatusInfo(request.getCommand()+" completed successfully");
            }else{
                System.out.println(request.getCommand()+" has failed");
                response.setAPISuccessStatus(false).setAPIStatusInfo(request.getCommand()+" has failed while executing txn");
            }

            responseObserver.onNext(response.build());
            responseObserver.onCompleted();
        } catch (Exception e){
            System.out.println("Exception creating Transaction:"+e);
        }
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        Server server = ServerBuilder.forPort(8050).addService(new txnService()).build();
        server.start();
        System.out.println("Server Started at 8050 ");
        server.awaitTermination();
    }


}
