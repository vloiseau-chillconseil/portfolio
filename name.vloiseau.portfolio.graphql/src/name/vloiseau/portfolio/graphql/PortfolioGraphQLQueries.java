package name.vloiseau.portfolio.graphql;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import io.leangen.graphql.annotations.GraphQLArgument;
import io.leangen.graphql.annotations.GraphQLMutation;
import io.leangen.graphql.annotations.GraphQLQuery;
import io.leangen.graphql.annotations.GraphQLSubscription;
import name.abuchen.portfolio.model.Client;
import name.abuchen.portfolio.model.Portfolio;
import name.abuchen.portfolio.model.Security;
import name.abuchen.portfolio.money.CurrencyConverterImpl;
import name.abuchen.portfolio.money.Money;
import name.abuchen.portfolio.money.Values;
import name.abuchen.portfolio.snapshot.ClientPerformanceSnapshot;
import name.abuchen.portfolio.snapshot.ClientSnapshot;
import name.abuchen.portfolio.snapshot.PerformanceIndex;
import name.abuchen.portfolio.snapshot.ReportingPeriod;
import name.abuchen.portfolio.snapshot.filter.ClientSecurityFilter;
import name.abuchen.portfolio.snapshot.filter.PortfolioClientFilter;
import name.abuchen.portfolio.snapshot.filter.ReadOnlyAccount;
import name.abuchen.portfolio.snapshot.filter.ReadOnlyPortfolio;
import name.abuchen.portfolio.snapshot.security.SecurityPerformanceRecord;
import name.abuchen.portfolio.snapshot.security.SecurityPerformanceSnapshot;
import name.abuchen.portfolio.ui.editor.ClientInput;
import name.abuchen.portfolio.ui.editor.ClientInputFactory;
import name.abuchen.portfolio.ui.jobs.priceupdate.PriceUpdateProgress;
import name.abuchen.portfolio.ui.jobs.priceupdate.PriceUpdateSnapshot;
import name.abuchen.portfolio.ui.jobs.priceupdate.UpdatePricesJob;
import name.abuchen.portfolio.ui.util.ClientFilterMenu;
import name.abuchen.portfolio.util.Interval;
import name.abuchen.portfolio.util.TextUtil;
import reactor.core.publisher.Flux;

public class PortfolioGraphQLQueries
{
    private final ClientInputFactory clientInputFactory;

    public PortfolioGraphQLQueries(ClientInputFactory clientInputFactory)
    {
        this.clientInputFactory = clientInputFactory;
    }

    @GraphQLQuery(name = "clients")
    public List<ClientInfo> clients()
    {
        return clientInputFactory.listOpenClients().stream().map(this::toClientInfo).toList();
    }

    @GraphQLQuery(name = "clientFilters")
    public List<ClientFilterInfo> clientFilters(@GraphQLArgument(name = "clientId") String clientId)
    {
        return findClientInput(clientId) //
                        .map(this::listClientFilters) //
                        .orElseGet(List::of);
    }

    @GraphQLQuery(name = "reportingPeriods")
    public List<ReportingPeriodInfo> reportingPeriods(@GraphQLArgument(name = "clientId") String clientId,
                    @GraphQLArgument(name = "referenceDate") String referenceDate)
    {
        Optional<ClientInput> input = findClientInput(clientId);
        if (input.isEmpty())
            return List.of();

        LocalDate ref = referenceDate != null ? LocalDate.parse(referenceDate) : LocalDate.now();

        return input.get().getReportingPeriods().stream()
                        .map(period -> new ReportingPeriodInfo(period, ref))
                        .toList();
    }

    @GraphQLQuery(name = "clientFilterDelta")
    public MoneyInfo clientFilterDelta(@GraphQLArgument(name = "clientId") String clientId,
                    @GraphQLArgument(name = "filterId") String filterId,
                    @GraphQLArgument(name = "startDate") String startDate,
                    @GraphQLArgument(name = "endDate") String endDate)
    {
        Optional<ClientInput> input = findClientInput(clientId);
        if (input.isEmpty())
            return null;

        LocalDate start = LocalDate.parse(startDate);
        LocalDate end = LocalDate.parse(endDate);

        ClientFilterMenu menu = new ClientFilterMenu(input.get().getClient(), input.get().getPreferenceStore());
        Optional<ClientFilterMenu.Item> item = menu.getAllItems().filter(i -> i.getId().equals(filterId)).findFirst();
        if (item.isEmpty())
            return null;

        Client filtered = item.get().getFilter().filter(input.get().getClient());
        var converter = new CurrencyConverterImpl(input.get().getExchangeRateProviderFacory(),
                        input.get().getClient().getBaseCurrency());

        Money delta = new ClientPerformanceSnapshot(filtered, converter, start, end).getAbsoluteDelta();
        return new MoneyInfo(delta);
    }

