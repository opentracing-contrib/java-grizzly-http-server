package io.opentracing.contrib.grizzly.http.server;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.SocketConnectorHandler;
import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.FilterChainBuilder;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.filterchain.TransportFilter;
import org.glassfish.grizzly.http.HttpClientFilter;
import org.glassfish.grizzly.http.HttpContent;
import org.glassfish.grizzly.http.HttpPacket;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.http.Method;
import org.glassfish.grizzly.http.Protocol;
import org.glassfish.grizzly.impl.FutureImpl;
import org.glassfish.grizzly.impl.SafeFutureImpl;
import org.glassfish.grizzly.nio.transport.TCPNIOConnectorHandler;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;

/**
 * @author Jose Montoya
 */
abstract class AbstractHttpTest {
	static final int PORT = 18906;
	static final String LOCALHOST = "localhost";

	HttpPacket createRequest(String uri, Map<String, String> headers) {
		HttpRequestPacket.Builder b = HttpRequestPacket.builder();
		b.method(Method.GET).protocol(Protocol.HTTP_1_1).uri(uri).header("Host", LOCALHOST);
		if (headers != null) {
			for (Map.Entry<String, String> entry : headers.entrySet()) {
				b.header(entry.getKey(), entry.getValue());
			}
		}

		return b.build();
	}

	HttpContent send(HttpPacket request, TCPNIOTransport transport) throws Exception {
		FutureImpl<HttpContent> testResultFuture = SafeFutureImpl.create();

		FilterChainBuilder clientFilterChainBuilder = FilterChainBuilder.stateless();
		clientFilterChainBuilder.add(new TransportFilter());
		clientFilterChainBuilder.add(new HttpClientFilter());
		clientFilterChainBuilder.add(new ClientFilter(testResultFuture));

		SocketConnectorHandler connectorHandler = TCPNIOConnectorHandler.builder(transport)
				.processor(clientFilterChainBuilder.build())
				.build();

		Future<Connection> connectFuture = connectorHandler.connect(LOCALHOST, PORT);
		Connection connection = null;

		try {
			connection = connectFuture.get(10, TimeUnit.SECONDS);
			connection.write(request);
			return testResultFuture.get(10, TimeUnit.SECONDS);
		} finally {
			// Close the client connection
			if (connection != null) {
				connection.closeSilently();
			}
		}
	}

	static class ClientFilter extends BaseFilter {
		private final FutureImpl<HttpContent> future;

		public ClientFilter(FutureImpl<HttpContent> future) {
			this.future = future;
		}

		@Override
		public NextAction handleRead(FilterChainContext ctx) throws IOException {
			final HttpContent content = ctx.getMessage();
			try {
				if (!content.isLast()) {
					return ctx.getStopAction(content);
				}

				future.result(content);
			} catch (Exception e) {
				future.failure(e);
				e.printStackTrace();
			}

			return ctx.getStopAction();
		}
	}
}
