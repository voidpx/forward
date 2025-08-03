package org.sz.net.forward;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;

import javax.net.ServerSocketFactory;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Server {
	ServerSocketFactory sf;
	int port;
	Map<String, Boolean> allow;
	public Server(ServerSocketFactory sf, int port, Map<String, Boolean> allow) {
		this.sf = sf;
		this.port = port;
		this.allow = allow;
	}
	
	private void handleConn(Socket s) {
		Thread th = new Thread(() -> {
			TunnelServer t;
			try {
				t = new TunnelServer(s, this.allow);
			} catch (IOException e) {
				log.error("error setting up tunnel connection", e);
				return;
			}
			try {
				if (t.accept()) {
					t.forward();
				}
			} catch (IOException e) {
				log.error(e.getMessage(), e);
				t.close(false);
			}
		});
		th.setDaemon(true);
		th.start();
	}
	
	public void start() throws IOException {
		ServerSocket ss = sf.createServerSocket(port);
		while (true) {
			Socket s = ss.accept();
			handleConn(s);
		}
	}

}
