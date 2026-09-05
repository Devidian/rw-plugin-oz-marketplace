package de.omegazirkel.risingworld.marketplace.ui;

import java.text.NumberFormat;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import de.omegazirkel.risingworld.Marketplace;
import de.omegazirkel.risingworld.marketplace.InventoryListingCandidate;
import de.omegazirkel.risingworld.marketplace.InventoryTransfer;
import de.omegazirkel.risingworld.marketplace.MarketZone;
import de.omegazirkel.risingworld.marketplace.MarketCrier;
import de.omegazirkel.risingworld.marketplace.MarketplaceItemNames;
import de.omegazirkel.risingworld.marketplace.MarketplaceItemState;
import de.omegazirkel.risingworld.marketplace.MarketplacePlayerPreferences;
import de.omegazirkel.risingworld.marketplace.MarketplaceListing;
import de.omegazirkel.risingworld.marketplace.MarketplaceResult;
import de.omegazirkel.risingworld.marketplace.MarketplaceSale;
import de.omegazirkel.risingworld.marketplace.MarketplaceService;
import de.omegazirkel.risingworld.tools.PlayerDatabaseHelper;
import de.omegazirkel.risingworld.marketplace.PluginSettings;
import de.omegazirkel.risingworld.marketplace.WalletBridge;
import de.omegazirkel.risingworld.tools.Colors;
import de.omegazirkel.risingworld.tools.I18n;
import de.omegazirkel.risingworld.tools.ui.AssetManager;
import de.omegazirkel.risingworld.tools.ui.BasePluginOverlayWithTabs;
import de.omegazirkel.risingworld.tools.ui.AdvancedButtonFactory;
import de.omegazirkel.risingworld.tools.ui.Dropdown;
import de.omegazirkel.risingworld.tools.ui.DropdownOption;
import de.omegazirkel.risingworld.tools.ui.AdvancedButton;
import de.omegazirkel.risingworld.tools.ui.OZUIElement;
import de.omegazirkel.risingworld.tools.ui.table.TableCell;
import de.omegazirkel.risingworld.tools.ui.table.TableRow;
import de.omegazirkel.risingworld.tools.ui.table.TableScrollView;
import net.risingworld.api.assets.TextureAsset;
import net.risingworld.api.definitions.Clothing.ClothingDefinition;
import net.risingworld.api.definitions.Constructions.ConstructionDefinition;
import net.risingworld.api.definitions.Definitions;
import net.risingworld.api.definitions.Items.ItemDefinition;
import net.risingworld.api.definitions.Objects.ObjectDefinition;
import net.risingworld.api.objects.Player;
import net.risingworld.api.ui.UILabel;
import net.risingworld.api.ui.UIElement;
import net.risingworld.api.ui.UIScrollView;
import net.risingworld.api.ui.UIScrollView.ScrollViewMode;
import net.risingworld.api.ui.UITextField;
import net.risingworld.api.ui.style.Align;
import net.risingworld.api.ui.style.DisplayStyle;
import net.risingworld.api.ui.style.FlexDirection;
import net.risingworld.api.ui.style.Font;
import net.risingworld.api.ui.style.Justify;
import net.risingworld.api.ui.style.Pivot;
import net.risingworld.api.ui.style.Position;
import net.risingworld.api.ui.style.ScaleMode;
import net.risingworld.api.ui.style.TextAnchor;
import net.risingworld.api.ui.style.Unit;
import net.risingworld.api.ui.style.Wrap;

public class MarketplaceOverlay extends BasePluginOverlayWithTabs {
    private static final int TABLE_BODY_HEIGHT = 356;

    private enum MarketTab {
        SELL,
        LOCAL,
        GLOBAL,
        WANTED,
        SALES,
        MANAGEMENT
    }

    private final Marketplace plugin;
    private final I18n t;
    private final Colors c = Colors.getInstance();

    private MarketTab marketTab = MarketTab.SELL;
    private InventoryListingCandidate selected;
    private OZUIElement selectedCandidateCard;
    private final IdentityHashMap<OZUIElement, InventoryListingCandidate> candidateCards = new IdentityHashMap<>();
    private OZUIElement listingForm;
    private UITextField amountField;
    private UITextField priceField;
    private UITextField managementFeeField;
    private UITextField managementCrierNameField;
    private UITextField managementCrierAccountAmountField;
    private UITextField managementTransformStepField;
    private UITextField managementRotationStepField;
    private String managementTransformStepDraft = "1.00";
    private String managementRotationStepDraft = "15";
    private UITextField listingTradeQuantityField;
    private UILabel listingTradeUnitPriceLabel;
    private MarketplaceListing listingTradePreviewListing;
    private String selectedCurrency = "";
    private String listingFilter = "";
    private boolean globalListing;
    private UILabel statusLabel;
    private String listingAmountDraft;
    private String listingPriceDraft;

    public MarketplaceOverlay(Marketplace plugin, Player player) {
        super(player, p -> {
            p.deleteAttribute("oz.marketplace.ui.overlay");
            p.deleteAttribute("oz.marketplace.crier.endpoint");
        });
        this.plugin = plugin;
        this.t = plugin.i18n();
        titleLabelKey = "tc.market.ui.title";
        descLabelKey = "tc.market.ui.subtitle";
        legendLabelKey = "tc.market.ui.legend";
        rebuild();
    }

    @Override
    protected I18n t() {
        return t;
    }

    @Override
    protected String titleText() {
        MarketCrier crier = currentMarketCrier();
        if (crier == null) {
            return super.titleText();
        }
        return t().get("tc.market.ui.crier.title", uiPlayer).replace("PH_NAME", crier.name());
    }

    @Override
    protected String descriptionText() {
        MarketCrier crier = currentMarketCrier();
        if (crier == null) {
            return super.descriptionText();
        }
        String key = crier.global()
                ? "tc.market.ui.crier.identity.global"
                : "tc.market.ui.crier.identity.personal";
        return t().get(key, uiPlayer).replace("PH_OWNER", crier.ownerName());
    }

    @Override
    protected String legendText() {
        PluginSettings settings = plugin.marketplaceSettings();
        if (!settings.globalMarketplaceEnabled) {
            return t().get("tc.market.ui.status.global.disabled", uiPlayer);
        }
        if (settings.marketZoneOnlyMode) {
            return t().get("tc.market.ui.status.zone.only", uiPlayer);
        }
        return t().get("tc.market.ui.status.global.enabled", uiPlayer);
    }

    private MarketCrier currentMarketCrier() {
        Object endpoint = uiPlayer.getAttribute("oz.marketplace.crier.endpoint");
        return endpoint instanceof MarketCrier crier ? crier : null;
    }

    @Override
    protected void setupTabs() {
        if (marketTab == null || !tabAvailable(marketTab)) {
            marketTab = MarketTab.SELL;
            if (!tabAvailable(marketTab)) {
                marketTab = MarketTab.SALES;
            }
        }
        setupTabContainer();
        setupWalletBalanceBar();
        if (tabAvailable(MarketTab.SELL)) {
            addTab(t().get("tc.market.ui.tab.sell", uiPlayer), 132, marketTab == MarketTab.SELL,
                    () -> switchTab(MarketTab.SELL));
        }
        if (tabAvailable(MarketTab.LOCAL)) {
            addTab(t().get("tc.market.ui.tab.local", uiPlayer), 132, marketTab == MarketTab.LOCAL,
                    () -> switchTab(MarketTab.LOCAL));
        }
        if (tabAvailable(MarketTab.GLOBAL)) {
            addTab(t().get("tc.market.ui.tab.global", uiPlayer), 132, marketTab == MarketTab.GLOBAL,
                    () -> switchTab(MarketTab.GLOBAL));
        }
        addTab(t().get("tc.market.ui.tab.wanted", uiPlayer), 132, marketTab == MarketTab.WANTED,
                () -> switchTab(MarketTab.WANTED));
        addTab(t().get("tc.market.ui.tab.sales", uiPlayer), 132, marketTab == MarketTab.SALES,
                () -> switchTab(MarketTab.SALES));
        if (tabAvailable(MarketTab.MANAGEMENT)) {
            addTab(t().get("tc.market.ui.tab.management", uiPlayer), 170, marketTab == MarketTab.MANAGEMENT, true,
                    () -> switchTab(MarketTab.MANAGEMENT));
        }
        setupActiveTabContent();
    }

    private boolean tabAvailable(MarketTab tab) {
        PluginSettings settings = plugin.marketplaceSettings();
        return switch (tab) {
            case SELL -> true;
            case LOCAL -> settings.localMarketplaceEnabled && plugin.safeCurrentMarketZone(uiPlayer).isPresent();
            case GLOBAL -> plugin.globalListingAllowed(uiPlayer);
            case WANTED -> true;
            case SALES -> true;
            case MANAGEMENT -> plugin.marketplaceManagementAvailable(uiPlayer);
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
        } else if (marketTab == MarketTab.WANTED) {
            setupWantedTab();
        } else if (marketTab == MarketTab.SALES) {
            setupSalesTab();
        } else {
            setupManagementTab();
        }
    }

    private void setupWalletBalanceBar() {
        List<WalletBridge.CurrencyInfo> currencies = plugin.walletCurrencies();
        if (currencies.isEmpty()) {
            return;
        }
        OZUIElement bar = new OZUIElement();
        bar.setPivot(Pivot.UpperLeft);
        bar.setPosition(0, -34, false);
        bar.style.width.set(100, Unit.Percent);
        bar.style.height.set(28, Unit.Pixel);
        bar.style.display.set(DisplayStyle.Flex);
        bar.style.flexDirection.set(FlexDirection.Row);
        bar.style.flexWrap.set(Wrap.NoWrap);
        bar.style.alignItems.set(Align.Center);
        bar.style.justifyContent.set(Justify.FlexStart);
        bar.setBackgroundColor(0, 0, 0, 0);

        boolean added = false;
        for (WalletBridge.CurrencyInfo currency : currencies) {
            WalletBridge.BalanceInfo balance = plugin.walletBalance(uiPlayer, currency.identifier());
            if (!balance.success()) {
                continue;
            }
            bar.addChild(walletBalanceEntry(currency, balance.balance()));
            added = true;
        }
        if (added) {
            panel.addChild(bar);
        }
    }

