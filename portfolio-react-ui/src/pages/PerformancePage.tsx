import { gql, useMutation, useQuery } from "@apollo/client";
import { Capacitor } from "@capacitor/core";
import {
  Card,
  DatePicker,
  Dropdown,
  Select,
  Space,
  Typography,
  Alert,
  Spin,
  message,
} from "antd";
import dayjs, { type Dayjs } from "dayjs";
import { useEffect, useMemo, useRef, useState } from "react";
import ReactECharts from "echarts-for-react";
import { useCurrentClient } from "../state/currentClientContext";
import { SyncOutlined } from "@ant-design/icons";
import type { MenuProps } from "antd";

const CLIENT_FILTERS_QUERY = gql`
  query ClientFilters($clientId: String) {
    clientFilters(clientId: $clientId) {
      id
      label
      uuids
    }
  }
`;

const CLIENT_FILTER_DELTA_QUERY = gql`
  query ClientFilterDelta(
    $clientId: String
    $filterId: String
    $startDate: String
    $endDate: String
  ) {
    clientFilterDelta(
      clientId: $clientId
      filterId: $filterId
      startDate: $startDate
      endDate: $endDate
    ) {
      amount
      currencyCode
    }
  }
`;

const CLIENT_FILTER_ACCUMULATED_QUERY = gql`
  query ClientFilterAccumulatedDelta(
    $clientId: String
    $filterId: String
    $startDate: String
    $endDate: String
  ) {
    clientFilterAccumulatedDelta(
      clientId: $clientId
      filterId: $filterId
      startDate: $startDate
      endDate: $endDate
    ) {
      date
      value {
        amount
        currencyCode
      }
    }
  }
`;

const UPDATE_QUOTES_MUTATION = gql`
  mutation UpdateQuotes($clientId: String, $scope: UpdateQuotesScope) {
    updateQuotes(clientId: $clientId, scope: $scope) {
      scheduled
      securityCount
    }
  }
`;

