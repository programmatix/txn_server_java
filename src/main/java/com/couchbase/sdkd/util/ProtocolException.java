/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.couchbase.sdkd.util;
/**
 *
 * @author mnunberg
 */
public class ProtocolException extends Exception {
    private String message;
    public ReqJson json;
    public Error err;
    
    public ProtocolException(String reason) {
        message = reason;
    }
    
    public ProtocolException(String reason, ReqJson jobj) {
        message = reason;
        json = jobj;
    }
    
    public ProtocolException(ReqJson jobj) {
        json = jobj;
        message = "Bad message";
    }
    
    public ProtocolException(Error e) {
        err = e;
        if (e.description == null) {
            message = "SDKD Protocol Error";
        }
    }
    
    public ProtocolException(Error e, ReqJson jobj) {
        err = e;
        json = jobj;
    }
    
    public ProtocolException() { }
    
    public int getCode() {
        if (err != null) {
            return err.major | err.minor;
        }
        return Error.SDKD_EINVAL | Error.SUBSYSf_SDKD;
    }
    
    public String getDescription() {
        if (message != null) {
            return message;
        }
        if (err != null && err.description != null &&
                err.description.length() > 0) {
            return err.description;
        }
        return "";
    }
    
    @Override
    public String toString() {
        String s = String.format("Error: %d (%s)",
                getCode(),
                getDescription());
        return s;
    }
}