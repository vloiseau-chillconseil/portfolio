package name.vloiseau.portfolio.graphql;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import io.leangen.graphql.annotations.GraphQLArgument;
import io.leangen.graphql.annotations.GraphQLMutation;
import io.leangen.graphql.annotations.GraphQLQuery;
import io.leangen.graphql.annotations.GraphQLSubscription;
import name.abuchen.portfolio.model.Client;
import name.abuchen.portfolio.model.Security;
import name.abuchen.portfolio.money.CurrencyConverterImpl;
import name.abuchen.portfolio.money.Money;
import name.abuchen.portfolio.money.Values;
import name.abuchen.portfolio.snapshot.ClientSnapshot;
import name.abuchen.portfolio.snapshot.PerformanceIndex;
import name.abuchen.portfolio.snapshot.ClientPerformanceSnapshot;
import name.abuchen.portfolio.ui.editor.ClientInput;
import name.abuchen.portfolio.ui.editor.ClientInputFactory;
import name.abuchen.portfolio.ui.jobs.priceupdate.PriceUpdateProgress;
import name.abuchen.portfolio.ui.jobs.priceupdate.PriceUpdateSnapshot;
import name.abuchen.portfolio.ui.jobs.priceupdate.UpdatePricesJob;
import name.abuchen.portfolio.ui.util.ClientFilterMenu;
import name.abuchen.portfolio.util.Interval;
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
                    @GraphQLArgument(name = "endDate") String endDate)
    {
        Optional<ClientInput> input = findClientInput(clientId);
        if (input.isEmpty())
            return List.of();

        LocalDate start = LocalDate.parse(startDate);
        LocalDate end = LocalDate.parse(endDate);

        ClientFilterMenu menu = new ClientFilterMenu(input.get().getClient(), input.get().getPreferenceStore());
        Optional<ClientFilterMenu.Item> item = menu.getAllItems().filter(i -> i.getId().equals(filterId)).findFirst();
        if (item.isEmpty())
            return List.of();

        Client filtered = item.get().getFilter().filter(input.get().getClient());
        var converter = new CurrencyConverterImpl(input.get().getExchangeRateProviderFacory(),
                        input.get().getClient().getBaseCurrency());

        List<Exception> warnings = new ArrayList<>();
        PerformanceIndex index = PerformanceIndex.forClient(filtered, converter, Interval.of(start, end), warnings);

        LocalDate[] dates = index.getDates();
        double[] accumulated = index.getAccumulatedPercentage();
        long initialTotal = index.getTotals().length > 0 ? index.getTotals()[0] : 0;

        List<DeltaPoint> points = new ArrayList<>(dates.length);
        for (int i = 0; i < dates.length; i++)
        {
            long value = Math.round(accumulated[i] * initialTotal);
            points.add(new DeltaPoint(dates[i].toString(), new MoneyInfo(Money.of(index.getCurrency(), value))));
        }

        return points;
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
}
