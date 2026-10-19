package org.nfcresearch.qpolllab;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.io.File;

/** Only select native Q when the ROM actually exposes its vendor reader flag. */
public final class QPollSupport {
    private QPollSupport() { }

    public static int detectNativeQFlag() {
        String name="com.nxp.nfc.NxpNfcAdapter";
        try { return readerFlag(Class.forName(name)); }
        catch (ReflectiveOperationException | LinkageError | SecurityException ignored) { }
        for(String path:new String[]{"/system_ext/framework/nfcvendorlib.jar","/system/framework/nfcvendorlib.jar"}) {
            if(!new File(path).isFile())continue;
            try {
                ClassLoader loader=(ClassLoader)Class.forName("dalvik.system.PathClassLoader")
                        .getConstructor(String.class,ClassLoader.class).newInstance(path,QPollSupport.class.getClassLoader());
                return readerFlag(Class.forName(name,false,loader));
            } catch (ReflectiveOperationException | LinkageError | SecurityException ignored) { }
        }
        return 0;
    }

    public static int readerFlag(Class<?> vendorAdapter) throws ReflectiveOperationException {
        Field field = vendorAdapter.getField("FLAG_READER_NFC_Q");
        if (field.getType() != int.class || !Modifier.isStatic(field.getModifiers()))
            throw new NoSuchFieldException("FLAG_READER_NFC_Q must be a static int");
        int value = field.getInt(null);
        // Reject overlap with standard technologies, skip-NDEF, sound and option flags.
        if (value != 0x20) throw new NoSuchFieldException("Unrecognized native Q reader flag: " + value);
        return value;
    }

    public static int flags(boolean standard, int nativeQFlag) {
        return 0x80 | (standard || nativeQFlag == 0 ? 0x01 : nativeQFlag);
    }
}
