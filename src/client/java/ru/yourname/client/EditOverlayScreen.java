package ru.yourname.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

public class EditOverlayScreen extends Screen {
	private TextField selectedField = null;
	private boolean dragging = false, resizing = false;
	private int dragOffsetX = 0, dragOffsetY = 0, startResizeX = 0, startResizeY = 0, startWidth = 0, startHeight = 0;

	public EditOverlayScreen() { super(Text.literal("Edit Overlay")); }

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 0) {
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
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		if (selectedField != null) {
			if (dragging) { selectedField.x = (int) (mouseX - dragOffsetX); selectedField.y = (int) (mouseY - dragOffsetY); }
			else if (resizing) {
				selectedField.width = Math.max(32, startWidth + (int)(mouseX - startResizeX));
				selectedField.height = Math.max(32, startHeight + (int)(mouseY - startResizeY));
			}
			return true;
		}
		return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		dragging = false; resizing = false;
		if (selectedField != null) ConfigManager.save();
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		for (TextField f : OverlayRenderer.fields) f.render(context, true);
		context.drawCenteredTextWithShadow(this.textRenderer, "EDIT MODE: Drag to move, drag blue corner to resize, DEL to delete, ESC to exit", this.width / 2, 10, 0xFFFF00);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (keyCode == 256) { ConfigManager.save(); close(); return true; }
		if (keyCode == 261 && selectedField != null) {
			selectedField.cleanup(); OverlayRenderer.fields.remove(selectedField);
			selectedField = null; OverlayRenderer.selectedField = null; ConfigManager.save(); return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}
}