const PerformancePage = () => {
  const { currentClient } = useCurrentClient();
  const [dateRange, setDateRange] = useState<[Dayjs, Dayjs] | null>(null);
  const [nativeStartDate, setNativeStartDate] = useState("");
  const [nativeEndDate, setNativeEndDate] = useState("");
  const [selectedFilterId, setSelectedFilterId] = useState<string | null>(null);
  const skipNextSaveRef = useRef(false);
  const isNativePlatform = Capacitor.isNativePlatform();

  const { data: filtersData, loading: filtersLoading } = useQuery<{
    clientFilters: Array<{ id: string | null; label: string | null }> | null;
  }>(CLIENT_FILTERS_QUERY, {
    variables: { clientId: currentClient?.id ?? null },
    skip: !currentClient?.id,
  });

  const formattedDates = useMemo(() => {
    if (!dateRange) return null;
    const [start, end] = dateRange;
    return {
      startDate: start.format("YYYY-MM-DD"),
      endDate: end.format("YYYY-MM-DD"),
    };
  }, [dateRange]);

  const hasDates = Boolean(
    formattedDates?.startDate && formattedDates?.endDate
  );
  const hasFilter = selectedFilterId !== null;
  const hasClient = Boolean(currentClient?.id);
  const shouldFetch = hasClient && hasFilter && hasDates;

  useEffect(() => {
    if (!currentClient?.id) {
      setDateRange(null);
      setSelectedFilterId(null);
      return;
    }

    const saved = localStorage.getItem(`performanceSettings:${currentClient.id}`);
    if (!saved) {
      return;
    }

    skipNextSaveRef.current = true;
    try {
      const parsed = JSON.parse(saved) as {
        startDate?: string | null;
        endDate?: string | null;
        filterId?: string | null;
      };
      if (parsed.startDate && parsed.endDate) {
        setDateRange([dayjs(parsed.startDate), dayjs(parsed.endDate)]);
      } else {
        setDateRange(null);
      }
      setSelectedFilterId(parsed.filterId ?? null);
    } catch {
      setDateRange(null);
      setSelectedFilterId(null);
    }
  }, [currentClient?.id]);

  useEffect(() => {
    if (!isNativePlatform) return;
    if (!dateRange) {
      setNativeStartDate("");
      setNativeEndDate("");
      return;
    }

    setNativeStartDate(dateRange[0].format("YYYY-MM-DD"));
    setNativeEndDate(dateRange[1].format("YYYY-MM-DD"));
  }, [dateRange, isNativePlatform]);

  useEffect(() => {
    if (!currentClient?.id) return;
    if (skipNextSaveRef.current) {
      skipNextSaveRef.current = false;
      return;
    }
    const payload = {
      startDate: dateRange?.[0]?.format("YYYY-MM-DD") ?? null,
      endDate: dateRange?.[1]?.format("YYYY-MM-DD") ?? null,
      filterId: selectedFilterId,
    };
    localStorage.setItem(
      `performanceSettings:${currentClient.id}`,
      JSON.stringify(payload)
    );
  }, [currentClient?.id, dateRange, selectedFilterId]);

  const deltaQuery = useQuery<{
    clientFilterDelta: { amount: number | null; currencyCode: string | null } | null;
  }>(CLIENT_FILTER_DELTA_QUERY, {
    variables: {
      clientId: currentClient?.id,
      filterId: selectedFilterId,
      ...formattedDates,
    },
    fetchPolicy: "network-only",
    skip: !shouldFetch,
  });

  const accumulatedQuery = useQuery<{
    clientFilterAccumulatedDelta:
      | Array<{
          date: string | null;
          value: { amount: number | null; currencyCode: string | null } | null;
        }>
      | null;
  }>(CLIENT_FILTER_ACCUMULATED_QUERY, {
    variables: {
      clientId: currentClient?.id,
      filterId: selectedFilterId,
      ...formattedDates,
    },
    fetchPolicy: "network-only",
    skip: !shouldFetch,
  });

  const [updateQuotes, updateQuotesState] = useMutation<{
    updateQuotes: { scheduled: boolean; securityCount: number | null } | null;
  }>(UPDATE_QUOTES_MUTATION);

  const chartData = useMemo(() => {
    const points = accumulatedQuery.data?.clientFilterAccumulatedDelta ?? [];
    return points.map((point) => ({
      date: point?.date ?? "",
      amount: point?.value?.amount ?? 0,
    }));
  }, [accumulatedQuery.data]);

  const filters = useMemo(
    () => filtersData?.clientFilters ?? [],
    [filtersData]
  );

  const delta = deltaQuery.data?.clientFilterDelta;
  const nativeStartValue = nativeStartDate || "";
  const nativeEndValue = nativeEndDate || "";
  const updateQuotesLoading = updateQuotesState.loading;

  const updateQuotesItems: MenuProps["items"] = [
    { key: "ALL", label: "Tous les titres" },
    { key: "ACTIVE", label: "Titres actifs" },
    { key: "HOLDINGS", label: "Titres détenus" },
  ];

  const handleUpdateQuotes: MenuProps["onClick"] = async ({ key }) => {
    if (!currentClient?.id) {
      message.info("Sélectionnez un client pour actualiser les titres.");
      return;
    }

    try {
      const { data } = await updateQuotes({
        variables: {
          clientId: currentClient.id,
          scope: key,
        },
      });
      const result = data?.updateQuotes;

      if (!result?.scheduled) {
        message.warning("Aucune mise à jour planifiée.");
        return;
      }

      message.success(
        `Actualisation lancée (${result.securityCount ?? 0} titres).`
      );

      if (shouldFetch) {
        await Promise.all([deltaQuery.refetch(), accumulatedQuery.refetch()]);
      }
    } catch {
      message.error("Erreur lors de l'actualisation des titres.");
    }
  };

  const updateNativeRange = (startValue: string, endValue: string) => {
    if (startValue && endValue) {
      setDateRange([dayjs(startValue), dayjs(endValue)]);
      return;
    }
    setDateRange(null);
  };

  const chartOption = useMemo(() => {
    return {
      tooltip: {
        trigger: "axis",
      },
      toolbox: {
        feature: {
          dataZoom: {},
          restore: {},
          saveAsImage: {},
        },
      },
      dataZoom: [
        { type: "inside" },
        { type: "slider" },
      ],
      xAxis: {
        type: "category",
        data: chartData.map((point) => point.date),
      },
      yAxis: {
        type: "value",
      },
      series: [
        {
          name: "Accumulated Delta",
          type: "line",
          smooth: true,
          data: chartData.map((point) => point.amount),
          showSymbol: false,
        },
      ],
    };
  }, [chartData]);

  return (
    <Space direction="vertical" size="large" className="page-stack">
      <Card
        title="Paramètres"
        extra={
          <Dropdown.Button
            menu={{ items: updateQuotesItems, onClick: handleUpdateQuotes }}
            icon={<SyncOutlined spin={updateQuotesLoading} />}
            loading={updateQuotesLoading}
            disabled={!currentClient}
          >
            Actualiser
          </Dropdown.Button>
        }
      >
        <Space direction="vertical" size="middle" className="form-stack">
          <Space direction="vertical" size={4}>
            <Typography.Text>Plage de dates</Typography.Text>
            {isNativePlatform ? (
              <Space direction="vertical" size={8} className="date-range-native">
                <input
                  type="date"
                  value={nativeStartValue}
                  onChange={(event) => {
                    const nextValue = event.target.value;
                    setNativeStartDate(nextValue);
                    updateNativeRange(nextValue, nativeEndValue);
                  }}
                />
                <input
                  type="date"
                  value={nativeEndValue}
                  onChange={(event) => {
                    const nextValue = event.target.value;
                    setNativeEndDate(nextValue);
                    updateNativeRange(nativeStartValue, nextValue);
                  }}
                />
              </Space>
            ) : (
              <DatePicker.RangePicker
                value={dateRange}
                onChange={(value) =>
                  setDateRange(value && value[0] && value[1] ? [value[0], value[1]] : null)
                }
                allowClear
              />
            )}
          </Space>
          <Space direction="vertical" size={4}>
            <Typography.Text>Client filter</Typography.Text>
            <Select
              placeholder={
                currentClient
                  ? "Sélectionnez un filtre"
                  : "Choisissez d'abord un client"
              }
              value={selectedFilterId}
              onChange={(value) => setSelectedFilterId(value)}
              options={filters.map((filter) => ({
                value: filter?.id ?? "",
                label: filter?.label ?? filter?.id ?? "",
              }))}
              loading={filtersLoading}
              disabled={!currentClient}
              style={{ minWidth: 280 }}
              allowClear
            />
          </Space>
        </Space>
      </Card>

      {!currentClient ? (
        <Alert
          type="info"
          message="Sélectionnez un client dans l'accueil pour continuer."
        />
      ) : null}

      {filtersLoading ? null : !filters.length && currentClient ? (
        <Alert
          type="warning"
          message="Aucun client filter disponible pour ce client."
        />
      ) : null}

      {deltaQuery.error || accumulatedQuery.error ? (
        <Alert
          type="error"
          message="Erreur lors du chargement des performances."
          description={
            deltaQuery.error?.message || accumulatedQuery.error?.message
          }
        />
      ) : shouldFetch ? (
        <Card title="Résultats">
          <Space direction="vertical" size="large" className="page-stack">
            {deltaQuery.loading ? (
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
            {accumulatedQuery.loading ? (
              <Spin />
            ) : chartData.length ? (
              <ReactECharts
                option={chartOption}
                style={{ height: 640, width: "100%" }}
                opts={{ renderer: "canvas" }}
              />
            ) : (
              <Typography.Text type="secondary">
                Aucun point disponible sur la période.
              </Typography.Text>
            )}
          </Space>
        </Card>
      ) : (
        <Card>
          <Typography.Text type="secondary">
            Renseignez une plage de date et un client filter pour afficher les
            données.
          </Typography.Text>
        </Card>
      )}
    </Space>
  );
};

export default PerformancePage;