    @GraphQLQuery(name = "clientFilterAccumulatedDelta")
    public List<DeltaPoint> clientFilterAccumulatedDelta(@GraphQLArgument(name = "clientId") String clientId,
                    @GraphQLArgument(name = "filterId") String filterId,
                    @GraphQLArgument(name = "startDate") String startDate,
                    @GraphQLArgument(name = "endDate") String endDate,
                    @GraphQLArgument(name = "portfolioId") String portfolioId,
                    @GraphQLArgument(name = "securityId") String securityId)
    {
        Optional<ClientInput> input = findClientInput(clientId);
        if (input.isEmpty())
            return List.of();

        LocalDate start = LocalDate.parse(startDate);
        LocalDate end = LocalDate.parse(endDate);

        Client filtered = input.get().getClient();
        if (filterId != null)
        {
            ClientFilterMenu menu = new ClientFilterMenu(input.get().getClient(), input.get().getPreferenceStore());
            Optional<ClientFilterMenu.Item> item = menu.getAllItems().filter(i -> i.getId().equals(filterId))
                            .findFirst();
            if (item.isEmpty())
                return List.of();

            filtered = item.get().getFilter().filter(input.get().getClient());
        }
        var converter = new CurrencyConverterImpl(input.get().getExchangeRateProviderFacory(),
                        input.get().getClient().getBaseCurrency());

        PerformanceIndex index = createPerformanceIndex(filtered, converter, Interval.of(start, end), portfolioId,
                        securityId);

        LocalDate[] dates = index.getDates();
		long[] deltas = index.calculateDelta();

        List<DeltaPoint> points = new ArrayList<>(dates.length);
        for (int i = 0; i < dates.length; i++)
        {
			long value = deltas[i];
            points.add(new DeltaPoint(dates[i].toString(), new MoneyInfo(Money.of(index.getCurrency(), value))));
        }

        return points;
    }

    @GraphQLQuery(name = "portfolioSecurityPerformance")
    public List<PortfolioSecurityPerformanceInfo> portfolioSecurityPerformance(
                    @GraphQLArgument(name = "clientId") String clientId,
                    @GraphQLArgument(name = "filterId") String filterId,
                    @GraphQLArgument(name = "startDate") String startDate,
                    @GraphQLArgument(name = "endDate") String endDate)
    {
        Optional<ClientInput> input = findClientInput(clientId);
        if (input.isEmpty())
            return List.of();

        LocalDate start = LocalDate.parse(startDate);
        LocalDate end = LocalDate.parse(endDate);

        Client client = input.get().getClient();
        if (filterId != null)
        {
            ClientFilterMenu menu = new ClientFilterMenu(client, input.get().getPreferenceStore());
            Optional<ClientFilterMenu.Item> item = menu.getAllItems().filter(i -> i.getId().equals(filterId))
                            .findFirst();
            if (item.isEmpty())
                return List.of();
            client = item.get().getFilter().filter(client);
        }

        var converter = new CurrencyConverterImpl(input.get().getExchangeRateProviderFacory(),
                        client.getBaseCurrency());

        Client filteredClient = client;
        List<PortfolioSecurityPerformanceInfo> results = new ArrayList<>();
        results.add(toTotalPortfolioPerformanceInfo(filteredClient, converter, start, end));
        results.addAll(filteredClient.getPortfolios().stream()
                        .sorted((left, right) -> TextUtil.compare(left.getName(), right.getName()))
                        .map(portfolio -> {
                            Portfolio source = unwrapPortfolio(portfolio);
                            return toPortfolioPerformanceInfo(portfolio, source, filteredClient, converter, start, end);
                        })
                        .toList());
        return results;
    }

