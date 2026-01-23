import { useEffect, useState } from "react";
import { Button, Card, Input, Space, Typography, message } from "antd";
import {
  getConnectUrl,
  getDefaultGraphqlUrl,
  getGraphqlUrl,
  setGraphqlUrl,
} from "../services/graphqlConfig";

const ConnectionPage = () => {
  const [graphqlUrl, setGraphqlUrlState] = useState("");
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    const loadGraphqlUrl = async () => {
      const storedUrl = await getGraphqlUrl();
      setGraphqlUrlState(storedUrl);
      setIsLoading(false);
    };

    void loadGraphqlUrl();
  }, []);

  const handleSave = async () => {
    await setGraphqlUrl(graphqlUrl);
    message.success("URL GraphQL enregistrée");
  };

  const handleOpenConnect = async () => {
    const storedUrl = graphqlUrl.trim() ? graphqlUrl : await getGraphqlUrl();
    const connectUrl = getConnectUrl(storedUrl);

    if (!connectUrl) {
      message.error("Renseignez une URL GraphQL avant d'ouvrir /connect");
      return;
    }

    window.location.assign(connectUrl);
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
        <Space>
          <Button type="primary" onClick={handleSave} disabled={isLoading}>
            Enregistrer
          </Button>
          <Button onClick={handleOpenConnect} disabled={isLoading}>
            Ouvrir /connect
          </Button>
        </Space>
      </Space>
    </Card>
  );
};

export default ConnectionPage;