    private OZUIElement walletBalanceEntry(WalletBridge.CurrencyInfo currency, long balance) {
        OZUIElement entry = new OZUIElement();
        entry.setPivot(Pivot.UpperLeft);
        entry.style.width.set(104, Unit.Pixel);
        entry.style.height.set(24, Unit.Pixel);
        entry.style.marginLeft.set(8);
        entry.style.marginTop.set(2);
        entry.setBackgroundColor(0.08f, 0.08f, 0.08f, 0.55f);
        entry.setBorder(1);
        entry.setBorderColor(0.95f, 0.75f, 0.25f, 0.48f);
        entry.setBorderEdgeRadius(4, false);

        TextureAsset iconAsset = currency.iconKey().isBlank() ? AssetManager.getIcon(uiPlayer, "coin-default")
                : AssetManager.getIcon(uiPlayer, currency.iconKey());
        OZUIElement icon = new OZUIElement();
        icon.setPivot(Pivot.UpperLeft);
        icon.setPosition(6, 4, false);
        icon.setSize(16, 16, false);
        icon.setBackgroundColor(0, 0, 0, 0);
        if (iconAsset != null) {
            icon.style.backgroundImage.set(iconAsset);
            icon.style.backgroundImageScaleMode.set(ScaleMode.ScaleToFit);
        }
        entry.addChild(icon);

        UILabel amount = label(String.valueOf(balance), 12, Font.DefaultBold);
        amount.setPivot(Pivot.UpperLeft);
        amount.setPosition(28, 3, false);
        amount.setSize(70, 18, false);
        amount.setFontColor(0xF2C766FF);
        amount.setTextAlign(TextAnchor.MiddleLeft);
        entry.addChild(amount);
        return entry;
    }

    private void setupSellTab() {
        if (!plugin.sellingAllowed(uiPlayer)) {
            addMessage(sellUnavailableMessage(), 18);
            return;
        }
        setupCandidateTable();
        refreshListingForm();
    }

    private void setupCandidateTable() {
        candidateCards.clear();
        selectedCandidateCard = null;
        OZUIElement listPanel = new OZUIElement();
        listPanel.setPivot(Pivot.UpperLeft);
        listPanel.setPosition(14, 14, false);
        listPanel.style.width.set(61, Unit.Percent);
        listPanel.style.height.set(416, Unit.Pixel);
        body.addChild(listPanel);

        List<InventoryListingCandidate> candidates = InventoryTransfer.listingCandidates(uiPlayer);
        if (candidates.isEmpty()) {
            UILabel empty = label(t().get("tc.market.ui.empty.inventory", uiPlayer), 15, Font.Default);
            empty.setPivot(Pivot.UpperLeft);
            empty.setPosition(12, 12, false);
            empty.setSize(90, 40, true);
            empty.setTextWrap(true);
            listPanel.addChild(empty);
        } else {
            UIScrollView scroll = flexScroll(416);
            OZUIElement wrapper = flexWrapper();
            for (InventoryListingCandidate candidate : candidates) {
                wrapper.addChild(candidateCard(candidate));
            }
            scroll.addChild(wrapper);
            listPanel.addChild(scroll);
        }
    }

    private TableRow candidateRow(InventoryListingCandidate candidate) {
        return new TableRow(Arrays.asList(
                labelCell(candidate.displayName() + conditionSuffix(candidate.itemName(), candidate.itemState()), 45f),
                labelCell(String.valueOf(candidate.variant()), 15f),
                labelCell(String.valueOf(candidate.availableAmount()), 18f),
                new TableCell(selectButton(candidate), 22f)));
    }

    private AdvancedButton selectButton(InventoryListingCandidate candidate) {
        AdvancedButton button = AdvancedButtonFactory.defaultButton(t().get("tc.market.ui.select", uiPlayer), event -> {
            selected = candidate;
            listingAmountDraft = null;
            listingPriceDraft = null;
            refreshListingForm();
            setStatus("");
        });
        button.setPivot(Pivot.UpperLeft);
        button.setPosition(4, 5, false);
        button.setSize(86, 22, false);
        button.setBorderEdgeRadius(3, false);
        return button;
    }

    private OZUIElement candidateCard(InventoryListingCandidate candidate) {
        OZUIElement placeholder = new OZUIElement();
        placeholder.setPivot(Pivot.UpperLeft);
        placeholder.style.width.set(174, Unit.Pixel);
        placeholder.style.height.set(92, Unit.Pixel);
        placeholder.style.marginLeft.set(6);
        placeholder.style.marginRight.set(6);
        placeholder.style.marginTop.set(6);
        placeholder.style.marginBottom.set(10);
        placeholder.setBackgroundColor(0, 0, 0, 0);
        placeholder.setClickable(true);
        candidateCards.put(placeholder, candidate);
        placeholder.setClickAction(event -> {
            if (selectedCandidateCard != null && selectedCandidateCard != placeholder) {
                refreshCandidateCard(selectedCandidateCard, false);
            }
            selected = candidate;
            selectedCandidateCard = placeholder;
            listingAmountDraft = null;
            listingPriceDraft = null;
            refreshCandidateCard(placeholder, true);
            refreshListingForm();
            setStatus("");
        });

        if (candidate.equals(selected)) {
            selectedCandidateCard = placeholder;
        }
        refreshCandidateCard(placeholder, candidate.equals(selected));
        return placeholder;
    }

    private void refreshCandidateCard(OZUIElement placeholder, boolean active) {
        InventoryListingCandidate candidate = candidateCards.get(placeholder);
        if (candidate == null) return;
        placeholder.removeAllChilds();
        OZUIElement card = smallCard(174, 92);
        card.setClickable(false);
        card.setHoverBackgroundColor(0x00000000);
        card.style.width.set(100, Unit.Percent);
        card.style.height.set(100, Unit.Percent);
        card.style.marginLeft.set(0);
        card.style.marginRight.set(0);
        card.style.marginTop.set(0);
        card.style.marginBottom.set(0);
        applyCandidateCardStyle(card, active);
        card.addChild(cardIcon(itemIcon(candidate.itemName(), candidate.variant()), 10, 10, 32));

        UILabel name = label(candidate.displayName(), 13, Font.DefaultBold);
        name.setPivot(Pivot.UpperLeft);
        name.setPosition(50, 8, false);
        name.setSize(114, 30, false);
        name.setTextWrap(true);
        name.setTextAlign(TextAnchor.UpperLeft);
        card.addChild(name);

        UILabel amount = label(t().get("tc.market.ui.card.amount", uiPlayer)
                .replace("PH_AMOUNT", String.valueOf(candidate.availableAmount()))
                + conditionSuffix(candidate.itemName(), candidate.itemState()), 12, Font.Default);
        amount.setPivot(Pivot.UpperLeft);
        amount.setPosition(10, 58, false);
        amount.setSize(154, 20, false);
        card.addChild(amount);
        placeholder.addChild(card);
    }

    private void applyCandidateCardStyle(OZUIElement card, boolean active) {
        card.setBackgroundColor(active ? 0.18f : 0.10f, active ? 0.15f : 0.09f, active ? 0.10f : 0.08f,
                active ? 0.98f : 0.92f);
        card.setBorder(active ? 2 : 1);
        card.setBorderColor(0.95f, 0.75f, 0.25f, active ? 0.9f : 0.42f);
    }

    private void refreshListingForm() {
        if (listingForm != null) {
            body.removeChild(listingForm);
        }
        setupListingForm();
    }

    private void setupListingForm() {
        normalizeListingMode();
        OZUIElement form = new OZUIElement();
        listingForm = form;
        form.setPivot(Pivot.UpperLeft);
        form.setPosition(65, 14, true);
        form.style.width.set(32, Unit.Percent);
        form.style.height.set(430, Unit.Pixel);
        body.addChild(form);

        UILabel formTitle = label(t().get("tc.market.ui.form.title", uiPlayer), 17, Font.DefaultBold);
        formTitle.setPivot(Pivot.UpperLeft);
        formTitle.setPosition(0, 0, false);
        formTitle.setSize(100, 26, true);
        form.addChild(formTitle);

        String selectedText = selected == null
                ? t().get("tc.market.ui.no.selection", uiPlayer)
                : selected.displayName() + " (" + selected.itemName() + ":" + selected.variant() + ")";
        UILabel selectedLabel = label(selectedText, 13, Font.Default);
        selectedLabel.setPivot(Pivot.UpperLeft);
        selectedLabel.setPosition(0, 30, false);
        selectedLabel.setSize(100, 28, true);
        selectedLabel.setTextWrap(true);
        form.addChild(selectedLabel);

        UILabel condition = label(selected == null ? "" : conditionLabel(selected.itemName(), selected.itemState()), 12,
                Font.DefaultBold);
        condition.setPivot(Pivot.UpperLeft);
        condition.setPosition(0, 60, false);
        condition.setSize(100, 20, true);
        form.addChild(condition);

        if (selected != null && isDamaged(selected.itemName(), selected.itemState())) {
            UILabel warning = label(t().get("tc.market.ui.durability.warning", uiPlayer)
                    .replace("PH_PERCENT", String.valueOf(durabilityPercent(selected.itemName(), selected.itemState()))),
                    12, Font.DefaultBold);
            warning.setPivot(Pivot.UpperLeft);
            warning.setPosition(0, 78, false);
            warning.setSize(100, 24, true);
            warning.setTextWrap(true);
            warning.setFontColor(0xE6A04CFF);
            form.addChild(warning);
        }

        String defaultAmount = selected == null ? "" : String.valueOf(Math.min(selected.availableAmount(),
                Math.max(1, selected.maxStackSize())));
        amountField = textField(listingAmountDraft == null ? defaultAmount : listingAmountDraft);
        addField(form, t().get("tc.market.ui.field.amount", uiPlayer), amountField, 106);

        priceField = textField(listingPriceDraft == null ? "" : listingPriceDraft);
        addField(form, t().get("tc.market.ui.field.price", uiPlayer), priceField, 160);

        Dropdown currencyDropdown = currencyDropdown();
        addField(form, t().get("tc.market.ui.field.currency", uiPlayer), currencyDropdown, 214);

        int modeX = 0;
        if (plugin.localListingAllowed(uiPlayer)) {
            form.addChild(modeButton(false, modeX, 268));
            modeX += 128;
        }
        if (plugin.globalListingAllowed(uiPlayer)) {
            form.addChild(modeButton(true, modeX, 268));
        }

        AdvancedButton confirm = AdvancedButtonFactory.defaultButton(t().get("tc.market.ui.confirm", uiPlayer),
                event -> validateAndConfirmListing());
        confirm.setPivot(Pivot.UpperLeft);
        confirm.setPosition(0, 316, false);
        confirm.setSize(148, 30, false);
        confirm.setBorderEdgeRadius(3, false);
        form.addChild(confirm);

        AdvancedButton refresh = AdvancedButtonFactory.defaultButton(t().get("tc.market.ui.refresh", uiPlayer), event -> {
            selected = null;
            rebuild();
        });
        refresh.setPivot(Pivot.UpperLeft);
        refresh.setPosition(160, 316, false);
        refresh.setSize(112, 30, false);
        refresh.setBorderEdgeRadius(3, false);
        form.addChild(refresh);

        statusLabel = label("", 13, Font.DefaultBold);
        statusLabel.setPivot(Pivot.UpperLeft);
        statusLabel.setPosition(0, 356, false);
        statusLabel.setSize(100, 42, true);
        statusLabel.setTextWrap(true);
        form.addChild(statusLabel);
    }

