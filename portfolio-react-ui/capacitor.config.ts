import type { CapacitorConfig } from "@capacitor/cli";

const config: CapacitorConfig = {
  appId: "name.vloiseau.portfolio",
  appName: "Portfolio Performances",
  webDir: "dist",
  bundledWebRuntime: false,
  plugins: {
    StatusBar: {
      overlaysWebView: false,
    },
  },
};

export default config;
