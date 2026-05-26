package de.omegazirkel.risingworld.marketplace.ui;

import de.omegazirkel.risingworld.Marketplace;
import de.omegazirkel.risingworld.tools.I18n;
import de.omegazirkel.risingworld.tools.ui.BasePlayerPluginSettingsPanel;
import de.omegazirkel.risingworld.tools.ui.OZUIElement;
import de.omegazirkel.risingworld.tools.ui.PlayerPluginSettings;
import net.risingworld.api.objects.Player;
import net.risingworld.api.ui.UILabel;
import net.risingworld.api.ui.style.Unit;

public class MarketplacePlayerPluginSettings extends PlayerPluginSettings {
    public MarketplacePlayerPluginSettings(String pluginVersion) {
        this.pluginLabel = Marketplace.name;
        this.pluginVersion = pluginVersion;
    }

    private I18n t() {
        return I18n.getInstance(Marketplace.name);
    }

    private String text(Player uiPlayer, String key) {
        return t().get(key, uiPlayer).replace("PH_PLUGIN_NAME", pluginLabel);
    }

    @Override
    public BasePlayerPluginSettingsPanel createPlayerPluginSettingsUIElement(Player uiPlayer) {
        return new BasePlayerPluginSettingsPanel(uiPlayer, pluginLabel) {
            @Override
            protected void redrawContent() {
                flexWrapper.removeAllChilds();
                OZUIElement element = defaultSettingsContainer();
                element.style.width.set(95, Unit.Percent);
                UILabel label = defaultSettingsLabel(text(uiPlayer, "TC_SETTINGS_EMPTY"));
                element.addChild(label);
                flexWrapper.addChild(element);
            }
        };
    }
}
