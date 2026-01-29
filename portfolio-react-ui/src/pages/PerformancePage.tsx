import { gql, useApolloClient, useMutation, useQuery, useSubscription } from "@apollo/client";
import { Capacitor } from "@capacitor/core";
import {
  Alert,
  Button,
  Card,
  Carousel,
  DatePicker,
  Dropdown,
  Select,
  Space,
  Typography,
  message,
} from "antd";
import dayjs, { type Dayjs } from "dayjs";
import type { EChartsOption } from "echarts";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import type { CarouselRef } from "antd/es/carousel";
import { useCurrentClient } from "../state/currentClientContext";
import {
  AlignLeftOutlined,
  SyncOutlined,
  UnorderedListOutlined,
} from "@ant-design/icons";
import type { MenuProps } from "antd";
import PerformanceResultsDesktop from "./PerformanceResultsDesktop";
import PerformanceResultsMobile from "./PerformanceResultsMobile";
import type {
  PerformanceRow,
  PerformanceTotals,
  SortState,
  TotalSummary,
} from "./performancePageViewTypes";

const SHOW_MOBILE_PLACEHOLDERS = false;
const QUOTE_UPDATES_SUBSCRIPTION = gql`
  subscription QuoteUpdates($clientId: String) {
    quoteUpdates(clientId: $clientId) {
      completedTaskCount
      taskCount
      timestamp
    }
  }
`;

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

const REPORTING_PERIODS_QUERY = gql`
  query ReportingPeriods($clientId: String, $referenceDate: String) {
    reportingPeriods(clientId: $clientId, referenceDate: $referenceDate) {
      code
      label
      startDate
      endDate
    }
  }
`;

const CLIENT_FILTER_ACCUMULATED_QUERY = gql`
  query ClientFilterAccumulatedDelta(
    $clientId: String
    $filterId: String
    $portfolioId: String
    $securityId: String
    $startDate: String
    $endDate: String
  ) {
    clientFilterAccumulatedDelta(
      clientId: $clientId
      filterId: $filterId
      portfolioId: $portfolioId
      securityId: $securityId
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

const PORTFOLIO_SECURITY_PERFORMANCE_QUERY = gql`
  query PortfolioSecurityPerformance(
    $clientId: String
    $filterId: String
    $startDate: String
    $endDate: String
  ) {
    portfolioSecurityPerformance(
      clientId: $clientId
      filterId: $filterId
      startDate: $startDate
      endDate: $endDate
    ) {
      portfolios {
        portfolioId
        portfolioName
        referenceAccountId
        referenceAccountName
        startValue {
          amount
          currencyCode
        }
        delta {
          amount
          currencyCode
        }
        deltaPercent
      }
      securities {
        id
        portfolioId
        securityId
        name
        startValue {
          amount
          currencyCode
        }
        delta {
          amount
          currencyCode
        }
        deltaPercent
        taxonomyAssignments {
          taxonomyId
          taxonomyName
          classificationId
          classificationName
        }
      }
      taxonomies {
        taxonomyId
        taxonomyName
        classifications {
          classificationId
          parentId
          name
          startValue {
            amount
            currencyCode
          }
          delta {
            amount
            currencyCode
          }
          deltaPercent
        }
      }
      total {
        startValue {
          amount
          currencyCode
        }
        delta {
          amount
          currencyCode
        }
        deltaPercent
      }
    }
  }
`;

type MoneyValue = { amount: number | null; currencyCode: string | null } | null;

type PortfolioPerformanceResult = {
  portfolios:
    | Array<{
        portfolioId: string | null;
        portfolioName: string | null;
        referenceAccountId: string | null;
        referenceAccountName: string | null;
        startValue: MoneyValue;
        delta: MoneyValue;
        deltaPercent: number | null;
      }>
    | null;
  securities:
    | Array<{
        id: string | null;
        portfolioId: string | null;
        securityId: string | null;
        name: string | null;
        startValue: MoneyValue;
        delta: MoneyValue;
        deltaPercent: number | null;
        taxonomyAssignments:
          | Array<{
              taxonomyId: string | null;
              taxonomyName: string | null;
              classificationId: string | null;
              classificationName: string | null;
            }>
          | null;
      }>
    | null;
  taxonomies:
    | Array<{
        taxonomyId: string | null;
        taxonomyName: string | null;
        classifications:
          | Array<{
              classificationId: string | null;
              parentId: string | null;
              name: string | null;
              startValue: MoneyValue;
              delta: MoneyValue;
              deltaPercent: number | null;
            }>
          | null;
      }>
    | null;
  total:
    | {
        startValue: MoneyValue;
        delta: MoneyValue;
        deltaPercent: number | null;
      }
    | null;
};

const isNonNullable = <T,>(value: T | null | undefined): value is T =>
  value !== null && value !== undefined;

const UPDATE_QUOTES_MUTATION = gql`
  mutation UpdateQuotes($clientId: String, $scope: UpdateQuotesScope) {
    updateQuotes(clientId: $clientId, scope: $scope) {
      scheduled
      securityCount
    }
  }
