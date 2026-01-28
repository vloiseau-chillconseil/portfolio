import type { ApexAxisChartSeries, ApexOptions } from "apexcharts";
import { Alert, Carousel, Checkbox, Space, Spin, Typography } from "antd";
import { DownOutlined, RightOutlined } from "@ant-design/icons";
import ReactApexChart from "react-apexcharts";
import { useRef } from "react";
import type {
  PerformanceRow,
  PerformanceTotals,
  TotalSummary,
} from "./performancePageViewTypes";

type PerformanceResultsMobileProps = {
  delta: { amount: number | null; currencyCode: string | null } | null;
  deltaLoading: boolean;
  accumulatedLoading: boolean;
  selectionLoading: boolean;
  hasChartData: boolean;
  chartSeries: ApexAxisChartSeries;
  chartOptions: ApexOptions;
  brushSeries: ApexAxisChartSeries;
  brushOptions: ApexOptions;
  selectionError: string | null;
  portfolioError: string | null;
  portfolioLoading: boolean;
  performanceRows: PerformanceRow[];
  mobilePerformanceRows: PerformanceRow[];
  expandedPortfolioKeys: string[];
  selectedRowKeys: string[];
  totalSummary: TotalSummary;
  performanceTotals: PerformanceTotals;
  onTogglePortfolioExpanded: (rowKey: string) => void;
  onListSelectionChange: (row: PerformanceRow, checked: boolean) => void;
  formatAmount: (amount: number | null, currencyCode: string | null) => string;
  formatPercent: (value: number | null | undefined) => string;
};

