import { useMemo, useState } from "react";
import { Layout, Menu, Button, Drawer, Grid, Typography } from "antd";
import type { MenuProps } from "antd";
import { HomeOutlined, LineChartOutlined, MenuOutlined } from "@ant-design/icons";
import { Route, Routes, useLocation, useNavigate } from "react-router-dom";
import HomePage from "./pages/HomePage";
import PerformancePage from "./pages/PerformancePage";

const { Content, Sider, Header } = Layout;
const { useBreakpoint } = Grid;

const menuItems: MenuProps["items"] = [
  { key: "/", label: "Accueil", icon: <HomeOutlined /> },
  { key: "/performances", label: "Performances", icon: <LineChartOutlined /> },
];

const App = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const screens = useBreakpoint();
  const [drawerOpen, setDrawerOpen] = useState(false);

  const selectedKey = useMemo(() => {
    if (location.pathname.startsWith("/performances")) {
      return "/performances";
    }
    return "/";
  }, [location.pathname]);

  const handleMenuClick: MenuProps["onClick"] = ({ key }) => {
    navigate(String(key));
    setDrawerOpen(false);
  };

  const menu = (
    <Menu
      mode="inline"
      items={menuItems}
      selectedKeys={[selectedKey]}
      onClick={handleMenuClick}
    />
  );

  return (
    <Layout className="app-layout">
      <Layout className="app-main">
        <Header className="app-header">
          {screens.xs ? (
            <Button
              type="text"
              icon={<MenuOutlined />}
              onClick={() => setDrawerOpen(true)}
            />
          ) : null}
          <Typography.Title level={4} className="app-title">
            Portfolio React
          </Typography.Title>
        </Header>
        <Content className="app-content">
          <Routes>
            <Route path="/" element={<HomePage />} />
            <Route path="/performances" element={<PerformancePage />} />
          </Routes>
        </Content>
      </Layout>
      {!screens.xs ? (
        <Sider className="app-sider" width={240}>
          {menu}
        </Sider>
      ) : null}
      <Drawer
        placement="right"
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        title="Navigation"
      >
        {menu}
      </Drawer>
    </Layout>
  );
};

export default App;
