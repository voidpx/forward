package org.sz.net.forward;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

import javax.net.ServerSocketFactory;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Server {
	ServerSocketFactory sf;
	int port;
	public Server(ServerSocketFactory sf, int port) {
		this.sf = sf;
		this.port = port;
	}
	
	private static void handleConn(Socket s) {
		Thread th = new Thread(() -> {
			try {
				TunnelServer t = new TunnelServer(s);
				t.accept();
				t.forward();
			} catch (IOException e) {
				e.printStackTrace();
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
