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

import java.io.IOException;
import java.util.Map;

import io.opentracing.*;
import org.glassfish.grizzly.filterchain.Filter;
import org.glassfish.grizzly.filterchain.FilterChain;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.FilterChainEvent;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.http.HttpContent;
import org.glassfish.grizzly.http.HttpRequestPacket;

import io.opentracing.propagation.Format;
import io.opentracing.tag.Tags;

/**
 * @author Jose Montoya
 */
public class TracingRequestHttpServerFilter implements Filter {
	private final Map<HttpRequestPacket, Span> weakRequestMap;
	private final Filter delegate;
	protected Tracer tracer;

	public TracingRequestHttpServerFilter(Filter delegate, Map<HttpRequestPacket, Span> weakRequestMap, Tracer tracer) {
		this.weakRequestMap = weakRequestMap;
		this.delegate = delegate;
		this.tracer = tracer;
	}

	@Override
  public NextAction handleRead(final FilterChainContext ctx) throws IOException {
		if (ctx.getMessage() instanceof HttpContent) {
			final HttpContent httpContent = ctx.getMessage();
			final HttpRequestPacket request = (HttpRequestPacket) httpContent.getHttpHeader();
			if (! weakRequestMap.containsKey(request)) {
				// If we have not have already started a span for this request

				SpanContext extractedContext = tracer.extract(Format.Builtin.HTTP_HEADERS,
						new GizzlyHttpRequestPacketAdapter(request));
				final Span span = tracer.buildSpan("HTTP::" + request.getMethod().getMethodString())
						.ignoreActiveSpan()
						.asChildOf(extractedContext)
						.withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_SERVER)
						.start();

				final Scope scope = tracer.scopeManager().activate(span);

				Tags.COMPONENT.set(span, "java-grizzly-http-server");
				Tags.HTTP_METHOD.set(span, request.getMethod().getMethodString());
				Tags.HTTP_URL.set(span, request.getRequestURI());

				ctx.addCompletionListener(new FilterChainContext.CompletionListener() {
					@Override
					public void onComplete(FilterChainContext context) {
						span.finish();
						scope.close();
						weakRequestMap.remove(request);
					}
				});

				weakRequestMap.put(request, span);

				NextAction delegateNextAction = delegate.handleRead(ctx);
				if (delegateNextAction.equals(ctx.getSuspendAction())) {
					scope.close();
				}

				return delegateNextAction;
			}
		}
		return delegate.handleRead(ctx);
	}

	@Override
  public NextAction handleWrite(FilterChainContext ctx) throws IOException {
		return delegate.handleWrite(ctx);
	}

	@Override
  public void onAdded(FilterChain filterChain) {
		delegate.onAdded(filterChain);
	}

	@Override
  public void onFilterChainChanged(FilterChain filterChain) {
		delegate.onFilterChainChanged(filterChain);
	}

	@Override
  public void onRemoved(FilterChain filterChain) {
		delegate.onRemoved(filterChain);
	}

	@Override
  public NextAction handleConnect(FilterChainContext ctx) throws IOException {
		return delegate.handleConnect(ctx);
	}

	@Override
  public NextAction handleAccept(FilterChainContext ctx) throws IOException {
		return delegate.handleAccept(ctx);
	}

	@Override
  public NextAction handleEvent(FilterChainContext ctx, FilterChainEvent event) throws IOException {
		return delegate.handleEvent(ctx, event);
	}

	@Override
  public NextAction handleClose(FilterChainContext ctx) throws IOException {
		return delegate.handleClose(ctx);
	}

	@Override
  public void exceptionOccurred(FilterChainContext ctx, Throwable error) {
		delegate.exceptionOccurred(ctx, error);
	}
}
