package de.omegazirkel.risingworld.marketplace.ui;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import de.omegazirkel.risingworld.Marketplace;
import de.omegazirkel.risingworld.marketplace.InventoryListingCandidate;
import de.omegazirkel.risingworld.marketplace.InventoryTransfer;
import de.omegazirkel.risingworld.marketplace.MarketZone;
import de.omegazirkel.risingworld.marketplace.MarketplaceListing;
import de.omegazirkel.risingworld.marketplace.MarketplaceResult;
import de.omegazirkel.risingworld.marketplace.MarketplaceSale;
import de.omegazirkel.risingworld.marketplace.PluginSettings;
import de.omegazirkel.risingworld.tools.Colors;
import de.omegazirkel.risingworld.tools.I18n;
import de.omegazirkel.risingworld.tools.ui.BasePluginOverlayWithTabs;
import de.omegazirkel.risingworld.tools.ui.ButtonFactory;
import de.omegazirkel.risingworld.tools.ui.InfoButton;
import de.omegazirkel.risingworld.tools.ui.OZUIElement;
import de.omegazirkel.risingworld.tools.ui.table.TableCell;
import de.omegazirkel.risingworld.tools.ui.table.TableRow;
import de.omegazirkel.risingworld.tools.ui.table.TableScrollView;
import net.risingworld.api.objects.Player;
import net.risingworld.api.ui.UILabel;
import net.risingworld.api.ui.UIElement;
import net.risingworld.api.ui.UITextField;
import net.risingworld.api.ui.style.Font;
import net.risingworld.api.ui.style.Pivot;
import net.risingworld.api.ui.style.TextAnchor;
import net.risingworld.api.ui.style.Unit;

public class MarketplaceOverlay extends BasePluginOverlayWithTabs {
    private static final int TABLE_BODY_HEIGHT = 356;

    private enum MarketTab {
        SELL,
        LOCAL,
        GLOBAL,
        SALES
    }

    private final Marketplace plugin;
    private final I18n t;
    private final Colors c = Colors.getInstance();

    private MarketTab marketTab = MarketTab.SELL;
    private InventoryListingCandidate selected;
    private UITextField amountField;
    private UITextField priceField;
    private UITextField currencyField;
    private boolean globalListing;
    private UILabel statusLabel;

    public MarketplaceOverlay(Marketplace plugin, Player player) {
        super(player, p -> p.deleteAttribute("oz.marketplace.ui.overlay"));
        this.plugin = plugin;
        this.t = I18n.getInstance(plugin);
        titleLabelKey = "TC_MARKET_UI_TITLE";
        descLabelKey = "TC_MARKET_UI_SUBTITLE";
        legendLabelKey = "TC_MARKET_UI_LEGEND";
        rebuild();
    }

    @Override
    protected I18n t() {
        return t;
    }

    @Override
    protected void setupTabs() {
        if (marketTab == null || !tabAvailable(marketTab)) {
            marketTab = MarketTab.SELL;
        }
        float tabX = 24;
        OZUIElement sellTab = tab(t().get("TC_MARKET_UI_TAB_SELL", uiPlayer), tabX, 86, 150, () -> switchTab(MarketTab.SELL));
        panel.addChild(sellTab);
        tabX += 150;

        OZUIElement localTab = null;
        OZUIElement globalTab = null;
        OZUIElement salesTab = null;
        PluginSettings settings = plugin.marketplaceSettings();
        if (settings.localMarketplaceEnabled) {
            localTab = tab(t().get("TC_MARKET_UI_TAB_LOCAL", uiPlayer), tabX, 86, 150,
                    () -> switchTab(MarketTab.LOCAL));
            panel.addChild(localTab);
            tabX += 150;
        }
        if (settings.globalMarketplaceEnabled) {
            globalTab = tab(t().get("TC_MARKET_UI_TAB_GLOBAL", uiPlayer), tabX, 86, 150,
                    () -> switchTab(MarketTab.GLOBAL));
            panel.addChild(globalTab);
            tabX += 150;
        }
        salesTab = tab(t().get("TC_MARKET_UI_TAB_SALES", uiPlayer), tabX, 86, 150,
                () -> switchTab(MarketTab.SALES));
        panel.addChild(salesTab);

        styleTab(sellTab, marketTab == MarketTab.SELL);
        styleTab(localTab, marketTab == MarketTab.LOCAL);
        styleTab(globalTab, marketTab == MarketTab.GLOBAL);
        styleTab(salesTab, marketTab == MarketTab.SALES);
        setupActiveTabContent();
    }

