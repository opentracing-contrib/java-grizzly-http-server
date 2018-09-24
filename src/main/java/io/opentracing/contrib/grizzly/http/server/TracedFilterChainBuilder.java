package io.opentracing.contrib.grizzly.http.server;

import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.util.GlobalTracer;
import org.glassfish.grizzly.filterchain.Filter;
import org.glassfish.grizzly.filterchain.FilterChain;
import org.glassfish.grizzly.filterchain.FilterChainBuilder;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.http.HttpServerFilter;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * @author Jose Montoya
 */
public class TracedFilterChainBuilder extends FilterChainBuilder.StatelessFilterChainBuilder {
	private final Tracer tracer;

	public TracedFilterChainBuilder(Tracer tracer) {
		this.tracer = tracer;
	}

	public TracedFilterChainBuilder() {
		this.tracer = GlobalTracer.get();
	}

	@Override
	public FilterChain build() {
		int httpServerFilterIdx = this.indexOfType(HttpServerFilter.class);
		if (httpServerFilterIdx != -1) {
			// If contains an HttpServerFilter
			addTracingFiltersAt(httpServerFilterIdx + 1, patternFilterChain, tracer);
			return super.build();
		}

		// This must be an http client FilterChain, or a non http chain, or some other unsupported setup
		return super.build();
	}

	// Methods to facilitate Byteman instrumentation
	private static void addTracingFiltersAt(int index, List<Filter> filterList, Tracer tracer1) {
		Map<HttpRequestPacket, Span> weakRequestMap = Collections.synchronizedMap(new WeakHashMap<HttpRequestPacket, Span>());

		TracingResponseHttpServerFilter responseFilter = new TracingResponseHttpServerFilter(weakRequestMap, tracer1);
		TracingRequestHttpServerFilter requestFilter = new TracingRequestHttpServerFilter(filterList.get(index), weakRequestMap, tracer1);

		filterList.remove(index);
		filterList.add(index, requestFilter);
		filterList.add(index, responseFilter);
	}

	public static FilterChain buildFrom(FilterChainBuilder builder, Tracer tracer2) {
		return new TracedFilterChainBuilder(tracer2).addAll(builder).build();
	}
}