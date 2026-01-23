import { Preferences } from "@capacitor/preferences";

const GRAPHQL_BASE_URL_KEY = "graphqlBaseUrl";
const DEFAULT_GRAPHQL_BASE_URL = "";

export const getDefaultGraphqlUrl = () =>
  DEFAULT_GRAPHQL_BASE_URL || window.location.origin;

export const normalizeGraphqlBaseUrl = (url: string) => {
  const trimmed = url.trim();

  if (!trimmed) {
    return "";
  }

  const withoutGraphql = trimmed.replace(/\/graphql\/?$/, "");
  return withoutGraphql.replace(/\/$/, "");
};

export const getStoredGraphqlBaseUrl = async () => {
  const { value } = await Preferences.get({ key: GRAPHQL_BASE_URL_KEY });
  return value ? normalizeGraphqlBaseUrl(value) : "";
};

export const getEffectiveGraphqlBaseUrl = async () => {
  const stored = await getStoredGraphqlBaseUrl();
  if (stored) {
    return stored;
  }

  return DEFAULT_GRAPHQL_BASE_URL || window.location.origin;
};

export const getGraphqlUrl = async () => {
  const baseUrl = await getEffectiveGraphqlBaseUrl();
  return `${baseUrl.replace(/\/$/, "")}/graphql`;
};

export const getGraphqlWsUrl = async () => {
  const graphqlUrl = await getGraphqlUrl();

  if (graphqlUrl.startsWith("https://")) {
    return graphqlUrl.replace(/^https:/, "wss:");
  }

  if (graphqlUrl.startsWith("http://")) {
    return graphqlUrl.replace(/^http:/, "ws:");
  }

  return graphqlUrl;
};

export const getGraphqlSseUrl = async () => {
  const graphqlUrl = await getGraphqlUrl();
  return `${graphqlUrl.replace(/\/$/, "")}/sse`;
};

export const setGraphqlUrl = async (url: string) =>
  Preferences.set({ key: GRAPHQL_BASE_URL_KEY, value: normalizeGraphqlBaseUrl(url) });

export const getConnectUrl = (graphqlUrl: string) => {
  const trimmed = normalizeGraphqlBaseUrl(graphqlUrl);

  if (!trimmed) {
    return "";
  }

  return `${trimmed.replace(/\/$/, "")}/connect`;
};

export const getHealthcheckUrl = (graphqlUrl: string) => {
  const trimmed = normalizeGraphqlBaseUrl(graphqlUrl);

  if (!trimmed) {
    return "";
  }

  return `${trimmed.replace(/\/$/, "")}/healthcheck`;
};