    private boolean tabAvailable(MarketTab tab) {
        PluginSettings settings = plugin.marketplaceSettings();
        return switch (tab) {
            case SELL -> true;
            case LOCAL -> settings.localMarketplaceEnabled;
            case GLOBAL -> settings.globalMarketplaceEnabled;
            case SALES -> true;
        };
    }

    private void switchTab(MarketTab tab) {
        marketTab = tab;
        selected = null;
        rebuild();
    }

    private void setupActiveTabContent() {
        body.removeAllChilds();
        if (marketTab == MarketTab.SELL) {
            setupSellTab();
        } else if (marketTab == MarketTab.LOCAL) {
            setupListingsTab(false);
        } else if (marketTab == MarketTab.GLOBAL) {
            setupListingsTab(true);
        } else {
            setupSalesTab();
        }
    }

    private void setupSellTab() {
        setupCandidateTable();
        setupListingForm();
    }

    private void setupCandidateTable() {
        OZUIElement listPanel = new OZUIElement();
        listPanel.setPivot(Pivot.UpperLeft);
        listPanel.setPosition(14, 14, false);
        listPanel.style.width.set(57, Unit.Percent);
        listPanel.style.height.set(392, Unit.Pixel);
        body.addChild(listPanel);

        TableScrollView table = new TableScrollView(
                Arrays.asList(
                        t().get("TC_MARKET_UI_COL_ITEM", uiPlayer),
                        t().get("TC_MARKET_UI_COL_VARIANT", uiPlayer),
                        t().get("TC_MARKET_UI_COL_AVAILABLE", uiPlayer),
                        t().get("TC_MARKET_UI_COL_ACTION", uiPlayer)),
                Arrays.asList(45f, 15f, 18f, 22f));
        table.setScrollBodyHeight(TABLE_BODY_HEIGHT);

        List<InventoryListingCandidate> candidates = InventoryTransfer.listingCandidates(uiPlayer);
        if (candidates.isEmpty()) {
            table.addRow(new TableRow(Arrays.asList(labelCell(t().get("TC_MARKET_UI_EMPTY_INVENTORY", uiPlayer), 100f))));
        } else {
            for (InventoryListingCandidate candidate : candidates) {
                table.addRow(candidateRow(candidate));
            }
        }
        listPanel.addChild(table);
    }

    private TableRow candidateRow(InventoryListingCandidate candidate) {
        return new TableRow(Arrays.asList(
                labelCell(candidate.displayName(), 45f),
                labelCell(String.valueOf(candidate.variant()), 15f),
                labelCell(String.valueOf(candidate.availableAmount()), 18f),
                new TableCell(selectButton(candidate), 22f)));
    }

    private InfoButton selectButton(InventoryListingCandidate candidate) {
        InfoButton button = ButtonFactory.info(t().get("TC_MARKET_UI_SELECT", uiPlayer), event -> {
            selected = candidate;
            rebuild();
            setStatus("");
        });
        button.setPivot(Pivot.UpperLeft);
        button.setPosition(4, 5, false);
        button.setSize(86, 22, false);
        button.setBorderEdgeRadius(3, false);
        return button;
    }

