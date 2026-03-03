package name.abuchen.portfolio.datatransfer.csv;

import static name.abuchen.portfolio.datatransfer.csv.CSVExtractorTestUtil.buildField2Column;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsEmptyCollection.empty;
import static org.junit.Assert.assertNull;

import java.text.MessageFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

import org.junit.Test;

import name.abuchen.portfolio.Messages;
import name.abuchen.portfolio.datatransfer.Extractor.Item;
import name.abuchen.portfolio.datatransfer.Extractor.SecurityItem;
import name.abuchen.portfolio.datatransfer.actions.AssertImportActions;
import name.abuchen.portfolio.model.AttributeType;
import name.abuchen.portfolio.model.Client;
import name.abuchen.portfolio.model.Security;
import name.abuchen.portfolio.money.CurrencyUnit;
import name.abuchen.portfolio.online.impl.AlphavantageQuoteFeed;
import name.abuchen.portfolio.online.impl.YahooFinanceQuoteFeed;

@SuppressWarnings("nls")
public class CSVSecurityExtractorTest
{
    @Test
    public void testSecurityCreationWithAllSecurityData() throws ParseException
    {
        Client client = new Client();

        CSVExtractor extractor = new CSVSecurityExtractor(client);

        List<Exception> errors = new ArrayList<Exception>();
        List<Item> results = extractor.extract(0, Arrays.<String[]>asList( //
                        new String[] { //
                                        "DE0007164600", // ISIN
                                        "716460", // WKN
                                        "SAP.DE", // TickerSymbol
                                        "SAP SE", // Security name
                                        "EUR", // Currency
                                        "Notiz" // Note
                        }), buildField2Column(extractor), errors);

        assertThat(errors, empty());
        assertThat(results.size(), is(1));
        new AssertImportActions().check(results, CurrencyUnit.EUR);

        Security security = results.stream().filter(SecurityItem.class::isInstance).findFirst()
                        .orElseThrow(IllegalArgumentException::new).getSecurity();

        assertThat(security.getIsin(), is("DE0007164600"));
        assertThat(security.getWkn(), is("716460"));
        assertThat(security.getTickerSymbol(), is("SAP.DE"));
        assertThat(security.getName(), is("SAP SE"));
        assertThat(security.getCurrencyCode(), is(CurrencyUnit.EUR));
        assertThat(security.getNote(), is("Notiz"));
        assertThat(security.getFeed(), is(YahooFinanceQuoteFeed.ID));
    }

    @Test
    public void testSecurityIsNotCreatedIfItAlreadyExists()
    {
        Security security = new Security("SAP SE", CurrencyUnit.EUR);
        security.setIsin("DE0007164600");
        security.setWkn("716460");
        security.setTickerSymbol("SAP.DE");
        security.setNote("Notiz");
        security.setFeed(AlphavantageQuoteFeed.ID);

        Client client = new Client();
        client.addSecurity(security);

        CSVExtractor extractor = new CSVSecurityExtractor(client);

        List<Exception> errors = new ArrayList<Exception>();
        List<Item> results = extractor.extract(0, Arrays.<String[]>asList( //
                        new String[] { //
                                        "DE0007164600", // ISIN
                                        "716460", // WKN
                                        "SAP.DE", // TickerSymbol
                                        "SAP SE", // Security name
                                        "EUR", // Currency
                                        "Notiz" // Note
                        }), buildField2Column(extractor), errors);

        assertThat(errors, empty());
        assertThat(results, empty());
        assertThat(security.getFeed(), is(AlphavantageQuoteFeed.ID));
    }

