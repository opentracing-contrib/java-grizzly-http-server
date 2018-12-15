# OpenTracing Grizzly HTTP Server Instrumentation
OpenTracing instrumentation for Grizzly HTTP Server.

## OpenTracing Agents
When using a runtime agent like [java-agent](https://github.com/opentracing-contrib/java-agent) or [java-specialagent](https://github.com/opentracing-contrib/java-specialagent) `TCPNIOTransport`s will be automatically instrumented as long as their `FilterChain` contains an `HttpServerFilter`. This is the case with the plain `HttpServer` through its constructor or `HttpServer.createSimpleServer` static factory methods:

```java
HttpServer httpServer = new HttpServer();
NetworkListener listener = new NetworkListener("grizzly", "localhost", "8080");
httpServer.addListener(listener);
httpServer.start();
```
or
```java
HttpServer httpServer = HttpServer.createSimpleServer();
httpServer.start();
```
Alternatively, chains created directly will also be instrumented automatically under the same condition:
```java
FilterChainBuilder filterChainBuilder = FilterChainBuilder.stateless();

filterChainBuilder.add(new TransportFilter());
filterChainBuilder.add(new HttpServerFilter());
filterChainBuilder.add(new SomeWorkerFilter());

TCPNIOTransport transport = TCPNIOTransportBuilder.newInstance().build();
transport.setProcessor(filterChainBuilder.build());
transport.bind("localhost", "8080");
transport.start();
```
Refer to the agents' documentation for how to include this library as an instrumentation plugin.

## Non-Agent Configuration
When not using any of the OpenTracing Agents the traced filter chain instance must be instantiated directly. Use of the plain `HttpServer` without a runtime Agent is not currently supported.

```java
FilterChainBuilder filterChainBuilder = new TracedFilterChainBuilder();

filterChainBuilder.add(new TransportFilter());
filterChainBuilder.add(new HttpServerFilter());
filterChainBuilder.add(new SomeWorkFilter());
...
```