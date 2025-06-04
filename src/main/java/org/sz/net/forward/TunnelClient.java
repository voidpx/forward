package org.sz.net.forward;

import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TunnelClient extends Tunnel {

	Consumer<TunnelClient> onDisconn;
	
	public TunnelClient(Socket ts, Consumer<TunnelClient> onDisconn) throws IOException {
		super(ts);
		this.onDisconn = onDisconn;
	}
	
	// called on client side
	public void connect(Socket peer, String host, int port) throws IOException {
		log.debug("{} connecting to {}:{}", peer, host, port);
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
		
		this.peer = peer;
		ProtoOp op = ProtoOp.read(in);
		pr.read(); // zero length
		if (op != ProtoOp.CONN_OK) {
			onDisconn();
			throw new IOException("cannot connect to " + host + ":" + port);
		}
		// connected
	}
	
	@Override
	protected synchronized void onDisconn() {
		if (peer != null) {
			onDisconn.accept(this);
		}
		super.onDisconn();
	}
}
