import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.util.Iterator;

/**
 * Controlador de periféricos remotos (Mouse, Teclado, Gamepad, Macros,
 * Remote Desktop em Tempo Real e Clipboard de Imagem).
 */
public class RemoteInputController {

    private static Robot robot;

    static {
        try {
            robot = new Robot();
            robot.setAutoDelay(2);
        } catch (Exception e) {
            System.err.println("Não foi possível inicializar java.awt.Robot: " + e.getMessage());
        }
    }

    public static void moveMouse(int dx, int dy) {
        if (robot == null) return;
        try {
            Point p = MouseInfo.getPointerInfo().getLocation();
            robot.mouseMove(p.x + dx, p.y + dy);
        } catch (Exception ignored) {}
    }

    public static void mouseMoveAbsolute(float normX, float normY) {
        if (robot == null) return;
        try {
            Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
            int x = Math.max(0, Math.min((int) (normX * screen.width), screen.width - 1));
            int y = Math.max(0, Math.min((int) (normY * screen.height), screen.height - 1));
            robot.mouseMove(x, y);
        } catch (Exception ignored) {}
    }

    public static void mouseMoveAbsolute(int normX, int normY, int screenW, int screenH) {
        if (screenW <= 0 || screenH <= 0) return;
        mouseMoveAbsolute((float) normX / screenW, (float) normY / screenH);
    }

    public static void mouseDownAbsolute(float normX, float normY, String button) {
        mouseMoveAbsolute(normX, normY);
        if (robot == null) return;
        try {
            int mask = "RIGHT".equalsIgnoreCase(button) ? InputEvent.BUTTON3_DOWN_MASK : InputEvent.BUTTON1_DOWN_MASK;
            robot.mousePress(mask);
        } catch (Exception ignored) {}
    }

    public static void mouseDownAbsolute(int normX, int normY, int screenW, int screenH, String button) {
        if (screenW <= 0 || screenH <= 0) return;
        mouseDownAbsolute((float) normX / screenW, (float) normY / screenH, button);
    }

    public static void mouseUpAbsolute(float normX, float normY, String button) {
        mouseMoveAbsolute(normX, normY);
        if (robot == null) return;
        try {
            int mask = "RIGHT".equalsIgnoreCase(button) ? InputEvent.BUTTON3_DOWN_MASK : InputEvent.BUTTON1_DOWN_MASK;
            robot.mouseRelease(mask);
        } catch (Exception ignored) {}
    }

    public static void mouseUpAbsolute(int normX, int normY, int screenW, int screenH, String button) {
        if (screenW <= 0 || screenH <= 0) return;
        mouseUpAbsolute((float) normX / screenW, (float) normY / screenH, button);
    }

    public static void mouseClickAbsolute(float normX, float normY, String button) {
        mouseMoveAbsolute(normX, normY);
        clickMouse(button);
    }

    public static void mouseClickAbsolute(int normX, int normY, int screenW, int screenH, String button) {
        if (screenW <= 0 || screenH <= 0) return;
        mouseClickAbsolute((float) normX / screenW, (float) normY / screenH, button);
    }

    public static void clickMouse(String button) {
        if (robot == null) return;
        try {
            int mask = "RIGHT".equalsIgnoreCase(button) ? InputEvent.BUTTON3_DOWN_MASK : InputEvent.BUTTON1_DOWN_MASK;
            robot.mousePress(mask);
            robot.mouseRelease(mask);
        } catch (Exception ignored) {}
    }

    public static void mouseScroll(int amount) {
        if (robot == null) return;
        try {
            robot.mouseWheel(amount);
        } catch (Exception ignored) {}
    }

