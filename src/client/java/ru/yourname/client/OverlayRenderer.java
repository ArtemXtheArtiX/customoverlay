package ru.yourname.client;

import net.minecraft.client.gui.DrawContext;
import java.util.ArrayList;
import java.util.List;

public class OverlayRenderer {
	public static final List<TextField> fields = new ArrayList<>();
	public static TextField selectedField = null;

	public static void render(DrawContext context) {
		for (TextField field : fields) field.render(context, false);
	}
}
