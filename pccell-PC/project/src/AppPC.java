import ui.ModernComponents;
import ui.ModernComponents.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.dnd.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import javax.imageio.ImageIO;

public class AppPC extends JFrame {

    private static final int PORTA = 5050;

    private final JLabel statusLabel = new JLabel("○ Aguardando conexão");
    private final RoundedPanel statusPill = new RoundedPanel(999, ModernComponents.BG_ELEVATED, ModernComponents.BORDER_SUBTLE);
    private final JLabel ipLabel = new JLabel();
    private final JPanel historyPanel = new JPanel();
    private final JScrollPane scrollPane;
    private final ModernTextField inputField = new ModernTextField("Enviar mensagem ao celular...");
    private final ModernButton sendButton = new ModernButton("Enviar", true, 18);
    private final ModernButton fileButton = new ModernButton("Arquivos", false);
    private final ModernButton clipboardButton = new ModernButton("Clipboard", false);
    private final ModernButton openFolderButton = new ModernButton("Downloads", false);
    private final JProgressBar progressBar = new JProgressBar(0, 100);
    private final JLabel progressLabel = new JLabel("");

    private JFrame cameraWindow;
    private JLabel cameraFeedLabel;
    private TrayIcon trayIcon;

    private final TcpServer server;
    private boolean isConnected = false;

