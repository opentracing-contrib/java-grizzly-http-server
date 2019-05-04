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

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import org.glassfish.grizzly.filterchain.FilterChain;
import org.glassfish.grizzly.filterchain.FilterChainBuilder;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.http.HttpServerFilter;

import io.opentracing.Span;
import io.opentracing.Tracer;

/**
 * @author Jose Montoya
 */
public class TracedFilterChainBuilder extends FilterChainBuilder.StatelessFilterChainBuilder {
  private final Tracer tracer;

  public TracedFilterChainBuilder(final FilterChainBuilder builder, final Tracer tracer) {
    this.tracer = tracer;
    addAll(builder);
  }

  public TracedFilterChainBuilder(final Tracer tracer) {
    this.tracer = tracer;
  }

  @Override
  public FilterChain build() {
    final int httpServerFilterIdx = this.indexOfType(HttpServerFilter.class);
    if (httpServerFilterIdx != -1) {
      // If contains an HttpServerFilter
      addTracingFiltersAt(httpServerFilterIdx + 1);
      return super.build();
    }

    // This must be an http client FilterChain, or a non http chain, or some
    // other unsupported setup
    return super.build();
  }

  // Methods to facilitate custom instrumentation
  private void addTracingFiltersAt(final int index) {
    final Map<HttpRequestPacket,Span> weakRequestMap = Collections.synchronizedMap(new WeakHashMap<HttpRequestPacket,Span>());

    final TracingResponseHttpServerFilter responseFilter = new TracingResponseHttpServerFilter(weakRequestMap, tracer);
    final TracingRequestHttpServerFilter requestFilter = new TracingRequestHttpServerFilter(patternFilterChain.get(index), weakRequestMap, tracer);

    patternFilterChain.remove(index);
    patternFilterChain.add(index, requestFilter);
    patternFilterChain.add(index, responseFilter);
  }
}