`;

type SelectionSeries = {
  key: string;
  name: string;
  points: Array<{ date: string; amount: number; timestamp: number }>;
};

const CHART_COLORS = [
  "#5470c6",
  "#91cc75",
  "#fac858",
  "#ee6666",
  "#73c0de",
  "#3ba272",
  "#fc8452",
  "#9a60b4",
  "#ea7ccc",
];

const PerformancePage = () => {
  const apolloClient = useApolloClient();
  const { currentClient } = useCurrentClient();
  const [dateRange, setDateRange] = useState<[Dayjs, Dayjs] | null>(null);
  const [selectedPeriodCode, setSelectedPeriodCode] = useState<string | null>(null);
  const [periodReferenceDate, setPeriodReferenceDate] = useState(() =>
    dayjs().format("YYYY-MM-DD")
  );
  const [nativeStartDate, setNativeStartDate] = useState("");
  const [nativeEndDate, setNativeEndDate] = useState("");
  const [selectedFilterId, setSelectedFilterId] = useState<string | null>(null);
  const [selectedRowKeys, setSelectedRowKeys] = useState<string[]>([]);
  const [selectedRows, setSelectedRows] = useState<PerformanceRow[]>([]);
  const [sortState, setSortState] = useState<SortState>({
    columnKey: null,
    order: null,
  });
  const [selectionSeries, setSelectionSeries] = useState<SelectionSeries[]>([]);
  const [selectionLoading, setSelectionLoading] = useState(false);
  const [selectionError, setSelectionError] = useState<string | null>(null);
  const [expandedPortfolioKeys, setExpandedPortfolioKeys] = useState<string[]>([]);
  const mobileCarouselRef = useRef<CarouselRef | null>(null);
  const [mobileActiveSlide, setMobileActiveSlide] = useState(0);
  const [isMobileLayout, setIsMobileLayout] = useState(
    () => window.innerWidth <= 500
  );
  const skipNextSaveRef = useRef(false);
  const [showFlatSecurities, setShowFlatSecurities] = useState(false);
  const [listSortMode, setListSortMode] = useState<"alpha" | "performance">(
    "performance"
  );
  const [listSortDirection, setListSortDirection] = useState<"asc" | "desc">(
    "desc"
  );
  const [listPerfDirection, setListPerfDirection] = useState<"asc" | "desc">(
    "desc"
  );
  const lastQuoteProgressTsRef = useRef<number | null>(null);
  const isNativePlatform = Capacitor.isNativePlatform();
  const quoteProgress = useSubscription<{
    quoteUpdates: { completedTaskCount: number; taskCount: number; timestamp: number } | null;
  }>(QUOTE_UPDATES_SUBSCRIPTION, {
    variables: { clientId: currentClient?.id ?? null },
    skip: !currentClient?.id,
  });

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
  const zoomRange = formattedDates
    ? { startDate: formattedDates.startDate, endDate: formattedDates.endDate }
    : null;

  const reportingPeriodsQuery = useQuery<{
    reportingPeriods:
      | Array<{
          code: string | null;
          label: string | null;
          startDate: string | null;
          endDate: string | null;
        }>
      | null;
  }>(REPORTING_PERIODS_QUERY, {
    variables: {
      clientId: currentClient?.id ?? null,
      referenceDate: periodReferenceDate,
    },
    fetchPolicy: "network-only",
    skip: !currentClient?.id,
  });

  const reportingPeriods =
    reportingPeriodsQuery.data?.reportingPeriods ?? [];

  useEffect(() => {
    if (!currentClient?.id) {
      setDateRange(null);
      setSelectedPeriodCode(null);
      setPeriodReferenceDate(dayjs().format("YYYY-MM-DD"));
      setSelectedFilterId(null);
      setSelectedRowKeys([]);
      setSortState({ columnKey: null, order: null });
      setExpandedPortfolioKeys([]);
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
        periodCode?: string | null;
        periodReferenceDate?: string | null;
        filterId?: string | null;
        selectedRowKeys?: string[] | null;
        sortState?: { columnKey?: string | null; order?: "ascend" | "descend" | null } | null;
      };
      if (parsed.startDate && parsed.endDate) {
        setDateRange([dayjs(parsed.startDate), dayjs(parsed.endDate)]);
      } else {
        setDateRange(null);
      }
      setSelectedPeriodCode(parsed.periodCode ?? null);
      setPeriodReferenceDate(
        parsed.periodReferenceDate ?? dayjs().format("YYYY-MM-DD")
      );
      setSelectedFilterId(parsed.filterId ?? null);
      setSelectedRowKeys(parsed.selectedRowKeys ?? []);
      setSortState({
        columnKey: parsed.sortState?.columnKey ?? null,
        order: parsed.sortState?.order ?? null,
      });
      setExpandedPortfolioKeys([]);
    } catch {
      setDateRange(null);
      setSelectedPeriodCode(null);
      setPeriodReferenceDate(dayjs().format("YYYY-MM-DD"));
      setSelectedFilterId(null);
      setSelectedRowKeys([]);
      setSortState({ columnKey: null, order: null });
      setExpandedPortfolioKeys([]);
    }
  }, [currentClient?.id]);

  useEffect(() => {
    const updateLayoutMode = () => {
      setIsMobileLayout(window.innerWidth <= 500);
    };

    updateLayoutMode();
    window.addEventListener("resize", updateLayoutMode);

    return () => {
      window.removeEventListener("resize", updateLayoutMode);
    };
  }, []);

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
      periodCode: selectedPeriodCode,
      periodReferenceDate,
      filterId: selectedFilterId,
      selectedRowKeys,
      sortState,
    };
    localStorage.setItem(
      `performanceSettings:${currentClient.id}`,
      JSON.stringify(payload)
    );
  }, [
    currentClient?.id,
    dateRange,
    periodReferenceDate,
    selectedPeriodCode,
    selectedFilterId,
    selectedRowKeys,
    sortState,
  ]);

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

  const portfolioPerformanceQuery = useQuery<{
    portfolioSecurityPerformance: PortfolioPerformanceResult | null;
  }>(PORTFOLIO_SECURITY_PERFORMANCE_QUERY, {
    variables: {
      clientId: currentClient?.id,
      filterId: selectedFilterId,
      startDate: formattedDates?.startDate,
      endDate: formattedDates?.endDate,
    },
    fetchPolicy: "no-cache",
    skip: !shouldFetch,
  });

  const [updateQuotes, updateQuotesState] = useMutation<{
    updateQuotes: { scheduled: boolean; securityCount: number | null } | null;
  }>(UPDATE_QUOTES_MUTATION);

  const mapDeltaPoints = useCallback(
    (
      points: Array<{
        date: string | null;
        value: { amount: number | null; currencyCode: string | null } | null;
      }>
    ) =>
      points.map((point) => ({
        date: point?.date ?? "",
        amount: point?.value?.amount ?? 0,
        timestamp: point?.date ? dayjs(point.date).valueOf() : 0,
      })),
    []
  );

  const chartData = useMemo(() => {
    const points = accumulatedQuery.data?.clientFilterAccumulatedDelta ?? [];
    return mapDeltaPoints(points);
  }, [accumulatedQuery.data, mapDeltaPoints]);

  const filters = useMemo(() => filtersData?.clientFilters ?? [], [filtersData]);

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

      // Nettoyer le cache pour forcer un rechargement complet des données.
      try {
        await apolloClient.clearStore();
      } catch {
        // ignore cache clear errors, we'll still refetch below
      }

      if (shouldFetch) {
        await Promise.all([
          deltaQuery.refetch(),
          accumulatedQuery.refetch(),
          portfolioPerformanceQuery.refetch(),
        ]);
      }
    } catch {
      message.error("Erreur lors de l'actualisation des titres.");
    }
  };

  const updateNativeRange = (startValue: string, endValue: string) => {
    setSelectedPeriodCode(null);
    if (startValue && endValue) {
      setDateRange([dayjs(startValue), dayjs(endValue)]);
      return;
    }
    setDateRange(null);
  };

  const handlePeriodChange = (value: string | null | undefined) => {
    const nextValue = value ?? null;
    setSelectedPeriodCode(nextValue);
    if (!nextValue) return;

    const selected = reportingPeriods.find((period) => period?.code === nextValue);
    if (selected?.startDate && selected?.endDate) {
      const nextRange: [Dayjs, Dayjs] = [
        dayjs(selected.startDate),
        dayjs(selected.endDate),
      ];
      setDateRange(nextRange);
      setPeriodReferenceDate(dayjs().format("YYYY-MM-DD"));
    }
  };

  const hasChartData = useMemo(
    () =>
      chartData.length > 0 ||
      selectionSeries.some((series) => series.points.length > 0),
    [chartData, selectionSeries]
  );

  const chartOptions = useMemo<EChartsOption>(() => {
    const series = [];

    if (chartData.length) {
      series.push({
        name: "Accumulated Delta",
        type: "line",
        showSymbol: false,
        smooth: false,
        data: chartData.map((point) => [point.timestamp, point.amount]),
      });
    }

    selectionSeries.forEach((seriesEntry) => {
      if (!seriesEntry.points.length) return;
      series.push({
        name: seriesEntry.name,
        type: "line",
        showSymbol: false,
        smooth: false,
        data: seriesEntry.points.map((point) => [point.timestamp, point.amount]),
      });
    });

    return {
      animation: false,
      color: CHART_COLORS,
      legend: { show: false },
      tooltip: {
        trigger: "axis",
        // On desktop we want the tooltip content back; keep it hidden on mobile
        show: !isMobileLayout,
        showContent: !isMobileLayout,
        valueFormatter: (value) =>
          Number(value).toLocaleString("fr-FR", {
            maximumFractionDigits: 2,
          }),
      },
      grid: { left: 12, right: 12, top: 16, bottom: 60 },
      xAxis: {
        type: "time",
        axisLabel: {
          formatter: (value: number | string) =>
            dayjs(value).format("YYYY-MM-DD"),
        },
      },
      yAxis: {
        type: "value",
        axisLabel: {
          // ellipsis: true,
          inside: true,
          // align: "right",
          margin: 8,
          formatter: (value: number) =>
            Number(value).toLocaleString("fr-FR", {
              maximumFractionDigits: 2,
            }),
        },
      },
      series,
    };
  }, [chartData, isMobileLayout, selectionSeries]);

  const selectionColors = useMemo<Record<string, string>>(() => {
    const offset = chartData.length ? 1 : 0;
    const map: Record<string, string> = {};
    selectionSeries.forEach((seriesEntry, index) => {
      const colorIndex = (offset + index) % CHART_COLORS.length;
      map[seriesEntry.key] = CHART_COLORS[colorIndex];
    });
    return map;
  }, [chartData.length, selectionSeries]);

  useEffect(() => {
    if (!selectedPeriodCode) return;
    const today = dayjs().format("YYYY-MM-DD");
    if (periodReferenceDate !== today) {
      setPeriodReferenceDate(today);
    }
  }, [periodReferenceDate, selectedPeriodCode]);

  useEffect(() => {
    if (!selectedPeriodCode) return;
    const selected = reportingPeriods.find((period) => period?.code === selectedPeriodCode);
    if (!selected?.startDate || !selected?.endDate) return;

    const nextRange: [Dayjs, Dayjs] = [
      dayjs(selected.startDate),
      dayjs(selected.endDate),
    ];

    const isSameRange =
      dateRange &&
      dateRange[0].isSame(nextRange[0], "day") &&
      dateRange[1].isSame(nextRange[1], "day");

    if (!isSameRange) {
      setDateRange(nextRange);
    }
  }, [dateRange, reportingPeriods, selectedPeriodCode]);

  const formatAmount = (amount: number | null, currencyCode: string | null) => {
    if (amount === null || amount === undefined) {
      return "-";
    }
    if (currencyCode) {
      return amount.toLocaleString("fr-FR", {
        style: "currency",
        currency: currencyCode,
        maximumFractionDigits: 2,
      });
    }
    return amount.toLocaleString("fr-FR", { maximumFractionDigits: 2 });
  };

  const formatPercent = (value: number | null | undefined) => {
    if (value === null || value === undefined) {
      return "-";
    }
    return `${value.toLocaleString("fr-FR", {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    })} %`;
  };

  const performanceRows = useMemo<PerformanceRow[]>(() => {
    const result = portfolioPerformanceQuery.data?.portfolioSecurityPerformance;
    const portfolios = result?.portfolios?.filter(isNonNullable) ?? [];
    const securities = result?.securities?.filter(isNonNullable) ?? [];

    return portfolios.map((portfolio, portfolioIndex) => {
      const portfolioKey =
        portfolio.portfolioId ?? `portfolio-${portfolioIndex}`;
      const children: PerformanceRow[] = securities
        .filter((security) => security.portfolioId === portfolio.portfolioId)
        .map((security, securityIndex) => ({
          key: security.id ?? `${portfolioKey}-security-${securityIndex}`,
          name: security.name ?? "-",
          deltaAmount: security.delta?.amount ?? null,
          deltaCurrency: security.delta?.currencyCode ?? null,
          deltaPercent: security.deltaPercent ?? null,
          startAmount: security.startValue?.amount ?? null,
          startCurrency: security.startValue?.currencyCode ?? null,
          portfolioId: security.portfolioId ?? null,
          securityId: security.securityId ?? null,
          rowType: "security",
          taxonomyAssignments: security.taxonomyAssignments ?? undefined,
        }));

      const portfolioName = portfolio.portfolioName ?? "-";
      const referenceName = portfolio.referenceAccountName ?? "-";

      return {
        key: portfolioKey,
        name:
          portfolioName === "-"
            ? referenceName
            : `${portfolioName} (${referenceName})`,
        deltaAmount: portfolio.delta?.amount ?? null,
        deltaCurrency: portfolio.delta?.currencyCode ?? null,
        deltaPercent: portfolio.deltaPercent ?? null,
        startAmount: portfolio.startValue?.amount ?? null,
        startCurrency: portfolio.startValue?.currencyCode ?? null,
        portfolioId: portfolio.portfolioId ?? null,
        securityId: null,
        rowType: "portfolio",
        children: children.length ? children : undefined,
      };
    });
  }, [portfolioPerformanceQuery.data]);

  const compareRows = useCallback(
    (a: PerformanceRow, b: PerformanceRow) => {
      if (listSortMode === "alpha") {
        const nameA = a.name ?? "";
        const nameB = b.name ?? "";
        return listSortDirection === "asc"
          ? nameA.localeCompare(nameB, "fr")
          : nameB.localeCompare(nameA, "fr");
      }
      const leftDelta = a.deltaAmount ?? 0;
      const rightDelta = b.deltaAmount ?? 0;
      return listPerfDirection === "asc" ? leftDelta - rightDelta : rightDelta - leftDelta;
    },
    [listPerfDirection, listSortDirection, listSortMode]
  );

  const sortRowsWithChildren = useCallback(
    (rows: PerformanceRow[]) => {
      const sortedParents = [...rows].sort(compareRows);
      return sortedParents.map((row) => {
        if (!row.children?.length) return row;
        const sortedChildren = [...row.children].sort(compareRows);
        return { ...row, children: sortedChildren };
      });
    },
    [compareRows]
  );

  const sortedPerformanceRows = useMemo(
    () => sortRowsWithChildren(performanceRows),
    [performanceRows, sortRowsWithChildren]
  );

  const securitiesOnlyRows = useMemo(() => {
    const rows = sortedPerformanceRows.flatMap((row) =>
      row.children?.length ? row.children.map((child) => ({ ...child })) : []
    );
    rows.sort(compareRows);
    return rows;
  }, [compareRows, sortedPerformanceRows]);

  const mobilePerformanceRows = useMemo(() => sortedPerformanceRows, [sortedPerformanceRows]);

  const flatPerformanceRows = useMemo(() => {
    const flatten = (rows: PerformanceRow[]) =>
      rows.flatMap((row) => [row, ...(row.children ? flatten(row.children) : [])]);
    return flatten(sortedPerformanceRows);
  }, [sortedPerformanceRows]);

  useEffect(() => {
    const progress = quoteProgress.data?.quoteUpdates;
    if (!progress) return;
    if (!progress.taskCount) return;
    if (progress.completedTaskCount < progress.taskCount) return;
    if (lastQuoteProgressTsRef.current === progress.timestamp) return;

    lastQuoteProgressTsRef.current = progress.timestamp;

    const refreshData = async () => {
      try {
        await apolloClient.clearStore();
      } catch {
        // ignore cache clear errors
      }
      if (shouldFetch) {
        await Promise.all([
          deltaQuery.refetch(),
          accumulatedQuery.refetch(),
          portfolioPerformanceQuery.refetch(),
        ]);
      }
    };

    void refreshData();
  }, [
    accumulatedQuery,
    apolloClient,
    deltaQuery,
    portfolioPerformanceQuery,
    quoteProgress.data?.quoteUpdates,
    shouldFetch,
  ]);

  useEffect(() => {
    if (!selectedRowKeys.length) {
      if (selectedRows.length) {
        setSelectedRows([]);
      }
      return;
    }

    const nextRows = flatPerformanceRows.filter((row) =>
      selectedRowKeys.includes(row.key)
    );

    if (
      nextRows.length !== selectedRows.length ||
      nextRows.some((row, index) => row.key !== selectedRows[index]?.key)
    ) {
      setSelectedRows(nextRows);
    }
  }, [flatPerformanceRows, selectedRowKeys, selectedRows]);

  useEffect(() => {
    if (!shouldFetch) {
      setSelectedRowKeys([]);
      setSelectedRows([]);
      setSelectionSeries([]);
      setSelectionError(null);
      setSelectionLoading(false);
      setExpandedPortfolioKeys([]);
    }
  }, [shouldFetch]);

  useEffect(() => {
    if (!shouldFetch || !formattedDates || !selectedRows.length) {
      setSelectionSeries([]);
      setSelectionError(null);
      setSelectionLoading(false);
      return;
    }

    let isCancelled = false;
    setSelectionLoading(true);
    setSelectionError(null);

    const loadSelectionSeries = async () => {
      const results = await Promise.all(
        selectedRows.map(async (row) => {
          const { data } = await apolloClient.query({
            query: CLIENT_FILTER_ACCUMULATED_QUERY,
            variables: {
              clientId: currentClient?.id ?? null,
              filterId: selectedFilterId,
              startDate: formattedDates.startDate,
              endDate: formattedDates.endDate,
              portfolioId: row.portfolioId,
              securityId: row.securityId,
            },
            fetchPolicy: "network-only",
          });

          const points = mapDeltaPoints(
            data?.clientFilterAccumulatedDelta ?? []
          );

          return {
            key: row.key,
            name: row.name,
            points,
          };
        })
      );

      if (!isCancelled) {
        setSelectionSeries(results);
      }
    };

    loadSelectionSeries()
      .catch(() => {
        if (!isCancelled) {
          setSelectionError(
            "Erreur lors du chargement des courbes sélectionnées."
          );
          setSelectionSeries([]);
        }
      })
      .finally(() => {
        if (!isCancelled) {
          setSelectionLoading(false);
        }
      });

    return () => {
      isCancelled = true;
    };
  }, [
    apolloClient,
    currentClient?.id,
    mapDeltaPoints,
    selectedRows,
    selectedFilterId,
    shouldFetch,
    formattedDates,
  ]);

  const handleRowSelectionChange = (
    keys: Array<string | number>,
    rows: PerformanceRow[]
  ) => {
    setSelectedRowKeys(keys.map(String));
    setSelectedRows(rows);
  };

  const handleListSelectionChange = (row: PerformanceRow, checked: boolean) => {
    const nextKeys = checked
      ? Array.from(new Set([...selectedRowKeys, row.key]))
      : selectedRowKeys.filter((key) => key !== row.key);
    setSelectedRowKeys(nextKeys);
    setSelectedRows(
      flatPerformanceRows.filter((item) => nextKeys.includes(item.key))
    );
  };

  const togglePortfolioExpanded = (rowKey: string) => {
    setExpandedPortfolioKeys((prev) =>
      prev.includes(rowKey)
        ? prev.filter((key) => key !== rowKey)
        : [...prev, rowKey]
    );
  };

  const handleTableChange = (
    _pagination: unknown,
    _filters: unknown,
    sorter:
      | { order?: "ascend" | "descend" | null; columnKey?: string }
      | Array<{ order?: "ascend" | "descend" | null; columnKey?: string }>
  ) => {
    const nextSorter = Array.isArray(sorter) ? sorter[0] : sorter;
    setSortState({
      columnKey: nextSorter?.columnKey ?? null,
      order: nextSorter?.order ?? null,
    });
  };

  const performanceTotals = useMemo<PerformanceTotals>(() => {
    const result = portfolioPerformanceQuery.data?.portfolioSecurityPerformance;
    const totalEntry = result?.total;

    if (totalEntry) {
      const totalAmount = totalEntry.delta?.amount ?? null;
      const startAmount = totalEntry.startValue?.amount ?? null;
      return {
        totalAmount,
        hasAmount: totalAmount !== null && totalAmount !== undefined,
        currencyCode: totalEntry.delta?.currencyCode ?? null,
        percentValue: totalEntry.deltaPercent ?? null,
        startAmount,
        hasStartAmount: startAmount !== null && startAmount !== undefined,
        startCurrency: totalEntry.startValue?.currencyCode ?? null,
        percentSum: 0,
        percentCount: 0,
      };
    }

    const portfolios = result?.portfolios?.filter(isNonNullable) ?? [];

    return portfolios.reduce(
      (accumulator, entry) => {
        const amount = entry.delta?.amount;
        if (amount !== null && amount !== undefined) {
          accumulator.totalAmount += amount;
          accumulator.hasAmount = true;
        }

        const startAmount = entry.startValue?.amount;
        if (startAmount !== null && startAmount !== undefined) {
          accumulator.startAmount += startAmount;
          accumulator.hasStartAmount = true;
        }

        if (!accumulator.currencyCode && entry.delta?.currencyCode) {
          accumulator.currencyCode = entry.delta.currencyCode;
        }

        if (!accumulator.startCurrency && entry.startValue?.currencyCode) {
          accumulator.startCurrency = entry.startValue.currencyCode;
        }

        const percent = entry.deltaPercent;
        if (percent !== null && percent !== undefined) {
          accumulator.percentSum += percent;
          accumulator.percentCount += 1;
        }

        return accumulator;
      },
      {
        totalAmount: 0,
        hasAmount: false,
        currencyCode: null as string | null,
        startAmount: 0,
        hasStartAmount: false,
        startCurrency: null as string | null,
        percentSum: 0,
        percentCount: 0,
      }
    );
  }, [portfolioPerformanceQuery.data]);

  const totalSummary = useMemo<TotalSummary>(() => {
    const totalAmount = performanceTotals.hasAmount
      ? performanceTotals.totalAmount
      : null;
    const totalStartAmount = performanceTotals.hasStartAmount
      ? performanceTotals.startAmount
      : null;
    const totalPercent =
      "percentValue" in performanceTotals &&
      performanceTotals.percentValue !== null
        ? performanceTotals.percentValue
        : performanceTotals.percentCount
          ? performanceTotals.percentSum / performanceTotals.percentCount
          : null;

    return {
      totalAmount,
      totalStartAmount,
      totalPercent,
    };
  }, [performanceTotals]);

  const parametersPanel = (
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
        <Space direction="vertical" size={4}>
          <Typography.Text>Période prédéfinie</Typography.Text>
          <Select
            placeholder={
              currentClient
                ? "Sélectionnez une période"
                : "Choisissez d'abord un client"
            }
            value={selectedPeriodCode}
            onChange={(value) => handlePeriodChange(value as string | undefined)}
            options={reportingPeriods.map((period) => ({
              value: period?.code ?? "",
              label: period?.label ?? period?.code ?? "",
            }))}
            loading={reportingPeriodsQuery.loading}
            disabled={!currentClient}
            allowClear
            style={{ minWidth: 280 }}
          />
        </Space>
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
                  {
                    setSelectedPeriodCode(null);
                    setDateRange(
                      value && value[0] && value[1] ? [value[0], value[1]] : null
                    );
                  }
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
  );

  return (
    <Space direction="vertical" size="large" className="page-stack">
      {!isMobileLayout ? parametersPanel : null}

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
          <Card title={isMobileLayout ? undefined : "Résultats"}>
            {isMobileLayout ? (
            SHOW_MOBILE_PLACEHOLDERS ? (
              <div className="performance-mobile-panels">
                <div className="performance-mobile-tabs">
                  <button
                    type="button"
                    className={`performance-mobile-tab${
                      mobileActiveSlide === 0 ? " performance-mobile-tab--active" : ""
                    }`}
                    onClick={() => {
                      setMobileActiveSlide(0);
                      mobileCarouselRef.current?.goTo(0);
                    }}
                  >
                    Graphe
                  </button>
                  <button
                    type="button"
                    className={`performance-mobile-tab${
                      mobileActiveSlide === 1 ? " performance-mobile-tab--active" : ""
                    }`}
                    onClick={() => {
                      setMobileActiveSlide(1);
                      mobileCarouselRef.current?.goTo(1);
                    }}
                  >
                    Liste
                  </button>
                </div>
                <Carousel
                  ref={mobileCarouselRef}
                  dots
                  draggable
                  swipeToSlide
                  afterChange={(current) => setMobileActiveSlide(current)}
                >
                  <div>
                    <div className="performance-mobile-panel">
                      <Typography.Text>Graphe</Typography.Text>
                    </div>
                  </div>
                  <div>
                    <div className="performance-mobile-panel">
                      <Typography.Text>Liste</Typography.Text>
                    </div>
                  </div>
                </Carousel>
              </div>
            ) : (
              <PerformanceResultsMobile
                delta={delta}
                deltaLoading={deltaQuery.loading}
              accumulatedLoading={accumulatedQuery.loading}
              selectionLoading={selectionLoading}
              hasChartData={hasChartData}
              chartOptions={chartOptions}
              selectionColors={selectionColors}
              selectionError={selectionError}
              portfolioError={portfolioPerformanceQuery.error?.message ?? null}
              portfolioLoading={portfolioPerformanceQuery.loading}
              parametersPanel={parametersPanel}
              performanceRows={performanceRows}
              mobilePerformanceRows={mobilePerformanceRows}
              showFlatSecurities={showFlatSecurities}
              securitiesRows={securitiesOnlyRows}
              listSortDirection={listSortDirection}
              listPerfDirection={listPerfDirection}
              onToggleListSortDirection={() => {
                setListSortMode("alpha");
                setListSortDirection((previous) => (previous === "asc" ? "desc" : "asc"));
              }}
              onTogglePerfSortDirection={() => {
                setListSortMode("performance");
                setListPerfDirection((previous) => (previous === "asc" ? "desc" : "asc"));
              }}
              onToggleFlatSecurities={() =>
                setShowFlatSecurities((previous) => !previous)
              }
              expandedPortfolioKeys={expandedPortfolioKeys}
              selectedRowKeys={selectedRowKeys}
              zoomRange={zoomRange}
              totalSummary={totalSummary}
              performanceTotals={performanceTotals}
              onTogglePortfolioExpanded={togglePortfolioExpanded}
              onListSelectionChange={handleListSelectionChange}
              formatAmount={formatAmount}
              formatPercent={formatPercent}
            />
          )
        ) : (
          <PerformanceResultsDesktop
            delta={delta}
            deltaLoading={deltaQuery.loading}
            accumulatedLoading={accumulatedQuery.loading}
            selectionLoading={selectionLoading}
            hasChartData={hasChartData}
            chartOptions={chartOptions}
            selectionError={selectionError}
            portfolioError={portfolioPerformanceQuery.error?.message ?? null}
            portfolioLoading={portfolioPerformanceQuery.loading}
              performanceRows={performanceRows}
              securitiesRows={securitiesOnlyRows}
              totalSummary={totalSummary}
              performanceTotals={performanceTotals}
              zoomRange={zoomRange}
              showFlatSecurities={showFlatSecurities}
              selectedRowKeys={selectedRowKeys}
            sortState={sortState}
            onToggleFlatSecurities={() =>
              setShowFlatSecurities((previous) => !previous)
            }
            onRowSelectionChange={handleRowSelectionChange}
            onTableChange={handleTableChange}
            formatAmount={formatAmount}
            formatPercent={formatPercent}
          />
          )}
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