    private void setupListingForm() {
        normalizeListingMode();
        OZUIElement form = new OZUIElement();
        form.setPivot(Pivot.UpperLeft);
        form.setPosition(60, 14, true);
        form.style.width.set(37, Unit.Percent);
        form.style.height.set(392, Unit.Pixel);
        body.addChild(form);

        UILabel formTitle = label(t().get("TC_MARKET_UI_FORM_TITLE", uiPlayer), 17, Font.DefaultBold);
        formTitle.setPivot(Pivot.UpperLeft);
        formTitle.setPosition(0, 0, false);
        formTitle.setSize(100, 26, true);
        form.addChild(formTitle);

        String selectedText = selected == null
                ? t().get("TC_MARKET_UI_NO_SELECTION", uiPlayer)
                : selected.displayName() + " (" + selected.itemName() + ":" + selected.variant() + ")";
        UILabel selectedLabel = label(selectedText, 13, Font.Default);
        selectedLabel.setPivot(Pivot.UpperLeft);
        selectedLabel.setPosition(0, 34, false);
        selectedLabel.setSize(100, 42, true);
        selectedLabel.setTextWrap(true);
        form.addChild(selectedLabel);

        amountField = textField(selected == null ? "" : String.valueOf(Math.min(selected.availableAmount(),
                Math.max(1, selected.maxStackSize()))));
        addField(form, t().get("TC_MARKET_UI_FIELD_AMOUNT", uiPlayer), amountField, 88);

        priceField = textField("");
        addField(form, t().get("TC_MARKET_UI_FIELD_PRICE", uiPlayer), priceField, 142);

        currencyField = textField("");
        addField(form, t().get("TC_MARKET_UI_FIELD_CURRENCY", uiPlayer), currencyField, 196);

        int modeX = 0;
        PluginSettings settings = plugin.marketplaceSettings();
        if (settings.localMarketplaceEnabled) {
            form.addChild(modeButton(false, modeX, 250));
            modeX += 128;
        }
        if (settings.globalMarketplaceEnabled) {
            form.addChild(modeButton(true, modeX, 250));
        }

        InfoButton confirm = ButtonFactory.info(t().get("TC_MARKET_UI_CONFIRM", uiPlayer),
                event -> validateAndConfirmListing());
        confirm.setPivot(Pivot.UpperLeft);
        confirm.setPosition(0, 304, false);
        confirm.setSize(148, 30, false);
        confirm.setBorderEdgeRadius(3, false);
        form.addChild(confirm);

        InfoButton refresh = ButtonFactory.info(t().get("TC_MARKET_UI_REFRESH", uiPlayer), event -> {
            selected = null;
            rebuild();
        });
        refresh.setPivot(Pivot.UpperLeft);
        refresh.setPosition(160, 304, false);
        refresh.setSize(112, 30, false);
        refresh.setBorderEdgeRadius(3, false);
        form.addChild(refresh);

        statusLabel = label("", 13, Font.DefaultBold);
        statusLabel.setPivot(Pivot.UpperLeft);
        statusLabel.setPosition(0, 344, false);
        statusLabel.setSize(100, 42, true);
        statusLabel.setTextWrap(true);
        form.addChild(statusLabel);
    }

    private void normalizeListingMode() {
        PluginSettings settings = plugin.marketplaceSettings();
        if (globalListing && !settings.globalMarketplaceEnabled) {
            globalListing = false;
        }
        if (!globalListing && !settings.localMarketplaceEnabled && settings.globalMarketplaceEnabled) {
            globalListing = true;
        }
    }

    private OZUIElement modeButton(boolean global, float x, float y) {
        OZUIElement button = new OZUIElement();
        button.setPivot(Pivot.UpperLeft);
        button.setPosition(x, y, false);
        button.setSize(120, 30, false);
        button.setBorder(1);
        button.setBorderEdgeRadius(3, false);
        button.setClickable(true);
        button.setClickAction(event -> {
            globalListing = global;
            rebuild();
        });
        styleTab(button, globalListing == global);
        UILabel label = label(t().get(global ? "TC_MARKET_UI_MODE_GLOBAL" : "TC_MARKET_UI_MODE_LOCAL", uiPlayer),
                13, Font.DefaultBold);
        label.setPivot(Pivot.MiddleCenter);
        label.setPosition(50, 50, true);
        label.setSize(100, 100, true);
        label.setTextAlign(TextAnchor.MiddleCenter);
        button.addChild(label);
        return button;
    }

