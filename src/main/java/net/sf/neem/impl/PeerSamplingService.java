package net.sf.neem.impl;

import net.sf.neem.MulticastChannel;

import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.nio.channels.ClosedChannelException;
import java.util.HashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Implements the CYCLON PSS
 */
public class PeerSamplingService {

    private final int h;
    private final int s;
    private final int c;
    private final HashMap<String, InetSocketAddress> view;
    private final ScheduledExecutorService scheduler;
    private final Runnable periodicShuffling;
    private final ScheduledFuture<?> periodicShufflingFuture = null;
    private final MulticastChannel neem;

    /**
     *
     * @param view the initial view
     * @param h TODO
     * @param s TODO
     * @param c TODO
     */
    public PeerSamplingService(final MulticastChannel neem, HashMap<String, InetSocketAddress> view, int h, int s, int c) {
        this.neem = neem;
        this.view = view;
        this.h = h;
        this.s = s;
        this.c = c;
        this.scheduler = Executors.newScheduledThreadPool(1);
        this.periodicShuffling = new Runnable() {

            public void run() {
                synchronized (this) {
                    InetSocketAddress partner = selectPartner();
                    HashMap<String, InetSocketAddress> toSend = selectToSend();
                    //TODO send to partner our view
                    try {
                        neem.write(null);
                    } catch (ClosedChannelException e) {
                        e.printStackTrace();
                    }
                    //TODO wait for answer and call
                    // selectToKeep(result)
                }
            }
        };
        //TODO check view.size < c
        //TODO check H+S < C/2
        /*
                for i =1, 4 do
            pss_activeThread()
            events.sleep(pss_active_thread_period / 4)
        end
        events.periodic(pss_activeThread, pss_active_thread_period)
         */
    }

    /**
     * Called by distant peers that want to exchange with this peer
     * @param o
     */
    public synchronized void receive(Object o) {

    }


    /**
     * Finds a partner with which to exchange views
     * @return the partner with which we want to exchange
     */
    private InetSocketAddress selectPartner() {
        return null;
    }

    /**
     * Selects which peers from our view to send
     * @return an array of peers to send
     */
    private HashMap<String, InetSocketAddress> selectToSend() {
        return null;
    }

    /**
     * Select peers to keep from the distant peer
     *
     * @param distantView view from the distant peer
     * @return
     */
    private HashMap<String, InetSocketAddress> selectToKeep(HashMap<String, InetSocketAddress> distantView) {
        return null;
    }
}
