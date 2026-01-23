package name.vloiseau.portfolio.graphql;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.jface.preference.IPreferenceStore;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.execution.SubscriptionExecutionStrategy;
import io.leangen.graphql.GraphQLSchemaGenerator;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import name.abuchen.portfolio.ui.PortfolioPlugin;
import name.abuchen.portfolio.ui.UIConstants;
import name.abuchen.portfolio.ui.editor.ClientInputFactory;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

public class GraphQLServerAddon
{
    private static final String DEFAULT_HOST = "0.0.0.0";
    private static final int DEFAULT_PORT = 7524;
    private static final String CORS_ALLOWED_ORIGIN = "capacitor://localhost";
    private static final String CORS_ALLOWED_ORIGIN_DEV = "http://localhost:5173";

    private final Gson gson = new Gson();

    @Inject
    private ClientInputFactory clientInputFactory;

    private HttpServer server;
    private ExecutorService executor;
    private GraphQL graphQL;

    @PostConstruct
    public void start()
    {
        GraphQLConfig config = GraphQLConfig.fromPreferences(PortfolioPlugin.getDefault().getPreferenceStore());
        if (!config.enabled())
            return;

        try
        {
            var schema = new GraphQLSchemaGenerator() //
                            .withOperationsFromSingleton(new PortfolioGraphQLQueries(clientInputFactory)) //
                            .generate();
            graphQL = GraphQL.newGraphQL(schema).subscriptionExecutionStrategy(new SubscriptionExecutionStrategy())
                            .build();

            server = HttpServer.create(new InetSocketAddress(config.host(), config.port()), 0);
            server.createContext("/graphql", this::handleRequest);
            server.createContext("/graphql/sse", this::handleGraphQlSse);
            server.createContext("/graphiql", this::handleGraphiQL);
            server.createContext("/healthcheck", this::handleHealthcheck);
            server.createContext("/", this::handleStatic);
            server.createContext("/assets", this::handleStatic);
            executor = Executors.newCachedThreadPool(r -> {
                Thread thread = new Thread(r, "PortfolioGraphQL");
                thread.setDaemon(true);
                return thread;
            });
            server.setExecutor(executor);
            server.start();
        }
        catch (IOException | RuntimeException e)
        {
			// System.err.println(e.getMessage());
			PortfolioPlugin.log(e);
        }
    }

    @PreDestroy
    public void stop()
    {
        if (server != null)
        {
            server.stop(0);
            server = null;
        }

        if (executor != null)
        {
            executor.shutdownNow();
            executor = null;
        }

        graphQL = null;
    }

