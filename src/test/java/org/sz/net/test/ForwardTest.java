package org.sz.net.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.sz.net.forward.Start;

public class ForwardTest {

	@BeforeAll
	public static void startEchoServer() throws IOException {
		Thread server = new Thread(() -> {
			try (ServerSocket ss = new ServerSocket(16789)) {
				while (true) {
					Socket s = ss.accept();
					byte[] buf = new byte[1024];
					while (true) {
						int n = s.getInputStream().read(buf);
						if (n == -1) {
							break;
						}
						System.out.print(new String(buf, 0, n));
						OutputStream o = s.getOutputStream();
						o.write(buf, 0, n);
						o.flush();
					}
					s.close();

				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		});
		server.setDaemon(true);
		server.start();

		Thread tserver = new Thread(() -> {
			try {
				Start.main(new String[] { "server" });
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		});
		tserver.setDaemon(true);
		tserver.start();

		Thread tclient = new Thread(() -> {
			try {
				Start.main(new String[] { "client", "-c", "client.properties", "-h", "localhost", "-o", "10000" });
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		});
		tclient.setDaemon(true);
		tclient.start();
	}

	@Test
	public void testEcho() throws UnknownHostException, IOException {
		try (Socket s = new Socket("localhost", 6789)) {
			s.getOutputStream().write("test".getBytes());
			byte[] buf = new byte[1024];
			int n = s.getInputStream().read(buf);
			String str = new String(buf, 0, n);
			assertEquals("test", str);
		}
	}

}
