package ru.yourname.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import ru.yourname.Customoverlay;

import java.nio.file.Path;
import java.util.List;

public class AddFieldScreen extends Screen {
	private TextFieldWidget urlField;
	private String previewText = "Drag & Drop file here\nor type URL/path";

	public AddFieldScreen() { 
		super(Text.literal("Add Overlay Field")); 
	}

	@Override
	protected void init() {
		// Поле ввода
		urlField = new TextFieldWidget(this.textRenderer, this.width / 2 - 100, this.height / 2 + 20, 200, 20, Text.literal("URL or File Path"));
		urlField.setMaxLength(256);
		urlField.setChangedListener(this::onUrlChanged); // Обновляем предпросмотр при вводе
		this.addDrawableChild(urlField); 
		this.setInitialFocus(urlField);

		// Единственная кнопка подтверждения
		this.addDrawableChild(ButtonWidget.builder(Text.literal("Add Field"), btn -> {
			addField();
		}).dimensions(this.width / 2 - 100, this.height / 2 + 50, 200, 20).build());
	}

	private void onUrlChanged(String text) {
		if (text.trim().isEmpty()) {
			previewText = "Drag & Drop file here\nor type URL/path";
		} else {
			String lower = text.toLowerCase();
			String type = "Image";
			if (lower.endsWith(".gif")) type = "GIF Animation";
			else if (lower.endsWith(".webp")) type = "WebP Image";
			
			String name = text;
			if (name.contains("/")) name = name.substring(name.lastIndexOf("/") + 1);
			if (name.contains("\\")) name = name.substring(name.lastIndexOf("\\") + 1);
			if (name.length() > 22) name = name.substring(0, 19) + "...";
			
			previewText = "Ready to add:\n§f" + name + "\n§7(" + type + ")";
		}
	}

	private void addField() {
		String source = urlField.getText().trim();
		if (!source.isEmpty()) {
			boolean isGif = source.toLowerCase().endsWith(".gif");
			OverlayRenderer.fields.add(new TextField(50, 50, 128, 128, source, isGif));
			ConfigManager.save(); 
			close();
		}
	}

	// Встроенная поддержка Drag & Drop в Minecraft 1.21+
	@Override
	public void filesDropped(List<Path> paths) {
		if (paths != null && !paths.isEmpty()) {
			Path path = paths.get(0); // Берём первый перетащенный файл
			String fileName = path.getFileName().toString().toLowerCase();
			
			// Проверяем, что это изображение
			if (fileName.endsWith(".png") || fileName.endsWith(".jpg") || fileName.endsWith(".jpeg") || 
			    fileName.endsWith(".gif") || fileName.endsWith(".webp")) {
				String filePath = path.toString().replace("\\", "/");
				urlField.setText(filePath);
				onUrlChanged(filePath);
			} else {
				previewText = "§cUnsupported file type!\n§7Only images and GIFs";
			}
		}
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		// Тёмный полупрозрачный фон
		context.fill(0, 0, this.width, this.height, 0x60000000);
		
		super.render(context, mouseX, mouseY, delta);
		
		// Заголовок
		context.drawCenteredTextWithShadow(this.textRenderer, "Add New Overlay", this.width / 2, this.height / 2 - 90, 0xFFFFFF);

		// --- Область Drag & Drop / Предпросмотра ---
		int dropX = this.width / 2 - 100;
		int dropY = this.height / 2 - 70;
		int dropW = 200;
		int dropH = 70;
		
		// 1. Полупрозрачный фон зоны
		context.fill(dropX, dropY, dropX + dropW, dropY + dropH, 0x40888888);
		
		// 2. Серая обводка (рисуем 4 линии для чёткой рамки)
		int borderColor = 0xFFAAAAAA;
		context.fill(dropX, dropY, dropX + dropW, dropY + 1, borderColor);       // Верх
		context.fill(dropX, dropY + dropH - 1, dropX + dropW, dropY + dropH, borderColor); // Низ
		context.fill(dropX, dropY, dropX + 1, dropY + dropH, borderColor);       // Лево
		context.fill(dropX + dropW - 1, dropY, dropX + dropW, dropY + dropH, borderColor); // Право

		// 3. Текст внутри зоны (поддержка переноса строки через \n)
		String[] lines = previewText.split("\n");
		int textY = dropY + (dropH / 2) - ((lines.length * 10) / 2) + 2;
		for (String line : lines) {
			context.drawCenteredTextWithShadow(this.textRenderer, line, this.width / 2, textY, 0xFFFFFF);
			textY += 12;
		}

		// Рендер поля ввода и кнопки
		urlField.render(context, mouseX, mouseY, delta);
	}

	@Override
	public boolean keyPressed(KeyInput input) {
		if (input.key() == 256) { close(); return true; }
		return super.keyPressed(input);
	}
}