    @GraphQLMutation(name = "updateQuotes")
    public UpdateQuotesResult updateQuotes(@GraphQLArgument(name = "clientId") String clientId,
                    @GraphQLArgument(name = "scope") UpdateQuotesScope scope)
    {
        Optional<ClientInput> input = findClientInput(clientId);
        if (input.isEmpty())
            return new UpdateQuotesResult(false, 0);

        Client client = input.get().getClient();
        UpdateQuotesScope effectiveScope = scope != null ? scope : UpdateQuotesScope.ALL;

        if (PriceUpdateProgress.getInstance().hasActiveJob(client))
            throw new IllegalStateException("Quote update already running");

        int count;
        switch (effectiveScope)
        {
            case ACTIVE:
                count = (int) client.getSecurities().stream().filter(s -> !s.isRetired()).count();
                new UpdatePricesJob(client, s -> !s.isRetired(), EnumSet.allOf(UpdatePricesJob.Target.class))
                                .schedule();
                break;
            case HOLDINGS:
                var converter = new CurrencyConverterImpl(input.get().getExchangeRateProviderFacory(),
                                client.getBaseCurrency());
                var snapshot = ClientSnapshot.create(client, converter, LocalDate.now());
                List<Security> securities = snapshot.getJointPortfolio().getPositions().stream() //
                                .map(position -> position.getSecurity()) //
                                .distinct() //
                                .toList();
                count = securities.size();
                new UpdatePricesJob(client, securities).schedule();
                break;
            case ALL:
            default:
                count = client.getSecurities().size();
                new UpdatePricesJob(client, EnumSet.allOf(UpdatePricesJob.Target.class)).schedule();
                break;
        }

        return new UpdateQuotesResult(true, count);
    }

    @GraphQLSubscription(name = "quoteUpdates")
    public Flux<QuoteUpdateProgress> quoteUpdates(@GraphQLArgument(name = "clientId") String clientId)
    {
        Optional<ClientInput> input = findClientInput(clientId);
        if (input.isEmpty())
            return Flux.error(new IllegalArgumentException("Unknown clientId"));

        var client = input.get().getClient();
        return Flux.create(sink -> {
            AtomicBoolean done = new AtomicBoolean(false);

            AtomicReference<PriceUpdateProgress.Listener> listenerRef = new AtomicReference<>();
            PriceUpdateProgress.Listener listener = snapshot -> {
                if (done.get())
                    return;
                sink.next(new QuoteUpdateProgress(snapshot));
                if (snapshot.getTaskCount() > 0 && snapshot.getCompletedTaskCount() >= snapshot.getTaskCount())
                {
                    if (done.compareAndSet(false, true))
                    {
                        PriceUpdateProgress.Listener current = listenerRef.get();
                        if (current != null)
                            PriceUpdateProgress.getInstance().unregister(client, current);
                        sink.complete();
                    }
                }
            };
            listenerRef.set(listener);

            PriceUpdateProgress.getInstance().register(client, listener);
            sink.onCancel(() -> {
                if (done.compareAndSet(false, true))
                {
                    PriceUpdateProgress.Listener current = listenerRef.get();
                    if (current != null)
                        PriceUpdateProgress.getInstance().unregister(client, current);
                }
            });
            sink.onDispose(() -> {
                if (done.compareAndSet(false, true))
                {
                    PriceUpdateProgress.Listener current = listenerRef.get();
                    if (current != null)
                        PriceUpdateProgress.getInstance().unregister(client, current);
                }
            });
        });
    }

    private ClientInfo toClientInfo(ClientInput input)
    {
        Client client = input.getClient();
        String baseCurrency = client != null ? client.getBaseCurrency() : null;
        String file = input.getFile() != null ? input.getFile().getAbsolutePath() : null;
        return new ClientInfo(clientId(input), input.getLabel(), file, baseCurrency);
    }

    private Optional<ClientInput> findClientInput(String clientId)
    {
        return clientInputFactory.listOpenClients().stream().filter(input -> clientId(input).equals(clientId))
                        .findFirst();
    }

    private Portfolio unwrapPortfolio(Portfolio portfolio)
    {
        return ReadOnlyPortfolio.unwrap(portfolio);
    }

    private name.abuchen.portfolio.model.Account unwrapAccount(name.abuchen.portfolio.model.Account account)
    {
        return ReadOnlyAccount.unwrap(account);
    }

