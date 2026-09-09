package ru.yourname.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import ru.yourname.Customoverlay;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class ConfigManager {
	private static final Path CONFIG_FILE = FabricLoader.getInstance().getConfigDir().resolve("customoverlay.json");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public static void save() {
		List<TextFieldData> dataList = new ArrayList<>();
		for (TextField f : OverlayRenderer.fields) {
			dataList.add(new TextFieldData(f.x, f.y, f.width, f.height, f.imageUrlOrPath, f.isGif, f.alpha, f.speed));
		}
		try (FileWriter w = new FileWriter(CONFIG_FILE.toFile())) { GSON.toJson(dataList, w); } 
		catch (Exception e) { Customoverlay.LOGGER.error("Failed to save config", e); }
	}

	public static void load() {
		for (TextField f : OverlayRenderer.fields) f.cleanup();
		OverlayRenderer.fields.clear();
		if (!CONFIG_FILE.toFile().exists()) return;
		try (FileReader r = new FileReader(CONFIG_FILE.toFile())) {
			TextFieldData[] arr = GSON.fromJson(r, TextFieldData[].class);
			if (arr != null) {
				for (TextFieldData d : arr) {
					TextField f = new TextField(d.x, d.y, d.w, d.h, d.source, d.isGif);
					f.alpha = d.alpha; f.speed = d.speed;
					OverlayRenderer.fields.add(f);
				}
			}
		} catch (Exception e) { Customoverlay.LOGGER.error("Failed to load config", e); }
	}

	public static class TextFieldData {
		int x, y, w, h; String source; boolean isGif; float alpha, speed;
		public TextFieldData(int x, int y, int w, int h, String source, boolean isGif, float alpha, float speed) {
			this.x = x; this.y = y; this.w = w; this.h = h; this.source = source; this.isGif = isGif; this.alpha = alpha; this.speed = speed;
		}
	}
}
