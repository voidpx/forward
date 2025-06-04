package org.sz.net.forward;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Objects;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Tunnel {
	Socket ts;
	Socket peer;
	OutputStream out;
	InputStream in;
	PacketReader pr;
	public Tunnel(Socket ts) throws IOException {
		this.ts = ts;
		this.ts.setTcpNoDelay(true);
		this.in = new BufferedInputStream(ts.getInputStream());
		this.out = new BufferedOutputStream(ts.getOutputStream());
		pr = new PacketReader(this.in);
	}
	
	public boolean forward() {
		Objects.requireNonNull(peer, "peer is null, accept or connect must be called first");
		Thread backward = new Thread(() -> {
			OutputStream po;
			try {
				po = peer.getOutputStream();
			} catch (IOException e) {
				log.debug("error getting output stream from peer", e);
				disconnect();
				return;
			}
			while (true) {
				ProtoOp op;
				byte[] msg;
				try {
					op = ProtoOp.read(in);
					msg = pr.read();
				} catch (IOException e) {
					log.error("error reading from tunnel", e);
					close();
					break;
				}
				if (op == ProtoOp.FORWARD) {
					try {
						po.write(msg);
						po.flush();
					} catch (IOException e) {
						log.error("error writting to peer", e);
						disconnect();
						break;
					}
				} else if (op == ProtoOp.DISCONN) {
					onDisconn();
					break;
				} else {
					log.error("invalid protocol op: {}", op);
					close();
					break;
				}
			}
		});
		backward.setName(ts + "=>" + peer);
		backward.setDaemon(true);
		backward.start();
		Thread.currentThread().setName(peer + "=>" + ts);
		InputStream pi;
		try {
			pi = peer.getInputStream();
		} catch (IOException e) {
			log.error("error getting peer input stream");
			disconnect();
			waitFor(backward);
			return true;
		}
		
		boolean ret = true;
		byte[] buf = new byte[PacketReader.MAX_PACKET_LEN + 2];
		while (true) {
			int n;
			try {
				n = pi.read(buf, 2, PacketReader.MAX_PACKET_LEN);
			} catch (IOException e) {
				log.debug("error reading from peer", e);
				disconnect();
				break;
			}
			if (n == -1) {
				log.info("peer closed");
				disconnect();
				break;
			}
			buf[0] = (byte) (n >> 8);
			buf[1] = (byte) (n & 0xff);
			try {
				ProtoOp.FORWARD.write(out);
				out.write(buf, 0, n + 2);
				out.flush();
			} catch (IOException e) {
				log.error("error farwarding to tunnel", e);
				close();
				ret = false;
				break;
			}
		}
		waitFor(backward);
		return ret;
	}
	
	private void waitFor(Thread t) {
		try {
			t.join();
		} catch (InterruptedException e) {
			log.debug("interrupted while waiting for thread {}", t);
		}
	}
	
	private synchronized void disconnect() {
		try {
			ProtoOp.DISCONN.write(out);
			out.write(new byte[] {0,0}); // zero length
			out.flush();
			onDisconn();
		} catch (IOException e) {
			log.error("error while DISCONN", e);
		}
	}
	
	protected synchronized void onDisconn() {
		closePeer();
	}
	
	private synchronized void closePeer() {
		if (peer == null) {return;}
		try {
			peer.close();
			peer = null;
		} catch (IOException e) {
			log.debug("error closing peer", e);
		}
	}
	
	public synchronized void close() {
		if (ts == null) {return;}
		try {
			ts.close();
			ts = null;
		} catch (IOException e) {
			log.debug("error while closing tunnel", e);
		}
		closePeer();
	}

	
}
