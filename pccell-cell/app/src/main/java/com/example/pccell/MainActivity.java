package com.example.pccell;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.animation.AnimatorSet;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.*;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.hardware.Camera;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.view.*;
import android.webkit.MimeTypeMap;
import android.widget.*;
import androidx.core.content.FileProvider;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

@SuppressWarnings("deprecation")
public class MainActivity extends Activity {

    private static final int PORT = 5050;
    private static final int PICK_FILE = 101;
    private static final int PERM_CAMERA = 102;
    private static final int PERM_AUDIO = 103;
    private static final int TAKE_PHOTO_FOR_CLIPBOARD = 104;

    // Elementos da interface
    private EditText ipEditText, messageEditText;
    private Button connectButton, discoverButton, connectDiscoveredButton;
    private Button attachButton, macroButton, powerButton, gamepadButton, photoToClipboardButton, screenshotButton;
    private Button touchpadButton, explorePcButton, micButton, webcamButton, clipboardButton, remoteButton, sendButton;
    private TextView statusTextView, progressTextView, welcomeHint, discoveredText;
    private LinearLayout discoveredBanner, chatMessagesContainer;
    private ScrollView chatScrollView;
    private ProgressBar transferProgressBar;

    private TcpClient client;
    private SharedPreferences prefs;
    private String discoveredIp = "";
    private Uri lastSavedUri = null;

    // Estado da Câmera / Webcam
    private Camera camera;
    private boolean isStreamingCamera = false;
    private long lastFrameTime = 0;
    // Camera facing (0=back, 1=front)
    private int currentCameraId = 0;

    // Estado do Microfone Sem Fio
    private AudioRecord audioRecord;
    private boolean isRecordingMic = false;
    private Thread micThread;

    // Estado do Explorador Remoto e Hardware
    private AlertDialog exploreDialog;
    private AlertDialog hardwareDialog;
    private TextView hardwareStatsText;

    // Foto para Clipboard
    private Uri tempPhotoUri;

    // Remote Desktop
    private Dialog remoteDesktopDialog;
    private ImageView remoteDesktopView;

    // Microfone Dialog refs
    private Dialog micDialog;
    private TextView micStatusText;
    private ProgressBar micLevelBar;
    private Button micToggleButton;
    private TextView micIcon;
    private AnimatorSet micPulseAnimator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("PCcellPrefs", MODE_PRIVATE);

        // Bind dos componentes
        ipEditText = findViewById(R.id.ipEditText);
        connectButton = findViewById(R.id.connectButton);
        discoverButton = findViewById(R.id.discoverButton);
        discoveredBanner = findViewById(R.id.discoveredBanner);
        discoveredText = findViewById(R.id.discoveredText);
        connectDiscoveredButton = findViewById(R.id.connectDiscoveredButton);

        statusTextView = findViewById(R.id.statusTextView);
        chatScrollView = findViewById(R.id.chatScrollView);
        chatMessagesContainer = findViewById(R.id.chatMessagesContainer);
        welcomeHint = findViewById(R.id.welcomeHint);

        progressTextView = findViewById(R.id.progressTextView);
        transferProgressBar = findViewById(R.id.transferProgressBar);

        attachButton = findViewById(R.id.attachButton);
        macroButton = findViewById(R.id.macroButton);
        powerButton = findViewById(R.id.powerButton);
        gamepadButton = findViewById(R.id.gamepadButton);
        photoToClipboardButton = findViewById(R.id.photoToClipboardButton);
        screenshotButton = findViewById(R.id.screenshotButton);
        touchpadButton = findViewById(R.id.touchpadButton);
        explorePcButton = findViewById(R.id.explorePcButton);
        micButton = findViewById(R.id.micButton);
        webcamButton = findViewById(R.id.webcamButton);
        clipboardButton = findViewById(R.id.clipboardButton);
        remoteButton = findViewById(R.id.remoteButton);
        messageEditText = findViewById(R.id.messageEditText);
        sendButton = findViewById(R.id.sendButton);

        ipEditText.setText(prefs.getString("last_ip", ""));