    private PerformanceIndex createPerformanceIndex(Client client, CurrencyConverterImpl converter, Interval interval,
                    String portfolioId, String securityId)
    {
        if (portfolioId == null && securityId == null)
            return PerformanceIndex.forClient(client, converter, interval, new ArrayList<>());

        if (portfolioId != null)
        {
            Portfolio portfolio = client.getPortfolios().stream()
                            .filter(p -> portfolioId.equals(unwrapPortfolio(p).getUUID()))
                            .findFirst()
                            .orElse(null);
            if (portfolio == null)
                throw new IllegalArgumentException("Unknown portfolioId: " + portfolioId); //$NON-NLS-1$

            if (securityId == null)
                return PerformanceIndex.forPortfolio(client, converter, portfolio, interval, new ArrayList<>());

            Security security = client.getSecurities().stream().filter(s -> securityId.equals(s.getUUID())).findFirst()
                            .orElse(null);
            if (security == null)
                throw new IllegalArgumentException("Unknown securityId: " + securityId); //$NON-NLS-1$

            Client portfolioFiltered = new PortfolioClientFilter(portfolio).filter(client);
            Client securityFiltered = new ClientSecurityFilter(security).filter(portfolioFiltered);
            return PerformanceIndex.forClient(securityFiltered, converter, interval, new ArrayList<>());
        }

        Security security = client.getSecurities().stream().filter(s -> securityId.equals(s.getUUID())).findFirst()
                        .orElse(null);
        if (security == null)
            throw new IllegalArgumentException("Unknown securityId: " + securityId); //$NON-NLS-1$

        Client securityFiltered = new ClientSecurityFilter(security).filter(client);
        return PerformanceIndex.forClient(securityFiltered, converter, interval, new ArrayList<>());
    }

    private List<ClientFilterInfo> listClientFilters(ClientInput input)
    {
        ClientFilterMenu menu = new ClientFilterMenu(input.getClient(), input.getPreferenceStore());
        return menu.getAllItems() //
                        .map(item -> new ClientFilterInfo(item.getId(), item.getLabel(), item.getUUIDs())) //
                        .toList();
    }

    private String clientId(ClientInput input)
    {
        return input.getFile() != null ? input.getFile().getAbsolutePath() : input.getLabel();
    }

    public enum UpdateQuotesScope
    {
        ALL, ACTIVE, HOLDINGS
    }

    public static final class UpdateQuotesResult
    {
        private final boolean scheduled;
        private final int securityCount;

        public UpdateQuotesResult(boolean scheduled, int securityCount)
        {
            this.scheduled = scheduled;
            this.securityCount = securityCount;
        }

        @GraphQLQuery
        public boolean scheduled()
        {
            return scheduled;
        }

        @GraphQLQuery
        public int securityCount()
        {
            return securityCount;
        }
    }

    public static final class QuoteUpdateProgress
    {
        private final long timestamp;
        private final int taskCount;
        private final int completedTaskCount;

        public QuoteUpdateProgress(PriceUpdateSnapshot snapshot)
        {
            this.timestamp = snapshot.getTimestamp();
            this.taskCount = snapshot.getTaskCount();
            this.completedTaskCount = snapshot.getCompletedTaskCount();
        }

        @GraphQLQuery
        public long timestamp()
        {
            return timestamp;
        }

        @GraphQLQuery
        public int taskCount()
        {
            return taskCount;
        }

        @GraphQLQuery
        public int completedTaskCount()
        {
            return completedTaskCount;
        }
    }

    public static final class ClientInfo
    {
        private final String id;
        private final String label;
        private final String file;
        private final String baseCurrency;

        public ClientInfo(String id, String label, String file, String baseCurrency)
        {
            this.id = id;
            this.label = label;
            this.file = file;
            this.baseCurrency = baseCurrency;
        }

        @GraphQLQuery
        public String getId()
        {
            return id;
        }

        @GraphQLQuery
        public String getLabel()
        {
            return label;
        }

        @GraphQLQuery
        public String getFile()
        {
            return file;
        }

        @GraphQLQuery
        public String getBaseCurrency()
        {
            return baseCurrency;
        }
    }

    public static final class ClientFilterInfo
    {
        private final String id;
        private final String label;
        private final String uuids;

        public ClientFilterInfo(String id, String label, String uuids)
        {
            this.id = id;
            this.label = label;
            this.uuids = uuids;
        }

        @GraphQLQuery
        public String getId()
        {
            return id;
        }

        @GraphQLQuery
        public String getLabel()
        {
            return label;
        }

        @GraphQLQuery
        public String getUuids()
        {
            return uuids;
        }
    }

    public static final class MoneyInfo
    {
        private final String currencyCode;
        private final double amount;

        public MoneyInfo(Money money)
        {
            this.currencyCode = money.getCurrencyCode();
            this.amount = money.getAmount() / Values.Money.divider();
        }

        @GraphQLQuery
        public String getCurrencyCode()
        {
            return currencyCode;
        }

