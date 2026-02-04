package org.sz.net.forward;

import java.io.IOException;
import java.net.Socket;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;

import javax.net.SocketFactory;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TunnelClientPool implements Runnable {
	static final int IDLE_CONNS = 5;
	LinkedList<TunnelClient> idle = new LinkedList<>();
	Set<TunnelClient> active = new HashSet<>();
	String host;
	int port;
	SocketFactory sf;
	long lastActiveCheck = System.currentTimeMillis();
	public TunnelClientPool(SocketFactory sf, String host, int port) 
			throws IOException {
		this.host = host;
		this.port = port;
		this.sf = sf;
		Thread pw = new Thread(this);
		pw.setDaemon(true);
		pw.setName("client pool watch");
		pw.start();
	}
	
	@Override
	public void run() {
		while (true) {
			try {
				checkPool();
				if (System.currentTimeMillis() - lastActiveCheck >= Tunnel.KA_INTERVAL) {
					synchronized (active) {
						for (TunnelClient c: active) {
							c.keepAlive();
						}
					}
				}
			} catch (IOException e) {
				log.debug("error populating pool", e);
			} finally {
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					log.debug("interrupted", e);
					Thread.currentThread().interrupt();
					break;
				}
			}
		}
	}
	
	private void checkPool() throws IOException {
		synchronized (idle) {
			for (int i = idle.size(); i < IDLE_CONNS; i++) {
				TunnelClient c = newClient();
				idle.addLast(c);
			}
		}
	}
	
	private TunnelClient newClient() throws IOException {
		Socket s = sf.createSocket(host, port);
		TunnelClient tc = new TunnelClient(s, this::close);
		synchronized (active) {
			active.add(tc);
		}
		return tc;
	}
	
	public TunnelClient get() throws IOException {
		synchronized(idle) {
			if (!idle.isEmpty()) {
				log.debug("got tunnel connection from pool");
				return idle.removeFirst();
			}
		}
		TunnelClient tc = newClient();
		return tc;
	}
	
	private void close(TunnelClient t) {
		synchronized (active) {
			active.remove(t);
		}
		synchronized (idle) {
			idle.remove(t);
		}
	}

}
