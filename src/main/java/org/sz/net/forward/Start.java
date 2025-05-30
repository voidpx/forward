package org.sz.net.forward;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Properties;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import com.tencent.kona.crypto.CryptoInsts;
import com.tencent.kona.pkix.KonaPKIXProvider;
import com.tencent.kona.ssl.KonaSSLProvider;
import com.tencent.kona.sun.security.tools.keytool.Main;

public class Start {

	public static final String SSL_PROVIDER = "KonaSSL";
	public static final String PKIX_PROVIDER = "KonaPKIX";
	public static final String STORE_TYPE = "PKCS12";
	public static final String CERT_TYPE = "X.509";
	public static final String PROTO = "TLCP";
	public static final String ALG = "EC";

	static {
//		System.setProperty("com.tencent.kona.ssl.debug", "all");
		Security.addProvider(CryptoInsts.PROV);
		Security.addProvider(KonaPKIXProvider.instance());
		Security.addProvider(KonaSSLProvider.instance());

	}

	public static SSLContext createSSLContext(KeyBundle[] trusted, KeyBundle[] certs, SSLParams params)
			throws Exception {

		KeyStore ts = null; // trust store
		KeyStore ks = null; // key store
		char passphrase[] = "test".toCharArray();

		// Generate certificate from cert string.
		CertificateFactory cf = CertificateFactory.getInstance(CERT_TYPE, PKIX_PROVIDER);

		// Import the trused certs.
		ByteArrayInputStream is;
		ts = KeyStore.getInstance(STORE_TYPE, PKIX_PROVIDER);
		ts.load(null, null);

		Certificate[] trustedCert = new Certificate[trusted.length];
		for (int i = 0; i < trustedCert.length; i++) {
			is = new ByteArrayInputStream(trusted[i].getCertificate().getBytes());
			try {
				trustedCert[i] = cf.generateCertificate(is);
			} finally {
				is.close();
			}

			ts.setCertificateEntry(trusted[i].getName(), trustedCert[i]);
		}

		ks = KeyStore.getInstance(STORE_TYPE, PKIX_PROVIDER);
		ks.load(null, null);

		for (int i = 0; i < certs.length; i++) {
			// generate the private key.
			PKCS8EncodedKeySpec priKeySpec = new PKCS8EncodedKeySpec(Base64.getMimeDecoder().decode(certs[i].privKey));
			KeyFactory kf = KeyFactory.getInstance("EC", CryptoInsts.PROV);
			PrivateKey priKey = kf.generatePrivate(priKeySpec);

			// generate certificate chain
			is = new ByteArrayInputStream(certs[i].getCertificate().getBytes());
			Certificate keyCert = null;
			try {
				keyCert = cf.generateCertificate(is);
			} finally {
				is.close();
			}

			Certificate[] chain = new Certificate[] { keyCert };

			// import the key entry.
			ks.setKeyEntry(certs[i].getName(), priKey, passphrase, chain);
		}

		// Create an SSLContext object.
		TrustManagerFactory tmf = TrustManagerFactory.getInstance(params.getTrustManagerAlg(), SSL_PROVIDER);
		tmf.init(ts);

		SSLContext context = SSLContext.getInstance(params.getProto(), SSL_PROVIDER);
		KeyManagerFactory kmf = KeyManagerFactory.getInstance(params.getKeyManagerAlg(), SSL_PROVIDER);
		kmf.init(ks, passphrase);

		context.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

		return context;
	}

	private static final String USAGE = "Usage:\n"
			+ "	commands:\n" 
			+ "		keytool <args>\n" 
			+ " 	server -p <port>\n"
			+ "		client -p <server port> -c <config> -h <server host> -o <port offset>\n";
	
	private static void startServer(int port) throws Exception {
		SSLContext ctx = createSSLContext(
				new KeyBundle[] { KeyBundle.fromFile("client",
						"certs/client.crt",
						"certs/client.key") },
				new KeyBundle[] { KeyBundle.fromFile("server",
						"certs/server.crt",
						"certs/server.key"), },
				new SSLParams("TLCP", "PKIX", "NewSunX509"));
		new Server(ctx.getServerSocketFactory(), port).start();
		
	}
	
	private static void startClient(String config, String shost, int sport, int poff) throws Exception {
		SSLContext cctx = createSSLContext(
				new KeyBundle[] { KeyBundle.fromFile("server",
						"certs/server.crt",
						"certs/server.key") },
				new KeyBundle[] { KeyBundle.fromFile("client",
						"certs/client.crt",
						"certs/client.key"), },
				new SSLParams("TLCP", "TencentPKIX", "PKIX"));
		Properties p = new Properties();
		p.load(new FileInputStream(Path.of(config).toFile()));
		new Client(cctx.getSocketFactory(), p, shost, sport, poff).start();
	}

	public static void main(String[] args) throws Exception {
		if (args.length == 0) {
			System.out.println(USAGE);
			System.exit(1);
		}
		String cmd = args[0];
		switch (cmd) {
		case "keytool":
			System.out.println("args: " + String.join(" ", args));
			Main.main(args);
			break;
		case "server":
			int port = 8888;
			for (int i = 1; i < args.length; i++) {
				if ("-p".equals(args[i]) && i < args.length - 1) {
					port = Integer.parseInt(args[i+1]);
				}
			}
			startServer(port);
			break;
		case "client":
			String shost = null;
			String config = null;
			int sport = 8888;
			int poff = 0;
			for (int i = 1; i < args.length; i++) {
				if ("-p".equals(args[i]) && i < args.length - 1) {
					sport = Integer.parseInt(args[i+1]);
				} else if ("-h".equals(args[i]) && i < args.length - 1) {
					shost = args[i+1];
				} else if ("-c".equals(args[i]) && i < args.length - 1) {
					config = args[i+1];
				} else if ("-o".equals(args[i]) && i < args.length - 1) {
					poff = Integer.parseInt(args[i+1]);
				}
			}
			if (shost == null) {
				throw new IllegalArgumentException("-h is required to specify the remote host");
			}
			if (config == null) {
				throw new IllegalArgumentException("-c is required to specify the configuration file");
			}
			startClient(config, shost, sport, poff);
			break;
		}
	}
}
