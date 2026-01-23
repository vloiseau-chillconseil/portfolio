import {
  ApolloClient,
  ApolloLink,
  HttpLink,
  InMemoryCache,
  Observable,
  split,
} from "@apollo/client";
import { setContext } from "@apollo/client/link/context";
import { getMainDefinition } from "@apollo/client/utilities";
import { createClient } from "graphql-sse";
import { print } from "graphql";
import { getGraphqlUrl, getGraphqlSseUrl } from "./graphqlConfig";

const sseClient = createClient({
  url: async () => await getGraphqlSseUrl(),
  withCredentials: true,
});

const sseLink = new ApolloLink(
  (operation) =>
    new Observable((sink) =>
      sseClient.subscribe(
        {
          ...operation,
          query: print(operation.query),
        },
        {
          next: (value) => sink.next(value),
          error: (error) => sink.error(error),
          complete: () => sink.complete(),
        }
      )
    )
);

const httpLink = new HttpLink({
  credentials: "include",
});

const authLink = setContext(async () => ({
  uri: await getGraphqlUrl(),
}));

const splitLink = split(
  ({ query }) => {
    const definition = getMainDefinition(query);
    return (
      definition.kind === "OperationDefinition" &&
      definition.operation === "subscription"
    );
  },
  sseLink,
  authLink.concat(httpLink)
);

export const apolloClient = new ApolloClient({
  link: splitLink,
  cache: new InMemoryCache(),
});
