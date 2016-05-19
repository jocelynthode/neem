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

import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Random;
import java.util.UUID;

/**
 * Implementation of gossip. Like bimodal, combines a forward
 * retransmission phase with a repair phase. However, the
 * optimistic phase is also gossip based. UUIDs, instead of
 * sequence numbers are used to identify and discard duplicates.  
 */
public class Gossip implements DataListener {
	/**
     *  Creates a new instance of Gossip.
     */
    public Gossip(Random rand, Transport net, Overlay memb, short dataport, short ctrlport) {
        this.memb = memb;
        this.dataport = dataport;
        this.rand = rand;
        this.fanout = 11;
        
        net.setDataListener(this, this.dataport);
    }
    
    public void handler(Application handler) {
        this.handler = handler;
    }
        
    public void multicast(ByteBuffer[] msg) {
        relay(msg, this.fanout, dataport, memb.connections());
    }

    private void relay(ByteBuffer[] msg, int fanout, short syncport, Connection[] conns) {
        // Select destinations
        int[] universe=RandomSamples.mkUniverse(conns.length);
        int samples=RandomSamples.uniformSample(fanout, universe, rand);

        // Forward
        for(int i = 0; i < samples; i++) {
            conns[universe[i]].send(Buffers.clone(msg), syncport);
        }
    }
    
    public void receive(ByteBuffer[] msg, Connection info, short port) {
        this.handler.deliver(msg);
	}


    /**
     * ConnectionListener management module.
     */
    private Overlay memb;

    /**
     *  Represents the class to which messages must be delivered.
     */
    private Application handler;

    /**
     *  The Transport port used by the Gossip class instances to exchange messages. 
     */
    private short dataport;



    /**
     * Random number generator for selecting targets.
     */
    private Random rand;

    // Configuration parameters
    
    /**
     *  Number of peers to relay messages to.
     */
    private int fanout;

    /**
     * Maximum number of stored ids.
     */
    private int maxIds = 100;


    public int getFanout() {
        return fanout;
    }

    public void setFanout(int fanout) {
        this.fanout = fanout;
    }

    public int getMaxIds() {
        return maxIds;
    }

    public void setMaxIds(int maxIds) {
        this.maxIds = maxIds;
    }
	
	// Statistics
    
    public int mcast, deliv, dataIn, dataOut, ackIn, ackOut, nackIn, nackOut;

	public void resetCounters() {
		mcast=deliv=dataIn=dataOut=ackIn=ackOut=nackIn=nackOut=0;
	}
}

