package ru.yourname.client;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public final class GifDecoder {
    
    public static GifImage read(InputStream in) throws IOException {
        GifImage image = new GifImage();
        image.read(in);
        return image;
    }

    public static class GifImage {
        private int width;
        private int height;
        private List<GifFrame> frames = new ArrayList<>();

        public int getWidth() { return width; }
        public int getHeight() { return height; }
        public int getFrameCount() { return frames.size(); }
        
        public BufferedImage getFrame(int i) {
            return frames.get(i).image;
        }
        
        public int getDelay(int i) {
            return frames.get(i).delay;
        }

        private void read(InputStream is) throws IOException {
            javax.imageio.ImageReader reader = javax.imageio.ImageIO.getImageReadersByFormatName("gif").next();
            javax.imageio.stream.ImageInputStream iis = javax.imageio.ImageIO.createImageInputStream(is);
            reader.setInput(iis);
            
            int numFrames = reader.getNumImages(true);
            if (numFrames == 0) return;
            
            BufferedImage first = reader.read(0);
            width = first.getWidth();
            height = first.getHeight();
            
            // Холст для накопления кадров
            BufferedImage canvas = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = canvas.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            
            // Сохраняем состояние холста для disposal method 3
            BufferedImage previousCanvas = null;
            
            for (int i = 0; i < numFrames; i++) {
                // Читаем метаданные текущего кадра
                int disposalMethod = 0;
                int delay = 100;
                
                try {
                    javax.imageio.metadata.IIOMetadata meta = reader.getImageMetadata(i);
                    String format = meta.getNativeMetadataFormatName();
                    if ("javax_imageio_gif_image_1.0".equals(format)) {
                        org.w3c.dom.Node tree = meta.getAsTree(format);
                        org.w3c.dom.NodeList children = tree.getChildNodes();
                        for (int j = 0; j < children.getLength(); j++) {
                            org.w3c.dom.Node node = children.item(j);
                            if ("GraphicControlExtension".equals(node.getNodeName())) {
                                org.w3c.dom.NodeList attrs = node.getChildNodes();
                                for (int k = 0; k < attrs.getLength(); k++) {
                                    org.w3c.dom.Node attr = attrs.item(k);
                                    if ("disposalMethod".equals(attr.getNodeName())) {
                                        disposalMethod = Integer.parseInt(attr.getAttributes().getNamedItem("value").getNodeValue());
                                    }
                                    if ("delayTime".equals(attr.getNodeName())) {
                                        delay = Integer.parseInt(attr.getAttributes().getNamedItem("value").getNodeValue()) * 10;
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    // ignore
                }
                
                if (delay == 0) delay = 100;
                
                // Применяем disposal method предыдущего кадра
                if (i > 0 && previousCanvas != null) {
                    // Восстанавливаем состояние холста согласно disposal method
                    // (обработка происходит после сохранения текущего кадра, см. ниже)
                }
                
                // Читаем текущий кадр
                BufferedImage frameImage = reader.read(i);
                
                // Сохраняем состояние холста ПЕРЕД рисованием (для disposal method 3)
                BufferedImage savedCanvas = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                Graphics2D savedG = savedCanvas.createGraphics();
                savedG.drawImage(canvas, 0, 0, null);
                savedG.dispose();
                
                // Рисуем текущий кадр на холст
                g.drawImage(frameImage, 0, 0, null);
                
                // Копируем текущее состояние холста как итоговый кадр
                BufferedImage copy = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                Graphics2D copyG = copy.createGraphics();
                copyG.drawImage(canvas, 0, 0, null);
                copyG.dispose();
                
                frames.add(new GifFrame(copy, delay));
                
                // Применяем disposal method текущего кадра для следующего
                if (disposalMethod == 2) {
                    // Restore to background: очищаем холст
                    g.setComposite(AlphaComposite.Clear);
                    g.fillRect(0, 0, width, height);
                    g.setComposite(AlphaComposite.SrcOver);
                } else if (disposalMethod == 3) {
                    // Restore to previous: восстанавливаем сохранённое состояние
                    g.drawImage(savedCanvas, 0, 0, null);
                }
                // disposalMethod == 1 (Do Not Dispose): ничего не делаем, оставляем как есть
            }
            
            g.dispose();
            reader.dispose();
        }
    }

    private static class GifFrame {
        BufferedImage image;
        int delay;
        
        GifFrame(BufferedImage image, int delay) {
            this.image = image;
            this.delay = delay;
        }
    }
}
