import java.awt.Desktop;
import java.net.URI;

/**
 * Utilitário para executar ações de controle remoto no PC,
 * como abrir links no navegador ou controlar mídias do Windows.
 */
public class MediaController {

    public static void executeCommand(String command, String param) {
        new Thread(() -> {
            try {
                switch (command.toUpperCase()) {
                    case "OPEN_URL":
                        if (param != null && (param.startsWith("http://") || param.startsWith("https://"))) {
                            Desktop.getDesktop().browse(new URI(param));
                        }
                        break;
                    case "PLAY_PAUSE":
                        sendWindowsMediaKey(179);
                        break;
                    case "NEXT_TRACK":
                        sendWindowsMediaKey(176);
                        break;
                    case "PREV_TRACK":
                        sendWindowsMediaKey(177);
                        break;
                    case "VOL_UP":
                        sendWindowsMediaKey(175);
                        break;
                    case "VOL_DOWN":
                        sendWindowsMediaKey(174);
                        break;
                    case "MUTE":
                        sendWindowsMediaKey(173);
                        break;
                    default:
                        System.out.println("Comando desconhecido: " + command);
                }
            } catch (Exception e) {
                System.err.println("Erro ao executar comando " + command + ": " + e.getMessage());
            }
        }).start();
    }

    private static void sendWindowsMediaKey(int keyCode) {
        try {
            // Executa via WScript.Shell SendKeys no Windows
            String psCommand = "$w = New-Object -ComObject Wscript.Shell; $w.SendKeys([char]" + keyCode + ")";
            ProcessBuilder pb = new ProcessBuilder("powershell", "-NoProfile", "-WindowStyle", "Hidden", "-Command", psCommand);
            pb.start();
        } catch (Exception e) {
            System.err.println("Falha ao simular tecla multimídia: " + e.getMessage());
        }
    }
}
