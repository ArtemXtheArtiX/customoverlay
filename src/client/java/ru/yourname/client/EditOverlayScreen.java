package ru.yourname.client;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;

public class EditOverlayScreen extends Screen {
	private TextField selectedField = null;
	private boolean dragging = false, resizing = false;
	private int dragOffsetX = 0, dragOffsetY = 0, startResizeX = 0, startResizeY = 0, startWidth = 0, startHeight = 0;

	public EditOverlayScreen() { super(Text.literal("Edit Overlay")); }

	@Override
	public boolean mouseClicked(Click click, boolean doubleClick) {
		if (click.button() == 0) {
			double mouseX = click.x();
			double mouseY = click.y();
			for (int i = OverlayRenderer.fields.size() - 1; i >= 0; i--) {
				TextField f = OverlayRenderer.fields.get(i);
				if (f.isMouseOver(mouseX, mouseY)) {
					selectedField = f; OverlayRenderer.selectedField = f;
					if (f.isOverResizeCorner(mouseX, mouseY)) {
						resizing = true; startResizeX = (int) mouseX; startResizeY = (int) mouseY;
						startWidth = f.width; startHeight = f.height;
					} else {
						dragging = true; dragOffsetX = (int) (mouseX - f.x); dragOffsetY = (int) (mouseY - f.y);
					}
					return true;
				}
			}
			selectedField = null; OverlayRenderer.selectedField = null;
		}
		return super.mouseClicked(click, doubleClick);
	}

	@Override
	public boolean mouseDragged(Click click, double deltaX, double deltaY) {
		if (selectedField != null) {
			double mouseX = click.x();
			double mouseY = click.y();
			if (dragging) { selectedField.x = (int) (mouseX - dragOffsetX); selectedField.y = (int) (mouseY - dragOffsetY); }
			else if (resizing) {
				selectedField.width = Math.max(32, startWidth + (int)(mouseX - startResizeX));
				selectedField.height = Math.max(32, startHeight + (int)(mouseY - startResizeY));
			}
			return true;
		}
		return super.mouseDragged(click, deltaX, deltaY);
	}

	@Override
	public boolean mouseReleased(Click click) {
		dragging = false; resizing = false;
		if (selectedField != null) ConfigManager.save();
		return super.mouseReleased(click);
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		for (TextField f : OverlayRenderer.fields) f.render(context, true);
		context.drawCenteredTextWithShadow(this.textRenderer, "EDIT MODE: Drag to move, drag blue corner to resize, DEL to delete, ESC to exit", this.width / 2, 10, 0xFFFF00);
	}

	@Override
	public boolean keyPressed(KeyInput input) {
		// ИСПРАВЛЕНО: input.key().getCode()
		if (input.key().getCode() == 256) { ConfigManager.save(); close(); return true; }
		if (input.key().getCode() == 261 && selectedField != null) {
			selectedField.cleanup(); OverlayRenderer.fields.remove(selectedField);
			selectedField = null; OverlayRenderer.selectedField = null; ConfigManager.save(); return true;
		}
		return super.keyPressed(input);
	}
}
