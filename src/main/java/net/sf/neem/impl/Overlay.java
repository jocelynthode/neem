/*
 * NeEM - Network-friendly Epidemic Multicast
 * Copyright (c) 2005-2007, University of Minho
 * All rights reserved.
 *
 * Contributors:
 *  - Pedro Santos <psantos@gmail.com>
 *  - Jose Orlando Pereira <jop@di.uminho.pt>
 * 
 * Partially funded by FCT, project P-SON (POSC/EIA/60941/2004).
 * See http://pson.lsd.di.uminho.pt/ for more information.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 * 
 *  - Redistributions of source code must retain the above copyright
 *  notice, this list of conditions and the following disclaimer.
 * 
 *  - Redistributions in binary form must reproduce the above copyright
 *  notice, this list of conditions and the following disclaimer in the
 *  documentation and/or other materials provided with the distribution.
 * 
 *  - Neither the name of the University of Minho nor the names of its
 *  contributors may be used to endorse or promote products derived from
 *  this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package net.sf.neem.impl;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.*;

/**
 * Implementation of overlay management. This class combines a
 * number of random walks upon initial join with periodic shuffling.
 */
public class Overlay implements ConnectionListener, DataListener {
    private final int h = 0;
    public int joins, purged, shuffleIn, shuffleOut;
    private Transport net;
    private InetSocketAddress netid;
    private short joinport;
    private short shuffleport;
    private short idport;
    private Periodic shuffle;
    /**
     * The peers variable can be queried by an external thread for JMX
     * management. Therefore, all sections of the code that modify it must be
     * synchronized. Sections that read it from the protocol thread need not be
     * synchronized.
     */
    private HashMap<UUID, Connection> peers;
    private UUID myId;
    private Random rand;
    private int fanout;
    private int s;
    private int exch;

    /**
     * Creates a new instance of Overlay
     */
    public Overlay(Random rand, InetSocketAddress id, UUID myId, Transport net, short joinport, short idport, short shuffleport) {
        this.rand = rand;
        this.netid = id;
        this.net = net;
        this.idport = idport;
        this.shuffleport = shuffleport;
        this.joinport = joinport;

        /*
         * Default Fanout
         */
        this.fanout = 15;
        this.exch = 7;
        this.s = 7;

        this.myId = myId;
        this.peers = new HashMap<UUID, Connection>();
        this.shuffle = new Periodic(rand, net, 10000) {
            public void run() {
                shuffle();
            }
        };


        net.setDataListener(this, this.shuffleport);
        net.setDataListener(this, this.idport);
        net.setConnectionListener(this);
    }

    public void receive(ByteBuffer[] msg, Connection info, short port) {
        info.age = 0;

        if (port == this.idport)
            handleId(msg, info);
        else if (port == this.shuffleport)
            handleShuffle(msg, info);
        else
            handleJoin(msg);
    }

    private void handleId(ByteBuffer[] msg, Connection info) {
        if (peers.isEmpty()) {
            info.send(new ByteBuffer[]{
                            UUIDs.writeUUIDToBuffer(myId),
                            Addresses.writeAddressToBuffer(netid)},
                    this.joinport);
            shuffle.start();
        }

        UUID id = UUIDs.readUUIDFromBuffer(msg);
        InetSocketAddress addr = Addresses.readAddressFromBuffer(msg);

        if (peers.containsKey(id)) {
            info.handleClose();
            return;
        }

        synchronized (this) {
            info.id = id;
            info.listen = addr;
            peers.put(id, info);
        }
    }

    private void handleShuffle(ByteBuffer[] msg, Connection info) {
        shuffleIn++;
        ArrayList<Connection> receivedView = new ArrayList<>();
        //add sender here
        receivedView.add(info);
        //TODO refactor reading from ByteBuffer[]
        for (ByteBuffer byteBuffer : msg) {
            byte[] content = byteBuffer.array();
            ByteArrayInputStream byteIn = new ByteArrayInputStream(content);

            try {
                ObjectInputStream objectIn = new ObjectInputStream(byteIn);
                receivedView.addAll((ArrayList<Connection>) objectIn.readObject());
            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
            }
        }
        selectToKeep(receivedView);
        /*
        UUID id = UUIDs.readUUIDFromBuffer(msg);
		InetSocketAddress addr = Addresses.readAddressFromBuffer(msg);

		if (peers.containsKey(id))
			return;

		// Flip a coin...
		if (peers.size() < fanout || rand.nextFloat() > 0.5) {
			net.add(addr);
		} else {
			shuffleOut++;
			Connection[] conns = connections();
			int idx = rand.nextInt(conns.length);
			conns[idx].send(Buffers.clone(beacon), this.shuffleport);
		}
		*/
    }

    private ArrayList<Connection> selectToSend() {
        ArrayList<Connection> toSend = new ArrayList<>();
        ArrayList<Connection> tmpView = new ArrayList<>(peers.values());

        Collections.shuffle(tmpView);
        //Move oldest H items to the end (from view) H = 0 for the moment
        for (int i = 0; i < h; i++) {
            Connection oldest = Collections.max(tmpView.subList(0, tmpView.size() - i), (o1, o2) -> o1.age - o2.age);
            Collections.swap(tmpView, tmpView.indexOf(oldest), tmpView.size() - (i + 1));
        }

        //Append the  exch-1 element from view to toSend
        for (int i = 0; i < exch - 1; i++) {
            toSend.add(tmpView.get(i));
        }
        return toSend;
    }