    private void setupListingsTab(boolean global) {
        TableScrollView table = new TableScrollView(
                Arrays.asList(
                        t().get("TC_MARKET_UI_COL_ITEM", uiPlayer),
                        t().get("TC_MARKET_UI_COL_AMOUNT", uiPlayer),
                        t().get("TC_MARKET_UI_COL_PRICE", uiPlayer),
                        t().get("TC_MARKET_UI_COL_SELLER", uiPlayer),
                        t().get("TC_MARKET_UI_COL_ZONE", uiPlayer),
                        t().get("TC_MARKET_UI_COL_ACTION", uiPlayer)),
                Arrays.asList(28f, 10f, 18f, 18f, 14f, 12f));
        table.setScrollBodyHeight(TABLE_BODY_HEIGHT);

        List<MarketplaceListing> listings = visibleListings(global);
        if (listings.isEmpty()) {
            table.addRow(new TableRow(Arrays.asList(labelCell(emptyListingsText(global), 100f))));
        } else {
            for (MarketplaceListing listing : listings) {
                table.addRow(listingRow(listing));
            }
        }
        body.addChild(table);
    }

    private List<MarketplaceListing> visibleListings(boolean global) {
        try {
            return plugin.visibleMarketplaceListings(uiPlayer).stream()
                    .filter(listing -> listing.globalListing() == global)
                    .toList();
        } catch (SQLException ex) {
            Marketplace.logger().error("Failed to render marketplace listings: " + ex.getMessage());
            return List.of();
        }
    }

    private String emptyListingsText(boolean global) {
        if (!global) {
            try {
                if (plugin.currentMarketZone(uiPlayer).isEmpty()) {
                    return t().get("TC_MARKET_UI_EMPTY_LOCAL_NO_ZONE", uiPlayer);
                }
            } catch (SQLException ex) {
                return t().get("TC_MARKET_UI_ERR_ZONE_READ", uiPlayer);
            }
        }
        return t().get(global ? "TC_MARKET_UI_EMPTY_GLOBAL" : "TC_MARKET_UI_EMPTY_LOCAL", uiPlayer);
    }

    private TableRow listingRow(MarketplaceListing listing) {
        return new TableRow(Arrays.asList(
                labelCell(listing.itemName() + ":" + listing.itemVariant(), 28f),
                labelCell(String.valueOf(listing.amount()), 10f),
                labelCell(listing.price() + currencyLabel(listing.currencyIdentifier()), 18f),
                labelCell(listing.sellerName(), 18f),
                labelCell(listing.marketZoneId(), 14f),
                new TableCell(buyButton(listing), 12f)));
    }

    private void setupSalesTab() {
        TableScrollView table = new TableScrollView(
                Arrays.asList(
                        t().get("TC_MARKET_UI_COL_ITEM", uiPlayer),
                        t().get("TC_MARKET_UI_COL_AMOUNT", uiPlayer),
                        t().get("TC_MARKET_UI_COL_PAYOUT", uiPlayer),
                        t().get("TC_MARKET_UI_COL_FEE", uiPlayer),
                        t().get("TC_MARKET_UI_COL_ZONE", uiPlayer),
                        t().get("TC_MARKET_UI_COL_ACTION", uiPlayer)),
                Arrays.asList(30f, 10f, 18f, 14f, 16f, 12f));
        table.setScrollBodyHeight(TABLE_BODY_HEIGHT);

        List<MarketplaceSale> sales = visibleSales();
        if (sales.isEmpty()) {
            table.addRow(new TableRow(Arrays.asList(labelCell(t().get("TC_MARKET_UI_EMPTY_SALES", uiPlayer), 100f))));
        } else {
            for (MarketplaceSale sale : sales) {
                table.addRow(saleRow(sale));
            }
        }
        body.addChild(table);
    }

