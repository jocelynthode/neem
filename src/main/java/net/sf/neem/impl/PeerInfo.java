package net.sf.neem.impl;

import java.io.Serializable;
import java.net.InetSocketAddress;
import java.util.UUID;

/**
 * Created by jocelyn on 18.07.16.
 */
class PeerInfo implements Serializable {
    InetSocketAddress listen;
    UUID id;
    int age;

    public PeerInfo(InetSocketAddress listen, UUID id, int age) {
        this.listen = listen;
        this.id = id;
        this.age = age;
    }

    public PeerInfo(Connection conn) {
        this.listen = conn.listen;
        this.id = conn.id;
        this.age = conn.age;
    }
}
