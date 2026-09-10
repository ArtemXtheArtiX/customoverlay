package ru.yourname.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;

public class AddFieldScreen extends Screen {
	private TextFieldWidget urlField;

	public AddFieldScreen() { 
		super(Text.literal("Add Overlay Field")); 
	}

	@Override
	protected void init() {
		// Поле для ввода URL или пути
		urlField = new TextFieldWidget(this.textRenderer, this.width / 2 - 100, this.height / 2 - 30, 200, 20, Text.literal("URL or File Path"));
		urlField.setMaxLength(256);
		this.addDrawableChild(urlField); 
		this.setInitialFocus(urlField);

		// Кнопка 1: Вызов окна выбора файла / Drag & Drop (как в оригинале)
		this.addDrawableChild(ButtonWidget.builder(Text.literal("Select File / D&D"), btn -> {
			openDragDropWindow();
		}).dimensions(this.width / 2 - 100, this.height / 2 + 10, 95, 20).build());

		// Кнопка 2: Подтверждение и добавление поля
		this.addDrawableChild(ButtonWidget.builder(Text.literal("Add Field"), btn -> {
			addField();
		}).dimensions(this.width / 2 + 5, this.height / 2 + 10, 95, 20).build());
	}

	private void openDragDropWindow() {
		// Здесь реализован базовый системный диалог выбора файла.
		// Если у тебя была своя кастомная логика Drag & Drop, ты можешь заменить этот блок на неё.
		try {
			java.awt.FileDialog fileDialog = new java.awt.FileDialog((java.awt.Frame) null, "Select Image or GIF", java.awt.FileDialog.LOAD);
			fileDialog.setVisible(true);
			if (fileDialog.getFile() != null) {
				String path = fileDialog.getDirectory() + fileDialog.getFile();
				// Заменяем обратные слеши на прямые для совместимости с Minecraft
				urlField.setText(path.replace("\\", "/"));
			}
		} catch (Exception e) {
			// Если AWT недоступен (например, в некоторых специфичных сборках Java), просто выводим в лог
			Customoverlay.LOGGER.warn("AWT File Dialog not supported in this environment: " + e.getMessage());
		}
	}

	private void addField() {
		String source = urlField.getText().trim();
		if (!source.isEmpty()) {
			// Автоматически определяем, гифка это или нет, по расширению файла
			boolean isGif = source.toLowerCase().endsWith(".gif");
			
			OverlayRenderer.fields.add(new TextField(50, 50, 128, 128, source, isGif));
			ConfigManager.save(); 
			close();
		}
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		// Рисуем полупрозрачный фон вручную, чтобы избежать краша с размытием
		context.fill(0, 0, this.width, this.height, 0x60000000);
		
		super.render(context, mouseX, mouseY, delta);
		context.drawCenteredTextWithShadow(this.textRenderer, "Enter URL, path, or use Select File:", this.width / 2, this.height / 2 - 50, 0xFFFFFF);
		urlField.render(context, mouseX, mouseY, delta);
	}

	@Override
	public boolean keyPressed(KeyInput input) {
		if (input.key() == 256) { close(); return true; }
		return super.keyPressed(input);
	}
}
