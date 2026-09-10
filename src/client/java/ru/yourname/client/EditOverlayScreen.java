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

	private AlphaSlider alphaSlider = null;
	private SpeedSlider speedSlider = null;

	public EditOverlayScreen() { super(Text.literal("Edit Overlay")); }

	@Override
	public boolean mouseClicked(Click click, boolean doubleClick) {
		double mouseX = click.x();
		double mouseY = click.y();

		// 1. Проверка клика по списку слева
		int listWidth = 140, listHeight = this.height / 2, listTop = 20;
		if (mouseX >= 5 && mouseX <= 5 + listWidth && mouseY >= listTop && mouseY <= listTop + listHeight) {
			int idx = (int)((mouseY - (listTop + 20)) / 14);
			if (idx >= 0 && idx < OverlayRenderer.fields.size()) {
				selectedField = OverlayRenderer.fields.get(idx);
				OverlayRenderer.selectedField = selectedField;
				alphaSlider = null; speedSlider = null;
				return true;
			} else {
				selectedField = null; OverlayRenderer.selectedField = null;
				alphaSlider = null; speedSlider = null;
				return true;
			}
		}

		// 2. Проверка слайдеров
		if (selectedField != null) {
			if (alphaSlider != null && alphaSlider.isMouseOver(mouseX, mouseY)) {
				alphaSlider.mousePressed(mouseX, mouseY); return true;
			}
			if (speedSlider != null && selectedField.isGif && speedSlider.isMouseOver(mouseX, mouseY)) {
				speedSlider.mousePressed(mouseX, mouseY); return true;
			}
		}

		// 3. Проверка полей
		if (click.button() == 0) {
			for (int i = OverlayRenderer.fields.size() - 1; i >= 0; i--) {
				TextField f = OverlayRenderer.fields.get(i);
				if (f.isMouseOver(mouseX, mouseY)) {
					selectedField = f; OverlayRenderer.selectedField = f;
					alphaSlider = null; speedSlider = null;

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
			alphaSlider = null; speedSlider = null;
		}
		return super.mouseClicked(click, doubleClick);
	}

	@Override
	public boolean mouseDragged(Click click, double deltaX, double deltaY) {
		double mouseX = click.x();
		double mouseY = click.y();

		if (alphaSlider != null && alphaSlider.dragging) { alphaSlider.mouseDragged(mouseX, mouseY); return true; }
		if (speedSlider != null && speedSlider.dragging && selectedField != null && selectedField.isGif) { speedSlider.mouseDragged(mouseX, mouseY); return true; }

		if (selectedField != null) {
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
		if (alphaSlider != null) alphaSlider.mouseReleased();
		if (speedSlider != null) speedSlider.mouseReleased();
		dragging = false; resizing = false;
		if (selectedField != null) ConfigManager.save();
		return super.mouseReleased(click);
	}

	private void updateSliderPositions() {
		if (selectedField == null) return;
		int sliderWidth = 100;
		int sliderX = selectedField.x + (selectedField.width / 2) - (sliderWidth / 2);
		int sliderY = selectedField.y + selectedField.height + 10;
		if (alphaSlider != null) { alphaSlider.x = sliderX; alphaSlider.y = sliderY; }
		if (speedSlider != null) { speedSlider.x = sliderX; speedSlider.y = sliderY + 20; }
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		for (TextField f : OverlayRenderer.fields) f.render(context, true);

		// --- Отрисовка списка слева ---
		int listWidth = 140, listHeight = this.height / 2, listTop = 20;
		context.fill(5, listTop, 5 + listWidth, listTop + listHeight, 0xDD000000);
		context.fill(5 + listWidth, listTop, 6 + listWidth, listTop + listHeight, 0xFFFFFFFF);
		
		context.drawText(textRenderer, "Active Fields:", 10, listTop + 5, 0xFFFFAA, true);
		
		int y = listTop + 20;
		for (int i = 0; i < OverlayRenderer.fields.size(); i++) {
			if (y + 14 > listTop + listHeight) break;
			TextField f = OverlayRenderer.fields.get(i);
			
			String name = f.imageUrlOrPath;
			if (name.contains("/")) name = name.substring(name.lastIndexOf("/") + 1);
			if (name.contains("\\")) name = name.substring(name.lastIndexOf("\\") + 1);
			if (name.length() > 15) name = name.substring(0, 12) + "...";
			
			int color = (f == selectedField) ? 0x55FF55 : 0xFFFFFF;
			context.drawText(textRenderer, "📄 " + name, 10, y, color, true);
			y += 14;
		}

		// --- Отрисовка слайдеров со значениями ---
		if (selectedField != null) {
			if (alphaSlider == null) alphaSlider = new AlphaSlider(0, 0, selectedField.alpha);
			if (speedSlider == null && selectedField.isGif) speedSlider = new SpeedSlider(0, 0, selectedField.speed);
			updateSliderPositions();
			alphaSlider.draw(context, mouseX, mouseY, this.width);
			if (speedSlider != null && selectedField.isGif) speedSlider.draw(context, mouseX, mouseY, this.width);
		}

		context.drawCenteredTextWithShadow(this.textRenderer, "EDIT MODE: Drag to move, drag blue corner to resize, DEL to delete, ESC to exit", this.width / 2, 10, 0xFFFF00);
	}

	@Override
	public boolean keyPressed(KeyInput input) {
		if (input.key() == 256) { ConfigManager.save(); close(); return true; }
		if (input.key() == 261 && selectedField != null) {
			selectedField.cleanup(); OverlayRenderer.fields.remove(selectedField);
			selectedField = null; OverlayRenderer.selectedField = null; 
			alphaSlider = null; speedSlider = null;
			ConfigManager.save(); return true;
		}
		return super.keyPressed(input);
	}

	// --- Встроенные классы слайдеров ---
	private class AlphaSlider {
		int x, y, width = 100, height = 10; 
		float value; 
		boolean dragging;

		AlphaSlider(int x, int y, float initial) { 
			this.x = x; this.y = y; 
			this.value = Math.max(0f, Math.min(1f, initial)); 
		}

		void draw(DrawContext context, int mouseX, int mouseY, int screenWidth) {
			context.fill(x, y, x + width, y + height, 0xFF888888);
			context.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0xFF444444);
			int sliderX = x + (int) (value * (width - 6));
			context.fill(sliderX, y + 1, sliderX + 6, y + height - 1, 0xFFAAAAAA);
			
			// ГАРАНТИРОВАННЫЙ вывод значения
			String text = String.format("Alpha: %.0f%%", value * 100);
			int textX = x + width + 6;
			if (textX + textRenderer.getWidth(text) > screenWidth - 5) {
				textX = x - textRenderer.getWidth(text) - 6; // Если не влезает справа, рисуем слева
			}
			context.drawText(textRenderer, text, textX, y, 0xFFFFFF, true);
		}

		boolean isMouseOver(double mx, double my) { return mx >= x && mx <= x + width && my >= y && my <= y + height; }
		void mousePressed(double mx, double my) { dragging = true; updateValue(mx); }
		void mouseDragged(double mx, double my) { if (dragging) updateValue(mx); }
		void mouseReleased() { dragging = false; }
		
		private void updateValue(double mx) {
			float newValue = Math.max(0f, Math.min(1f, (float) (mx - x) / width));
			if (value != newValue) { 
				value = newValue; 
				if (selectedField != null) selectedField.alpha = value; 
			}
		}
	}

	private class SpeedSlider {
		int x, y, width = 100, height = 10; 
		float value; 
		boolean dragging;

		SpeedSlider(int x, int y, float initialSpeed) { 
			this.x = x; this.y = y; 
			this.value = mapSpeedToLinear(initialSpeed); 
		}

		private float mapSpeedToLinear(float speed) { return (speed - 0.5f) / 1.5f; }
		private float mapLinearToSpeed(float linear) { return 0.5f + linear * 1.5f; }

		void draw(DrawContext context, int mouseX, int mouseY, int screenWidth) {
			context.fill(x, y, x + width, y + height, 0xFF888888);
			context.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0xFF444444);
			int sliderX = x + (int) (value * (width - 6));
			context.fill(sliderX, y + 1, sliderX + 6, y + height - 1, 0xFFAAAAAA);
			
			// ГАРАНТИРОВАННЫЙ вывод значения
			String text = String.format("Speed: %.1fx", mapLinearToSpeed(value));
			int textX = x + width + 6;
			if (textX + textRenderer.getWidth(text) > screenWidth - 5) {
				textX = x - textRenderer.getWidth(text) - 6; // Если не влезает справа, рисуем слева
			}
			context.drawText(textRenderer, text, textX, y, 0xFFFFFF, true);
		}

		boolean isMouseOver(double mx, double my) { return mx >= x && mx <= x + width && my >= y && my <= y + height; }
		void mousePressed(double mx, double my) { dragging = true; updateValue(mx); }
		void mouseDragged(double mx, double my) { if (dragging) updateValue(mx); }
		void mouseReleased() { dragging = false; }
		
		private void updateValue(double mx) {
			float newLinear = Math.max(0f, Math.min(1f, (float) (mx - x) / width));
			if (value != newLinear) {
				value = newLinear;
				if (selectedField != null && selectedField.isGif) selectedField.speed = mapLinearToSpeed(value);
			}
		}
	}
}
