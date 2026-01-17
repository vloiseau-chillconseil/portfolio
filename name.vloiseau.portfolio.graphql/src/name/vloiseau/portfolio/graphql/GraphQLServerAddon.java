package name.vloiseau.portfolio.graphql;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.eclipse.core.runtime.Platform;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import io.leangen.graphql.GraphQLSchemaGenerator;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import name.abuchen.portfolio.ui.PortfolioPlugin;
import name.abuchen.portfolio.ui.editor.ClientInputFactory;

public class GraphQLServerAddon
{
    private static final String DEFAULT_HOST = "127.0.0.1";
    private static final int DEFAULT_PORT = 7524;

    private final Gson gson = new Gson();

    @Inject
    private ClientInputFactory clientInputFactory;

    private HttpServer server;
    private ExecutorService executor;

    @PostConstruct
    public void start()
    {
        GraphQLConfig config = GraphQLConfig.fromArgs(Platform.getApplicationArgs());
        if (!config.enabled())
            return;

        try
        {
            var schema = new GraphQLSchemaGenerator() //
                            .withOperationsFromSingleton(new PortfolioGraphQLQueries(clientInputFactory)) //
                            .generate();
            var graphQL = GraphQL.newGraphQL(schema).build();

            server = HttpServer.create(new InetSocketAddress(config.host(), config.port()), 0);
            server.createContext("/graphql", exchange -> handleRequest(exchange, graphQL));
            server.createContext("/graphiql", this::handleGraphiQL);
            executor = Executors.newSingleThreadExecutor(r -> {
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
    }

    private void handleRequest(HttpExchange exchange, GraphQL graphQL) throws IOException
    {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod()))
        {
            sendPlainText(exchange, 405, "Only POST is supported");
            return;
        }

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

    private record GraphQLConfig(boolean enabled, String host, int port)
    {
        private static GraphQLConfig fromArgs(String[] args)
        {
            boolean enabled = true;
            String host = DEFAULT_HOST;
            int port = DEFAULT_PORT;

            for (String arg : args)
            {
                if (arg == null)
                    continue;

                if (arg.equals("--graphql"))
                {
                    enabled = true;
                }
                else if (arg.startsWith("--graphql="))
                {
                    enabled = parseBoolean(arg.substring("--graphql=".length()), enabled);
                }
                else if (arg.startsWith("--graphql-host="))
                {
                    host = arg.substring("--graphql-host=".length());
                }
                else if (arg.startsWith("--graphql-port="))
                {
                    port = parseInt(arg.substring("--graphql-port=".length()), port);
                }
            }

            return new GraphQLConfig(enabled, host, port);
        }

        private static boolean parseBoolean(String raw, boolean defaultValue)
        {
            if (raw == null)
                return defaultValue;

            String normalized = raw.trim().toLowerCase(Locale.ROOT);
            if ("true".equals(normalized) || "1".equals(normalized) || "yes".equals(normalized))
                return true;
            if ("false".equals(normalized) || "0".equals(normalized) || "no".equals(normalized))
                return false;

            return defaultValue;
        }

        private static int parseInt(String raw, int defaultValue)
        {
            try
            {
                return Integer.parseInt(raw);
            }
            catch (NumberFormatException e)
            {
                return defaultValue;
            }
        }
    }
}