        @GraphQLQuery
        public double getAmount()
        {
            return amount;
        }
    }

    public static final class ReportingPeriodInfo
    {
        private final String code;
        private final String label;
        private final String startDate;
        private final String endDate;

        public ReportingPeriodInfo(ReportingPeriod period, LocalDate referenceDate)
        {
            this.code = period.getCode();
            this.label = period.toString();
            Interval interval = period.toInterval(referenceDate);
            this.startDate = interval.getStart().toString();
            this.endDate = interval.getEnd().toString();
        }

        @GraphQLQuery
        public String getCode()
        {
            return code;
        }

        @GraphQLQuery
        public String getLabel()
        {
            return label;
        }

        @GraphQLQuery
        public String getStartDate()
        {
            return startDate;
        }

        @GraphQLQuery
        public String getEndDate()
        {
            return endDate;
        }
    }

    public static final class DeltaPoint
    {
        private final String date;
        private final MoneyInfo value;

        public DeltaPoint(String date, MoneyInfo value)
        {
            this.date = date;
            this.value = value;
        }

        @GraphQLQuery
        public String getDate()
        {
            return date;
        }

        @GraphQLQuery
        public MoneyInfo getValue()
        {
            return value;
        }
    }

    public static final class PortfolioSecurityPerformanceInfo
    {
        private final String portfolioId;
        private final String portfolioName;
        private final String referenceAccountId;
        private final String referenceAccountName;
        private final MoneyInfo startValue;
        private final MoneyInfo delta;
        private final double deltaPercent;
        private final List<SecurityPerformanceInfo> securities;

        public PortfolioSecurityPerformanceInfo(String portfolioId, String portfolioName, String referenceAccountId,
                        String referenceAccountName, MoneyInfo startValue, MoneyInfo delta, double deltaPercent,
                        List<SecurityPerformanceInfo> securities)
        {
            this.portfolioId = portfolioId;
            this.portfolioName = portfolioName;
            this.referenceAccountId = referenceAccountId;
            this.referenceAccountName = referenceAccountName;
            this.startValue = startValue;
            this.delta = delta;
            this.deltaPercent = deltaPercent;
            this.securities = securities;
        }

        @GraphQLQuery
        public String getPortfolioId()
        {
            return portfolioId;
        }

        @GraphQLQuery
        public String getPortfolioName()
        {
            return portfolioName;
        }

        @GraphQLQuery
        public String getReferenceAccountId()
        {
            return referenceAccountId;
        }

        @GraphQLQuery
        public String getReferenceAccountName()
        {
            return referenceAccountName;
        }

        @GraphQLQuery
        public MoneyInfo getStartValue()
        {
            return startValue;
        }

        @GraphQLQuery
        public MoneyInfo getDelta()
        {
            return delta;
        }

        @GraphQLQuery
        public double getDeltaPercent()
        {
            return deltaPercent;
        }

        @GraphQLQuery
        public List<SecurityPerformanceInfo> getSecurities()
        {
            return securities;
        }
    }

    public static final class SecurityPerformanceInfo
    {
        private final String securityId;
        private final String securityName;
        private final MoneyInfo startValue;
        private final MoneyInfo delta;
        private final double deltaPercent;

        public SecurityPerformanceInfo(String securityId, String securityName, MoneyInfo startValue, MoneyInfo delta,
                        double deltaPercent)
        {
            this.securityId = securityId;
            this.securityName = securityName;
            this.startValue = startValue;
            this.delta = delta;
            this.deltaPercent = deltaPercent;
        }

        @GraphQLQuery
        public String getSecurityId()
        {
            return securityId;
        }

        @GraphQLQuery
        public String getSecurityName()
        {
            return securityName;
        }

        @GraphQLQuery
        public MoneyInfo getStartValue()
        {
            return startValue;
        }

        @GraphQLQuery
        public MoneyInfo getDelta()
        {
            return delta;
        }

        @GraphQLQuery
        public double getDeltaPercent()
        {
            return deltaPercent;
        }
    }

