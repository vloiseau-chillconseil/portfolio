import { useEffect, useState } from "react";
import { Button, Card, Input, Space, Typography, message } from "antd";
import {
  getDefaultGraphqlUrl,
  getEffectiveGraphqlBaseUrl,
  getStoredGraphqlBaseUrl,
  getHealthcheckUrl,
  normalizeGraphqlBaseUrl,
  setGraphqlUrl,
} from "../services/graphqlConfig";

const ConnectionPage = () => {
  const [graphqlUrl, setGraphqlUrlState] = useState("");
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    const loadGraphqlUrl = async () => {
      const storedUrl = await getStoredGraphqlBaseUrl();
      setGraphqlUrlState(storedUrl);
      setIsLoading(false);
    };

    void loadGraphqlUrl();
  }, []);

  const handleSave = async () => {
    const candidateUrl = normalizeGraphqlBaseUrl(graphqlUrl);
    const baseUrl = candidateUrl || (await getEffectiveGraphqlBaseUrl());
    const healthcheckUrl = getHealthcheckUrl(baseUrl);

    if (!healthcheckUrl) {
      message.error("Renseignez une URL GraphQL avant d'enregistrer");
      return;
    }

    try {
      const response = await fetch(healthcheckUrl, { credentials: "include" });

      if (!response.ok) {
        message.error("Impossible de joindre le serveur configuré");
        return;
      }

      let version = "";

      try {
        const data = await response.clone().json();
        if (typeof data === "string") {
          version = data;
        } else if (data && typeof data === "object" && "version" in data) {
          version = String((data as { version: string }).version);
        }
      } catch {
        const text = await response.text();
        version = text.trim();
      }

      if (!version) {
        message.error("Serveur accessible mais version introuvable");
        return;
      }

      await setGraphqlUrl(candidateUrl);
      message.success(`Serveur ok : version ${version}`);
    } catch {
      message.error("Impossible de joindre le serveur configuré");
    }
  };

  return (
    <Card title="Connexion" style={{ maxWidth: 560 }}>
      <Space direction="vertical" size="middle" style={{ width: "100%" }}>
        <Typography.Paragraph>
          Configurez l'adresse du service GraphQL utilisé par l'application.
        </Typography.Paragraph>
        <Input
          value={graphqlUrl}
          onChange={(event) => setGraphqlUrlState(event.target.value)}
          placeholder={getDefaultGraphqlUrl()}
          disabled={isLoading}
        />
        <Button type="primary" onClick={handleSave} disabled={isLoading}>
          Enregistrer
        </Button>
      </Space>
    </Card>
  );
};

export default ConnectionPage;
