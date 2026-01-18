import { gql, useQuery } from "@apollo/client";
import { Card, List, Radio, Space, Typography } from "antd";
import { useCurrentClient } from "../state/currentClientContext";

const CLIENTS_QUERY = gql`
  query Clients {
    clients {
      id
      label
      baseCurrency
    }
  }
`;

const HomePage = () => {
  const { currentClient, setCurrentClient } = useCurrentClient();
  const { data, loading } = useQuery<{
    clients: Array<{
      id: string | null;
      label: string | null;
      baseCurrency: string | null;
    }> | null;
  }>(CLIENTS_QUERY);

  return (
    <Space direction="vertical" size="large" className="page-stack">
      <Card>
        <Typography.Title level={3}>Welcome to Portfolio React</Typography.Title>
        <Typography.Paragraph>
          Sélectionnez un client pour alimenter les autres requêtes.
        </Typography.Paragraph>
      </Card>
      <Card title="Clients disponibles" loading={loading}>
        <Radio.Group
          onChange={(event) => {
            const selected = data?.clients?.find(
              (client) => client?.id === event.target.value
            );
            setCurrentClient(selected ?? null);
          }}
          value={currentClient?.id}
        >
          <List
            dataSource={data?.clients ?? []}
            renderItem={(client) => (
              <List.Item>
                <Radio value={client?.id ?? ""}>
                  <Space direction="vertical" size={0}>
                    <Typography.Text strong>{client?.label}</Typography.Text>
                    <Typography.Text type="secondary">
                      {client?.id} · {client?.baseCurrency}
                    </Typography.Text>
                  </Space>
                </Radio>
              </List.Item>
            )}
          />
        </Radio.Group>
      </Card>
    </Space>
  );
};

export default HomePage;
