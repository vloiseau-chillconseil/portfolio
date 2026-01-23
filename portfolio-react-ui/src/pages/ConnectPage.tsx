import { Button, Card, Space, Typography } from "antd";

const ConnectPage = () => (
  <Card title="Connexion" style={{ maxWidth: 560 }}>
    <Space direction="vertical" size="middle">
      <Typography.Title level={3}>Connection OK</Typography.Title>
      <Button type="primary" onClick={() => window.history.back()}>
        Retour dans l'application
      </Button>
    </Space>
  </Card>
);

export default ConnectPage;
