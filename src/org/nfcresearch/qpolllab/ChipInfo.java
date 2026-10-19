package org.nfcresearch.qpolllab;
import java.io.*;
import java.util.regex.*;

public final class ChipInfo {
    public static String inspect(){
        // ROMs ship firmware for several controllers; installed files are not chip IDs.
        for(String chip:new String[]{"SN220","SN300","SN100"}){
            String path="/data/vendor/nfc/libnfc-mtp-"+chip+".conf";
            if(new File(path).isFile())return "NXP "+chip+" 配置线索\n依据："+path+
                    "\n配置文件不等于控制器 ID；可能存在多个型号配置，实际芯片需结合运行信息核实";
        }
        for(String path:new String[]{"/odm/etc/libnfc-nxp.conf","/vendor/etc/libnfc-nxp.conf"}){
            try(BufferedReader in=new BufferedReader(new FileReader(path))){String line;while((line=in.readLine())!=null){
                if(line.trim().startsWith("#"))continue;
                Matcher match=Pattern.compile("^\\s*NXP_FW_NAME\\s*=\\s*\"([^\"]+)\"").matcher(line);
                if(match.find()){
                    String firmware=match.group(1);Matcher chip=Pattern.compile("lib([a-zA-Z0-9]+)_fw\\.so").matcher(firmware);
                    return "NXP 固件配置 "+(chip.matches()?chip.group(1).toUpperCase(java.util.Locale.ROOT):firmware)+"\n依据："+path+" / "+firmware+"\n共享固件库名称，不能单独确定芯片型号；非芯片 ID 实读";
                }
            }}catch(IOException ignored){}
        }
        return "未能从当前可读配置识别，请查看系统日志；不据此判定不支持 NFC 或 Q-Poll。";
    }
}
