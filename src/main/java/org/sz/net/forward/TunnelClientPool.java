package org.sz.net.forward;

import java.io.IOException;
import java.net.Socket;
import java.util.LinkedList;

import javax.net.SocketFactory;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TunnelClientPool {
	private static final int IDLE_CONNS = 20;
	LinkedList<TunnelClient> pool = new LinkedList<>();
	String host;
	int port;
	SocketFactory sf;
	public TunnelClientPool(SocketFactory sf, String host, int port) 
			throws IOException {
		this.host = host;
		this.port = port;
		this.sf = sf;
		init();
	}
	
	private void init() throws IOException {
		for (int i = 0; i < IDLE_CONNS; i++) {
			this.put(newClient());
		}
	}
	
	private TunnelClient newClient() throws IOException {
		Socket s = sf.createSocket(host, port);
		TunnelClient tc = new TunnelClient(s, this::put);
		return tc;
	}
	
	public TunnelClient get() throws IOException {
		synchronized(this) {
			if (!pool.isEmpty()) {
				log.debug("got tunnel connection from pool");
				return pool.removeLast();
			}
		}
		TunnelClient tc = newClient();
		return tc;
	}
	
	public synchronized void put(TunnelClient t) {
		log.debug("put idle connection in pool");
		pool.addLast(t);
		for (int i = 0; i < pool.size() - IDLE_CONNS; i++) {
			pool.removeFirst().close();
		}
	}

}
