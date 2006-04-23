/*
 * NeEM - Network-friendly Epidemic Multicast
 * Copyright (c) 2005-2006, University of Minho
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


import java.io.IOException;
import java.net.BindException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;


/**
 * Implementation of the NEEM network layer.
 */
public class Transport implements Runnable {
    private Connection idinfo;

	public Transport(InetSocketAddress local) throws IOException, BindException {
        timers = new TreeMap<Long, Runnable>();
        handlers = new HashMap<Short, DataListener>();
        selector = SelectorProvider.provider().openSelector();

        connections = new HashSet<Connection>();
        idinfo = new Connection(this, local, false);
        connections.add(idinfo); 

        id = new InetSocketAddress(InetAddress.getLocalHost(), local.getPort());
    }
    
    /**
     * Get local id.
     */
    public InetSocketAddress id() {
        return id;
    }
    
    /**
     * Get all connections.
     */
    public Connection[] connections() {
        return (Connection[]) connections.toArray(
                new Connection[connections.size()]);
    }

    /**
     * Call periodically to garbage collect idle connections.
     */
    public void gc() {
        Iterator i = connections.iterator();

        while (i.hasNext()) {
            Connection info = (Connection) i.next();

            info.handleGC();
        }
    }

    /**
     * Close all socket connections and release polling thread.
     */
    public synchronized void close() {
        if (closed)
            return;
        closed=true;
        timers.clear();
        membership_handler=null;
        selector.wakeup();
        for(Connection info: connections)
            info.handleClose();
        idinfo.handleClose();
    }

    /**
     * Queue processing task.
     */
    public synchronized void queue(Runnable task) {
        schedule(task, 0);
    }

    /**
     * Schedule processing task.
     * @param delay delay before execution
     */
    public synchronized void schedule(Runnable task, long delay) {
        Long key = new Long(System.nanoTime() + delay*1000000);

        while(timers.containsKey(key))
        	key=key+1;
        timers.put(key, task);
        if (key == timers.firstKey()) {
            selector.wakeup();
        }
    }

    /**
     * Initiate connection to peer. This is effective only
     * after the open callback.
     */
    public void add(InetSocketAddress addr) {
        try {
           Connection info = new Connection(this, null, true);
           info.connect(addr);
        } catch (IOException e) {
        	// We don't care
        }
    }

    /**
     * Add a reference to an event handler.
     */
    public void handler(DataListener handler, short port) {
        this.handlers.put(new Short(port), handler);
    }
        
    /** Sets the reference to the membership event handler.
     */
    public void membership_handler(Membership handler) {
        this.membership_handler = handler;
    }

    /**
     * Main loop.
     */
    public void run() {
        while (true) {
            try {
                // Execute pending tasks.
                Runnable task = null;
                long delay = 0;

                synchronized (this) {
                    if (!timers.isEmpty()) {
                        long now = System.nanoTime();
                        Long key = timers.firstKey();

                        if (key <= now) {
                            task = timers.remove(key);
                        } else {
                            delay = key - now;
                        }
                    }
                }
            
                if (task != null) {
                    task.run();
                } else {    
                	if (delay>0 && delay<1000000)
                		delay=1;
                	else
                		delay/=1000000;

                	selector.select(delay);
                    if (closed)
                        break;
                            
                    // Execute pending event-handlers.
                            
                    for (Iterator j = selector.selectedKeys().iterator(); j.hasNext();) {
                        SelectionKey key = (SelectionKey) j.next();
                        Connection info = (Connection) key.attachment();

                        if (!key.isValid()) {
                            info.handleClose();
                            continue;
                        }
                        if (key.isReadable()) {
                            info.handleRead();
                        } else if (key.isAcceptable()) {
                            info.handleAccept();
                        } else if (key.isConnectable()) {
                            info.handleConnect();
                        } else if (key.isWritable()) {
                            info.handleWrite();
                        } 
                    }
                }
                        
            } catch (IOException e) {
                // This handles only exceptions thrown by the selector and the
                // server socket. Invdidual connections are dropped silently.
                e.printStackTrace();
            } catch (CancelledKeyException cke) {
                System.out.println("The selected key was closed.");
            }
        }
    }

	void deliverSocket(SocketChannel sock) throws IOException, SocketException, ClosedChannelException {
		final Connection info = new Connection(this, sock);

		synchronized(this) {
			this.connections.add(info); // adiciona nova connection as connections conhecidas
		}
		queue(new Runnable() {
		    public void run() {
		    	membership_handler.open(info);
		    }
		});
		this.accepted++;
	}

	void notifyOpen(final Connection info) {
        synchronized(this) {
        	connections.add(info);
        }
        queue(new Runnable() {
			public void run() {
				if (membership_handler != null)
					membership_handler.open(info);
			}
		});
	}
    
	void notifyClose(final Connection info) {
        if (closed) {
			return;
		}
		synchronized (this) {
			connections.remove(info);
		}
		queue(new Runnable() {
			public void run() {
				if (membership_handler!=null)
					membership_handler.close(info);
			}
		});
	}

	void deliver(final Connection source, final Short prt, final ByteBuffer[] msg) {
		final DataListener handler = handlers.get(prt);
		if (handler==null) {
			// unknown handler
			return;
		}
		queue(new Runnable() {
			public void run() {
				handler.receive(msg, source, prt);
			}
		});
	}

    /**
	 * Local id for each instance
	 */
    private InetSocketAddress id;
    
    /**
     * Selector for events
     */
    Selector selector;

    /** Storage for open connections to other group members
     * This variable can be queried by an external thread for JMX
     * management. Therefore, all sections of the code that modify it must
     * be synchronized. Sections that read it from the protocol thread need
     * not be synchronized.
     */
    private Set<Connection> connections;

    /** 
     * Queue for tasks
     */
    private SortedMap<Long, Runnable> timers;

    /** 
     * Service indicator
     */
    public int accepted = 0;

    /** 
     * Storage for DataListener protocol events handlers
     */
    private Map<Short, DataListener> handlers;

    /** 
     * Reference for Membership events handler
     */
    private Membership membership_handler;

    /**
     * If we're not responding any more
     */
    private boolean closed;
    
    /**
     * Execution queue size
     */
    private int default_Q_size = 10;

    public int getDefault_Q_size() {
		return default_Q_size;
	}

	public void setDefault_Q_size(int default_Q_size) {
		this.default_Q_size = default_Q_size;
	}

	/**
     * Get addresses of all connected peers.
     */
    public InetSocketAddress[] getPeers() {
    	List<InetSocketAddress> addrs=new ArrayList<InetSocketAddress>();
    	for(Connection info: connections) {
    		InetSocketAddress addr=info.getPeer();
    		if (addr!=null)
    			addrs.add(addr);
    	}
    	return addrs.toArray(new InetSocketAddress[addrs.size()]);
    }

	/**
     * Get all local addresses.
     */
    public InetSocketAddress[] getLocals() {
    	List<InetSocketAddress> addrs=new ArrayList<InetSocketAddress>();
    	for(Connection info: connections) {
    		InetSocketAddress addr=info.getLocal();
    		if (addr!=null)
    			addrs.add(addr);
    	}
    	return addrs.toArray(new InetSocketAddress[addrs.size()]);
    }
}

// arch-tag: d500660f-d7f0-498f-8f49-eb548dbe39f5