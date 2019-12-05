/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.couchbase.sdkd.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 *
 * @author mnunberg
 *
 * This class is a wrapper which does strict protocol verification.
 * The @c get() methods assume a valid key of the type specified and raise a 
 * ProtocolException otherwise.
 * The @c getAny() methods will always return a valid type. If the item does
 * not exist it will return a sane default (empty string or 0 for ints). If the
 * item does exist but is present as an inconvertible type (i.e. an integer
 * value where a string is expected), it will throw a ProtocolException.
 */
public class ReqJson {
    private JsonObject inner;

    public ReqJson(JsonElement elem) throws ProtocolException {
        try {
            inner = elem.getAsJsonObject();
        } catch (IllegalStateException exc) {
            throw new ProtocolException(Error.newInvalid(exc.getMessage()));
        }
    }
    
    public ReqJson() {
        inner = new JsonObject();
    }

    /**
     *
     * @param s The object key
     * @return The Value
     * @throws ProtocolException if the key was not found
     */
    public JsonElement get(String s) throws ProtocolException {
        JsonElement ret = inner.get(s);
        if (s == null) {
            throw new ProtocolException(Error.newInvalid(
                    "Missing required field " + s));
        }
        return ret;
    }

    private JsonElement get(String s, boolean required) {
        try {
            return get(s);
        } catch (ProtocolException exc) {
            return null;
        }
    }

    public int getInt(String s) throws ProtocolException {
        try {
            return get(s).getAsInt();
        } catch (ClassCastException exc) {
            throw new ProtocolException(
                    Error.newInvalid(exc.toString()));
        }
    }
    
    public int getAnyInt(String s) throws ProtocolException {
        JsonElement e = get(s, false);
        if (e == null || e.isJsonNull()) {
            return 0;
        }

        try {
            return e.getAsInt();
        } catch (ClassCastException exc) {
            throw new ProtocolException(Error.newInvalid(exc.toString()));
        }
    }

    public int getPositiveInt(String s) throws ProtocolException {
        try {
            int ret = get(s).getAsInt();
            if (ret < 1) {
                throw new IllegalArgumentException("Must be greater than zero");
            }
            return ret;
        } catch (IllegalArgumentException exc) {
            throw new ProtocolException(Error.newInvalid(s + exc));
        }
    }

    public boolean getBoolean(String s) throws ProtocolException {
        JsonElement elem = get(s);
        try {
            try {
                return elem.getAsBoolean();
            } catch (ClassCastException exc) {
                try {
                    return elem.getAsInt() != 0;
                } catch (IllegalArgumentException e2) {
                    return elem.getAsString().isEmpty();
                }
            }
        } catch (Exception exc) {
            throw new ProtocolException(Error.newInvalid(exc.toString()));
        }
    }
    
    public boolean getAnyBoolean(String s) throws ProtocolException {
        JsonElement e = get(s, false);
        if (e == null) {
            return false;
        }
        try {
            return e.getAsBoolean();
        } catch (ClassCastException exc) {
            return getInt(s) !=0;
        }
    }

    public String getString(String s) throws ProtocolException {
        try {
            JsonElement elem = get(s);
            if (elem == null) {
                throw new ProtocolException(Error.newInvalid(
                        "Couldn't find key " + s));
            }
            return elem.getAsString();
        } catch (ClassCastException exc) {
            throw new ProtocolException(Error.newInvalid(
                    "Couldn't convert value of " + s + " to string." + exc));
        }
    }

    /**
     * Gets any string
     *
     * @param s
     * @return a string, "" if nothing was found.
     *
     */
    public String getAnyString(String s) {
        JsonElement elem = get(s, false);
        if (elem == null) {
            return "";
        }
        
        String ret = elem.toString();
        if (ret.isEmpty()) {
            return ret;
        }
        
        return ret.substring(1, ret.length() - 1);
    }
    
    public String getNonEmptyString(String s) throws ProtocolException {
        String ret = getString(s);
        if (ret.length() == 0) {
            throw new ProtocolException(Error.newInvalid(
                    "Value for key " + s + " must not be empty"));
        }
        return ret;
    }

    public JsonObject getInner() {
        return inner;
    }

    public ReqJson getObject(String s) throws ProtocolException {
        try {
            return new ReqJson(get(s));
        } catch (ClassCastException exc) {
            throw new ProtocolException(Error.newInvalid("Value for " + s
                    + " is not a valid JSON object"));
        }
    }
    
    public boolean containsKey(String s) {
        return inner.has(s);
    }
}
