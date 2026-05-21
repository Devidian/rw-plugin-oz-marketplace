package de.omegazirkel.risingworld.marketplace.ui;

import net.risingworld.api.objects.Player;
import net.risingworld.api.ui.UIElement;
import net.risingworld.api.ui.UILabel;
import net.risingworld.api.ui.style.Font;
import net.risingworld.api.ui.style.Pivot;
import net.risingworld.api.ui.style.Position;
import net.risingworld.api.ui.style.TextAnchor;
import net.risingworld.api.ui.style.Unit;

public class MarketplaceZoneIndicatorOverlay {
    private final UIElement root;
    private final UILabel label;

    public MarketplaceZoneIndicatorOverlay() {
        root = new UIElement();
        root.setPivot(Pivot.UpperCenter);
        root.style.position.set(Position.Absolute);
        root.style.left.set(50, Unit.Percent);
        root.style.top.set(9.6f, Unit.Percent);
        root.style.width.set(42, Unit.Percent);
        root.style.minWidth.set(34, Unit.Percent);
        root.style.maxWidth.set(72, Unit.Percent);
        root.style.height.set(28, Unit.Pixel);
        root.setBackgroundColor(0f, 0f, 0f, 0.68f);
        root.setBorder(1);
        root.setBorderColor(0.95f, 0.75f, 0.25f, 0.58f);
        root.setBorderEdgeRadius(5, false);

        label = new UILabel("");
        label.setRichTextEnabled(true);
        label.setFont(Font.DefaultBold);
        label.setFontSize(14);
        label.setFontColor(0xFFFFFFFF);
        label.setPivot(Pivot.MiddleCenter);
        label.setPosition(50, 50, true);
        label.style.width.set(96, Unit.Percent);
        label.style.height.set(22, Unit.Pixel);
        label.setTextAlign(TextAnchor.MiddleCenter);
        label.setTextWrap(false);

        root.addChild(label);
    }

    public void updateText(String text) {
        label.setText(text == null ? "" : text);
    }

    public void show(Player player) {
        player.removeUIElement(root);
        player.addUIElement(root);
    }

    public void hide(Player player) {
        player.removeUIElement(root);
    }
}
