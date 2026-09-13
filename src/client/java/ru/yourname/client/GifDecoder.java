package ru.yourname.client;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
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
            // Читаем заголовок GIF
            byte[] header = new byte[6];
            if (is.read(header) != 6) throw new IOException("Invalid GIF");
            
            String sig = new String(header, 0, 3);
            String ver = new String(header, 3, 3);
            if (!sig.equals("GIF")) throw new IOException("Not a GIF file");
            
            // Читаем логический дескриптор экрана
            byte[] screenDescriptor = new byte[7];
            if (is.read(screenDescriptor) != 7) throw new IOException("Invalid GIF");
            
            width = (screenDescriptor[1] & 0xFF) | ((screenDescriptor[2] & 0xFF) << 8);
            height = (screenDescriptor[3] & 0xFF) | ((screenDescriptor[4] & 0xFF) << 8);
            
            boolean hasGlobalColorTable = (screenDescriptor[5] & 0x80) != 0;
            int globalColorTableSize = 1 << ((screenDescriptor[5] & 0x07) + 1);
            int backgroundColorIndex = screenDescriptor[6] & 0xFF;
            
            int[] globalColorTable = null;
            if (hasGlobalColorTable) {
                globalColorTable = readColorTable(is, globalColorTableSize);
            }
            
            // Холст для накопления кадров
            BufferedImage canvas = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            int[] canvasPixels = ((DataBufferInt) canvas.getRaster().getDataBuffer()).getData();
            
            // Инициализируем холст прозрачным
            for (int i = 0; i < canvasPixels.length; i++) {
                canvasPixels[i] = 0x00000000;
            }
            
            // Сохраняем состояние холста для disposal method 3
            int[] previousCanvasPixels = null;
            
            boolean done = false;
            while (!done) {
                int blockType = is.read();
                if (blockType == -1) break;
                
                switch (blockType) {
                    case 0x21: // Extension
                        int extensionLabel = is.read();
                        if (extensionLabel == 0xF9) { // Graphic Control Extension
                            byte[] gce = new byte[6];
                            if (is.read(gce) != 6) throw new IOException("Invalid GCE");
                            
                            int disposalMethod = (gce[1] & 0x1C) >> 2;
                            boolean transparentFlag = (gce[1] & 0x01) != 0;
                            int delay = (gce[2] & 0xFF) | ((gce[3] & 0xFF) << 8);
                            delay *= 10; // Конвертируем в миллисекунды
                            if (delay == 0) delay = 100;
                            int transparentColorIndex = gce[4] & 0xFF;
                            
                            // Читаем следующий блок (Image Descriptor)
                            int nextBlock = is.read();
                            if (nextBlock != 0x2C) {
                                done = true;
                                break;
                            }
                            
                            // Читаем Image Descriptor
                            byte[] imageDescriptor = new byte[9];
                            if (is.read(imageDescriptor) != 9) throw new IOException("Invalid Image Descriptor");
                            
                            int left = (imageDescriptor[1] & 0xFF) | ((imageDescriptor[2] & 0xFF) << 8);
                            int top = (imageDescriptor[3] & 0xFF) | ((imageDescriptor[4] & 0xFF) << 8);
                            int imgWidth = (imageDescriptor[5] & 0xFF) | ((imageDescriptor[6] & 0xFF) << 8);
                            int imgHeight = (imageDescriptor[7] & 0xFF) | ((imageDescriptor[8] & 0xFF) << 8);
                            
                            boolean hasLocalColorTable = (imageDescriptor[9] & 0x80) != 0;
                            boolean interlace = (imageDescriptor[9] & 0x40) != 0;
                            int localColorTableSize = 1 << ((imageDescriptor[9] & 0x07) + 1);
                            
                            int[] colorTable = globalColorTable;
                            if (hasLocalColorTable) {
                                colorTable = readColorTable(is, localColorTableSize);
                            }
                            
                            // Сохраняем состояние холста перед рисованием (для disposal method 3)
                            previousCanvasPixels = new int[canvasPixels.length];
                            System.arraycopy(canvasPixels, 0, previousCanvasPixels, 0, canvasPixels.length);
                            
                            // Читаем и декодируем изображение
                            int[] imagePixels = readImage(is, imgWidth, imgHeight, interlace, colorTable, transparentFlag, transparentColorIndex);
                            
                            // Накладываем изображение на холст
                            for (int y = 0; y < imgHeight; y++) {
                                for (int x = 0; x < imgWidth; x++) {
                                    int srcPixel = imagePixels[y * imgWidth + x];
                                    int dstX = left + x;
                                    int dstY = top + y;
                                    
                                    if (dstX >= 0 && dstX < width && dstY >= 0 && dstY < height) {
                                        int dstIndex = dstY * width + dstX;
                                        
                                        // Если пиксель не прозрачный, рисуем его
                                        if ((srcPixel & 0xFF000000) != 0) {
                                            canvasPixels[dstIndex] = srcPixel;
                                        }
                                    }
                                }
                            }
                            
                            // Копируем текущее состояние холста как итоговый кадр
                            BufferedImage frameImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                            int[] framePixels = ((DataBufferInt) frameImage.getRaster().getDataBuffer()).getData();
                            System.arraycopy(canvasPixels, 0, framePixels, 0, canvasPixels.length);
                            
                            frames.add(new GifFrame(frameImage, delay));
                            
                            // Применяем disposal method для подготовки к следующему кадру
                            if (disposalMethod == 2) {
                                // Restore to background: очищаем область изображения
                                for (int y = 0; y < imgHeight; y++) {
                                    for (int x = 0; x < imgWidth; x++) {
                                        int dstX = left + x;
                                        int dstY = top + y;
                                        if (dstX >= 0 && dstX < width && dstY >= 0 && dstY < height) {
                                            canvasPixels[dstY * width + dstX] = 0x00000000;
                                        }
                                    }
                                }
                            } else if (disposalMethod == 3) {
                                // Restore to previous: восстанавливаем сохранённое состояние
                                if (previousCanvasPixels != null) {
                                    System.arraycopy(previousCanvasPixels, 0, canvasPixels, 0, canvasPixels.length);
                                }
                            }
                            // disposalMethod == 0 или 1: ничего не делаем, оставляем как есть
                            
                        } else {
                            // Пропускаем другие расширения
                            skipSubBlocks(is);
                        }
                        break;
                        
                    case 0x2C: // Image Descriptor (без GCE)
                        byte[] imageDescriptor = new byte[9];
                        if (is.read(imageDescriptor) != 9) throw new IOException("Invalid Image Descriptor");
                        
                        int left = (imageDescriptor[1] & 0xFF) | ((imageDescriptor[2] & 0xFF) << 8);
                        int top = (imageDescriptor[3] & 0xFF) | ((imageDescriptor[4] & 0xFF) << 8);
                        int imgWidth = (imageDescriptor[5] & 0xFF) | ((imageDescriptor[6] & 0xFF) << 8);
                        int imgHeight = (imageDescriptor[7] & 0xFF) | ((imageDescriptor[8] & 0xFF) << 8);
                        
                        boolean hasLocalColorTable = (imageDescriptor[9] & 0x80) != 0;
                        boolean interlace = (imageDescriptor[9] & 0x40) != 0;
                        int localColorTableSize = 1 << ((imageDescriptor[9] & 0x07) + 1);
                        
                        int[] colorTable = globalColorTable;
                        if (hasLocalColorTable) {
                            colorTable = readColorTable(is, localColorTableSize);
                        }
                        
                        int[] imagePixels = readImage(is, imgWidth, imgHeight, interlace, colorTable, false, 0);
                        
                        for (int y = 0; y < imgHeight; y++) {
                            for (int x = 0; x < imgWidth; x++) {
                                int srcPixel = imagePixels[y * imgWidth + x];
                                int dstX = left + x;
                                int dstY = top + y;
                                
                                if (dstX >= 0 && dstX < width && dstY >= 0 && dstY < height) {
                                    canvasPixels[dstY * width + dstX] = srcPixel;
                                }
                            }
                        }
                        
                        BufferedImage frameImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                        int[] framePixels = ((DataBufferInt) frameImage.getRaster().getDataBuffer()).getData();
                        System.arraycopy(canvasPixels, 0, framePixels, 0, canvasPixels.length);
                        
                        frames.add(new GifFrame(frameImage, 100));
                        break;
                        
                    case 0x3B: // Trailer
                        done = true;
                        break;
                        
                    default:
                        done = true;
                        break;
                }
            }
        }
        
        private int[] readColorTable(InputStream is, int size) throws IOException {
            int[] colorTable = new int[size];
            byte[] buffer = new byte[size * 3];
            if (is.read(buffer) != size * 3) throw new IOException("Invalid color table");
            
            for (int i = 0; i < size; i++) {
                int r = buffer[i * 3] & 0xFF;
                int g = buffer[i * 3 + 1] & 0xFF;
                int b = buffer[i * 3 + 2] & 0xFF;
                colorTable[i] = 0xFF000000 | (r << 16) | (g << 8) | b;
            }
            
            return colorTable;
        }
        
        private int[] readImage(InputStream is, int width, int height, boolean interlace, int[] colorTable, boolean transparent, int transparentIndex) throws IOException {
            int minCodeSize = is.read();
            if (minCodeSize < 2 || minCodeSize > 8) throw new IOException("Invalid min code size");
            
            // Читаем все подблоки данных
            byte[] data = readSubBlocks(is);
            
            // Декодируем LZW
            int[] pixels = lzwDecode(data, width * height, minCodeSize);
            
            // Конвертируем индексы в цвета
            int[] result = new int[width * height];
            for (int i = 0; i < pixels.length; i++) {
                int index = pixels[i];
                if (transparent && index == transparentIndex) {
                    result[i] = 0x00000000; // Прозрачный
                } else if (index < colorTable.length) {
                    result[i] = colorTable[index];
                } else {
                    result[i] = 0x00000000; // Прозрачный для невалидных индексов
                }
            }
            
            // Обрабатываем interlace
            if (interlace) {
                int[] temp = new int[result.length];
                int srcIndex = 0;
                
                // Pass 1: каждые 8 строк, начиная с 0
                for (int y = 0; y < height; y += 8) {
                    for (int x = 0; x < width; x++) {
                        temp[y * width + x] = result[srcIndex++];
                    }
                }
                
                // Pass 2: каждые 8 строк, начиная с 4
                for (int y = 4; y < height; y += 8) {
                    for (int x = 0; x < width; x++) {
                        temp[y * width + x] = result[srcIndex++];
                    }
                }
                
                // Pass 3: каждые 4 строки, начиная с 2
                for (int y = 2; y < height; y += 4) {
                    for (int x = 0; x < width; x++) {
                        temp[y * width + x] = result[srcIndex++];
                    }
                }
                
                // Pass 4: каждые 2 строки, начиная с 1
                for (int y = 1; y < height; y += 2) {
                    for (int x = 0; x < width; x++) {
                        temp[y * width + x] = result[srcIndex++];
                    }
                }
                
                result = temp;
            }
            
            return result;
        }
        
        private byte[] readSubBlocks(InputStream is) throws IOException {
            List<byte[]> blocks = new ArrayList<>();
            int totalSize = 0;
            
            while (true) {
                int blockSize = is.read();
                if (blockSize == 0 || blockSize == -1) break;
                
                byte[] block = new byte[blockSize];
                if (is.read(block) != blockSize) throw new IOException("Invalid sub-block");
                
                blocks.add(block);
                totalSize += blockSize;
            }
            
            byte[] result = new byte[totalSize];
            int offset = 0;
            for (byte[] block : blocks) {
                System.arraycopy(block, 0, result, offset, block.length);
                offset += block.length;
            }
            
            return result;
        }
        
        private void skipSubBlocks(InputStream is) throws IOException {
            while (true) {
                int blockSize = is.read();
                if (blockSize == 0 || blockSize == -1) break;
                if (is.skip(blockSize) != blockSize) throw new IOException("Failed to skip sub-block");
            }
        }
        
        private int[] lzwDecode(byte[] data, int pixelCount, int minCodeSize) {
            int clearCode = 1 << minCodeSize;
            int eoiCode = clearCode + 1;
            
            int codeSize = minCodeSize + 1;
            int nextCode = eoiCode + 1;
            int maxCode = 1 << codeSize;
            
            int[][] codeTable = new int[4096][];
            for (int i = 0; i < clearCode; i++) {
                codeTable[i] = new int[]{i};
            }
            
            int[] result = new int[pixelCount];
            int resultIndex = 0;
            
            BitReader reader = new BitReader(data);
            
            int prevCode = -1;
            
            while (resultIndex < pixelCount) {
                int code = reader.readBits(codeSize);
                if (code == -1) break;
                
                if (code == clearCode) {
                    codeSize = minCodeSize + 1;
                    nextCode = eoiCode + 1;
                    maxCode = 1 << codeSize;
                    prevCode = -1;
                    continue;
                }
                
                if (code == eoiCode) {
                    break;
                }
                
                int[] entry;
                if (code < nextCode && codeTable[code] != null) {
                    entry = codeTable[code];
                } else if (code == nextCode && prevCode != -1 && codeTable[prevCode] != null) {
                    int[] prevEntry = codeTable[prevCode];
                    entry = new int[prevEntry.length + 1];
                    System.arraycopy(prevEntry, 0, entry, 0, prevEntry.length);
                    entry[prevEntry.length] = prevEntry[0];
                } else {
                    // Ошибка в данных, пропускаем
                    break;
                }
                
                // Добавляем пиксели в результат
                for (int pixel : entry) {
                    if (resultIndex < pixelCount) {
                        result[resultIndex++] = pixel;
                    }
                }
                
                // Добавляем новую запись в таблицу
                if (prevCode != -1 && nextCode < 4096) {
                    int[] prevEntry = codeTable[prevCode];
                    int[] newEntry = new int[prevEntry.length + 1];
                    System.arraycopy(prevEntry, 0, newEntry, 0, prevEntry.length);
                    newEntry[prevEntry.length] = entry[0];
                    codeTable[nextCode++] = newEntry;
                    
                    if (nextCode >= maxCode && codeSize < 12) {
                        codeSize++;
                        maxCode = 1 << codeSize;
                    }
                }
                
                prevCode = code;
            }
            
            return result;
        }
        
        private static class BitReader {
            private byte[] data;
            private int byteIndex = 0;
            private int bitIndex = 0;
            
            BitReader(byte[] data) {
                this.data = data;
            }
            
            int readBits(int numBits) {
                int result = 0;
                for (int i = 0; i < numBits; i++) {
                    if (byteIndex >= data.length) return -1;
                    
                    int bit = (data[byteIndex] >> bitIndex) & 1;
                    result |= (bit << i);
                    
                    bitIndex++;
                    if (bitIndex == 8) {
                        bitIndex = 0;
                        byteIndex++;
                    }
                }
                return result;
            }
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
