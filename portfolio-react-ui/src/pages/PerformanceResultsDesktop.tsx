import { Alert, Button, Space, Spin, Table, Typography } from "antd";
import { AlignLeftOutlined, UnorderedListOutlined } from "@ant-design/icons";
import type { TableProps } from "antd";
import type { EChartsOption } from "echarts";
import ReactECharts from "echarts-for-react";
import type {
  PerformanceRow,
  PerformanceTotals,
  SortState,
  TotalSummary,
  ZoomRange,
} from "./performancePageViewTypes";

type PerformanceResultsDesktopProps = {
  delta: { amount: number | null; currencyCode: string | null } | null;
  deltaLoading: boolean;
  accumulatedLoading: boolean;
  selectionLoading: boolean;
  hasChartData: boolean;
  chartOptions: EChartsOption;
  selectionError: string | null;
  portfolioError: string | null;
  portfolioLoading: boolean;
  performanceRows: PerformanceRow[];
  securitiesRows: PerformanceRow[];
  totalSummary: TotalSummary;
  performanceTotals: PerformanceTotals;
  zoomRange: ZoomRange;
  showFlatSecurities: boolean;
  selectedRowKeys: string[];
  sortState: SortState;
  onToggleFlatSecurities: () => void;
  onRowSelectionChange: (keys: Array<string | number>, rows: PerformanceRow[]) => void;
  onTableChange: TableProps<PerformanceRow>["onChange"];
  formatAmount: (amount: number | null, currencyCode: string | null) => string;
  formatPercent: (value: number | null | undefined) => string;
};

const PerformanceResultsDesktop = ({
  delta,
  deltaLoading,
  accumulatedLoading,
  selectionLoading,
  hasChartData,
  chartOptions,
  selectionError,
  portfolioError,
  portfolioLoading,
  performanceRows,
  securitiesRows,
  totalSummary,
  performanceTotals,
  zoomRange,
  showFlatSecurities,
  selectedRowKeys,
  sortState,
  onToggleFlatSecurities,
  onRowSelectionChange,
  onTableChange,
  formatAmount,
  formatPercent,
}: PerformanceResultsDesktopProps) => {
  const tableRows = showFlatSecurities ? securitiesRows : performanceRows;

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
          {zoomRange ? (
            <Typography.Text type="secondary" style={{ marginLeft: 8 }}>
              {`(${zoomRange.startDate} - ${zoomRange.endDate})`}
            </Typography.Text>
          ) : null}
        </Typography.Text>
      )}
      <div className="performance-layout">
        <div className="performance-chart">
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
        <div className="performance-table">
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
            <Space direction="vertical" size="middle" className="page-stack">
              <Typography.Text type="secondary">
                Détail des performances du {zoomRange?.startDate} au{" "}
                {zoomRange?.endDate}
              </Typography.Text>
              <Button
                type="default"
                icon={
                  showFlatSecurities ? <AlignLeftOutlined /> : <UnorderedListOutlined />
                }
                onClick={onToggleFlatSecurities}
              >
                {showFlatSecurities ? "Groupé par portefeuille" : "Titres à plat"}
              </Button>
              <div className="performance-table-desktop">
                <Table
                  rowSelection={{
                    selectedRowKeys,
                    onChange: onRowSelectionChange,
                    checkStrictly: true,
                  }}
                  onChange={onTableChange}
                  tableLayout="fixed"
                  columns={[
                    {
                      title: "Nom",
                      dataIndex: "name",
                      key: "name",
                      className: "performance-name-cell",
                      width: 240,
                      ellipsis: true,
                      sorter: (a, b) => a.name.localeCompare(b.name, "fr"),
                      sortOrder:
                        sortState.columnKey === "name" ? sortState.order : null,
                      sortDirections: ["ascend", "descend"],
                    },
                    {
                      title: "Valeur de départ",
                      dataIndex: "startAmount",
                      key: "startAmount",
                      align: "right",
                      className: "performance-start-value",
                      width: 150,
                      render: (_value, record) =>
                        formatAmount(record.startAmount, record.startCurrency),
                      sorter: (a, b) =>
                        (a.startAmount ?? 0) - (b.startAmount ?? 0),
                      sortOrder:
                        sortState.columnKey === "startAmount"
                          ? sortState.order
                          : null,
                      sortDirections: ["ascend", "descend"],
                    },
                    {
                      title: "Performance en euros",
                      dataIndex: "deltaAmount",
                      key: "deltaAmount",
                      align: "right",
                      width: 170,
                      render: (_value, record) =>
                        formatAmount(record.deltaAmount, record.deltaCurrency),
                      sorter: (a, b) =>
                        (a.deltaAmount ?? 0) - (b.deltaAmount ?? 0),
                      sortOrder:
                        sortState.columnKey === "deltaAmount"
                          ? sortState.order
                          : null,
                      sortDirections: ["ascend", "descend"],
                    },
                    {
                      title: "Performance en %",
                      dataIndex: "deltaPercent",
                      key: "deltaPercent",
                      align: "right",
                      width: 130,
                      render: (value) => formatPercent(value),
                      sorter: (a, b) =>
                        (a.deltaPercent ?? 0) - (b.deltaPercent ?? 0),
                      sortOrder:
                        sortState.columnKey === "deltaPercent"
                          ? sortState.order
                          : null,
                      sortDirections: ["ascend", "descend"],
                    },
                  ]}
                  dataSource={tableRows}
                  pagination={false}
                  size="small"
                  summary={() => (
                    <Table.Summary>
                      <Table.Summary.Row>
                        <Table.Summary.Cell index={0} />
                        <Table.Summary.Cell index={1}>
                          <Typography.Text strong>Total</Typography.Text>
                        </Table.Summary.Cell>
                        <Table.Summary.Cell
                          index={2}
                          align="right"
                          className="performance-start-value"
                        >
                          <Typography.Text strong>
                            {formatAmount(
                              totalSummary.totalStartAmount,
                              performanceTotals.startCurrency ?? null
                            )}
                          </Typography.Text>
                        </Table.Summary.Cell>
                        <Table.Summary.Cell index={3} align="right">
                          <Typography.Text strong>
                            {formatAmount(
                              totalSummary.totalAmount,
                              performanceTotals.currencyCode
                            )}
                          </Typography.Text>
                        </Table.Summary.Cell>
                        <Table.Summary.Cell index={4} align="right">
                          <Typography.Text strong>
                            {formatPercent(totalSummary.totalPercent)}
                          </Typography.Text>
                        </Table.Summary.Cell>
                      </Table.Summary.Row>
                    </Table.Summary>
                  )}
                />
              </div>
            </Space>
          ) : (
            <Typography.Text type="secondary">
              Aucun détail disponible sur la période sélectionnée.
            </Typography.Text>
          )}
        </div>
      </div>
    </Space>
  );
};

export default PerformanceResultsDesktop;
