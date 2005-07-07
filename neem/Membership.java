/*
 * NeEM - Network-friendly Epidemic Multicast
 * Copyright (c) 2005, University of Minho
 * All rights reserved.
 *
 * Contributors:
 *  - Pedro Santos <psantos@gmail.com>
 *  - Jose Orlando Pereira <jop@di.uminho.pt>
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 * 
 *  - Redistributions of source code must retain the above copyright
 *  notice, this list of conditions and the following disclaimer.
 * 
 *  - Redistributions in binary form must reproduce the above copyright
 *  notice, this list of conditions and the following disclaimer in the
 *  documentation and/or other materials provided with the distribution.
 * 
 *  - Neither the name of the University of Minho nor the names of its
 *  contributors may be used to endorse or promote products derived from
 *  this software without specific prior written permission.
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

/*
 * Membership.java
 *
 * Created on March 17, 2005, 4:07 PM
 */
package neem;


import java.io.*;

import java.net.*;

import java.nio.*;

import java.util.*;


/**
 *  This interface defines the methods to handle events related with changes in 
 * local group. Events about new connections, closing of open connections 
 * and selection of peers for fanout from the members of the group must be
 * handled by these methods.
 *
 * @author psantos@GSD
 */
public interface Membership extends DataListener {
    
    /**
     *  This method is called from Transport whenever a member joins the group.
     * When called, if it's the first time it's called starts the Membership.Waker 
     * class instance that will, from time to time, tell our peers of our open 
     * connections. Then it'll randomly select a peer to be evicted from our local 
     * membership. If it's not the first time this method is called, only the 2nd 
     * step will be executed.
     * @param info The connection to the new peer.
     */
    public void open(Transport.Connection info, int i); // event

    /**
     *  This method is called from Transport whenever a member leaves the group.
     * When called, decreases the number of connected members by one, as the connection
     * to the now disconnected peer has already been removed at the transport layer.
     * @param addr The address of the now closed connection.
     */
    public void close(InetSocketAddress addr); // event
    
    /**
     *  If the number of connected peers equals maximum group size, must evict an
     * older member, as the new one has already been added to the local membership
     * by the transport layer. First we must create space for the new member by 
     * randomly selectig one of the local members (wich can be the newbie, because
     * it has already been added) then read from socket the address where it's 
     * accepting connections. Then increase the number of local members. If the
     * nb_members (current number of members in local membership) is less than 
     * grp_size (maximum number of members in local membership), this method only
     * increases by one the number of members.
     */
    public void probably_remove();
    
    /**
     *  Tell a fanout number of members of my local membership, that there is a
     * connection do the peer identified by its address, wich is sent to the 
     * peers.
     */ 
    public void distributeConnections();
}


; // arch-tag: ffede092-c2f3-43d3-a370-e70051be1ede
