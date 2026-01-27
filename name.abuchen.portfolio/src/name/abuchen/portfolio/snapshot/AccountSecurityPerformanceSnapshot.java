package name.abuchen.portfolio.snapshot;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import name.abuchen.portfolio.model.Account;
import name.abuchen.portfolio.model.AccountTransaction;
import name.abuchen.portfolio.model.Client;
import name.abuchen.portfolio.model.Portfolio;
import name.abuchen.portfolio.model.PortfolioTransaction;
import name.abuchen.portfolio.model.Security;
import name.abuchen.portfolio.model.Transaction;
import name.abuchen.portfolio.model.TransactionOwner;
import name.abuchen.portfolio.money.CurrencyConverter;
import name.abuchen.portfolio.money.Money;
import name.abuchen.portfolio.money.MutableMoney;
import name.abuchen.portfolio.snapshot.security.CalculationLineItem;
import name.abuchen.portfolio.util.Interval;

public class AccountSecurityPerformanceSnapshot
{
    public static class Item
    {
        private final Account account;
        private final Security security;
        private final Money delta;
        private final double deltaPercent;
        private final List<CalculationLineItem> lineItems;

        private Item(Account account, Security security, Money delta, double deltaPercent,
                        List<CalculationLineItem> lineItems)
        {
            this.account = account;
            this.security = security;
            this.delta = delta;
            this.deltaPercent = deltaPercent;
            this.lineItems = lineItems;
        }

        public Account getAccount()
        {
            return account;
        }

        public Security getSecurity()
        {
            return security;
        }

        public Money getDelta()
        {
            return delta;
        }

        public double getDeltaPercent()
        {
            return deltaPercent;
        }

        public List<CalculationLineItem> getLineItems()
        {
            return lineItems;
        }
    }

    private static class AccountValuation implements CalculationLineItem
    {
        private final Account account;
        private final SecurityPosition position;
        private final LocalDateTime dateTime;
        private final boolean isStart;

        private AccountValuation(Account account, SecurityPosition position, LocalDateTime dateTime, boolean isStart)
        {
            this.account = account;
            this.position = position;
            this.dateTime = dateTime;
            this.isStart = isStart;
        }

        public boolean isStart()
        {
            return isStart;
        }

        @Override
        public TransactionOwner<?> getOwner()
        {
            return account;
        }

        @Override
        public String getLabel()
        {
            return null;
        }

        @Override
        public LocalDateTime getDateTime()
        {
            return dateTime;
        }

        @Override
        public long getOrderingHint()
        {
            return isStart ? 0 : Long.MAX_VALUE;
        }

        @Override
        public Money getValue()
        {
            return position.calculateValue();
        }

        @Override
        public Optional<SecurityPosition> getSecurityPosition()
        {
            return Optional.of(position);
        }
    }

    private final Client client;
    private final CurrencyConverter converter;
    private final Interval period;
    private final AccountSecuritySnapshot snapshotStart;
    private final AccountSecuritySnapshot snapshotEnd;
    private final Map<Account, Map<Security, Item>> items = new HashMap<>();

    public AccountSecurityPerformanceSnapshot(Client client, CurrencyConverter converter, Interval period)
    {
        this.client = client;
        this.converter = converter;
        this.period = period;
        this.snapshotStart = AccountSecuritySnapshot.create(client, converter, period.getStart());
        this.snapshotEnd = AccountSecuritySnapshot.create(client, converter, period.getEnd());

        calculate();
    }

    public Client getClient()
    {
        return client;
    }

    public AccountSecuritySnapshot getStartSnapshot()
    {
        return snapshotStart;
    }

    public AccountSecuritySnapshot getEndSnapshot()
    {
        return snapshotEnd;
    }

    public Map<Account, Map<Security, Item>> getItems()
    {
        return items;
    }

    private void calculate()
    {
        Map<Account, Map<Security, List<CalculationLineItem>>> lineItemsByAccount = new HashMap<>();

        addAccountTransactions(lineItemsByAccount);
        addPortfolioTransactions(lineItemsByAccount);
        addValuations(lineItemsByAccount, snapshotStart, true);
        addValuations(lineItemsByAccount, snapshotEnd, false);

        for (Map.Entry<Account, Map<Security, List<CalculationLineItem>>> byAccount : lineItemsByAccount.entrySet())
        {
            Account account = byAccount.getKey();
            Map<Security, Item> bySecurity = new HashMap<>();

            for (Map.Entry<Security, List<CalculationLineItem>> bySecurityItems : byAccount.getValue().entrySet())
            {
                Security security = bySecurityItems.getKey();
                List<CalculationLineItem> lineItems = bySecurityItems.getValue();
                Delta delta = calculateDelta(lineItems);

                if (!lineItems.isEmpty())
                    bySecurity.put(security, new Item(account, security, delta.delta, delta.deltaPercent, lineItems));
            }

            if (!bySecurity.isEmpty())
                items.put(account, bySecurity);
        }
    }

    private void addAccountTransactions(Map<Account, Map<Security, List<CalculationLineItem>>> lineItemsByAccount)
    {
        for (Account account : client.getAccounts())
        {
            for (AccountTransaction t : account.getTransactions())
            {
                if (t.getSecurity() == null || !period.contains(t.getDateTime()))
                    continue;

                switch (t.getType())
                {
                    case DIVIDENDS:
                    case INTEREST:
                    case INTEREST_CHARGE:
                    case TAXES:
                    case TAX_REFUND:
                    case FEES:
                    case FEES_REFUND:
                        lineItemsByAccount.computeIfAbsent(account, a -> new HashMap<>())
                                        .computeIfAbsent(t.getSecurity(), s -> new ArrayList<>())
                                        .add(CalculationLineItem.of(account, t));
                        break;
                    case BUY:
                    case SELL:
                    case DEPOSIT:
                    case REMOVAL:
                    case TRANSFER_IN:
                    case TRANSFER_OUT:
                        break;
                    default:
                        throw new UnsupportedOperationException();
                }
            }
        }
    }

