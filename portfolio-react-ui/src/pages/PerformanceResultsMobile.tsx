import { Alert, Button, Carousel, Checkbox, Segmented, Space, Spin, Typography } from "antd";
import {
  AlignLeftOutlined,
  UnorderedListOutlined,
  DownOutlined,
  UpOutlined,
  SortAscendingOutlined,
  SortDescendingOutlined,
} from "@ant-design/icons";
import type { EChartsOption } from "echarts";
import ReactECharts from "echarts-for-react";
import { useEffect, useRef, useState, type KeyboardEvent, type MouseEvent } from "react";
import type {
  PerformanceRow,
  PerformanceTotals,
  TotalSummary,
  ZoomRange,
} from "./performancePageViewTypes";

type PerformanceResultsMobileProps = {
  delta: { amount: number | null; currencyCode: string | null } | null;
  deltaLoading: boolean;
  accumulatedLoading: boolean;
  selectionLoading: boolean;
  hasChartData: boolean;
  chartOptions: EChartsOption;
  selectionColors: Record<string, string>;
  parametersPanel: React.ReactNode;
  selectionError: string | null;
  portfolioError: string | null;
  portfolioLoading: boolean;
  performanceRows: PerformanceRow[];
  mobilePerformanceRows: PerformanceRow[];
  securitiesRows: PerformanceRow[];
  expandedPortfolioKeys: string[];
  selectedRowKeys: string[];
  zoomRange: ZoomRange;
  totalSummary: TotalSummary;
  performanceTotals: PerformanceTotals;
  showFlatSecurities: boolean;
  listSortDirection: "asc" | "desc";
  listPerfDirection: "asc" | "desc";
  onToggleListSortDirection: () => void;
  onTogglePerfSortDirection: () => void;
  onToggleFlatSecurities: () => void;
  onTogglePortfolioExpanded: (rowKey: string) => void;
  onListSelectionChange: (row: PerformanceRow, checked: boolean) => void;
  formatAmount: (amount: number | null, currencyCode: string | null) => string;
  formatPercent: (value: number | null | undefined) => string;
};

type DisplayRow = PerformanceRow & { isChild?: boolean };