    private PortfolioSecurityPerformanceInfo toPortfolioPerformanceInfo(Portfolio portfolio, Portfolio source,
                    Client client, CurrencyConverterImpl converter, LocalDate startDate, LocalDate endDate)
    {
        Client filtered = new PortfolioClientFilter(portfolio).filter(client);
        ClientSnapshot startSnapshot = ClientSnapshot.create(filtered, converter, startDate);
        ClientSnapshot endSnapshot = ClientSnapshot.create(filtered, converter, endDate);
        var interval = Interval.of(startDate, endDate);
        SecurityPerformanceSnapshot performance = SecurityPerformanceSnapshot.create(filtered, converter, interval,
                        startSnapshot, endSnapshot);

        var termCurrency = converter.getTermCurrency();
        var portfolioBase = name.abuchen.portfolio.money.MutableMoney.of(termCurrency);
        var portfolioDelta = name.abuchen.portfolio.money.MutableMoney.of(termCurrency);

        List<SecurityPerformanceInfo> securities = performance.getRecords().stream()
                        .sorted((left, right) -> TextUtil.compare(left.getSecurityName(), right.getSecurityName()))
                        .map(record -> toSecurityPerformanceInfo(record, filtered, startDate, endDate, converter,
                                        portfolioBase, portfolioDelta))
                        .toList();

        PerformanceIndex portfolioIndex = PerformanceIndex
                        .forPortfolio(client, converter, source, Interval.of(startDate, endDate), new ArrayList<>());
        double portfolioPercent = portfolioIndex.getFinalAccumulatedPercentage() * 100d;
        long portfolioStartValue = firstNonZero(portfolioIndex.getTotals());

        return new PortfolioSecurityPerformanceInfo(source.getUUID(), source.getName(),
                        source.getReferenceAccount() != null ? unwrapAccount(source.getReferenceAccount()).getUUID()
                                        : null,
                        source.getReferenceAccount() != null ? unwrapAccount(source.getReferenceAccount()).getName()
                                        : null,
                        new MoneyInfo(Money.of(termCurrency, portfolioStartValue)),
                        new MoneyInfo(portfolioDelta.toMoney()),
                        portfolioPercent, securities);
    }

    private SecurityPerformanceInfo toSecurityPerformanceInfo(SecurityPerformanceRecord record, Client scopeClient,
                    LocalDate startDate, LocalDate endDate, CurrencyConverterImpl converter,
                    name.abuchen.portfolio.money.MutableMoney portfolioBase,
                    name.abuchen.portfolio.money.MutableMoney portfolioDelta)
    {
        Security security = record.getSecurity();
        PerformanceIndex securityIndex = PerformanceIndex.forInvestment(scopeClient, converter, security,
                        Interval.of(startDate, endDate), new ArrayList<>());
        long startValue = firstNonZero(securityIndex.getTotals());
        Money baseValue = Money.of(converter.getTermCurrency(), startValue);

        portfolioBase.add(baseValue);
        portfolioDelta.add(record.getDelta());

        double percent = securityIndex.getFinalAccumulatedPercentage() * 100d;

        return new SecurityPerformanceInfo(security.getUUID(), security.getName(), new MoneyInfo(baseValue),
                        new MoneyInfo(record.getDelta()), percent);
    }

    private PortfolioSecurityPerformanceInfo toTotalPortfolioPerformanceInfo(Client client,
                    CurrencyConverterImpl converter, LocalDate startDate, LocalDate endDate)
    {
        ClientSnapshot startSnapshot = ClientSnapshot.create(client, converter, startDate);
        ClientSnapshot endSnapshot = ClientSnapshot.create(client, converter, endDate);
        var interval = Interval.of(startDate, endDate);
        SecurityPerformanceSnapshot performance = SecurityPerformanceSnapshot.create(client, converter, interval,
                        startSnapshot, endSnapshot);

        var termCurrency = converter.getTermCurrency();
        var totalBase = name.abuchen.portfolio.money.MutableMoney.of(termCurrency);
        var totalDelta = name.abuchen.portfolio.money.MutableMoney.of(termCurrency);

        performance.getRecords().forEach(record -> toSecurityPerformanceInfo(record, client, startDate, endDate,
                        converter, totalBase, totalDelta));

        PerformanceIndex totalIndex = PerformanceIndex.forClient(client, converter, Interval.of(startDate, endDate),
                        new ArrayList<>());
        double totalPercent = totalIndex.getFinalAccumulatedPercentage() * 100d;
        long totalStartValue = firstNonZero(totalIndex.getTotals());

        return new PortfolioSecurityPerformanceInfo("TOTAL", "Total", null, null, new MoneyInfo(Money.of(termCurrency,
                        totalStartValue)), new MoneyInfo(totalDelta.toMoney()), totalPercent, List.of());
    }

    private long firstNonZero(long[] totals)
    {
        for (long value : totals)
            if (value != 0)
                return value;
        return totals.length > 0 ? totals[0] : 0;
    }
}
