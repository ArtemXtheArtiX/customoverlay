package ru.yourname.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;

import java.nio.file.Path;
import java.util.List;

public class AddFieldScreen extends Screen {
	private TextFieldWidget urlField;
	private String previewText = "Drag & Drop file here\nor paste URL/path (Ctrl+V)";

	public AddFieldScreen() { 
		super(Text.literal("Add Overlay Field")); 
	}

	@Override
	protected void init() {
		urlField = new TextFieldWidget(this.textRenderer, this.width / 2 - 100, this.height / 2 + 20, 200, 20, Text.literal("URL or File Path"));
		urlField.setMaxLength(256);
		urlField.setChangedListener(this::onUrlChanged);
		this.addDrawableChild(urlField); 
		this.setInitialFocus(urlField);

		this.addDrawableChild(ButtonWidget.builder(Text.literal("Add Field"), btn -> {
			addField();
		}).dimensions(this.width / 2 - 100, this.height / 2 + 50, 200, 20).build());
	}

	private void onUrlChanged(String text) {
		if (text.trim().isEmpty()) {
			previewText = "Drag & Drop file here\nor paste URL/path (Ctrl+V)";
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

	// ИСПРАВЛЕНО: правильное имя метода onFilesDropped и возврат void
	@Override
	public void onFilesDropped(List<Path> paths) {
		if (paths != null && !paths.isEmpty()) {
			Path path = paths.get(0);
			String fileName = path.getFileName().toString().toLowerCase();
			
			if (fileName.endsWith(".png") || fileName.endsWith(".jpg") || fileName.endsWith(".jpeg") || 
			    fileName.endsWith(".gif") || fileName.endsWith(".webp")) {
				// Используем абсолютный путь, чтобы Minecraft точно нашёл файл
				String filePath = path.toAbsolutePath().toString().replace("\\", "/");
				urlField.setText(filePath);
				onUrlChanged(filePath);
			} else {
				previewText = "§cUnsupported file type!\n§7Only images and GIFs";
			}
		}
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		context.fill(0, 0, this.width, this.height, 0x60000000);
		super.render(context, mouseX, mouseY, delta);
		
		context.drawCenteredTextWithShadow(this.textRenderer, "Add New Overlay", this.width / 2, this.height / 2 - 90, 0xFFFFFF);

		// Область Drag & Drop / Предпросмотра
		int dropX = this.width / 2 - 100;
		int dropY = this.height / 2 - 70;
		int dropW = 200;
		int dropH = 70;
		
		context.fill(dropX, dropY, dropX + dropW, dropY + dropH, 0x40888888);
		
		int borderColor = 0xFFAAAAAA;
		context.fill(dropX, dropY, dropX + dropW, dropY + 1, borderColor);
		context.fill(dropX, dropY + dropH - 1, dropX + dropW, dropY + dropH, borderColor);
		context.fill(dropX, dropY, dropX + 1, dropY + dropH, borderColor);
		context.fill(dropX + dropW - 1, dropY, dropX + dropW, dropY + dropH, borderColor);

		String[] lines = previewText.split("\n");
		int textY = dropY + (dropH / 2) - ((lines.length * 10) / 2) + 2;
		for (String line : lines) {
			context.drawCenteredTextWithShadow(this.textRenderer, line, this.width / 2, textY, 0xFFFFFF);
			textY += 12;
		}

		urlField.render(context, mouseX, mouseY, delta);
	}

	@Override
	public boolean keyPressed(KeyInput input) {
		if (input.key() == 256) { close(); return true; }
		return super.keyPressed(input);
	}
}