    private void handleRequest(HttpExchange exchange) throws IOException
    {
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod()))
        {
            applyCorsHeaders(exchange);
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod()))
        {
            sendPlainText(exchange, 405, "Only POST is supported");
            return;
        }

        applyCorsHeaders(exchange);
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, Object> payload;
        try
        {
            payload = gson.fromJson(body, Map.class);
        }
        catch (JsonSyntaxException e)
        {
            sendPlainText(exchange, 400, "Invalid JSON payload");
            return;
        }

        if (payload == null || payload.get("query") == null)
        {
            sendPlainText(exchange, 400, "Missing GraphQL query");
            return;
        }

        String query = String.valueOf(payload.get("query"));
        String operationName = payload.get("operationName") != null
                        ? String.valueOf(payload.get("operationName"))
                        : null;

        Map<String, Object> variables = Collections.emptyMap();
        Object variablesRaw = payload.get("variables");
        if (variablesRaw instanceof Map<?, ?> raw)
        {
            @SuppressWarnings("unchecked")
            Map<String, Object> casted = (Map<String, Object>) raw;
            variables = casted;
        }

        ExecutionInput input = ExecutionInput.newExecutionInput() //
                        .query(query) //
                        .operationName(operationName) //
                        .variables(variables) //
                        .build();
        ExecutionResult result = graphQL.execute(input);

        String responseJson = gson.toJson(result.toSpecification());
        byte[] responseBytes = responseJson.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(200, responseBytes.length);
        try (OutputStream output = exchange.getResponseBody())
        {
            output.write(responseBytes);
        }
    }

    private void handleGraphiQL(HttpExchange exchange) throws IOException
    {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod()))
        {
            sendPlainText(exchange, 405, "Only GET is supported");
            return;
        }

        sendHtml(exchange, 200, graphiqlHtml());
    }

    private void handleGraphQlSse(HttpExchange exchange) throws IOException
    {
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod()))
        {
            applyCorsHeaders(exchange);
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())
                        && !"POST".equalsIgnoreCase(exchange.getRequestMethod()))
        {
            sendPlainText(exchange, 405, "Only GET/POST is supported");
            return;
        }

        if (graphQL == null)
        {
            sendPlainText(exchange, 500, "GraphQL not initialized");
            return;
        }

        GraphQlRequest request = parseGraphQlSseRequest(exchange);
        if (request == null || request.query() == null || request.query().isBlank())
        {
            sendPlainText(exchange, 400, "Missing GraphQL query");
            return;
        }

        ExecutionInput input = ExecutionInput.newExecutionInput() //
                        .query(request.query()) //
                        .operationName(request.operationName()) //
                        .variables(request.variables()) //
                        .build();
        ExecutionResult executionResult = graphQL.execute(input);
        Object data = executionResult.getData();
        if (!(data instanceof Publisher<?> publisher))
        {
            if (executionResult.getErrors().isEmpty())
            {
                sendPlainText(exchange, 400, "Query is not a subscription");
            }
            else
            {
                sendPlainText(exchange, 400, gson.toJson(executionResult.toSpecification()));
            }
            return;
        }

        applyCorsHeaders(exchange);
        exchange.getResponseHeaders().set("Content-Type", "text/event-stream; charset=UTF-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-cache");
        exchange.getResponseHeaders().set("Connection", "keep-alive");
        exchange.sendResponseHeaders(200, 0);

        var output = exchange.getResponseBody();
        var closed = new AtomicBoolean(false);
        var done = new CountDownLatch(1);
        var subscriptionRef = new AtomicReference<Subscription>();

        try
        {
            @SuppressWarnings("unchecked")
            Publisher<ExecutionResult> executionPublisher = (Publisher<ExecutionResult>) publisher;
            executionPublisher.subscribe(new Subscriber<ExecutionResult>()
            {
                @Override
                public void onSubscribe(Subscription subscription)
                {
                    subscriptionRef.set(subscription);
                    subscription.request(Long.MAX_VALUE);
                }

                @Override
                public void onNext(ExecutionResult result)
                {
                    if (closed.get())
                        return;
                    try
                    {
                        sendSseEvent(output, "next", gson.toJson(result.toSpecification()));
                    }
                    catch (IOException e)
                    {
                        closed.set(true);
                        Subscription current = subscriptionRef.get();
                        if (current != null)
                            current.cancel();
                    }
                }

                @Override
                public void onError(Throwable error)
                {
                    try
                    {
                        sendSseEvent(output, "next", gson.toJson(Map.of("errors",
                                        new Object[] { Map.of("message", error.getMessage()) })));
                    }
                    catch (IOException ignore)
                    {
                        // ignore
                    }
                    done.countDown();
                }

                @Override
                public void onComplete()
                {
                    done.countDown();
                }
            });
            done.await();
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
        }
        finally
        {
            try
            {
                sendSseEvent(output, "complete", null);
            }
            catch (IOException ignore)
            {
                // ignore
            }
            output.close();
        }
    }

    private void handleHealthcheck(HttpExchange exchange) throws IOException
    {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod()))
        {
            sendPlainText(exchange, 405, "Only GET is supported");
            return;
        }

        applyCorsHeaders(exchange);
        String version = PortfolioPlugin.getDefault().getBundle().getVersion().toString();
        sendPlainText(exchange, 200, version);
    }

    private void sendPlainText(HttpExchange exchange, int status, String message) throws IOException
    {
        byte[] responseBytes = message.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
        exchange.sendResponseHeaders(status, responseBytes.length);
        try (OutputStream output = exchange.getResponseBody())
        {
            output.write(responseBytes);
        }
    }

    private void handleStatic(HttpExchange exchange) throws IOException
    {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod()))
        {
            sendPlainText(exchange, 405, "Only GET is supported");
            return;
        }

        String path = exchange.getRequestURI().getPath();
        String subPath;
        if (path.startsWith("/ui"))
            subPath = path.substring(3);
        else if (path.startsWith("/assets"))
            subPath = path;
        else
            subPath = path;
        subPath = URLDecoder.decode(subPath, StandardCharsets.UTF_8);

        if (subPath.isEmpty() || "/".equals(subPath))
            subPath = "/index.html";

        if (subPath.contains(".."))
        {
            sendPlainText(exchange, 400, "Invalid path");
            return;
        }

        String resourcePath = "/web" + subPath;
        byte[] content = readResource(resourcePath);
        if (content == null)
        {
            content = readResource("/web/index.html");
            if (content == null)
            {
                sendPlainText(exchange, 404, "Not found");
                return;
            }
            resourcePath = "/web/index.html";
        }

        sendBytes(exchange, 200, content, contentTypeFor(resourcePath));
    }

    private byte[] readResource(String resourcePath) throws IOException
    {
        try (var stream = GraphQLServerAddon.class.getResourceAsStream(resourcePath))
        {
            if (stream == null)
                return null;
            return stream.readAllBytes();
        }
    }

    private String contentTypeFor(String resourcePath)
    {
        String lower = resourcePath.toLowerCase();
        if (lower.endsWith(".html"))
            return "text/html; charset=UTF-8";
        if (lower.endsWith(".js"))
            return "text/javascript; charset=UTF-8";
        if (lower.endsWith(".css"))
            return "text/css; charset=UTF-8";
        if (lower.endsWith(".json"))
            return "application/json; charset=UTF-8";
        if (lower.endsWith(".svg"))
            return "image/svg+xml";
        if (lower.endsWith(".png"))
            return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg"))
            return "image/jpeg";
        if (lower.endsWith(".gif"))
            return "image/gif";
        if (lower.endsWith(".woff"))
            return "font/woff";
        if (lower.endsWith(".woff2"))
            return "font/woff2";
        if (lower.endsWith(".ttf"))
            return "font/ttf";
        if (lower.endsWith(".ico"))
            return "image/x-icon";
        if (lower.endsWith(".map"))
            return "application/json; charset=UTF-8";
        return "application/octet-stream";
    }

    private void sendBytes(HttpExchange exchange, int status, byte[] content, String contentType) throws IOException
    {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, content.length);
        try (OutputStream output = exchange.getResponseBody())
        {
            output.write(content);
        }
    }

    private void sendHtml(HttpExchange exchange, int status, String html) throws IOException
    {
        byte[] responseBytes = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(status, responseBytes.length);
        try (OutputStream output = exchange.getResponseBody())
        {
            output.write(responseBytes);
        }
    }

    private String graphiqlHtml()
    {
        return """
                        <!--
                         *  Copyright (c) 2025 GraphQL Contributors
                         *  All rights reserved.
                         *
                         *  This source code is licensed under the license found in the
                         *  LICENSE file in the root directory of this source tree.
                        -->
                        <!doctype html>
                        <html lang="en">
                        <head>
                            <meta charset="UTF-8" />
                            <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                            <title>GraphiQL 5 with React 19 and GraphiQL Explorer</title>
                            <style>
                                body {
                                    margin: 0;
                                }

                                #graphiql {
                                    height: 100dvh;
                                }

                                .loading {
                                    height: 100%;
                                    display: flex;
                                    align-items: center;
                                    justify-content: center;
                                    font-size: 4rem;
                                }
                            </style>
                            <link rel="stylesheet" href="https://esm.sh/graphiql/dist/style.css" />
                            <link
                                    rel="stylesheet"
                                    href="https://esm.sh/@graphiql/plugin-explorer/dist/style.css"
                            />
                            <!--
                             * Note:
                             * The ?standalone flag bundles the module along with all of its `dependencies`, excluding `peerDependencies`, into a single JavaScript file.
                             * `@emotion/is-prop-valid` is a shim to remove the console error ` module "@emotion /is-prop-valid" not found`. Upstream issue: https://github.com/motiondivision/motion/issues/3126
                            -->
                            <script type="importmap">
                                {
                                  "imports": {
                                    "react": "https://esm.sh/react@19.1.0",
                                    "react/": "https://esm.sh/react@19.1.0/",

                                    "react-dom": "https://esm.sh/react-dom@19.1.0",
                                    "react-dom/": "https://esm.sh/react-dom@19.1.0/",

                                    "graphiql": "https://esm.sh/graphiql?standalone&external=react,react-dom,@graphiql/react,graphql",
                                    "graphiql/": "https://esm.sh/graphiql/",
                                    "@graphiql/plugin-explorer": "https://esm.sh/@graphiql/plugin-explorer?standalone&external=react,@graphiql/react,graphql",
                                    "@graphiql/react": "https://esm.sh/@graphiql/react?standalone&external=react,react-dom,graphql,@graphiql/toolkit,@emotion/is-prop-valid",

                                    "@graphiql/toolkit": "https://esm.sh/@graphiql/toolkit?standalone&external=graphql",
                                    "graphql": "https://esm.sh/graphql@16.11.0",
                                    "@emotion/is-prop-valid": "data:text/javascript,"
                                  }
                                }
                            </script>
                            <script type="module">
                                import React from 'react';
                                import ReactDOM from 'react-dom/client';
                                import { GraphiQL, HISTORY_PLUGIN } from 'graphiql';
                                import { createGraphiQLFetcher } from '@graphiql/toolkit';
                                import { explorerPlugin } from '@graphiql/plugin-explorer';
                                import 'graphiql/setup-workers/esm.sh';

                                const fetcher = createGraphiQLFetcher({
                                    url: "/graphql",
                                });
                                const plugins = [HISTORY_PLUGIN, explorerPlugin()];

                                function App() {
                                    return React.createElement(GraphiQL, {
                                        fetcher,
                                        plugins,
                                        defaultEditorToolsVisibility: true,
                                    });
                                }

                                const container = document.getElementById('graphiql');
                                const root = ReactDOM.createRoot(container);
                                root.render(React.createElement(App));
                            </script>
                        </head>
                        <body>
                        <div id="graphiql">
                            <div class="loading">Loading…</div>
                        </div>
                        </body>
                        </html>
                        """;
    }

    private void applyCorsHeaders(HttpExchange exchange)
    {
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        if (CORS_ALLOWED_ORIGIN.equals(origin) || CORS_ALLOWED_ORIGIN_DEV.equals(origin))
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", origin);
        else
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", CORS_ALLOWED_ORIGIN);
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
        exchange.getResponseHeaders().set("Access-Control-Allow-Credentials", "true");
    }

    private String getQueryParam(HttpExchange exchange, String name)
    {
        String query = exchange.getRequestURI().getRawQuery();
        if (query == null || query.isBlank())
            return null;
        for (String pair : query.split("&"))
        {
            int idx = pair.indexOf('=');
            if (idx <= 0)
                continue;
            String key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
            if (!name.equals(key))
                continue;
            return URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);
        }
        return null;
    }

    private void sendSseEvent(OutputStream output, String event, String data) throws IOException
    {
        String payload = data != null
                        ? "event: " + event + "\n" + "data: " + data + "\n\n"
                        : "event: " + event + "\n\n";
        output.write(payload.getBytes(StandardCharsets.UTF_8));
        output.flush();
    }

    private GraphQlRequest parseGraphQlSseRequest(HttpExchange exchange) throws IOException
    {
        if ("GET".equalsIgnoreCase(exchange.getRequestMethod()))
        {
            String query = getQueryParam(exchange, "query");
            String operationName = getQueryParam(exchange, "operationName");
            Map<String, Object> variables = parseVariables(getQueryParam(exchange, "variables"));
            return new GraphQlRequest(query, operationName, variables);
        }

        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, Object> payload;
        try
        {
            payload = gson.fromJson(body, Map.class);
        }
        catch (JsonSyntaxException e)
        {
            return null;
        }

        if (payload == null)
            return null;

        String query = payload.get("query") != null ? String.valueOf(payload.get("query")) : null;
        String operationName = payload.get("operationName") != null ? String.valueOf(payload.get("operationName"))
                        : null;
        Map<String, Object> variables = parseVariables(payload.get("variables"));
        return new GraphQlRequest(query, operationName, variables);
    }

    private Map<String, Object> parseVariables(Object variablesRaw)
    {
        if (variablesRaw == null)
            return Collections.emptyMap();
        if (variablesRaw instanceof Map<?, ?> raw)
        {
            @SuppressWarnings("unchecked")
            Map<String, Object> casted = (Map<String, Object>) raw;
            return casted;
        }
        if (variablesRaw instanceof String raw)
        {
            try
            {
                @SuppressWarnings("unchecked")
                Map<String, Object> casted = (Map<String, Object>) gson.fromJson(raw, Map.class);
                return casted != null ? casted : Collections.emptyMap();
            }
            catch (JsonSyntaxException e)
            {
                return Collections.emptyMap();
            }
        }
        return Collections.emptyMap();
    }

    private record GraphQlRequest(String query, String operationName, Map<String, Object> variables)
    {
    }

    private record GraphQLConfig(boolean enabled, String host, int port)
    {
        private static GraphQLConfig fromPreferences(IPreferenceStore store)
        {
            boolean enabled = store.getBoolean(UIConstants.Preferences.WEB_SERVER_ENABLED);
            String host = store.getString(UIConstants.Preferences.WEB_SERVER_HOST);
            int port = store.getInt(UIConstants.Preferences.WEB_SERVER_PORT);

            if (host == null || host.isBlank())
                host = DEFAULT_HOST;
            if (port <= 0)
                port = DEFAULT_PORT;

            return new GraphQLConfig(enabled, host, port);
        }

    }
}
