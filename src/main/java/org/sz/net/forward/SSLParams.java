package org.sz.net.forward;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SSLParams {
	String proto;
	String trustManagerAlg;
	String keyManagerAlg;
}
