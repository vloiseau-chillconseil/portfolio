package name.vloiseau.portfolio.graphql;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import io.leangen.graphql.annotations.GraphQLArgument;
import io.leangen.graphql.annotations.GraphQLQuery;
import name.abuchen.portfolio.model.Client;
import name.abuchen.portfolio.money.CurrencyConverterImpl;
import name.abuchen.portfolio.money.Money;
import name.abuchen.portfolio.money.Values;
import name.abuchen.portfolio.snapshot.PerformanceIndex;
import name.abuchen.portfolio.snapshot.ClientPerformanceSnapshot;
import name.abuchen.portfolio.ui.editor.ClientInput;
import name.abuchen.portfolio.ui.editor.ClientInputFactory;
import name.abuchen.portfolio.ui.util.ClientFilterMenu;
import name.abuchen.portfolio.util.Interval;

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
