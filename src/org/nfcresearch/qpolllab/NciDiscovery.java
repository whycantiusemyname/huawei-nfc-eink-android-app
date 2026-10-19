package org.nfcresearch.qpolllab;

public final class NciDiscovery {
    /** Match only the response to the latest complete RF_DISCOVER command. */
    public static final class Tracker {
        private boolean pendingQ;
        public synchronized void transmitted(String packet) {
            if(packet.startsWith("2103"))pendingQ=hasQPoll(packet);
        }
        public synchronized Integer received(String packet) {
            if(!packet.startsWith("4103"))return null;
            boolean expected=pendingQ;pendingQ=false;
            if(!expected||packet.length()!=8||!packet.startsWith("410301"))return null;
            try { return Integer.parseInt(packet.substring(6,8),16); }
            catch(NumberFormatException invalid) { return null; }
        }
    }
    public static String statusName(int status) {
        switch(status){
            case 0:return "STATUS_OK";
            case 1:return "STATUS_REJECTED";
            case 5:return "STATUS_SYNTAX_ERROR（语法错误）";
            case 6:return "STATUS_SEMANTIC_ERROR";
            case 9:return "STATUS_INVALID_PARAM";
            default:return "NCI / 厂商状态";
        }
    }
    public static boolean hasQPoll(String packet){
        try{
            if(!packet.startsWith("2103")||packet.length()<8)return false;
            int length=Integer.parseInt(packet.substring(4,6),16),count=Integer.parseInt(packet.substring(6,8),16);
            if(count==0||length!=1+2*count||packet.length()!=6+length*2||!packet.matches("[0-9A-Fa-f]+"))return false;
            for(int i=0;i<count;i++)if(Integer.parseInt(packet.substring(8+4*i,10+4*i),16)==0x71)return true;
        }catch(RuntimeException ignored){}
        return false;
    }
}