const PerformanceResultsMobile = ({
  delta,
  deltaLoading,
  accumulatedLoading,
  selectionLoading,
  hasChartData,
  chartOptions,
  selectionColors,
  parametersPanel,
  selectionError,
  portfolioError,
  portfolioLoading,
  performanceRows,
  mobilePerformanceRows,
  securitiesRows,
  expandedPortfolioKeys,
  selectedRowKeys,
  zoomRange,
  totalSummary,
  performanceTotals,
  showFlatSecurities,
  listSortDirection,
  listPerfDirection,
  onToggleListSortDirection,
  onTogglePerfSortDirection,
  onToggleFlatSecurities,
  onTogglePortfolioExpanded,
  onListSelectionChange,
  formatAmount,
  formatPercent,
}: PerformanceResultsMobileProps) => {
  const carouselRef = useRef<{ goTo: (slide: number) => void } | null>(null);
  const [activeSlide, setActiveSlide] = useState(0);

  const listRows: DisplayRow[] = showFlatSecurities
    ? securitiesRows
    : mobilePerformanceRows.flatMap((row) => {
        const items: DisplayRow[] = [{ ...row, isChild: false }];
        if (row.children?.length && expandedPortfolioKeys.includes(row.key)) {
          items.push(...row.children.map((child) => ({ ...child, isChild: true })));
        }
        return items;
      });

  return (
    <Space direction="vertical" size="large" className="page-stack">
      {deltaLoading ? (
        <Spin />
      ) : (
        <Typography.Text>
          Perf :{" "}
          <Typography.Text strong>
            {delta?.amount?.toLocaleString("fr-FR", {
              maximumFractionDigits: 2,
            }) ?? "-"}
          </Typography.Text>{" "}
          {delta?.currencyCode ? `(${delta.currencyCode})` : null}
          {zoomRange ? (
            <Typography.Text type="secondary" style={{ marginLeft: 8 }}>
              {`Du ${zoomRange.startDate} au ${zoomRange.endDate}`}
            </Typography.Text>
          ) : null}
        </Typography.Text>
      )}
      <div className="performance-mobile-panels">
        <Segmented
          block
          value={activeSlide}
          options={[
            { label: "Paramètres", value: 0 },
            { label: "Graphe", value: 1 },
            { label: "Liste", value: 2 },
          ]}
          onChange={(value) => {
            const next = Number(value);
            setActiveSlide(next);
            carouselRef.current?.goTo(next);
          }}
        />
        <Carousel
          ref={carouselRef}
          dots
          draggable
          swipeToSlide
          afterChange={(current) => setActiveSlide(current)}
        >
          <div className="performance-mobile-panel performance-mobile-panel--params">
            {parametersPanel}
          </div>
          <div className="performance-mobile-panel performance-mobile-panel--chart">
            {accumulatedLoading ? (
              <Spin />
            ) : (
              <Spin spinning={selectionLoading}>
                {hasChartData ? (
                  <Space direction="vertical" size="small" className="page-stack">
                    <ReactECharts
                      option={chartOptions}
                      style={{ height: 470, width: "100%" }}
                      notMerge
                      lazyUpdate
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
          <div className="performance-mobile-panel performance-mobile-panel--list">
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
                <Space direction="vertical" size="small" style={{ width: "100%" }}>
                  <Button
                    block
                    icon={
                      showFlatSecurities ? <AlignLeftOutlined /> : <UnorderedListOutlined />
                    }
                    onClick={onToggleFlatSecurities}
                  >
                    {showFlatSecurities ? "Groupé par portefeuille" : "Titres à plat"}
                  </Button>
                  <Button
                    block
                    icon={
                      listSortDirection === "asc" ? (
                        <SortAscendingOutlined />
                      ) : (
                        <SortDescendingOutlined />
                      )
                    }
                    onClick={onToggleListSortDirection}
                  >
                    {listSortDirection === "asc" ? "Tri A → Z" : "Tri Z → A"}
                  </Button>
                  <Button
                    block
                    icon={
                      listPerfDirection === "asc" ? (
                        <SortAscendingOutlined />
                      ) : (
                        <SortDescendingOutlined />
                      )
                    }
                    onClick={onTogglePerfSortDirection}
                  >
                    {listPerfDirection === "asc"
                      ? "Performance € croissante"
                      : "Performance € décroissante"}
                  </Button>
                </Space>
                <div className="performance-list">
                  <div className="performance-list-item performance-list-total">
                    <div className="performance-list-header">
                      <Typography.Text strong className="performance-list-title">
                        Total
                      </Typography.Text>
                    </div>
                    <div className="performance-list-values">
                      <span className="performance-list-toggle-spacer" />
                      <span className="performance-list-checkbox" />
                      <div className="performance-list-metrics">
                        <Typography.Text strong className="performance-list-value">
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
                  {listRows.map((row) => {
                    const isSelected = selectedRowKeys.includes(row.key);
                    const color = isSelected ? selectionColors[row.key] : undefined;
                    const itemStyle =
                      color && isSelected
                        ? {
                            borderInlineStart: `4px solid ${color}`,
                            paddingInlineStart: 12,
                          }
                        : undefined;
                    const isExpanded = expandedPortfolioKeys.includes(row.key);

                    return (
                      <div
                        key={row.key}
                        className={`performance-list-item${
                          row.isChild ? " performance-list-item--child" : ""
                        }`}
                        style={itemStyle}
                        onClick={(event: MouseEvent<HTMLDivElement>) => {
                          if (
                            row.rowType !== "portfolio" ||
                            !row.children?.length ||
                            (event.target as HTMLElement).closest(
                              ".performance-list-checkbox"
                            )
                          ) {
                            return;
                          }
                          onTogglePortfolioExpanded(row.key);
                        }}
                      >
                        <div className="performance-list-header">
                          <Typography.Text
                            strong={row.rowType === "portfolio" ? true : false}
                            className="performance-list-title"
                          >
                            {row.name}
                          </Typography.Text>
                          {row.rowType === "portfolio" && row.children?.length ? (
                            <span
                              className="performance-list-toggle"
                              role="button"
                              tabIndex={0}
                              aria-label={isExpanded ? "Réduire" : "Déployer"}
                              onClick={(event) => {
                                event.stopPropagation();
                                onTogglePortfolioExpanded(row.key);
                              }}
                              onKeyDown={(event: KeyboardEvent<HTMLSpanElement>) => {
                                if (event.key === "Enter" || event.key === " ") {
                                  event.preventDefault();
                                  event.stopPropagation();
                                  onTogglePortfolioExpanded(row.key);
                                }
                              }}
                            >
                              {isExpanded ? <UpOutlined /> : <DownOutlined />}
                            </span>
                          ) : null}
                        </div>
                        <div className="performance-list-values">
                          <div className="performance-list-checkbox">
                            <Checkbox
                              checked={isSelected}
                              onChange={(event) =>
                                onListSelectionChange(row, event.target.checked)
                              }
                            />
                          </div>
                          <div className="performance-list-metrics">
                            <Typography.Text
                              type="secondary"
                              className="performance-list-value"
                            >
                              {formatPercent(row.deltaPercent)}
                            </Typography.Text>
                            <Typography.Text
                              className="performance-list-value"
                              style={{
                                color:
                                  row.deltaAmount && row.deltaAmount < 0
                                    ? "#cf1322"
                                    : "#3f8600",
                              }}
                            >
                              {formatAmount(row.deltaAmount, row.deltaCurrency)}
                            </Typography.Text>
                          </div>
                        </div>
                      </div>
                    );
                  })}
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
