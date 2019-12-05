/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.couchbase.sdkd.util;

import java.lang.reflect.Field;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;

/**
 *
 * @author mnunberg
 */
public enum Command {
    __UNKNOWN,
    TXN_BASIC_TEST,
    TXN_DATA_UPDATE,
    TXN_DATA_DELETE,
    TXN_CREATE,
    TXN_LOAD_DATA,
    TXN_DATA_LOAD;


    private final static Map<String,Command> commandMap = new HashMap<>();

    static {
        for (Field f : Command.class.getDeclaredFields()) {
            if (!f.isEnumConstant()) {
                continue;
            }
            try {
                commandMap.put(f.getName(), (Command) f.get(f.getName()));
            } catch (Exception exc) {
                SdkdConfig.sdkdLogger().log(Level.SEVERE, "..", exc);
            }
        }
    }

    private final static Map<Command,String> reverseCommandMap = new EnumMap<>(Command.class);

    static {
        Iterator<Entry<String,Command>> iter = commandMap.entrySet().iterator();
        while (iter.hasNext()) {
            Entry<String,Command> e = iter.next();
            reverseCommandMap.put(e.getValue(), e.getKey());
        }
    }

    public static Command fromString (String s) throws ProtocolException {
        if (!commandMap.containsKey(s)) {
            return __UNKNOWN;
        }
        return commandMap.get(s);
    }

    public static String fromCommand(Command c) {
        if (reverseCommandMap.containsKey(c)) {
            return reverseCommandMap.get(c);
        }
        return null;
    }
}
