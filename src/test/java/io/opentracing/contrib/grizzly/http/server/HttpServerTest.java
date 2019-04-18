package io.opentracing.contrib.grizzly.http.server;

import io.opentracing.contrib.specialagent.AgentRunner;
import io.opentracing.contrib.specialagent.AgentRunner.Config.Log;
import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;
import io.opentracing.util.GlobalTracer;
import org.glassfish.grizzly.http.HttpContent;
import org.glassfish.grizzly.http.HttpPacket;
import org.glassfish.grizzly.http.HttpResponsePacket;
import org.glassfish.grizzly.http.server.*;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

import static org.glassfish.grizzly.http.server.NetworkListener.DEFAULT_NETWORK_HOST;
import static org.junit.Assert.assertEquals;

/**
 * @author Jose Montoya
 */
@RunWith(AgentRunner.class)
@AgentRunner.Config(log=Log.FINEST)
public class HttpServerTest extends AbstractHttpTest {
	private HttpServer httpServer;

	static {
	  try {
      Class.forName("org.glassfish.grizzly.filterchain.FilterChainBuilder$StatelessFilterChainBuilder");
    }
    catch (final ClassNotFoundException e) {
      throw new ExceptionInInitializerError(e);
    }
	}

	@BeforeClass
	public static void beforeClass(MockTracer tracer) throws Exception {
		GlobalTracer.register(tracer);
	}

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