    private void addPortfolioTransactions(Map<Account, Map<Security, List<CalculationLineItem>>> lineItemsByAccount)
    {
        for (Portfolio portfolio : client.getPortfolios())
        {
            for (PortfolioTransaction t : portfolio.getTransactions())
            {
                if (t.getSecurity() == null || !period.contains(t.getDateTime()))
                    continue;

                Account account = resolveAccount(portfolio, t);
                if (account == null)
                    continue;

                lineItemsByAccount.computeIfAbsent(account, a -> new HashMap<>())
                                .computeIfAbsent(t.getSecurity(), s -> new ArrayList<>())
                                .add(CalculationLineItem.of(portfolio, t));
            }
        }
    }

    private void addValuations(Map<Account, Map<Security, List<CalculationLineItem>>> lineItemsByAccount,
                    AccountSecuritySnapshot snapshot, boolean isStart)
    {
        LocalDateTime dateTime = snapshot.getTime().atStartOfDay();

        for (Map.Entry<Account, Map<Security, SecurityPosition>> byAccount : snapshot.getPositions().entrySet())
        {
            Account account = byAccount.getKey();

            for (Map.Entry<Security, SecurityPosition> bySecurity : byAccount.getValue().entrySet())
            {
                lineItemsByAccount.computeIfAbsent(account, a -> new HashMap<>())
                                .computeIfAbsent(bySecurity.getKey(), s -> new ArrayList<>())
                                .add(new AccountValuation(account, bySecurity.getValue(), dateTime, isStart));
            }
        }
    }

    private Account resolveAccount(Portfolio portfolio, PortfolioTransaction t)
    {
        if (t.getCrossEntry() != null)
        {
            TransactionOwner<? extends Transaction> owner = t.getCrossEntry().getCrossOwner(t);
            if (owner instanceof Account account)
                return account;
        }

        return portfolio.getReferenceAccount();
    }

    private Delta calculateDelta(List<CalculationLineItem> lineItems)
    {
        MutableMoney delta = MutableMoney.of(converter.getTermCurrency());
        MutableMoney cost = MutableMoney.of(converter.getTermCurrency());

        for (CalculationLineItem item : lineItems)
        {
            if (item instanceof AccountValuation valuation)
            {
                Money amount = valuation.getValue().with(converter.at(valuation.getDateTime()));
                if (valuation.isStart())
                {
                    delta.subtract(amount);
                    cost.add(amount);
                }
                else
                {
                    delta.add(amount);
                }
            }
            else if (item instanceof CalculationLineItem.DividendPayment dividend)
            {
                delta.add(dividend.getValue().with(converter.at(dividend.getDateTime())));
            }
            else if (item instanceof CalculationLineItem.TransactionItem txItem)
            {
                Transaction tx = txItem.getTransaction().orElseThrow(IllegalArgumentException::new);
                if (tx instanceof AccountTransaction at)
                {
                    applyAccountTransaction(delta, at);
                }
                else if (tx instanceof PortfolioTransaction pt)
                {
                    applyPortfolioTransaction(delta, cost, pt);
                }
                else
                {
                    throw new UnsupportedOperationException();
                }
            }
            else
            {
                throw new UnsupportedOperationException();
            }
        }

        double deltaPercent;
        if (delta.getAmount() == 0L && cost.getAmount() == 0L)
            deltaPercent = 0d;
        else
            deltaPercent = delta.getAmount() / (double) cost.getAmount();

        return new Delta(delta.toMoney(), deltaPercent);
    }

    private void applyAccountTransaction(MutableMoney delta, AccountTransaction t)
    {
        Money amount = t.getMonetaryAmount().with(converter.at(t.getDateTime()));

        switch (t.getType())
        {
            case DIVIDENDS:
            case INTEREST:
                delta.add(amount);
                break;
            case INTEREST_CHARGE:
                delta.subtract(amount);
                break;
            case TAXES:
            case FEES:
                delta.subtract(amount);
                break;
            case TAX_REFUND:
            case FEES_REFUND:
                delta.add(amount);
                break;
            default:
                throw new UnsupportedOperationException("unsupported type " + t.getType()); //$NON-NLS-1$
        }
    }

    private void applyPortfolioTransaction(MutableMoney delta, MutableMoney cost, PortfolioTransaction t)
    {
        Money amount = t.getMonetaryAmount().with(converter.at(t.getDateTime()));

        switch (t.getType())
        {
            case BUY:
            case DELIVERY_INBOUND:
                delta.subtract(amount);
                cost.add(amount);
                break;
            case SELL:
            case DELIVERY_OUTBOUND:
                delta.add(amount);
                break;
            case TRANSFER_IN:
            case TRANSFER_OUT:
                break;
            default:
                throw new UnsupportedOperationException("unsupported type " + t.getType()); //$NON-NLS-1$
        }
    }

    private static class Delta
    {
        private final Money delta;
        private final double deltaPercent;

        private Delta(Money delta, double deltaPercent)
        {
            this.delta = delta;
            this.deltaPercent = deltaPercent;
        }
    }
}
