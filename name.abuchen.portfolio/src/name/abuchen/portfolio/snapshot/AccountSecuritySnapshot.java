package name.abuchen.portfolio.snapshot;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import name.abuchen.portfolio.model.Account;
import name.abuchen.portfolio.model.Client;
import name.abuchen.portfolio.model.Portfolio;
import name.abuchen.portfolio.model.PortfolioTransaction;
import name.abuchen.portfolio.model.Security;
import name.abuchen.portfolio.model.SecurityPrice;
import name.abuchen.portfolio.model.Transaction;
import name.abuchen.portfolio.model.TransactionOwner;
import name.abuchen.portfolio.money.CurrencyConverter;

public class AccountSecuritySnapshot
{
    private final CurrencyConverter converter;
    private final LocalDate date;
    private final Map<Account, Map<Security, SecurityPosition>> positions = new HashMap<>();

    public static AccountSecuritySnapshot create(Client client, CurrencyConverter converter, LocalDate date)
    {
        AccountSecuritySnapshot snapshot = new AccountSecuritySnapshot(converter, date);
        snapshot.collectPositions(client);
        return snapshot;
    }

    private AccountSecuritySnapshot(CurrencyConverter converter, LocalDate date)
    {
        this.converter = converter;
        this.date = date;
    }

    public LocalDate getTime()
    {
        return date;
    }

    public CurrencyConverter getCurrencyConverter()
    {
        return converter;
    }

    public Map<Account, Map<Security, SecurityPosition>> getPositions()
    {
        return positions;
    }

    private void collectPositions(Client client)
    {
        Map<Account, Map<Security, List<PortfolioTransaction>>> transactionsByAccount = new HashMap<>();

        for (Portfolio portfolio : client.getPortfolios())
        {
            for (PortfolioTransaction t : portfolio.getTransactions())
            {
                if (t.getSecurity() == null || t.getDateTime().toLocalDate().isAfter(date))
                    continue;

                Account account = resolveAccount(portfolio, t);
                if (account == null)
                    continue;

                transactionsByAccount.computeIfAbsent(account, a -> new HashMap<>())
                                .computeIfAbsent(t.getSecurity(), s -> new ArrayList<>())
                                .add(t);
            }
        }

        for (Map.Entry<Account, Map<Security, List<PortfolioTransaction>>> byAccount : transactionsByAccount
                        .entrySet())
        {
            Account account = byAccount.getKey();
            Map<Security, SecurityPosition> bySecurity = new HashMap<>();

            for (Map.Entry<Security, List<PortfolioTransaction>> bySecurityTransactions : byAccount.getValue()
                            .entrySet())
            {
                Security security = bySecurityTransactions.getKey();
                List<PortfolioTransaction> transactions = bySecurityTransactions.getValue();

                SecurityPrice price = resolvePrice(security, transactions);
                SecurityPosition position = new SecurityPosition(security, converter, price, transactions);
                if (position.getShares() != 0)
                    bySecurity.put(security, position);
            }

            if (!bySecurity.isEmpty())
                positions.put(account, bySecurity);
        }
    }

    private SecurityPrice resolvePrice(Security security, List<PortfolioTransaction> transactions)
    {
        SecurityPrice price = security.getSecurityPrice(date);

        if (price.getValue() == 0L)
        {
            Optional<PortfolioTransaction> last = transactions.stream()
                            .sorted(Transaction.BY_DATE.reversed())
                            .findFirst();

            if (last.isPresent())
            {
                PortfolioTransaction t = last.get();
                price = new SecurityPrice(t.getDateTime().toLocalDate(),
                                t.getGrossPricePerShare(converter.with(security.getCurrencyCode())).getAmount());
            }
        }

        return price;
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
}