    public static void pressSpecialKey(String key) {
        if (robot == null || key == null) return;
        try {
            switch (key.toUpperCase()) {
                case "PAGE_UP": typeKey(KeyEvent.VK_PAGE_UP); break;
                case "PAGE_DOWN": typeKey(KeyEvent.VK_PAGE_DOWN); break;
                case "LEFT": typeKey(KeyEvent.VK_LEFT); break;
                case "RIGHT": typeKey(KeyEvent.VK_RIGHT); break;
                case "UP": typeKey(KeyEvent.VK_UP); break;
                case "DOWN": typeKey(KeyEvent.VK_DOWN); break;
                case "ENTER": typeKey(KeyEvent.VK_ENTER); break;
                case "BACKSPACE": typeKey(KeyEvent.VK_BACK_SPACE); break;
                case "SPACE": typeKey(KeyEvent.VK_SPACE); break;
                case "ESC": typeKey(KeyEvent.VK_ESCAPE); break;
            }
        } catch (Exception ignored) {}
    }

    public static void handleGamepadButton(String button, boolean isDown) {
        if (robot == null || button == null) return;
        int vk = -1;
        switch (button.toUpperCase()) {
            case "UP": vk = KeyEvent.VK_UP; break;
            case "DOWN": vk = KeyEvent.VK_DOWN; break;
            case "LEFT": vk = KeyEvent.VK_LEFT; break;
            case "RIGHT": vk = KeyEvent.VK_RIGHT; break;
            case "A": vk = KeyEvent.VK_Z; break;
            case "B": vk = KeyEvent.VK_X; break;
            case "X": vk = KeyEvent.VK_A; break;
            case "Y": vk = KeyEvent.VK_S; break;
            case "L1": vk = KeyEvent.VK_Q; break;
            case "R1": vk = KeyEvent.VK_W; break;
            case "L2": vk = KeyEvent.VK_1; break;
            case "R2": vk = KeyEvent.VK_2; break;
            case "START": vk = KeyEvent.VK_ENTER; break;
            case "SELECT": vk = KeyEvent.VK_ESCAPE; break;
        }

        if (vk != -1) {
            try {
                if (isDown) robot.keyPress(vk);
                else robot.keyRelease(vk);
            } catch (Exception ignored) {}
        }
    }

    public static void executeMacro(String macro) {
        new Thread(() -> {
            try {
                switch (macro.toUpperCase()) {
                    case "PANIC":
                        pressKeyCombo(KeyEvent.VK_WINDOWS, KeyEvent.VK_D);
                        MediaController.executeCommand("MUTE", "");
                        break;
                    case "DESKTOP":
                        pressKeyCombo(KeyEvent.VK_WINDOWS, KeyEvent.VK_D);
                        break;
                    case "TASK_MANAGER":
                        // Executa Ctrl+Shift+Esc via teclado e abre via cmd
                        pressKeyCombo3(KeyEvent.VK_CONTROL, KeyEvent.VK_SHIFT, KeyEvent.VK_ESCAPE);
                        try {
                            new ProcessBuilder("cmd.exe", "/c", "start", "taskmgr.exe").start();
                        } catch (Exception ignored) {}
                        break;
                    case "ALT_F4":
                        pressKeyCombo(KeyEvent.VK_ALT, KeyEvent.VK_F4);
                        break;
                    case "ALT_TAB":
                        pressKeyCombo(KeyEvent.VK_ALT, KeyEvent.VK_TAB);
                        break;
                    case "EXPLORER":
                        new ProcessBuilder("explorer.exe").start();
                        break;
                    case "CALC":
                        new ProcessBuilder("calc.exe").start();
                        break;
                    case "NOTEPAD":
                        new ProcessBuilder("notepad.exe").start();
                        break;
                    case "SPOTIFY":
                        Desktop.getDesktop().browse(new URI("spotify:"));
                        break;
                    case "BROWSER":
                        Desktop.getDesktop().browse(new URI("https://google.com"));
                        break;
                }
            } catch (Exception e) {
                System.err.println("Erro ao executar macro " + macro + ": " + e.getMessage());
            }
        }).start();
    }

