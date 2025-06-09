package org.sz.net.forward;

import java.io.IOException;
import java.io.InputStream;

import lombok.AllArgsConstructor;
import lombok.Data;
// a packet: two byte length plus the content
public class PacketReader {
	
	@Data
	@AllArgsConstructor
	public static class Packet {
		byte[] buf;
		int start;
		int len;
	}
	public static final int MAX_PACKET_LEN = (1 << 16) - 0x40; //???
	byte[] buffer;
	InputStream in;
	public PacketReader(InputStream in) {
		this.in = in;
		buffer = new byte[MAX_PACKET_LEN + 2]; // 2 byte length
	}
	
	public Packet read() throws IOException {
		int n = in.read(buffer);
		if (n < 2) {
			throw new IOException("invalid packet header");
		}
		int len = ((buffer[0]&0xff) << 8) | (buffer[1] & 0xff);
		assert len <= MAX_PACKET_LEN;
		int s = n - 2;
		int left = len - s;
		while (left > 0) {
			n = in.read(buffer, s + 2, left);
			if (n == -1) {
				throw new IOException("error while reading packet");
			}
			s += n;
			left -= n;
		}
		return new Packet(buffer, 2, len);
	}
	

}
