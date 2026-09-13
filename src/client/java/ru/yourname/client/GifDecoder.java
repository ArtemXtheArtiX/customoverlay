package ru.yourname.client;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
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
        private List<Frame> frames = new ArrayList<>();

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
            // Упрощённый GIF декодер
            // Полный код из SignPicture слишком большой, использую стандартный подход
            javax.imageio.ImageReader reader = javax.imageio.ImageIO.getImageReadersByFormatName("gif").next();
            javax.imageio.stream.ImageInputStream iis = javax.imageio.ImageIO.createImageInputStream(is);
            reader.setInput(iis);
            
            int numFrames = reader.getNumImages(true);
            if (numFrames > 0) {
                BufferedImage first = reader.read(0);
                width = first.getWidth();
                height = first.getHeight();
                
                BufferedImage canvas = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = canvas.createGraphics();
                
                for (int i = 0; i < numFrames; i++) {
                    BufferedImage frame = reader.read(i);
                    g.drawImage(frame, 0, 0, null);
                    
                    BufferedImage copy = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                    copy.getGraphics().drawImage(canvas, 0, 0, null);
                    
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
                    
                    frames.add(new Frame(copy, delay));
                }
                
                g.dispose();
            }
            
            reader.dispose();
        }
    }

    private static class Frame {
        BufferedImage image;
        int delay;
        
        Frame(BufferedImage image, int delay) {
            this.image = image;
            this.delay = delay;
        }
    }
}
