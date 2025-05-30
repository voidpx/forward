package org.sz.net.test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

public class EchoServer {
	
	private static void handleEcho(Socket s) throws IOException {
		new Thread(() -> {
			try {
				while (true) {
					byte[] buf = new byte[1024];
					int n = s.getInputStream().read(buf);
					if (n == -1) {
						break;
					}
					System.out.print(new String(buf, 0, n));
					OutputStream o = s.getOutputStream();
					o.write(buf, 0, n);
					o.flush();
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
			
		}).start();
	}

	public static void main(String[] args) {
		try (ServerSocket ss = new ServerSocket(16789)) {
			while (true) {
				Socket s = ss.accept();
				handleEcho(s);
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}
