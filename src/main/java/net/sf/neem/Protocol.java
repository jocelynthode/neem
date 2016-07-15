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

package net.sf.neem;

import net.sf.neem.impl.Gossip;
import net.sf.neem.impl.Overlay;
import net.sf.neem.impl.Transport;

import java.net.InetSocketAddress;
import java.util.UUID;

/**
 * Implementation of the NeEM management bean.
 */
public class Protocol implements ProtocolMBean {
    private MulticastChannel neem;

    // Gossip
    private Transport net;
    private Gossip gossip;
    private Overlay overlay;

    Protocol(MulticastChannel neem) {
        this.neem = neem;
        this.net = neem.net;
        this.gossip = neem.gossip;
        this.overlay = neem.overlay;
    }

    public int getGossipFanout() {
        return gossip.getFanout();
    }

    public void setGossipFanout(int fanout) {
        gossip.setFanout(fanout);
    }

    public int getMaxIds() {
        return gossip.getMaxIds();
    }

    public void setMaxIds(int max) {
        gossip.setMaxIds(max);
    }

    public int getDelivered() {
        return gossip.deliv;
    }

    public int getMulticast() {
        return gossip.mcast;
    }

    public int getDataReceived() {
        return gossip.dataIn;
    }

    public int getDataSent() {
        return gossip.dataOut;
    }

    // --- Overlay

    public int getHintsReceived() {
        return gossip.ackIn;
    }

    public int getHintsSent() {
        return gossip.ackOut;
    }

    public int getPullReceived() {
        return gossip.nackIn;
    }

    public int getPullSent() {
        return gossip.nackOut;
    }

    public UUID getLocalId() {
        return overlay.getId();
    }

    public UUID[] getPeerIds() {
        return overlay.getPeers();
    }

    public InetSocketAddress getPublicAddress() {
        return overlay.getLocalSocketAddress();
    }

    public int getOverlayFanout() {
        return overlay.getFanout();
    }

    public void setOverlayFanout(int fanout) {
        overlay.setFanout(fanout);
    }

    public int getShufflePeriod() {
        return overlay.getShufflePeriod();
    }

    public void setShufflePeriod(int period) {
        overlay.setShufflePeriod(period);
    }

    // -- Transport

    public int getJoinRequests() {
        return overlay.joins;
    }

    public int getPurgedConnections() {
        return overlay.purged;
    }

    public int getShufflesReceived() {
        return overlay.shuffleIn;
    }

    public int getShufflesSent() {
        return overlay.shuffleOut;
    }

    public InetSocketAddress getLocalAddress() {
        return net.getLocalSocketAddress();
    }

    public InetSocketAddress[] getPeerAddresses() {
        return overlay.getPeerAddresses();
    }

    public synchronized void addPeer(String addr, int port) {
        neem.connect(new InetSocketAddress(addr, port));
    }

    public int getQueueSize() {
        return net.getQueueSize();
    }

    public void setQueueSize(int size) {
        net.setQueueSize(size);
    }

    public int getBufferSize() {
        return net.getBufferSize();
    }

    public void setBufferSize(int size) {
        net.setBufferSize(size);
    }

    public int getAcceptedSocks() {
        return net.accepted;
    }

    public int getConnectedSocks() {
        return net.connected;
    }

    // --- Global

    public int getPacketsReceived() {
        return net.pktIn;
    }

    public int getPacketsSent() {
        return net.pktOut;
    }

    public int getBytesReceived() {
        return net.bytesIn;
    }

    public int getBytesSent() {
        return net.bytesOut;
    }

    public void resetCounters() {
        net.resetCounters();
        overlay.resetCounters();
        gossip.resetCounters();
    }

};

