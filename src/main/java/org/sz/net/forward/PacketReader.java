package org.sz.net.forward;

import java.io.IOException;
import java.io.InputStream;
// a packet: two byte length plus the content
public class PacketReader {
	public static final int MAX_PACKET_LEN = 1450;
	InputStream in;
	public PacketReader(InputStream in) {
		this.in = in;
	}
	
	public byte[] read() throws IOException {
		byte[] lenb = new byte[2];
		int n = in.read(lenb);
		if (n != lenb.length) {
			throw new IOException("Invalid header");
		}
		int len = (lenb[0] << 8) | (lenb[1] & 0xff);
		assert len <= MAX_PACKET_LEN;
		byte[] buf = new byte[len];
		int s = 0;
		int left = len - s;
		while (left > 0) {
			n = in.read(buf, s, left);
			if (n == -1) {
				throw new IOException("error while reading packet");
			}
			s += n;
			left -= n;
		}
		return buf;
	}

}