    @Test
    public void testSecurityCreationOnlyWithISIN()
    {
        Client client = new Client();

        CSVExtractor extractor = new CSVSecurityExtractor(client);

        List<Exception> errors = new ArrayList<Exception>();
        List<Item> results = extractor.extract(0, Arrays.<String[]>asList( //
                        new String[] { //
                                        "DE0007164600", // ISIN
                                        "", // WKN
                                        "", // TickerSymbol
                                        "", // Security name
                                        "", // Currency
                                        "" // Note
                        }), buildField2Column(extractor), errors);

        assertThat(errors, empty());
        assertThat(results.size(), is(1));
        new AssertImportActions().check(results, CurrencyUnit.EUR);

        Security security = results.stream().filter(SecurityItem.class::isInstance).findFirst()
                        .orElseThrow(IllegalArgumentException::new).getSecurity();

        assertThat(security.getIsin(), is("DE0007164600"));
        assertNull(security.getWkn());
        assertNull(security.getTickerSymbol());
        assertThat(security.getName(), is(MessageFormat.format(Messages.CSVImportedSecurityLabel, "DE0007164600")));
        assertThat(security.getCurrencyCode(), is(CurrencyUnit.EUR));
        assertNull(security.getNote());
        assertNull(security.getFeed());
    }

    @Test
    public void testSecurityCreationOnlyWithWKN()
    {
        Client client = new Client();

        CSVExtractor extractor = new CSVSecurityExtractor(client);

        List<Exception> errors = new ArrayList<Exception>();
        List<Item> results = extractor.extract(0, Arrays.<String[]>asList( //
                        new String[] { //
                                        "", // ISIN
                                        "716460", // WKN
                                        "", // TickerSymbol
                                        "", // Security name
                                        "", // Currency
                                        "" // Note
                        }), buildField2Column(extractor), errors);

        assertThat(errors, empty());
        assertThat(results.size(), is(1));
        new AssertImportActions().check(results, CurrencyUnit.EUR);

        Security security = results.stream().filter(SecurityItem.class::isInstance).findFirst()
                        .orElseThrow(IllegalArgumentException::new).getSecurity();

        assertNull(security.getIsin());
        assertThat(security.getWkn(), is("716460"));
        assertNull(security.getTickerSymbol());
        assertThat(security.getName(), is(MessageFormat.format(Messages.CSVImportedSecurityLabel, "716460")));
        assertThat(security.getCurrencyCode(), is(CurrencyUnit.EUR));
        assertNull(security.getNote());
        assertNull(security.getFeed());
    }

    @Test
    public void testSecurityCreationOnlyWithTickerSymbol()
    {
        Client client = new Client();

        CSVExtractor extractor = new CSVSecurityExtractor(client);

        List<Exception> errors = new ArrayList<Exception>();
        List<Item> results = extractor.extract(0, Arrays.<String[]>asList( //
                        new String[] { //
                                        "", // ISIN
                                        "", // WKN
                                        "SAP.DE", // TickerSymbol
                                        "", // Security name
                                        "", // Currency
                                        "" // Note
                        }), buildField2Column(extractor), errors);

        assertThat(errors, empty());
        assertThat(results.size(), is(1));
        new AssertImportActions().check(results, CurrencyUnit.EUR);

        Security security = results.stream().filter(SecurityItem.class::isInstance).findFirst()
                        .orElseThrow(IllegalArgumentException::new).getSecurity();

        assertNull(security.getIsin());
        assertNull(security.getWkn());
        assertThat(security.getTickerSymbol(), is("SAP.DE"));
        assertThat(security.getName(), is(MessageFormat.format(Messages.CSVImportedSecurityLabel, "SAP.DE")));
        assertThat(security.getCurrencyCode(), is(CurrencyUnit.EUR));
        assertNull(security.getNote());
        assertThat(security.getFeed(), is(YahooFinanceQuoteFeed.ID));
    }

    @Test
    public void testSecurityCreationOnlyWithSecurityName()
    {
        Client client = new Client();

        CSVExtractor extractor = new CSVSecurityExtractor(client);

        List<Exception> errors = new ArrayList<Exception>();
        List<Item> results = extractor.extract(0, Arrays.<String[]>asList( //
                        new String[] { //
                                        "", // ISIN
                                        "", // WKN
                                        "", // TickerSymbol
                                        "SAP SE", // Security name
                                        "", // Currency
                                        "" // Note
                        }), buildField2Column(extractor), errors);

        assertThat(errors, empty());
        assertThat(results.size(), is(1));
        new AssertImportActions().check(results, CurrencyUnit.EUR);

        Security security = results.stream().filter(SecurityItem.class::isInstance).findFirst()
                        .orElseThrow(IllegalArgumentException::new).getSecurity();

        assertNull(security.getIsin());
        assertNull(security.getWkn());
        assertNull(security.getTickerSymbol());
        assertThat(security.getName(), is("SAP SE"));
        assertThat(security.getCurrencyCode(), is(CurrencyUnit.EUR));
        assertNull(security.getNote());
        assertNull(security.getFeed());
    }

