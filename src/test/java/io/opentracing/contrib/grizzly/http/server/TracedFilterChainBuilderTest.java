package io.opentracing.contrib.grizzly.http.server;

import io.opentracing.Scope;
import io.opentracing.contrib.concurrent.TracedExecutorService;
import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import io.opentracing.util.ThreadLocalScopeManager;
import org.glassfish.grizzly.filterchain.*;
import org.glassfish.grizzly.http.*;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.glassfish.grizzly.nio.transport.TCPNIOTransportBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.function.Function;

import static org.junit.Assert.assertEquals;

/**
 * @author Jose Montoya
 */
public class TracedFilterChainBuilderTest extends AbstractHttpTest {
	private TCPNIOTransport transport;
	protected static final MockTracer tracer = new MockTracer(new ThreadLocalScopeManager());

	@BeforeClass
	public static void beforeClass() throws Exception {
		GlobalTracer.register(tracer);
	}

	@Before
	public void before() throws Exception{
		// clear traces
		tracer.reset();
	}
	@After
	public void after() throws Exception {
		// stop the transport
		transport.shutdownNow();
	}

	@Test
	public void testSyncResponse() throws Exception {
		setupServer(new Function<FilterChainContext, NextAction>() {
			@Override
			public NextAction apply(FilterChainContext ctx) {
				writeEmptyResponse(ctx);

				return ctx.getStopAction();
			}
		});

		HttpPacket request = createRequest("/", null);
		HttpContent responseContent = send(request, transport);

		HttpResponsePacket responsePacket = (HttpResponsePacket) responseContent.getHttpHeader();
		assertEquals(200, responsePacket.getStatus());

		List<MockSpan> spans = tracer.finishedSpans();
		assertEquals(1, spans.size());

		MockSpan mockSpan = spans.get(0);
		assertEquals("HTTP::GET", mockSpan.operationName());
		assertEquals(5, mockSpan.tags().size());
		assertEquals(Tags.SPAN_KIND_SERVER, mockSpan.tags().get(Tags.SPAN_KIND.getKey()));
		assertEquals("GET", mockSpan.tags().get(Tags.HTTP_METHOD.getKey()));
		assertEquals("/", mockSpan.tags().get(Tags.HTTP_URL.getKey()));
		assertEquals(200, mockSpan.tags().get(Tags.HTTP_STATUS.getKey()));
		assertEquals("java-grizzly-http-server", mockSpan.tags().get(Tags.COMPONENT.getKey()));
	}

	@Test
	public void testSyncResponseWithChild() throws Exception {
		setupServer(new Function<FilterChainContext, NextAction>() {
			@Override
			public NextAction apply(FilterChainContext ctx) {
				Scope scope = tracer.buildSpan("child").startActive(true);
				writeEmptyResponse(ctx);
				scope.close();

				return ctx.getStopAction();
			}
		});

		HttpPacket request = createRequest("/", null);
		HttpContent responseContent = send(request, transport);

		HttpResponsePacket responsePacket = (HttpResponsePacket) responseContent.getHttpHeader();
		assertEquals(200, responsePacket.getStatus());

		List<MockSpan> spans = tracer.finishedSpans();
		assertEquals(2, spans.size());

		assertEquals(spans.get(0).context().traceId(), spans.get(1).context().traceId());
		assertEquals(spans.get(0).parentId(), spans.get(1).context().spanId());
	}

	@Test
	public void testAsyncResponseWithChild() throws Exception {
		setupServer(new Function<FilterChainContext, NextAction>() {
			@Override
			public NextAction apply(FilterChainContext ctx) {
				new TracedExecutorService(Executors.newFixedThreadPool(2), tracer).submit(new Runnable() {
					@Override
					public void run() {
						Scope scope = tracer.buildSpan("child").startActive(true);
						try {
							Thread.sleep(200);
							writeEmptyResponse(ctx);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
						scope.close();

						ctx.resume(ctx.getStopAction());
					}
				});

				return ctx.getSuspendAction();
			}
		});

		HttpPacket request = createRequest("/", null);
		HttpContent responseContent = send(request, transport);

		HttpResponsePacket responsePacket = (HttpResponsePacket) responseContent.getHttpHeader();
		assertEquals(200, responsePacket.getStatus());

		List<MockSpan> spans = tracer.finishedSpans();
		assertEquals(2, spans.size());

		assertEquals(spans.get(0).context().traceId(), spans.get(1).context().traceId());
		assertEquals(spans.get(0).parentId(), spans.get(1).context().spanId());
	}

	private void setupServer(Function<FilterChainContext, NextAction> nextActionSupplier) throws Exception {
		// Create a FilterChain using TracedFilterChainBuilder
		FilterChainBuilder filterChainBuilder = new TracedFilterChainBuilder(GlobalTracer.get());

		// Add TransportFilter, which is responsible
		// for reading and writing data to the connection
		filterChainBuilder.add(new TransportFilter());
		filterChainBuilder.add(new HttpServerFilter());
		filterChainBuilder.add(new BaseFilter() {
			@Override
			public NextAction handleRead(FilterChainContext ctx) throws IOException {
				if (ctx.getMessage() instanceof HttpContent) {
					return nextActionSupplier.apply(ctx);
				}

				return ctx.getStopAction();
			}
		});

		// Create TCP transport
		transport = TCPNIOTransportBuilder.newInstance().build();
		transport.setProcessor(filterChainBuilder.build());

		// binding transport to start listen on certain host and port
		transport.bind(LOCALHOST, PORT);

		// start the transport
		transport.start();
	}

	private void writeEmptyResponse(FilterChainContext ctx) {
		HttpContent httpContent = ctx.getMessage();
		HttpRequestPacket request = (HttpRequestPacket) httpContent.getHttpHeader();
		HttpResponsePacket responsePacket = HttpResponsePacket.builder(request)
				.status(200)
				.reasonPhrase("OK")
				.build();

		ctx.write(HttpContent.builder(responsePacket)
				.content(null)
				.last(true)
				.build());
	}
}
