import { useEffect, useMemo, useState } from "react";
import { gql, useSubscription } from "@apollo/client";
import { Layout, Menu, Button, Drawer, Typography, Progress } from "antd";
import type { MenuProps } from "antd";
import {
  HomeOutlined,
  LineChartOutlined,
  MenuOutlined,
  LinkOutlined,
  AppstoreOutlined,
} from "@ant-design/icons";
import { Route, Routes, useLocation, useNavigate } from "react-router-dom";
import HomePage from "./pages/HomePage";
import PerformancePage from "./pages/PerformancePage";
import ConnectionPage from "./pages/ConnectionPage";
import ConnectPage from "./pages/ConnectPage";
import SecuritiesPage from "./pages/SecuritiesPage";
import { useCurrentClient } from "./state/currentClientContext";

const { Content, Header } = Layout;
const MOBILE_MAX_WIDTH = 500;

const QUOTE_UPDATES_SUBSCRIPTION = gql`
  subscription QuoteUpdates($clientId: String) {
    quoteUpdates(clientId: $clientId) {
      completedTaskCount
      taskCount
      timestamp
    }
  }
`;

const menuItems: MenuProps["items"] = [
  { key: "/", label: "Accueil", icon: <HomeOutlined /> },
  { key: "/performances", label: "Performances", icon: <LineChartOutlined /> },
  { key: "/securities", label: "Titres", icon: <AppstoreOutlined /> },
  { key: "/connection", label: "Connexion", icon: <LinkOutlined /> },
];

const App = () => {
  const { currentClient } = useCurrentClient();
  const navigate = useNavigate();
  const location = useLocation();
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [isMobileLayout, setIsMobileLayout] = useState(
    () => window.innerWidth <= MOBILE_MAX_WIDTH
  );
  const [quoteProgress, setQuoteProgress] = useState<{
    completedTaskCount: number;
    taskCount: number;
    timestamp: number;
  } | null>(null);

  const { data: quoteUpdateData } = useSubscription<{
    quoteUpdates: {
      completedTaskCount: number;
      taskCount: number;
      timestamp: number;
    } | null;
  }>(QUOTE_UPDATES_SUBSCRIPTION, {
    variables: { clientId: currentClient?.id ?? null },
    skip: !currentClient?.id,
  });

  useEffect(() => {
    if (quoteUpdateData?.quoteUpdates) {
      setQuoteProgress(quoteUpdateData.quoteUpdates);
    }
  }, [quoteUpdateData]);

  useEffect(() => {
    const updateLayoutMode = () => {
      setIsMobileLayout(window.innerWidth <= MOBILE_MAX_WIDTH);
    };

    updateLayoutMode();
    window.addEventListener("resize", updateLayoutMode);

    return () => {
      window.removeEventListener("resize", updateLayoutMode);
    };
  }, []);

  useEffect(() => {
    document.body.classList.toggle("app-mode-mobile", isMobileLayout);
    document.body.classList.toggle("app-mode-desktop", !isMobileLayout);
  }, [isMobileLayout]);

  const progressPercent = useMemo(() => {
    const taskCount = quoteProgress?.taskCount ?? 0;
    const completed = quoteProgress?.completedTaskCount ?? 0;
    if (!taskCount) {
      return 0;
    }
    return Math.min(100, Math.round((completed / taskCount) * 100));
  }, [quoteProgress]);

  const isQuoteActive = Boolean(
    quoteProgress?.taskCount &&
      quoteProgress.completedTaskCount < quoteProgress.taskCount
  );

  const selectedKey = useMemo(() => {
    if (location.pathname.startsWith("/performances")) {
      return "/performances";
    }
    if (location.pathname.startsWith("/connection")) {
      return "/connection";
    }
    if (location.pathname.startsWith("/securities")) {
      return "/securities";
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
    <Layout
      className={`app-layout ${
        isMobileLayout ? "app-layout--mobile" : "app-layout--desktop"
      }`}
    >
      <Layout className="app-main">
        <Header className="app-header">
          <Button
            type="text"
            icon={<MenuOutlined />}
            onClick={() => setDrawerOpen(true)}
          />
          <Typography.Title level={4} className="app-title">
            PP
          </Typography.Title>
          <div className="app-header-progress">
            <Progress
              percent={progressPercent}
              showInfo={false}
              size="small"
              strokeColor={isQuoteActive ? "#1677ff" : "#bfbfbf"}
            />
          </div>
        </Header>
        <Content className="app-content">
          <Routes>
            <Route path="/" element={<HomePage />} />
            <Route path="/performances" element={<PerformancePage />} />
            <Route path="/securities" element={<SecuritiesPage />} />
            <Route path="/connection" element={<ConnectionPage />} />
            <Route path="/connect" element={<ConnectPage />} />
          </Routes>
        </Content>
      </Layout>
      <Drawer
        placement="left"
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
