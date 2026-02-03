import { Alert, Button, Select, Space, Spin, Table, Typography } from "antd";
import type { TableProps } from "antd";
import { DownloadOutlined } from "@ant-design/icons";
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
  tableRows: PerformanceRow[];
  hasPerformanceData: boolean;
  totalSummary: TotalSummary;
  performanceTotals: PerformanceTotals;
  zoomRange: ZoomRange;
  selectedRowKeys: string[];
  sortState: SortState;
  groupingMode: string;
  groupingOptions: Array<{ value: string; label: string }>;
  onGroupingModeChange: (value: string) => void;
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
  tableRows,
  hasPerformanceData,
  totalSummary,
  performanceTotals,
  zoomRange,
  selectedRowKeys,
  sortState,
  groupingMode,
  groupingOptions,
  onGroupingModeChange,
  onRowSelectionChange,
  onTableChange,
  formatAmount,
  formatPercent,
}: PerformanceResultsDesktopProps) => {
  const handleExportCsv = () => {
    if (!tableRows.length) return;

    const collectLeaves = (rows: PerformanceRow[], parents: string[] = []) =>
      rows.flatMap((row) => {
        const nextParents = parents.concat(row.name ?? "-");
        if (row.children?.length) {
          return collectLeaves(row.children, nextParents);
        }
        return [{ record: row, ancestors: parents }];
      });

    const flattened = collectLeaves(tableRows);
    if (!flattened.length) return;

    const maxDepth = flattened.reduce((depth, entry) =>
      Math.max(depth, entry.ancestors.length), 0);

    const hierarchyHeaders = Array.from({ length: maxDepth }, (_value, index) =>
      `Niveau ${index + 1}`
    );
    const header = [
      ...hierarchyHeaders,
      "Nom",
      "Type",
      "Valeur de départ",
      "Devise départ",
      "Performance €",
      "Devise performance",
      "Performance %",
    ];

    const body = flattened.map(({ record, ancestors }) => {
      const hierarchyCells = hierarchyHeaders.map((_value, index) => ancestors[index] ?? "");
      const cells = [
        ...hierarchyCells,
        record.name ?? "",
        record.rowType ?? "",
        record.startAmount ?? "",
        record.startCurrency ?? "",
        record.deltaAmount ?? "",
        record.deltaCurrency ?? "",
        record.deltaPercent ?? "",
      ];
      return cells
        .map((cell) => {
          if (cell === null || cell === undefined) return "";
          const text = String(cell);
          return text.includes(";") || text.includes("\"")
            ? `"${text.replace(/"/g, '""')}"`
            : text;
        })
        .join(";");
    });
    const csvContent = [header.join(";"), ...body].join("\n");
    const blob = new Blob([`\uFEFF${csvContent}`], { type: "text/csv;charset=utf-8;" });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = "performance.csv";
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
  };

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
          ) : hasPerformanceData ? (
            <Space direction="vertical" size="middle" className="page-stack">
              <Typography.Text type="secondary">
                Détail des performances du {zoomRange?.startDate} au{" "}
                {zoomRange?.endDate}
              </Typography.Text>
              <Space wrap>
                <Select
                  value={groupingMode}
                  options={groupingOptions}
                  onChange={onGroupingModeChange}
                  style={{ width: 320 }}
                />
                <Button
                  icon={<DownloadOutlined />}
                  onClick={handleExportCsv}
                  disabled={!tableRows.length}
                >
                  Exporter en CSV
                </Button>
              </Space>
              <div className="performance-table-desktop">
                <Table
                  rowSelection={{
                    selectedRowKeys,
                    onChange: onRowSelectionChange,
                    checkStrictly: true,
                    getCheckboxProps: (record) => ({
                      disabled: record.rowType === "classification",
                    }),
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
