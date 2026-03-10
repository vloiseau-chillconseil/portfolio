import { gql, useMutation, useQuery } from "@apollo/client";
import type { MutationFunction } from "@apollo/client";
import {
  Alert,
  Button,
  Card,
  Checkbox,
  Form,
  Input,
  Modal,
  Select,
  Space,
  Table,
  Tabs,
  Typography,
} from "antd";
import type { TabsProps } from "antd";
import { SearchOutlined } from "@ant-design/icons";
import { useCallback, useEffect, useMemo, useState } from "react";
import { useUserReactUIPreference } from "../services/userReactUiPreferences";
import { useCurrentClient } from "../state/currentClientContext";

type SecurityAttribute = {
  attrid: string;
  name: string | null;
  value: string | null;
};

type SecurityInfo = {
  id: string;
  name: string | null;
  currencyCode: string | null;
  targetCurrencyCode: string | null;
  note: string | null;
  isin: string | null;
  tickerSymbol: string | null;
  wkn: string | null;
  calendar: string | null;
  feed: string | null;
  feedURL: string | null;
  latestFeed: string | null;
  latestFeedURL: string | null;
  onlineId: string | null;
  retired: boolean | null;
  attributes: SecurityAttribute[] | null;
};

type EditableCellProps = {
  title: string;
  editable: boolean;
  dataIndex: string;
  record: SecurityInfo;
  editing: boolean;
  onSave: (record: SecurityInfo) => void;
  onCancel: () => void;
  onEdit: (record: SecurityInfo, dataIndex: string) => void;
  children?: React.ReactNode;
};

type SecurityQuote = {
  currencyCode: string | null;
  date: string | null;
  value: number | null;
};

type SecurityAttributeType = {
  id: string;
  name: string | null;
};


const SECURITIES_QUERY = gql`
  query Securities($clientId: String) {
    securities(clientId: $clientId) {
      id
      name
      currencyCode
      targetCurrencyCode
      note
      isin
      tickerSymbol
      wkn
      calendar
      feed
      feedURL
      latestFeed
      latestFeedURL
      onlineId
      retired
      attributes {
        attrid
        name
        value
      }
    }
  }
`;

const SECURITY_QUOTES_QUERY = gql`
  query SecurityQuotes($clientId: String, $securityId: String) {
    securityQuotes(clientId: $clientId, securityId: $securityId) {
      currencyCode
      date
      value
    }
  }
`;

const SECURITY_ATTRIBUTES_QUERY = gql`
  query SecurityAttributes($clientId: String) {
    securityAttributes(clientId: $clientId) {
      id
      name
    }
  }
`;

const CREATE_SECURITY_MUTATION = gql`
  mutation CreateSecurity(
    $clientId: String
    $name: String
    $currencyCode: String
    $targetCurrencyCode: String
    $note: String
    $isin: String
    $tickerSymbol: String
    $wkn: String
    $calendar: String
    $feed: String
    $feedURL: String
    $latestFeed: String
    $latestFeedURL: String
    $onlineId: String
    $retired: Boolean
    $attributes: [SecurityAttributeInputInput]
  ) {
    createSecurity(
      clientId: $clientId
      name: $name
      currencyCode: $currencyCode
      targetCurrencyCode: $targetCurrencyCode
      note: $note
      isin: $isin
      tickerSymbol: $tickerSymbol
      wkn: $wkn
      calendar: $calendar
      feed: $feed
      feedURL: $feedURL
      latestFeed: $latestFeed
      latestFeedURL: $latestFeedURL
      onlineId: $onlineId
      retired: $retired
      attributes: $attributes
    ) {
      id
      name
      currencyCode
      targetCurrencyCode
      note
      isin
      tickerSymbol
      wkn
      calendar
      feed
      feedURL
      latestFeed
      latestFeedURL
      onlineId
      retired
      attributes {
        attrid
        name
        value
      }
    }
  }
`;

