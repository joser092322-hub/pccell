import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
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

    // Elementos visuais
    private final JLabel statusLabel = new JLabel("● Aguardando conexão do celular...");
    private final JLabel ipLabel = new JLabel();
    private final JPanel historyPanel = new JPanel();
    private final JScrollPane scrollPane;
    private final JTextField inputField = new JTextField();
    private final JButton sendButton = new JButton("Enviar");
    private final JButton fileButton = new JButton("📁 Arquivos...");
    private final JButton clipboardButton = new JButton("📋 Enviar Clipboard");
    private final JButton openFolderButton = new JButton("📂 Downloads");
    private final JProgressBar progressBar = new JProgressBar(0, 100);
    private final JLabel progressLabel = new JLabel("");

    // Janela de Câmera Remota
    private JFrame cameraWindow;
    private JLabel cameraFeedLabel;

    // System Tray
    private TrayIcon trayIcon;

    private final TcpServer server;
    private boolean isConnected = false;

    // Cores modernas
    private static final Color COLOR_BG = new Color(24, 24, 37);
    private static final Color COLOR_SURFACE = new Color(30, 30, 46);
    private static final Color COLOR_CARD = new Color(40, 40, 60);
    private static final Color COLOR_TEXT = new Color(241, 245, 249);
    private static final Color COLOR_MUTED = new Color(148, 163, 184);
    private static final Color COLOR_ACCENT = new Color(37, 99, 235);
    private static final Color COLOR_SUCCESS = new Color(16, 185, 129);
    private static final Color COLOR_WARNING = new Color(245, 158, 11);

    public AppPC() {
        super("PCcell Desktop - Conexão PC ↔ Celular");
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setSize(650, 680);
        setMinimumSize(new Dimension(500, 500));
        setLocationRelativeTo(null);
        getContentPane().setBackground(COLOR_BG);

        // Painel Superior (Cabeçalho)
        JPanel topPanel = createHeaderPanel();

        // Painel Central (Feed de Mensagens e Arquivos com Drag and Drop)
        historyPanel.setLayout(new BoxLayout(historyPanel, BoxLayout.Y_AXIS));
        historyPanel.setBackground(COLOR_BG);
        historyPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        scrollPane = new JScrollPane(historyPanel);
        scrollPane.setBorder(null);
        scrollPane.setBackground(COLOR_BG);
        scrollPane.getViewport().setBackground(COLOR_BG);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        // Painel de Drag and Drop informativo
        JPanel dropZoneBanner = new JPanel(new BorderLayout());
        dropZoneBanner.setBackground(COLOR_SURFACE);
        dropZoneBanner.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createDashedBorder(COLOR_MUTED, 2, 4, 4, true),
                new EmptyBorder(10, 12, 10, 12)
        ));
        JLabel dropLabel = new JLabel("📥 Arraste e solte arquivos aqui para enviar direto ao celular", SwingConstants.CENTER);
        dropLabel.setForeground(COLOR_MUTED);
        dropLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
        dropZoneBanner.add(dropLabel, BorderLayout.CENTER);

        JPanel centerContainer = new JPanel(new BorderLayout());
        centerContainer.setBackground(COLOR_BG);
        centerContainer.add(dropZoneBanner, BorderLayout.NORTH);
        centerContainer.add(scrollPane, BorderLayout.CENTER);

        // Barra de Progresso de Envio/Recebimento
        JPanel progressPanel = new JPanel(new BorderLayout(5, 5));
        progressPanel.setBackground(COLOR_BG);
        progressPanel.setBorder(new EmptyBorder(4, 16, 4, 16));
        progressBar.setStringPainted(true);
        progressBar.setVisible(false);
        progressLabel.setForeground(COLOR_TEXT);
        progressLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        progressPanel.add(progressLabel, BorderLayout.WEST);
        progressPanel.add(progressBar, BorderLayout.CENTER);

        // Barra Inferior de Envio e Ações
        JPanel bottomPanel = createBottomPanel(progressPanel);

        setLayout(new BorderLayout());
        add(topPanel, BorderLayout.NORTH);
        add(centerContainer, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);

        // Configura Drag and Drop
        setupDragAndDrop();

        // Configura Bandeja do Windows (System Tray)
        setupSystemTray();

        // Inicializa o servidor TCP com callbacks completos
        server = new TcpServer(PORTA, new TcpServer.ServerListener() {
            @Override
            public void onStatusChanged(String status, boolean conectado) {
                SwingUtilities.invokeLater(() -> {
                    isConnected = conectado;
                    statusLabel.setText((conectado ? "● " : "○ ") + status);
                    statusLabel.setForeground(conectado ? COLOR_SUCCESS : COLOR_WARNING);
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
                    } else if (!comando.startsWith("MOUSE") && !comando.startsWith("KEY") && !comando.startsWith("GAMEPAD")) {
                        addCommandCard("Comando Remoto", comando + (parametro.isEmpty() ? "" : ": " + parametro));
                    }
                });
            }

            @Override
            public void onImagePastedToClipboard() {
                SwingUtilities.invokeLater(() -> {
                    Toolkit.getDefaultToolkit().beep();
                    addSystemMessage("📸 Foto recebida do celular e colada no Ctrl+V do Windows!", false);
                    if (trayIcon != null) {
                        trayIcon.displayMessage("Foto no Clipboard!", "A foto tirada no celular já pode ser colada com Ctrl+V.", TrayIcon.MessageType.INFO);
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
                        trayIcon.displayMessage("Arquivo Recebido!", arquivo.getName() + " foi salvo em Downloads.", TrayIcon.MessageType.INFO);
                    }
                });
            }

            @Override
            public void onFileSent(File arquivo) {
                SwingUtilities.invokeLater(() -> addFileCard("Você (PC)", arquivo, true));
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
                SwingUtilities.invokeLater(() -> addSystemMessage("[Aviso/Erro] " + erro, true));
            }
        });

        // Evento de fechar janela -> Minimiza para o Tray
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (SystemTray.isSupported()) {
                    setVisible(false);
                    if (trayIcon != null) {
                        trayIcon.displayMessage("PCcell", "Minimizado para a bandeja do sistema. Clique 2 vezes para reabrir.", TrayIcon.MessageType.INFO);
                    }
                } else {
                    encerrarAplicativo();
                }
            }
        });

        server.start();
        addSystemMessage("Servidor PCcell iniciado! Descoberta e recursos remotos prontos.", false);
    }

    private void setupSystemTray() {
        if (!SystemTray.isSupported()) return;

        try {
            SystemTray tray = SystemTray.getSystemTray();
            Image iconImage = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = (Graphics2D) iconImage.getGraphics();
            g.setColor(COLOR_ACCENT);
            g.fillOval(0, 0, 16, 16);
            g.dispose();

            PopupMenu popup = new PopupMenu();
            MenuItem openItem = new MenuItem("Abrir PCcell");
            openItem.addActionListener(e -> {
                setVisible(true);
                setExtendedState(JFrame.NORMAL);
                toFront();
            });

            MenuItem folderItem = new MenuItem("Abrir Pasta Downloads");
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
            System.err.println("Não foi possível registrar SystemTray: " + e.getMessage());
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
                cameraWindow = new JFrame("📷 Câmera Remota do Celular (Webcam)");
                cameraWindow.setSize(540, 720);
                cameraWindow.setLocationRelativeTo(this);
                cameraFeedLabel = new JLabel("", SwingConstants.CENTER);
                cameraFeedLabel.setBackground(Color.BLACK);
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
        JPanel header = new JPanel(new BorderLayout(10, 6));
        header.setBackground(COLOR_SURFACE);
        header.setBorder(new EmptyBorder(14, 16, 14, 16));

        String ipLocal = TcpServer.getLocalIpAddress();
        ipLabel.setText("IP do PC: " + ipLocal + "  |  Porta: " + PORTA);
        ipLabel.setForeground(COLOR_TEXT);
        ipLabel.setFont(new Font("SansSerif", Font.BOLD, 14));

        JButton copyIpBtn = new JButton("Copiar IP");
        copyIpBtn.setFocusPainted(false);
        copyIpBtn.addActionListener(e -> {
            setSystemClipboard(ipLocal);
            JOptionPane.showMessageDialog(this, "IP copiado: " + ipLocal, "Copiado", JOptionPane.INFORMATION_MESSAGE);
        });

        JPanel ipContainer = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        ipContainer.setOpaque(false);
        ipContainer.add(ipLabel);
        ipContainer.add(copyIpBtn);

        statusLabel.setForeground(COLOR_WARNING);
        statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 13));

        JLabel infoSub = new JLabel("📡 Descoberta ativa • Touchpad, Explorador e Câmera habilitados");
        infoSub.setForeground(COLOR_MUTED);
        infoSub.setFont(new Font("SansSerif", Font.PLAIN, 11));

        JPanel leftBox = new JPanel(new GridLayout(3, 1, 0, 3));
        leftBox.setOpaque(false);
        leftBox.add(ipContainer);
        leftBox.add(statusLabel);
        leftBox.add(infoSub);

        header.add(leftBox, BorderLayout.WEST);
        return header;
    }

    private JPanel createBottomPanel(JPanel progressPanel) {
        JPanel bottomContainer = new JPanel(new BorderLayout(0, 4));
        bottomContainer.setBackground(COLOR_BG);
        bottomContainer.add(progressPanel, BorderLayout.NORTH);

        // Toolbar de botões
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        toolbar.setBackground(COLOR_SURFACE);
        toolbar.setBorder(new EmptyBorder(2, 8, 2, 8));

        fileButton.setEnabled(false);
        fileButton.addActionListener(e -> escolherEEnviarArquivos());

        clipboardButton.setEnabled(false);
        clipboardButton.addActionListener(e -> enviarClipboard());

        openFolderButton.addActionListener(e -> abrirPastaDownloads());

        toolbar.add(fileButton);
        toolbar.add(clipboardButton);
        toolbar.add(openFolderButton);

        // Barra de digitação
        JPanel inputBar = new JPanel(new BorderLayout(8, 0));
        inputBar.setBackground(COLOR_SURFACE);
        inputBar.setBorder(new EmptyBorder(8, 12, 12, 12));

        inputField.setFont(new Font("SansSerif", Font.PLAIN, 14));
        inputField.setEnabled(false);
        inputField.addActionListener(e -> enviarMensagem());

        sendButton.setEnabled(false);
        sendButton.setBackground(COLOR_ACCENT);
        sendButton.setForeground(Color.WHITE);
        sendButton.setFont(new Font("SansSerif", Font.BOLD, 13));
        sendButton.addActionListener(e -> enviarMensagem());

        inputBar.add(inputField, BorderLayout.CENTER);
        inputBar.add(sendButton, BorderLayout.EAST);

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.add(toolbar, BorderLayout.NORTH);
        wrapper.add(inputBar, BorderLayout.SOUTH);

        bottomContainer.add(wrapper, BorderLayout.CENTER);
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
        chooser.setDialogTitle("Selecione arquivos para enviar ao celular");
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
            addClipboardCard("Você (PC)", texto);
        } else {
            JOptionPane.showMessageDialog(this, "A área de transferência está vazia ou não contém texto.", "Aviso", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void abrirPastaDownloads() {
        try {
            File pasta = server.getPastaDownloads();
            if (!pasta.exists()) pasta.mkdirs();
            Desktop.getDesktop().open(pasta);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Não foi possível abrir a pasta: " + e.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void enviarMensagem() {
        String texto = inputField.getText().trim();
        if (texto.isEmpty()) return;

        server.sendText(texto);
        addTextMessage("Você (PC)", texto, true);
        inputField.setText("");
    }

    // Adiciona cartão de mensagem no feed (Esquerda para o outro dispositivo, Direita em Azul para o PC)
    private void addTextMessage(String remetente, String texto, boolean isSent) {
        JPanel card = createBaseCard(remetente, isSent ? COLOR_ACCENT : COLOR_CARD);
        JLabel content = new JLabel("<html><body style='width: 320px; word-wrap: break-word;'>" + escapeHtml(texto) + "</body></html>");
        content.setForeground(COLOR_TEXT);
        content.setFont(new Font("SansSerif", Font.PLAIN, 13));
        card.add(content, BorderLayout.CENTER);

        // Se for um link HTTP, adiciona botão para abrir no navegador
        if (texto.startsWith("http://") || texto.startsWith("https://")) {
            JButton openLinkBtn = new JButton("🌐 Abrir no Navegador");
            openLinkBtn.setFocusPainted(false);
            openLinkBtn.addActionListener(e -> {
                try { Desktop.getDesktop().browse(new URI(texto)); } catch (Exception ignored) {}
            });
            JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 4));
            actionPanel.setOpaque(false);
            actionPanel.add(openLinkBtn);
            card.add(actionPanel, BorderLayout.SOUTH);
        }

        appendRowToHistory(card, isSent);
    }

    // Adiciona cartão de arquivo no feed com Miniatura Real (Thumbnail) se for imagem
    private void addFileCard(String remetente, File arquivo, boolean isSent) {
        JPanel card = createBaseCard(remetente, isSent ? COLOR_ACCENT : COLOR_CARD);
        card.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(isSent ? new Color(147, 197, 253) : COLOR_SUCCESS, 1, true),
                new EmptyBorder(8, 10, 8, 10)
        ));

        JPanel contentBox = new JPanel(new BorderLayout(6, 6));
        contentBox.setOpaque(false);

        // Se for imagem, carrega miniatura real
        if (isImageFile(arquivo.getName())) {
            try {
                BufferedImage bimg = ImageIO.read(arquivo);
                if (bimg != null) {
                    int maxW = 160, maxH = 120;
                    int origW = bimg.getWidth(), origH = bimg.getHeight();
                    int newW = maxW, newH = maxH;
                    if (origW > origH) {
                        newH = (origH * maxW) / origW;
                    } else {
                        newW = (origW * maxH) / origH;
                    }
                    Image thumb = bimg.getScaledInstance(Math.max(1, newW), Math.max(1, newH), Image.SCALE_SMOOTH);
                    JLabel thumbLabel = new JLabel(new ImageIcon(thumb));
                    thumbLabel.setBorder(new LineBorder(new Color(255, 255, 255, 40), 1, true));
                    contentBox.add(thumbLabel, BorderLayout.NORTH);
                }
            } catch (Exception ignored) {}
        }

        String sizeText = formatFileSize(arquivo.length());
        JLabel fileInfo = new JLabel("<html><b>📄 " + escapeHtml(arquivo.getName()) + "</b> (" + sizeText + ")</html>");
        fileInfo.setForeground(COLOR_TEXT);
        fileInfo.setFont(new Font("SansSerif", Font.PLAIN, 13));
        contentBox.add(fileInfo, BorderLayout.CENTER);
        card.add(contentBox, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        actions.setOpaque(false);

        JButton openBtn = new JButton("Abrir Arquivo");
        openBtn.setFocusPainted(false);
        openBtn.addActionListener(e -> {
            try {
                Desktop.getDesktop().open(arquivo);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Não foi possível abrir o arquivo: " + ex.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
            }
        });

        JButton folderBtn = new JButton("Ver na Pasta");
        folderBtn.setFocusPainted(false);
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
        JPanel card = createBaseCard(remetente, isSent ? COLOR_ACCENT : COLOR_CARD);
        String preview = clipboardText.length() > 120 ? clipboardText.substring(0, 120) + "..." : clipboardText;
        JLabel lbl = new JLabel("<html><b>📋 Área de Transferência:</b><br/>" + escapeHtml(preview) + "</html>");
        lbl.setForeground(COLOR_TEXT);
        card.add(lbl, BorderLayout.CENTER);

        JButton copyBtn = new JButton("Copiar novamente");
        copyBtn.setFocusPainted(false);
        copyBtn.addActionListener(e -> setSystemClipboard(clipboardText));

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 4));
        actions.setOpaque(false);
        actions.add(copyBtn);
        card.add(actions, BorderLayout.SOUTH);

        appendRowToHistory(card, isSent);
    }

    private void addCommandCard(String titulo, String detalhe) {
        JPanel card = createBaseCard(titulo, COLOR_CARD);
        JLabel lbl = new JLabel("<html><b>🎮 Ação:</b> " + escapeHtml(detalhe) + "</html>");
        lbl.setForeground(COLOR_TEXT);
        card.add(lbl, BorderLayout.CENTER);
        appendRowToHistory(card, false);
    }

    private void addSystemMessage(String msg, boolean isError) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.CENTER));
        p.setOpaque(false);
        JLabel l = new JLabel(msg);
        l.setForeground(isError ? new Color(239, 68, 68) : COLOR_MUTED);
        l.setFont(new Font("SansSerif", Font.ITALIC, 11));
        p.add(l);

        historyPanel.add(p);
        historyPanel.revalidate();
        historyPanel.repaint();
    }

    private JPanel createBaseCard(String remetente, Color bgColor) {
        JPanel card = new JPanel(new BorderLayout(4, 4));
        card.setBackground(bgColor);
        card.setBorder(new EmptyBorder(8, 12, 8, 12));

        String time = new SimpleDateFormat("HH:mm").format(new Date());
        JLabel header = new JLabel(remetente + " • " + time);
        header.setForeground(COLOR_MUTED);
        header.setFont(new Font("SansSerif", Font.BOLD, 11));
        card.add(header, BorderLayout.NORTH);
        return card;
    }

    private void appendRowToHistory(JPanel card, boolean isSent) {
        JPanel row = new JPanel(new FlowLayout(isSent ? FlowLayout.RIGHT : FlowLayout.LEFT, 4, 2));
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, card.getPreferredSize().height + 10));
        row.add(card);

        historyPanel.add(row);
        historyPanel.add(Box.createRigidArea(new Dimension(0, 4)));
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
