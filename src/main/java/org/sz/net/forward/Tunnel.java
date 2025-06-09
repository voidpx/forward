package org.sz.net.forward;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Objects;

import org.sz.net.forward.PacketReader.Packet;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Tunnel {
	static final int KA_INTERVAL = 2 * 60 * 1000; // 2 minutes
	Socket ts;
	Socket peer;
	OutputStream out;
	InputStream in;
	PacketReader pr;
	volatile long lastActive = System.currentTimeMillis();
	public Tunnel(Socket ts) throws IOException {
		this.ts = ts;
		this.ts.setTcpNoDelay(true);
		this.in = new BufferedInputStream(ts.getInputStream());
		this.out = new BufferedOutputStream(ts.getOutputStream());
		pr = new PacketReader(this.in);
	}
	
	private synchronized void updateLiveness() {
		lastActive = System.currentTimeMillis();
	}
	
	public void forward() {
		Objects.requireNonNull(peer, "peer is null, accept or connect must be called first");
		Thread backward = new Thread(() -> {
			OutputStream po;
			try {
				po = peer.getOutputStream();
			} catch (IOException e) {
				log.error("error getOutputStream for peer");
				close(true);
				return;
			}
			while (true) {
				ProtoOp op;
				Packet msg;
				try {
					op = ProtoOp.read(in);
					msg = pr.read();
				} catch (IOException e) {
					log.error("error reading from tunnel", e);
					close(false);
					break;
				}
				if (op == ProtoOp.FORWARD) {
					try {
						po.write(msg.getBuf(), msg.getStart(), msg.getLen());
						po.flush();
					} catch (IOException e) {
						log.error("error writing to peer", e);
						close(true);
						break;
					}
				} else if (op == ProtoOp.KEEPALV) {
					log.debug("keep alive probe");
				} else if (op == ProtoOp.DISCONN) {
					close(false);
					break;
				} else {
					log.error("invalid protocol op: {}", op);
					break;
				}
				updateLiveness();
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
			log.error("error getInputStream from peer", e);
			close(true);
			return;
		}
			byte[] buf = new byte[PacketReader.MAX_PACKET_LEN + 2];
			while (true) {
				int n;
				try {
					n = pi.read(buf, 2, PacketReader.MAX_PACKET_LEN);
				} catch (Exception e) {
					log.debug("error reading from peer", e);
					close(true);
					break;
				}
				if (n == -1) {
					log.debug("peer closed, closing tunnel");
					close(true);
					log.debug("tunnel closed");
					break;
				}
				buf[0] = (byte)(n >> 8);
				buf[1] = (byte)(n & 0xff);
				try {
					synchronized (out) {
						ProtoOp.FORWARD.write(out);
						out.write(buf, 0, n + 2);
						out.flush();
					}
				} catch (IOException e) {
					log.error("error writing to tunnel", e);
					close(false);
					break;
				}
				updateLiveness();
			}
	}
	
	public void keepAlive() {
		if (ts.isClosed() || System.currentTimeMillis() - lastActive < KA_INTERVAL) return;
		try {
			synchronized (out) {
				ProtoOp.KEEPALV.write(out);
				out.write(new byte[] {0x0, 0x0});
				out.flush();
			}
			updateLiveness();
		} catch (IOException e) {
			log.error("error sending keep alive");
			close(false);
		}
	}
	
	private void close(boolean notifyOtherEnd) {
		if (ts.isClosed())
			return;
		if (notifyOtherEnd) {
			try {
				synchronized (out) {
					ProtoOp.DISCONN.write(out);
					out.write(new byte[] { 0, 0 }); // zero length
					out.flush();
				}
			} catch (IOException e) {
				log.error("error sending DISCONN", e);
			}
		}
		try {
			if (peer != null) {
				peer.close();
			}
			ts.close();
		} catch (IOException e) {
			log.error("error while closing tunnel", e);
		}
		onClose();
	}

	protected void onClose() {
		
	}
}