const UPDATE_SECURITY_MUTATION = gql`
  mutation UpdateSecurity(
    $clientId: String
    $securityId: String
    $name: String
    $currencyCode: String
    $targetCurrencyCode: String
    $note: String
    $isin: String
    $tickerSymbol: String
    $wkn: String
    $calendar: String
    $feed: String
    $feedURL: String
    $latestFeed: String
    $latestFeedURL: String
    $onlineId: String
    $retired: Boolean
    $attributes: [SecurityAttributeInputInput]
  ) {
    updateSecurity(
      clientId: $clientId
      securityId: $securityId
      name: $name
      currencyCode: $currencyCode
      targetCurrencyCode: $targetCurrencyCode
      note: $note
      isin: $isin
      tickerSymbol: $tickerSymbol
      wkn: $wkn
      calendar: $calendar
      feed: $feed
      feedURL: $feedURL
      latestFeed: $latestFeed
      latestFeedURL: $latestFeedURL
      onlineId: $onlineId
      retired: $retired
      attributes: $attributes
    ) {
      id
      name
      currencyCode
      targetCurrencyCode
      note
      isin
      tickerSymbol
      wkn
      calendar
      feed
      feedURL
      latestFeed
      latestFeedURL
      onlineId
      retired
      attributes {
        attrid
        name
        value
      }
    }
  }
`;

const serializeAttributes = (attributes: SecurityAttribute[] | null | undefined) => {
  if (!attributes?.length) return "";
  return attributes
    .map((attribute) => `${attribute.attrid}=${attribute.value ?? ""}`)
    .join("; ");
};

const parseAttributes = (input: string) => {
  if (!input.trim()) return [] as Array<{ id: string; value: string | null }>;
  return input
    .split(/\n|;|,/)
    .map((entry) => entry.trim())
    .filter(Boolean)
    .map((entry) => {
      const [id, ...rest] = entry.split("=");
      return {
        id: id.trim(),
        value: rest.length ? rest.join("=").trim() : "",
      };
    })
    .filter((entry) => entry.id.length > 0);
};

const EditableCell = ({
  title,
  editable,
  dataIndex,
  record,
  editing,
  onSave,
  onCancel,
  onEdit,
  children,
  ...restProps
}: EditableCellProps & React.HTMLAttributes<HTMLElement>) => {
  if (!editable) {
    return <td {...restProps}>{children}</td>;
  }

  const input = dataIndex === "note" ? (
      <Input.TextArea
        autoSize={{ minRows: 1, maxRows: 4 }}
        autoFocus
        onFocus={(event) => event.target.select()}
        onKeyDown={(event) => {
          if (event.key === "Escape") {
            event.preventDefault();
            onCancel();
          }
          if (event.key === "Enter" && !event.shiftKey) {
            event.preventDefault();
            onSave(record);
          }
        }}
        onBlur={() => onCancel()}
      />
    ) : (
      <Input
        autoFocus
        onFocus={(event) => event.target.select()}
        onKeyDown={(event) => {
          if (event.key === "Escape") {
            event.preventDefault();
            onCancel();
          }
          if (event.key === "Enter") {
            event.preventDefault();
            onSave(record);
          }
        }}
        onBlur={() => onCancel()}
      />
    );

  return (
    <td
      {...restProps}
      onDoubleClick={() => onEdit(record, dataIndex)}
      style={{ cursor: "pointer" }}
    >
      {editing ? (
        <Form.Item
          name={dataIndex}
          style={{ margin: 0 }}
          rules={
            dataIndex === "name"
              ? [{ required: true, message: `${title} est requis` }]
              : undefined
          }
        >
          {input}
        </Form.Item>
      ) : (
        children
      )}
    </td>
  );
};

