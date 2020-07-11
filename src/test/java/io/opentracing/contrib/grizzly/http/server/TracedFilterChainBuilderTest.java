/*
 * Copyright 2018 The OpenTracing Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.opentracing.contrib.grizzly.http.server;

import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.Response;
import io.opentracing.Scope;
import io.opentracing.Span;
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
import java.net.URL;
import java.util.List;
import java.util.concurrent.ExecutorService;
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
		GlobalTracer.registerIfAbsent(tracer);
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

		Response response;

		try (AsyncHttpClient client = new AsyncHttpClient()) {
			response = client.prepareGet(new URL("http", LOCALHOST, PORT, "/").toString()).execute().get();
		}

		assertEquals(200, response.getStatusCode());

		List<MockSpan> spans = tracer.finishedSpans();
		assertEquals(1, spans.size());

		MockSpan mockSpan = spans.get(0);
		assertEquals("HTTP::GET", mockSpan.operationName());
		assertEquals(5, mockSpan.tags().size());
		assertEquals(Tags.SPAN_KIND_SERVER, mockSpan.tags().get(Tags.SPAN_KIND.getKey()));
		assertEquals("GET", mockSpan.tags().get(Tags.HTTP_METHOD.getKey()));
		assertEquals("http://localhost:18906/", mockSpan.tags().get(Tags.HTTP_URL.getKey()));
		assertEquals(200, mockSpan.tags().get(Tags.HTTP_STATUS.getKey()));
		assertEquals("java-grizzly-http-server", mockSpan.tags().get(Tags.COMPONENT.getKey()));
	}

	@Test
	public void testSyncResponseWithChild() throws Exception {
		setupServer(new Function<FilterChainContext, NextAction>() {
			@Override
			public NextAction apply(FilterChainContext ctx) {
				Span span = tracer.buildSpan("child").start();
				Scope scope = tracer.scopeManager().activate(span);

				writeEmptyResponse(ctx);

				span.finish();
				scope.close();

				return ctx.getStopAction();
			}
		});

		Response response;

		try (AsyncHttpClient client = new AsyncHttpClient()) {
			response = client.prepareGet(new URL("http", LOCALHOST, PORT, "/").toString()).execute().get();
		}

		assertEquals(200, response.getStatusCode());

		List<MockSpan> spans = tracer.finishedSpans();
		assertEquals(2, spans.size());

		assertEquals(spans.get(0).context().traceId(), spans.get(1).context().traceId());
		assertEquals(spans.get(0).operationName(), "child");
		assertEquals(spans.get(0).parentId(), spans.get(1).context().spanId());
	}

	@Test
	public void testAsyncResponseWithChild() throws Exception {
		final ExecutorService executorService = new TracedExecutorService(Executors.newFixedThreadPool(2), tracer);

		setupServer(new Function<FilterChainContext, NextAction>() {
			@Override
			public NextAction apply(FilterChainContext ctx) {
				executorService.submit(new Runnable() {
					@Override
					public void run() {
						Span span = tracer.buildSpan("async-child").start();
						Scope scope = tracer.scopeManager().activate(span);

						try {
							System.out.println("runnable " + Thread.currentThread().getName());
							Thread.sleep(200);
							writeEmptyResponse(ctx);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}

						span.finish();
						scope.close();

						ctx.resume(ctx.getStopAction());
					}
				});

				return ctx.getSuspendAction();
			}
		});

		Response response;

		try (AsyncHttpClient client = new AsyncHttpClient()) {
			response = client.prepareGet(new URL("http", LOCALHOST, PORT, "/").toString()).execute().get();
		}

		assertEquals(200, response.getStatusCode());

		List<MockSpan> spans = tracer.finishedSpans();
		assertEquals(2, spans.size());

		assertEquals(spans.get(0).context().traceId(), spans.get(1).context().traceId());
		assertEquals(spans.get(0).operationName(), "async-child");
		assertEquals(spans.get(0).parentId(), spans.get(1).context().spanId());
	}

	private void setupServer(Function<FilterChainContext, NextAction> nextActionSupplier) throws Exception {
		// Create a FilterChain using TracedFilterChainBuilder
		FilterChainBuilder filterChainBuilder = new TracedFilterChainBuilder(tracer);

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
