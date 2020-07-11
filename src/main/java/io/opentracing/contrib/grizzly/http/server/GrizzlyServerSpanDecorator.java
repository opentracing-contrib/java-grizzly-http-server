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

import io.opentracing.Span;
import io.opentracing.tag.Tags;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.http.HttpResponsePacket;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;

public interface GrizzlyServerSpanDecorator {
    void onRequest(HttpRequestPacket request, Span span);
    void onResponse(HttpResponsePacket response, Span span);
    void onError(Throwable thrown, Span span);

    GrizzlyServerSpanDecorator STANDARD_TAGS = new GrizzlyServerSpanDecorator() {
        @Override
        public void onRequest(HttpRequestPacket request, Span span) {
            Tags.COMPONENT.set(span, "java-grizzly-http-server");
            Tags.HTTP_METHOD.set(span, request.getMethod().getMethodString());
            Tags.HTTP_URL.set(span, getUri(request));
            Tags.SPAN_KIND.set(span, Tags.SPAN_KIND_SERVER);
        }

        @Override
        public void onResponse(HttpResponsePacket response, Span span) {
            Tags.HTTP_STATUS.set(span, response.getStatus());
        }

        @Override
        public void onError(Throwable thrown, Span span) {
            final HashMap<String,Object> errorLogs = new HashMap<>(2);
            errorLogs.put("event", Tags.ERROR.getKey());
            errorLogs.put("error.object", thrown);
            span.setTag(Tags.ERROR, Boolean.TRUE);
            span.log(errorLogs);
        }

        protected String getUri(HttpRequestPacket request) {
            try {
                return new URI(
                        (request.isSecure() ? "https://" : "http://")
                                + request.getRemoteHost()
                                + ":"
                                + request.getLocalPort()
                                + request.getRequestURI()
                                + (request.getQueryString() != null ? "?" + request.getQueryString() : ""))
                        .toString();
            } catch (URISyntaxException e) {
                // shouldn't happen
                e.printStackTrace();
                return request.getRequestURI();
            }
        }
    };
}
