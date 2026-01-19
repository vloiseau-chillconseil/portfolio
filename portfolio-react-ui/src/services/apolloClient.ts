import { ApolloClient, InMemoryCache, HttpLink } from "@apollo/client";
import { Capacitor } from "@capacitor/core";

const graphqlUri = Capacitor.isNativePlatform()
  ? "http://mac:7524/graphql"
  : "/graphql";

export const apolloClient = new ApolloClient({
  link: new HttpLink({
    uri: graphqlUri,
  }),
  cache: new InMemoryCache(),
});
