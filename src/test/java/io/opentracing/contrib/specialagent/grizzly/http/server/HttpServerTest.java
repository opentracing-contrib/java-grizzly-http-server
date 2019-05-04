package io.opentracing.contrib.specialagent.grizzly.http.server;

import static org.glassfish.grizzly.http.server.NetworkListener.*;
import static org.junit.Assert.*;

import java.util.List;

import io.opentracing.contrib.grizzly.http.server.AbstractHttpTest;
import org.glassfish.grizzly.http.HttpContent;
import org.glassfish.grizzly.http.HttpPacket;
import org.glassfish.grizzly.http.HttpResponsePacket;
import org.glassfish.grizzly.http.server.HttpHandler;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.grizzly.http.server.NetworkListener;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.server.Response;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.opentracing.contrib.specialagent.AgentRunner;
import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;

/**
 * @author Jose Montoya
 */
@RunWith(AgentRunner.class)
public class HttpServerTest extends AbstractHttpTest {
	private HttpServer httpServer;

	@Before
	public void before(MockTracer tracer) throws Exception {
		// clear traces
		tracer.reset();

		httpServer = new HttpServer();
		NetworkListener listener = new NetworkListener("grizzly", DEFAULT_NETWORK_HOST, PORT);
		httpServer.addListener(listener);
		httpServer.start();
	}

	@After
	public void after() throws Exception {
		if (httpServer != null) {
			httpServer.shutdownNow();
		}
	}

	@Test
	public void testSyncResponse(MockTracer tracer) throws Exception {
		httpServer.getServerConfiguration().addHttpHandler(new HttpHandler() {
			@Override
			public void service(Request request, Response response) throws Exception {
				response.setStatus(201);
			}
		});

		HttpPacket request = createRequest("/", null);
		HttpContent responseContent = send(request, httpServer.getListener("grizzly").getTransport());

		assertEquals(201, ((HttpResponsePacket) responseContent.getHttpHeader()).getStatus());

		List<MockSpan> spans = tracer.finishedSpans();
		assertEquals(1, spans.size());
	}
}
