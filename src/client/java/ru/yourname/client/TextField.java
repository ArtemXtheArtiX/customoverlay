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
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.awt.*;
import java.awt.image.BufferedImage;
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
	
	public int originalWidth = 0, originalHeight = 0;
	public boolean keepAspect = false;
	public boolean isPreview = false; // НОВОЕ: для отключения анимации в предпросмотре

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

	private NativeImage bufferedImageToNative(BufferedImage bImg) {
		int w = bImg.getWidth();
		int h = bImg.getHeight();
		NativeImage nativeImg = new NativeImage(NativeImage.Format.RGBA, w, h, false);
		for (int py = 0; py < h; py++) {
			for (int px = 0; px < w; px++) {
				nativeImg.setColorArgb(px, py, bImg.getRGB(px, py));
			}
		}
		return nativeImg;
	}

	private void loadStatic(InputStream is) throws Exception {
		String lower = imageUrlOrPath.toLowerCase();
		NativeImage originalImg;
		
		if (lower.endsWith(".png")) {
			originalImg = NativeImage.read(is);
		} else {
			BufferedImage bImg = ImageIO.read(is);
			if (bImg == null) throw new Exception("Unsupported image format");
			originalImg = bufferedImageToNative(bImg);
		}
		
		originalWidth = originalImg.getWidth();
		originalHeight = originalImg.getHeight();
		
		final NativeImage finalImg;
		if (originalImg.getWidth() > MAX_DIMENSION || originalImg.getHeight() > MAX_DIMENSION) {
			finalImg = resizeImage(originalImg, MAX_DIMENSION, MAX_DIMENSION);
			originalImg.close();
		} else {
			finalImg = originalImg;
		}
		MinecraftClient.getInstance().execute(() -> {
			NativeImageBackedTexture tex = new NativeImageBackedTexture(() -> "customoverlay", finalImg);
			textureId = Identifier.of("customoverlay", "static_" + imageUrlOrPath.hashCode());
			MinecraftClient.getInstance().getTextureManager().registerTexture(textureId, tex);
			
			if (keepAspect && originalWidth > 0 && originalHeight > 0) {
				adjustSizeToAspect();
			}
		});
	}

	// ИСПРАВЛЕНО: правильная логика декодирования GIF с наложением кадров и disposal methods
	private void loadGif(InputStream is) throws Exception {
		ImageInputStream iis = ImageIO.createImageInputStream(is);
		ImageReader reader = ImageIO.getImageReadersByFormatName("gif").next();
		reader.setInput(iis);
		int numFrames = reader.getNumImages(true);
		
		// Первый кадр для определения размера холста
		BufferedImage firstFrame = reader.read(0);
		int canvasWidth = firstFrame.getWidth();
		int canvasHeight = firstFrame.getHeight();
		
		originalWidth = canvasWidth;
		originalHeight = canvasHeight;
		
		// Массивы для хранения метаданных каждого кадра
		int[] frameDelaysRaw = new int[numFrames];
		int[] disposalMethods = new int[numFrames];
		
		// Читаем метаданные всех кадров
		for (int i = 0; i < numFrames; i++) {
			IIOMetadata meta = reader.getImageMetadata(i);
			String metaFormat = meta.getNativeMetadataFormatName();
			if ("javax_imageio_gif_image_1.0".equals(metaFormat)) {
				Node tree = meta.getAsTree(metaFormat);
				NodeList children = tree.getChildNodes();
				for (int j = 0; j < children.getLength(); j++) {
					Node node = children.item(j);
					if ("GraphicControlExtension".equals(node.getNodeName())) {
						NodeList attrs = node.getChildNodes();
						for (int k = 0; k < attrs.getLength(); k++) {
							Node attr = attrs.item(k);
							if ("delayTime".equals(attr.getNodeName())) {
								frameDelaysRaw[i] = Integer.parseInt(attr.getAttributes().getNamedItem("value").getNodeValue()) * 10; // в миллисекунды
							}
							if ("disposalMethod".equals(attr.getNodeName())) {
								disposalMethods[i] = Integer.parseInt(attr.getAttributes().getNamedItem("value").getNodeValue());
							}
						}
					}
				}
			}
			if (frameDelaysRaw[i] == 0) frameDelaysRaw[i] = 100; // дефолтная задержка
		}
		
		// Создаём холст и последовательно накладываем кадры
		BufferedImage currentCanvas = new BufferedImage(canvasWidth, canvasHeight, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = currentCanvas.createGraphics();
		
		List<BufferedImage> fullFrames = new ArrayList<>();
		
		for (int i = 0; i < numFrames; i++) {
			BufferedImage partial = reader.read(i);
			if (partial == null) continue;
			
			// Применяем disposal method предыдущего кадра
			if (i > 0 && disposalMethods[i - 1] == 2) {
				// Restore to background: очищаем холст
				g.setComposite(AlphaComposite.Clear);
				g.fillRect(0, 0, canvasWidth, canvasHeight);
				g.setComposite(AlphaComposite.SrcOver);
			}
			
			// Рисуем текущий кадр на холст
			g.drawImage(partial, 0, 0, null);
			
			// Копируем текущее состояние холста как итоговый кадр
			BufferedImage copy = new BufferedImage(canvasWidth, canvasHeight, BufferedImage.TYPE_INT_ARGB);
			Graphics2D copyG = copy.createGraphics();
			copyG.drawImage(currentCanvas, 0, 0, null);
			copyG.dispose();
			fullFrames.add(copy);
		}
		g.dispose();
		reader.dispose();
		
		// Конвертируем в текстуры
		MinecraftClient.getInstance().execute(() -> {
			gifTextureIds = new ArrayList<>();
			frameDelays = new ArrayList<>();
			
			for (int i = 0; i < fullFrames.size(); i++) {
				try {
					BufferedImage bImg = fullFrames.get(i);
					NativeImage originalImg = bufferedImageToNative(bImg);
					
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
					frameDelays.add(frameDelaysRaw[i]);
				} catch (Exception e) {
					Customoverlay.LOGGER.warn("Skipped frame " + i);
				}
			}
			
			if (keepAspect && originalWidth > 0 && originalHeight > 0) {
				adjustSizeToAspect();
			}
		});
	}

	private void adjustSizeToAspect() {
		float aspect = (float) originalWidth / originalHeight;
		if (aspect > 1) {
			height = (int) (width / aspect);
		} else {
			width = (int) (height * aspect);
		}
	}

	private NativeImage resizeImage(NativeImage img, int maxW, int maxH) {
		float scaleX = (float) maxW / img.getWidth();
		float scaleY = (float) maxH / img.getHeight();
		float scale = Math.min(scaleX, scaleY);
		
		if (scale >= 1.0f) return img;
		
		int newW = (int) (img.getWidth() * scale);
		int newH = (int) (img.getHeight() * scale);
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
		
		int color = ((int)(this.alpha * 255) << 24) | 0x00FFFFFF;

		if (isGif && gifTextureIds != null && !gifTextureIds.isEmpty()) {
			// ИСПРАВЛЕНО: в предпросмотре не проигрываем анимацию, показываем только первый кадр
			if (!isPreview) {
				long now = System.currentTimeMillis();
				if (now - lastFrameTime >= (frameDelays.get(currentFrame) / speed)) {
					currentFrame = (currentFrame + 1) % gifTextureIds.size(); 
					lastFrameTime = now;
				}
			} else {
				currentFrame = 0; // Всегда первый кадр в предпросмотре
			}
			
			Identifier currentId = gifTextureIds.get(currentFrame);
			context.drawTexture(RenderPipelines.GUI_TEXTURED, currentId, x, y, 0.0f, 0.0f, width, height, width, height, color);
		} else if (textureId != null) {
			context.drawTexture(RenderPipelines.GUI_TEXTURED, textureId, x, y, 0.0f, 0.0f, width, height, width, height, color);
		}
		
		if (editMode && OverlayRenderer.selectedField == this) {
			int bx = x - 2, by = y - 2, bw = width + 4, bh = height + 4;
			int c = 0xFFFFFF00;
			context.fill(bx, by, bx + bw, by + 1, c);
			context.fill(bx, by + bh - 1, bx + bw, by + bh, c);
			context.fill(bx, by, bx + 1, by + bh, c);
			context.fill(bx + bw - 1, by, bx + bw, by + bh, c);
			
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
