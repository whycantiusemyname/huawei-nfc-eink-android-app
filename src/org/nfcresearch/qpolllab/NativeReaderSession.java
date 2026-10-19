package org.nfcresearch.qpolllab;

import android.app.Activity;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.os.Bundle;
import android.os.SystemClock;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileLock;
import java.util.Arrays;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** A foreground Activity owns native Q polling; root UIDs may be silently ignored by ROMs. */
public final class NativeReaderSession {
    public interface Events { void emit(String kind,String message); }
    private final Activity activity;
    private final NfcAdapter adapter;
    private final File stop;
    private final Events events;
    private final String mode;
    private final int seconds, flags;
    private boolean readerTouched;

    private NativeReaderSession(Activity activity,String mode,int seconds,File stop,int nativeFlag,Events events) {
        this.activity=activity;this.mode=mode;this.seconds=seconds;this.stop=stop;this.events=events;
        this.adapter=NfcAdapter.getDefaultAdapter(activity);
        this.flags=QPollSupport.flags(mode.equals("standard"),nativeFlag);
    }
    public static void run(Activity activity,String mode,int seconds,File job,File stop,File lock,int nativeFlag,Events events) {
        NativeReaderSession session=new NativeReaderSession(activity,mode,seconds,stop,nativeFlag,events);
        session.execute(job,lock);
    }
    private void ui(Runnable action) throws Exception {
        FutureTask<Void> task=new FutureTask<>(action,null);
        activity.runOnUiThread(task);
        try { task.get(10,TimeUnit.SECONDS); }
        catch(Exception e) { task.cancel(false);throw e; }
    }
    private Tag discover() throws Exception {
        AtomicReference<Tag> found=new AtomicReference<>();
        Bundle extras=new Bundle();
        if(mode.equals("identity")||mode.equals("refresh"))extras.putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY,5000);
        if(stop.exists())return null;
        readerTouched=true;
        ui(()->{
            if(stop.exists()||activity.isFinishing()||activity.isDestroyed())throw new IllegalStateException("页面已离开");
            adapter.enableReaderMode(activity,tag->found.compareAndSet(null,tag),flags,extras);
        });
        events.emit("START",mode+":"+seconds);
        long deadline=SystemClock.elapsedRealtime()+seconds*1000L;
        while(!stop.exists()&&found.get()==null&&SystemClock.elapsedRealtime()<deadline)SystemClock.sleep(50);
        if(stop.exists())throw new InterruptedException("用户停止；未继续协议通信");
        Tag tag=found.get();
        if(tag!=null)events.emit("TAG","收到标签 UID="+PanelProtocol.toHex(tag.getId())+"；技术="+Arrays.toString(tag.getTechList()));
        return tag;
    }
    private void release() throws Exception {
        ui(()->adapter.disableReaderMode(activity));readerTouched=false;
    }
    private void execute(File job,File lockPath) {
        try(RandomAccessFile file=new RandomAccessFile(lockPath,"rw");FileLock lock=file.getChannel().tryLock()) {
            if(lock==null)throw new IllegalStateException("另一轮测试正在运行");
            // Keep release inside the lock, so another session cannot lose its ReaderMode.
            try { executeLocked(job); }
            finally {
                if(readerTouched){
                    try { release();events.emit("READER_OFF","前台 ReaderMode 已释放");events.emit("RESTORED","本轮 ReaderMode 已释放，普通寻卡由系统管理"); }
                    catch(Exception e){events.emit("RESTORE_FAILED","释放前台 ReaderMode 失败："+e);}
                }
            }
        }catch(InterruptedException e){events.emit("CANCELLED",e.getMessage());}
        catch(Exception e){events.emit("ERROR",e.getClass().getSimpleName()+": "+e.getMessage());}
        finally { events.emit("END","本轮结束"); }
    }
    private void executeLocked(File job) throws Exception {
        if(adapter==null||!adapter.isEnabled())throw new IllegalStateException("请先在系统设置中开启 NFC");
        if(seconds<1||seconds>60||!Arrays.asList("standard","qtag","identity","refresh").contains(mode))
            throw new IllegalArgumentException("Invalid native reader session");
        events.emit("BACKEND","前台 Activity ReaderMode，flags=0x"+Integer.toHexString(flags)+"；无需 Shizuku / Root");
        if(!mode.equals("standard"))events.emit("CAPABILITY","使用 ROM 原生 Q 标志；接口返回不代表控制器接受。此路径不读取 HAL 日志，需标签回调或独立抓包验证。");
        Tag first=discover();
        if(first==null){events.emit("RESULT","本轮超时，未收到标签；不能据此认定硬件不支持");return;}
        if(mode.equals("standard")||mode.equals("qtag")){events.emit("RESULT","已收到标签；只显示元信息，未发送 APDU");return;}
        byte[] uid=first.getId().clone();
        Tag[] current={first};int[] attempt={0};
        ConnectionRetry.run(()->{
            if(stop.exists())throw new InterruptedException("已停止，未开始协议通信");
            if(!Arrays.equals(uid,current[0].getId()))throw new IOException("重新寻到不同 UID 的标签，停止；尚未发送 APDU");
            events.emit("CONNECT_ATTEMPT",(++attempt[0])+" / 3");
            PanelSession.run(current[0],mode,job,stop,events::emit);
        },()->{
            if(stop.exists())throw new InterruptedException("已停止重新寻卡");
            events.emit("RECONNECT","尚未发送 APDU；重新寻卡以获取新标签");
            release();SystemClock.sleep(200);current[0]=discover();
            if(current[0]==null)throw new IOException("重新寻卡超时；尚未发送 APDU");
        });
        if(mode.equals("identity"))events.emit("RESULT","D1 屏幕规格与原厂配置校验通过，未写图像");
    }
}
