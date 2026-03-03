package name.abuchen.portfolio.datatransfer.csv;

import java.text.MessageFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;

import name.abuchen.portfolio.Messages;
import name.abuchen.portfolio.datatransfer.Extractor;
import name.abuchen.portfolio.datatransfer.csv.CSVImporter.AmountField;
import name.abuchen.portfolio.datatransfer.csv.CSVImporter.AttributeField;
import name.abuchen.portfolio.datatransfer.csv.CSVImporter.Column;
import name.abuchen.portfolio.datatransfer.csv.CSVImporter.DateField;
import name.abuchen.portfolio.datatransfer.csv.CSVImporter.Field;
import name.abuchen.portfolio.model.AttributeType;
import name.abuchen.portfolio.model.Client;
import name.abuchen.portfolio.model.Security;
import name.abuchen.portfolio.online.impl.YahooFinanceQuoteFeed;

/* package */class CSVSecurityExtractor extends BaseCSVExtractor
{
    private final List<AttributeField> attributeFields = new ArrayList<>();

    /* package */ CSVSecurityExtractor(Client client)
    {
        super(client, Messages.CSVDefSecurities);

        var fields = getFields();
        fields.add(new Field("isin", Messages.CSVColumn_ISIN).setOptional(true)); //$NON-NLS-1$
        fields.add(new Field("wkn", Messages.CSVColumn_WKN).setOptional(true)); //$NON-NLS-1$
        fields.add(new Field("ticker", Messages.CSVColumn_TickerSymbol).setOptional(true)); //$NON-NLS-1$
        fields.add(new Field("name", Messages.CSVColumn_SecurityName).setOptional(true)); //$NON-NLS-1$
        fields.add(new Field("currency", Messages.CSVColumn_Currency).setOptional(true)); //$NON-NLS-1$
        fields.add(new Field("note", Messages.CSVColumn_Note).setOptional(true)); //$NON-NLS-1$

        fields.add(new DateField("date", Messages.CSVColumn_DateQuote).setOptional(true)); //$NON-NLS-1$
        fields.add(new AmountField("quote", Messages.CSVColumn_Quote, "Schluss", "Schlusskurs", "Close") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                        .setOptional(true));

        client.getSettings().getAttributeTypes().filter(type -> type.supports(Security.class)).forEach(type -> {
            var field = new AttributeField(attributeFieldCode(type), type, attributeFieldNames(type));
            field.setOptional(true);
            fields.add(field);
            attributeFields.add(field);
        });
    }

    @Override
    public String getCode()
    {
        return "investment-vehicle"; //$NON-NLS-1$
    }

    @Override
    void extract(List<Item> items, String[] rawValues, Map<String, Column> field2column) throws ParseException
    {
        var attributeValues = parseAttributeValues(rawValues, field2column);

        // check if we can identify a security
        var security = getSecurity(rawValues, field2column, s -> {
            s.setCurrencyCode(getCurrencyCode(Messages.CSVColumn_Currency, rawValues, field2column));

            var note = getText(Messages.CSVColumn_Note, rawValues, field2column);
            s.setNote(note);

            var tickerSymbol = getText(Messages.CSVColumn_TickerSymbol, rawValues, field2column);
            if (tickerSymbol != null && !tickerSymbol.isBlank())
            {
                s.setTickerSymbol(tickerSymbol);
                s.setFeed(YahooFinanceQuoteFeed.ID);
            }

            attributeValues.forEach((type, value) -> s.getAttributes().put(type, value));

            items.add(new Extractor.SecurityItem(s));
        });

        if (security == null)
            throw new ParseException(MessageFormat.format(Messages.CSVImportMissingSecurity,
                            new StringJoiner(", ").add(Messages.CSVColumn_ISIN) //$NON-NLS-1$
                                            .add(Messages.CSVColumn_TickerSymbol).add(Messages.CSVColumn_WKN)
                                            .toString()),
                            0);

        // nothing to do to add the security: if necessary, the security item
        // has been created in the callback of the #extractSecurity method

        // check if the data contains price

        getSecurityPrice(Messages.CSVColumn_DateQuote, rawValues, field2column)
                        .ifPresent(price -> items.add(new SecurityPriceItem(security, price)));
    }

    private Map<AttributeType, Object> parseAttributeValues(String[] rawValues, Map<String, Column> field2column)
                    throws ParseException
    {
        var attributeValues = new LinkedHashMap<AttributeType, Object>();

        for (var field : attributeFields)
        {
            var text = getText(field.getName(), rawValues, field2column);
            if (text == null)
                continue;

            try
            {
                var value = field.getAttributeType().getConverter().fromString(text);
                if (value != null)
                    attributeValues.put(field.getAttributeType(), value);
            }
            catch (IllegalArgumentException e)
            {
                throw new ParseException(e.getMessage(), 0);
            }
        }

        return attributeValues;
    }

    private static String attributeFieldCode(AttributeType type)
    {
        return "attribute:" + type.getId(); //$NON-NLS-1$
    }

    private static String[] attributeFieldNames(AttributeType type)
    {
        var label = type.getColumnLabel();
        if (label == null || label.isBlank())
            label = type.getName();
        if (label == null || label.isBlank())
            label = type.getId();

        if (Objects.equals(label, type.getName()) || type.getName() == null || type.getName().isBlank())
            return new String[] { label };

        return new String[] { label, type.getName() };
    }
}
