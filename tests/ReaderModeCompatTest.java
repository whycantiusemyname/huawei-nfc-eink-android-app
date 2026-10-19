package org.nfcresearch.qpolllab;

public final class ReaderModeCompatTest {
    public static class Four { public void setReaderMode(Object token, Runnable cb, int flags, Number extras) { } }
    public static class Five { public void setReaderMode(Object token, Runnable cb, int flags, Number extras, String pkg) { } }
    public static class Both extends Four { public void setReaderMode(Object token, Runnable cb, int flags, Number extras, String pkg) { } }
    public static class NativeQ { public static final int FLAG_READER_NFC_Q=0x20; }
    public static class WrongFlag { public static final int FLAG_READER_NFC_Q=0x01; }
    public static void main(String[] args) throws Exception {
        for(Class<?> type:new Class<?>[]{Four.class,Five.class,Both.class}){
            int count=ReaderModeCompat.resolve(type,Object.class,Runnable.class,Number.class).getParameterTypes().length;
            if(count!=(type==Five.class?5:4))throw new AssertionError(type);
        }
        try { ReaderModeCompat.resolve(Object.class,Object.class,Runnable.class,Number.class);throw new AssertionError(); }
        catch(NoSuchMethodException expected) { }
        if(QPollSupport.readerFlag(NativeQ.class)!=0x20)throw new AssertionError();
        try { QPollSupport.readerFlag(WrongFlag.class);throw new AssertionError(); }
        catch(NoSuchFieldException expected) { }
        if(QPollSupport.flags(false,0x20)!=0xa0||QPollSupport.flags(true,0x20)!=0x81||QPollSupport.flags(false,0)!=0x81)
            throw new AssertionError("Native Q, standard A and legacy A must select different routes");
        System.out.println("ReaderMode ROM compatibility and Q route tests passed");
    }
}
