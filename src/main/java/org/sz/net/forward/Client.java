package org.sz.net.forward;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

import javax.net.SocketFactory;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Client {
	@Data
	@AllArgsConstructor
	@EqualsAndHashCode
	private static class Peer {
		String host;
		int port;
	}
	
	@Data
	@AllArgsConstructor
	private static class Pair {
		Peer local;
		String remote;
	}
	
	SocketFactory sf;
	Map<Integer, Pair> ports;
	String serverHost;
	int serverPort;
	int portOffset = 0;
	TunnelClientPool pool;
	public Client(SocketFactory sf, Properties config, String serverHost, int serverPort, int poff) throws IOException {
		this.sf = sf;
		this.serverHost = serverHost;
		this.serverPort = serverPort;
		this.portOffset = poff;
		ports = config.entrySet().stream().map(e -> {
		   Peer p = parsePeer((String)e.getKey(), -1);
		   return Map.entry(p.port, new Pair(p, (String)e.getValue()));
		})
			.collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue()));
		pool = new TunnelClientPool(sf, serverHost, serverPort);
		
	}
	
	private Peer parsePeer(String s, int defPort) {
		int ci = s.lastIndexOf(':');
		String h = "localhost";
		int port = defPort;
		if (ci >= 0 && !(s.contains("]") && ci > 0 && s.charAt(ci-1) != ']')) {
			h = s.substring(0, ci);
			port = Integer.parseInt(s.substring(ci + 1));
		} else {
			if (port == -1) {
				port = Integer.parseInt(s);
			} else {
				port = defPort + portOffset;
			}
		}
		if (port == -1) {
			throw new IllegalArgumentException("invalid configuration: " + s);
		}
		return new Peer(h, port);
	}
	
	private void handleConn(Socket peer) throws UnknownHostException, IOException {
		Thread th = new Thread(() -> {
			TunnelClient t;
			try {
				t = pool.get();
			} catch (IOException e) {
				log.error("error retrieving a tunnel connection", e);
				return;
			}
			try {
				Pair pr = ports.get(peer.getLocalPort());
				Peer remote = parsePeer(pr.remote, pr.getLocal().getPort());
				t.connect(peer, remote.getHost(), remote.getPort());
				t.forward();
			} catch (IOException e) {
				log.error(e.getMessage(), e);
				t.close(false);
			}
		});
		th.setName(peer.toString());
		th.setDaemon(true);
		th.start();
		
	}
	
	private Thread startOne(Peer p) {
		Thread th = new Thread(() -> {
			try (ServerSocket ss = new ServerSocket()) {
				ss.bind(new InetSocketAddress(p.getHost(), p.getPort()));
				while (true) {
					Socket s = ss.accept();
					s.setTcpNoDelay(true);
					handleConn(s);
				}
			} catch (IOException e) {
				log.error("unable to accept at " + p.getHost() + ":" + p.getPort(), e);
			}
		});
		th.setName(String.valueOf(p.getPort()));
		th.setDaemon(true);
		th.start();
		return th;
	}
	
	public void start() throws IOException, InterruptedException {
		List<Thread> ths = new ArrayList<>();
		for (Map.Entry<Integer, Pair> e : ports.entrySet()) {
			ths.add(startOne(e.getValue().getLocal()));
		}
		for (Thread t : ths) {
			t.join();
		}
	}

}