const PerformanceResultsMobile = ({
  delta,
  deltaLoading,
  accumulatedLoading,
  selectionLoading,
  hasChartData,
  chartSeries,
  chartOptions,
  brushSeries,
  brushOptions,
  selectionError,
  portfolioError,
  portfolioLoading,
  performanceRows,
  mobilePerformanceRows,
  expandedPortfolioKeys,
  selectedRowKeys,
  totalSummary,
  performanceTotals,
  onTogglePortfolioExpanded,
  onListSelectionChange,
  formatAmount,
  formatPercent,
}: PerformanceResultsMobileProps) => {
  const carouselRef = useRef<{ goTo: (slide: number) => void } | null>(null);

  return (
    <Space direction="vertical" size="large" className="page-stack">
      {deltaLoading ? (
        <Spin />
      ) : (
        <Typography.Text>
          Montant clientFilterDelta :{" "}
          <Typography.Text strong>
            {delta?.amount?.toLocaleString("fr-FR", {
              maximumFractionDigits: 2,
            }) ?? "-"}
          </Typography.Text>{" "}
          {delta?.currencyCode ? `(${delta.currencyCode})` : null}
        </Typography.Text>
      )}
      <div className="performance-mobile-panels">
        <div className="performance-mobile-tabs">
          <button
            type="button"
            className="performance-mobile-tab"
            onClick={() => carouselRef.current?.goTo(0)}
          >
            Graphe
          </button>
          <button
            type="button"
            className="performance-mobile-tab"
            onClick={() => carouselRef.current?.goTo(1)}
          >
            Liste
          </button>
        </div>
        <Carousel
          ref={carouselRef}
          dots
          draggable
          swipeToSlide
        >
          <div className="performance-mobile-panel performance-mobile-panel--chart">
            {accumulatedLoading ? (
              <Spin />
            ) : (
              <Spin spinning={selectionLoading}>
                {hasChartData ? (
                  <Space direction="vertical" size="small" className="page-stack">
                    <ReactApexChart
                      options={chartOptions}
                      series={chartSeries}
                      type="line"
                      height={360}
                    />
                    <ReactApexChart
                      options={brushOptions}
                      series={brushSeries}
                      type="area"
                      height={110}
                    />
                  </Space>
                ) : (
                  <Typography.Text type="secondary">
                    Aucun point disponible sur la période.
                  </Typography.Text>
                )}
              </Spin>
            )}
          </div>
          <div className="performance-mobile-panel">
            {selectionError ? (
              <Alert
                type="error"
                message="Erreur lors du chargement des courbes sélectionnées."
                description={selectionError}
              />
            ) : null}
            {portfolioError ? (
              <Alert
                type="error"
                message="Erreur lors du chargement du détail des performances."
                description={portfolioError}
              />
            ) : portfolioLoading ? (
              <Spin />
            ) : performanceRows.length ? (
              <div className="performance-table-mobile">
                <div className="performance-list">
                  {mobilePerformanceRows.map((row) => (
                    <div key={row.key} className="performance-list-item">
                      <div className="performance-list-header">
                        <Typography.Text strong className="performance-list-title">
                          {row.name}
                        </Typography.Text>
                      </div>
                      <div className="performance-list-values">
                        {row.children?.length ? (
                          <button
                            type="button"
                            className="performance-list-toggle-button"
                            onClick={() => onTogglePortfolioExpanded(row.key)}
                            aria-label={
                              expandedPortfolioKeys.includes(row.key)
                                ? "Réduire"
                                : "Déployer"
                            }
                          >
                            {expandedPortfolioKeys.includes(row.key) ? (
                              <DownOutlined />
                            ) : (
                              <RightOutlined />
                            )}
                          </button>
                        ) : (
                          <span className="performance-list-toggle-spacer" />
                        )}
                        <Checkbox
                          checked={selectedRowKeys.includes(row.key)}
                          onChange={(event) =>
                            onListSelectionChange(row, event.target.checked)
                          }
                        />
                        <div className="performance-list-metrics">
                          <Typography.Text className="performance-list-value">
                            {formatAmount(row.deltaAmount, row.deltaCurrency)}
                          </Typography.Text>
                          <Typography.Text
                            type="secondary"
                            className="performance-list-value"
                          >
                            {formatPercent(row.deltaPercent)}
                          </Typography.Text>
                        </div>
                      </div>
                      {row.children?.length &&
                      expandedPortfolioKeys.includes(row.key) ? (
                        <div className="performance-list-children">
                          {row.children.map((child) => (
                            <div key={child.key} className="performance-list-item">
                              <div className="performance-list-header">
                                <Typography.Text className="performance-list-title">
                                  {child.name}
                                </Typography.Text>
                              </div>
                              <div className="performance-list-values">
                                <span className="performance-list-toggle-spacer" />
                                <Checkbox
                                  checked={selectedRowKeys.includes(child.key)}
                                  onChange={(event) =>
                                    onListSelectionChange(
                                      child,
                                      event.target.checked
                                    )
                                  }
                                />
                                <div className="performance-list-metrics">
                                  <Typography.Text className="performance-list-value">
                                    {formatAmount(
                                      child.deltaAmount,
                                      child.deltaCurrency
                                    )}
                                  </Typography.Text>
                                  <Typography.Text
                                    type="secondary"
                                    className="performance-list-value"
                                  >
                                    {formatPercent(child.deltaPercent)}
                                  </Typography.Text>
                                </div>
                              </div>
                            </div>
                          ))}
                        </div>
                      ) : null}
                    </div>
                  ))}
                  <div className="performance-list-item performance-list-total">
                    <div className="performance-list-header">
                      <span className="performance-list-checkbox" />
                      <Typography.Text strong className="performance-list-title">
                        Total
                      </Typography.Text>
                    </div>
                    <div className="performance-list-values">
                      <span className="performance-list-toggle-spacer" />
                      <span className="performance-list-checkbox" />
                      <div className="performance-list-metrics">
                        <Typography.Text
                          strong
                          className="performance-list-value"
                        >
                          {formatAmount(
                            totalSummary.totalAmount,
                            performanceTotals.currencyCode
                          )}
                        </Typography.Text>
                        <Typography.Text
                          strong
                          type="secondary"
                          className="performance-list-value"
                        >
                          {formatPercent(totalSummary.totalPercent)}
                        </Typography.Text>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            ) : (
              <Typography.Text type="secondary">
                Aucun détail disponible sur la période sélectionnée.
              </Typography.Text>
            )}
          </div>
        </Carousel>
      </div>
    </Space>
  );
};

export default PerformanceResultsMobile;
