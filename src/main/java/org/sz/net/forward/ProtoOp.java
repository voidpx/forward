package org.sz.net.forward;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public enum ProtoOp {
	CONN((short)0x0), // connect
	CONN_OK((short)0x1), // connect ok
	CONN_ERR((short)0x2), // connect error
	FORWARD((short)0x3), // forward
	DISCONN((short)0x4), // disconnect
	KEEPALV((short)0x5); // keep alive
	
	static ProtoOp[] ops = new ProtoOp[] {CONN, CONN_OK, CONN_ERR, FORWARD, DISCONN, KEEPALV};
	
	private short op;
	private ProtoOp(short o) {
		op = o;
	}
	
	public short getOp() {
		return op;
	}
	
	public void write(OutputStream out) throws IOException {
		out.write(op >> 8);
		out.write(op & 0xff);
	}
	
	public static ProtoOp read(InputStream in) throws IOException {
		int b1 = in.read();
		int b2 = in.read();
		if (b1 == -1 || b2 == -1) {
			throw new IOException("connection closed");
		}
		short o = (short)(b1 << 8 | (b2 & 0xff));
		return ops[o];
	}
}
