package org.sz.net.forward;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import org.sz.net.forward.PacketReader.Packet;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TunnelServer extends Tunnel {

	public TunnelServer(Socket ts) throws IOException {
		super(ts);
	}
	
	// called on server
	public boolean accept() throws IOException {
		ProtoOp op;
		Packet msg;
		do {
			op = ProtoOp.read(in);
			msg = pr.read();
		} while (op == ProtoOp.KEEPALV);
		if (op != ProtoOp.CONN) {
			throw new IOException("expected CONN command");
		}
		int hl = msg.getBuf()[msg.getStart()];
		String host = new String(msg.getBuf(), msg.getStart() + 1, hl, StandardCharsets.UTF_8);
		int port = ((msg.getBuf()[msg.getStart() + 1 + hl] & 0xff) << 8) | (msg.getBuf()[msg.getStart() + hl + 2] & 0xff);
		log.debug("accepting connection to {}:{}", host, port);

		try {
			peer = new Socket();
			peer.setTcpNoDelay(true);
			peer.connect(new InetSocketAddress(host, port));
		} catch (IOException e) {
			log.error("error connecting to " + host + ":" + port, e);
			ProtoOp.CONN_ERR.write(out);
			out.write(new byte[] {0, 0});
			out.flush();
			return false;
		}
		ProtoOp.CONN_OK.write(out);
		out.write(new byte[] {0, 0});
		out.flush();
		
		log.debug("established connection to {}:{}", host, port);
		return true;
	}

}