        initTcpClient();
        setupActions();
        handleIncomingShareIntent(getIntent());
    }

    private void initTcpClient() {
        client = new TcpClient(new TcpClient.ClientListener() {
            @Override
            public void onStatusChanged(String status, boolean connected) {
                runOnUiThread(() -> {
                    boolean connecting = status.startsWith("Conectando");
                    statusTextView.setText((connected ? "● " : "○ ") + status);
                    statusTextView.setTextColor(connected ? Color.parseColor("#10B981") : (connecting ? Color.parseColor("#F59E0B") : Color.parseColor("#94A3B8")));

                    connectButton.setText(connected ? "Desconectar" : (connecting ? "Cancelar" : "Conectar"));
                    ipEditText.setEnabled(!connected && !connecting);
                    discoverButton.setEnabled(!connected && !connecting);

                    messageEditText.setEnabled(connected);
                    sendButton.setEnabled(connected);
                    attachButton.setEnabled(connected);
                    macroButton.setEnabled(connected);
                    powerButton.setEnabled(connected);
                    gamepadButton.setEnabled(connected);
                    photoToClipboardButton.setEnabled(connected);
                    screenshotButton.setEnabled(connected);
                    touchpadButton.setEnabled(connected);
                    explorePcButton.setEnabled(connected);
                    micButton.setEnabled(connected);
                    webcamButton.setEnabled(connected);
                    clipboardButton.setEnabled(connected);
                    remoteButton.setEnabled(connected);
                });
            }

            @Override
            public void onTextReceived(String text) {
                runOnUiThread(() -> addTextMessage("PC", text, false));
            }

            @Override
            public void onClipboardReceived(String text) {
                runOnUiThread(() -> {
                    copyToClipboard(text);
                    addClipboardCard("PC", text);
                    vibrarFeedback(50);
                    Toast.makeText(MainActivity.this, "📋 Texto copiado para a área de transferência!", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onCommandReceived(String cmd, String param) {
                runOnUiThread(() -> {
                    if ("OPEN_URL".equals(cmd) && param != null && !param.isEmpty()) {
                        try {
                            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(param));
                            startActivity(browserIntent);
                        } catch (Exception ignored) {}
                    }
                });
            }

            @Override
            public void onDirDataReceived(String path, List<RemoteDirItem> items) {
                runOnUiThread(() -> exibirDialogExplorador(path, items));
            }

            @Override
            public void onHardwareStatsReceived(int cpu, int ram, long ramFreeMb, long ramTotalMb, int disk, long diskFreeGb, long diskTotalGb) {
                runOnUiThread(() -> {
                    if (hardwareStatsText != null) {
                        String txt = "⚡ CPU: " + cpu + "%\n" +
                                "🧠 Memória RAM: " + ram + "% (" + (ramTotalMb - ramFreeMb) + " MB / " + ramTotalMb + " MB)\n" +
                                "💾 Disco (C:): " + disk + "% (" + diskFreeGb + " GB livres de " + diskTotalGb + " GB)";
                        hardwareStatsText.setText(txt);
                    }
                });
            }

            @Override
            public void onScreenshotReceived(byte[] scBytes) {
                // Now opens the Remote Desktop dialog instead of a static screenshot
                runOnUiThread(() -> showRemoteDesktopDialog());
            }

            @Override
            public void onRemoteScreenFrameReceived(byte[] frameData) {
                Bitmap bitmap = BitmapFactory.decodeByteArray(frameData, 0, frameData.length);
                if (bitmap != null) {
                    runOnUiThread(() -> {
                        if (remoteDesktopView != null) {
                            remoteDesktopView.setImageBitmap(bitmap);
                        }
                    });
                }
            }

            @Override
            public OutputStream createFileOutputStream(String name) throws IOException {
                return createDownloadDestination(name);
            }

            @Override
            public void onFileReceived(String name, long size) {
                runOnUiThread(() -> {
                    vibrarFeedback(120);
                    addFileCard("PC", name, size, lastSavedUri, false);
                    Toast.makeText(MainActivity.this, "Arquivo salvo em Downloads: " + name, Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onFileSent(String name, long size) {
                runOnUiThread(() -> addFileCard("Você (Celular)", name, size, null, true));
            }

            @Override
            public void onProgress(String description, int percent) {
                runOnUiThread(() -> {
                    if (description == null || description.isEmpty() || percent >= 100) {
                        progressTextView.setVisibility(View.GONE);
                        transferProgressBar.setVisibility(View.GONE);
                    } else {
                        progressTextView.setVisibility(View.VISIBLE);
                        transferProgressBar.setVisibility(View.VISIBLE);
                        progressTextView.setText(description + " (" + percent + "%)");
                        transferProgressBar.setProgress(percent);
                    }
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, error, Toast.LENGTH_LONG).show());
            }
        });
    }

    private void setupActions() {
        connectButton.setOnClickListener(v -> {
            if (client.isConectado() || client.isConectando()) client.disconnect();
            else conectarAoIp(ipEditText.getText().toString().trim());
        });

        discoverButton.setOnClickListener(v -> iniciarDescobertaPc());

        connectDiscoveredButton.setOnClickListener(v -> {
            if (!discoveredIp.isEmpty()) {
                ipEditText.setText(discoveredIp);
                conectarAoIp(discoveredIp);
                discoveredBanner.setVisibility(View.GONE);
            }
        });

        sendButton.setOnClickListener(v -> enviarMensagemTexto());
        messageEditText.setOnEditorActionListener((v, a, e) -> {
            enviarMensagemTexto();
            return true;
        });

        attachButton.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            startActivityForResult(intent, PICK_FILE);
        });

        // 1. Macro Deck
        macroButton.setOnClickListener(v -> exibirMacroDeckDialog());

        // 2. Energia & Hardware
        powerButton.setOnClickListener(v -> exibirEnergiaHardwareDialog());

        // 3. Gamepad (new beautiful UI)
        gamepadButton.setOnClickListener(v -> showGamepadDialog());

        // 4. Foto no Ctrl+V
        photoToClipboardButton.setOnClickListener(v -> tirarFotoParaClipboard());

        // 5. Screenshot do PC -> now opens Remote Desktop
        screenshotButton.setOnClickListener(v -> showRemoteDesktopDialog());

        // 6. Touchpad
        touchpadButton.setOnClickListener(v -> exibirTouchpadDialog());

        // 7. Explorador Remoto
        explorePcButton.setOnClickListener(v -> {
            Toast.makeText(this, "Carregando pastas do PC...", Toast.LENGTH_SHORT).show();
            client.sendCommand("BROWSE_DIR", "ROOT");
        });

        // 8. Microfone Sem Fio (new beautiful UI)
        micButton.setOnClickListener(v -> showMicDialog());

        // 9. Câmera / Webcam (new beautiful UI)
        webcamButton.setOnClickListener(v -> showCameraStudioDialog());

        // 10. Clipboard & Mídia
        clipboardButton.setOnClickListener(v -> enviarClipboardLocal());
        remoteButton.setOnClickListener(v -> exibirDialogControleRemoto());
    }

    private void vibrarFeedback(long millis) {
        try {
            Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null && v.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= 26) {
                    v.vibrate(VibrationEffect.createOneShot(millis, VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    v.vibrate(millis);
                }
            }
        } catch (Exception ignored) {}
    }

    // ==========================================
    // 1. 🗂️ MACRO DECK / STREAM DECK
    // ==========================================
    private void exibirMacroDeckDialog() {
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#0F172A"));
        root.setPadding(20, 20, 20, 20);

        TextView t = new TextView(this);
        t.setText("🗂️ Macro Deck (Stream Deck)");
        t.setTextColor(Color.WHITE);
        t.setTextSize(17);
        t.setTypeface(null, Typeface.BOLD);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, 0, 0, 16);
        root.addView(t);

        // Botão Modo Pânico (Vermelho e Grande)
        Button btnPanic = new Button(this);
        btnPanic.setText("🚨 MODO PÂNICO (Minimizar & Mutar)");
        btnPanic.setBackgroundColor(Color.parseColor("#DC2626"));
        btnPanic.setTextColor(Color.WHITE);
        btnPanic.setTextSize(13);
        btnPanic.setTypeface(null, Typeface.BOLD);
        btnPanic.setOnClickListener(v -> {
            vibrarFeedback(80);
            client.sendCommand("MACRO", "PANIC");
            Toast.makeText(this, "Modo Pânico Ativado!", Toast.LENGTH_SHORT).show();
        });
        root.addView(btnPanic);

        // Grade de Atalhos
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(2);
        grid.setPadding(0, 12, 0, 0);

        addGridMacro(grid, "🖥️ Mostrar Desktop", "DESKTOP");
        addGridMacro(grid, "📋 Gerenciador Tarefas", "TASK_MANAGER");
        addGridMacro(grid, "❌ Fechar Janela (Alt+F4)", "ALT_F4");
        addGridMacro(grid, "🔄 Alternar (Alt+Tab)", "ALT_TAB");
        addGridMacro(grid, "📁 Explorador Windows", "EXPLORER");
        addGridMacro(grid, "🧮 Calculadora", "CALC");
        addGridMacro(grid, "📝 Bloco de Notas", "NOTEPAD");
        addGridMacro(grid, "🎵 Abrir Spotify", "SPOTIFY");
        addGridMacro(grid, "🌐 Abrir Navegador", "BROWSER");

        root.addView(grid);
        b.setView(root);
        b.setPositiveButton("Fechar", null);
        b.show();
    }

    private void addGridMacro(GridLayout grid, String title, String macroCode) {
        Button btn = createCustomButton(title);
        GridLayout.LayoutParams p = new GridLayout.LayoutParams();
        p.width = 0;
        p.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        p.setMargins(6, 6, 6, 6);
        btn.setLayoutParams(p);
        btn.setOnClickListener(v -> {
            vibrarFeedback(30);
            client.sendCommand("MACRO", macroCode);
        });
        grid.addView(btn);
    }

    // ==========================================
    // 2. ⚡ ENERGIA & STATUS DO HARDWARE DO PC
    // ==========================================
    private void exibirEnergiaHardwareDialog() {
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#0F172A"));
        root.setPadding(20, 20, 20, 20);

        TextView t = new TextView(this);
        t.setText("⚡ Energia & Monitor de Hardware");
        t.setTextColor(Color.WHITE);
        t.setTextSize(17);
        t.setTypeface(null, Typeface.BOLD);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, 0, 0, 12);
        root.addView(t);

        // Painel com CPU, RAM e Disco
        hardwareStatsText = new TextView(this);
        hardwareStatsText.setText("Lendo status do computador...");
        hardwareStatsText.setTextColor(Color.parseColor("#38BDF8"));
        hardwareStatsText.setTextSize(13);
        hardwareStatsText.setBackgroundResource(R.drawable.bg_card);
        hardwareStatsText.setPadding(16, 14, 16, 14);
        root.addView(hardwareStatsText);

        client.sendCommand("GET_HARDWARE_STATS", "");

        // Ações de Energia
        TextView lblPower = new TextView(this);
        lblPower.setText("\nAções de Energia:");
        lblPower.setTextColor(Color.parseColor("#94A3B8"));
        lblPower.setTextSize(12);
        root.addView(lblPower);

        Button btnLock = createCustomButton("🔒 Bloquear Tela do Windows");
        btnLock.setOnClickListener(v -> client.sendCommand("POWER", "LOCK"));
        root.addView(btnLock);

        Button btnSleep = createCustomButton("💤 Suspender Computador");
        btnSleep.setOnClickListener(v -> client.sendCommand("POWER", "SLEEP"));
        root.addView(btnSleep);

        LinearLayout timerRow = new LinearLayout(this);
        timerRow.setOrientation(LinearLayout.HORIZONTAL);
        timerRow.setPadding(0, 6, 0, 6);

        Button btnTimer30 = createCustomButton("⏱️ Desligar em 30m");
        btnTimer30.setOnClickListener(v -> {
            client.sendCommand("POWER", "SHUTDOWN_TIMER:1800");
            Toast.makeText(this, "PC desligará em 30 minutos!", Toast.LENGTH_SHORT).show();
        });

        Button btnCancelTimer = createCustomButton("🛑 Cancelar Timer");
        btnCancelTimer.setOnClickListener(v -> {
            client.sendCommand("POWER", "SHUTDOWN_CANCEL");
            Toast.makeText(this, "Desligamento cancelado!", Toast.LENGTH_SHORT).show();
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(0, 0, 6, 0);
        btnTimer30.setLayoutParams(lp);
        btnCancelTimer.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        timerRow.addView(btnTimer30);
        timerRow.addView(btnCancelTimer);
        root.addView(timerRow);

        b.setView(root);
        b.setPositiveButton("Fechar", null);
        hardwareDialog = b.show();
    }

    // ==========================================
    // ==========================================
    // 3. 🎮 GAMEPAD / CONTROLE VIRTUAL (MODO HORIZONTAL ERGONÔMICO)
    // ==========================================
    private void showGamepadDialog() {
        Dialog dialog = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        // Bloqueia em modo paisagem (horizontal) para jogabilidade ideal
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);

        // Container raiz ocupando tela cheia horizontal
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#090D16"));
        root.setPadding(dp(16), dp(8), dp(16), dp(8));
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // ---- TOP ROW: L2, L1, [SELECT / START / SAIR], R1, R2 ----
        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);
        topRow.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        Button btnL2 = makeShoulderBtn("L2");
        Button btnL1 = makeShoulderBtn("L1");
        setupGamepadTouch(btnL2, "L2");
        setupGamepadTouch(btnL1, "L1");
        topRow.addView(btnL2);
        topRow.addView(btnL1);

        View spacerL = new View(this);
        spacerL.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1f));
        topRow.addView(spacerL);

        // Botões centrais de sistema
        Button btnSelect = makeSystemBtn("SELECT");
        setupGamepadTouch(btnSelect, "SELECT");
        Button btnStart = makeSystemBtn("START");
        setupGamepadTouch(btnStart, "START");

        Button btnExit = new Button(this);
        btnExit.setText("✕ SAIR");
        btnExit.setTextColor(Color.WHITE);
        btnExit.setTextSize(11);
        btnExit.setTypeface(null, Typeface.BOLD);
        GradientDrawable exitBg = new GradientDrawable();
        exitBg.setColor(Color.parseColor("#EF4444"));
        exitBg.setCornerRadius(dp(10));
        btnExit.setBackground(exitBg);
        LinearLayout.LayoutParams exitLp = new LinearLayout.LayoutParams(dp(62), dp(36));
        exitLp.setMargins(dp(6), 0, dp(6), 0);
        btnExit.setLayoutParams(exitLp);
        btnExit.setOnClickListener(v -> dialog.dismiss());

        topRow.addView(btnSelect);
        topRow.addView(btnStart);
        topRow.addView(btnExit);

        View spacerR = new View(this);
        spacerR.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1f));
        topRow.addView(spacerR);

        Button btnR1 = makeShoulderBtn("R1");
        Button btnR2 = makeShoulderBtn("R2");
        setupGamepadTouch(btnR1, "R1");
        setupGamepadTouch(btnR2, "R2");
        topRow.addView(btnR1);
        topRow.addView(btnR2);
        root.addView(topRow);

        // ---- ÁREA DE JOGO PRINCIPAL (D-PAD na esquerda, ABXY na direita) ----
        LinearLayout mainPlayArea = new LinearLayout(this);
        mainPlayArea.setOrientation(LinearLayout.HORIZONTAL);
        mainPlayArea.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams mainParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        mainParams.setMargins(0, dp(6), 0, dp(6));
        mainPlayArea.setLayoutParams(mainParams);

        // ================= D-PAD (ESQUERDA) =================
        LinearLayout dpadWing = new LinearLayout(this);
        dpadWing.setOrientation(LinearLayout.VERTICAL);
        dpadWing.setGravity(Gravity.CENTER);
        dpadWing.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        Button dpadUp = makeDpadBtn("▲", dp(76), dp(62));
        setupGamepadTouch(dpadUp, "DPAD_UP");

        LinearLayout dpadMidRow = new LinearLayout(this);
        dpadMidRow.setOrientation(LinearLayout.HORIZONTAL);
        dpadMidRow.setGravity(Gravity.CENTER);

        Button dpadLeft = makeDpadBtn("◀", dp(62), dp(76));
        setupGamepadTouch(dpadLeft, "DPAD_LEFT");

        View dpadCenter = new View(this);
        dpadCenter.setLayoutParams(new LinearLayout.LayoutParams(dp(48), dp(48)));
        GradientDrawable dpadCenterBg = new GradientDrawable();
        dpadCenterBg.setColor(Color.parseColor("#1E2536"));
        dpadCenterBg.setCornerRadius(dp(8));
        dpadCenter.setBackground(dpadCenterBg);

        Button dpadRight = makeDpadBtn("▶", dp(62), dp(76));
        setupGamepadTouch(dpadRight, "DPAD_RIGHT");

        dpadMidRow.addView(dpadLeft);
        dpadMidRow.addView(dpadCenter);
        dpadMidRow.addView(dpadRight);

        Button dpadDown = makeDpadBtn("▼", dp(76), dp(62));
        setupGamepadTouch(dpadDown, "DPAD_DOWN");

        dpadWing.addView(dpadUp);
        dpadWing.addView(dpadMidRow);
        dpadWing.addView(dpadDown);

        // Botão L3 (Thumbstick analógico esquerdo)
        Button btnL3 = makeThumbstickBtn("L3");
        setupGamepadTouch(btnL3, "LS");
        dpadWing.addView(btnL3);

        mainPlayArea.addView(dpadWing);

        // ================= CENTRO (STATUS) =================
        LinearLayout centerInfo = new LinearLayout(this);
        centerInfo.setOrientation(LinearLayout.VERTICAL);
        centerInfo.setGravity(Gravity.CENTER);
        centerInfo.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.4f));

        TextView tvGame = new TextView(this);
        tvGame.setText("🎮 PCcell\nGAMEPAD");
        tvGame.setTextColor(Color.parseColor("#475569"));
        tvGame.setTextSize(12);
        tvGame.setTypeface(null, Typeface.BOLD);
        tvGame.setGravity(Gravity.CENTER);
        centerInfo.addView(tvGame);

        mainPlayArea.addView(centerInfo);

        // ================= BOTÕES DE AÇÃO ABXY (DIREITA) =================
        LinearLayout abxyWing = new LinearLayout(this);
        abxyWing.setOrientation(LinearLayout.VERTICAL);
        abxyWing.setGravity(Gravity.CENTER);
        abxyWing.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        // Botão Y (Amarelo no topo)
        Button btnY = makeActionBtn("Y", "#F59E0B", Color.BLACK);
        setupGamepadTouch(btnY, "Y");

        LinearLayout abxyMidRow = new LinearLayout(this);
        abxyMidRow.setOrientation(LinearLayout.HORIZONTAL);
        abxyMidRow.setGravity(Gravity.CENTER);

        // Botão X (Azul na esquerda)
        Button btnX = makeActionBtn("X", "#0284C7", Color.WHITE);
        setupGamepadTouch(btnX, "X");

        View abxySpacer = new View(this);
        abxySpacer.setLayoutParams(new LinearLayout.LayoutParams(dp(28), 1));

        // Botão B (Vermelho na direita)
        Button btnB = makeActionBtn("B", "#DC2626", Color.WHITE);
        setupGamepadTouch(btnB, "B");

        abxyMidRow.addView(btnX);
        abxyMidRow.addView(abxySpacer);
        abxyMidRow.addView(btnB);

        // Botão A (Verde embaixo)
        Button btnA = makeActionBtn("A", "#16A34A", Color.WHITE);
        setupGamepadTouch(btnA, "A");

        abxyWing.addView(btnY);
        abxyWing.addView(abxyMidRow);
        abxyWing.addView(btnA);

        // Botão R3 (Thumbstick analógico direito)
        Button btnR3 = makeThumbstickBtn("R3");
        setupGamepadTouch(btnR3, "RS");
        abxyWing.addView(btnR3);

        mainPlayArea.addView(abxyWing);
        root.addView(mainPlayArea);

        dialog.setContentView(root);
        dialog.setOnDismissListener(d -> {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        });
        dialog.show();
    }

    private int dp(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    private Button makeShoulderBtn(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(Color.WHITE);
        b.setTypeface(null, Typeface.BOLD);
        b.setTextSize(14);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#1E293B"));
        bg.setCornerRadius(dp(12));
        bg.setStroke(dp(1), Color.parseColor("#475569"));
        b.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(78), dp(44));
        lp.setMargins(dp(4), 0, dp(4), 0);
        b.setLayoutParams(lp);
        return b;
    }

    private Button makeSystemBtn(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(Color.parseColor("#CBD5E1"));
        b.setTextSize(10);
        b.setTypeface(null, Typeface.BOLD);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#1E293B"));
        bg.setCornerRadius(dp(10));
        bg.setStroke(dp(1), Color.parseColor("#334155"));
        b.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(68), dp(36));
        lp.setMargins(dp(4), 0, dp(4), 0);
        b.setLayoutParams(lp);
        return b;
    }

    private Button makeDpadBtn(String text, int widthPx, int heightPx) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(Color.parseColor("#E2E8F0"));
        b.setTextSize(20);
        b.setPadding(0, 0, 0, 0);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#1E293B"));
        bg.setCornerRadius(dp(10));
        bg.setStroke(dp(1), Color.parseColor("#3B82F6"));
        b.setBackground(bg);
        b.setLayoutParams(new LinearLayout.LayoutParams(widthPx, heightPx));
        return b;
    }

    private Button makeActionBtn(String text, String hexColor, int textColor) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(textColor);
        b.setTypeface(null, Typeface.BOLD);
        b.setTextSize(22);
        b.setPadding(0, 0, 0, 0);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(Color.parseColor(hexColor));
        bg.setStroke(dp(2), Color.parseColor("#FFFFFF"));
        b.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(68), dp(68));
        lp.setMargins(dp(4), dp(4), dp(4), dp(4));
        b.setLayoutParams(lp);
        return b;
    }

    private Button makeThumbstickBtn(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(Color.parseColor("#94A3B8"));
        b.setTextSize(12);
        b.setTypeface(null, Typeface.BOLD);
        b.setPadding(0, 0, 0, 0);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#1E2536"));
        bg.setCornerRadius(dp(14));
        bg.setStroke(dp(1), Color.parseColor("#06B6D4"));
        b.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(54), dp(38));
        lp.setMargins(0, dp(8), 0, 0);
        b.setLayoutParams(lp);
        return b;
    }

    private void setupGamepadTouch(Button btn, String key) {
        btn.setOnTouchListener((v, e) -> {
            int action = e.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
                vibrarFeedback(30);
                btn.setScaleX(0.92f);
                btn.setScaleY(0.92f);
                btn.setAlpha(0.85f);
                client.sendCommand("GAMEPAD_DOWN", key);
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP || action == MotionEvent.ACTION_CANCEL) {
                btn.setScaleX(1.0f);
                btn.setScaleY(1.0f);
                btn.setAlpha(1.0f);
                client.sendCommand("GAMEPAD_UP", key);
            }
            return true;
        });
    }

    // ==========================================
    // 4. 📸 FOTO DIRETO NO CTRL+V DO PC
    // ==========================================
    private void tirarFotoParaClipboard() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        File photoFile = new File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "clipboard_temp.jpg");
        tempPhotoUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", photoFile);
        takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, tempPhotoUri);
        takePictureIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivityForResult(takePictureIntent, TAKE_PHOTO_FOR_CLIPBOARD);
        } catch (Exception e) {
            Toast.makeText(this, "Não foi possível abrir a câmera: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // ==========================================
    // 5. 🖥️ REMOTE DESKTOP (60Hz + TOUCHPAD + BOTÕES ESQUERDO/DIREITO)
    // ==========================================
    private void showRemoteDesktopDialog() {
        remoteDesktopDialog = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        remoteDesktopDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(Color.BLACK);
        frame.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Tela do PC
        remoteDesktopView = new ImageView(this);
        remoteDesktopView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        remoteDesktopView.setBackgroundColor(Color.BLACK);
        remoteDesktopView.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Variáveis de toque
        final float[] lastTouchX = {0};
        final float[] lastTouchY = {0};
        final long[] downTime = {0};
        final boolean[] hasMoved = {false};

        // Touchpad na tela do celular
        remoteDesktopView.setOnTouchListener((v, event) -> {
            int viewW = v.getWidth();
            int viewH = v.getHeight();
            float x = event.getX();
            float y = event.getY();

            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    lastTouchX[0] = x;
                    lastTouchY[0] = y;
                    downTime[0] = System.currentTimeMillis();
                    hasMoved[0] = false;
                    client.sendRemoteMouseMove(x, y, viewW, viewH);
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (Math.hypot(x - lastTouchX[0], y - lastTouchY[0]) > dp(6)) {
                        hasMoved[0] = true;
                    }
                    lastTouchX[0] = x;
                    lastTouchY[0] = y;
                    client.sendRemoteMouseMove(x, y, viewW, viewH);
                    break;
                case MotionEvent.ACTION_UP:
                    if (!hasMoved[0] && (System.currentTimeMillis() - downTime[0] < 220)) {
                        vibrarFeedback(25);
                        client.sendCommand("MOUSE_CLICK", "LEFT");
                    }
                    break;
            }
            return true;
        });

        // Top Bar com status 60Hz e botão Fechar
        LinearLayout topBarRD = new LinearLayout(this);
        topBarRD.setOrientation(LinearLayout.HORIZONTAL);
        topBarRD.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout.LayoutParams topBarLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        topBarLp.gravity = Gravity.TOP;
        topBarLp.setMargins(dp(16), dp(16), dp(16), 0);
        topBarRD.setLayoutParams(topBarLp);

        // Badge 60Hz
        TextView badge60Hz = new TextView(this);
        badge60Hz.setText("● Ao Vivo • 60Hz");
        badge60Hz.setTextColor(Color.parseColor("#10B981"));
        badge60Hz.setTextSize(12);
        badge60Hz.setTypeface(null, Typeface.BOLD);
        badge60Hz.setPadding(dp(12), dp(6), dp(12), dp(6));
        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setColor(Color.parseColor("#D90F172A"));
        badgeBg.setCornerRadius(dp(16));
        badgeBg.setStroke(dp(1), Color.parseColor("#1E293B"));
        badge60Hz.setBackground(badgeBg);
        topBarRD.addView(badge60Hz);

        View topSpacer = new View(this);
        topSpacer.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1f));
        topBarRD.addView(topSpacer);

        Button closeBtnRD = new Button(this);
        closeBtnRD.setText("✕ Fechar");
        closeBtnRD.setTextColor(Color.WHITE);
        closeBtnRD.setTextSize(12);
        closeBtnRD.setTypeface(null, Typeface.BOLD);
        GradientDrawable closeBg = new GradientDrawable();
        closeBg.setColor(Color.parseColor("#D90F172A"));
        closeBg.setCornerRadius(dp(16));
        closeBg.setStroke(dp(1), Color.parseColor("#EF4444"));
        closeBtnRD.setBackground(closeBg);
        closeBtnRD.setPadding(dp(14), dp(6), dp(14), dp(6));
        closeBtnRD.setOnClickListener(v -> remoteDesktopDialog.dismiss());
        topBarRD.addView(closeBtnRD);

        // DOCK INFERIOR COM OS BOTÕES DE CLIQUE DO MOUSE
        LinearLayout dockClicks = new LinearLayout(this);
        dockClicks.setOrientation(LinearLayout.HORIZONTAL);
        dockClicks.setGravity(Gravity.CENTER);
        GradientDrawable dockBg = new GradientDrawable();
        dockBg.setColor(Color.parseColor("#E60F172A"));
        dockBg.setCornerRadius(dp(26));
        dockBg.setStroke(dp(1), Color.parseColor("#334155"));
        dockClicks.setBackground(dockBg);
        dockClicks.setPadding(dp(16), dp(8), dp(16), dp(8));
        FrameLayout.LayoutParams dockLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dockLp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        dockLp.setMargins(0, 0, 0, dp(18));
        dockClicks.setLayoutParams(dockLp);

        // Botão CLIQUE ESQUERDO
        Button btnClickLeft = new Button(this);
        btnClickLeft.setText("🖱️ ESQUERDO");
        btnClickLeft.setTextColor(Color.WHITE);
        btnClickLeft.setTextSize(13);
        btnClickLeft.setTypeface(null, Typeface.BOLD);
        GradientDrawable leftBg = new GradientDrawable();
        leftBg.setColor(Color.parseColor("#1E293B"));
        leftBg.setCornerRadius(dp(18));
        leftBg.setStroke(dp(2), Color.parseColor("#10B981"));
        btnClickLeft.setBackground(leftBg);
        LinearLayout.LayoutParams btnLeftLp = new LinearLayout.LayoutParams(dp(140), dp(46));
        btnClickLeft.setLayoutParams(btnLeftLp);
        btnClickLeft.setOnTouchListener((v, e) -> {
            int action = e.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                vibrarFeedback(35);
                btnClickLeft.setAlpha(0.75f);
                int w = remoteDesktopView != null ? remoteDesktopView.getWidth() : 1;
                int h = remoteDesktopView != null ? remoteDesktopView.getHeight() : 1;
                client.sendRemoteMouseDown(lastTouchX[0], lastTouchY[0], w, h, "LEFT");
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                btnClickLeft.setAlpha(1.0f);
                int w = remoteDesktopView != null ? remoteDesktopView.getWidth() : 1;
                int h = remoteDesktopView != null ? remoteDesktopView.getHeight() : 1;
                client.sendRemoteMouseUp(lastTouchX[0], lastTouchY[0], w, h, "LEFT");
            }
            return true;
        });

        View dockDivider = new View(this);
        dockDivider.setLayoutParams(new LinearLayout.LayoutParams(dp(16), 1));

        // Botão CLIQUE DIREITO
        Button btnClickRight = new Button(this);
        btnClickRight.setText("🖱️ DIREITO");
        btnClickRight.setTextColor(Color.WHITE);
        btnClickRight.setTextSize(13);
        btnClickRight.setTypeface(null, Typeface.BOLD);
        GradientDrawable rightBg = new GradientDrawable();
        rightBg.setColor(Color.parseColor("#1E293B"));
        rightBg.setCornerRadius(dp(18));
        rightBg.setStroke(dp(2), Color.parseColor("#F59E0B"));
        btnClickRight.setBackground(rightBg);
        LinearLayout.LayoutParams btnRightLp = new LinearLayout.LayoutParams(dp(140), dp(46));
        btnClickRight.setLayoutParams(btnRightLp);
        btnClickRight.setOnTouchListener((v, e) -> {
            int action = e.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                vibrarFeedback(35);
                btnClickRight.setAlpha(0.75f);
                client.sendCommand("MOUSE_CLICK", "RIGHT");
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                btnClickRight.setAlpha(1.0f);
            }
            return true;
        });

        dockClicks.addView(btnClickLeft);
        dockClicks.addView(dockDivider);
        dockClicks.addView(btnClickRight);

        frame.addView(remoteDesktopView);
        frame.addView(topBarRD);
        frame.addView(dockClicks);

        remoteDesktopDialog.setContentView(frame);
        remoteDesktopDialog.setOnDismissListener(d -> {
            client.sendCommand("STREAM_SCREEN_STOP", "");
            remoteDesktopView = null;
        });

        remoteDesktopDialog.show();
        client.sendCommand("STREAM_SCREEN_START", "");
    }

    // 6. 📷 CÂMERA / WEBCAM - Tela Dedicada
    // ==========================================
    private void showCameraStudioDialog() {
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, PERM_CAMERA);
            return;
        }

        Dialog cameraDialog = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        cameraDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(Color.BLACK);

        // SurfaceView for camera preview
        SurfaceView surfaceView = new SurfaceView(this);
        surfaceView.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        frame.addView(surfaceView);

        // Top overlay bar
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setBackgroundColor(Color.parseColor("#AA000000"));
        topBar.setPadding(dp(8), dp(8), dp(8), dp(8));
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout.LayoutParams topBarLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        topBar.setLayoutParams(topBarLp);

        // Close button (top-left)
        Button btnClose = makeCameraOverlayBtn("✕ Fechar");
        btnClose.setBackgroundColor(Color.parseColor("#DC2626"));
        btnClose.setOnClickListener(v -> cameraDialog.dismiss());
        topBar.addView(btnClose);

        // Spacer
        View topSpacer = new View(this);
        topSpacer.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1f));
        topBar.addView(topSpacer);

        // Switch camera button (top-right)
        Button btnSwitch = makeCameraOverlayBtn("🔄 Trocar Câmera");
        topBar.addView(btnSwitch);

        frame.addView(topBar);

        // Bottom overlay bar
        LinearLayout bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setBackgroundColor(Color.parseColor("#AA000000"));
        bottomBar.setPadding(dp(8), dp(8), dp(8), dp(8));
        bottomBar.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams bottomBarLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bottomBarLp.gravity = Gravity.BOTTOM;
        bottomBar.setLayoutParams(bottomBarLp);

        // Stream toggle button
        Button[] btnStreamRef = new Button[1];
        Button btnStream = makeCameraOverlayBtn("📡 Transmitir Webcam");
        btnStream.setBackgroundColor(Color.parseColor("#546E7A"));
        btnStreamRef[0] = btnStream;
        boolean[] streamingRef = {false};

        btnStream.setOnClickListener(v -> {
            streamingRef[0] = !streamingRef[0];
            isStreamingCamera = streamingRef[0];
            if (streamingRef[0]) {
                btnStreamRef[0].setBackgroundColor(Color.parseColor("#2E7D32"));
                btnStreamRef[0].setText("📡 Parar Transmissão");
            } else {
                btnStreamRef[0].setBackgroundColor(Color.parseColor("#546E7A"));
                btnStreamRef[0].setText("📡 Transmitir Webcam");
            }
        });

        // Photo to clipboard button
        Button btnPhoto = makeCameraOverlayBtn("📸 Foto → Ctrl+V");
        btnPhoto.setOnClickListener(v -> captureAndSendCameraFrame());

        bottomBar.addView(btnStream);
        View bottomSpacer = new View(this);
        bottomSpacer.setLayoutParams(new LinearLayout.LayoutParams(dp(12), 1));
        bottomBar.addView(bottomSpacer);
        bottomBar.addView(btnPhoto);

        frame.addView(bottomBar);

        // Camera setup via SurfaceHolder
        SurfaceHolder holder = surfaceView.getHolder();
        holder.addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder h) {
                openCamera(currentCameraId, h);
            }
            @Override public void surfaceChanged(SurfaceHolder h, int f, int w, int ht) {}
            @Override public void surfaceDestroyed(SurfaceHolder h) {
                pararCamera();
            }
        });

        btnSwitch.setOnClickListener(v -> {
            pararCamera();
            currentCameraId = (currentCameraId == 0) ? 1 : 0;
            openCamera(currentCameraId, holder);
        });

        cameraDialog.setOnDismissListener(d -> {
            pararCamera();
            isStreamingCamera = false;
            client.sendCommand("CAM_STOP", "");
        });

        cameraDialog.setContentView(frame);
        cameraDialog.show();
    }

    private void openCamera(int cameraId, SurfaceHolder holder) {
        try {
            int numCameras = Camera.getNumberOfCameras();
            if (cameraId >= numCameras) cameraId = 0;
            camera = Camera.open(cameraId);
            camera.setDisplayOrientation(90);
            camera.setPreviewDisplay(holder);
            camera.setPreviewCallback((data, cam) -> {
                if (!isStreamingCamera) return;
                long now = System.currentTimeMillis();
                if (now - lastFrameTime > 65) {
                    lastFrameTime = now;
                    Camera.Size s = cam.getParameters().getPreviewSize();
                    try {
                        YuvImage yuv = new YuvImage(data, ImageFormat.NV21, s.width, s.height, null);
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        yuv.compressToJpeg(new Rect(0, 0, s.width, s.height), 50, baos);
                        client.sendCameraFrame(baos.toByteArray());
                    } catch (Exception ignored) {}
                }
            });
            camera.startPreview();
        } catch (Exception e) {
            Toast.makeText(MainActivity.this, "Erro ao iniciar câmera: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void captureAndSendCameraFrame() {
        if (camera == null) return;
        camera.takePicture(null, null, (data, cam) -> {
            if (data != null) {
                client.sendClipboardImage(data);
                runOnUiThread(() -> Toast.makeText(this, "📸 Foto enviada para o Ctrl+V do PC!", Toast.LENGTH_SHORT).show());
            }
            cam.startPreview();
        });
    }

    private Button makeCameraOverlayBtn(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(Color.WHITE);
        b.setTextSize(12);
        b.setPadding(dp(10), dp(6), dp(10), dp(6));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#AA37474F"));
        bg.setCornerRadius(dp(8));
        b.setBackground(bg);
        return b;
    }

    private void pararCamera() {
        isStreamingCamera = false;
        if (camera != null) {
            try {
                camera.setPreviewCallback(null);
                camera.stopPreview();
                camera.release();
            } catch (Exception ignored) {}
            camera = null;
        }
    }

    // ==========================================
    // 7. 🎙️ MICROFONE SEM FIO - Interface Moderna
    // ==========================================
    private void showMicDialog() {
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, PERM_AUDIO);
            return;
        }

        micDialog = new Dialog(this);
        micDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(24), dp(32), dp(24), dp(32));

        GradientDrawable rootBg = new GradientDrawable();
        rootBg.setColor(Color.parseColor("#1A1A2E"));
        rootBg.setCornerRadius(dp(24));
        root.setBackground(rootBg);

        // Title
        TextView titleTv = new TextView(this);
        titleTv.setText("🎙️ Microfone Sem Fio");
        titleTv.setTextColor(Color.WHITE);
        titleTv.setTextSize(18);
        titleTv.setTypeface(null, Typeface.BOLD);
        titleTv.setGravity(Gravity.CENTER);
        root.addView(titleTv);

        // Microphone icon (pulsing when recording)
        micIcon = new TextView(this);
        micIcon.setText("🎤");
        micIcon.setTextSize(60);
        micIcon.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(100));
        iconParams.setMargins(0, dp(16), 0, dp(8));
        micIcon.setLayoutParams(iconParams);
        root.addView(micIcon);

        // Audio level progress bar
        micLevelBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        micLevelBar.setMax(100);
        micLevelBar.setProgress(0);
        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(16));
        barParams.setMargins(0, dp(8), 0, dp(16));
        micLevelBar.setLayoutParams(barParams);
        root.addView(micLevelBar);

        // Status text
        micStatusText = new TextView(this);
        micStatusText.setText("Aguardando...");
        micStatusText.setTextColor(Color.parseColor("#94A3B8"));
        micStatusText.setTextSize(14);
        micStatusText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        statusParams.setMargins(0, 0, 0, dp(20));
        micStatusText.setLayoutParams(statusParams);
        root.addView(micStatusText);

        // Toggle button
        micToggleButton = new Button(this);
        micToggleButton.setText("⏺ INICIAR GRAVAÇÃO");
        micToggleButton.setTextColor(Color.WHITE);
        micToggleButton.setTypeface(null, Typeface.BOLD);
        micToggleButton.setTextSize(15);
        GradientDrawable micBtnBg = new GradientDrawable();
        micBtnBg.setColor(Color.parseColor("#2E7D32"));
        micBtnBg.setCornerRadius(dp(12));
        micToggleButton.setBackground(micBtnBg);
        LinearLayout.LayoutParams micBtnParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(56));
        micBtnParams.setMargins(0, 0, 0, dp(12));
        micToggleButton.setLayoutParams(micBtnParams);

        micToggleButton.setOnClickListener(v -> {
            if (isRecordingMic) {
                stopMicRecording();
            } else {
                startMicRecording();
            }
        });
        root.addView(micToggleButton);

        // Close button
        Button closeBtn = new Button(this);
        closeBtn.setText("Fechar");
        closeBtn.setTextColor(Color.parseColor("#94A3B8"));
        closeBtn.setTextSize(13);
        GradientDrawable closeBg = new GradientDrawable();
        closeBg.setColor(Color.parseColor("#263238"));
        closeBg.setCornerRadius(dp(8));
        closeBtn.setBackground(closeBg);
        closeBtn.setOnClickListener(v -> micDialog.dismiss());
        root.addView(closeBtn);

        micDialog.setOnDismissListener(d -> {
            if (isRecordingMic) stopMicRecording();
            stopMicPulse();
        });

        micDialog.setContentView(root);

        // Update button state if already recording
        if (isRecordingMic) updateMicDialogRecordingState(true);

        micDialog.show();
    }

    private void startMicRecording() {
        int sampleRate = 16000;
        int channelConfig = AudioFormat.CHANNEL_IN_MONO;
        int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
        int bufferSize = Math.max(AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat), 2048);

        try {
            audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, channelConfig, audioFormat, bufferSize);
            audioRecord.startRecording();
            isRecordingMic = true;
            updateMicDialogRecordingState(true);

            micThread = new Thread(() -> {
                byte[] buffer = new byte[1024];
                while (isRecordingMic && audioRecord != null) {
                    int read = audioRecord.read(buffer, 0, buffer.length);
                    if (read > 0) {
                        byte[] chunk = new byte[read];
                        System.arraycopy(buffer, 0, chunk, 0, read);
                        client.sendMicAudio(chunk);

                        // Calculate audio level
                        int max = 0;
                        for (int i = 0; i < read - 1; i += 2) {
                            int sample = Math.abs((buffer[i + 1] << 8) | (buffer[i] & 0xFF));
                            if (sample > max) max = sample;
                        }
                        final int level = Math.min(100, (int)(max / 327.0));
                        runOnUiThread(() -> { if (micLevelBar != null) micLevelBar.setProgress(level); });
                    }
                }
            }, "Wireless-Mic-Thread");
            micThread.start();
        } catch (Exception e) {
            Toast.makeText(this, "Falha ao iniciar microfone: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void stopMicRecording() {
        isRecordingMic = false;
        pararMicrofone();
        updateMicDialogRecordingState(false);
        micButton.setText("🎙️ Mic Sem Fio");
        micButton.setTextColor(Color.parseColor("#E2E8F0"));
    }

    private void updateMicDialogRecordingState(boolean recording) {
        if (recording) {
            micButton.setText("🔴 Gravando Mic...");
            micButton.setTextColor(Color.parseColor("#EF4444"));

            if (micToggleButton != null) {
                micToggleButton.setText("⏹ PARAR");
                GradientDrawable bg = new GradientDrawable();
                bg.setColor(Color.parseColor("#C62828"));
                bg.setCornerRadius(dp(12));
                micToggleButton.setBackground(bg);
            }
            if (micStatusText != null) micStatusText.setText("Gravando e transmitindo...");
            startMicPulse();
        } else {
            if (micToggleButton != null) {
                micToggleButton.setText("⏺ INICIAR GRAVAÇÃO");
                GradientDrawable bg = new GradientDrawable();
                bg.setColor(Color.parseColor("#2E7D32"));
                bg.setCornerRadius(dp(12));
                micToggleButton.setBackground(bg);
            }
            if (micStatusText != null) micStatusText.setText("Parado");
            if (micLevelBar != null) micLevelBar.setProgress(0);
            stopMicPulse();
        }
    }

    private void startMicPulse() {
        if (micIcon == null) return;
        stopMicPulse();
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(micIcon, "scaleX", 1f, 1.2f, 1f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(micIcon, "scaleY", 1f, 1.2f, 1f);
        micPulseAnimator = new AnimatorSet();
        micPulseAnimator.playTogether(scaleX, scaleY);
        micPulseAnimator.setDuration(600);
        // Use simple handler loop to repeat pulse
        Handler pulseHandler = new Handler(Looper.getMainLooper());
        Runnable pulseRunnable = new Runnable() {
            @Override
            public void run() {
                if (isRecordingMic && micIcon != null) {
                    micIcon.animate().scaleX(1.25f).scaleY(1.25f).setDuration(300)
                            .withEndAction(() -> micIcon.animate().scaleX(1f).scaleY(1f).setDuration(300).start())
                            .start();
                    pulseHandler.postDelayed(this, 700);
                }
            }
        };
        pulseHandler.post(pulseRunnable);
    }

    private void stopMicPulse() {
        if (micPulseAnimator != null) {
            micPulseAnimator.cancel();
            micPulseAnimator = null;
        }
        if (micIcon != null) {
            micIcon.setScaleX(1f);
            micIcon.setScaleY(1f);
        }
    }

    private void iniciarMicrofone() {
        startMicRecording();
    }

    private void pararMicrofone() {
        isRecordingMic = false;
        if (audioRecord != null) {
            try {
                audioRecord.stop();
                audioRecord.release();
            } catch (Exception ignored) {}
            audioRecord = null;
        }
    }

    private void alternarMicrofoneSemFio() {
        // kept for backward compat; now delegates to showMicDialog
        showMicDialog();
    }

    private void abrirWebcamStream() {
        // kept for backward compat; delegates to new method
        showCameraStudioDialog();
    }

    // ==========================================
    // RECURSOS EXISTENTES: TOUCHPAD, EXPLORADOR
    // ==========================================
    private void exibirTouchpadDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#0F172A"));
        root.setPadding(20, 20, 20, 20);

        TextView title = new TextView(this);
        title.setText("🖱️ Touchpad / Mouse Sem Fio");
        title.setTextColor(Color.WHITE);
        title.setTextSize(16);
        title.setTypeface(null, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        TextView hint = new TextView(this);
        hint.setText("Arraste para mover • Toque = Clique • 2 dedos = Scroll");
        hint.setTextColor(Color.parseColor("#94A3B8"));
        hint.setTextSize(12);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, 4, 0, 12);
        root.addView(hint);

        View touchpadArea = new View(this);
        touchpadArea.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 550));
        touchpadArea.setBackgroundResource(R.drawable.bg_card);

        final float[] lastPos = new float[2];
        final long[] downTime = new long[1];
        final boolean[] moved = new boolean[1];

        touchpadArea.setOnTouchListener((v, event) -> {
            int pointerCount = event.getPointerCount();
            int action = event.getActionMasked();

            if (pointerCount == 1) {
                float x = event.getX();
                float y = event.getY();

                switch (action) {
                    case MotionEvent.ACTION_DOWN:
                        lastPos[0] = x;
                        lastPos[1] = y;
                        downTime[0] = System.currentTimeMillis();
                        moved[0] = false;
                        break;
                    case MotionEvent.ACTION_MOVE:
                        float dx = x - lastPos[0];
                        float dy = y - lastPos[1];
                        if (Math.abs(dx) > 1 || Math.abs(dy) > 1) {
                            moved[0] = true;
                            client.sendCommand("MOUSE_MOVE", ((int)(dx * 1.6f)) + ":" + ((int)(dy * 1.6f)));
                        }
                        lastPos[0] = x;
                        lastPos[1] = y;
                        break;
                    case MotionEvent.ACTION_UP:
                        if (!moved[0] && (System.currentTimeMillis() - downTime[0] < 220)) {
                            vibrarFeedback(30);
                            client.sendCommand("MOUSE_CLICK", "LEFT");
                        }
                        break;
                }
            } else if (pointerCount == 2 && action == MotionEvent.ACTION_MOVE) {
                float y = event.getY(0);
                float dy = y - lastPos[1];
                if (Math.abs(dy) > 5) {
                    int amount = dy > 0 ? -1 : 1;
                    client.sendCommand("MOUSE_SCROLL", String.valueOf(amount));
                }
                lastPos[1] = y;
            }
            return true;
        });
        root.addView(touchpadArea);

        LinearLayout clickRow = new LinearLayout(this);
        clickRow.setOrientation(LinearLayout.HORIZONTAL);
        clickRow.setPadding(0, 12, 0, 8);

        Button btnLeft = createCustomButton("Clique Esq.");
        btnLeft.setOnClickListener(v -> {
            vibrarFeedback(30);
            client.sendCommand("MOUSE_CLICK", "LEFT");
        });

        Button btnRight = createCustomButton("Clique Dir.");
        btnRight.setOnClickListener(v -> {
            vibrarFeedback(30);
            client.sendCommand("MOUSE_CLICK", "RIGHT");
        });

        LinearLayout.LayoutParams p1 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        p1.setMargins(0, 0, 6, 0);
        btnLeft.setLayoutParams(p1);
        btnRight.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        clickRow.addView(btnLeft);
        clickRow.addView(btnRight);
        root.addView(clickRow);

        LinearLayout extraRow = new LinearLayout(this);
        extraRow.setOrientation(LinearLayout.HORIZONTAL);

        Button btnPrevSlide = createCustomButton("⏪ Slide Ant.");
        btnPrevSlide.setOnClickListener(v -> client.sendCommand("KEY_PRESS", "PAGE_UP"));

        Button btnNextSlide = createCustomButton("⏩ Próx. Slide");
        btnNextSlide.setOnClickListener(v -> client.sendCommand("KEY_PRESS", "PAGE_DOWN"));

        Button btnKeyboard = createCustomButton("⌨️ Digitar...");
        btnKeyboard.setOnClickListener(v -> exibirDialogDigitarTexto());

        LinearLayout.LayoutParams p2 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        p2.setMargins(0, 0, 4, 0);
        btnPrevSlide.setLayoutParams(p2);
        btnNextSlide.setLayoutParams(p2);
        btnKeyboard.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        extraRow.addView(btnPrevSlide);
        extraRow.addView(btnNextSlide);
        extraRow.addView(btnKeyboard);
        root.addView(extraRow);

        builder.setView(root);
        builder.setPositiveButton("Fechar", null);
        builder.show();
    }

    private void exibirDialogDigitarTexto() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("⌨️ Digitar Texto no PC");
        EditText input = new EditText(this);
        input.setHint("Digite o texto para enviar ao PC...");
        builder.setView(input);
        builder.setPositiveButton("Enviar", (dialog, which) -> {
            String text = input.getText().toString();
            if (!text.isEmpty()) {
                client.sendCommand("KEY_TYPE", text);
                Toast.makeText(this, "Digitado no PC!", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Cancelar", null);
        builder.show();
    }

    private void exibirDialogExplorador(String path, List<RemoteDirItem> items) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(Color.parseColor("#0F172A"));
        layout.setPadding(16, 16, 16, 16);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, 0, 0, 10);

        TextView pathLbl = new TextView(this);
        pathLbl.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        pathLbl.setText("📂 " + (path.equals("ROOT") ? "PC (Início)" : path));
        pathLbl.setTextColor(Color.WHITE);
        pathLbl.setTextSize(13);
        pathLbl.setTypeface(null, Typeface.BOLD);
        header.addView(pathLbl);

        if (!"ROOT".equals(path)) {
            Button btnUp = new Button(this);
            btnUp.setText("⬆️ Voltar");
            btnUp.setTextSize(11);
            btnUp.setBackgroundResource(R.drawable.bg_button_secondary);
            btnUp.setTextColor(Color.parseColor("#93C5FD"));
            btnUp.setOnClickListener(v -> {
                File cur = new File(path);
                File parent = cur.getParentFile();
                client.sendCommand("BROWSE_DIR", parent != null ? parent.getAbsolutePath() : "ROOT");
                if (exploreDialog != null) exploreDialog.dismiss();
            });
            header.addView(btnUp);
        }
        layout.addView(header);

        ListView listView = new ListView(this);
        listView.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 700));

        List<String> labels = new ArrayList<>();
        for (RemoteDirItem it : items) {
            String prefix = it.isDir ? "📁 " : "📄 ";
            String suffix = it.isDir ? "" : " (" + formatFileSize(it.size) + ")";
            labels.add(prefix + it.name + suffix);
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, labels) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView tv = (TextView) super.getView(position, convertView, parent);
                tv.setTextColor(Color.parseColor("#F1F5F9"));
                tv.setTextSize(14);
                tv.setPadding(12, 16, 12, 16);
                return tv;
            }
        };
        listView.setAdapter(adapter);

        listView.setOnItemClickListener((parent, view, position, id) -> {
            RemoteDirItem selected = items.get(position);
            if (selected.isDir) {
                client.sendCommand("BROWSE_DIR", selected.fullPath);
                if (exploreDialog != null) exploreDialog.dismiss();
            } else {
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Baixar Arquivo do PC")
                        .setMessage("Deseja baixar \"" + selected.name + "\" para o celular?")
                        .setPositiveButton("Baixar Agora", (d, w) -> {
                            client.sendCommand("REQUEST_FILE", selected.fullPath);
                            Toast.makeText(MainActivity.this, "Baixando " + selected.name + "...", Toast.LENGTH_SHORT).show();
                        })
                        .setNegativeButton("Cancelar", null)
                        .show();
            }
        });

        layout.addView(listView);
        builder.setView(layout);
        builder.setPositiveButton("Fechar", null);

        if (exploreDialog != null && exploreDialog.isShowing()) {
            exploreDialog.dismiss();
        }
        exploreDialog = builder.create();
        exploreDialog.show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERM_CAMERA && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            showCameraStudioDialog();
        } else if (requestCode == PERM_AUDIO && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            showMicDialog();
        }
    }

    private void addFileCard(String sender, String fileName, long size, Uri fileUri, boolean isSent) {
        welcomeHint.setVisibility(View.GONE);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        row.setGravity(isSent ? Gravity.END : Gravity.START);
        row.setPadding(0, 6, 0, 6);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(isSent ? R.drawable.bg_bubble_sent : R.drawable.bg_card);
        card.setPadding(22, 16, 22, 16);

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        if (isSent) {
            cardParams.setMargins(90, 0, 0, 0);
        } else {
            cardParams.setMargins(0, 0, 90, 0);
        }
        card.setLayoutParams(cardParams);

        TextView header = new TextView(this);
        header.setText((isSent ? "📤 Enviado: " : "📥 Recebido: ") + sender + " • " + getCurrentTime());
        header.setTextColor(isSent ? Color.parseColor("#BFDBFE") : Color.parseColor("#94A3B8"));
        header.setTextSize(11);
        header.setTypeface(null, Typeface.BOLD);
        card.addView(header);

        if (isImageFile(fileName) && fileUri != null) {
            try {
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inSampleSize = 4;
                InputStream is = getContentResolver().openInputStream(fileUri);
                Bitmap bmp = BitmapFactory.decodeStream(is, null, opts);
                if (bmp != null) {
                    ImageView iv = new ImageView(this);
                    iv.setImageBitmap(bmp);
                    iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    LinearLayout.LayoutParams ivParams = new LinearLayout.LayoutParams(260, 200);
                    ivParams.setMargins(0, 8, 0, 8);
                    iv.setLayoutParams(ivParams);
                    card.addView(iv);
                }
            } catch (Exception ignored) {}
        }

        String icon = getFileIcon(fileName);
        TextView fileInfo = new TextView(this);
        fileInfo.setText(icon + " " + fileName + "\n(" + formatFileSize(size) + ")");
        fileInfo.setTextColor(Color.WHITE);
        fileInfo.setTextSize(14);
        fileInfo.setTypeface(null, Typeface.BOLD);
        fileInfo.setPadding(0, 6, 0, 6);
        card.addView(fileInfo);

        if (fileUri != null) {
            TextView pathHint = new TextView(this);
            pathHint.setText("✓ Salvo em Downloads/TransferApp");
            pathHint.setTextColor(Color.parseColor("#10B981"));
            pathHint.setTextSize(11);
            card.addView(pathHint);

            LinearLayout actions = new LinearLayout(this);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            actions.setPadding(0, 10, 0, 0);

            Button btnOpen = new Button(this);
            btnOpen.setText("Abrir");
            btnOpen.setTextSize(12);
            btnOpen.setBackgroundResource(R.drawable.bg_button_action);
            btnOpen.setTextColor(Color.WHITE);
            btnOpen.setOnClickListener(v -> abrirArquivo(fileUri, fileName));

            Button btnShare = new Button(this);
            btnShare.setText("Compartilhar");
            btnShare.setTextSize(12);
            btnShare.setBackgroundResource(R.drawable.bg_button_secondary);
            btnShare.setTextColor(Color.parseColor("#93C5FD"));
            btnShare.setOnClickListener(v -> compartilharArquivo(fileUri, fileName));

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
            lp.setMargins(0, 0, 8, 0);
            btnOpen.setLayoutParams(lp);
            btnShare.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));

            actions.addView(btnOpen);
            actions.addView(btnShare);
            card.addView(actions);
        }

        row.addView(card);
        chatMessagesContainer.addView(row);
        scrollToBottom();
    }

    private boolean isImageFile(String name) {
        String lower = name.toLowerCase();
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".webp") || lower.endsWith(".gif");
    }

    private Button createCustomButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(12);
        b.setTextColor(Color.parseColor("#E2E8F0"));
        b.setBackgroundResource(R.drawable.bg_button_secondary);
        b.setPadding(8, 8, 8, 8);
        return b;
    }

    private void conectarAoIp(String host) {
        if (host.isEmpty()) {
            ipEditText.setError("Informe o IP do PC");
            return;
        }
        prefs.edit().putString("last_ip", host).apply();
        client.connect(host, PORT);
    }

    private void iniciarDescobertaPc() {
        discoverButton.setEnabled(false);
        discoverButton.setText("🔍 Buscando PC na rede Wi-Fi...");
        discoveredBanner.setVisibility(View.GONE);

        DiscoveryClient.discover(this, new DiscoveryClient.DiscoveryListener() {
            @Override
            public void onServerFound(String serverName, String host, int port) {
                runOnUiThread(() -> {
                    discoveredIp = host;
                    discoveredText.setText("🖥️ " + serverName + "\nIP: " + host + ":" + port);
                    discoveredBanner.setVisibility(View.VISIBLE);
                });
            }

            @Override
            public void onDiscoveryFinished() {
                runOnUiThread(() -> {
                    discoverButton.setEnabled(true);
                    discoverButton.setText("🔍 Buscar PC Automaticamente na Rede");
                });
            }
        });
    }

    private void enviarMensagemTexto() {
        String texto = messageEditText.getText().toString().trim();
        if (texto.isEmpty()) return;

        client.sendText(texto);
        addTextMessage("Você (Celular)", texto, true);
        messageEditText.setText("");
    }

    private void enviarClipboardLocal() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null && clipboard.hasPrimaryClip() && clipboard.getPrimaryClip().getItemCount() > 0) {
            CharSequence text = clipboard.getPrimaryClip().getItemAt(0).getText();
            if (text != null && text.length() > 0) {
                client.sendClipboard(text.toString());
                addClipboardCard("Você (Celular)", text.toString());
                Toast.makeText(this, "Clipboard enviado ao PC!", Toast.LENGTH_SHORT).show();
                return;
            }
        }
        Toast.makeText(this, "Área de transferência está vazia.", Toast.LENGTH_SHORT).show();
    }

    private void copyToClipboard(String text) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            ClipData clip = ClipData.newPlainText("PCcell", text);
            clipboard.setPrimaryClip(clip);
        }
    }

    private void exibirDialogControleRemoto() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("🎮 Controle Remoto de Mídia");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(30, 20, 30, 20);

        LinearLayout mediaRow = new LinearLayout(this);
        mediaRow.setOrientation(LinearLayout.HORIZONTAL);
        mediaRow.setGravity(Gravity.CENTER);

        Button btnPlay = createCustomButton("⏯️ Play");
        Button btnVolDown = createCustomButton("🔉 Vol -");
        Button btnVolUp = createCustomButton("🔊 Vol +");
        Button btnMute = createCustomButton("🔇 Mudo");

        btnPlay.setOnClickListener(v -> client.sendCommand("PLAY_PAUSE", ""));
        btnVolDown.setOnClickListener(v -> client.sendCommand("VOL_DOWN", ""));
        btnVolUp.setOnClickListener(v -> client.sendCommand("VOL_UP", ""));
        btnMute.setOnClickListener(v -> client.sendCommand("MUTE", ""));

        mediaRow.addView(btnPlay);
        mediaRow.addView(btnVolDown);
        mediaRow.addView(btnVolUp);
        mediaRow.addView(btnMute);
        layout.addView(mediaRow);

        TextView lblUrl = new TextView(this);
        lblUrl.setText("\nAbrir Link no Navegador do PC:");
        lblUrl.setTextSize(13);
        layout.addView(lblUrl);

        EditText urlInput = new EditText(this);
        urlInput.setHint("https://youtube.com");
        layout.addView(urlInput);

        Button btnOpenUrl = createCustomButton("🌐 Abrir no PC");
        btnOpenUrl.setOnClickListener(v -> {
            String url = urlInput.getText().toString().trim();
            if (!url.isEmpty()) {
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    url = "https://" + url;
                }
                client.sendCommand("OPEN_URL", url);
                Toast.makeText(this, "Enviando URL ao PC...", Toast.LENGTH_SHORT).show();
            }
        });
        layout.addView(btnOpenUrl);

        builder.setView(layout);
        builder.setPositiveButton("Fechar", null);
        builder.show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK) return;

        if (requestCode == PICK_FILE && data != null) {
            if (data.getClipData() != null) {
                ClipData clipData = data.getClipData();
                for (int i = 0; i < clipData.getItemCount(); i++) {
                    enviarUri(clipData.getItemAt(i).getUri());
                }
            } else if (data.getData() != null) {
                enviarUri(data.getData());
            }
        } else if (requestCode == TAKE_PHOTO_FOR_CLIPBOARD) {
            // Foto tirada para enviar direto para a Área de Transferência (Ctrl+V) do PC!
            try {
                File photoFile = new File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "clipboard_temp.jpg");
                if (photoFile.exists()) {
                    FileInputStream fis = new FileInputStream(photoFile);
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    byte[] buf = new byte[4096];
                    int r;
                    while ((r = fis.read(buf)) != -1) baos.write(buf, 0, r);
                    fis.close();
                    client.sendClipboardImage(baos.toByteArray());
                    vibrarFeedback(60);
                    Toast.makeText(this, "📸 Foto enviada! Já está no Ctrl+V do seu PC.", Toast.LENGTH_LONG).show();
                }
            } catch (Exception e) {
                Toast.makeText(this, "Erro ao enviar foto: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void enviarUri(Uri uri) {
        if (uri == null) return;
        String name = "arquivo";
        long size = -1;

        try (Cursor c = getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int n = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                int s = c.getColumnIndex(OpenableColumns.SIZE);
                if (n >= 0) name = c.getString(n);
                if (s >= 0 && !c.isNull(s)) size = c.getLong(s);
            }
        } catch (Exception ignored) {}

        if (size <= 0) {
            try (InputStream test = getContentResolver().openInputStream(uri)) {
                if (test != null) size = test.available();
            } catch (Exception ignored) {}
        }

        try {
            InputStream in = getContentResolver().openInputStream(uri);
            if (in == null) throw new IOException("Arquivo indisponível.");
            client.sendFile(name, size, in);
        } catch (IOException e) {
            Toast.makeText(this, "Erro ao abrir arquivo: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void handleIncomingShareIntent(Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        String type = intent.getType();

        if (Intent.ACTION_SEND.equals(action) && type != null) {
            if ("text/plain".equals(type)) {
                String sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
                if (sharedText != null) {
                    messageEditText.setText(sharedText);
                    Toast.makeText(this, "Texto pronto para envio!", Toast.LENGTH_SHORT).show();
                }
            } else {
                Uri fileUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
                if (fileUri != null) {
                    enviarUri(fileUri);
                }
            }
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(action) && type != null) {
            ArrayList<Uri> fileUris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            if (fileUris != null) {
                for (Uri u : fileUris) {
                    enviarUri(u);
                }
            }
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIncomingShareIntent(intent);
    }

    private OutputStream createDownloadDestination(String originalName) throws IOException {
        String name = new File(originalName).getName().replaceAll("[\\\\/:*?\"<>|]", "_");
        if (Build.VERSION.SDK_INT >= 29) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Downloads.DISPLAY_NAME, name);
            values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/TransferApp");
            Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri == null) throw new IOException("Não foi possível criar entrada em Downloads.");
            lastSavedUri = uri;
            OutputStream out = getContentResolver().openOutputStream(uri);
            if (out == null) throw new IOException("Não foi possível gravar no arquivo.");
            return out;
        }

        File folder = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "TransferApp");
        if (!folder.exists() && !folder.mkdirs()) {
            folder = new File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "TransferApp");
            if (!folder.exists() && !folder.mkdirs()) {
                throw new IOException("Não foi possível criar a pasta de destino.");
            }
        }
        File target = new File(folder, name);
        lastSavedUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", target);
        return new FileOutputStream(target);
    }

    private void addTextMessage(String sender, String text, boolean isSent) {
        welcomeHint.setVisibility(View.GONE);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        row.setGravity(isSent ? Gravity.END : Gravity.START);
        row.setPadding(0, 4, 0, 4);

        LinearLayout bubble = new LinearLayout(this);
        bubble.setOrientation(LinearLayout.VERTICAL);
        bubble.setBackgroundResource(isSent ? R.drawable.bg_bubble_sent : R.drawable.bg_bubble_received);
        bubble.setPadding(26, 14, 26, 14);

        LinearLayout.LayoutParams bubbleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        if (isSent) {
            bubbleParams.setMargins(110, 0, 0, 0);
        } else {
            bubbleParams.setMargins(0, 0, 110, 0);
        }
        bubble.setLayoutParams(bubbleParams);

        TextView senderView = new TextView(this);
        senderView.setText(sender + " • " + getCurrentTime());
        senderView.setTextColor(isSent ? Color.parseColor("#BFDBFE") : Color.parseColor("#94A3B8"));
        senderView.setTextSize(11);
        bubble.addView(senderView);

        TextView contentView = new TextView(this);
        contentView.setText(text);
        contentView.setTextColor(Color.WHITE);
        contentView.setTextSize(15);
        contentView.setTextIsSelectable(true);
        bubble.addView(contentView);

        if (text.startsWith("http://") || text.startsWith("https://")) {
            Button linkBtn = new Button(this);
            linkBtn.setText("🌐 Abrir Link");
            linkBtn.setTextSize(11);
            linkBtn.setBackgroundResource(R.drawable.bg_button_secondary);
            linkBtn.setTextColor(Color.parseColor("#93C5FD"));
            linkBtn.setOnClickListener(v -> {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(text)));
                } catch (Exception ignored) {}
            });
            bubble.addView(linkBtn);
        }

        row.addView(bubble);
        chatMessagesContainer.addView(row);
        scrollToBottom();
    }

    private void addClipboardCard(String sender, String text) {
        welcomeHint.setVisibility(View.GONE);

        boolean isSent = sender.startsWith("Você");
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        row.setGravity(isSent ? Gravity.END : Gravity.START);
        row.setPadding(0, 4, 0, 4);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(isSent ? R.drawable.bg_bubble_sent : R.drawable.bg_card);
        card.setPadding(20, 14, 20, 14);

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        if (isSent) cardParams.setMargins(90, 0, 0, 0);
        else cardParams.setMargins(0, 0, 90, 0);
        card.setLayoutParams(cardParams);

        TextView header = new TextView(this);
        header.setText("📋 Área de Transferência • " + sender);
        header.setTextColor(isSent ? Color.parseColor("#BFDBFE") : Color.parseColor("#93C5FD"));
        header.setTextSize(11);
        card.addView(header);

        String preview = text.length() > 100 ? text.substring(0, 100) + "..." : text;
        TextView content = new TextView(this);
        content.setText(preview);
        content.setTextColor(Color.WHITE);
        content.setTextSize(13);
        content.setPadding(0, 4, 0, 4);
        card.addView(content);

        Button copyBtn = new Button(this);
        copyBtn.setText("Copiar novamente");
        copyBtn.setTextSize(11);
        copyBtn.setBackgroundResource(R.drawable.bg_button_secondary);
        copyBtn.setTextColor(Color.parseColor("#93C5FD"));
        copyBtn.setOnClickListener(v -> {
            copyToClipboard(text);
            Toast.makeText(this, "Copiado!", Toast.LENGTH_SHORT).show();
        });
        card.addView(copyBtn);

        row.addView(card);
        chatMessagesContainer.addView(row);
        scrollToBottom();
    }

    private void abrirArquivo(Uri uri, String fileName) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            String mime = getMimeType(fileName);
            intent.setDataAndType(uri, mime);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "Abrir com"));
        } catch (Exception e) {
            Toast.makeText(this, "Nenhum aplicativo encontrado para abrir esse arquivo: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void compartilharArquivo(Uri uri, String fileName) {
        try {
            Intent intent = new Intent(Intent.ACTION_SEND);
            String mime = getMimeType(fileName);
            intent.setType(mime);
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "Compartilhar arquivo"));
        } catch (Exception e) {
            Toast.makeText(this, "Falha ao compartilhar: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private String getMimeType(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot >= 0) {
            String ext = fileName.substring(dot + 1).toLowerCase();
            String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
            if (mime != null) return mime;
        }
        return "*/*";
    }

    private String getFileIcon(String name) {
        String lower = name.toLowerCase();
        if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".webp") || lower.endsWith(".gif")) return "🖼️";
        if (lower.endsWith(".mp4") || lower.endsWith(".mkv") || lower.endsWith(".avi") || lower.endsWith(".mov")) return "🎬";
        if (lower.endsWith(".mp3") || lower.endsWith(".wav") || lower.endsWith(".aac") || lower.endsWith(".flac")) return "🎵";
        if (lower.endsWith(".pdf") || lower.endsWith(".doc") || lower.endsWith(".docx") || lower.endsWith(".txt")) return "📄";
        if (lower.endsWith(".zip") || lower.endsWith(".rar") || lower.endsWith(".7z") || lower.endsWith(".tar")) return "📦";
        if (lower.endsWith(".apk")) return "📱";
        return "📁";
    }

    private static String formatFileSize(long bytes) {
        if (bytes <= 0) return "0 B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        char pre = "KMGTPE".charAt(Math.max(0, exp - 1));
        return String.format(Locale.getDefault(), "%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }

    private String getCurrentTime() {
        return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
    }

    private void scrollToBottom() {
        chatScrollView.post(() -> chatScrollView.fullScroll(View.FOCUS_DOWN));
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Preserva conexão ativa ao girar celular entre vertical e horizontal!
    }

    @Override
    protected void onDestroy() {
        pararCamera();
        pararMicrofone();
        stopMicPulse();
        if (remoteDesktopDialog != null && remoteDesktopDialog.isShowing()) remoteDesktopDialog.dismiss();
        if (micDialog != null && micDialog.isShowing()) micDialog.dismiss();
        if (client != null) client.shutdown();
        super.onDestroy();
    }
}
