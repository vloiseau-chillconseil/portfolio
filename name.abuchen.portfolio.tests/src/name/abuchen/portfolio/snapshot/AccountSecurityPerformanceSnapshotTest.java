package name.abuchen.portfolio.snapshot;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.is;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;

import org.junit.Test;

import name.abuchen.portfolio.junit.TestCurrencyConverter;
import name.abuchen.portfolio.model.Account;
import name.abuchen.portfolio.model.BuySellEntry;
import name.abuchen.portfolio.model.Client;
import name.abuchen.portfolio.model.Portfolio;
import name.abuchen.portfolio.model.PortfolioTransaction;
import name.abuchen.portfolio.model.Security;
import name.abuchen.portfolio.model.SecurityPrice;
import name.abuchen.portfolio.money.CurrencyUnit;
import name.abuchen.portfolio.money.Money;
import name.abuchen.portfolio.money.Values;
import name.abuchen.portfolio.util.Interval;

@SuppressWarnings("nls")
public class AccountSecurityPerformanceSnapshotTest
{
    @Test
    public void testDeltaByAccountAndSecurity()
    {
        LocalDate startDate = LocalDate.of(2010, Month.DECEMBER, 31);
        LocalDate endDate = LocalDate.of(2011, Month.DECEMBER, 31);

        Client client = new Client();
        Security security = new Security("Test", CurrencyUnit.EUR);
        security.addPrice(new SecurityPrice(startDate, Values.Quote.factorize(100)));
        security.addPrice(new SecurityPrice(endDate, Values.Quote.factorize(120)));
        client.addSecurity(security);

        Account accountA = new Account("Account A");
        Account accountB = new Account("Account B");
        client.addAccount(accountA);
        client.addAccount(accountB);

        Portfolio portfolio = new Portfolio();
        portfolio.setReferenceAccount(accountA);
        client.addPortfolio(portfolio);

        BuySellEntry buyA = new BuySellEntry(portfolio, accountA);
        buyA.setType(PortfolioTransaction.Type.BUY);
        buyA.setDate(LocalDateTime.of(2010, Month.JANUARY, 1, 0, 0));
        buyA.setSecurity(security);
        buyA.setMonetaryAmount(Money.of(CurrencyUnit.EUR, 1000_00));
        buyA.setShares(Values.Share.factorize(10));
        buyA.insert();

        BuySellEntry buyB = new BuySellEntry(portfolio, accountB);
        buyB.setType(PortfolioTransaction.Type.BUY);
        buyB.setDate(LocalDateTime.of(2010, Month.FEBRUARY, 1, 0, 0));
        buyB.setSecurity(security);
        buyB.setMonetaryAmount(Money.of(CurrencyUnit.EUR, 500_00));
        buyB.setShares(Values.Share.factorize(5));
        buyB.insert();

        var converter = new TestCurrencyConverter();
        var snapshot = new AccountSecurityPerformanceSnapshot(client, converter, Interval.of(startDate, endDate));

        var itemA = snapshot.getItems().get(accountA).get(security);
        var itemB = snapshot.getItems().get(accountB).get(security);

        assertThat(itemA.getDelta(), is(Money.of(CurrencyUnit.EUR, 200_00)));
        assertThat(itemA.getDeltaPercent(), is(closeTo(0.2d, 0.0001d)));

        assertThat(itemB.getDelta(), is(Money.of(CurrencyUnit.EUR, 100_00)));
        assertThat(itemB.getDeltaPercent(), is(closeTo(0.2d, 0.0001d)));
    }
}
