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

import org.glassfish.grizzly.filterchain.BaseFilter;
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
  private Class<? extends BaseFilter> toWrapType = HttpServerFilter.class;
  private int toWrapIdx = -1;

  public TracedFilterChainBuilder(final FilterChainBuilder builder, final Tracer tracer) {
    this.tracer = tracer;
    addAll(builder);
  }

  public TracedFilterChainBuilder(final Tracer tracer) {
    this.tracer = tracer;
  }


  /**
   * Utilize this method to customize the TracedFilterChainBuilder by specifying the class of the
   * filter that should be intercepted instead of the default HttpServerFilter.
   *
   * @param toWrap the class of the filter to be intercepted
   * @return the same chain builder to provide a fluent api
   */
  public TracedFilterChainBuilder wrapping(Class<? extends BaseFilter> toWrap) {
    this.toWrapType = toWrap;
    return this;
  }

  /**
   * Utilize this method to customize the TracedFilterChainBuilder by specifying the index of the
   * filter that should be intercepted.
   *
   * @param toWrapIdx index of the filter to intercept
   * @return the same chain builder to provide a fluent api
   */
  public TracedFilterChainBuilder wrapping(int toWrapIdx) {
    this.toWrapIdx = toWrapIdx;
    return this;
  }

  @Override
  public FilterChain build() {
    if (toWrapIdx == -1) {
      toWrapIdx = this.indexOfType(toWrapType);

      // If no appropriate filter found we give up
      if (toWrapIdx == -1) {
        return super.build();
      }
    }

    toWrapIdx++;
    final Map<HttpRequestPacket, Span> weakRequestMap = Collections.synchronizedMap(new WeakHashMap<HttpRequestPacket, Span>());
    final TracingResponseHttpServerFilter responseFilter = new TracingResponseHttpServerFilter(weakRequestMap, tracer);
    final TracingRequestHttpServerFilter requestFilter = new TracingRequestHttpServerFilter(patternFilterChain.get(toWrapIdx), weakRequestMap, tracer);

    patternFilterChain.remove(toWrapIdx);
    patternFilterChain.add(toWrapIdx, requestFilter);
    patternFilterChain.add(toWrapIdx, responseFilter);
    return super.build();
  }
}
