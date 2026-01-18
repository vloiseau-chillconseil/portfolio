import type { ReactNode } from "react";
import { createContext, useContext, useEffect, useMemo, useState } from "react";

type ClientInfo = {
  id: string | null;
  label: string | null;
  baseCurrency: string | null;
};

type CurrentClientContextValue = {
  currentClient: ClientInfo | null;
  setCurrentClient: (client: ClientInfo | null) => void;
};

const CurrentClientContext = createContext<CurrentClientContextValue | null>(null);

type CurrentClientProviderProps = {
  children: ReactNode;
};

export const CurrentClientProvider = ({
  children,
}: CurrentClientProviderProps) => {
  const [currentClient, setCurrentClient] = useState<ClientInfo | null>(null);

  useEffect(() => {
    const saved = localStorage.getItem("currentClient");
    if (!saved) return;
    try {
      const parsed = JSON.parse(saved) as ClientInfo;
      setCurrentClient(parsed ?? null);
    } catch {
      setCurrentClient(null);
    }
  }, []);

  useEffect(() => {
    if (!currentClient) {
      localStorage.removeItem("currentClient");
      return;
    }
    localStorage.setItem("currentClient", JSON.stringify(currentClient));
  }, [currentClient]);

  const value = useMemo(
    () => ({ currentClient, setCurrentClient }),
    [currentClient]
  );

  return (
    <CurrentClientContext.Provider value={value}>
      {children}
    </CurrentClientContext.Provider>
  );
};

export const useCurrentClient = () => {
  const context = useContext(CurrentClientContext);
  if (!context) {
    throw new Error("useCurrentClient must be used within CurrentClientProvider");
  }
  return context;
};