    private void normalizeListingMode() {
        boolean localAllowed = plugin.localListingAllowed(uiPlayer);
        boolean globalAllowed = plugin.globalListingAllowed(uiPlayer);
        if (globalListing && !globalAllowed) {
            globalListing = false;
        }
        if (!globalListing && !localAllowed && globalAllowed) {
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
            amountField.getCurrentText(uiPlayer, amount -> priceField.getCurrentText(uiPlayer, price -> {
                listingAmountDraft = amount == null ? "" : amount;
                listingPriceDraft = price == null ? "" : price;
                globalListing = global;
                refreshListingForm();
            }));
        });
        styleTab(button, globalListing == global);
        UILabel label = label(t().get(global ? "tc.market.ui.mode.global" : "tc.market.ui.mode.local", uiPlayer),
                13, Font.DefaultBold);
        label.setPivot(Pivot.MiddleCenter);
        label.setPosition(50, 50, true);
        label.setSize(100, 100, true);
        label.setTextAlign(TextAnchor.MiddleCenter);
        button.addChild(label);
        return button;
    }

    private void setupListingsTab(boolean global) {
        addListingsLayoutToggle();
        setupListingSearch();
        if (MarketplacePlayerPreferences.LAYOUT_CARD.equals(MarketplacePlayerPreferences.listingLayout(uiPlayer))) {
            setupListingCards(global);
            return;
        }
        TableScrollView table = new TableScrollView(
                Arrays.asList(
                        t().get("tc.market.ui.col.item", uiPlayer),
                        t().get("tc.market.ui.col.amount", uiPlayer),
                        t().get("tc.market.ui.col.condition", uiPlayer),
                        t().get("tc.market.ui.col.price", uiPlayer),
                        t().get("tc.market.ui.col.seller", uiPlayer),
                        t().get("tc.market.ui.col.zone", uiPlayer),
                        t().get("tc.market.ui.col.action", uiPlayer)),
                Arrays.asList(21f, 9f, 12f, 16f, 15f, 12f, 15f));
        table.setPosition(0, 42, false);
        table.setScrollBodyHeight(TABLE_BODY_HEIGHT - 42);

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

    private void setupListingCards(boolean global) {
        List<MarketplaceListing> listings = visibleListings(global);
        UIScrollView scroll = flexScroll(TABLE_BODY_HEIGHT - 42);
        scroll.setPosition(0, 42, false);
        OZUIElement wrapper = flexWrapper();
        if (listings.isEmpty()) {
            UILabel empty = label(emptyListingsText(global), 15, Font.Default);
            empty.setPivot(Pivot.UpperLeft);
            empty.setPosition(12, 12, false);
            empty.setSize(90, 40, true);
            empty.setTextWrap(true);
            wrapper.addChild(empty);
        } else {
            for (MarketplaceListing listing : listings) {
                wrapper.addChild(listingCard(listing));
            }
        }
        scroll.addChild(wrapper);
        body.addChild(scroll);
    }

    private void addListingsLayoutToggle() {
        boolean table = MarketplacePlayerPreferences.LAYOUT_TABLE.equals(MarketplacePlayerPreferences.listingLayout(uiPlayer));
        OZUIElement cards = layoutButton(t().get("tc.market.ui.layout.cards", uiPlayer), 14, table ? false : true,
                () -> {
                    MarketplacePlayerPreferences.setListingLayout(uiPlayer, MarketplacePlayerPreferences.LAYOUT_CARD);
                    rebuild();
                });
        body.addChild(cards);
        OZUIElement tableButton = layoutButton(t().get("tc.market.ui.layout.table", uiPlayer), 140, table,
                () -> {
                    MarketplacePlayerPreferences.setListingLayout(uiPlayer, MarketplacePlayerPreferences.LAYOUT_TABLE);
                    rebuild();
                });
        body.addChild(tableButton);
    }

    private void setupListingSearch() {
        UILabel label = label(t().get("tc.market.ui.search", uiPlayer), 13, Font.Default);
        label.setPivot(Pivot.UpperLeft);
        label.setPosition(274, 9, false);
        label.setSize(56, 26, false);
        body.addChild(label);

        UITextField searchField = textField(listingFilter);
        searchField.setPivot(Pivot.UpperLeft);
        searchField.setPosition(334, 8, false);
        searchField.setSize(194, 28, false);
        searchField.setMaxCharacters(80);
        body.addChild(searchField);

        AdvancedButton apply = AdvancedButtonFactory.defaultButton(t().get("tc.market.ui.search.apply", uiPlayer), event -> {
            searchField.getCurrentText(uiPlayer, text -> {
                listingFilter = text == null ? "" : text.trim();
                rebuild();
            });
        });
        apply.setPivot(Pivot.UpperLeft);
        apply.setPosition(540, 8, false);
        apply.setSize(86, 28, false);
        body.addChild(apply);

        AdvancedButton clear = AdvancedButtonFactory.defaultButton(t().get("tc.market.ui.search.clear", uiPlayer), event -> {
            listingFilter = "";
            rebuild();
        });
        clear.setPivot(Pivot.UpperLeft);
        clear.setPosition(638, 8, false);
        clear.setSize(112, 28, false);
        body.addChild(clear);
    }

    private OZUIElement layoutButton(String text, int x, boolean active, Runnable action) {
        OZUIElement button = new OZUIElement();
        button.setPivot(Pivot.UpperLeft);
        button.setPosition(x, 8, false);
        button.setSize(116, 28, false);
        button.setBorder(1);
        button.setBorderEdgeRadius(3, false);
        button.setClickable(true);
        button.setClickAction(event -> action.run());
        styleTab(button, active);
        UILabel label = label(text, 12, Font.DefaultBold);
        label.setPivot(Pivot.MiddleCenter);
        label.setPosition(50, 50, true);
        label.setSize(100, 100, true);
        label.setTextAlign(TextAnchor.MiddleCenter);
        button.addChild(label);
        return button;
    }

    private List<MarketplaceListing> visibleListings(boolean global) {
        try {
            return unfilteredVisibleListings(global).stream()
                    .filter(this::matchesListingFilter)
                    .toList();
        } catch (SQLException ex) {
            Marketplace.logger().error("Failed to render marketplace listings: " + ex.getMessage());
            return List.of();
        }
    }

    private List<MarketplaceListing> unfilteredVisibleListings(boolean global) throws SQLException {
        return plugin.visibleMarketplaceListings(uiPlayer).stream()
                .filter(listing -> listing.globalListing() == global)
                .filter(MarketplaceListing::offer)
                .toList();
    }

    private boolean matchesListingFilter(MarketplaceListing listing) {
        String filter = normalizedListingFilter();
        return filter.isBlank() || listingLabel(listing).toLowerCase(Locale.ROOT).contains(filter);
    }

    private String normalizedListingFilter() {
        return listingFilter == null ? "" : listingFilter.trim().toLowerCase(Locale.ROOT);
    }

    private String emptyListingsText(boolean global) {
        if (!normalizedListingFilter().isBlank()) {
            try {
                if (!unfilteredVisibleListings(global).isEmpty()) {
                    return t().get("tc.market.ui.empty.filter", uiPlayer);
                }
            } catch (SQLException ex) {
                return t().get("tc.market.ui.err.zone.read", uiPlayer);
            }
        }
        if (!global) {
            try {
                if (plugin.currentMarketZone(uiPlayer).isEmpty()) {
                    return t().get("tc.market.ui.empty.local.no.zone", uiPlayer);
                }
            } catch (SQLException ex) {
                return t().get("tc.market.ui.err.zone.read", uiPlayer);
            }
        }
        return t().get(global ? "tc.market.ui.empty.global" : "tc.market.ui.empty.local", uiPlayer);
    }

    private TableRow listingRow(MarketplaceListing listing) {
        long fee = plugin.marketplaceBuyerFee(uiPlayer, listing);
        int feePercent = plugin.marketplaceBuyerFeePercent(uiPlayer, listing);
        return new TableRow(Arrays.asList(
                labelCell(listingLabel(listing), 21f),
                labelCell(String.valueOf(listing.amount()), 9f),
                labelCell(conditionValue(listing.itemName(), listing.itemState()), 12f),
                labelCell(priceWithFee(listing.price(), fee, feePercent, listing.currencyIdentifier()), 16f),
                labelCell(listing.sellerName(), 15f),
                labelCell(listing.marketZoneId(), 12f),
                new TableCell(buyButton(listing), 15f)));
    }

    private OZUIElement listingCard(MarketplaceListing listing) {
        long fee = plugin.marketplaceBuyerFee(uiPlayer, listing);
        int feePercent = plugin.marketplaceBuyerFeePercent(uiPlayer, listing);
        OZUIElement card = smallCard(252, 176);
        card.addChild(cardIcon(itemIcon(listing.itemName(), listing.itemVariant()), 12, 12, 36));

        UILabel title = label(listingLabel(listing), 14, Font.DefaultBold);
        title.setPivot(Pivot.UpperLeft);
        title.setPosition(60, 12, false);
        title.setSize(178, 34, false);
        title.setTextWrap(true);
        title.setTextAlign(TextAnchor.UpperLeft);
        card.addChild(title);

        UILabel amount = label(t().get("tc.market.ui.card.amount", uiPlayer)
                .replace("PH_AMOUNT", String.valueOf(listing.amount())), 12, Font.Default);
        amount.setPivot(Pivot.UpperLeft);
        amount.setPosition(12, 56, false);
        amount.setSize(110, 22, false);
        card.addChild(amount);

        UILabel seller = label(listing.sellerName(), 12, Font.Default);
        seller.setPivot(Pivot.UpperLeft);
        seller.setPosition(12, 76, false);
        seller.setSize(226, 20, false);
        seller.setText(t().get("tc.market.ui.card.seller", uiPlayer).replace("PH_SELLER", listing.sellerName()));
        card.addChild(seller);

        String conditionLabel = conditionLabel(listing.itemName(), listing.itemState());
        if (!conditionLabel.isBlank()) {
            UILabel condition = label(conditionLabel, 12, Font.Default);
            condition.setPivot(Pivot.UpperLeft);
            condition.setPosition(12, 98, false);
            condition.setSize(226, 20, false);
            card.addChild(condition);
        }

        UILabel price = label(priceWithFee(listing.price(), fee, feePercent, listing.currencyIdentifier()), 13,
                Font.DefaultBold);
        price.setPivot(Pivot.UpperLeft);
        price.setPosition(12, 118, false);
        price.setSize(110, 24, false);
        price.setFontColor(0xF2C766FF);
        card.addChild(price);

        UIElement action = buyButton(listing);
        action.setPivot(Pivot.LowerRight);
        action.setPosition(238, 164, false);
        action.setSize(112, 26, false);
        card.addChild(action);
        return card;
    }

    private void setupSalesTab() {
        TableScrollView table = new TableScrollView(
                Arrays.asList(
                        t().get("tc.market.ui.col.item", uiPlayer),
                        t().get("tc.market.ui.col.amount", uiPlayer),
                        t().get("tc.market.ui.col.condition", uiPlayer),
                        t().get("tc.market.ui.col.buyer", uiPlayer),
                        t().get("tc.market.ui.col.payout", uiPlayer),
                        t().get("tc.market.ui.col.fee", uiPlayer),
                        t().get("tc.market.ui.col.zone", uiPlayer),
                        t().get("tc.market.ui.col.action", uiPlayer)),
                Arrays.asList(22f, 8f, 14f, 14f, 14f, 10f, 10f, 8f));
        table.setScrollBodyHeight(TABLE_BODY_HEIGHT);

        List<MarketplaceSale> sales = visibleSales();
        if (sales.isEmpty()) {
            table.addRow(new TableRow(Arrays.asList(labelCell(t().get("tc.market.ui.empty.sales", uiPlayer), 100f))));
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
                labelCell(listingLabel(sale.itemName(), sale.itemVariant()), 22f),
                labelCell(String.valueOf(sale.amount()), 8f),
                labelCell(conditionValue(sale.itemName(), sale.itemState()), 14f),
                labelCell(buyerName(sale), 14f),
                labelCell(sale.sellerPayout() + currencyLabel(sale.currencyIdentifier()), 14f),
                labelCell(String.valueOf(sale.fee()), 10f),
                labelCell(sale.marketZoneId(), 10f),
                new TableCell(removeSaleButton(sale), 8f)));
    }

    private String buyerName(MarketplaceSale sale) {
        PlayerDatabaseHelper.PlayerRecord buyer = PlayerDatabaseHelper.findPlayersByDbIds(plugin,
                java.util.Set.of(sale.buyerDbId())).get(sale.buyerDbId());
        return buyer == null
                        ? t().get("tc.market.ui.unknown", uiPlayer)
                        : buyer.name;
    }

    private UIElement removeSaleButton(MarketplaceSale sale) {
        AdvancedButton button = AdvancedButtonFactory.defaultButton(t().get("tc.market.ui.remove", uiPlayer),
                event -> showRemoveSaleConfirmation(sale));
        button.setPivot(Pivot.UpperLeft);
        button.setPosition(4, 5, false);
        button.setSize(72, 22, false);
        button.setBorderEdgeRadius(3, false);
        return button;
    }

    private UIElement buyButton(MarketplaceListing listing) {
        if (listing.sellerDbId() == uiPlayer.getDbID()) {
            AdvancedButton button = AdvancedButtonFactory.defaultButton(t().get("tc.market.ui.cancel.listing", uiPlayer),
                    event -> showCancelListingConfirmation(listing));
            button.setPivot(Pivot.UpperLeft);
            button.setPosition(4, 5, false);
            button.setSize(112, 22, false);
            button.setBorderEdgeRadius(3, false);
            return button;
        }
        AdvancedButton button = AdvancedButtonFactory.defaultButton(
                t().get(listing.wanted() ? "tc.market.ui.sell.to.request" : "tc.market.ui.buy", uiPlayer),
                event -> showBuyConfirmation(listing));
        button.setPivot(Pivot.UpperLeft);
        button.setPosition(4, 5, false);
        button.setSize(listing.wanted() ? 126 : 72, 22, false);
        button.setBorderEdgeRadius(3, false);
        if (listing.wanted() && InventoryTransfer.snapshotForSeller(uiPlayer, listing.itemName(),
                listing.itemVariant(), 1) == null) {
            button.setClickable(false);
            styleDisabledButton(button);
        }
        return button;
    }

    private void showBuyConfirmation(MarketplaceListing listing) {
        clearListingTradePreview();
        OZUIElement dialog = confirmationDialog(t().get(
                listing.wanted() ? "tc.market.ui.wanted.fulfill.title" : "tc.market.ui.buy.confirm.title", uiPlayer));
        dialog.setSize(430, 350, false);
        long fee = plugin.marketplaceBuyerFee(uiPlayer, listing);
        int feePercent = plugin.marketplaceBuyerFeePercent(uiPlayer, listing);
        String details = t().get(listing.wanted() ? "tc.market.ui.wanted.fulfill.text"
                : "tc.market.ui.buy.confirm.text", uiPlayer)
                .replace("PH_ITEM", listingLabel(listing))
                .replace("PH_AMOUNT", String.valueOf(listing.amount()))
                .replace("PH_PRICE", String.valueOf(listing.price()))
                .replace("PH_FEE_PERCENT", String.valueOf(feePercent))
                .replace("PH_FEE", String.valueOf(fee))
                .replace("PH_TOTAL", String.valueOf(listing.price() + fee))
                .replace("PH_CURRENCY", currencyLabel(listing.currencyIdentifier()).trim())
                .replace("PH_MODE", t().get(listing.globalListing() ? "tc.market.ui.mode.global" : "tc.market.ui.mode.local",
                        uiPlayer))
                .replace("PH_SELLER", listing.sellerName())
                + conditionDetails(listing.itemName(), listing.itemState());
        addDialogMessage(dialog, details);
        UITextField quantity = textField(String.valueOf(Math.min(1, listing.amount())));
        addDialogField(dialog, t().get("tc.market.ui.field.amount", uiPlayer), quantity, 195);
        listingTradeQuantityField = quantity;
        listingTradePreviewListing = listing;
        listingTradeUnitPriceLabel = label("", 12, Font.DefaultBold);
        listingTradeUnitPriceLabel.setPivot(Pivot.UpperLeft);
        listingTradeUnitPriceLabel.setPosition(20, 235, false);
        listingTradeUnitPriceLabel.setSize(390, 42, false);
        listingTradeUnitPriceLabel.setTextWrap(true);
        dialog.addChild(listingTradeUnitPriceLabel);
        refreshListingTradePreview(quantity, String.valueOf(Math.min(1, listing.amount())));
        OZUIElement cancel = AdvancedButtonFactory.cancel(t().get("tc.market.ui.cancel", uiPlayer),
                event -> closeListingTradeDialog(dialog));
        cancel.setPivot(Pivot.UpperLeft);
        cancel.setPosition(18, 298, false);
        cancel.setSize(112, 32, false);
        dialog.addChild(cancel);
        AdvancedButton partial = AdvancedButtonFactory.ok(t().get(
                listing.wanted() ? "tc.market.ui.sell.amount" : "tc.market.ui.buy.amount", uiPlayer), event ->
                quantity.getCurrentText(uiPlayer, value -> executeListingTrade(dialog, listing, parseInt(value))));
        partial.setPivot(Pivot.UpperLeft);
        partial.setPosition(142, 298, false);
        partial.setSize(132, 32, false);
        dialog.addChild(partial);
        AdvancedButton all = AdvancedButtonFactory.ok(t().get(
                listing.wanted() ? "tc.market.ui.sell.all" : "tc.market.ui.buy.all", uiPlayer),
                event -> executeListingTrade(dialog, listing, listing.amount()));
        all.setPivot(Pivot.UpperLeft);
        all.setPosition(286, 298, false);
        all.setSize(126, 32, false);
        dialog.addChild(all);
    }

    private void executeListingTrade(OZUIElement dialog, MarketplaceListing listing, int amount) {
        if (amount <= 0 || amount > listing.amount()) {
            uiPlayer.showInfoMessageBox(t().get("tc.market.ui.err.amount.title", uiPlayer),
                    t().get("tc.market.ui.err.amount.range", uiPlayer)
                            .replace("PH_AVAILABLE", String.valueOf(listing.amount())));
            return;
        }
        closeListingTradeDialog(dialog);
        MarketplaceResult result = plugin.buyMarketplaceListing(uiPlayer, listing.id(), amount);
        if (!result.success() && result.hasKey("tc.market.result.mailbox.full")) {
            uiPlayer.showInfoMessageBox(t().get("tc.market.ui.mailbox.full.title", uiPlayer),
                    t().get("tc.market.ui.mailbox.full.text", uiPlayer));
        } else {
            sendResult(result);
        }
        rebuild();
    }

    public void onTextFieldChanged(UITextField field, String newText) {
        if (field == null || field != listingTradeQuantityField) return;
        refreshListingTradePreview(field, newText);
    }

    private void refreshListingTradePreview(UITextField field, String amountText) {
        if (field != listingTradeQuantityField || listingTradeUnitPriceLabel == null
                || listingTradePreviewListing == null) {
            return;
        }
        int amount = parseInt(amountText);
        if (amount <= 0 || amount > listingTradePreviewListing.amount()) {
            listingTradeUnitPriceLabel.setText(t().get("tc.market.ui.unit.price.invalid", uiPlayer)
                    .replace("PH_AVAILABLE", String.valueOf(listingTradePreviewListing.amount())));
            return;
        }
        long total = MarketplaceService.partialPrice(listingTradePreviewListing.price(),
                listingTradePreviewListing.amount(), amount);
        String unitPrice = formatUnitPrice(total / (double) amount);
        listingTradeUnitPriceLabel.setText(t().get("tc.market.ui.unit.price", uiPlayer)
                .replace("PH_UNIT", unitPrice)
                .replace("PH_TOTAL", String.valueOf(total))
                .replace("PH_AMOUNT", String.valueOf(amount))
                .replace("PH_CURRENCY", currencyLabel(listingTradePreviewListing.currencyIdentifier()).trim()));
    }

    private String formatUnitPrice(double amount) {
        NumberFormat format = NumberFormat.getNumberInstance(playerLocale());
        format.setMinimumFractionDigits(0);
        format.setMaximumFractionDigits(3);
        format.setGroupingUsed(false);
        return format.format(Math.max(0.0d, amount));
    }

    private Locale playerLocale() {
        String language = de.omegazirkel.risingworld.OZTools.getPlayerLanguage(uiPlayer);
        return language == null || language.isBlank()
                ? Locale.ROOT
                : Locale.forLanguageTag(language.trim().replace('_', '-'));
    }

    private void closeListingTradeDialog(OZUIElement dialog) {
        removeChild(dialog);
        clearListingTradePreview();
    }

    private void clearListingTradePreview() {
        listingTradeQuantityField = null;
        listingTradeUnitPriceLabel = null;
        listingTradePreviewListing = null;
    }

    private void setupWantedTab() {
        AdvancedButton create = AdvancedButtonFactory.defaultButton(t().get("tc.market.ui.wanted.create", uiPlayer),
                event -> selectWantedItem());
        boolean canCreate = plugin.localListingAllowed(uiPlayer) || plugin.globalListingAllowed(uiPlayer);
        create.setClickable(canCreate);
        if (!canCreate) {
            styleDisabledButton(create);
        }
        create.setPivot(Pivot.UpperLeft);
        create.setPosition(12, 4, false);
        create.setSize(190, 30, false);
        body.addChild(create);

        TableScrollView table = new TableScrollView(Arrays.asList(
                t().get("tc.market.ui.col.item", uiPlayer),
                t().get("tc.market.ui.col.amount", uiPlayer),
                t().get("tc.market.ui.col.price", uiPlayer),
                t().get("tc.market.ui.col.buyer", uiPlayer),
                t().get("tc.market.ui.col.zone", uiPlayer),
                t().get("tc.market.ui.col.action", uiPlayer)),
                Arrays.asList(25f, 10f, 18f, 18f, 14f, 15f));
        table.setPosition(0, 42, false);
        table.setScrollBodyHeight(TABLE_BODY_HEIGHT - 42);
        List<MarketplaceListing> wanted;
        try {
            wanted = plugin.visibleMarketplaceListings(uiPlayer).stream().filter(MarketplaceListing::wanted).toList();
        } catch (SQLException ex) {
            wanted = List.of();
        }
        if (wanted.isEmpty()) {
            table.addRow(new TableRow(List.of(labelCell(t().get("tc.market.ui.empty.wanted", uiPlayer), 100f))));
        } else {
            for (MarketplaceListing listing : wanted) {
                table.addRow(new TableRow(Arrays.asList(
                        labelCell(listingLabel(listing), 25f),
                        labelCell(String.valueOf(listing.amount()), 10f),
                        labelCell(listing.price() + currencyLabel(listing.currencyIdentifier()), 18f),
                        labelCell(listing.sellerName(), 18f),
                        labelCell(listing.marketZoneId(), 14f),
                        new TableCell(buyButton(listing), 15f))));
            }
        }
        body.addChild(table);
    }

    private void selectWantedItem() {
        uiPlayer.showItemSelectionMenu(true, true, true, false, item -> {
            String itemName = item == null ? null : item.getName();
            int variant = item == null ? 0 : item.getVariant();
            // Item selection closes a modal client-side. Recreate it before
            // adding the wanted dialog, after removing the stale root.
            uiPlayer.removeUIElement(this);
            de.omegazirkel.risingworld.marketplace.PluginGUI.getInstance()
                    .openMarketplaceWantedDialog(uiPlayer, itemName, variant);
        });
    }

    public void showWantedCreateDialog(String itemName, int variant) {
        OZUIElement dialog = confirmationDialog(t().get("tc.market.ui.wanted.create.title", uiPlayer));
        dialog.setSize(470, 360, false);
        addDialogMessage(dialog, t().get("tc.market.ui.wanted.create.text", uiPlayer)
                .replace("PH_ITEM", listingLabel(itemName, variant)));
        UITextField wantedAmount = textField("1");
        addDialogField(dialog, t().get("tc.market.ui.field.amount", uiPlayer), wantedAmount, 120);
        UITextField wantedPrice = textField("");
        addDialogField(dialog, t().get("tc.market.ui.field.price", uiPlayer), wantedPrice, 166);
        Dropdown wantedCurrency = currencyDropdown();
        addDialogField(dialog, t().get("tc.market.ui.field.currency", uiPlayer), wantedCurrency, 212);
        OZUIElement cancel = AdvancedButtonFactory.cancel(t().get("tc.market.ui.cancel", uiPlayer),
                event -> removeChild(dialog));
        cancel.setPivot(Pivot.UpperLeft);
        cancel.setPosition(18, 300, false);
        cancel.setSize(112, 32, false);
        dialog.addChild(cancel);
        if (plugin.localListingAllowed(uiPlayer)) {
            AdvancedButton local = AdvancedButtonFactory.ok(t().get("tc.market.ui.create.local", uiPlayer), event ->
                    readWantedFields(dialog, wantedAmount, wantedPrice, itemName, variant, false));
            local.setPivot(Pivot.UpperLeft);
            local.setPosition(142, 300, false);
            local.setSize(142, 32, false);
            dialog.addChild(local);
        }
        if (plugin.globalListingAllowed(uiPlayer)) {
            AdvancedButton global = AdvancedButtonFactory.ok(t().get("tc.market.ui.create.global", uiPlayer), event ->
                    readWantedFields(dialog, wantedAmount, wantedPrice, itemName, variant, true));
            global.setPivot(Pivot.UpperLeft);
            global.setPosition(296, 300, false);
            global.setSize(156, 32, false);
            dialog.addChild(global);
        }
    }

    private void readWantedFields(OZUIElement dialog, UITextField wantedAmount, UITextField wantedPrice,
            String itemName, int variant, boolean global) {
        wantedAmount.getCurrentText(uiPlayer, amountText -> wantedPrice.getCurrentText(uiPlayer, priceText -> {
            int amount = parseInt(amountText);
            long price = parseLong(priceText);
            if (amount <= 0 || price <= 0) {
                uiPlayer.showInfoMessageBox(t().get("tc.market.ui.err.amount.title", uiPlayer),
                        t().get("tc.market.ui.err.wanted.values", uiPlayer));
                return;
            }
            removeChild(dialog);
            MarketplaceResult result = plugin.createWantedMarketplaceListing(uiPlayer, itemName, variant, amount,
                    price, selectedCurrency, global);
            sendResult(result);
            rebuild();
        }));
    }

    private void showCancelListingConfirmation(MarketplaceListing listing) {
        OZUIElement dialog = confirmationDialog(t().get("tc.market.ui.cancel.listing.confirm.title", uiPlayer));
        String details = t().get("tc.market.ui.cancel.listing.confirm.text", uiPlayer)
                .replace("PH_ITEM", listingLabel(listing))
                .replace("PH_AMOUNT", String.valueOf(listing.amount()))
                .replace("PH_PRICE", String.valueOf(listing.price()))
                .replace("PH_CURRENCY", currencyLabel(listing.currencyIdentifier()).trim())
                + conditionDetails(listing.itemName(), listing.itemState());
        addDialogMessage(dialog, details);
        addDialogButtons(dialog, t().get("tc.market.ui.cancel", uiPlayer),
                t().get("tc.market.ui.withdraw.listing", uiPlayer),
                () -> {
                    MarketplaceResult result = plugin.cancelMarketplaceListing(uiPlayer, listing.id());
                    sendResult(result);
                    rebuild();
                });
    }

    private void showRemoveSaleConfirmation(MarketplaceSale sale) {
        OZUIElement dialog = confirmationDialog(t().get("tc.market.ui.remove.confirm.title", uiPlayer));
        String details = t().get("tc.market.ui.remove.confirm.text", uiPlayer)
                .replace("PH_ITEM", listingLabel(sale.itemName(), sale.itemVariant()))
                .replace("PH_AMOUNT", String.valueOf(sale.amount()))
                .replace("PH_PAYOUT", String.valueOf(sale.sellerPayout()))
                .replace("PH_CURRENCY", currencyLabel(sale.currencyIdentifier()).trim());
        addDialogMessage(dialog, details);
        addDialogButtons(dialog, t().get("tc.market.ui.cancel", uiPlayer), t().get("tc.market.ui.remove", uiPlayer),
                () -> {
                    MarketplaceResult result = plugin.hideMarketplaceSale(uiPlayer, sale.id());
                    sendResult(result);
                    rebuild();
                });
    }

    private void validateAndConfirmListing() {
        if (selected == null) {
            setStatus(t().get("tc.market.ui.err.select.item", uiPlayer));
            return;
        }
        amountField.getCurrentText(uiPlayer, amountText -> priceField.getCurrentText(uiPlayer,
                priceText -> validateAndConfirmListing(amountText, priceText, selectedCurrency)));
    }

    private void validateAndConfirmListing(String amountText, String priceText, String currencyText) {
        int amount = parseInt(amountText);
        if (amount <= 0) {
            setStatus(t().get("tc.market.ui.err.amount.positive", uiPlayer));
            return;
        }
        if (amount > selected.availableAmount()) {
            setStatus(t().get("tc.market.ui.err.amount.available", uiPlayer)
                    .replace("PH_AVAILABLE", String.valueOf(selected.availableAmount())));
            return;
        }
        long price = parseLong(priceText);
        if (price <= 0) {
            setStatus(t().get("tc.market.ui.err.price.positive", uiPlayer));
            return;
        }
        Optional<MarketZone> zone;
        try {
            zone = plugin.currentMarketZone(uiPlayer);
        } catch (SQLException ex) {
            Marketplace.logger().error("Failed to validate market zone for listing UI: " + ex.getMessage());
            setStatus(t().get("tc.market.ui.err.zone.read", uiPlayer));
            return;
        }
        if (zone.isEmpty()) {
            boolean needsZone = !globalListing || plugin.marketplaceSettings().marketZoneOnlyMode;
            if (needsZone) {
                setStatus(t().get("tc.market.ui.err.market.zone", uiPlayer));
                return;
            }
        }
        if (globalListing && !plugin.globalListingAllowed(uiPlayer)) {
            setStatus(t().get("tc.market.ui.sell.no.mode", uiPlayer));
            return;
        }
        if (!globalListing && !plugin.localListingAllowed(uiPlayer)) {
            setStatus(t().get("tc.market.ui.sell.no.mode", uiPlayer));
            return;
        }
        showListingConfirmation(amount, price, normalizeCurrency(currencyText), zone);
    }

    private void showListingConfirmation(int amount, long price, String currency, Optional<MarketZone> zone) {
        OZUIElement dialog = confirmationDialog(t().get("tc.market.ui.confirm.title", uiPlayer));
        String currencyLabel = currency.isBlank() ? plugin.defaultCurrencyIdentifier() : currency;
        String zoneLabel = zone
                .map(current -> current.name() + " (" + current.id() + ")")
                .orElse(t().get("tc.market.ui.zone.global", uiPlayer));
        String details = t().get("tc.market.ui.confirm.text", uiPlayer)
                .replace("PH_ITEM", selected.displayName() + " (" + selected.itemName() + ":" + selected.variant() + ")")
                .replace("PH_AMOUNT", String.valueOf(amount))
                .replace("PH_PRICE", String.valueOf(price))
                .replace("PH_CURRENCY", currencyLabel)
                .replace("PH_MODE", t().get(globalListing ? "tc.market.ui.mode.global" : "tc.market.ui.mode.local", uiPlayer))
                .replace("PH_ZONE", zoneLabel)
                + conditionDetails(selected.itemName(), selected.itemState());
        addDialogMessage(dialog, details);
        addDialogButtons(dialog, t().get("tc.market.ui.cancel", uiPlayer), t().get("tc.market.ui.create", uiPlayer),
                () -> {
                    MarketplaceResult result = plugin.createMarketplaceListing(uiPlayer, selected.itemName(), selected.variant(),
                            amount, price, currency, globalListing, selected.itemState());
                    sendResult(result);
                    setStatus(result.localized(t(), uiPlayer));
                    if (result.success()) {
                        selected = null;
                        rebuild();
                    }
                });
    }

    private void setupManagementTab() {
        OZUIElement panel = new OZUIElement();
        panel.setPivot(Pivot.UpperLeft);
        panel.setPosition(18, 18, false);
        panel.style.width.set(94, Unit.Percent);
        panel.style.height.set(450, Unit.Pixel);
        body.addChild(panel);

        UILabel title = label(t().get("tc.market.ui.management.title", uiPlayer), 18, Font.DefaultBold);
        title.setPivot(Pivot.UpperLeft);
        title.setPosition(0, 0, false);
        title.setSize(100, 30, true);
        panel.addChild(title);

        UILabel status = label(plugin.currentMarketZoneStatus(uiPlayer), 13, Font.Default);
        status.setPivot(Pivot.UpperLeft);
        status.setPosition(0, 38, false);
        status.setSize(100, 46, true);
        status.setTextWrap(true);
        panel.addChild(status);

        Optional<MarketZone> currentZone = plugin.safeCurrentMarketZone(uiPlayer);
        int y = 96;
        Object endpoint = uiPlayer.getAttribute("oz.marketplace.crier.endpoint");
        if (endpoint instanceof MarketCrier crier) {
            boolean editable = crier.ownedBy(uiPlayer.getDbID()) || (crier.global() && uiPlayer.isAdmin());
            OZUIElement divider = new OZUIElement();
            divider.setPivot(Pivot.UpperLeft);
            divider.setPosition(0, y, false);
            divider.style.left.set(50, Unit.Percent);
            divider.setSize(1, 250, false);
            divider.setBackgroundColor(0.75f, 0.58f, 0.15f, 0.44f);
            panel.addChild(divider);
            OZUIElement transformPanel = new OZUIElement();
            transformPanel.setPivot(Pivot.UpperLeft);
            transformPanel.setPosition(0, y, false);
            transformPanel.style.left.set(52, Unit.Percent);
            transformPanel.setSize(380, 230, false);
            panel.addChild(transformPanel);
            int marketY = y;
            addManagementButton(panel, t().get("tc.market.ui.management.crier.position", uiPlayer), 0, marketY, editable,
                    () -> runManagementAction(plugin.moveCurrentMarketCrier(uiPlayer)));
            addManagementButton(panel, t().get("tc.market.ui.management.crier.appearance", uiPlayer), 188, marketY, editable,
                    () -> runManagementAction(plugin.copyCurrentMarketCrierAppearance(uiPlayer)));
            marketY += 40;
            UILabel transform = label(t().get("tc.market.ui.management.crier.transform", uiPlayer), 13, Font.DefaultBold);
            transform.setPivot(Pivot.UpperLeft);
            transform.setPosition(0, 0, false);
            transform.setSize(160, 26, false);
            transformPanel.addChild(transform);
            managementTransformStepField = textField(managementTransformStepDraft);
            addField(transformPanel, t().get("tc.market.ui.management.crier.move.step", uiPlayer), managementTransformStepField, 28);
            addCrierNudgeButton(transformPanel, "X−", 0, 66, editable, -1, 0, 0);
            addCrierNudgeButton(transformPanel, "X+", 58, 66, editable, 1, 0, 0);
            addCrierNudgeButton(transformPanel, "Y−", 116, 66, editable, 0, -1, 0);
            addCrierNudgeButton(transformPanel, "Y+", 174, 66, editable, 0, 1, 0);
            addCrierNudgeButton(transformPanel, "Z−", 232, 66, editable, 0, 0, -1);
            addCrierNudgeButton(transformPanel, "Z+", 290, 66, editable, 0, 0, 1);
            UILabel rotation = label(t().get("tc.market.ui.management.crier.rotation", uiPlayer), 13, Font.DefaultBold);
            rotation.setPivot(Pivot.UpperLeft);
            rotation.setPosition(0, 112, false);
            rotation.setSize(160, 26, false);
            transformPanel.addChild(rotation);
            managementRotationStepField = textField(managementRotationStepDraft);
            addField(transformPanel, t().get("tc.market.ui.management.crier.rotation.step", uiPlayer), managementRotationStepField, 140);
            addCrierRotationButton(transformPanel, "Y−", 0, 178, editable, -1);
            addCrierRotationButton(transformPanel, "Y+", 58, 178, editable, 1);
            managementCrierNameField = textField(crier.name());
            addField(panel, t().get("tc.market.ui.management.crier.name", uiPlayer), managementCrierNameField, marketY);
            addManagementButton(panel, t().get("tc.market.ui.save", uiPlayer), 288, marketY, editable,
                    () -> managementCrierNameField.getCurrentText(uiPlayer,
                            value -> runManagementAction(plugin.renameCurrentMarketCrier(uiPlayer, value))));
            marketY += 40;
            managementFeeField = textField(String.valueOf(crier.feePercent()));
            addField(panel, t().get("tc.market.ui.management.fee.field", uiPlayer), managementFeeField, marketY);
            addManagementButton(panel, t().get("tc.market.ui.management.set.fee", uiPlayer), 288, marketY, editable,
                    () -> managementFeeField.getCurrentText(uiPlayer,
                            value -> runManagementAction(plugin.setCurrentMarketCrierFee(uiPlayer, parseInt(value)))));
            marketY += 40;
            if (crier.personal()) {
                addCrierSharingSwitch(panel, crier, 0, marketY, editable);
                marketY += 40;
                String currency = plugin.defaultCurrencyIdentifier();
                WalletBridge.BalanceInfo playerBalance = plugin.walletBalance(uiPlayer, currency);
                UILabel playerBalanceLabel = label(t().get("tc.market.ui.management.crier.account.player.balance", uiPlayer)
                        .replace("PH_BALANCE", String.valueOf(playerBalance.success() ? playerBalance.balance() : 0L))
                        .replace("PH_CURRENCY", currency), 14, Font.DefaultBold);
                playerBalanceLabel.setPivot(Pivot.UpperLeft);
                playerBalanceLabel.setPosition(0, marketY, false);
                playerBalanceLabel.setSize(560, 20, false);
                panel.addChild(playerBalanceLabel);
                UILabel balance = label(t().get("tc.market.ui.management.crier.account.balance", uiPlayer)
                        .replace("PH_BALANCE", String.valueOf(plugin.currentMarketCrierBalance(uiPlayer, currency)))
                        .replace("PH_CURRENCY", currency), 14, Font.DefaultBold);
                balance.setPivot(Pivot.UpperLeft);
                balance.setPosition(0, marketY + 20, false);
                balance.setSize(560, 20, false);
                panel.addChild(balance);
                marketY += 40;
                managementCrierAccountAmountField = textField("");
                addField(panel, t().get("tc.market.ui.management.crier.account.amount", uiPlayer),
                        managementCrierAccountAmountField, marketY);
                addManagementButton(panel, t().get("tc.market.ui.management.crier.account.deposit", uiPlayer), 280, marketY, 132, editable,
                        () -> managementCrierAccountAmountField.getCurrentText(uiPlayer,
                                value -> runManagementAction(plugin.transferCurrentMarketCrierAccount(uiPlayer,
                                        "deposit", parseLong(value), currency))));
                addManagementButton(panel, t().get("tc.market.ui.management.crier.account.withdraw", uiPlayer), 420, marketY, 132, editable,
                        () -> managementCrierAccountAmountField.getCurrentText(uiPlayer,
                                value -> runManagementAction(plugin.transferCurrentMarketCrierAccount(uiPlayer,
                                        "withdraw", parseLong(value), currency))));
                marketY += 40;
            }
            OZUIElement dissolve = AdvancedButtonFactory.danger(t().get("tc.market.ui.management.crier.delete", uiPlayer),
                    event -> showDissolveCrierConfirmation());
            dissolve.setPivot(Pivot.UpperLeft);
            dissolve.setPosition(0, marketY, false);
            dissolve.setSize(220, 32, false);
            dissolve.setBorderEdgeRadius(3, false);
            dissolve.setClickable(editable);
            panel.addChild(dissolve);
            return;
        }
        if (currentZone.isEmpty()) {
            if (uiPlayer.isAdmin()) {
                addManagementButton(panel, t().get("tc.market.ui.management.create.system", uiPlayer), 0, y,
                        () -> runManagementAction(plugin.createOrUpdateCurrentMarketZone(uiPlayer, false)));
                addManagementButton(panel, t().get("tc.market.ui.management.create.private", uiPlayer), 188, y,
                        plugin.canCreatePrivateMarket(uiPlayer),
                        () -> runManagementAction(plugin.createOrUpdateCurrentMarketZone(uiPlayer, true)));
            } else {
                addManagementButton(panel, t().get("tc.market.ui.management.create", uiPlayer), 0, y,
                        () -> runManagementAction(plugin.createOrUpdateCurrentMarketZone(uiPlayer)));
            }
            return;
        }

        addManagementButton(panel, t().get("tc.market.ui.management.sync.name", uiPlayer), 0, y,
                () -> runManagementAction(plugin.syncCurrentMarketZoneName(uiPlayer)));
        addManagementButton(panel, t().get("tc.market.ui.management.next.global", uiPlayer), 188, y,
                !currentZone.get().playerOwned(), () -> runManagementAction(plugin.toggleCurrentMarketZoneGlobal(uiPlayer)));

        y += 54;
        managementFeeField = textField(String.valueOf(currentZone.get().feePercent()));
        addField(panel, t().get("tc.market.ui.management.fee.field", uiPlayer), managementFeeField, y);
        addManagementButton(panel, t().get("tc.market.ui.management.set.fee", uiPlayer), 288, y,
                () -> managementFeeField.getCurrentText(uiPlayer,
                        value -> runManagementAction(plugin.setCurrentMarketZoneFee(uiPlayer, parseInt(value)))));

        y += 70;
        OZUIElement delete = AdvancedButtonFactory.cancel(t().get("tc.market.ui.management.delete", uiPlayer),
                event -> showDeleteZoneConfirmation());
        delete.setPivot(Pivot.UpperLeft);
        delete.setPosition(0, y, false);
        delete.setSize(178, 32, false);
        delete.setBorderEdgeRadius(3, false);
        panel.addChild(delete);
    }

    private void addManagementButton(OZUIElement parent, String text, int x, int y, Runnable action) {
        addManagementButton(parent, text, x, y, true, action);
    }

    private void addCrierSharingSwitch(OZUIElement parent, MarketCrier crier, int x, int y, boolean enabled) {
        UILabel label = label(t().get("tc.market.ui.management.crier.sharing.label", uiPlayer), 12, Font.DefaultBold);
        label.setPivot(Pivot.UpperLeft);
        label.setPosition(x, y + 4, false);
        label.setSize(280, 24, false);
        parent.addChild(label);
        boolean shared = crier.sharedListings();
        AdvancedButton toggle = AdvancedButtonFactory.defaultButton(t().get(shared
                ? "tc.market.ui.management.crier.sharing.on" : "tc.market.ui.management.crier.sharing.off", uiPlayer),
                event -> runManagementAction(plugin.setCurrentMarketCrierSharing(uiPlayer, !shared)));
        toggle.setClickable(enabled);
        if (shared) {
            toggle.setBackgroundColor(0.12f, 0.40f, 0.16f, 0.96f);
            toggle.setBorderColor(0.50f, 0.88f, 0.50f, 0.58f);
        } else {
            toggle.setBackgroundColor(0.46f, 0.18f, 0.12f, 0.96f);
            toggle.setBorderColor(0.95f, 0.42f, 0.32f, 0.58f);
        }
        if (!enabled) styleDisabledButton(toggle);
        toggle.setPivot(Pivot.UpperLeft);
        toggle.setPosition(x + 300, y, false);
        toggle.setSize(112, 32, false);
        toggle.setBorderEdgeRadius(16, false);
        parent.addChild(toggle);
    }

    private void addManagementButton(OZUIElement parent, String text, int x, int y, boolean enabled, Runnable action) {
        addManagementButton(parent, text, x, y, 178, enabled, action);
    }

    private void addManagementButton(OZUIElement parent, String text, int x, int y, int width, boolean enabled,
            Runnable action) {
        AdvancedButton button = AdvancedButtonFactory.defaultButton(text, event -> action.run());
        button.setClickable(enabled);
        if (!enabled) {
            styleDisabledButton(button);
        }
        button.setPivot(Pivot.UpperLeft);
        button.setPosition(x, y, false);
        button.setSize(width, 32, false);
        button.setBorderEdgeRadius(3, false);
        parent.addChild(button);
    }

    private void addCrierNudgeButton(OZUIElement parent, String text, int x, int y, boolean enabled,
            float deltaX, float deltaY, float deltaZ) {
        AdvancedButton button = AdvancedButtonFactory.defaultButton(text, event -> managementTransformStepField
                .getCurrentText(uiPlayer, value -> {
                    managementTransformStepDraft = retainedDecimal(value, "1.00");
                    float step = positiveDecimal(value, 1.0f);
                    runTransformAction(plugin.nudgeCurrentMarketCrier(uiPlayer,
                            deltaX * step, deltaY * step, deltaZ * step));
                }));
        button.setClickable(enabled);
        if (!enabled) styleDisabledButton(button);
        button.setPivot(Pivot.UpperLeft);
        button.setPosition(x, y, false);
        button.setSize(52, 30, false);
        button.setBorderEdgeRadius(3, false);
        parent.addChild(button);
    }

    private void addCrierRotationButton(OZUIElement parent, String text, int x, int y, boolean enabled,
            float direction) {
        AdvancedButton button = AdvancedButtonFactory.defaultButton(text, event -> managementRotationStepField
                .getCurrentText(uiPlayer, value -> {
                    managementRotationStepDraft = retainedDecimal(value, "15");
                    float step = positiveDecimal(value, 15.0f);
                    runTransformAction(plugin.rotateCurrentMarketCrier(uiPlayer, direction * step));
                }));
        button.setClickable(enabled);
        if (!enabled) styleDisabledButton(button);
        button.setPivot(Pivot.UpperLeft);
        button.setPosition(x, y, false);
        button.setSize(52, 30, false);
        button.setBorderEdgeRadius(3, false);
        parent.addChild(button);
    }

    private void styleDisabledButton(AdvancedButton button) {
        button.setBackgroundColor(0.10f, 0.10f, 0.10f, 0.45f);
        button.setBorderColor(0.45f, 0.45f, 0.45f, 0.35f);
        button.setHoverBackgroundColor(0x1A1A1A73);
        button.setHoverBorderColor(0x73737359);
        button.setHoverBorderWidth(1);
    }

    private void styleWarningButton(AdvancedButton button) {
        button.setBackgroundColor(0.54f, 0.30f, 0.06f, 0.96f);
        button.setBorderColor(0.95f, 0.75f, 0.25f, 0.72f);
        button.setHoverBackgroundColor(0xA85B10F5);
        button.setHoverBorderColor(0xF2C766DD);
        button.setHoverBorderWidth(1);
    }

    private void runManagementAction(MarketplaceResult result) {
        sendResult(result);
        rebuild();
    }

    private void runTransformAction(MarketplaceResult result) {
        sendResult(result);
    }

    private void sendResult(MarketplaceResult result) {
        uiPlayer.sendTextMessage((result.success() ? c.okay : c.error) + result.localized(t(), uiPlayer));
    }

    private void showDeleteZoneConfirmation() {
        OZUIElement dialog = confirmationDialog(t().get("tc.market.ui.management.delete.confirm.title", uiPlayer));
        addDialogMessage(dialog, t().get("tc.market.ui.management.delete.confirm.text", uiPlayer));
        addDialogButtons(dialog, t().get("tc.market.ui.cancel", uiPlayer),
                t().get("tc.market.ui.management.delete", uiPlayer),
                () -> runManagementAction(plugin.deleteCurrentMarketZone(uiPlayer)));
    }

    private void showDissolveCrierConfirmation() {
        OZUIElement dialog = confirmationDialog(t().get("tc.market.ui.management.crier.delete.confirm.title", uiPlayer));
        dialog.setSize(560, 250, false);
        addDialogMessage(dialog, t().get("tc.market.ui.management.crier.delete.confirm.text", uiPlayer), 520);
        addDissolveCrierButtons(dialog);
    }

    private void addDissolveCrierButtons(OZUIElement dialog) {
        OZUIElement cancel = AdvancedButtonFactory.cancel(t().get("tc.market.ui.cancel", uiPlayer), event -> removeChild(dialog));
        cancel.setPivot(Pivot.UpperLeft); cancel.setPosition(20, 184, false); cancel.setSize(120, 32, false);
        dialog.addChild(cancel);
        AdvancedButton unlink = AdvancedButtonFactory.defaultButton(t().get("tc.market.ui.management.crier.delete.unlink", uiPlayer),
                event -> { removeChild(dialog); runManagementAction(plugin.dissolveCurrentMarketCrier(uiPlayer, false)); });
        styleWarningButton(unlink);
        unlink.setPivot(Pivot.UpperLeft); unlink.setPosition(152, 184, false); unlink.setSize(190, 32, false);
        dialog.addChild(unlink);
        OZUIElement deleteNpc = AdvancedButtonFactory.danger(t().get("tc.market.ui.management.crier.delete.npc", uiPlayer),
                event -> { removeChild(dialog); runManagementAction(plugin.dissolveCurrentMarketCrier(uiPlayer, true)); });
        deleteNpc.setPivot(Pivot.UpperLeft); deleteNpc.setPosition(354, 184, false); deleteNpc.setSize(186, 32, false);
        dialog.addChild(deleteNpc);
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
        addDialogMessage(dialog, details, 390);
    }

    private void addDialogMessage(OZUIElement dialog, String details, int width) {
        UILabel message = label(details, 13, Font.Default);
        message.setPivot(Pivot.UpperLeft);
        message.setPosition(20, 54, false);
        message.setSize(width, 110, false);
        message.setTextWrap(true);
        dialog.addChild(message);
    }

    private void addDialogButtons(OZUIElement dialog, String cancelText, String confirmText, Runnable onConfirm) {
        OZUIElement cancel = AdvancedButtonFactory.cancel(cancelText, event -> removeChild(dialog));
        cancel.setPivot(Pivot.UpperLeft);
        cancel.setPosition(26, 184, false);
        cancel.setSize(132, 32, false);
        cancel.setBorderEdgeRadius(3, false);
        dialog.addChild(cancel);

        OZUIElement confirm = AdvancedButtonFactory.ok(confirmText, event -> {
            removeChild(dialog);
            onConfirm.run();
        });
        confirm.setPivot(Pivot.UpperLeft);
        int confirmWidth = Math.max(132, Math.min(190, confirmText == null ? 132 : confirmText.length() * 9 + 28));
        confirm.setPosition(430 - 26 - confirmWidth, 184, false);
        confirm.setSize(confirmWidth, 32, false);
        confirm.setBorderEdgeRadius(3, false);
        dialog.addChild(confirm);
    }

    private void addField(OZUIElement form, String labelText, UIElement field, int y) {
        addField(form, labelText, field, 0, y);
    }

    private void addDialogField(OZUIElement dialog, String labelText, UIElement field, int y) {
        addField(dialog, labelText, field, 20, y);
    }

    private void addField(OZUIElement form, String labelText, UIElement field, int x, int y) {
        UILabel fieldLabel = label(labelText, 12, Font.DefaultBold);
        fieldLabel.setPivot(Pivot.UpperLeft);
        fieldLabel.setPosition(x, y, false);
        fieldLabel.setSize(108, 24, false);
        form.addChild(fieldLabel);

        field.setPivot(Pivot.UpperLeft);
        field.setPosition(x + 112, y, false);
        field.setSize(160, 30, false);
        form.addChild(field);
    }

    private Dropdown currencyDropdown() {
        List<DropdownOption> options = plugin.walletCurrencies().stream()
                .map(currency -> new DropdownOption(currency.defaultCurrency() ? "" : currency.identifier(),
                        currency.identifier() + (currency.defaultCurrency() ? " *" : "")))
                .toList();
        if (options.isEmpty()) {
            options = List.of(new DropdownOption("", plugin.defaultCurrencyIdentifier()));
        }
        Dropdown dropdown = new Dropdown(options, selectedCurrency, key -> selectedCurrency = key == null ? "" : key);
        dropdown.setPivot(Pivot.UpperLeft);
        dropdown.setSize(160, 30, false);
        return dropdown;
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

    private void addMessage(String text, int y) {
        UILabel message = label(text, 15, Font.DefaultBold);
        message.setPivot(Pivot.UpperLeft);
        message.setPosition(18, y, false);
        message.setSize(94, 80, true);
        message.setTextWrap(true);
        body.addChild(message);
    }

    private UIScrollView flexScroll(int height) {
        UIScrollView scroll = new UIScrollView(ScrollViewMode.Vertical);
        scroll.setPivot(Pivot.UpperLeft);
        scroll.setPosition(0, 0, false);
        scroll.style.width.set(100, Unit.Percent);
        scroll.style.height.set(height, Unit.Pixel);
        scroll.style.paddingLeft.set(10);
        scroll.style.paddingRight.set(10);
        scroll.style.paddingTop.set(10);
        scroll.style.paddingBottom.set(16);
        return scroll;
    }

    private OZUIElement flexWrapper() {
        OZUIElement wrapper = new OZUIElement();
        wrapper.setPivot(Pivot.UpperLeft);
        wrapper.style.width.set(100, Unit.Percent);
        wrapper.style.height.set(100, Unit.Percent);
        wrapper.style.display.set(DisplayStyle.Flex);
        wrapper.style.flexDirection.set(FlexDirection.Row);
        wrapper.style.flexWrap.set(Wrap.Wrap);
        wrapper.style.alignContent.set(Align.FlexStart);
        wrapper.style.justifyContent.set(Justify.FlexStart);
        return wrapper;
    }

    private OZUIElement smallCard(int width, int height) {
        OZUIElement card = new OZUIElement();
        card.setPivot(Pivot.UpperLeft);
        card.style.width.set(width, Unit.Pixel);
        card.style.height.set(height, Unit.Pixel);
        card.style.marginLeft.set(6);
        card.style.marginRight.set(6);
        card.style.marginTop.set(6);
        card.style.marginBottom.set(10);
        card.setBackgroundColor(0.10f, 0.09f, 0.08f, 0.92f);
        card.setHoverBackgroundColor(0x2A2419DD);
        card.setBorder(1);
        card.setBorderColor(0.95f, 0.75f, 0.25f, 0.42f);
        card.setBorderEdgeRadius(6, false);
        return card;
    }

    private OZUIElement cardIcon(TextureAsset asset, int x, int y, int size) {
        OZUIElement icon = new OZUIElement();
        icon.setPivot(Pivot.UpperLeft);
        icon.setPosition(x, y, false);
        icon.setSize(size, size, false);
        icon.setBackgroundColor(0, 0, 0, 0);
        if (asset != null) {
            icon.style.backgroundImage.set(asset);
            icon.style.backgroundImageScaleMode.set(ScaleMode.ScaleToFit);
        }
        return icon;
    }

    private TextureAsset itemIcon(String itemName, int itemVariant) {
        TextureAsset asset = null;
        if (itemName != null && !itemName.isBlank()) {
            ObjectDefinition objectDefinition = MarketplaceItemNames.objectDefinition(itemName);
            ObjectDefinition.Variant objectVariant = objectDefinition == null ? null : objectDefinition.getVariant(itemVariant);
            if (objectVariant != null) {
                asset = objectDefinition.getIcon(objectVariant.variant);
            }
            ItemDefinition definition = MarketplaceItemNames.definition(itemName);
            ItemDefinition.Variant definitionVariant = definition == null ? null : definition.getVariant(itemVariant);
            if (asset == null && definitionVariant != null) {
                asset = definition.getIcon(definitionVariant.variant);
            }
            ConstructionDefinition constructionDefinition = Definitions.getConstructionDefinition(itemName);
            if (asset == null && constructionDefinition != null) {
                asset = constructionDefinition.getIcon(itemVariant);
            }
            ClothingDefinition clothingDefinition = Definitions.getClothingDefinition(itemName);
            if (asset == null && clothingDefinition != null) {
                asset = clothingDefinition.getIcon(itemVariant);
            }
        }
        if (asset == null) {
            asset = AssetManager.getIcon(uiPlayer, "placeholder");
        }
        return asset;
    }

    private String listingLabel(MarketplaceListing listing) {
        return listingLabel(listing.itemName(), listing.itemVariant());
    }

    private int durabilityPercent(String itemName, MarketplaceItemState itemState) {
        return MarketplaceItemNames.durabilityPercent(itemName, itemState);
    }

    private boolean isDamaged(String itemName, MarketplaceItemState itemState) {
        int percent = durabilityPercent(itemName, itemState);
        return percent >= 0 && percent < 100;
    }

    private String conditionLabel(String itemName, MarketplaceItemState itemState) {
        int percent = durabilityPercent(itemName, itemState);
        if (percent < 0) {
            return "";
        }
        return t().get("tc.market.ui.condition", uiPlayer).replace("PH_PERCENT", String.valueOf(percent));
    }

    private String conditionValue(String itemName, MarketplaceItemState itemState) {
        int percent = durabilityPercent(itemName, itemState);
        return percent < 0 ? "" : percent + "%";
    }

    private String conditionDetails(String itemName, MarketplaceItemState itemState) {
        String condition = conditionLabel(itemName, itemState);
        return condition.isBlank() ? "" : "\n" + condition;
    }

    private String conditionSuffix(String itemName, MarketplaceItemState itemState) {
        return isDamaged(itemName, itemState) ? " (" + durabilityPercent(itemName, itemState) + "%)" : "";
    }

    private String listingLabel(String itemName, int itemVariant) {
        return MarketplaceItemNames.listingLabel(itemName, itemVariant, uiPlayer.getLanguage());
    }

    private String sellUnavailableMessage() {
        PluginSettings settings = plugin.marketplaceSettings();
        if (!settings.localMarketplaceEnabled && !settings.globalMarketplaceEnabled) {
            return t().get("tc.market.ui.sell.disabled", uiPlayer);
        }
        if (settings.marketZoneOnlyMode && plugin.safeCurrentMarketZone(uiPlayer).isEmpty()) {
            return t().get("tc.market.ui.sell.zone.required", uiPlayer);
        }
        return t().get("tc.market.ui.sell.no.mode", uiPlayer);
    }

    private String normalizeCurrency(String currency) {
        String value = currency == null ? "" : currency.trim();
        return value.equalsIgnoreCase(plugin.defaultCurrencyIdentifier()) ? "" : value;
    }

    private String currencyLabel(String currency) {
        return currency == null || currency.isBlank()
                ? " " + plugin.defaultCurrencyIdentifier()
                : " " + currency;
    }

    private String priceWithFee(long price, long fee, int feePercent, String currency) {
        String label = price + currencyLabel(currency);
        return fee > 0L
                ? label + " +" + fee + currencyLabel(currency) + " (" + feePercent + "%)"
                : label;
    }

    private int parseInt(String value) {
        try {
            return Integer.parseInt(value == null ? "" : value.trim());
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private float positiveDecimal(String value, float fallback) {
        try {
            float parsed = Float.parseFloat(value == null ? "" : value.trim().replace(',', '.'));
            return Float.isFinite(parsed) && parsed > 0f && parsed <= 1000f ? parsed : fallback;
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private String retainedDecimal(String value, String fallback) {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }

    private long parseLong(String value) {
        try {
            return Long.parseLong(value == null ? "" : value.trim());
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }
}