    private List<MarketplaceSale> visibleSales() {
        try {
            return plugin.marketplaceSales(uiPlayer, 30);
        } catch (SQLException ex) {
            Marketplace.logger().error("Failed to render marketplace sales: " + ex.getMessage());
            return List.of();
        }
    }

    private TableRow saleRow(MarketplaceSale sale) {
        return new TableRow(Arrays.asList(
                labelCell(sale.itemName() + ":" + sale.itemVariant(), 30f),
                labelCell(String.valueOf(sale.amount()), 10f),
                labelCell(sale.sellerPayout() + currencyLabel(sale.currencyIdentifier()), 18f),
                labelCell(String.valueOf(sale.fee()), 14f),
                labelCell(sale.marketZoneId(), 16f),
                new TableCell(removeSaleButton(sale), 12f)));
    }

    private UIElement removeSaleButton(MarketplaceSale sale) {
        InfoButton button = ButtonFactory.info(t().get("TC_MARKET_UI_REMOVE", uiPlayer),
                event -> showRemoveSaleConfirmation(sale));
        button.setPivot(Pivot.UpperLeft);
        button.setPosition(4, 5, false);
        button.setSize(72, 22, false);
        button.setBorderEdgeRadius(3, false);
        return button;
    }

    private UIElement buyButton(MarketplaceListing listing) {
        if (listing.sellerDbId() == uiPlayer.getDbID()) {
            UILabel own = label(t().get("TC_MARKET_UI_OWN_LISTING", uiPlayer), 12, Font.DefaultBold);
            own.setSize(78, 22, false);
            return own;
        }
        InfoButton button = ButtonFactory.info(t().get("TC_MARKET_UI_BUY", uiPlayer),
                event -> showBuyConfirmation(listing));
        button.setPivot(Pivot.UpperLeft);
        button.setPosition(4, 5, false);
        button.setSize(72, 22, false);
        button.setBorderEdgeRadius(3, false);
        return button;
    }

    private void showBuyConfirmation(MarketplaceListing listing) {
        OZUIElement dialog = confirmationDialog(t().get("TC_MARKET_UI_BUY_CONFIRM_TITLE", uiPlayer));
        String details = t().get("TC_MARKET_UI_BUY_CONFIRM_TEXT", uiPlayer)
                .replace("PH_ITEM", listing.itemName() + ":" + listing.itemVariant())
                .replace("PH_AMOUNT", String.valueOf(listing.amount()))
                .replace("PH_PRICE", String.valueOf(listing.price()))
                .replace("PH_CURRENCY", currencyLabel(listing.currencyIdentifier()).trim())
                .replace("PH_MODE", t().get(listing.globalListing() ? "TC_MARKET_UI_MODE_GLOBAL" : "TC_MARKET_UI_MODE_LOCAL",
                        uiPlayer))
                .replace("PH_SELLER", listing.sellerName());
        addDialogMessage(dialog, details);
        addDialogButtons(dialog, t().get("TC_MARKET_UI_CANCEL", uiPlayer), t().get("TC_MARKET_UI_BUY", uiPlayer),
                () -> {
                    MarketplaceResult result = plugin.buyMarketplaceListing(uiPlayer, listing.id());
                    uiPlayer.sendTextMessage((result.success() ? c.okay : c.error) + result.message());
                    rebuild();
                });
    }

    private void showRemoveSaleConfirmation(MarketplaceSale sale) {
        OZUIElement dialog = confirmationDialog(t().get("TC_MARKET_UI_REMOVE_CONFIRM_TITLE", uiPlayer));
        String details = t().get("TC_MARKET_UI_REMOVE_CONFIRM_TEXT", uiPlayer)
                .replace("PH_ITEM", sale.itemName() + ":" + sale.itemVariant())
                .replace("PH_AMOUNT", String.valueOf(sale.amount()))
                .replace("PH_PAYOUT", String.valueOf(sale.sellerPayout()))
                .replace("PH_CURRENCY", currencyLabel(sale.currencyIdentifier()).trim());
        addDialogMessage(dialog, details);
        addDialogButtons(dialog, t().get("TC_MARKET_UI_CANCEL", uiPlayer), t().get("TC_MARKET_UI_REMOVE", uiPlayer),
                () -> {
                    MarketplaceResult result = plugin.hideMarketplaceSale(uiPlayer, sale.id());
                    uiPlayer.sendTextMessage((result.success() ? c.okay : c.error) + result.message());
                    rebuild();
                });
    }

