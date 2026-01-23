import { Capacitor } from "@capacitor/core";
import { Preferences } from "@capacitor/preferences";

const GRAPHQL_URL_KEY = "graphqlUrl";
const DEFAULT_GRAPHQL_URL = Capacitor.isNativePlatform()
  ? "http://mac:7524/graphql"
  : "/graphql";

export const getDefaultGraphqlUrl = () => DEFAULT_GRAPHQL_URL;

export const getGraphqlUrl = async () => {
  const { value } = await Preferences.get({ key: GRAPHQL_URL_KEY });
  const trimmed = value?.trim();
  return trimmed ? trimmed : DEFAULT_GRAPHQL_URL;
};

export const setGraphqlUrl = async (url: string) =>
  Preferences.set({ key: GRAPHQL_URL_KEY, value: url.trim() });

export const getConnectUrl = (graphqlUrl: string) => {
  const trimmed = graphqlUrl.trim();

  if (!trimmed) {
    return "";
  }

  if (trimmed.endsWith("/graphql")) {
    return trimmed.replace(/\/graphql$/, "/connect");
  }

  return `${trimmed.replace(/\/$/, "")}/connect`;
};
