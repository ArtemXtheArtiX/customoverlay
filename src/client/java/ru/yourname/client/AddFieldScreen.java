package ru.yourname.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.fabricmc.loader.api.FabricLoader;
import ru.yourname.Customoverlay;

import java.awt.Desktop;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class AddFieldScreen extends Screen {
	private TextFieldWidget urlField;
	private final Path imageFolder = FabricLoader.getInstance().getConfigDir().resolve("customoverlay_images");
	private List<String> availableFiles = new ArrayList<>();

	public AddFieldScreen() { 
		super(Text.literal("Add Overlay Field")); 
	}

	@Override
	protected void init() {
		ensureFolderExists();
		loadFiles();

		urlField = new TextFieldWidget(this.textRenderer, this.width / 2 - 100, this.height / 2 - 40, 200, 20, Text.literal("URL or filename"));
		urlField.setMaxLength(256);
		this.addDrawableChild(urlField); 
		this.setInitialFocus(urlField);

		// Кнопка открытия папки
		this.addDrawableChild(ButtonWidget.builder(Text.literal("📁 Open Folder"), btn -> {
			openFolder();
		}).dimensions(this.width / 2 - 100, this.height / 2 - 10, 95, 20).build());

		// Кнопка добавления
		this.addDrawableChild(ButtonWidget.builder(Text.literal("Add Field"), btn -> {
			addField();
		}).dimensions(this.width / 2 + 5, this.height / 2 - 10, 95, 20).build());
	}

	private void ensureFolderExists() {
		try {
			if (!Files.exists(imageFolder)) {
				Files.createDirectories(imageFolder);
			}
		} catch (Exception e) {
			Customoverlay.LOGGER.error("Failed to create image folder", e);
		}
	}

	private void loadFiles() {
		availableFiles.clear();
		try {
			Files.newDirectoryStream(imageFolder, path -> {
				String name = path.getFileName().toString().toLowerCase();
				return name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".gif") || name.endsWith(".webp");
			}).forEach(path -> availableFiles.add(path.getFileName().toString()));
		} catch (Exception e) {
			Customoverlay.LOGGER.error("Failed to read image folder", e);
		}
	}

	private void openFolder() {
		try {
			Desktop.getDesktop().open(imageFolder.toFile());
		} catch (Exception e) {
			Customoverlay.LOGGER.warn("Could not open folder: " + e.getMessage());
		}
	}

	private void addField() {
		String source = urlField.getText().trim();
		if (!source.isEmpty()) {
			// Если введено только имя файла без пути, подставляем путь к нашей папке
			if (!source.contains("/") && !source.contains("\\") && !source.startsWith("http")) {
				source = imageFolder.resolve(source).toString().replace("\\", "/");
			}
			
			boolean isGif = source.toLowerCase().endsWith(".gif");
			OverlayRenderer.fields.add(new TextField(50, 50, 128, 128, source, isGif));
			ConfigManager.save(); 
			close();
		}
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		context.fill(0, 0, this.width, this.height, 0x60000000);
		super.render(context, mouseX, mouseY, delta);
		
		context.drawCenteredTextWithShadow(this.textRenderer, "Enter URL, filename, or click Open Folder:", this.width / 2, this.height / 2 - 60, 0xFFFFFF);
		urlField.render(context, mouseX, mouseY, delta);

		// Отрисовка списка доступных файлов
		int listX = this.width / 2 - 100;
		int listY = this.height / 2 + 20;
		context.drawText(this.textRenderer, "Files in folder:", listX, listY, 0xAAAAAA, false);
		
		int y = listY + 12;
		for (int i = 0; i < Math.min(5, availableFiles.size()); i++) {
			String fileName = availableFiles.get(i);
			int color = 0x55FF55; // Зелёный цвет для кликабельных файлов
			if (mouseX >= listX && mouseX <= listX + 200 && mouseY >= y && mouseY <= y + 10) {
				color = 0xFFFF55; // Жёлтый при наведении
				if (mouseX >= listX && mouseX <= listX + 200 && mouseY >= y && mouseY <= y + 10 && mouseY >= listY) {
					// Клик по файлу вписывает его в поле
					if (org.lwjgl.glfw.GLFW.glfwGetMouseButton(net.minecraft.client.MinecraftClient.getInstance().getWindow().getHandle(), org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS) {
						urlField.setText(fileName);
					}
				}
			}
			context.drawText(this.textRenderer, "• " + fileName, listX, y, color, false);
			y += 12;
		}
		if (availableFiles.size() > 5) {
			context.drawText(this.textRenderer, "... and " + (availableFiles.size() - 5) + " more", listX, y, 0x888888, false);
		}
	}

	@Override
	public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean doubleClick) {
		// Обработка клика по списку файлов вручную, так как это не виджет
		int listX = this.width / 2 - 100;
		int listY = this.height / 2 + 32;
		double mouseY = click.y();
		double mouseX = click.x();
		
		if (mouseX >= listX && mouseX <= listX + 200 && mouseY >= listY) {
			int index = (int) ((mouseY - listY) / 12);
			if (index >= 0 && index < availableFiles.size()) {
				urlField.setText(availableFiles.get(index));
				return true;
			}
		}
		return super.mouseClicked(click, doubleClick);
	}

	@Override
	public boolean keyPressed(KeyInput input) {
		if (input.key() == 256) { close(); return true; }
		return super.keyPressed(input);
	}
}