    public static void executePowerCommand(String action, String param) {
        new Thread(() -> {
            try {
                switch (action.toUpperCase()) {
                    case "LOCK":
                        new ProcessBuilder("rundll32.exe", "user32.dll,LockWorkStation").start();
                        break;
                    case "SLEEP":
                        new ProcessBuilder("rundll32.exe", "powrprof.dll,SetSuspendState", "0,1,0").start();
                        break;
                    case "SHUTDOWN_TIMER":
                        int secs = Integer.parseInt(param);
                        new ProcessBuilder("shutdown", "/s", "/t", String.valueOf(secs)).start();
                        break;
                    case "SHUTDOWN_CANCEL":
                        new ProcessBuilder("shutdown", "/a").start();
                        break;
                    case "SHUTDOWN_NOW":
                        new ProcessBuilder("shutdown", "/s", "/t", "0").start();
                        break;
                    case "RESTART_NOW":
                        new ProcessBuilder("shutdown", "/r", "/t", "0").start();
                        break;
                }
            } catch (Exception e) {
                System.err.println("Erro ao executar comando de energia " + action + ": " + e.getMessage());
            }
        }).start();
    }

    public static byte[] captureScreenshot() {
        return captureScreenFrame(1280, 720);
    }

    public static byte[] captureScreenFrame(int targetW, int targetH) {
        try {
            if (robot == null) return null;
            Rectangle screenRect = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
            BufferedImage screenCapture = robot.createScreenCapture(screenRect);

            int finalW = Math.min(screenCapture.getWidth(), targetW);
            int finalH = (screenCapture.getHeight() * finalW) / screenCapture.getWidth();

            BufferedImage bimg = new BufferedImage(finalW, finalH, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = bimg.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(screenCapture, 0, 0, finalW, finalH, null);
            g.dispose();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
            if (writers.hasNext()) {
                ImageWriter writer = writers.next();
                try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
                    writer.setOutput(ios);
                    ImageWriteParam param = writer.getDefaultWriteParam();
                    if (param.canWriteCompressed()) {
                        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                        param.setCompressionQuality(0.55f);
                    }
                    writer.write(null, new IIOImage(bimg, null, null), param);
                }
                writer.dispose();
            } else {
                ImageIO.write(bimg, "jpg", baos);
            }
            return baos.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }

    public static void copyImageToClipboard(byte[] imageBytes) {
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (img != null) {
                ImageTransferable trans = new ImageTransferable(img);
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(trans, null);
            }
        } catch (Exception e) {
            System.err.println("Erro ao copiar imagem para o clipboard: " + e.getMessage());
        }
    }

    private static void pressKeyCombo(int key1, int key2) {
        if (robot == null) return;
        try {
            robot.keyPress(key1);
            robot.keyPress(key2);
            robot.keyRelease(key2);
            robot.keyRelease(key1);
        } catch (Exception ignored) {}
    }

    private static void pressKeyCombo3(int k1, int k2, int k3) {
        if (robot == null) return;
        try {
            robot.keyPress(k1);
            robot.keyPress(k2);
            robot.keyPress(k3);
            robot.keyRelease(k3);
            robot.keyRelease(k2);
            robot.keyRelease(k1);
        } catch (Exception ignored) {}
    }

    private static void typeKey(int vk) {
        robot.keyPress(vk);
        robot.keyRelease(vk);
    }

    public static void typeText(String text) {
        if (robot == null || text == null) return;
        for (char c : text.toCharArray()) {
            try {
                int code = KeyEvent.getExtendedKeyCodeForChar(c);
                if (code != KeyEvent.VK_UNDEFINED) {
                    robot.keyPress(code);
                    robot.keyRelease(code);
                }
            } catch (Exception ignored) {}
        }
    }

    private static class ImageTransferable implements Transferable {
        private final Image image;

        public ImageTransferable(Image image) {
            this.image = image;
        }

        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[]{DataFlavor.imageFlavor};
        }

        @Override
        public boolean isDataFlavorSupported(DataFlavor flavor) {
            return DataFlavor.imageFlavor.equals(flavor);
        }

        @Override
        public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
            if (!isDataFlavorSupported(flavor)) throw new UnsupportedFlavorException(flavor);
            return image;
        }
    }
}
