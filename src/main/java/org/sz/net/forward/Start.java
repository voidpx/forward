package org.sz.net.forward;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ServerSocketFactory;
import javax.net.SocketFactory;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import com.tencent.kona.crypto.CryptoInsts;
import com.tencent.kona.pkix.KonaPKIXProvider;
import com.tencent.kona.ssl.KonaSSLProvider;
import com.tencent.kona.sun.security.tools.keytool.Main;

import cn.ksh.crypto.api.Crypto;

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
			+ "commands:\n" 
			+ "	keytool <args>\n" 
			+ "	server -p <port> -a <certificate path> -n\n"
			+ "	client -p <server port> -c <config> -h <server host> -o <port offset> -a <certificate path> -n\n";
	
	private static void startServer(String config, int port, String certPath, boolean nossl) throws Exception {
		if (!certPath.endsWith("/")) {
			certPath += "/";
		}
		Properties p = new Properties();
		if (config != null) {
			p.load(new FileInputStream(Path.of(config).toFile()));
		}
		var allow = p.entrySet().stream().map(e -> Map.entry((String)e.getKey(), 
				Boolean.valueOf((String)e.getValue()))).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
		SSLContext ctx = createSSLContext(
				new KeyBundle[] { KeyBundle.fromFile("client",
						certPath + "certs/client.crt",
						certPath + "certs/client.key") },
				new KeyBundle[] { KeyBundle.fromFile("server",
						certPath + "certs/server.crt",
						certPath + "certs/server.key"), },
				new SSLParams("TLCP", "PKIX", "NewSunX509"));
		if (nossl) {
			new Server(ServerSocketFactory.getDefault(), port, allow).start();
		} else {
			new Server(ctx.getServerSocketFactory(), port, allow).start();
		}
		
		
	}
	
	private static void startClient(String config, String shost, int sport, int poff, String certPath, boolean nossl) throws Exception {
		Properties p = new Properties();
		if (config == null) {
			// take it from stdin, and decrypt
			byte[] in = System.in.readAllBytes();
			int idx = -1;
			for (int i = 0; i < in.length; i++) {
				if (in[i] == '\n') {
					idx = i;
					break;
				}
			}
			if (idx <= 0) {
				throw new IllegalArgumentException("Invalid config");
			}
			String pwd = new String(Arrays.copyOfRange(in, 0, idx), StandardCharsets.UTF_8);
			byte[] cfg = Arrays.copyOfRange(in, idx + 1, in.length);
			String dec = doDecryptAsString(pwd, new String(cfg, StandardCharsets.UTF_8));
			p.load(new StringReader(dec));
		} else {
			p.load(new FileInputStream(Path.of(config).toFile()));
		}
		if (!certPath.endsWith("/")) {
			certPath += "/";
		}
		SSLContext cctx = createSSLContext(
				new KeyBundle[] { KeyBundle.fromFile("server",
						certPath + "certs/server.crt",
						certPath + "certs/server.key") },
				new KeyBundle[] { KeyBundle.fromFile("client",
						certPath + "certs/client.crt",
						certPath + "certs/client.key"), },
				new SSLParams("TLCP", "TencentPKIX", "PKIX"));
		if (nossl) {
			new Client(SocketFactory.getDefault(), p, shost, sport, poff).start();
		} else {
			new Client(cctx.getSocketFactory(), p, shost, sport, poff).start();
		}
		
	}
	
	private static void errorOut() {
		System.out.println(USAGE);
		System.exit(1);
	}
	
	static class Options {
		List<Option> opts;
		Options(List<Option> opts) {
			this.opts = opts;
		}
		static Options parse(String[] cmdline, Option... opts) {
			if (opts == null || cmdline == null) {
				return new Options(Collections.emptyList());
			}
			List<Option> all = new ArrayList<>(Arrays.asList(opts));
			List<Option> ret = new ArrayList<>();
			for (int i = 0; i < cmdline.length; i++) {
				for (Option o : opts) {
					if (cmdline[i].equals(o.opt)) {
						if (o.requireArg) {
							if (i < cmdline.length - 1) {
								o.arg = cmdline[i+1];
								i+=1;
							} else {
								throw new IllegalArgumentException("option " + o.opt + " requires an argument");
							}
						}
						ret.add(o);
						all.remove(o);
					}
				}
			}
			for (Option o : all) {
				if (o.required) {
					throw new IllegalArgumentException("required option " + o.opt + " not provided");
				}
			}
			return new Options(ret);
		}
		
		boolean present(Option o) {
			return opts.stream().filter(a -> a == o).findAny().isPresent();
		}
		
	}
	
	static enum Option {
		p("-p", true, true),
		c("-c", true, true),
		h("-h", true, true),
		n("-n", false, false),
		o("-o", false, true),
		a("-a", false, true);
		String opt;
		boolean required;
		boolean requireArg;
		String arg;
		private Option(String opt, boolean required, boolean requireArg) {
			this.opt = opt;
			this.requireArg = requireArg;
		}
		
		@SuppressWarnings("unchecked")
		<T> T getArg(Class<T> type) {
			if (type == Integer.class) {
				return (T) Integer.valueOf(arg);
			}
			return (T) arg;
		}
		
	}
	
	private static final  int iterations = 65536; // Higher = slower but more secure
    private static final  int keyLength = 128; // bits
    private static final  int ivLen = 12;

    private static byte[] genSalt() {
    	byte[] salt = new byte[keyLength/8];
        SecureRandom random = new SecureRandom();
        random.nextBytes(salt);
        return salt;
    }
    
    private static byte[] deriveKey(String pass, byte[] salt) throws NoSuchAlgorithmException, InvalidKeySpecException {
        PBEKeySpec spec = new PBEKeySpec(pass.toCharArray(), salt, iterations, keyLength);
        
        // 4. Generate the key using PBKDF2WithHmacSHA256
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        byte[] keyBytes = factory.generateSecret(spec).getEncoded();
        
        SecretKey secretKey = new SecretKeySpec(keyBytes, "sm4");
        return secretKey.getEncoded();
    }
    
    private static String doEncryptFile(String pass, String cfgPath) throws NoSuchAlgorithmException, InvalidKeySpecException, IOException {
    	return doEncrypt(pass, Files.readAllBytes(Path.of(cfgPath)));
    }

	private static String doEncrypt(String pass, byte[] cfg) throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
		Crypto c = Crypto.getInstance("sm4", null);
		byte[] salt = genSalt();
		byte[] key = deriveKey(pass, salt);
		byte[] iv = Arrays.copyOfRange(salt, 0, ivLen); // iv len: 12
		byte[] enc = c.encrypt(key, iv, cfg);
	
		byte[] buf = new byte[salt.length + enc.length];
		System.arraycopy(salt, 0, buf, 0, salt.length);
		System.arraycopy(enc, 0, buf, salt.length, enc.length);
		String out = Base64.getEncoder().encodeToString(buf);
		return out;
	}
	
	private static byte[] doDecrypt(String pass, String encrypted) throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
		Crypto c = Crypto.getInstance("sm4", null);
		byte[] dec = Base64.getDecoder().decode(encrypted);
		byte[] salt = Arrays.copyOfRange(dec, 0, keyLength/8);
		byte[] iv = Arrays.copyOfRange(salt, 0, ivLen);
		byte[] data = Arrays.copyOfRange(dec, salt.length, dec.length);
		byte[] key = deriveKey(pass, salt);
		byte[] cfg = c.decrypt(key, iv, data);
		return cfg;
	}
	
	private static String doDecryptAsString(String pass, String encrypted) throws NoSuchAlgorithmException, InvalidKeySpecException, IOException {
		return new String(doDecrypt(pass, encrypted), StandardCharsets.UTF_8);
	}

	public static void main(String[] args) throws Exception {
		if (args.length == 0) {
			errorOut();
		}
		String cmd = args[0];
		String certPath = "./";
		String[] options = Arrays.copyOfRange(args, 1, args.length);
		switch (cmd) {
		case "encrypt":
			Option.c.required = true;
			Options.parse(options, Option.p, Option.c);
			System.out.println(doEncryptFile(Option.p.arg, Option.c.arg));
			break;
		case "decrypt":
			Option.c.required = true;
			Options.parse(options, Option.p, Option.c);
			System.out.println(doDecryptAsString(Option.p.arg, Option.c.arg));
			break;
		case "keytool":
			System.out.println("args: " + String.join(" ", args));
			Main.main(args);
			break;
		case "server":
			int port = 8888;
			Option.p.required = false;
			Options opts = Options.parse(options, Option.p, Option.a, Option.n, Option.c);
			if (opts.present(Option.p)) {
				port = Option.p.getArg(Integer.class);
			}
			if (opts.present(Option.a)) {
				certPath = Option.a.arg;
			}
			startServer(Option.c.arg, port, certPath, opts.present(Option.n));
			break;
		case "client":
			int sport = 8888;
			int poff = 0;
			Option.p.required = false;
			Option.c.required = false;
			Options copts = Options.parse(options, Option.p, Option.h, Option.o, Option.a, Option.n, Option.c);
			if (copts.present(Option.p)) {
				sport = Option.p.getArg(Integer.class);
			}
			if (copts.present(Option.o)) {
				poff = Option.o.getArg(Integer.class);
			}
			if (copts.present(Option.a)) {
				certPath = Option.a.arg;
			}
			startClient(Option.c.arg, Option.h.arg, sport, poff, certPath, copts.present(Option.n));
			break;
		default:
			errorOut();
		}
	}
}
