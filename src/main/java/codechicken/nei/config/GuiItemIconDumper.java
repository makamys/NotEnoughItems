package codechicken.nei.config;

import codechicken.core.CommonUtils;
import codechicken.lib.gui.GuiDraw;
import codechicken.nei.ItemPanels;
import codechicken.nei.NEIClientConfig;
import codechicken.nei.NEIClientUtils;
import codechicken.nei.guihook.GuiContainerManager;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import org.lwjgl.BufferUtils;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

public class GuiItemIconDumper extends GuiScreen {
    static final int[] illegalChars = {
        34, 60, 62, 124, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25,
        26, 27, 28, 29, 30, 31, 58, 42, 63, 92, 47
    };

    static {
        Arrays.sort(illegalChars);
    }

    private Option opt;
    private int drawIndex;
    private int parseIndex;
    private final File dir = new File(CommonUtils.getMinecraftDir(), "dumps/itempanel_icons");
    private int iconSize;
    private int borderSize;
    private int boxSize;

    public GuiItemIconDumper(Option opt, int iconSize) {
        this.opt = opt;
        this.iconSize = iconSize;
        borderSize = iconSize / 16;
        boxSize = iconSize + borderSize * 2;

        if (dir.exists()) {
            for (File f : dir.listFiles()) if (f.isFile()) f.delete();
        } else dir.mkdirs();
    }

    private void returnScreen(IChatComponent msg) {
        Minecraft.getMinecraft().displayGuiScreen(opt.slot.getGui());
        NEIClientUtils.printChatMessage(msg);
    }

    @Override
    protected void keyTyped(char c, int keycode) {
        if (keycode == Keyboard.KEY_ESCAPE || keycode == Keyboard.KEY_BACK) {
            returnScreen(new ChatComponentTranslation(opt.fullName() + ".icon.cancelled"));
            return;
        }
        super.keyTyped(c, keycode);
    }

    @Override
    public void drawScreen(int mousex, int mousey, float frame) {
        try {
            drawItems();
            exportItems();
        } catch (Exception e) {
            NEIClientConfig.logger.error("Error dumping item icons", e);
        }
    }

    private void drawItems() {
        Dimension d = GuiDraw.displayRes();

        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glLoadIdentity();
        GL11.glOrtho(0.0D, d.width * 16D / iconSize, d.height * 16D / iconSize, 0.0D, 1000.0D, 3000.0D);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glClearColor(0, 0, 0, 0);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);

        int rows = d.height / boxSize;
        int cols = d.width / boxSize;
        int fit = rows * cols;

        RenderHelper.enableGUIStandardItemLighting();
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
        GL11.glColor4f(1, 1, 1, 1);

        final ArrayList<ItemStack> items = ItemPanels.itemPanel.getItems();

        for (int i = 0; drawIndex < items.size() && i < fit; drawIndex++, i++) {
            int x = i % cols * 18;
            int y = i / cols * 18;
            GuiContainerManager.drawItem(x + 1, y + 1, items.get(drawIndex));
        }

        GL11.glFlush();
    }

    private void exportItems() throws IOException {
        BufferedImage img = screenshot();
        int rows = img.getHeight() / boxSize;
        int cols = img.getWidth() / boxSize;
        int fit = rows * cols;

        final ArrayList<ItemStack> items = ItemPanels.itemPanel.getItems();

        for (int i = 0; parseIndex < items.size() && i < fit; parseIndex++, i++) {
            int x = i % cols * boxSize;
            int y = i / cols * boxSize;
            exportImage(
                    dir, img.getSubimage(x + borderSize, y + borderSize, iconSize, iconSize), items.get(parseIndex));
        }

        if (parseIndex >= items.size()) {
            returnScreen(new ChatComponentTranslation(opt.fullName() + ".icon.dumped", "dumps/itempanel_icons"));
        }
    }

    public static String cleanFileName(String name) {
        StringBuilder cleanName = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            int c = name.charAt(i);
            if (Arrays.binarySearch(illegalChars, c) < 0) cleanName.append((char) c);
            else cleanName.append('_');
        }
        return cleanName.toString();
    }

    private void exportImage(File dir, BufferedImage img, ItemStack stack) throws IOException {
        String name = EnumChatFormatting.getTextWithoutFormattingCodes(GuiContainerManager.itemDisplayNameShort(stack));
        name = cleanFileName(name);
        File file = new File(dir, name + ".png");
        for (int i = 2; file.exists(); i++) file = new File(dir, name + '_' + i + ".png");
        ImageIO.write(img, "png", file);
    }

    private IntBuffer pixelBuffer;
    private int[] pixelValues;

    private BufferedImage screenshot() {
        Framebuffer fb = Minecraft.getMinecraft().getFramebuffer();
        Dimension mcSize = GuiDraw.displayRes();
        Dimension texSize = mcSize;

        if (OpenGlHelper.isFramebufferEnabled())
            texSize = new Dimension(fb.framebufferTextureWidth, fb.framebufferTextureHeight);

        int k = texSize.width * texSize.height;
        if (pixelBuffer == null || pixelBuffer.capacity() < k) {
            pixelBuffer = BufferUtils.createIntBuffer(k);
            pixelValues = new int[k];
        }

        GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
        pixelBuffer.clear();

        if (OpenGlHelper.isFramebufferEnabled()) {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, fb.framebufferTexture);
            GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, pixelBuffer);
        } else {
            GL11.glReadPixels(
                    0, 0, texSize.width, texSize.height, GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, pixelBuffer);
        }

        pixelBuffer.get(pixelValues);
        TextureUtil.func_147953_a(pixelValues, texSize.width, texSize.height);

        BufferedImage img = new BufferedImage(mcSize.width, mcSize.height, BufferedImage.TYPE_INT_ARGB);
        if (OpenGlHelper.isFramebufferEnabled()) {
            int yOff = texSize.height - mcSize.height;
            for (int y = 0; y < mcSize.height; ++y)
                for (int x = 0; x < mcSize.width; ++x) img.setRGB(x, y, pixelValues[(y + yOff) * texSize.width + x]);
        } else {
            img.setRGB(0, 0, texSize.width, height, pixelValues, 0, texSize.width);
        }

        return img;
    }
}
