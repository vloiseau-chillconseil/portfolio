import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  base: "/ui/",
  plugins: [react()],
  server: {
    proxy: {
      "/graphql": {
        target: "http://127.0.0.1:7524",
        changeOrigin: true,
        secure: false,
      },
    },
  },
});