const SecuritiesPage = () => {
  const { currentClient } = useCurrentClient();
  const [securities, setSecurities] = useState<SecurityInfo[]>([]);
  const [activeSecurity, setActiveSecurity] = useState<SecurityInfo | null>(null);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [dialogMode, setDialogMode] = useState<"create" | "edit">("edit");
  const [editingCell, setEditingCell] = useState<{
    id: string;
    dataIndex: string;
  } | null>(null);
  const [form] = Form.useForm();
  const USER_PREF_KEY = "securities.visibleColumns";

  const { data, loading } = useQuery<{ securities: SecurityInfo[] | null }>(
    SECURITIES_QUERY,
    {
      variables: { clientId: currentClient?.id ?? null },
      skip: !currentClient?.id,
    }
  );

  const { data: attributeData } = useQuery<{
    securityAttributes: SecurityAttributeType[] | null;
  }>(SECURITY_ATTRIBUTES_QUERY, {
    variables: { clientId: currentClient?.id ?? null },
    skip: !currentClient?.id,
  });

  const [createSecurity, { loading: creating }] = useMutation(
    CREATE_SECURITY_MUTATION
  );
  const [updateSecurity, { loading: updating }] = useMutation(
    UPDATE_SECURITY_MUTATION
  );

  useEffect(() => {
    setSecurities(data?.securities ?? []);
  }, [data?.securities]);

  const defaultVisibleColumns = useMemo(() => {
    const base = ["name", "isin", "tickerSymbol", "wkn", "note"];
    const attributes = (attributeData?.securityAttributes ?? []).map(
      (attribute) => `attribute:${attribute.id}`
    );
    return [...base, ...attributes];
  }, [attributeData?.securityAttributes]);

  const parseVisibleColumns = useCallback(
    (raw: string | null) => {
      if (!raw) return defaultVisibleColumns;
      try {
        const parsed = JSON.parse(raw) as string[];
        return Array.isArray(parsed) ? parsed : defaultVisibleColumns;
      } catch {
        return defaultVisibleColumns;
      }
    },
    [defaultVisibleColumns]
  );

  const serializeVisibleColumns = useCallback(
    (value: string[]) => JSON.stringify(value),
    []
  );

  const {
    value: visibleColumnKeys,
    setValue: setVisibleColumnKeys,
    loaded: prefLoaded,
  } = useUserReactUIPreference<string[]>({
    clientId: currentClient?.id ?? null,
    key: USER_PREF_KEY,
    defaultValue: defaultVisibleColumns,
    parse: parseVisibleColumns,
    serialize: serializeVisibleColumns,
  });

  const handleOpenCreate = () => {
    setDialogMode("create");
    setActiveSecurity(null);
    setDialogOpen(true);
  };

  const handleOpenEdit = (security: SecurityInfo) => {
    setDialogMode("edit");
    setActiveSecurity(security);
    setDialogOpen(true);
  };

  const updateSecurityState = (updated: SecurityInfo) => {
    setSecurities((current) =>
      current.map((security) =>
        security.id === updated.id ? updated : security
      )
    );
  };

  const isEditing = (record: SecurityInfo, dataIndex: string) =>
    Boolean(editingCell && editingCell.id === record.id && editingCell.dataIndex === dataIndex);

  const startEdit = (record: SecurityInfo, dataIndex: string) => {
    if (editingCell) {
      return;
    }
    const initialValue = getFieldValue(record, dataIndex);
    form.setFieldsValue({ [dataIndex]: initialValue });
    setEditingCell({ id: record.id, dataIndex });
  };

  const cancelEdit = () => {
    setEditingCell(null);
    form.resetFields();
  };

  const saveEdit = async (record: SecurityInfo) => {
    if (!currentClient?.id) return;
    if (!editingCell) return;
    const values = await form.validateFields([editingCell.dataIndex]);
    const variables: Record<string, unknown> = {
      clientId: currentClient.id,
      securityId: record.id,
    };
    if (editingCell.dataIndex.startsWith("attribute:")) {
      const attributeId = editingCell.dataIndex.slice("attribute:".length);
      variables.attributes = [
        { id: attributeId, value: values[editingCell.dataIndex] ?? "" },
      ];
    } else {
      variables[editingCell.dataIndex] = values[editingCell.dataIndex];
    }
    const { data: mutationData } = await updateSecurity({ variables });
    const updated = mutationData?.updateSecurity as SecurityInfo | null;
    if (updated) {
      updateSecurityState(updated);
    }
    setEditingCell(null);
  };

  const getFieldValue = (record: SecurityInfo, dataIndex: string) => {
    if (dataIndex.startsWith("attribute:")) {
      const attributeId = dataIndex.slice("attribute:".length);
      const match = record.attributes?.find(
        (attribute) => attribute.attrid === attributeId
      );
      return match?.value ?? "";
    }
    return (record as Record<string, string | null | undefined>)[dataIndex] ?? "";
  };

  const buildSorter = useCallback(
    (dataIndex: string) => (a: SecurityInfo, b: SecurityInfo) =>
      getFieldValue(a, dataIndex).localeCompare(getFieldValue(b, dataIndex)),
    []
  );

  const buildTextFilterProps = useCallback(
    (dataIndex: string) => ({
      filterDropdown: ({
        setSelectedKeys,
        selectedKeys,
        confirm,
        clearFilters,
      }: {
        setSelectedKeys: (keys: React.Key[]) => void;
        selectedKeys: React.Key[];
        confirm: () => void;
        clearFilters?: () => void;
      }) => (
        <div style={{ padding: 8 }}>
          <Input
            placeholder="Filtrer"
            value={selectedKeys[0] as string}
            onChange={(event) =>
              setSelectedKeys(event.target.value ? [event.target.value] : [])
            }
            onPressEnter={() => confirm()}
            style={{ marginBottom: 8, display: "block" }}
          />
          <Space>
            <Button
              type="primary"
              size="small"
              onClick={() => confirm()}
            >
              OK
            </Button>
            <Button
              size="small"
              onClick={() => {
                clearFilters?.();
                confirm();
              }}
            >
              Reset
            </Button>
          </Space>
        </div>
      ),
      filterIcon: (filtered: boolean) => (
        <SearchOutlined style={{ color: filtered ? "#1677ff" : undefined }} />
      ),
      onFilter: (value: string | number | boolean, record: SecurityInfo) =>
        getFieldValue(record, dataIndex)
          .toLowerCase()
          .includes(String(value).toLowerCase()),
    }),
    []
  );

  const columns = useMemo(
    () => [
      {
        title: "Nom",
        dataIndex: "name",
        editable: true,
        sorter: buildSorter("name"),
        ...buildTextFilterProps("name"),
      },
      {
        title: "ISIN",
        dataIndex: "isin",
        width: 160,
        editable: true,
        sorter: buildSorter("isin"),
        ...buildTextFilterProps("isin"),
      },
      {
        title: "Ticker",
        dataIndex: "tickerSymbol",
        width: 140,
        editable: true,
        sorter: buildSorter("tickerSymbol"),
        ...buildTextFilterProps("tickerSymbol"),
      },
      {
        title: "WKN",
        dataIndex: "wkn",
        width: 120,
        editable: true,
        sorter: buildSorter("wkn"),
        ...buildTextFilterProps("wkn"),
      },
      {
        title: "Note",
        dataIndex: "note",
        editable: true,
        sorter: buildSorter("note"),
        ...buildTextFilterProps("note"),
      },
      {
        title: "",
        key: "actions",
        width: 120,
        render: (_: unknown, security: SecurityInfo) => (
          <Button onClick={() => handleOpenEdit(security)}>Détails</Button>
        ),
      },
    ],
    [buildTextFilterProps, buildSorter]
  );

  const attributeColumns = useMemo(() => {
    return (attributeData?.securityAttributes ?? []).map((attribute) => {
      const dataIndex = `attribute:${attribute.id}`;
      return {
        title: attribute.name ?? attribute.id,
        dataIndex,
        editable: true,
        sorter: buildSorter(dataIndex),
        ...buildTextFilterProps(dataIndex),
        render: (_: unknown, security: SecurityInfo) =>
          getFieldValue(security, dataIndex),
      };
    });
  }, [attributeData?.securityAttributes, buildSorter, buildTextFilterProps]);

  const columnsWithAttributes = useMemo(() => {
    const baseColumns = columns.slice(0, columns.length - 1);
    const actionsColumn = columns[columns.length - 1];
    const allColumns = [...baseColumns, ...attributeColumns];
    const filteredColumns = allColumns.filter((column) =>
      visibleColumnKeys.includes(String(column.dataIndex))
    );
    return [...filteredColumns, actionsColumn];
  }, [columns, attributeColumns, visibleColumnKeys]);

  const mergedColumns = columnsWithAttributes.map((column) => {
    if (!(column as { editable?: boolean }).editable) {
      return column;
    }
    return {
      ...column,
      onCell: (record: SecurityInfo) => ({
        record,
        editable: (column as { editable?: boolean }).editable,
        dataIndex: column.dataIndex,
        title: column.title,
        editing: isEditing(record, column.dataIndex as string),
        onSave: saveEdit,
        onCancel: cancelEdit,
        onEdit: startEdit,
      }),
    };
  });

  const loadingState = loading || updating;
  const isReady = !currentClient?.id || (prefLoaded && !loading);

  return (
    <Space direction="vertical" size="large" className="page-stack">
      <Card
        title="Titres"
        extra={
          <Space wrap>
            <Select
              mode="multiple"
              placeholder="Colonnes"
              value={visibleColumnKeys}
              onChange={(value) => setVisibleColumnKeys(value)}
              options={[
                { label: "Nom", value: "name" },
                { label: "ISIN", value: "isin" },
                { label: "Ticker", value: "tickerSymbol" },
                { label: "WKN", value: "wkn" },
                { label: "Note", value: "note" },
                ...(attributeData?.securityAttributes ?? []).map((attribute) => ({
                  label: attribute.name ?? attribute.id,
                  value: `attribute:${attribute.id}`,
                })),
              ]}
              maxTagCount="responsive"
              style={{ minWidth: 220 }}
            />
            <Button type="primary" onClick={handleOpenCreate}>
              Nouveau titre
            </Button>
          </Space>
        }
      >
        {!currentClient?.id ? (
          <Alert
            type="warning"
            message="Sélectionnez un client dans l'accueil pour charger les titres."
          />
        ) : !isReady ? (
          <Table
            rowKey="id"
            dataSource={[]}
            columns={columnsWithAttributes}
            loading
            pagination={false}
          />
        ) : (
          <Form form={form} component={false}>
            <Table
              rowKey="id"
              dataSource={securities}
              columns={mergedColumns}
              loading={loadingState}
              pagination={false}
              scroll={{ x: true }}
              components={{
                body: {
                  cell: EditableCell,
                },
              }}
            />
          </Form>
        )}
      </Card>
      <SecurityDialog
        open={dialogOpen}
        mode={dialogMode}
        security={activeSecurity}
        clientId={currentClient?.id ?? null}
        baseCurrency={currentClient?.baseCurrency ?? ""}
        onClose={() => setDialogOpen(false)}
        onCreated={(security) => {
          setSecurities((current) => [security, ...current]);
          setDialogOpen(false);
        }}
        onUpdated={(security) => {
          updateSecurityState(security);
          setDialogOpen(false);
        }}
        createSecurity={createSecurity}
        updateSecurity={updateSecurity}
        saving={creating || updating}
      />
    </Space>
  );
};

