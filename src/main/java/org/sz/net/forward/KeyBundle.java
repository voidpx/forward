package org.sz.net.forward;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class KeyBundle {
    private static final String BEGIN = "-----BEGIN";
    private static final String END = "-----END";
	String name;
	String certificate;
	String privKey;
	public static KeyBundle fromFile(String name, String certFile, String keyFile) throws IOException {
		String cert = filterPem(Files.readAllLines(Path.of(certFile)), true);
		String key = filterPem(Files.readAllLines(Path.of(keyFile)), false);
		return new KeyBundle(name, cert, key);
	}
	
    private static String filterPem(List<String> lines, boolean keepSeparator) {
        StringBuilder result = new StringBuilder();

        boolean begin = false;
        for (String line : lines) {
            if (line.startsWith(END)) {
                if (keepSeparator) {
                    result.append(line);
                }
                break;
            }

            if (line.startsWith(BEGIN)) {
                begin = true;
                if (keepSeparator) {
                    result.append(line).append("\n");
                }
                continue;
            }

            if (begin) {
                result.append(line).append("\n");
            }
        }

        return result.toString();
    }
}
