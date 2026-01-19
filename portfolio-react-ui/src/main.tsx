import React from "react";
import ReactDOM from "react-dom/client";
import { BrowserRouter } from "react-router-dom";
import { ApolloProvider } from "@apollo/client";
import App from "./App";
import { apolloClient } from "./services/apolloClient";
import { CurrentClientProvider } from "./state/currentClientContext";
import "./styles.css";

ReactDOM.createRoot(document.getElementById("root")).render(
  <React.StrictMode>
    <ApolloProvider client={apolloClient}>
      <CurrentClientProvider>
        <BrowserRouter basename="/ui">
          <App />
        </BrowserRouter>
      </CurrentClientProvider>
    </ApolloProvider>
  </React.StrictMode>
);
