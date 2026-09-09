package ru.yourname.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;

public class AddFieldScreen extends Screen {
	private TextFieldWidget urlField;
	private boolean isGif = false;

	public AddFieldScreen() { super(Text.literal("Add Overlay Field")); }

	@Override
	protected void init() {
		urlField = new TextFieldWidget(this.textRenderer, this.width / 2 - 100, this.height / 2 - 30, 200, 20, Text.literal("URL or File Path"));
		urlField.setMaxLength(256);
		this.addDrawableChild(urlField); this.setInitialFocus(urlField);

		this.addDrawableChild(ButtonWidget.builder(Text.literal("Add Image"), btn -> { isGif = false; addField(); }).dimensions(this.width / 2 - 100, this.height / 2 + 10, 95, 20).build());
		this.addDrawableChild(ButtonWidget.builder(Text.literal("Add GIF"), btn -> { isGif = true; addField(); }).dimensions(this.width / 2 + 5, this.height / 2 + 10, 95, 20).build());
	}

	private void addField() {
		String source = urlField.getText().trim();
		if (!source.isEmpty()) {
			OverlayRenderer.fields.add(new TextField(50, 50, 128, 128, source, isGif));
			ConfigManager.save(); close();
		}
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		this.renderBackground(context, mouseX, mouseY, delta);
		super.render(context, mouseX, mouseY, delta);
		context.drawCenteredTextWithShadow(this.textRenderer, "Enter URL or local file path:", this.width / 2, this.height / 2 - 50, 0xFFFFFF);
		urlField.render(context, mouseX, mouseY, delta);
	}

	@Override
	public boolean keyPressed(KeyInput input) {
		// ИСПРАВЛЕНО: input.key() возвращает int напрямую
		if (input.key() == 256) { close(); return true; }
		return super.keyPressed(input);
	}
}
