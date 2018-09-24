package io.opentracing.contrib.grizzly.http.server;

import io.opentracing.propagation.TextMap;
import org.glassfish.grizzly.http.HttpRequestPacket;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * @author Jose Montoya
 */
public class GizzlyHttpRequestPacketAdapter implements TextMap {
	private final HttpRequestPacket requestPacket;
	private final Map<String, String> headers;

	public GizzlyHttpRequestPacketAdapter(HttpRequestPacket requestPacket) {
		this.requestPacket = requestPacket;
		this.headers = new HashMap<>(requestPacket.getHeaders().size());
		for (String headerName : requestPacket.getHeaders().names()) {
			headers.put(headerName, requestPacket.getHeaders().getHeader(headerName));
		}
	}

	@Override
	public Iterator<Map.Entry<String, String>> iterator() {
		return headers.entrySet().iterator();
	}

	@Override
	public void put(String key, String value) {
		requestPacket.addHeader(key, value);
	}
}
