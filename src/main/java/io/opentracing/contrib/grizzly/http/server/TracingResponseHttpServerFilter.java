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
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.http.HttpContent;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.http.HttpResponsePacket;

import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.tag.Tags;

/**
 * @author Jose Montoya
 */
public class TracingResponseHttpServerFilter extends BaseFilter {
	private final Map<HttpRequestPacket, Span> weakRequestMap;
	private final Set<Span> tagged = Collections.newSetFromMap(new WeakHashMap<Span, Boolean>());
	protected Tracer tracer;

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
