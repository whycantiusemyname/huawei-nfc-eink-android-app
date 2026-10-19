package org.nfcresearch.qpolllab;
public final class NciDiscoveryTest {
    private static void check(boolean value){if(!value)throw new AssertionError();}
    public static void main(String[] args){
        check(NciDiscovery.hasQPoll("210303017101"));
        check(NciDiscovery.hasQPoll("21030703000171010101"));
        check(!NciDiscovery.hasQPoll("210309040001010102010601"));
        check(!NciDiscovery.hasQPoll("210303010071"));
        check(!NciDiscovery.hasQPoll("2103030171"));
        check(!NciDiscovery.hasQPoll("210303027101"));
        check(!NciDiscovery.hasQPoll("bad"));
        check(NciDiscovery.hasQPoll("21030703710180018201"));
        check(!NciDiscovery.hasQPoll("2103030171ZZ"));
        NciDiscovery.Tracker tracker=new NciDiscovery.Tracker();
        check(tracker.received("41030100")==null);
        tracker.transmitted("21030703710180018201");
        check(tracker.received("41060100")==null);
        check(tracker.received("41030100")==0);
        check(tracker.received("41030100")==null);
        tracker.transmitted("210303017101");
        check(tracker.received("41030105")==5);
        // A later successful A discovery must not turn a rejected Q request into success.
        tracker.transmitted("210303010001");
        check(tracker.received("41030100")==null);
        tracker.transmitted("210303017101");
        tracker.transmitted("210303010001");
        check(tracker.received("41030100")==null);
        tracker.transmitted("210303017101");
        check(tracker.received("4103010")==null);
        check(tracker.received("41030100")==null);
        check(NciDiscovery.statusName(5).contains("SYNTAX_ERROR"));
        System.out.println("NCI Q-Poll discovery tests passed");
    }
}
