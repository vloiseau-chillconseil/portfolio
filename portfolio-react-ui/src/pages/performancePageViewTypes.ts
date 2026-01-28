export type PerformanceRow = {
  key: string;
  name: string;
  deltaAmount: number | null;
  deltaCurrency: string | null;
  deltaPercent: number | null;
  startAmount: number | null;
  startCurrency: string | null;
  portfolioId: string | null;
  securityId: string | null;
  rowType: "portfolio" | "security";
  children?: PerformanceRow[];
};

export type SortState = {
  columnKey: string | null;
  order: "ascend" | "descend" | null;
};

export type PerformanceTotals = {
  totalAmount: number | null;
  hasAmount: boolean;
  currencyCode: string | null;
  startAmount: number | null;
  hasStartAmount: boolean;
  startCurrency: string | null;
  percentSum: number;
  percentCount: number;
  percentValue?: number | null;
};

export type TotalSummary = {
  totalAmount: number | null;
  totalStartAmount: number | null;
  totalPercent: number | null;
};

export type ZoomRange = { startDate: string; endDate: string } | null;
