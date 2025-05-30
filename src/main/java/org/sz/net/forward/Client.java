package org.sz.net.forward;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

import javax.net.SocketFactory;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Client {
	SocketFactory sf;
	Map<Integer, String> ports;
	String serverHost;
	int serverPort;
	int portOffset = 0;
	public Client(SocketFactory sf, Properties config, String serverHost, int serverPort, int poff) {
		this.sf = sf;
		this.serverHost = serverHost;
		this.serverPort = serverPort;
		this.portOffset = poff;
		ports = config.entrySet().stream().map(e -> Map.entry(Integer.parseInt((String)e.getKey()), (String)e.getValue()))
			.collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue()));
	}
	
	private void handleConn(Socket peer) throws UnknownHostException, IOException {
		Thread th = new Thread(() -> {
			try {
				Socket s = sf.createSocket(serverHost, serverPort);
				Tunnel t = new Tunnel(s);
				int rp;
				String rh = ports.get(peer.getLocalPort());
				int ci = rh.lastIndexOf(':');
				if (ci >= 0 && !(rh.contains("]") && ci > 0 && rh.charAt(ci-1) != ']')) {
					rp = Integer.parseInt(rh.substring(ci + 1));
					rh = rh.substring(0, ci);
				} else {
					rp = peer.getLocalPort() + portOffset;
				}
				t.connect(peer, rh, rp);
				t.forward();
			} catch (IOException e) {
				log.error(e.getMessage(), e);
			}
		});
		th.setName(peer.toString());
		th.setDaemon(true);
		th.start();
		
	}
	
	private Thread startOne(int port) {
		Thread th = new Thread(() -> {
			try (ServerSocket ss = new ServerSocket(port)) {
				while (true) {
					Socket s = ss.accept();
					handleConn(s);
				}
			} catch (IOException e) {
				log.error("unable to accept at port: " + port, e);
			}
		});
		th.setName(String.valueOf(port));
		th.setDaemon(true);
		th.start();
		return th;
	}
	
	public void start() throws IOException, InterruptedException {
		List<Thread> ths = new ArrayList<>();
		for (Map.Entry<Integer, String> e : ports.entrySet()) {
			ths.add(startOne(e.getKey()));
		}
		for (Thread t : ths) {
			t.join();
		}
	}

}