    public AppPC() {
        super("PCcell");
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setSize(640, 700);
        setMinimumSize(new Dimension(500, 520));
        setLocationRelativeTo(null);
        getContentPane().setBackground(ModernComponents.BG_CANVAS);
        setIconImage(ModernComponents.createAppIcon(64));

        JPanel topPanel = createHeaderPanel();

        historyPanel.setLayout(new BoxLayout(historyPanel, BoxLayout.Y_AXIS));
        historyPanel.setBackground(ModernComponents.BG_CANVAS);
        historyPanel.setBorder(new EmptyBorder(12, 14, 12, 14));

        scrollPane = new JScrollPane(historyPanel);
        scrollPane.setBorder(null);
        scrollPane.setBackground(ModernComponents.BG_CANVAS);
        scrollPane.getViewport().setBackground(ModernComponents.BG_CANVAS);
        ModernComponents.styleScrollBar(scrollPane);

        // Zona de Drag and Drop minimalista
        RoundedPanel dropZoneBanner = new RoundedPanel(12, ModernComponents.BG_SURFACE, ModernComponents.BORDER_SUBTLE);
        dropZoneBanner.setLayout(new BorderLayout());
        dropZoneBanner.setBorder(new EmptyBorder(10, 14, 10, 14));
        JLabel dropLabel = new JLabel("Arraste arquivos para esta janela para enviar ao celular", SwingConstants.CENTER);
        dropLabel.setForeground(ModernComponents.TEXT_MUTED);
        dropLabel.setFont(ModernComponents.uiFont(Font.PLAIN, 12));
        dropZoneBanner.add(dropLabel, BorderLayout.CENTER);

        JPanel dropWrapper = new JPanel(new BorderLayout());
        dropWrapper.setBackground(ModernComponents.BG_CANVAS);
        dropWrapper.setBorder(new EmptyBorder(12, 16, 4, 16));
        dropWrapper.add(dropZoneBanner, BorderLayout.CENTER);

        JPanel centerContainer = new JPanel(new BorderLayout());
        centerContainer.setBackground(ModernComponents.BG_CANVAS);
        centerContainer.add(dropWrapper, BorderLayout.NORTH);
        centerContainer.add(scrollPane, BorderLayout.CENTER);

        // Progresso minimalista
        JPanel progressPanel = new JPanel(new BorderLayout(10, 4));
        progressPanel.setBackground(ModernComponents.BG_CANVAS);
        progressPanel.setBorder(new EmptyBorder(4, 16, 6, 16));
        progressBar.setBorderPainted(false);
        progressBar.setBackground(ModernComponents.BG_ELEVATED);
        progressBar.setForeground(ModernComponents.ACCENT_BLUE);
        progressBar.setPreferredSize(new Dimension(0, 6));
        progressBar.setVisible(false);
        progressLabel.setForeground(ModernComponents.TEXT_SECONDARY);
        progressLabel.setFont(ModernComponents.uiFont(Font.PLAIN, 11));
        progressPanel.add(progressLabel, BorderLayout.WEST);
        progressPanel.add(progressBar, BorderLayout.CENTER);

        JPanel bottomPanel = createBottomPanel(progressPanel);

        setLayout(new BorderLayout());
        add(topPanel, BorderLayout.NORTH);
        add(centerContainer, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);

        setupDragAndDrop();
        setupSystemTray();

        server = new TcpServer(PORTA, new TcpServer.ServerListener() {
            @Override
            public void onStatusChanged(String status, boolean conectado) {
                SwingUtilities.invokeLater(() -> {
                    isConnected = conectado;
                    statusLabel.setText((conectado ? "● " : "○ ") + status);
                    statusLabel.setForeground(conectado ? ModernComponents.STATUS_SUCCESS : ModernComponents.STATUS_WARNING);
                    statusPill.setStrokeColor(conectado ? new Color(16, 185, 129, 90) : ModernComponents.BORDER_SUBTLE);
                    sendButton.setEnabled(conectado);
                    fileButton.setEnabled(conectado);
                    clipboardButton.setEnabled(conectado);
                    inputField.setEnabled(conectado);
                    if (conectado) {
                        inputField.requestFocusInWindow();
                    }
                });
            }

            @Override
            public void onTextReceived(String mensagem) {
                SwingUtilities.invokeLater(() -> addTextMessage("Celular", mensagem, false));
            }

            @Override
            public void onClipboardReceived(String clipboardText) {
                SwingUtilities.invokeLater(() -> {
                    setSystemClipboard(clipboardText);
                    addClipboardCard("Celular", clipboardText);
                });
            }

            @Override
            public void onCommandReceived(String comando, String parametro) {
                SwingUtilities.invokeLater(() -> {
                    if ("CAM_STOP".equals(comando)) {
                        fecharJanelaCamera();
                    } else if (!comando.startsWith("MOUSE")
                            && !comando.startsWith("KEY")
                            && !comando.startsWith("GAMEPAD")
                            && !comando.startsWith("REMOTE_MOUSE")
                            && !comando.startsWith("STREAM_SCREEN")) {
                        addCommandCard("Controle remoto", comando + (parametro.isEmpty() ? "" : " (" + parametro + ")"));
                    }
                });
            }

            @Override
            public void onImagePastedToClipboard() {
                SwingUtilities.invokeLater(() -> {
                    Toolkit.getDefaultToolkit().beep();
                    addSystemMessage("Foto recebida e copiada para a area de transferencia (Ctrl+V).", false);
                    if (trayIcon != null) {
                        trayIcon.displayMessage("PCcell", "Foto disponivel para colar com Ctrl+V.", TrayIcon.MessageType.INFO);
                    }
                });
            }

            @Override
            public void onCameraFrameReceived(byte[] frameData) {
                SwingUtilities.invokeLater(() -> exibirFrameCamera(frameData));
            }

            @Override
            public void onFileReceived(File arquivo) {
                SwingUtilities.invokeLater(() -> {
                    Toolkit.getDefaultToolkit().beep();
                    addFileCard("Celular", arquivo, false);
                    if (trayIcon != null) {
                        trayIcon.displayMessage("Arquivo recebido", arquivo.getName() + " salvo em Downloads.", TrayIcon.MessageType.INFO);
                    }
                });
            }

            @Override
            public void onFileSent(File arquivo) {
                SwingUtilities.invokeLater(() -> addFileCard("Você", arquivo, true));
            }

            @Override
            public void onProgress(String descricao, int porcentagem) {
                SwingUtilities.invokeLater(() -> {
                    if (descricao == null || descricao.isEmpty() || porcentagem >= 100) {
                        progressBar.setVisible(false);
                        progressLabel.setText("");
                    } else {
                        progressBar.setVisible(true);
                        progressBar.setValue(porcentagem);
                        progressLabel.setText(descricao + " (" + porcentagem + "%)");
                    }
                });
            }

            @Override
            public void onError(String erro) {
                SwingUtilities.invokeLater(() -> addSystemMessage("Erro: " + erro, true));
            }
        });

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (SystemTray.isSupported()) {
                    setVisible(false);
                    if (trayIcon != null) {
                        trayIcon.displayMessage("PCcell", "Em execucao na bandeja do sistema.", TrayIcon.MessageType.INFO);
                    }
                } else {
                    encerrarAplicativo();
                }
            }
        });

        server.start();
        addSystemMessage("Servidor ativo na porta " + PORTA + ". Aguardando dispositivo.", false);
    }

    private void setupSystemTray() {
        if (!SystemTray.isSupported()) return;

        try {
            SystemTray tray = SystemTray.getSystemTray();
            Image iconImage = ModernComponents.createAppIcon(32);

            PopupMenu popup = new PopupMenu();
            MenuItem openItem = new MenuItem("Abrir PCcell");
            openItem.addActionListener(e -> {
                setVisible(true);
                setExtendedState(JFrame.NORMAL);
                toFront();
            });

            MenuItem folderItem = new MenuItem("Pasta Downloads");
            folderItem.addActionListener(e -> abrirPastaDownloads());

            MenuItem exitItem = new MenuItem("Sair");
            exitItem.addActionListener(e -> encerrarAplicativo());

            popup.add(openItem);
            popup.add(folderItem);
            popup.addSeparator();
            popup.add(exitItem);

            trayIcon = new TrayIcon(iconImage, "PCcell Desktop", popup);
            trayIcon.setImageAutoSize(true);
            trayIcon.addActionListener(e -> {
                setVisible(true);
                setExtendedState(JFrame.NORMAL);
                toFront();
            });

            tray.add(trayIcon);
        } catch (Exception e) {
            System.err.println("Nao foi possivel registrar SystemTray: " + e.getMessage());
        }
    }

    private void encerrarAplicativo() {
        if (server != null) server.stop();
        if (cameraWindow != null) cameraWindow.dispose();
        System.exit(0);
    }

    private void exibirFrameCamera(byte[] jpegBytes) {
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(jpegBytes));
            if (img == null) return;

            if (cameraWindow == null) {
                cameraWindow = new JFrame("PCcell Webcam");
                cameraWindow.setSize(540, 720);
                cameraWindow.setLocationRelativeTo(this);
                cameraWindow.setIconImage(ModernComponents.createAppIcon(64));
                cameraFeedLabel = new JLabel("", SwingConstants.CENTER);
                cameraFeedLabel.setBackground(ModernComponents.BG_CANVAS);
                cameraFeedLabel.setOpaque(true);
                cameraWindow.add(cameraFeedLabel);
                cameraWindow.addWindowListener(new WindowAdapter() {
                    @Override
                    public void windowClosing(WindowEvent e) {
                        fecharJanelaCamera();
                    }
                });
                cameraWindow.setVisible(true);
            }

            int w = cameraFeedLabel.getWidth() > 0 ? cameraFeedLabel.getWidth() : 480;
            int h = cameraFeedLabel.getHeight() > 0 ? cameraFeedLabel.getHeight() : 640;
            Image scaled = img.getScaledInstance(w, h, Image.SCALE_FAST);
            cameraFeedLabel.setIcon(new ImageIcon(scaled));
        } catch (Exception ignored) {}
    }

    private void fecharJanelaCamera() {
        if (cameraWindow != null) {
            cameraWindow.setVisible(false);
            cameraWindow.dispose();
            cameraWindow = null;
        }
    }

    private JPanel createHeaderPanel() {
        JPanel header = new JPanel(new BorderLayout(12, 8));
        header.setBackground(ModernComponents.BG_SURFACE);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, ModernComponents.BORDER_SUBTLE),
                new EmptyBorder(14, 18, 14, 18)
        ));

        JPanel titleRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        titleRow.setOpaque(false);

        JLabel title = new JLabel("PCcell");
        title.setForeground(ModernComponents.TEXT_PRIMARY);
        title.setFont(ModernComponents.uiFont(Font.BOLD, 18));

        JLabel subtitle = new JLabel("  •  Porta " + PORTA);
        subtitle.setForeground(ModernComponents.TEXT_MUTED);
        subtitle.setFont(ModernComponents.uiFont(Font.PLAIN, 12));

        titleRow.add(title);
        titleRow.add(subtitle);

        String ipLocal = TcpServer.getLocalIpAddress();
        ipLabel.setText(ipLocal);
        ipLabel.setForeground(ModernComponents.ACCENT_CYAN);
        ipLabel.setFont(ModernComponents.monoFont(Font.BOLD, 13));

        ModernButton copyIpBtn = new ModernButton("Copiar IP", false, 8);
        copyIpBtn.setFont(ModernComponents.uiFont(Font.BOLD, 11));
        copyIpBtn.setBorder(new EmptyBorder(5, 10, 5, 10));
        copyIpBtn.addActionListener(e -> {
            setSystemClipboard(ipLocal);
            copyIpBtn.setText("Copiado");
            Timer t = new Timer(1500, ev -> copyIpBtn.setText("Copiar IP"));
            t.setRepeats(false);
            t.start();
        });

        JPanel ipContainer = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        ipContainer.setOpaque(false);
        JLabel ipPrefix = new JLabel("IP Local:");
        ipPrefix.setForeground(ModernComponents.TEXT_SECONDARY);
        ipPrefix.setFont(ModernComponents.uiFont(Font.PLAIN, 12));
        ipContainer.add(ipPrefix);
        ipContainer.add(ipLabel);
        ipContainer.add(copyIpBtn);

        JPanel leftBox = new JPanel(new GridLayout(2, 1, 0, 4));
        leftBox.setOpaque(false);
        leftBox.add(titleRow);
        leftBox.add(ipContainer);

        statusLabel.setForeground(ModernComponents.STATUS_WARNING);
        statusLabel.setFont(ModernComponents.uiFont(Font.BOLD, 11));
        statusPill.setLayout(new BorderLayout());
        statusPill.setBorder(new EmptyBorder(6, 12, 6, 12));
        statusPill.add(statusLabel, BorderLayout.CENTER);

        JPanel rightWrapper = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 4));
        rightWrapper.setOpaque(false);
        rightWrapper.add(statusPill);

        header.add(leftBox, BorderLayout.WEST);
        header.add(rightWrapper, BorderLayout.EAST);
        return header;
    }

    private JPanel createBottomPanel(JPanel progressPanel) {
        JPanel bottomContainer = new JPanel(new BorderLayout());
        bottomContainer.setBackground(ModernComponents.BG_CANVAS);
        bottomContainer.add(progressPanel, BorderLayout.NORTH);

        JPanel surfaceFooter = new JPanel(new BorderLayout());
        surfaceFooter.setBackground(ModernComponents.BG_SURFACE);
        surfaceFooter.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, ModernComponents.BORDER_SUBTLE));

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        toolbar.setOpaque(false);
        toolbar.setBorder(new EmptyBorder(10, 14, 6, 14));

        fileButton.setEnabled(false);
        fileButton.addActionListener(e -> escolherEEnviarArquivos());

        clipboardButton.setEnabled(false);
        clipboardButton.addActionListener(e -> enviarClipboard());

        openFolderButton.addActionListener(e -> abrirPastaDownloads());

        toolbar.add(fileButton);
        toolbar.add(clipboardButton);
        toolbar.add(openFolderButton);

        JPanel inputBar = new JPanel(new BorderLayout(10, 0));
        inputBar.setOpaque(false);
        inputBar.setBorder(new EmptyBorder(6, 14, 14, 14));

        inputField.setEnabled(false);
        inputField.addActionListener(e -> enviarMensagem());

        sendButton.setEnabled(false);
        sendButton.setBorder(new EmptyBorder(10, 20, 10, 20));
        sendButton.addActionListener(e -> enviarMensagem());

        inputBar.add(inputField, BorderLayout.CENTER);
        inputBar.add(sendButton, BorderLayout.EAST);

        surfaceFooter.add(toolbar, BorderLayout.NORTH);
        surfaceFooter.add(inputBar, BorderLayout.SOUTH);

        bottomContainer.add(surfaceFooter, BorderLayout.CENTER);
        return bottomContainer;
    }

    private void setupDragAndDrop() {
        new DropTarget(this, new DropTargetListener() {
            @Override
            public void dragEnter(DropTargetDragEvent dtde) {
                if (isConnected && dtde.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                    dtde.acceptDrag(DnDConstants.ACTION_COPY);
                } else {
                    dtde.rejectDrag();
                }
            }
            @Override public void dragOver(DropTargetDragEvent dtde) {}
            @Override public void dropActionChanged(DropTargetDragEvent dtde) {}
            @Override public void dragExit(DropTargetEvent dte) {}

            @Override
            @SuppressWarnings("unchecked")
            public void drop(DropTargetDropEvent dtde) {
                try {
                    if (!isConnected) {
                        dtde.rejectDrop();
                        JOptionPane.showMessageDialog(AppPC.this, "Nenhum celular conectado no momento.", "Aviso", JOptionPane.WARNING_MESSAGE);
                        return;
                    }
                    dtde.acceptDrop(DnDConstants.ACTION_COPY);
                    Transferable transferable = dtde.getTransferable();
                    List<File> files = (List<File>) transferable.getTransferData(DataFlavor.javaFileListFlavor);
                    if (files != null && !files.isEmpty()) {
                        server.sendFiles(files);
                    }
                    dtde.dropComplete(true);
                } catch (Exception ex) {
                    ex.printStackTrace();
                    dtde.dropComplete(false);
                }
            }
        });
    }

    private void escolherEEnviarArquivos() {
        JFileChooser chooser = new JFileChooser();
        chooser.setMultiSelectionEnabled(true);
        chooser.setDialogTitle("Selecione arquivos para enviar");
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File[] selected = chooser.getSelectedFiles();
            if (selected != null) {
                for (File f : selected) {
                    server.sendFile(f);
                }
            }
        }
    }

    private void enviarClipboard() {
        String texto = getSystemClipboard();
        if (texto != null && !texto.isEmpty()) {
            server.sendClipboard(texto);
            addClipboardCard("Você", texto);
        } else {
            JOptionPane.showMessageDialog(this, "A area de transferencia esta vazia.", "Aviso", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void abrirPastaDownloads() {
        try {
            File pasta = server.getPastaDownloads();
            if (!pasta.exists()) pasta.mkdirs();
            Desktop.getDesktop().open(pasta);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Nao foi possivel abrir a pasta: " + e.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void enviarMensagem() {
        String texto = inputField.getText().trim();
        if (texto.isEmpty()) return;

        server.sendText(texto);
        addTextMessage("Você", texto, true);
        inputField.setText("");
    }

    private void addTextMessage(String remetente, String texto, boolean isSent) {
        RoundedPanel card = createBaseCard(remetente, isSent);
        JLabel content = new JLabel("<html><body style='width: 300px; word-wrap: break-word;'>" + escapeHtml(texto) + "</body></html>");
        content.setForeground(ModernComponents.TEXT_PRIMARY);
        content.setFont(ModernComponents.uiFont(Font.PLAIN, 13));
        card.add(content, BorderLayout.CENTER);

        if (texto.startsWith("http://") || texto.startsWith("https://")) {
            ModernButton openLinkBtn = new ModernButton("Abrir link", false, 8);
            openLinkBtn.setFont(ModernComponents.uiFont(Font.BOLD, 11));
            openLinkBtn.setBorder(new EmptyBorder(5, 10, 5, 10));
            openLinkBtn.addActionListener(e -> {
                try { Desktop.getDesktop().browse(new URI(texto)); } catch (Exception ignored) {}
            });
            JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 6));
            actionPanel.setOpaque(false);
            actionPanel.add(openLinkBtn);
            card.add(actionPanel, BorderLayout.SOUTH);
        }

        appendRowToHistory(card, isSent);
    }

    private void addFileCard(String remetente, File arquivo, boolean isSent) {
        RoundedPanel card = createBaseCard(remetente, isSent);

        JPanel contentBox = new JPanel(new BorderLayout(8, 8));
        contentBox.setOpaque(false);

        if (isImageFile(arquivo.getName())) {
            try {
                BufferedImage bimg = ImageIO.read(arquivo);
                if (bimg != null) {
                    int maxW = 180, maxH = 130;
                    int origW = bimg.getWidth(), origH = bimg.getHeight();
                    int newW = maxW, newH = maxH;
                    if (origW > origH) {
                        newH = (origH * maxW) / origW;
                    } else {
                        newW = (origW * maxH) / origH;
                    }
                    Image thumb = bimg.getScaledInstance(Math.max(1, newW), Math.max(1, newH), Image.SCALE_SMOOTH);
                    JLabel thumbLabel = new JLabel(new ImageIcon(thumb));
                    contentBox.add(thumbLabel, BorderLayout.NORTH);
                }
            } catch (Exception ignored) {}
        }

        String sizeText = formatFileSize(arquivo.length());
        JLabel fileInfo = new JLabel("<html><b>" + escapeHtml(arquivo.getName()) + "</b> <span style='color:#a1a1aa;'>(" + sizeText + ")</span></html>");
        fileInfo.setForeground(ModernComponents.TEXT_PRIMARY);
        fileInfo.setFont(ModernComponents.uiFont(Font.PLAIN, 13));
        contentBox.add(fileInfo, BorderLayout.CENTER);
        card.add(contentBox, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));
        actions.setOpaque(false);

        ModernButton openBtn = new ModernButton("Abrir", false, 8);
        openBtn.setFont(ModernComponents.uiFont(Font.BOLD, 11));
        openBtn.setBorder(new EmptyBorder(5, 10, 5, 10));
        openBtn.addActionListener(e -> {
            try {
                Desktop.getDesktop().open(arquivo);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Nao foi possivel abrir o arquivo: " + ex.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
            }
        });

        ModernButton folderBtn = new ModernButton("Mostrar na pasta", false, 8);
        folderBtn.setFont(ModernComponents.uiFont(Font.BOLD, 11));
        folderBtn.setBorder(new EmptyBorder(5, 10, 5, 10));
        folderBtn.addActionListener(e -> {
            try {
                Runtime.getRuntime().exec(new String[]{"explorer.exe", "/select,", arquivo.getAbsolutePath()});
            } catch (Exception ex) {
                try { Desktop.getDesktop().open(arquivo.getParentFile()); } catch (Exception ignored) {}
            }
        });

        actions.add(openBtn);
        actions.add(folderBtn);
        card.add(actions, BorderLayout.SOUTH);

        appendRowToHistory(card, isSent);
    }

    private boolean isImageFile(String name) {
        String lower = name.toLowerCase();
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".webp") || lower.endsWith(".gif");
    }

    private void addClipboardCard(String remetente, String clipboardText) {
        boolean isSent = remetente.startsWith("Você");
        RoundedPanel card = createBaseCard(remetente + " • Clipboard", isSent);
        String preview = clipboardText.length() > 120 ? clipboardText.substring(0, 120) + "..." : clipboardText;
        JLabel lbl = new JLabel("<html><body style='width: 280px; word-wrap: break-word;'>" + escapeHtml(preview) + "</body></html>");
        lbl.setForeground(ModernComponents.TEXT_PRIMARY);
        lbl.setFont(ModernComponents.uiFont(Font.PLAIN, 12));
        card.add(lbl, BorderLayout.CENTER);

        ModernButton copyBtn = new ModernButton("Copiar", false, 8);
        copyBtn.setFont(ModernComponents.uiFont(Font.BOLD, 11));
        copyBtn.setBorder(new EmptyBorder(5, 10, 5, 10));
        copyBtn.addActionListener(e -> setSystemClipboard(clipboardText));

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 6));
        actions.setOpaque(false);
        actions.add(copyBtn);
        card.add(actions, BorderLayout.SOUTH);

        appendRowToHistory(card, isSent);
    }

    private void addCommandCard(String titulo, String detalhe) {
        RoundedPanel card = createBaseCard(titulo, false);
        JLabel lbl = new JLabel(detalhe);
        lbl.setForeground(ModernComponents.TEXT_SECONDARY);
        lbl.setFont(ModernComponents.uiFont(Font.PLAIN, 12));
        card.add(lbl, BorderLayout.CENTER);
        appendRowToHistory(card, false);
    }

    private void addSystemMessage(String msg, boolean isError) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.CENTER));
        p.setOpaque(false);
        JLabel l = new JLabel(msg);
        l.setForeground(isError ? ModernComponents.STATUS_ERROR : ModernComponents.TEXT_MUTED);
        l.setFont(ModernComponents.uiFont(Font.PLAIN, 11));
        p.add(l);

        historyPanel.add(p);
        historyPanel.revalidate();
        historyPanel.repaint();
    }

    private RoundedPanel createBaseCard(String remetente, boolean isSent) {
        Color bg = isSent ? ModernComponents.ACCENT_BLUE : ModernComponents.BG_ELEVATED;
        Color border = isSent ? null : ModernComponents.BORDER_SUBTLE;
        RoundedPanel card = new RoundedPanel(14, bg, border);
        card.setLayout(new BorderLayout(4, 4));
        card.setBorder(new EmptyBorder(10, 14, 10, 14));

        String time = new SimpleDateFormat("HH:mm").format(new Date());
        JLabel header = new JLabel(remetente + " • " + time);
        header.setForeground(isSent ? new Color(191, 219, 254) : ModernComponents.TEXT_MUTED);
        header.setFont(ModernComponents.uiFont(Font.BOLD, 10));
        card.add(header, BorderLayout.NORTH);
        return card;
    }

    private void appendRowToHistory(JPanel card, boolean isSent) {
        JPanel row = new JPanel(new FlowLayout(isSent ? FlowLayout.RIGHT : FlowLayout.LEFT, 4, 2));
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, card.getPreferredSize().height + 10));
        row.add(card);

        historyPanel.add(row);
        historyPanel.add(Box.createRigidArea(new Dimension(0, 6)));
        historyPanel.revalidate();
        historyPanel.repaint();

        SwingUtilities.invokeLater(() -> {
            JScrollBar vertical = scrollPane.getVerticalScrollBar();
            vertical.setValue(vertical.getMaximum());
        });
    }

    private String getSystemClipboard() {
        try {
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            Transferable contents = clipboard.getContents(null);
            if (contents != null && contents.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                return (String) contents.getTransferData(DataFlavor.stringFlavor);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private void setSystemClipboard(String text) {
        try {
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(new StringSelection(text), null);
        } catch (Exception ignored) {}
    }

    private static String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        char pre = "KMGTPE".charAt(Math.max(0, exp - 1));
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }

    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br/>");
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new AppPC().setVisible(true));
    }
}
