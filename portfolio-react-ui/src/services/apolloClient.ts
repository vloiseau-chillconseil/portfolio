import { ApolloClient, InMemoryCache, HttpLink } from "@apollo/client";
import { setContext } from "@apollo/client/link/context";
import { getGraphqlUrl } from "./graphqlConfig";

export const apolloClient = new ApolloClient({
  link: setContext(async () => ({
    uri: await getGraphqlUrl(),
  })).concat(
    new HttpLink({
      credentials: "include",
    })
  ),
  cache: new InMemoryCache(),
});