type SecurityDialogProps = {
  open: boolean;
  mode: "create" | "edit";
  security: SecurityInfo | null;
  clientId: string | null;
  baseCurrency: string;
  onClose: () => void;
  onCreated: (security: SecurityInfo) => void;
  onUpdated: (security: SecurityInfo) => void;
  createSecurity: MutationFunction;
  updateSecurity: MutationFunction;
  saving: boolean;
};

const SecurityDialog = ({
  open,
  mode,
  security,
  clientId,
  baseCurrency,
  onClose,
  onCreated,
  onUpdated,
  createSecurity,
  updateSecurity,
  saving,
}: SecurityDialogProps) => {
  const [form] = Form.useForm();
  const [attributes, setAttributes] = useState<SecurityAttribute[]>([]);
  const [newAttributeId, setNewAttributeId] = useState("");
  const [newAttributeValue, setNewAttributeValue] = useState("");

  const { data: quotesData, loading: quotesLoading } = useQuery<{
    securityQuotes: SecurityQuote[] | null;
  }>(SECURITY_QUOTES_QUERY, {
    variables: { clientId, securityId: security?.id ?? null },
    skip: !open || !clientId || !security?.id,
  });

  useEffect(() => {
    if (!open) return;
    const initial = security ?? {
      id: "",
      name: "",
      currencyCode: baseCurrency || "",
      targetCurrencyCode: "",
      note: "",
      isin: "",
      tickerSymbol: "",
      wkn: "",
      calendar: "",
      feed: "",
      feedURL: "",
      latestFeed: "",
      latestFeedURL: "",
      onlineId: "",
      retired: false,
      attributes: [],
    };

    form.setFieldsValue({
      name: initial.name ?? "",
      currencyCode: initial.currencyCode ?? baseCurrency ?? "",
      targetCurrencyCode: initial.targetCurrencyCode ?? "",
      note: initial.note ?? "",
      isin: initial.isin ?? "",
      tickerSymbol: initial.tickerSymbol ?? "",
      wkn: initial.wkn ?? "",
      calendar: initial.calendar ?? "",
      feed: initial.feed ?? "",
      feedURL: initial.feedURL ?? "",
      latestFeed: initial.latestFeed ?? "",
      latestFeedURL: initial.latestFeedURL ?? "",
      onlineId: initial.onlineId ?? "",
      retired: initial.retired ?? false,
    });
    setAttributes(initial.attributes ?? []);
    setNewAttributeId("");
    setNewAttributeValue("");
  }, [open, security, baseCurrency, form]);

  const handleSave = async () => {
    if (!clientId) return;
    const values = await form.validateFields();
    const payload = {
      clientId,
      name: values.name,
      currencyCode: values.currencyCode,
      targetCurrencyCode: values.targetCurrencyCode,
      note: values.note,
      isin: values.isin,
      tickerSymbol: values.tickerSymbol,
      wkn: values.wkn,
      calendar: values.calendar,
      feed: values.feed,
      feedURL: values.feedURL,
      latestFeed: values.latestFeed,
      latestFeedURL: values.latestFeedURL,
      onlineId: values.onlineId,
      retired: values.retired,
      attributes: attributes.map((attribute) => ({
        id: attribute.attrid,
        value: attribute.value ?? "",
      })),
    };

    if (mode === "create") {
      const { data } = await createSecurity({ variables: payload });
      const created = data?.createSecurity as SecurityInfo | null;
      if (created) onCreated(created);
      return;
    }

    const { data } = await updateSecurity({
      variables: { ...payload, securityId: security?.id ?? null },
    });
    const updated = data?.updateSecurity as SecurityInfo | null;
    if (updated) onUpdated(updated);
  };

  const attributeColumns = [
    {
      title: "Attribut",
      dataIndex: "name",
      render: (_: unknown, attribute: SecurityAttribute) => (
        <Typography.Text>{attribute.name ?? attribute.attrid}</Typography.Text>
      ),
    },
    {
      title: "Valeur",
      dataIndex: "value",
      render: (_: unknown, attribute: SecurityAttribute) => (
        <Input
          value={attribute.value ?? ""}
          onChange={(event) =>
            setAttributes((current) =>
              current.map((item) =>
                item.attrid === attribute.attrid
                  ? { ...item, value: event.target.value }
                  : item
              )
            )
          }
        />
      ),
    },
    {
      title: "",
      width: 80,
      render: (_: unknown, attribute: SecurityAttribute) => (
        <Button
          danger
          onClick={() =>
            setAttributes((current) =>
              current.filter((item) => item.attrid !== attribute.attrid)
            )
          }
        >
          Supprimer
        </Button>
      ),
    },
  ];

  const tabs: TabsProps["items"] = [
    {
      key: "master",
      label: "Master Data",
      children: (
        <Space direction="vertical" size="middle" style={{ width: "100%" }}>
          <Form.Item name="name" label="Nom" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="currencyCode" label="Devise">
            <Input />
          </Form.Item>
          <Form.Item name="targetCurrencyCode" label="Devise cible">
            <Input />
          </Form.Item>
          <Form.Item name="isin" label="ISIN">
            <Input />
          </Form.Item>
          <Form.Item name="tickerSymbol" label="Ticker">
            <Input />
          </Form.Item>
          <Form.Item name="wkn" label="WKN">
            <Input />
          </Form.Item>
          <Form.Item name="calendar" label="Calendrier">
            <Input />
          </Form.Item>
          <Form.Item name="retired" valuePropName="checked">
            <Checkbox>Instrument inactif</Checkbox>
          </Form.Item>
          <Form.Item name="note" label="Note">
            <Input.TextArea rows={4} />
          </Form.Item>
        </Space>
      ),
    },
    {
      key: "attributes",
      label: "Attributs",
      children: (
        <Space direction="vertical" size="middle" style={{ width: "100%" }}>
          <Table
            rowKey="attrid"
            dataSource={attributes}
            columns={attributeColumns}
            pagination={false}
            locale={{ emptyText: "Aucun attribut." }}
          />
          <Card size="small" title="Ajouter un attribut">
            <Space direction="vertical" style={{ width: "100%" }}>
              <Input
                placeholder="Identifiant"
                value={newAttributeId}
                onChange={(event) => setNewAttributeId(event.target.value)}
              />
              <Input
                placeholder="Valeur"
                value={newAttributeValue}
                onChange={(event) => setNewAttributeValue(event.target.value)}
              />
              <Button
                disabled={!newAttributeId.trim()}
                onClick={() => {
                  const id = newAttributeId.trim();
                  if (!id) return;
                  setAttributes((current) => {
                    if (current.some((item) => item.attrid === id)) return current;
                    return [
                      ...current,
                      { attrid: id, name: id, value: newAttributeValue },
                    ];
                  });
                  setNewAttributeId("");
                  setNewAttributeValue("");
                }}
              >
                Ajouter
              </Button>
            </Space>
          </Card>
        </Space>
      ),
    },
    {
      key: "taxonomy",
      label: "Taxonomie",
      children: (
        <Alert
          type="info"
          message="Les données de taxonomie ne sont pas encore exposées par l'API GraphQL."
        />
      ),
    },
    {
      key: "quotes",
      label: "Quotes historiques",
      children: (
        <Table
          rowKey={(record) => `${record.date}-${record.value}`}
          dataSource={quotesData?.securityQuotes ?? []}
          loading={quotesLoading}
          pagination={{ pageSize: 10 }}
          columns={[
            { title: "Date", dataIndex: "date" },
            {
              title: "Valeur",
              dataIndex: "value",
              align: "right" as const,
              render: (value: number | null, record: SecurityQuote) =>
                value === null
                  ? ""
                  : `${value.toLocaleString()} ${record.currencyCode ?? ""}`,
            },
          ]}
        />
      ),
    },
    {
      key: "latest",
      label: "Latest Quote",
      children: (
        <Space direction="vertical" size="middle" style={{ width: "100%" }}>
          <Form.Item name="feed" label="Feed historique">
            <Input />
          </Form.Item>
          <Form.Item name="feedURL" label="Feed URL historique">
            <Input />
          </Form.Item>
          <Form.Item name="latestFeed" label="Feed dernier cours">
            <Input />
          </Form.Item>
          <Form.Item name="latestFeedURL" label="Feed URL dernier cours">
            <Input />
          </Form.Item>
          <Form.Item name="onlineId" label="Online ID">
            <Input />
          </Form.Item>
        </Space>
      ),
    },
  ];

  return (
    <Modal
      open={open}
      title={
        mode === "create"
          ? "Créer un titre"
          : `Éditer ${security?.name ?? "le titre"}`
      }
      onCancel={onClose}
      onOk={() => void handleSave()}
      width={900}
      confirmLoading={saving}
      destroyOnClose
    >
      <Form layout="vertical" form={form}>
        <Tabs items={tabs} />
      </Form>
    </Modal>
  );
};

export default SecuritiesPage;
