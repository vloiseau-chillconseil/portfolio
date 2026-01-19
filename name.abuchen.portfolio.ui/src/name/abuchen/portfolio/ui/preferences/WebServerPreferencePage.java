package name.abuchen.portfolio.ui.preferences;

import org.eclipse.jface.preference.BooleanFieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.preference.IntegerFieldEditor;
import org.eclipse.jface.preference.StringFieldEditor;

import name.abuchen.portfolio.ui.Messages;
import name.abuchen.portfolio.ui.UIConstants;

public class WebServerPreferencePage extends FieldEditorPreferencePage
{
    public WebServerPreferencePage()
    {
        super(GRID);
        setTitle(Messages.PrefTitleWebServer);
    }

    @Override
    protected void createFieldEditors()
    {
        addField(new BooleanFieldEditor(UIConstants.Preferences.WEB_SERVER_ENABLED,
                        Messages.PrefLabelWebServerEnabled, getFieldEditorParent()));

        var hostField = new StringFieldEditor(UIConstants.Preferences.WEB_SERVER_HOST,
                        Messages.PrefLabelWebServerHost, getFieldEditorParent());
        hostField.setEmptyStringAllowed(false);
        addField(hostField);

        var portField = new IntegerFieldEditor(UIConstants.Preferences.WEB_SERVER_PORT,
                        Messages.PrefLabelWebServerPort, getFieldEditorParent());
        portField.setValidRange(1, 65535);
        addField(portField);
    }
}