    //TODO is C really equals to fanout ?
    private synchronized void selectToKeep(ArrayList<Connection> receivedView) {
        //remove duplicates from view
        ArrayList<Connection> toRemove = new ArrayList<>();
        for (Connection conn : receivedView) {
            boolean isDuplicate = removeDuplicate(conn);
            if (isDuplicate) toRemove.add(conn);
        }
        receivedView.removeAll(toRemove);
        //merge view and received in an arrayList
        ArrayList<Connection> newView = new ArrayList<>(peers.values());
        newView.addAll(receivedView);
        //remove min(H, #view-c) oldest items
        int minimum = (Math.min(h, (newView.size() - fanout)));
        while (minimum > 0) {
            Connection oldestConnection = Collections.max(newView, (o1, o2) -> o1.age - o2.age);
            newView.remove(oldestConnection);
            if (peers.containsKey(oldestConnection.id)) {
                purgeConnection(oldestConnection);
            } else {
                receivedView.remove(oldestConnection);
            }
            minimum--;
        }

        //remove the min(S, #view -c) head items from view
        minimum = Math.min(s, (newView.size() - fanout));
        while (minimum > 0) {
            Connection removed = newView.remove(0);
            if (peers.containsKey(removed.id)) {
                purgeConnection(removed);
            } else {
                receivedView.remove(removed);
            }
            minimum--;
        }

        //add new connections
        for (Connection conn : receivedView) {
            net.add(conn.listen);
        }

        //trim size
        purgeConnections();
    }

    private boolean removeDuplicate(Connection info) {
        if (peers.containsKey(info.id) && peers.get(info.id).age <= info.age) return true;
        else if (peers.containsKey(info.id)) {
            peers.get(info.id).age = info.age;
            return true;
        } else {
            return false;
        }
    }

    private void handleJoin(ByteBuffer[] msg) {
        joins++;
        ByteBuffer[] beacon = Buffers.clone(msg);

        Connection[] conns = connections();
        for (int i = 0; i < conns.length; i++) {
            shuffleOut++;
            conns[i].send(Buffers.clone(beacon), this.shuffleport);
        }
    }

    public void open(Connection info) {
        info.send(new ByteBuffer[]{UUIDs.writeUUIDToBuffer(this.myId),
                Addresses.writeAddressToBuffer(netid)}, this.idport);
        purgeConnections();
    }

    public synchronized void close(Connection info) {
        if (info.id != null) {
            peers.remove(info.id);
        }
        if (peers.isEmpty()) {
            // Disconnected. Should it notify the application?
        }
    }

    private void purgeConnections() {
        Connection[] conns = connections();
        int nc = conns.length;

        while (peers.size() > fanout) {
            Connection info = conns[rand.nextInt(nc)];
            peers.remove(info.id);
            info.handleClose();
            info.id = null;
            purged++;
        }
    }

    private void purgeConnection(Connection info) {
        peers.remove(info.id);
        info.handleClose();
        info.id = null;
        purged++;
    }

    /**
     * Shuffle a part of view to an other peer
     */
    private synchronized void shuffle() {
        //don't shuffle if not enough in the view
        if (connections().length < fanout) return;
        //selectPartner from view
        Connection partner = connections()[rand.nextInt(connections().length)];
        //selectTosend
        ArrayList<Connection> toSend = selectToSend();
        //send to Partner
        this.sendPeers(partner, toSend);
        // increase all ages in the view
        for (Connection connection : peers.values()) {
            connection.age++;
        }

        /*
        Connection[] conns = connections();
        if (conns.length<2)
        	return;
		Connection toSend = conns[rand.nextInt(conns.length)];
		Connection toReceive = conns[rand.nextInt(conns.length)];

		if (toSend.id == null)
			return;

		this.tradePeers(toReceive, toSend);
		*/
    }

    /**
     * Send fresh connection to a target peer
     *
     * @param target The connection peer.
     * @param toSend the array of peers
     */
    public void sendPeers(Connection target, ArrayList<Connection> toSend) {
        shuffleOut++;
        ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
        try {
            ObjectOutputStream out = new ObjectOutputStream(byteOut);
            out.writeObject(toSend);
        } catch (IOException e) {
            e.printStackTrace();
        }
        ByteBuffer bb = ByteBuffer.wrap(byteOut.toByteArray());
        target.send(new ByteBuffer[]{bb}, this.shuffleport);
    }

    // Configuration parameters

    /**
     * Get all connections that have been validated.
     */
    public synchronized Connection[] connections() {
        return peers.values().toArray(new Connection[peers.size()]);
    }

    /**
     * Get all connected peers.
     */
    public synchronized UUID[] getPeers() {
        UUID[] peers = new UUID[this.peers.size()];
        peers = this.peers.keySet().toArray(peers);
        return peers;
    }

    /**
     * Get all peer addresses.
     */
    public synchronized InetSocketAddress[] getPeerAddresses() {
        InetSocketAddress[] addrs = new InetSocketAddress[this.peers.size()];
        int i = 0;
        for (Connection peer : peers.values())
            addrs[i++] = peer.listen;
        return addrs;
    }

    /**
     * Get globally unique ID in the overlay.
     */
    public UUID getId() {
        return myId;
    }

    public InetSocketAddress getLocalSocketAddress() {
        return netid;
    }

    public int getFanout() {
        return fanout;
    }

    public void setFanout(int fanout) {
        this.fanout = fanout;
        this.exch = fanout / 2;
        this.s = fanout / 2;
    }

    public int getShufflePeriod() {
        return shuffle.getInterval();
    }

    // Statistics

    public void setShufflePeriod(int shufflePeriod) {
        this.shuffle.setInterval(shufflePeriod);
    }

    public void resetCounters() {
        joins = purged = shuffleIn = shuffleOut = 0;
    }
}

