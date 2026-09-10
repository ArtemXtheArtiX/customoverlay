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
	private TextField previewField;
	private String hintMessage = "Drag & Drop file here!\n(or paste URL/path)";

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
		if (previewField != null) {
			previewField.cleanup();
			previewField = null;
		}

		if (text.trim().isEmpty()) {
			hintMessage = "Drag & Drop file here!\n(or paste URL/path)";
		} else {
			hintMessage = "";
			boolean isGif = text.toLowerCase().endsWith(".gif");
			// Квадратный предпросмотр 120x120 с сохранением пропорций
			previewField = new TextField(this.width / 2 - 60, this.height / 2 - 60, 120, 120, text, isGif);
			previewField.keepAspect = true; // Включаем сохранение пропорций
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

	@Override
	public void onFilesDropped(List<Path> paths) {
		if (paths != null && !paths.isEmpty()) {
			Path path = paths.get(0);
			String fileName = path.getFileName().toString().toLowerCase();
			
			if (fileName.endsWith(".png") || fileName.endsWith(".jpg") || fileName.endsWith(".jpeg") || 
			    fileName.endsWith(".gif") || fileName.endsWith(".webp")) {
				String filePath = path.toAbsolutePath().toString().replace("\\", "/");
				urlField.setText(filePath);
				onUrlChanged(filePath);
			} else {
				hintMessage = "§cUnsupported file type!\n§7Only images and GIFs";
				if (previewField != null) { previewField.cleanup(); previewField = null; }
			}
		}
	}

	@Override
	public void removed() {
		if (previewField != null) previewField.cleanup();
		super.removed();
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		context.fill(0, 0, this.width, this.height, 0x60000000);
		super.render(context, mouseX, mouseY, delta);
		
		context.drawCenteredTextWithShadow(this.textRenderer, "Add New Overlay", this.width / 2, this.height / 2 - 90, 0xFFFFFF);

		int dropX = this.width / 2 - 100;
		int dropY = this.height / 2 - 70;
		int dropW = 200;
		int dropH = 70;
		
		context.fill(dropX, dropY, dropX + dropW, dropY + dropH, 0x30888888);
		
		int borderColor = 0xFF55FF55;
		context.fill(dropX, dropY, dropX + dropW, dropY + 2, borderColor);
		context.fill(dropX, dropY + dropH - 2, dropX + dropW, dropY + dropH, borderColor);
		context.fill(dropX, dropY, dropX + 2, dropY + dropH, borderColor);
		context.fill(dropX + dropW - 2, dropY, dropX + dropW, dropY + dropH, borderColor);

		if (previewField != null) {
			previewField.render(context, false);
		} else {
			String[] lines = hintMessage.split("\n");
			int textY = dropY + (dropH / 2) - ((lines.length * 10) / 2) + 2;
			for (String line : lines) {
				context.drawCenteredTextWithShadow(this.textRenderer, line, this.width / 2, textY, 0xFFFFFF);
				textY += 12;
			}
		}

		urlField.render(context, mouseX, mouseY, delta);
	}

	@Override
	public boolean keyPressed(KeyInput input) {
		if (input.key() == 256) { close(); return true; }
		return super.keyPressed(input);
	}
}