    private void validateAndConfirmListing() {
        if (selected == null) {
            setStatus(t().get("TC_MARKET_UI_ERR_SELECT_ITEM", uiPlayer));
            return;
        }
        amountField.getCurrentText(uiPlayer, amountText -> priceField.getCurrentText(uiPlayer,
                priceText -> currencyField.getCurrentText(uiPlayer,
                        currencyText -> validateAndConfirmListing(amountText, priceText, currencyText))));
    }

    private void validateAndConfirmListing(String amountText, String priceText, String currencyText) {
        int amount = parseInt(amountText);
        if (amount <= 0) {
            setStatus(t().get("TC_MARKET_UI_ERR_AMOUNT_POSITIVE", uiPlayer));
            return;
        }
        if (amount > selected.availableAmount()) {
            setStatus(t().get("TC_MARKET_UI_ERR_AMOUNT_AVAILABLE", uiPlayer)
                    .replace("PH_AVAILABLE", String.valueOf(selected.availableAmount())));
            return;
        }
        long price = parseLong(priceText);
        if (price <= 0) {
            setStatus(t().get("TC_MARKET_UI_ERR_PRICE_POSITIVE", uiPlayer));
            return;
        }
        Optional<MarketZone> zone;
        try {
            zone = plugin.currentMarketZone(uiPlayer);
        } catch (SQLException ex) {
            Marketplace.logger().error("Failed to validate market zone for listing UI: " + ex.getMessage());
            setStatus(t().get("TC_MARKET_UI_ERR_ZONE_READ", uiPlayer));
            return;
        }
        if (zone.isEmpty()) {
            setStatus(t().get("TC_MARKET_UI_ERR_MARKET_ZONE", uiPlayer));
            return;
        }
        showListingConfirmation(amount, price, currencyText == null ? "" : currencyText.trim(), zone.get());
    }

    private void showListingConfirmation(int amount, long price, String currency, MarketZone zone) {
        OZUIElement dialog = confirmationDialog(t().get("TC_MARKET_UI_CONFIRM_TITLE", uiPlayer));
        String currencyLabel = currency.isBlank() ? t().get("TC_MARKET_UI_DEFAULT_CURRENCY", uiPlayer) : currency;
        String details = t().get("TC_MARKET_UI_CONFIRM_TEXT", uiPlayer)
                .replace("PH_ITEM", selected.displayName() + " (" + selected.itemName() + ":" + selected.variant() + ")")
                .replace("PH_AMOUNT", String.valueOf(amount))
                .replace("PH_PRICE", String.valueOf(price))
                .replace("PH_CURRENCY", currencyLabel)
                .replace("PH_MODE", t().get(globalListing ? "TC_MARKET_UI_MODE_GLOBAL" : "TC_MARKET_UI_MODE_LOCAL", uiPlayer))
                .replace("PH_ZONE", zone.name() + " (" + zone.id() + ")");
        addDialogMessage(dialog, details);
        addDialogButtons(dialog, t().get("TC_MARKET_UI_CANCEL", uiPlayer), t().get("TC_MARKET_UI_CREATE", uiPlayer),
                () -> {
                    MarketplaceResult result = plugin.createMarketplaceListing(uiPlayer, selected.itemName(), selected.variant(),
                            amount, price, currency, globalListing);
                    uiPlayer.sendTextMessage((result.success() ? c.okay : c.error) + result.message());
                    setStatus(result.message());
                    if (result.success()) {
                        selected = null;
                        rebuild();
                    }
                });
    }

