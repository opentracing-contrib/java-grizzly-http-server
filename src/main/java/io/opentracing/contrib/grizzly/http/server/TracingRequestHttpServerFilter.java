package io.opentracing.contrib.grizzly.http.server;

import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import io.opentracing.tag.Tags;
import org.glassfish.grizzly.filterchain.*;
import org.glassfish.grizzly.http.HttpContent;
import org.glassfish.grizzly.http.HttpRequestPacket;

import java.io.IOException;
import java.util.Map;

/**
 * @author Jose Montoya
 */
public class TracingRequestHttpServerFilter implements Filter {
	private final Map<HttpRequestPacket, Span> weakRequestMap;
	private final Filter delegate;
	protected Tracer tracer;

	/**
	 * @param weakRequestMap
	 * @param delegate
	 * @param tracer
	 */
	public TracingRequestHttpServerFilter(Filter delegate, Map<HttpRequestPacket, Span> weakRequestMap, Tracer tracer) {
		this.weakRequestMap = weakRequestMap;
		this.delegate = delegate;
		this.tracer = tracer;
	}

	public NextAction handleRead(final FilterChainContext ctx) throws IOException {
		if (ctx.getMessage() instanceof HttpContent) {
			final HttpContent httpContent = ctx.getMessage();
			final HttpRequestPacket request = (HttpRequestPacket) httpContent.getHttpHeader();
			if (! weakRequestMap.containsKey(request)) {
				// If we have not have already started a span for this request

				SpanContext extractedContext = tracer.extract(Format.Builtin.HTTP_HEADERS,
						new GizzlyHttpRequestPacketAdapter(request));
				final Scope scope = tracer.buildSpan("HTTP::" + request.getMethod().getMethodString())
						.ignoreActiveSpan()
						.asChildOf(extractedContext)
						.withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_SERVER)
						.startActive(false);

				Tags.COMPONENT.set(scope.span(), "java-grizzly-http-server");
				Tags.HTTP_METHOD.set(scope.span(), request.getMethod().getMethodString());
				Tags.HTTP_URL.set(scope.span(), request.getRequestURI());

				ctx.addCompletionListener(new FilterChainContext.CompletionListener() {
					@Override
					public void onComplete(FilterChainContext context) {
						scope.span().finish();
						scope.close();
						weakRequestMap.remove(request);
					}
				});

				weakRequestMap.put(request, scope.span());

				NextAction delegateNextAction = delegate.handleRead(ctx);
				if (delegateNextAction.equals(ctx.getSuspendAction())) {
					scope.close();
				}

				return delegateNextAction;
			}
		}
		return delegate.handleRead(ctx);
	}

	public NextAction handleWrite(FilterChainContext ctx) throws IOException {
		return delegate.handleWrite(ctx);
	}

	public void onAdded(FilterChain filterChain) {
		delegate.onAdded(filterChain);
	}

	public void onFilterChainChanged(FilterChain filterChain) {
		delegate.onFilterChainChanged(filterChain);
	}

	public void onRemoved(FilterChain filterChain) {
		delegate.onRemoved(filterChain);
	}

	public NextAction handleConnect(FilterChainContext ctx) throws IOException {
		return delegate.handleConnect(ctx);
	}

	public NextAction handleAccept(FilterChainContext ctx) throws IOException {
		return delegate.handleAccept(ctx);
	}

	public NextAction handleEvent(FilterChainContext ctx, FilterChainEvent event) throws IOException {
		return delegate.handleEvent(ctx, event);
	}

	public NextAction handleClose(FilterChainContext ctx) throws IOException {
		return delegate.handleClose(ctx);
	}

	public void exceptionOccurred(FilterChainContext ctx, Throwable error) {
		delegate.exceptionOccurred(ctx, error);
	}
}