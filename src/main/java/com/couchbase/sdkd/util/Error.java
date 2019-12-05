/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.couchbase.sdkd.util;

/**
 *
 * @author mnunberg
 */
public class Error {
    public static final int SUBSYSf_UNKNOWN = 0x1;
    public static final int SUBSYSf_CLUSTER = 0x2;
    public static final int SUBSYSf_CLIENT = 0x4;
    public static final int SUBSYSf_MEMD = 0x8;
    public static final int SUBSYSf_NETWORK = 0x10;
    public static final int SUBSYSf_SDKD = 0x20;
    public static final int SUBSYSf_KVOPS = 0x40;
    public static final int KVOPS_EMATCH = 0x200;
    public static final int SDKD_EINVAL = 0x200;
    public static final int SDKD_ENOIMPL = 0x300;
    public static final int SDKD_ENOHANDLE = 0x400;
    public static final int SDKD_ENODS = 0x500;
    public static final int SDKD_ENOREQ = 0x600;
    public static final int ERROR_GENERIC = 0x100;
    public static final int CLIENT_ETMO = 0x200;
    public static final int CLIENT_WOULDHANG = 0x300;
    public static final int CLIENT_EBACKP = 0x400;
    public static final int CLIENT_EREQCAN = 0x500;
    public static final int CLUSTER_EAUTH = 0x200;
    public static final int CLUSTER_ENOENT = 0x300;
    public static final int MEMD_ENOENT = 0x200;
    public static final int MEMD_ECAS = 0x300;
    public static final int MEMD_ESET = 0x400;
    public static final int MEMD_EVBUCKET = 0x500;
    
    public static final int SUBSYSf_VIEWS = 0x41;
    public static final int SUBSYSf_N1QL = 0x42;
    
    public static final int CLIENT_ESCHED = 0x300;
    public static final int VIEWS_MALFORMED = 0x200;
    public static final int VIEWS_MISMATCH = 0x300;
    public static final int VIEWS_HTTP_ERROR = 0x400;
    public static final int VIEWS_HTTP_3XX = 0x500;
    public static final int VIEWS_HTTP_4XX = 0x600;
    public static final int VIEWS_HTTP_5XX = 0x700;
    public static final int VIEWS_EXC_UNEXPECTED = 0x800;
    public static final int VIEWS_NOT_FOUND = 0x900;
    
    public static final int N1QL_EXC_UNEXPECTED = 0x100;
    public static final int N1QL_ENOENT = 0x200;
    
    public int major = 0;
    public int minor = 0;
    public String description = "";
    
    public Error(int pmajor, int pminor, String pdesc) {
        major = pmajor;
        minor = pminor;
        if (pdesc != null) {
            description = pdesc;
        }
    }
    
    public Error(int combined, String pdesc) {
        splitError(combined);
        if (pdesc.length() < 1) {
            description = pdesc;
        }
    }
    
    public Error(int combined) {
        splitError(combined);
    }
    
    private void splitError(int combined) {
        major = combined & 0xff;
        minor = combined & ~0xff;
    }

    
    public void setMessage(String msg) {
        description = msg;
    }
    
    public String getMessage() {
        if (description != null) {
            return description;
        }
        return "";
    }
    
    public static Error newInvalid(String reason) {
        return new Error(SUBSYSf_SDKD,
                SDKD_EINVAL,
                reason);
    }
    
    @Override
    public String toString() {
        return String.format("Code: %d: [%x/%x], Description: %s",
                major|minor,
                major, minor,
                description);
    }
    
    public int getCode() {
        return major | minor;
    }
}
