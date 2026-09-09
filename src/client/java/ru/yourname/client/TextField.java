package ru.yourname.client;

import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import ru.yourname.Customoverlay;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class TextField {
	private static final int MAX_DIMENSION = 512;

	public int x, y, width, height;
	public String imageUrlOrPath;
	public boolean isGif;
	public float alpha = 1.0f;
	public float speed = 1.0f;

	private Identifier textureId;
	private List<Identifier> gifTextureIds;
	private List<Integer> frameDelays;
	private int currentFrame = 0;
	private long lastFrameTime = 0;
	private boolean isLoaded = false;

	public TextField(int x, int y, int width, int height, String urlOrPath, boolean isGif) {
		this.x = x; this.y = y; this.width = width; this.height = height;
		this.imageUrlOrPath = urlOrPath; this.isGif = isGif;
		loadImageAsync();
	}

	private void loadImageAsync() {
		new Thread(() -> {
			try {
				InputStream is = imageUrlOrPath.startsWith("http") ? new URL(imageUrlOrPath).openStream() : new java.io.FileInputStream(imageUrlOrPath);
				if (isGif) loadGif(is); else loadStatic(is);
				isLoaded = true;
			} catch (Exception e) { Customoverlay.LOGGER.error("Failed to load: " + imageUrlOrPath, e); }
		}).start();
	}

	private void loadStatic(InputStream is) throws Exception {
		NativeImage originalImg = NativeImage.read(is);
		// ИСПРАВЛЕНО: используем final переменную, чтобы она была effectively final для лямбды
		final NativeImage finalImg;
		if (originalImg.getWidth() > MAX_DIMENSION || originalImg.getHeight() > MAX_DIMENSION) {
			finalImg = resizeImage(originalImg, MAX_DIMENSION, MAX_DIMENSION);
			originalImg.close(); // Освобождаем память оригинала, если он был уменьшен
		} else {
			finalImg = originalImg;
		}
		
		MinecraftClient.getInstance().execute(() -> {
			NativeImageBackedTexture tex = new NativeImageBackedTexture(() -> "customoverlay", finalImg);
			textureId = Identifier.of("customoverlay", "static_" + imageUrlOrPath.hashCode());
			MinecraftClient.getInstance().getTextureManager().registerTexture(textureId, tex);
		});
	}

	private void loadGif(InputStream is) throws Exception {
		ImageInputStream iis = ImageIO.createImageInputStream(is);
		ImageReader reader = ImageIO.getImageReadersByFormatName("gif").next();
		reader.setInput(iis);
		int numFrames = reader.getNumImages(true);
		MinecraftClient.getInstance().execute(() -> {
			gifTextureIds = new ArrayList<>(); 
			frameDelays = new ArrayList<>();
			for (int i = 0; i < numFrames; i++) {
				try {
					java.awt.image.BufferedImage bImg = reader.read(i);
					NativeImage originalImg = NativeImage.read(new java.io.ByteArrayOutputStream() {{ 
						ImageIO.write(bImg, "png", this); 
					}}.toByteArray());
					
					// ИСПРАВЛЕНО: используем final переменную для лямбды
					final NativeImage finalImg;
					if (originalImg.getWidth() > MAX_DIMENSION || originalImg.getHeight() > MAX_DIMENSION) {
						finalImg = resizeImage(originalImg, MAX_DIMENSION, MAX_DIMENSION);
						originalImg.close();
					} else {
						finalImg = originalImg;
					}
					
					NativeImageBackedTexture tex = new NativeImageBackedTexture(() -> "customoverlay", finalImg);
					Identifier id = Identifier.of("customoverlay", "gif_" + imageUrlOrPath.hashCode() + "_" + i);
					MinecraftClient.getInstance().getTextureManager().registerTexture(id, tex);
					gifTextureIds.add(id); 
					frameDelays.add(100);
				} catch (Exception e) { 
					Customoverlay.LOGGER.warn("Skipped frame " + i); 
				}
			}
			reader.dispose();
		});
	}

	private NativeImage resizeImage(NativeImage img, int maxW, int maxH) {
		float scale = Math.min((float) maxW / img.getWidth(), (float) maxH / img.getHeight());
		if (scale >= 1.0f) return img;
		int newW = (int) (img.getWidth() * scale), newH = (int) (img.getHeight() * scale);
		NativeImage resized = new NativeImage(NativeImage.Format.RGBA, newW, newH, false);
		for (int y = 0; y < newH; y++) {
			for (int x = 0; x < newW; x++) {
				resized.setColorArgb(x, y, img.getColorArgb((int)(x / scale), (int)(y / scale)));
			}
		}
		return resized;
	}

	public void render(DrawContext context, boolean editMode) {
		if (!isLoaded) return;
		
		if (isGif && gifTextureIds != null && !gifTextureIds.isEmpty()) {
			long now = System.currentTimeMillis();
			if (now - lastFrameTime >= (frameDelays.get(currentFrame) / speed)) {
				currentFrame = (currentFrame + 1) % gifTextureIds.size(); 
				lastFrameTime = now;
			}
			Identifier currentId = gifTextureIds.get(currentFrame);
			context.drawTexture(RenderPipelines.GUI_TEXTURED, currentId, x, y, 0.0f, 0.0f, width, height, width, height);
		} else if (textureId != null) {
			context.drawTexture(RenderPipelines.GUI_TEXTURED, textureId, x, y, 0.0f, 0.0f, width, height, width, height);
		}
		
		if (editMode && OverlayRenderer.selectedField == this) {
			context.fill(x - 2, y - 2, x + width + 2, y + height + 2, 0x80FFFF00);
			context.fill(x + width - 8, y + height - 8, x + width, y + height, 0xFF0088FF);
		}
	}

	public boolean isMouseOver(double mouseX, double mouseY) { 
		return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height; 
	}
	
	public boolean isOverResizeCorner(double mouseX, double mouseY) { 
		return mouseX >= x + width - 8 && mouseX <= x + width && mouseY >= y + height - 8 && mouseY <= y + height; 
	}

	public void cleanup() {
		gifTextureIds = null;
		textureId = null;
	}
}