    private OZUIElement confirmationDialog(String titleText) {
        OZUIElement dialog = new OZUIElement();
        dialog.setPivot(Pivot.MiddleCenter);
        dialog.setPosition(50, 50, true);
        dialog.setSize(430, 250, false);
        dialog.setBackgroundColor(0, 0, 0, 0.94f);
        dialog.setBorder(1);
        dialog.setBorderColor(0.95f, 0.75f, 0.25f, 0.72f);
        dialog.setBorderEdgeRadius(6, false);
        addChild(dialog);

        UILabel title = label(titleText, 18, Font.DefaultBold);
        title.setPivot(Pivot.UpperLeft);
        title.setPosition(20, 16, false);
        title.setSize(390, 30, false);
        dialog.addChild(title);
        return dialog;
    }

    private void addDialogMessage(OZUIElement dialog, String details) {
        UILabel message = label(details, 13, Font.Default);
        message.setPivot(Pivot.UpperLeft);
        message.setPosition(20, 54, false);
        message.setSize(390, 110, false);
        message.setTextWrap(true);
        dialog.addChild(message);
    }

    private void addDialogButtons(OZUIElement dialog, String cancelText, String confirmText, Runnable onConfirm) {
        OZUIElement cancel = ButtonFactory.cancel(cancelText, event -> removeChild(dialog));
        cancel.setPivot(Pivot.UpperLeft);
        cancel.setPosition(112, 184, false);
        cancel.setSize(96, 32, false);
        cancel.setBorderEdgeRadius(3, false);
        dialog.addChild(cancel);

        OZUIElement confirm = ButtonFactory.ok(confirmText, event -> {
            removeChild(dialog);
            onConfirm.run();
        });
        confirm.setPivot(Pivot.UpperLeft);
        confirm.setPosition(222, 184, false);
        confirm.setSize(120, 32, false);
        confirm.setBorderEdgeRadius(3, false);
        dialog.addChild(confirm);
    }

    private void addField(OZUIElement form, String labelText, UITextField field, int y) {
        UILabel fieldLabel = label(labelText, 12, Font.DefaultBold);
        fieldLabel.setPivot(Pivot.UpperLeft);
        fieldLabel.setPosition(0, y, false);
        fieldLabel.setSize(108, 24, false);
        form.addChild(fieldLabel);

        field.setPivot(Pivot.UpperLeft);
        field.setPosition(112, y, false);
        field.setSize(160, 30, false);
        form.addChild(field);
    }

    private UITextField textField(String value) {
        UITextField field = new UITextField(value == null ? "" : value);
        field.setReadOnly(false);
        field.setBackgroundColor(0.02f, 0.02f, 0.02f, 0.78f);
        field.setBorder(1);
        field.setBorderColor(0.95f, 0.75f, 0.25f, 0.46f);
        field.setBorderEdgeRadius(4, false);
        field.setFontSize(13);
        return field;
    }

    private TableCell labelCell(String text, float width) {
        UILabel label = label(text == null ? "" : text, 13, Font.Default);
        label.setTextWrap(false);
        return new TableCell(label, width);
    }

    private UILabel label(String text, int fontSize, Font font) {
        UILabel label = new UILabel(text == null ? "" : text);
        label.setFont(font);
        label.setFontSize(fontSize);
        label.setTextAlign(TextAnchor.MiddleLeft);
        return label;
    }

    private void setStatus(String text) {
        if (statusLabel != null) {
            statusLabel.setText(text == null ? "" : text);
        }
    }

    private String currencyLabel(String currency) {
        return currency == null || currency.isBlank()
                ? " " + t().get("TC_MARKET_UI_DEFAULT_CURRENCY", uiPlayer)
                : " " + currency;
    }

    private int parseInt(String value) {
        try {
            return Integer.parseInt(value == null ? "" : value.trim());
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private long parseLong(String value) {
        try {
            return Long.parseLong(value == null ? "" : value.trim());
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }
}
