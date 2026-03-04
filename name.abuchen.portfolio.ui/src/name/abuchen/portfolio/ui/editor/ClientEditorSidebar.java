package name.abuchen.portfolio.ui.editor;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.DropTargetAdapter;
import org.eclipse.swt.dnd.DropTargetEvent;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.events.ControlListener;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;

import name.abuchen.portfolio.model.AttributeType;
import name.abuchen.portfolio.model.Security;
import name.abuchen.portfolio.model.Watchlist;
import name.abuchen.portfolio.ui.Images;
import name.abuchen.portfolio.ui.dnd.ImportFromFileDropAdapter;
import name.abuchen.portfolio.ui.dnd.SecurityTransfer;
import name.abuchen.portfolio.ui.editor.Navigation.Item;
import name.abuchen.portfolio.ui.editor.Navigation.Tag;
import name.abuchen.portfolio.ui.views.SecurityListView;

/* package */class ClientEditorSidebar
{
    private PortfolioPart editor;

    private Sidebar<Navigation.Item> sidebar;

    public ClientEditorSidebar(PortfolioPart editor)
    {
        this.editor = editor;
    }

    public Control createSidebarControl(Composite parent)
    {
        Sidebar.Model<Navigation.Item> model = new Sidebar.Model<Navigation.Item>()
        {
            @Override
            public Stream<Navigation.Item> getElements()
            {
                return editor.getClientInput().getNavigation().getRoots().filter(i -> !i.contains(Tag.HIDE));
            }

            @Override
            public Stream<Navigation.Item> getChildren(Item item)
            {
                return item.getChildren().filter(i -> !i.contains(Tag.HIDE));
            }

            @Override
            public String getLabel(Item item)
            {
                return item.getLabel();
            }

            @Override
            public Optional<Images> getImage(Item item)
            {
                return Optional.ofNullable(item.getImage());
            }

            @Override
            public void select(Navigation.Item item)
            {
                if (item.getViewClass() != null)
                    editor.activateView(item);
            }

            @Override
            public Navigation.MenuListener getActionMenu(Navigation.Item item)
            {
                return item.getActionMenu();
            }

            @Override
            public Navigation.MenuListener getContextMenu(Navigation.Item item)
            {
                return item.getContextMenu();
            }
        };

        ScrolledComposite scrolledComposite = new ScrolledComposite(parent, SWT.V_SCROLL);

        sidebar = new Sidebar<>(scrolledComposite, editor, model);

        editor.getClientInput().getNavigation().findAll(item -> item.getViewClass() == SecurityListView.class)
                        .forEach(this::setupAllSecuritesAndWatchlistDnD);

        scrolledComposite.setContent(sidebar);
        scrolledComposite.setExpandVertical(true);
        scrolledComposite.setExpandHorizontal(true);

        parent.getParent().addControlListener(ControlListener.controlResizedAdapter(
                        e -> scrolledComposite.setMinSize(sidebar.computeSize(SWT.DEFAULT, SWT.DEFAULT))));

        Navigation.Listener listener = item -> {
            sidebar.rebuild();
            editor.getClientInput().getNavigation().findAll(i -> item.getViewClass() == SecurityListView.class)
                            .forEach(this::setupAllSecuritesAndWatchlistDnD);

            scrolledComposite.setMinSize(sidebar.computeSize(SWT.DEFAULT, SWT.DEFAULT));
            sidebar.layout(true);
            sidebar.redraw();
            sidebar.update();
        };
        editor.getClientInput().getNavigation().addListener(listener);
        sidebar.addDisposeListener(e -> editor.getClientInput().getNavigation().removeListener(listener));

        ImportFromFileDropAdapter.attach(sidebar, editor);

        return scrolledComposite;
    }

    public void select(Item item)
    {
        sidebar.select(item);
    }

    private void setupAllSecuritesAndWatchlistDnD(Navigation.Item item)
    {
        DropTargetAdapter dropTargetListener = new DropTargetAdapter()
        {
            @Override
            public void drop(DropTargetEvent event)
            {
                if (!SecurityTransfer.getTransfer().isSupportedType(event.currentDataType))
                    return;

                List<Security> securities = SecurityTransfer.getTransfer().getSecurities();
                if (securities == null)
                    return;

                List<AttributeType> sourceAttributeTypes = SecurityTransfer.getTransfer().getAttributeTypes();

                boolean isDirty = false;

                for (Security security : securities)
                {
                    // if the security is dragged from another file, add
                    // a deep copy to the client's securities list
                    if (!editor.getClient().getSecurities().contains(security))
                    {
                        Security source = security;
                        security = security.deepCopy();
                        copyAttributes(source, security, sourceAttributeTypes);
                        editor.getClient().addSecurity(security);
                        isDirty = true;
                    }

                    // if drop target is a watchlist, add
                    if (item.getParameter() instanceof Watchlist watchlist
                                    && !watchlist.getSecurities().contains(security))
                    {
                        watchlist.addSecurity(security);
                        isDirty = true;
                    }
                }

                if (isDirty)
                    editor.getClient().touch();
            }
        };

        sidebar.addDropSupport(item, DND.DROP_MOVE, new Transfer[] { SecurityTransfer.getTransfer() },
                        dropTargetListener);
    }

    private void copyAttributes(Security source, Security target, List<AttributeType> sourceAttributeTypes)
    {
        var sourceByName = new HashMap<String, Object>();

        if (sourceAttributeTypes != null)
        {
            for (var attributeType : sourceAttributeTypes)
            {
                if (!attributeType.supports(Security.class))
                    continue;

                Object value = source.getAttributes().get(attributeType);
                if (value != null)
                    sourceByName.put(attributeType.getName(), value);
            }
        }

        editor.getClient().getSettings().getAttributeTypes() //
                        .filter(attributeType -> attributeType.supports(Security.class))
                        .forEach(attributeType -> {
                            Object value = sourceByName.get(attributeType.getName());
                            if (value != null)
                                target.getAttributes().put(attributeType, value);
                        });
    }
}