    @Test
    public void testSecurityIsCreatedOnlyOnce()
    {
        Client client = new Client();

        CSVExtractor extractor = new CSVSecurityExtractor(client);

        List<Exception> errors = new ArrayList<Exception>();
        List<Item> results = extractor.extract(0, Arrays.<String[]>asList( //
                        new String[] { //
                                        "DE0007164600", // ISIN
                                        "716460", // WKN
                                        "SAP.DE", // TickerSymbol
                                        "SAP SE", // Security name
                                        "EUR", // Currency
                                        "Notiz" // Note
                        }, //
                        new String[] { //
                                        "DE0007164600", // ISIN
                                        "716460", // WKN
                                        "SAP.DE", // TickerSymbol
                                        "SAP SE", // Security name
                                        "EUR", // Currency
                                        "Notiz" // Note
                        }), buildField2Column(extractor), errors);

        assertThat(results.size(), is(1));
        assertThat(errors.size(), is(0)); // no warning a/b duplicate imports

        Security security = results.stream().filter(SecurityItem.class::isInstance).findFirst()
                        .orElseThrow(IllegalArgumentException::new).getSecurity();

        assertThat(security.getIsin(), is("DE0007164600"));
        assertThat(security.getWkn(), is("716460"));
        assertThat(security.getTickerSymbol(), is("SAP.DE"));
        assertThat(security.getName(), is("SAP SE"));
        assertThat(security.getCurrencyCode(), is(CurrencyUnit.EUR));
        assertThat(security.getNote(), is("Notiz"));
        assertThat(security.getFeed(), is(YahooFinanceQuoteFeed.ID));
    }

