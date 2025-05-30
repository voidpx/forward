package org.sz.net.forward;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
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
		this.in = new BufferedInputStream(ts.getInputStream());
		this.out = new BufferedOutputStream(ts.getOutputStream());
		pr = new PacketReader(this.in);
	}
	
	public void connect(Socket peer, String host, int port) throws IOException {
		ProtoOp.CONN.write(out);
		byte[] h = host.getBytes(StandardCharsets.UTF_8);
		assert h.length < 256;
		short len = (short)(h.length + 3); // 2 bytes port, 1 byte host length
		out.write(len >> 8);
		out.write(len & 0xff);
		out.write(h.length);
		out.write(h);
		out.write(new byte[] {(byte)(port>>8), (byte)(port&0xff)});
		out.flush();
		
		ProtoOp op = ProtoOp.read(in);
		if (op != ProtoOp.CONN_OK) {
			throw new IOException("cannot connect to " + host + ":" + port);
		}
		pr.read();
		// connected
		this.peer = peer;
	}
	
	public void accept() throws IOException {
		ProtoOp op = ProtoOp.read(in);
		if (op != ProtoOp.CONN) {
			throw new IOException("expected CONN command");
		}
		byte[] msg = pr.read();
		int hl = msg[0];
		String host = new String(msg, 1, hl, StandardCharsets.UTF_8);
		int port = (msg[hl+1] << 8) | (msg[hl + 2] & 0xff);
		peer = new Socket(host, port);
		ProtoOp.CONN_OK.write(out);
		out.write(new byte[] {0, 0});
		out.flush();
	}
	
	public void forward() {
		Objects.requireNonNull(peer, "peer is null, accept or connect must be called first");
		Thread backward = new Thread(() -> {
			try {
				OutputStream po = peer.getOutputStream();
				while (true) {
					ProtoOp op = ProtoOp.read(in);
					byte[] msg = pr.read();
					if (op == ProtoOp.FORWARD) {
						po.write(msg);
						po.flush();
					} else if (op == ProtoOp.DISCONN) {
						close(false);
					} else {
						throw new IOException("Invalid protocol op: " + op);
					}
				}
			} catch (IOException e) {
				close(false);
				log.error(e.getMessage(), e);
			}
		});
		backward.setName(ts + "=>" + peer);
		backward.setDaemon(true);
		backward.start();
		Thread.currentThread().setName(peer + "=>" + ts);
		try {
			InputStream pi = peer.getInputStream();
			byte[] buf = new byte[PacketReader.MAX_PACKET_LEN + 2];
			while (true) {
				int n = pi.read(buf, 2, PacketReader.MAX_PACKET_LEN);
				if (n == -1) {
					log.info("peer closed");
					close(true);
					log.info("tunnel closed");
					break;
				}
				buf[0] = (byte)(n >> 8);
				buf[1] = (byte)(n & 0xff);
				ProtoOp.FORWARD.write(out);
				out.write(buf, 0, n + 2);
				out.flush();
			}
		} catch (Exception e) {
			close(false);
			log.error("IO error occurred", e);
		}
	}
	
	private synchronized void close(boolean notifyOtherEnd) {
		try {
			if (notifyOtherEnd) {
				ProtoOp.DISCONN.write(out);
				out.write(new byte[] {0,0}); // zero length
				out.flush();
			}
			ts.close();
			if (peer != null) {
				peer.close();
			}
		} catch (IOException e) {
			log.error("error while closing tunnel", e);
		} finally {
//			ts = null;
//			peer = null;
		}
	}

	
}
