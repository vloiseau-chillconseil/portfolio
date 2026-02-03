package name.vloiseau.portfolio.graphql;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.leangen.graphql.annotations.GraphQLArgument;
import io.leangen.graphql.annotations.GraphQLMutation;
import io.leangen.graphql.annotations.GraphQLQuery;
import io.leangen.graphql.annotations.GraphQLSubscription;
import name.abuchen.portfolio.model.Account;
import name.abuchen.portfolio.model.Classification;
import name.abuchen.portfolio.model.Client;
import name.abuchen.portfolio.model.InvestmentVehicle;
import name.abuchen.portfolio.model.Portfolio;
import name.abuchen.portfolio.model.Security;
import name.abuchen.portfolio.model.Taxonomy;
import name.abuchen.portfolio.money.CurrencyConverterImpl;
import name.abuchen.portfolio.money.Money;
import name.abuchen.portfolio.money.MutableMoney;
import name.abuchen.portfolio.money.Values;
import name.abuchen.portfolio.snapshot.AccountSnapshot;
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
import name.abuchen.portfolio.ui.Messages;
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
                    @GraphQLArgument(name = "securityId") String securityId,
                    @GraphQLArgument(name = "referenceAccountId") String referenceAccountId)
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
                        securityId, referenceAccountId);

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
    public PortfolioSecurityPerformanceResult portfolioSecurityPerformance(
                    @GraphQLArgument(name = "clientId") String clientId,
                    @GraphQLArgument(name = "filterId") String filterId,
                    @GraphQLArgument(name = "startDate") String startDate,
                    @GraphQLArgument(name = "endDate") String endDate)
    {
        Optional<ClientInput> input = findClientInput(clientId);
        if (input.isEmpty())
            return new PortfolioSecurityPerformanceResult(List.of(), List.of(), List.of(), List.of(), null);

        LocalDate start = LocalDate.parse(startDate);
        LocalDate end = LocalDate.parse(endDate);

        Client client = input.get().getClient();
        if (filterId != null)
        {
            ClientFilterMenu menu = new ClientFilterMenu(client, input.get().getPreferenceStore());
            Optional<ClientFilterMenu.Item> item = menu.getAllItems().filter(i -> i.getId().equals(filterId))
                            .findFirst();
            if (item.isEmpty())
                return new PortfolioSecurityPerformanceResult(List.of(), List.of(), List.of(), List.of(), null);
            client = item.get().getFilter().filter(client);
        }

        var converter = new CurrencyConverterImpl(input.get().getExchangeRateProviderFacory(),
                        client.getBaseCurrency());

        Client filteredClient = client;
        List<Taxonomy> clientTaxonomies = input.get().getClient().getTaxonomies().stream()
                        .filter(Objects::nonNull)
                        .toList();
        Map<String, TaxonomyAggregation> taxonomyAggregations = clientTaxonomies.stream()
                        .collect(Collectors.toMap(Taxonomy::getId,
                                        taxonomy -> new TaxonomyAggregation(taxonomy, converter.getTermCurrency())));

        List<PerformancePortfolioInfo> portfolios = new ArrayList<>();
        List<PerformanceSecurityInfo> securities = new ArrayList<>();
        filteredClient.getPortfolios().stream()
                        .sorted((left, right) -> TextUtil.compare(left.getName(), right.getName()))
                        .forEach(portfolio -> {
                            Portfolio source = unwrapPortfolio(portfolio);
                            collectPortfolioPerformance(portfolios, securities, portfolio, source, filteredClient,
                                            converter, start, end, taxonomyAggregations);
                        });

        List<PerformanceReferenceAccountInfo> referenceAccounts = toReferenceAccountEntries(filteredClient, converter,
                        start, end, taxonomyAggregations);

        PerformanceTotalInfo total = toTotalPerformanceInfo(filteredClient, converter, start, end);

        List<PerformanceTaxonomyInfo> taxonomies = clientTaxonomies.stream()
                        .map(taxonomy -> toPerformanceTaxonomyInfo(taxonomy,
                                        taxonomyAggregations.get(taxonomy.getId())))
                        .filter(Objects::nonNull)
                        .toList();

        return new PortfolioSecurityPerformanceResult(portfolios, securities, referenceAccounts, taxonomies, total);
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
                    String portfolioId, String securityId, String referenceAccountId)
    {
        if (referenceAccountId != null && (portfolioId != null || securityId != null))
            throw new IllegalArgumentException("Specify only one of referenceAccountId, portfolioId, or securityId"); //$NON-NLS-1$

        if (referenceAccountId != null)
        {
            Account account = client.getAccounts().stream()
                            .filter(a -> referenceAccountId.equals(unwrapAccount(a).getUUID()))
                            .map(this::unwrapAccount)
                            .findFirst()
                            .orElse(null);
            if (account == null)
                throw new IllegalArgumentException("Unknown referenceAccountId: " + referenceAccountId); //$NON-NLS-1$
            return PerformanceIndex.forAccount(client, converter, account, interval, new ArrayList<>());
        }

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

    public static final class PortfolioSecurityPerformanceResult
    {
        private final List<PerformancePortfolioInfo> portfolios;
        private final List<PerformanceSecurityInfo> securities;
        private final List<PerformanceReferenceAccountInfo> referenceAccounts;
        private final List<PerformanceTaxonomyInfo> taxonomies;
        private final PerformanceTotalInfo total;

        public PortfolioSecurityPerformanceResult(List<PerformancePortfolioInfo> portfolios,
                        List<PerformanceSecurityInfo> securities, List<PerformanceReferenceAccountInfo> referenceAccounts,
                        List<PerformanceTaxonomyInfo> taxonomies, PerformanceTotalInfo total)
        {
            this.portfolios = portfolios;
            this.securities = securities;
            this.referenceAccounts = referenceAccounts;
            this.taxonomies = taxonomies;
            this.total = total;
        }

        @GraphQLQuery
        public List<PerformancePortfolioInfo> getPortfolios()
        {
            return portfolios;
        }

        @GraphQLQuery
        public List<PerformanceSecurityInfo> getSecurities()
        {
            return securities;
        }

        @GraphQLQuery
        public List<PerformanceReferenceAccountInfo> getReferenceAccounts()
        {
            return referenceAccounts;
        }

        @GraphQLQuery
        public List<PerformanceTaxonomyInfo> getTaxonomies()
        {
            return taxonomies;
        }

        @GraphQLQuery
        public PerformanceTotalInfo getTotal()
        {
            return total;
        }
    }

    public static final class PerformancePortfolioInfo
    {
        private final String portfolioId;
        private final String portfolioName;
        private final String referenceAccountId;
        private final String referenceAccountName;
        private final MoneyInfo startValue;
        private final MoneyInfo delta;
        private final double deltaPercent;

        public PerformancePortfolioInfo(String portfolioId, String portfolioName, String referenceAccountId,
                        String referenceAccountName, MoneyInfo startValue, MoneyInfo delta, double deltaPercent)
        {
            this.portfolioId = portfolioId;
            this.portfolioName = portfolioName;
            this.referenceAccountId = referenceAccountId;
            this.referenceAccountName = referenceAccountName;
            this.startValue = startValue;
            this.delta = delta;
            this.deltaPercent = deltaPercent;
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
    }

    public static final class PerformanceSecurityInfo
    {
        private final String id;
        private final String portfolioId;
        private final String securityId;
        private final String name;
        private final MoneyInfo startValue;
        private final MoneyInfo delta;
        private final double deltaPercent;
        private final List<PerformanceTaxonomyAssignmentInfo> taxonomyAssignments;

        public PerformanceSecurityInfo(String id, String portfolioId, String securityId, String name,
                        MoneyInfo startValue, MoneyInfo delta, double deltaPercent,
                        List<PerformanceTaxonomyAssignmentInfo> taxonomyAssignments)
        {
            this.id = id;
            this.portfolioId = portfolioId;
            this.securityId = securityId;
            this.name = name;
            this.startValue = startValue;
            this.delta = delta;
            this.deltaPercent = deltaPercent;
            this.taxonomyAssignments = taxonomyAssignments;
        }

        @GraphQLQuery
        public String getId()
        {
            return id;
        }

        @GraphQLQuery
        public String getPortfolioId()
        {
            return portfolioId;
        }

        @GraphQLQuery
        public String getSecurityId()
        {
            return securityId;
        }

        @GraphQLQuery
        public String getName()
        {
            return name;
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
        public List<PerformanceTaxonomyAssignmentInfo> getTaxonomyAssignments()
        {
            return taxonomyAssignments;
        }
    }

    public static final class PerformanceReferenceAccountInfo
    {
        private final String accountId;
        private final String accountName;
        private final MoneyInfo startValue;
        private final MoneyInfo delta;
        private final double deltaPercent;
        private final List<PerformanceTaxonomyAssignmentInfo> taxonomyAssignments;

        public PerformanceReferenceAccountInfo(String accountId, String accountName, MoneyInfo startValue,
                        MoneyInfo delta, double deltaPercent,
                        List<PerformanceTaxonomyAssignmentInfo> taxonomyAssignments)
        {
            this.accountId = accountId;
            this.accountName = accountName;
            this.startValue = startValue;
            this.delta = delta;
            this.deltaPercent = deltaPercent;
            this.taxonomyAssignments = taxonomyAssignments;
        }

        @GraphQLQuery
        public String getAccountId()
        {
            return accountId;
        }

        @GraphQLQuery
        public String getAccountName()
        {
            return accountName;
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
        public List<PerformanceTaxonomyAssignmentInfo> getTaxonomyAssignments()
        {
            return taxonomyAssignments;
        }
    }

    public static final class PerformanceTotalInfo
    {
        private final MoneyInfo startValue;
        private final MoneyInfo delta;
        private final double deltaPercent;

        public PerformanceTotalInfo(MoneyInfo startValue, MoneyInfo delta, double deltaPercent)
        {
            this.startValue = startValue;
            this.delta = delta;
            this.deltaPercent = deltaPercent;
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

    public static final class PerformanceTaxonomyInfo
    {
        private final String taxonomyId;
        private final String taxonomyName;
        private final List<PerformanceTaxonomyClassificationInfo> classifications;

        public PerformanceTaxonomyInfo(String taxonomyId, String taxonomyName,
                        List<PerformanceTaxonomyClassificationInfo> classifications)
        {
            this.taxonomyId = taxonomyId;
            this.taxonomyName = taxonomyName;
            this.classifications = classifications;
        }

        @GraphQLQuery
        public String getTaxonomyId()
        {
            return taxonomyId;
        }

        @GraphQLQuery
        public String getTaxonomyName()
        {
            return taxonomyName;
        }

        @GraphQLQuery
        public List<PerformanceTaxonomyClassificationInfo> getClassifications()
        {
            return classifications;
        }
    }

    public static final class PerformanceTaxonomyClassificationInfo
    {
        private final String classificationId;
        private final String parentId;
        private final String name;
        private final MoneyInfo startValue;
        private final MoneyInfo delta;
        private final double deltaPercent;

        public PerformanceTaxonomyClassificationInfo(String classificationId, String parentId, String name,
                        MoneyInfo startValue, MoneyInfo delta, double deltaPercent)
        {
            this.classificationId = classificationId;
            this.parentId = parentId;
            this.name = name;
            this.startValue = startValue;
            this.delta = delta;
            this.deltaPercent = deltaPercent;
        }

        @GraphQLQuery
        public String getClassificationId()
        {
            return classificationId;
        }

        @GraphQLQuery
        public String getParentId()
        {
            return parentId;
        }

        @GraphQLQuery
        public String getName()
        {
            return name;
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

    public static final class PerformanceTaxonomyAssignmentInfo
    {
        private final String taxonomyId;
        private final String taxonomyName;
        private final String classificationId;
        private final String classificationName;

        public PerformanceTaxonomyAssignmentInfo(String taxonomyId, String taxonomyName, String classificationId,
                        String classificationName)
        {
            this.taxonomyId = taxonomyId;
            this.taxonomyName = taxonomyName;
            this.classificationId = classificationId;
            this.classificationName = classificationName;
        }

        @GraphQLQuery
        public String getTaxonomyId()
        {
            return taxonomyId;
        }

        @GraphQLQuery
        public String getTaxonomyName()
        {
            return taxonomyName;
        }

        @GraphQLQuery
        public String getClassificationId()
        {
            return classificationId;
        }

        @GraphQLQuery
        public String getClassificationName()
        {
            return classificationName;
        }
    }

    private void collectPortfolioPerformance(List<PerformancePortfolioInfo> portfolios,
                    List<PerformanceSecurityInfo> securities, Portfolio portfolio, Portfolio source, Client client,
                    CurrencyConverterImpl converter, LocalDate startDate, LocalDate endDate,
                    Map<String, TaxonomyAggregation> taxonomyAggregations)
    {
        Client filtered = new PortfolioClientFilter(portfolio).filter(client);
        ClientSnapshot startSnapshot = ClientSnapshot.create(filtered, converter, startDate);
        ClientSnapshot endSnapshot = ClientSnapshot.create(filtered, converter, endDate);
        var interval = Interval.of(startDate, endDate);
        SecurityPerformanceSnapshot performance = SecurityPerformanceSnapshot.create(filtered, converter, interval,
                        startSnapshot, endSnapshot);

        var termCurrency = converter.getTermCurrency();
        var portfolioDelta = MutableMoney.of(termCurrency);

        List<PerformanceSecurityInfo> portfolioSecurities = performance.getRecords().stream()
                        .sorted((left, right) -> TextUtil.compare(left.getSecurityName(), right.getSecurityName()))
                        .map(record -> toSecurityPerformanceInfo(record, filtered, startDate, endDate, converter,
                                        portfolioDelta, source.getUUID(), taxonomyAggregations))
                        .toList();

        PerformanceIndex portfolioIndex = PerformanceIndex
                        .forPortfolioPlusAccount(client, converter, source, interval, new ArrayList<>());
        double portfolioPercent = portfolioIndex.getFinalAccumulatedPercentage() * 100d;
        long portfolioStartValue = firstNonZero(portfolioIndex.getTotals());
        String referenceAccountId = source.getReferenceAccount() != null
                        ? unwrapAccount(source.getReferenceAccount()).getUUID()
                        : null;
        String referenceAccountName = source.getReferenceAccount() != null
                        ? unwrapAccount(source.getReferenceAccount()).getName()
                        : null;

        Money accountStart = startSnapshot.getAccounts().stream()
                        .map(AccountSnapshot::getFunds)
                        .reduce(Money.of(termCurrency, 0), Money::add);
        Money accountEnd = endSnapshot.getAccounts().stream()
                        .map(AccountSnapshot::getFunds)
                        .reduce(Money.of(termCurrency, 0), Money::add);
        Money accountDelta = accountEnd.subtract(accountStart);
        portfolioDelta.add(accountDelta);

        portfolios.add(new PerformancePortfolioInfo(source.getUUID(), source.getName(), referenceAccountId,
                        referenceAccountName, new MoneyInfo(Money.of(termCurrency, portfolioStartValue)),
                        new MoneyInfo(portfolioDelta.toMoney()), portfolioPercent));
        securities.addAll(portfolioSecurities);
    }

    private PerformanceSecurityInfo toSecurityPerformanceInfo(SecurityPerformanceRecord record, Client scopeClient,
                    LocalDate startDate, LocalDate endDate, CurrencyConverterImpl converter,
                    MutableMoney portfolioDelta, String portfolioId,
                    Map<String, TaxonomyAggregation> taxonomyAggregations)
    {
        Security security = record.getSecurity();
        PerformanceIndex securityIndex = PerformanceIndex.forInvestment(scopeClient, converter, security,
                        Interval.of(startDate, endDate), new ArrayList<>());
        long startValue = firstNonZero(securityIndex.getTotals());
        Money baseValue = Money.of(converter.getTermCurrency(), startValue);

        portfolioDelta.add(record.getDelta());

        double percent = securityIndex.getFinalAccumulatedPercentage() * 100d;

        List<PerformanceTaxonomyAssignmentInfo> assignments = toTaxonomyAssignments(security, baseValue,
                        record.getDelta(), taxonomyAggregations);
        String securityId = security.getUUID();
        String entryId = portfolioId != null && securityId != null ? portfolioId + ":" + securityId : securityId;

        return new PerformanceSecurityInfo(entryId, portfolioId, securityId, security.getName(),
                        new MoneyInfo(baseValue), new MoneyInfo(record.getDelta()), percent, assignments);
    }

    private List<PerformanceReferenceAccountInfo> toReferenceAccountEntries(Client client,
                    CurrencyConverterImpl converter, LocalDate startDate, LocalDate endDate,
                    Map<String, TaxonomyAggregation> taxonomyAggregations)
    {
        ClientSnapshot startSnapshot = ClientSnapshot.create(client, converter, startDate);
        ClientSnapshot endSnapshot = ClientSnapshot.create(client, converter, endDate);

        Function<AccountSnapshot, String> idExtractor = snapshot -> unwrapAccount(snapshot.getAccount()).getUUID();

        Map<String, Money> startValues = startSnapshot.getAccounts().stream()
                        .collect(Collectors.toMap(idExtractor, AccountSnapshot::getFunds));
        Map<String, Money> endValues = endSnapshot.getAccounts().stream()
                        .collect(Collectors.toMap(idExtractor, AccountSnapshot::getFunds));

        List<PerformanceReferenceAccountInfo> entries = new ArrayList<>();
        for (Account account : client.getAccounts())
        {
            Account unwrapped = unwrapAccount(account);
            Money startValue = startValues.getOrDefault(unwrapped.getUUID(),
                            Money.of(converter.getTermCurrency(), 0));
            Money endValue = endValues.getOrDefault(unwrapped.getUUID(),
                            Money.of(converter.getTermCurrency(), 0));
            Money delta = Money.of(converter.getTermCurrency(), 0);
            double percent = 0d;

            List<PerformanceTaxonomyAssignmentInfo> assignments = toTaxonomyAssignments(unwrapped, endValue, delta,
                            taxonomyAggregations);

            entries.add(new PerformanceReferenceAccountInfo(unwrapped.getUUID(), unwrapped.getName(),
                            new MoneyInfo(endValue), new MoneyInfo(delta), percent, assignments));
        }

        return entries;
    }

    private PerformanceTotalInfo toTotalPerformanceInfo(Client client, CurrencyConverterImpl converter,
                    LocalDate startDate, LocalDate endDate)
    {
        ClientSnapshot startSnapshot = ClientSnapshot.create(client, converter, startDate);
        ClientSnapshot endSnapshot = ClientSnapshot.create(client, converter, endDate);
        var interval = Interval.of(startDate, endDate);
        SecurityPerformanceSnapshot performance = SecurityPerformanceSnapshot.create(client, converter, interval,
                        startSnapshot, endSnapshot);

        var termCurrency = converter.getTermCurrency();
        var totalDelta = MutableMoney.of(termCurrency);

        performance.getRecords().forEach(record -> totalDelta.add(record.getDelta()));

        PerformanceIndex totalIndex = PerformanceIndex.forClient(client, converter, interval, new ArrayList<>());
        double totalPercent = totalIndex.getFinalAccumulatedPercentage() * 100d;
        long totalStartValue = firstNonZero(totalIndex.getTotals());

        return new PerformanceTotalInfo(new MoneyInfo(Money.of(termCurrency, totalStartValue)),
                        new MoneyInfo(totalDelta.toMoney()), totalPercent);
    }

    private PerformanceTaxonomyInfo toPerformanceTaxonomyInfo(Taxonomy taxonomy, TaxonomyAggregation aggregation)
    {
        if (taxonomy == null)
            return null;

        List<PerformanceTaxonomyClassificationInfo> classifications = new ArrayList<>();
        taxonomy.foreach(new Taxonomy.Visitor()
        {
            @Override
            public void visit(Classification classification)
            {
                String parentId = classification.getParent() != null ? classification.getParent().getId() : null;
                ClassificationPerformance performance = aggregation != null
                                ? aggregation.classificationPerformance.get(classification.getId())
                                : null;
                MoneyInfo startValue = performance != null ? new MoneyInfo(performance.start.toMoney()) : null;
                MoneyInfo delta = performance != null ? new MoneyInfo(performance.delta.toMoney()) : null;
                double percent = performance != null ? performance.percent() : 0d;

                classifications.add(new PerformanceTaxonomyClassificationInfo(classification.getId(), parentId,
                                classification.getName(), startValue, delta, percent));
            }
        });

        if (aggregation != null)
        {
            ClassificationPerformance unclassified = aggregation.classificationPerformance
                            .get(TaxonomyAggregation.UNCLASSIFIED_KEY);
            if (unclassified != null)
            {
                classifications.add(new PerformanceTaxonomyClassificationInfo(
                                TaxonomyAggregation.UNCLASSIFIED_KEY,
                                null,
                                Messages.LabelWithoutClassification,
                                new MoneyInfo(unclassified.start.toMoney()),
                                new MoneyInfo(unclassified.delta.toMoney()), unclassified.percent()));
            }
        }

        return new PerformanceTaxonomyInfo(taxonomy.getId(), taxonomy.getName(), classifications);
    }

    private List<PerformanceTaxonomyAssignmentInfo> toTaxonomyAssignments(InvestmentVehicle vehicle, Money baseValue,
                    Money delta, Map<String, TaxonomyAggregation> taxonomyAggregations)
    {
        if (taxonomyAggregations.isEmpty() || vehicle == null)
            return List.of();

        List<PerformanceTaxonomyAssignmentInfo> assignments = new ArrayList<>();
        for (TaxonomyAggregation aggregation : taxonomyAggregations.values())
        {
            List<Classification> classifications = aggregation.taxonomy.getClassifications(vehicle);
            if (classifications == null || classifications.isEmpty())
            {
                aggregateUnclassifiedPerformance(aggregation, baseValue, delta);
                continue;
            }

            for (Classification classification : classifications)
            {
                assignments.add(new PerformanceTaxonomyAssignmentInfo(aggregation.taxonomy.getId(),
                                aggregation.taxonomy.getName(), classification.getId(), classification.getName()));
                aggregateClassificationPerformance(aggregation, classification, baseValue, delta);
            }
        }

        return assignments.isEmpty() ? List.of() : assignments;
    }

    private void aggregateClassificationPerformance(TaxonomyAggregation aggregation, Classification classification,
                    Money baseValue, Money delta)
    {
        Classification current = classification;
        while (current != null && !Classification.VIRTUAL_ROOT.equals(current.getId()))
        {
            ClassificationPerformance performance = aggregation.classificationPerformance
                            .computeIfAbsent(current.getId(), key -> new ClassificationPerformance(aggregation.currency));
            performance.add(baseValue, delta);
            current = current.getParent();
        }
    }

    private void aggregateUnclassifiedPerformance(TaxonomyAggregation aggregation, Money baseValue, Money delta)
    {
        ClassificationPerformance performance = aggregation.classificationPerformance
                        .computeIfAbsent(TaxonomyAggregation.UNCLASSIFIED_KEY,
                                        key -> new ClassificationPerformance(aggregation.currency));
        performance.add(baseValue, delta);
    }

    private static final class TaxonomyAggregation
    {
        private static final String UNCLASSIFIED_KEY = "$unassigned$";
        private final Taxonomy taxonomy;
        private final String currency;
        private final Map<String, ClassificationPerformance> classificationPerformance = new HashMap<>();

        private TaxonomyAggregation(Taxonomy taxonomy, String currency)
        {
            this.taxonomy = taxonomy;
            this.currency = currency;
        }
    }

    private static final class ClassificationPerformance
    {
        private final MutableMoney start;
        private final MutableMoney delta;

        private ClassificationPerformance(String currency)
        {
            this.start = MutableMoney.of(currency);
            this.delta = MutableMoney.of(currency);
        }

        private void add(Money startValue, Money deltaValue)
        {
            if (startValue != null)
                this.start.add(startValue);
            if (deltaValue != null)
                this.delta.add(deltaValue);
        }

        private double percent()
        {
            long startAmount = this.start.getAmount();
            if (startAmount == 0)
                return 0d;
            return (this.delta.getAmount() * 100d) / startAmount;
        }
    }

    private long firstNonZero(long[] totals)
    {
        for (long value : totals)
            if (value != 0)
                return value;
        return totals.length > 0 ? totals[0] : 0;
    }
}
