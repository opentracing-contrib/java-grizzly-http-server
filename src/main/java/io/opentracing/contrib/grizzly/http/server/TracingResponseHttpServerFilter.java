package io.opentracing.contrib.grizzly.http.server;

import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.tag.Tags;
import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.http.HttpContent;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.http.HttpResponsePacket;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * @author Jose Montoya
 */
public class TracingResponseHttpServerFilter extends BaseFilter {
	private final Map<HttpRequestPacket, Span> weakRequestMap;
	private final Set<Span> tagged = Collections.newSetFromMap(new WeakHashMap<Span, Boolean>());
	protected Tracer tracer;

	/**
	 * @param weakRequestMap
	 * @param tracer
	 */
	public TracingResponseHttpServerFilter(Map<HttpRequestPacket, Span> weakRequestMap, Tracer tracer) {
		this.weakRequestMap = weakRequestMap;
		this.tracer = tracer;
	}


	@Override
	public NextAction handleWrite(FilterChainContext ctx) throws IOException {
		if (ctx.getMessage() instanceof HttpContent) {
			final HttpContent httpContent = ctx.getMessage();
			final HttpResponsePacket response = (HttpResponsePacket) httpContent.getHttpHeader();
			Span toTag = weakRequestMap.get(response.getRequest());
			if (toTag != null && ! tagged.contains(toTag)) {
				// If we have not already set appropriate response tags
				Tags.HTTP_STATUS.set(toTag, response.getStatus());

				tagged.add(toTag);
			}
		}
		return super.handleWrite(ctx);
	}
}