    @Test
    public void testSecurityCreationWithCustomAttribute()
    {
        Client client = new Client();

        Map<AttributeType, String> attributeValues = new LinkedHashMap<>();
        attributeValues.put(createAttribute(client, "custom-text", "Custom Text", String.class, //$NON-NLS-1$ //$NON-NLS-2$
                        AttributeType.StringConverter.class), "Internal"); //$NON-NLS-1$
        attributeValues.put(createAttribute(client, "custom-amount", "Custom Amount", Long.class, //$NON-NLS-1$ //$NON-NLS-2$
                        AttributeType.AmountConverter.class), "1234"); //$NON-NLS-1$
        attributeValues.put(createAttribute(client, "custom-amount-plain", "Custom Amount Plain", Long.class, //$NON-NLS-1$ //$NON-NLS-2$
                        AttributeType.AmountPlainConverter.class), "567"); //$NON-NLS-1$
        attributeValues.put(createAttribute(client, "custom-quote", "Custom Quote", Long.class, //$NON-NLS-1$ //$NON-NLS-2$
                        AttributeType.QuoteConverter.class), "89"); //$NON-NLS-1$
        attributeValues.put(createAttribute(client, "custom-share", "Custom Share", Long.class, //$NON-NLS-1$ //$NON-NLS-2$
                        AttributeType.ShareConverter.class), "10"); //$NON-NLS-1$
        attributeValues.put(createAttribute(client, "custom-percent", "Custom Percent", Double.class, //$NON-NLS-1$ //$NON-NLS-2$
                        AttributeType.PercentConverter.class), "12%"); //$NON-NLS-1$
        attributeValues.put(createAttribute(client, "custom-percent-plain", "Custom Percent Plain", Double.class, //$NON-NLS-1$ //$NON-NLS-2$
                        AttributeType.PercentPlainConverter.class), "2,5"); //$NON-NLS-1$
        attributeValues.put(createAttribute(client, "custom-date", "Custom Date", java.time.LocalDate.class, //$NON-NLS-1$ //$NON-NLS-2$
                        AttributeType.DateConverter.class), "2024-01-15"); //$NON-NLS-1$
        attributeValues.put(createAttribute(client, "custom-boolean", "Custom Boolean", Boolean.class, //$NON-NLS-1$ //$NON-NLS-2$
                        AttributeType.BooleanConverter.class), "true"); //$NON-NLS-1$
        attributeValues.put(createAttribute(client, "custom-limit", "Custom Limit", name.abuchen.portfolio.model.LimitPrice.class, //$NON-NLS-1$ //$NON-NLS-2$
                        AttributeType.LimitPriceConverter.class), "<=100"); //$NON-NLS-1$
        attributeValues.put(createAttribute(client, "custom-bookmark", "Custom Bookmark", name.abuchen.portfolio.model.Bookmark.class, //$NON-NLS-1$ //$NON-NLS-2$
                        AttributeType.BookmarkConverter.class), "https://example.com"); //$NON-NLS-1$
        attributeValues.put(createAttribute(client, "custom-image", "Custom Image", String.class, //$NON-NLS-1$ //$NON-NLS-2$
                        AttributeType.ImageConverter.class), "logo.png"); //$NON-NLS-1$

        CSVExtractor extractor = new CSVSecurityExtractor(client);

        List<Exception> errors = new ArrayList<Exception>();
        List<String> columns = new ArrayList<>(
                        Arrays.asList("isin", "wkn", "ticker", "name", "currency", "note")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
        attributeValues.keySet().forEach(attribute -> columns.add("attribute:" + attribute.getId())); //$NON-NLS-1$

        List<String> row = new ArrayList<>(Arrays.asList("DE0007164600", "716460", "", "SAP SE", "EUR", "")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
        attributeValues.values().forEach(row::add);
        List<Item> results = extractor.extract(0, Arrays.<String[]>asList(row.toArray(new String[0])),
                        buildField2Column(extractor, columns.toArray(new String[0])), errors);

        assertThat(errors, empty());
        assertThat(results.size(), is(1));

        Security security = results.stream().filter(SecurityItem.class::isInstance).findFirst()
                        .orElseThrow(IllegalArgumentException::new).getSecurity();

        attributeValues.forEach((attribute, value) -> assertThat(security.getAttributes().get(attribute),
                        is(attribute.getConverter().fromString(value))));
    }

    private AttributeType createAttribute(Client client, String id, String name, Class<?> type,
                    Class<? extends AttributeType.Converter> converter)
    {
        AttributeType attribute = new AttributeType(id);
        attribute.setName(name);
        attribute.setColumnLabel(name);
        attribute.setType(type);
        attribute.setConverter(converter);
        attribute.setTarget(Security.class);
        client.getSettings().addAttributeType(attribute);
        return attribute;
    }

    @Test
    public void testSecurityCreationWithoutEnoughSecurityData()
    {
        Client client = new Client();

        CSVExtractor extractor = new CSVSecurityExtractor(client);

        List<Exception> errors = new ArrayList<Exception>();
        List<Item> results = extractor.extract(0, Arrays.<String[]>asList( //
                        new String[] { //
                                        "", // ISIN
                                        "", // WKN
                                        "", // TickerSymbol
                                        "", // Security name
                                        "USD", // Currency
                                        "Notiz" // Note
                        }), buildField2Column(extractor), errors);

        assertThat(results, empty());
        assertThat(errors.size(), is(1));

        assertThat(errors.get(0).getMessage(), is(MessageFormat.format(Messages.CSVLineXwithMsgY, "1", //
                        MessageFormat.format(Messages.CSVImportMissingSecurity, //
                                        new StringJoiner(", ") //
                                                        .add(Messages.CSVColumn_ISIN) // ISIN
                                                        .add(Messages.CSVColumn_TickerSymbol) // TickerSymbol
                                                        .add(Messages.CSVColumn_WKN) // WKN
                                                        .toString()), //
                        new ArrayList<>(Arrays.asList( //
                                        "", // ISIN
                                        "", // WKN
                                        "", // TickerSymbol
                                        "", // Security name
                                        "USD", // Currency
                                        "Notiz" // Note
                        )))));
    }
}
