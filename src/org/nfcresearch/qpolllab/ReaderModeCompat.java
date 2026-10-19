package org.nfcresearch.qpolllab;

import java.lang.reflect.Method;

/** Resolve ROM differences without retrying a Binder call that already failed. */
public final class ReaderModeCompat {
    private ReaderModeCompat() { }

    public static Method resolve(Class<?> adapter, Class<?> binder, Class<?> callback,
                                 Class<?> bundle) throws NoSuchMethodException {
        try {
            return adapter.getMethod("setReaderMode", binder, callback, int.class, bundle);
        } catch (NoSuchMethodException absent) {
            return adapter.getMethod("setReaderMode", binder, callback, int.class, bundle, String.class);
        }
    }
}
