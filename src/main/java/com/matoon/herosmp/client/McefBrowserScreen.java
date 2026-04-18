package com.matoon.herosmp.client;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.lang.reflect.Method;

public class McefBrowserScreen extends GuiScreen {

    private final GuiScreen parentScreen;
    private final String startUrl;
    private Object browser;
    private boolean mcefReady;
    private int browserLeft;
    private int browserTop;
    private int browserRight;
    private int browserBottom;
    private GuiButton closeButton;
    private boolean externalFallbackTriggered;

    public McefBrowserScreen(GuiScreen parentScreen, String startUrl) {
        this.parentScreen = parentScreen;
        this.startUrl = startUrl;
    }

    @Override
    public void initGui() {
        this.buttonList.clear();
        this.closeButton = new GuiButton(0, this.width - 66, 8, 58, 20, "Close");
        this.buttonList.add(this.closeButton);
        Keyboard.enableRepeatEvents(true);

        this.mcefReady = initBrowser();
        if (!this.mcefReady) {
            tryOpenExternalAndClose();
        }
        updateBrowserBounds();
    }

    @Override
    public void onGuiClosed() {
        closeBrowser();
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == 0) {
            mc.displayGuiScreen(parentScreen);
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(parentScreen);
            return;
        }

        if (mcefReady && browser != null) {
            invokeBrowser("injectKeyPressedByKeyCode", new Class[]{int.class, char.class, int.class}, keyCode, typedChar, 0);
            if (typedChar != 0) {
                invokeBrowser("injectKeyTyped", new Class[]{char.class, int.class}, typedChar, 0);
            }
            invokeBrowser("injectKeyReleasedByKeyCode", new Class[]{int.class, char.class, int.class}, keyCode, typedChar, 0);
        }

        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);

        if (mcefReady && browser != null && isInsideBrowser(mouseX, mouseY)) {
            invokeBrowser("injectMouseButton", new Class[]{int.class, int.class, int.class, int.class, boolean.class, int.class},
                    toBrowserX(mouseX), toBrowserY(mouseY), 0, mouseButton + 1, true, 1);
        }
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        super.mouseReleased(mouseX, mouseY, state);

        if (mcefReady && browser != null && isInsideBrowser(mouseX, mouseY)) {
            invokeBrowser("injectMouseButton", new Class[]{int.class, int.class, int.class, int.class, boolean.class, int.class},
                    toBrowserX(mouseX), toBrowserY(mouseY), 0, state + 1, false, 1);
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();

        if (!mcefReady || browser == null) {
            return;
        }

        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) {
            return;
        }

        int mouseX = Mouse.getEventX() * this.width / this.mc.displayWidth;
        int mouseY = this.height - Mouse.getEventY() * this.height / this.mc.displayHeight - 1;
        if (isInsideBrowser(mouseX, mouseY)) {
            invokeBrowser("injectMouseWheel", new Class[]{int.class, int.class, int.class, int.class, int.class},
                    toBrowserX(mouseX), toBrowserY(mouseY), 0, 1, wheel);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        updateBrowserBounds();
        drawDefaultBackground();

        drawRect(browserLeft - 2, browserTop - 2, browserRight + 2, browserBottom + 2, 0xCC111111);

        if (mcefReady && browser != null) {
            invokeBrowser("injectMouseMove", new Class[]{int.class, int.class, int.class, boolean.class},
                    toBrowserX(mouseX), toBrowserY(mouseY), 0, !isInsideBrowser(mouseX, mouseY));

            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            invokeBrowser("draw", new Class[]{double.class, double.class, double.class, double.class},
                    (double) browserLeft, (double) browserBottom, (double) browserRight, (double) browserTop);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
        } else {
            drawCenteredString(this.fontRenderer, "MCEF is not loaded. Install MCEF mod to use in-game browser.", this.width / 2, this.height / 2 - 10, 0xFFFFFF);
            drawCenteredString(this.fontRenderer, "Close this window and install mcef for 1.12.2.", this.width / 2, this.height / 2 + 4, 0xAAAAAA);
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private boolean initBrowser() {
        try {
            Class<?> mcefApiClass = Class.forName("net.montoyo.mcef.api.MCEFApi");
            Method isLoaded = mcefApiClass.getMethod("isMCEFLoaded");
            if (!(Boolean) isLoaded.invoke(null)) {
                return false;
            }

            Method getApi = mcefApiClass.getMethod("getAPI");
            Object api = getApi.invoke(null);
            if (api == null) {
                return false;
            }

            Method createBrowser = api.getClass().getMethod("createBrowser", String.class, boolean.class);
            this.browser = createBrowser.invoke(api, startUrl, false);
            return this.browser != null;
        } catch (Throwable t) {
            t.printStackTrace();
            return false;
        }
    }

    private void closeBrowser() {
        if (browser == null) {
            return;
        }

        try {
            invokeBrowser("close", new Class[0]);
        } catch (Throwable ignored) {
            // Ignore shutdown errors
        } finally {
            browser = null;
        }
    }

    private void tryOpenExternalAndClose() {
        if (externalFallbackTriggered) {
            return;
        }

        externalFallbackTriggered = true;
        if (openInSystemBrowser(startUrl)) {
            mc.displayGuiScreen(parentScreen);
        }
    }

    private static boolean openInSystemBrowser(String url) {
        if (url == null || url.trim().isEmpty()) {
            return false;
        }

        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
                return true;
            }
        } catch (Throwable ignored) {
        }

        try {
            String osName = System.getProperty("os.name", "").toLowerCase();
            if (osName.contains("win")) {
                Runtime.getRuntime().exec(new String[]{"rundll32", "url.dll,FileProtocolHandler", url});
                return true;
            }
            if (osName.contains("mac")) {
                Runtime.getRuntime().exec(new String[]{"open", url});
                return true;
            }
            Runtime.getRuntime().exec(new String[]{"xdg-open", url});
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void updateBrowserBounds() {
        int margin = 10;
        int topBar = 34;
        browserLeft = margin;
        browserTop = topBar;
        browserRight = this.width - margin;
        browserBottom = this.height - margin;

        if (closeButton != null) {
            closeButton.x = this.width - 66;
            closeButton.y = 8;
        }

        if (mcefReady && browser != null) {
            int browserW = Math.max(32, scaleX(browserRight - browserLeft));
            int browserH = Math.max(32, scaleY(browserBottom - browserTop));
            invokeBrowser("resize", new Class[]{int.class, int.class}, browserW, browserH);
        }
    }

    private boolean isInsideBrowser(int mouseX, int mouseY) {
        return mouseX >= browserLeft && mouseX <= browserRight && mouseY >= browserTop && mouseY <= browserBottom;
    }

    private int toBrowserX(int mouseX) {
        return scaleX(mouseX - browserLeft);
    }

    private int toBrowserY(int mouseY) {
        return scaleY(mouseY - browserTop);
    }

    private int scaleX(int x) {
        return x * this.mc.displayWidth / Math.max(1, this.width);
    }

    private int scaleY(int y) {
        return y * this.mc.displayHeight / Math.max(1, this.height);
    }

    private Object invokeBrowser(String methodName, Class<?>[] signature, Object... args) {
        try {
            Method method = browser.getClass().getMethod(methodName, signature);
            return method.invoke(browser, args);
        } catch (Throwable t) {
            this.mcefReady = false;
            tryOpenExternalAndClose();
            return null;
        }
    }
}
