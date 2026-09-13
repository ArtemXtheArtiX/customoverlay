package ru.yourname.client;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class GifDecoder {
    public static GifImage read(InputStream is) {
        GifImage gif = new GifImage();
        gif.read(is);
        return gif;
    }

    public static class GifImage {
        public int width;
        public int height;
        public List<GifFrame> frames = new ArrayList<>();
        public int status = STATUS_OK;

        public static final int STATUS_OK = 0;
        public static final int STATUS_FORMAT_ERROR = 1;
        public static final int STATUS_OPEN_ERROR = 2;

        public int getWidth() { return width; }
        public int getHeight() { return height; }
        public int getFrameCount() { return frames.size(); }
        public BufferedImage getFrame(int i) { return frames.get(i).image; }
        public int getDelay(int i) { return frames.get(i).delay; }

        private void read(InputStream is) {
            try {
                GifStream stream = new GifStream(is);
                if (!stream.readHeader()) { status = STATUS_OPEN_ERROR; return; }
                if (!stream.readScreenDescriptor()) { status = STATUS_FORMAT_ERROR; return; }
                
                width = stream.width;
                height = stream.height;
                int[] globalPalette = stream.globalPalette;
                
                BufferedImage canvas = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = canvas.createGraphics();
                
                int[] previousCanvasPixels = null;

                while (true) {
                    int blockType = stream.read();
                    if (blockType == -1 || blockType == 0x3B) break; // EOF или Trailer

                    if (blockType == 0x21) { // Extension
                        int label = stream.read();
                        if (label == 0xF9) { // Graphic Control Extension
                            stream.skip(1); // block size
                            int packed = stream.read();
                            int disposalMethod = (packed & 0x1C) >> 2;
                            boolean hasTransparency = (packed & 0x01) != 0;
                            int delay = stream.readShort() * 10;
                            if (delay == 0) delay = 100;
                            int transIndex = stream.read();
                            stream.skip(1); // block terminator

                            // Читаем Image Descriptor
                            if (stream.read() != 0x2C) { status = STATUS_FORMAT_ERROR; break; }
                            stream.skip(4); // left, top
                            int imgWidth = stream.readShort();
                            int imgHeight = stream.readShort();
                            int packedImg = stream.read();
                            boolean hasLocalPalette = (packedImg & 0x80) != 0;
                            boolean isInterlaced = (packedImg & 0x40) != 0;
                            
                            int[] palette = globalPalette;
                            if (hasLocalPalette) {
                                int size = 1 << ((packedImg & 0x07) + 1);
                                palette = stream.readPalette(size);
                            }

                            // Сохраняем холст для disposal method 3
                            if (disposalMethod == 3) {
                                previousCanvasPixels = new int[width * height];
                                System.arraycopy(((DataBufferInt) canvas.getRaster().getDataBuffer()).getData(), 0, previousCanvasPixels, 0, width * height);
                            }

                            // Декодируем и рисуем кадр
                            int[] pixels = stream.readImageData(imgWidth, imgHeight, isInterlaced, palette, hasTransparency, transIndex);
                            drawFrame(g, canvas, pixels, stream.left, stream.top, imgWidth, imgHeight);

                            // Сохраняем кадр
                            BufferedImage frameCopy = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                            frameCopy.getGraphics().drawImage(canvas, 0, 0, null);
                            frames.add(new GifFrame(frameCopy, delay));

                            // Применяем disposal method
                            if (disposalMethod == 2) {
                                g.setComposite(AlphaComposite.Clear);
                                g.fillRect(stream.left, stream.top, imgWidth, imgHeight);
                                g.setComposite(AlphaComposite.SrcOver);
                            } else if (disposalMethod == 3 && previousCanvasPixels != null) {
                                DataBufferInt db = (DataBufferInt) canvas.getRaster().getDataBuffer();
                                System.arraycopy(previousCanvasPixels, 0, db.getData(), 0, width * height);
                            }
                        } else {
                            stream.skipBlocks();
                        }
                    } else if (blockType == 0x2C) { // Image Descriptor без GCE
                        stream.skip(4);
                        int imgWidth = stream.readShort();
                        int imgHeight = stream.readShort();
                        int packedImg = stream.read();
                        boolean hasLocalPalette = (packedImg & 0x80) != 0;
                        boolean isInterlaced = (packedImg & 0x40) != 0;
                        
                        int[] palette = globalPalette;
                        if (hasLocalPalette) {
                            int size = 1 << ((packedImg & 0x07) + 1);
                            palette = stream.readPalette(size);
                        }
                        
                        int[] pixels = stream.readImageData(imgWidth, imgHeight, isInterlaced, palette, false, 0);
                        drawFrame(g, canvas, pixels, stream.left, stream.top, imgWidth, imgHeight);
                        
                        BufferedImage frameCopy = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                        frameCopy.getGraphics().drawImage(canvas, 0, 0, null);
                        frames.add(new GifFrame(frameCopy, 100));
                    } else {
                        stream.skipBlocks();
                    }
                }
                g.dispose();
                stream.close();
            } catch (Exception e) {
                status = STATUS_FORMAT_ERROR;
            }
        }

        private void drawFrame(Graphics2D g, BufferedImage canvas, int[] pixels, int x, int y, int w, int h) {
            BufferedImage frameImg = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            System.arraycopy(pixels, 0, ((DataBufferInt) frameImg.getRaster().getDataBuffer()).getData(), 0, pixels.length);
            g.drawImage(frameImg, x, y, null);
        }
    }

    public static class GifFrame {
        public BufferedImage image;
        public int delay;
        public GifFrame(BufferedImage image, int delay) { this.image = image; this.delay = delay; }
    }

    private static class GifStream {
        private InputStream is;
        public int width, height, left, top;
        public int[] globalPalette;

        public GifStream(InputStream is) { this.is = is; }

        public int read() { try { return is.read(); } catch (Exception e) { return -1; } }
        public void skip(int n) { try { is.skip(n); } catch (Exception e) {} }
        public int readShort() { int lo = read(); int hi = read(); return (hi << 8) | lo; }
        
        public int[] readPalette(int size) {
            int[] p = new int[size];
            for (int i = 0; i < size; i++) {
                int r = read(), g = read(), b = read();
                p[i] = 0xFF000000 | (r << 16) | (g << 8) | b;
            }
            return p;
        }

        public boolean readHeader() {
            byte[] h = new byte[6];
            try { if (is.read(h) != 6) return false; } catch (Exception e) { return false; }
            return new String(h, 0, 3).equals("GIF");
        }

        public boolean readScreenDescriptor() {
            width = readShort(); height = readShort();
            int packed = read();
            int bgColor = read();
            skip(1);
            if ((packed & 0x80) != 0) {
                globalPalette = readPalette(1 << ((packed & 0x07) + 1));
            }
            return true;
        }

        public void skipBlocks() {
            while (true) {
                int size = read();
                if (size <= 0) break;
                skip(size);
            }
        }

        public int[] readImageData(int w, int h, boolean interlaced, int[] palette, boolean hasTransparency, int transIndex) {
            int minCodeSize = read();
            if (minCodeSize < 2 || minCodeSize > 8) { skipBlocks(); return new int[w * h]; }
            
            // Читаем все байты данных
            java.util.List<Byte> data = new java.util.ArrayList<>();
            while (true) {
                int size = read();
                if (size <= 0) break;
                for (int i = 0; i < size; i++) data.add((byte) read());
            }
            
            byte[] bytes = new byte[data.size()];
            for (int i = 0; i < data.size(); i++) bytes[i] = data.get(i);
            
            return lzwDecode(bytes, w * h, minCodeSize, palette, hasTransparency, transIndex, interlaced, w, h);
        }

        private int[] lzwDecode(byte[] data, int expectedPixels, int minCodeSize, int[] palette, boolean hasTransparency, int transIndex, boolean interlaced, int w, int h) {
            int clearCode = 1 << minCodeSize;
            int eoiCode = clearCode + 1;
            int codeSize = minCodeSize + 1;
            int nextCode = eoiCode + 1;
            int maxCode = 1 << codeSize;

            int[][] table = new int[4096][];
            for (int i = 0; i < clearCode; i++) table[i] = new int[]{i};

            int[] result = new int[expectedPixels];
            int resIdx = 0;
            int bitIdx = 0, byteIdx = 0;
            int prevCode = -1;

            while (resIdx < expectedPixels && byteIdx < data.length) {
                int code = 0;
                for (int i = 0; i < codeSize; i++) {
                    if (byteIdx >= data.length) break;
                    int bit = (data[byteIdx] >> bitIdx) & 1;
                    code |= (bit << i);
                    bitIdx++;
                    if (bitIdx == 8) { bitIdx = 0; byteIdx++; }
                }
                if (byteIdx >= data.length && code == 0) break;

                if (code == clearCode) {
                    codeSize = minCodeSize + 1;
                    nextCode = eoiCode + 1;
                    maxCode = 1 << codeSize;
                    prevCode = -1;
                    continue;
                }
                if (code == eoiCode) break;

                int[] entry;
                if (code < nextCode && table[code] != null) {
                    entry = table[code];
                } else if (code == nextCode && prevCode != -1 && table[prevCode] != null) {
                    int[] prev = table[prevCode];
                    entry = new int[prev.length + 1];
                    System.arraycopy(prev, 0, entry, 0, prev.length);
                    entry[prev.length] = prev[0];
                } else {
                    break; // Ошибка в данных, прерываем
                }

                for (int val : entry) {
                    if (resIdx < expectedPixels) {
                        if (hasTransparency && val == transIndex) {
                            result[resIdx++] = 0x00000000;
                        } else if (val < palette.length) {
                            result[resIdx++] = palette[val];
                        } else {
                            result[resIdx++] = 0x00000000;
                        }
                    }
                }

                if (prevCode != -1 && nextCode < 4096) {
                    int[] prev = table[prevCode];
                    int[] newEntry = new int[prev.length + 1];
                    System.arraycopy(prev, 0, newEntry, 0, prev.length);
                    newEntry[prev.length] = entry[0];
                    table[nextCode++] = newEntry;

                    if (nextCode >= maxCode && codeSize < 12) {
                        codeSize++;
                        maxCode = 1 << codeSize;
                    }
                }
                prevCode = code;
            }

            if (interlaced) {
                int[] temp = new int[result.length];
                int src = 0;
                int[] passes = {0, 4, 2, 1};
                int[] steps = {8, 8, 4, 2};
                for (int p = 0; p < 4; p++) {
                    for (int y = passes[p]; y < h; y += steps[p]) {
                        for (int x = 0; x < w; x++) {
                            if (src < result.length) temp[y * w + x] = result[src++];
                        }
                    }
                }
                result = temp;
            }
            return result;
        }
        
        public void close() { try { is.close(); } catch (Exception e) {} }
    }
}
