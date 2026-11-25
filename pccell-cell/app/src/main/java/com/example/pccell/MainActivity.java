package com.example.pccell;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.animation.AnimatorSet;
import android.app.Activity;
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
    private Dialog exploreDialog;
    private Dialog hardwareDialog;
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
    private ImageView micIcon;
    private AnimatorSet micPulseAnimator;
    private TextView allToolsButton;
    private LinearLayout connectionCard;

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
        connectionCard = findViewById(R.id.connectionCard);
        allToolsButton = findViewById(R.id.allToolsButton);

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
                    statusTextView.setText(connected ? "● Conectado" : (connecting ? "○ Conectando" : "○ Desconectado"));
                    statusTextView.setTextColor(connected ? Color.parseColor("#10B981") : (connecting ? Color.parseColor("#F59E0B") : Color.parseColor("#A1A1AA")));

                    connectButton.setText(connected ? "Desconectar" : (connecting ? "Cancelar" : "Conectar"));
                    ipEditText.setEnabled(!connected && !connecting);
                    discoverButton.setEnabled(!connected && !connecting);
                    discoverButton.setVisibility(connected ? View.GONE : View.VISIBLE);

                    float activeAlpha = connected ? 1.0f : 0.45f;
                    Button[] tools = {
                            attachButton, macroButton, powerButton, gamepadButton,
                            photoToClipboardButton, screenshotButton, touchpadButton,
                            explorePcButton, micButton, webcamButton, clipboardButton,
                            remoteButton, sendButton
                    };
                    for (Button b : tools) {
                        b.setEnabled(connected);
                        b.setAlpha(activeAlpha);
                    }
                    messageEditText.setEnabled(connected);
                    allToolsButton.setEnabled(connected);
                    allToolsButton.setAlpha(activeAlpha);
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
                    Toast.makeText(MainActivity.this, "Texto copiado para a area de transferencia.", Toast.LENGTH_SHORT).show();
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
                        String txt = "CPU: " + cpu + "%\n" +
                                "RAM: " + ram + "% (" + (ramTotalMb - ramFreeMb) + " / " + ramTotalMb + " MB)\n" +
                                "Disco (C:): " + disk + "% (" + diskFreeGb + " GB livres de " + diskTotalGb + " GB)";
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

        // Botão Grade Completa ("Ver todos")
        allToolsButton.setOnClickListener(v -> showAllToolsDialog());

        // 1. Macro Deck
        macroButton.setOnClickListener(v -> exibirMacroDeckDialog());

        // 2. Energia & Hardware
        powerButton.setOnClickListener(v -> exibirEnergiaHardwareDialog());

        // 3. Gamepad
        gamepadButton.setOnClickListener(v -> showGamepadDialog());

        // 4. Foto no Ctrl+V
        photoToClipboardButton.setOnClickListener(v -> tirarFotoParaClipboard());

        // 5. Ver Tela 60Hz
        screenshotButton.setOnClickListener(v -> showRemoteDesktopDialog());

        // 6. Touchpad
        touchpadButton.setOnClickListener(v -> exibirTouchpadDialog());

        // 7. Explorador Remoto
        explorePcButton.setOnClickListener(v -> {
            Toast.makeText(this, "Carregando pastas do PC...", Toast.LENGTH_SHORT).show();
            client.sendCommand("BROWSE_DIR", "ROOT");
        });

        // 8. Microfone Sem Fio
        micButton.setOnClickListener(v -> showMicDialog());

        // 9. Câmera / Webcam
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
    // HELPERS DE INTERFACE MINIMALISTA
    // ==========================================
    private Dialog createDarkModal(LinearLayout root) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        }
        dialog.setContentView(root);
        if (dialog.getWindow() != null) {
            int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.92f);
            dialog.getWindow().setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        return dialog;
    }

    private LinearLayout createDarkModalRoot(String title, String subtitle) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(20), dp(20), dp(18));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#111114"));
        bg.setCornerRadius(dp(20));
        bg.setStroke(dp(1), Color.parseColor("#27272A"));
        root.setBackground(bg);

        TextView tvTitle = new TextView(this);
        tvTitle.setText(title);
        tvTitle.setTextColor(Color.parseColor("#FAFAFA"));
        tvTitle.setTextSize(17);
        tvTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        root.addView(tvTitle);

        if (subtitle != null && !subtitle.isEmpty()) {
            TextView tvSub = new TextView(this);
            tvSub.setText(subtitle);
            tvSub.setTextColor(Color.parseColor("#A1A1AA"));
            tvSub.setTextSize(12);
            tvSub.setPadding(0, dp(3), 0, 0);
            root.addView(tvSub);
        }

        View divider = new View(this);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1);
        dlp.setMargins(0, dp(12), 0, dp(14));
        divider.setLayoutParams(dlp);
        divider.setBackgroundColor(Color.parseColor("#27272A"));
        root.addView(divider);

        return root;
    }

    private Button makeStyledModalBtn(String text, String fillHex, String strokeHex, String textHex) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setAllCaps(false);
        btn.setTextSize(13);
        btn.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        btn.setTextColor(Color.parseColor(textHex != null ? textHex : "#FAFAFA"));
        btn.setStateListAnimator(null);
        btn.setPadding(dp(12), dp(10), dp(12), dp(10));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor(fillHex != null ? fillHex : "#18181C"));
        bg.setCornerRadius(dp(12));
        bg.setStroke(dp(1), Color.parseColor(strokeHex != null ? strokeHex : "#27272A"));
        btn.setBackground(bg);
        return btn;
    }

    private Button makeModalCloseButton(Dialog dialog) {
        Button close = makeStyledModalBtn("Fechar", "#18181C", "#27272A", "#A1A1AA");
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42));
        lp.setMargins(0, dp(14), 0, 0);
        close.setLayoutParams(lp);
        close.setOnClickListener(v -> dialog.dismiss());
        return close;
    }

    private void showAllToolsDialog() {
        LinearLayout root = createDarkModalRoot("Todos os Controles", "Selecione uma ferramenta para o PC");
        Dialog dialog = createDarkModal(root);

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(3);

        Object[][] tools = {
                {"Tela 60Hz", R.drawable.ic_monitor, (Runnable) this::showRemoteDesktopDialog},
                {"Touchpad", R.drawable.ic_touchpad, (Runnable) this::exibirTouchpadDialog},
                {"Gamepad", R.drawable.ic_gamepad, (Runnable) this::showGamepadDialog},
                {"Webcam", R.drawable.ic_video, (Runnable) this::showCameraStudioDialog},
                {"Microfone", R.drawable.ic_mic, (Runnable) this::showMicDialog},
                {"Arquivos", R.drawable.ic_folder, (Runnable) () -> attachButton.performClick()},
                {"Pastas PC", R.drawable.ic_folder_open, (Runnable) () -> explorePcButton.performClick()},
                {"Foto Ctrl+V", R.drawable.ic_camera_shot, (Runnable) this::tirarFotoParaClipboard},
                {"Clipboard", R.drawable.ic_clipboard, (Runnable) this::enviarClipboardLocal},
                {"Macros", R.drawable.ic_macro, (Runnable) this::exibirMacroDeckDialog},
                {"Energia", R.drawable.ic_power, (Runnable) this::exibirEnergiaHardwareDialog},
                {"Midia", R.drawable.ic_sliders, (Runnable) this::exibirDialogControleRemoto}
        };

        for (Object[] item : tools) {
            String label = (String) item[0];
            int iconRes = (int) item[1];
            Runnable action = (Runnable) item[2];

            Button tile = new Button(this);
            tile.setText(label);
            tile.setAllCaps(false);
            tile.setTextSize(11);
            tile.setTextColor(Color.parseColor("#E4E4E7"));
            tile.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            tile.setStateListAnimator(null);
            tile.setBackgroundResource(R.drawable.bg_tool_tile);
            tile.setCompoundDrawablesWithIntrinsicBounds(0, iconRes, 0, 0);
            tile.setCompoundDrawablePadding(dp(6));
            tile.setPadding(dp(6), dp(12), dp(6), dp(10));

            GridLayout.LayoutParams gp = new GridLayout.LayoutParams();
            gp.width = 0;
            gp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            gp.setMargins(dp(4), dp(4), dp(4), dp(4));
            tile.setLayoutParams(gp);
            tile.setOnClickListener(v -> {
                dialog.dismiss();
                action.run();
            });
            grid.addView(tile);
        }

        root.addView(grid);
        root.addView(makeModalCloseButton(dialog));
        dialog.show();
    }

    // ==========================================
    // 1. MACRO DECK / STREAM DECK
    // ==========================================
    private void exibirMacroDeckDialog() {
        LinearLayout root = createDarkModalRoot("Macro Deck", "Atalhos rapidos para o Windows");
        Dialog dialog = createDarkModal(root);

        // Botao Modo Panico discreto e claro
        Button btnPanic = makeStyledModalBtn("Modo Panico (Minimizar e Mutar)", "#241115", "#EF4444", "#FCA5A5");
        LinearLayout.LayoutParams panicLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        panicLp.setMargins(0, 0, 0, dp(10));
        btnPanic.setLayoutParams(panicLp);
        btnPanic.setOnClickListener(v -> {
            vibrarFeedback(80);
            client.sendCommand("MACRO", "PANIC");
            Toast.makeText(this, "Modo panico ativado.", Toast.LENGTH_SHORT).show();
        });
        root.addView(btnPanic);

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(2);

        addGridMacro(grid, "Mostrar Desktop", "DESKTOP");
        addGridMacro(grid, "Gerenciador Tarefas", "TASK_MANAGER");
        addGridMacro(grid, "Fechar (Alt+F4)", "ALT_F4");
        addGridMacro(grid, "Alternar (Alt+Tab)", "ALT_TAB");
        addGridMacro(grid, "Explorador Windows", "EXPLORER");
        addGridMacro(grid, "Calculadora", "CALC");
        addGridMacro(grid, "Bloco de Notas", "NOTEPAD");
        addGridMacro(grid, "Spotify", "SPOTIFY");
        addGridMacro(grid, "Navegador", "BROWSER");

        root.addView(grid);
        root.addView(makeModalCloseButton(dialog));
        dialog.show();
    }

    private void addGridMacro(GridLayout grid, String title, String macroCode) {
        Button btn = makeStyledModalBtn(title, "#18181C", "#27272A", "#E4E4E7");
        btn.setTextSize(12);
        GridLayout.LayoutParams p = new GridLayout.LayoutParams();
        p.width = 0;
        p.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        p.setMargins(dp(4), dp(4), dp(4), dp(4));
        btn.setLayoutParams(p);
        btn.setOnClickListener(v -> {
            vibrarFeedback(30);
            client.sendCommand("MACRO", macroCode);
        });
        grid.addView(btn);
    }

    // ==========================================
    // 2. ENERGIA E MONITOR DE HARDWARE
    // ==========================================
    private void exibirEnergiaHardwareDialog() {
        LinearLayout root = createDarkModalRoot("Energia e Hardware", "Telemetria e controle do sistema");
        Dialog dialog = createDarkModal(root);

        hardwareStatsText = new TextView(this);
        hardwareStatsText.setText("Carregando telemetria...");
        hardwareStatsText.setTextColor(Color.parseColor("#38BDF8"));
        hardwareStatsText.setTextSize(13);
        hardwareStatsText.setTypeface(Typeface.MONOSPACE);
        hardwareStatsText.setLineSpacing(dp(4), 1.0f);
        GradientDrawable statsBg = new GradientDrawable();
        statsBg.setColor(Color.parseColor("#09090B"));
        statsBg.setCornerRadius(dp(12));
        statsBg.setStroke(dp(1), Color.parseColor("#27272A"));
        hardwareStatsText.setBackground(statsBg);
        hardwareStatsText.setPadding(dp(14), dp(12), dp(14), dp(12));
        root.addView(hardwareStatsText);

        client.sendCommand("GET_HARDWARE_STATS", "");

        TextView lblPower = new TextView(this);
        lblPower.setText("CONTROLE DE ENERGIA");
        lblPower.setTextColor(Color.parseColor("#71717A"));
        lblPower.setTextSize(10);
        lblPower.setLetterSpacing(0.08f);
        lblPower.setPadding(0, dp(14), 0, dp(8));
        root.addView(lblPower);

        Button btnLock = makeStyledModalBtn("Bloquear Tela do Windows", "#18181C", "#27272A", "#FAFAFA");
        LinearLayout.LayoutParams lockLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42));
        lockLp.setMargins(0, 0, 0, dp(8));
        btnLock.setLayoutParams(lockLp);
        btnLock.setOnClickListener(v -> client.sendCommand("POWER", "LOCK"));
        root.addView(btnLock);

        Button btnSleep = makeStyledModalBtn("Suspender Computador", "#18181C", "#27272A", "#FAFAFA");
        LinearLayout.LayoutParams sleepLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42));
        sleepLp.setMargins(0, 0, 0, dp(8));
        btnSleep.setLayoutParams(sleepLp);
        btnSleep.setOnClickListener(v -> client.sendCommand("POWER", "SLEEP"));
        root.addView(btnSleep);

        LinearLayout timerRow = new LinearLayout(this);
        timerRow.setOrientation(LinearLayout.HORIZONTAL);

        Button btnTimer30 = makeStyledModalBtn("Desligar 30m", "#18181C", "#27272A", "#FBBF24");
        btnTimer30.setOnClickListener(v -> {
            client.sendCommand("POWER", "SHUTDOWN_TIMER:1800");
            Toast.makeText(this, "Desligamento agendado para 30 minutos.", Toast.LENGTH_SHORT).show();
        });

        Button btnCancelTimer = makeStyledModalBtn("Cancelar Timer", "#18181C", "#27272A", "#A1A1AA");
        btnCancelTimer.setOnClickListener(v -> {
            client.sendCommand("POWER", "SHUTDOWN_CANCEL");
            Toast.makeText(this, "Desligamento cancelado.", Toast.LENGTH_SHORT).show();
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(42), 1f);
        lp.setMargins(0, 0, dp(6), 0);
        btnTimer30.setLayoutParams(lp);
        btnCancelTimer.setLayoutParams(new LinearLayout.LayoutParams(0, dp(42), 1f));
        timerRow.addView(btnTimer30);
        timerRow.addView(btnCancelTimer);
        root.addView(timerRow);

        root.addView(makeModalCloseButton(dialog));
        if (hardwareDialog != null && hardwareDialog.isShowing()) {
            hardwareDialog.dismiss();
        }
        hardwareDialog = dialog;
        dialog.show();
    }

    // ==========================================
    // 3. GAMEPAD VIRTUAL (MODO HORIZONTAL MINIMALISTA)
    // ==========================================
    private void showGamepadDialog() {
        Dialog dialog = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#09090B"));
        root.setPadding(dp(16), dp(10), dp(16), dp(10));
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

        Button btnSelect = makeSystemBtn("SELECT");
        setupGamepadTouch(btnSelect, "SELECT");
        Button btnStart = makeSystemBtn("START");
        setupGamepadTouch(btnStart, "START");

        Button btnExit = new Button(this);
        btnExit.setText("Sair");
        btnExit.setAllCaps(false);
        btnExit.setTextColor(Color.parseColor("#FCA5A5"));
        btnExit.setTextSize(12);
        btnExit.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        btnExit.setStateListAnimator(null);
        GradientDrawable exitBg = new GradientDrawable();
        exitBg.setColor(Color.parseColor("#1D1114"));
        exitBg.setCornerRadius(dp(10));
        exitBg.setStroke(dp(1), Color.parseColor("#EF4444"));
        btnExit.setBackground(exitBg);
        LinearLayout.LayoutParams exitLp = new LinearLayout.LayoutParams(dp(64), dp(36));
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

        // ---- ÁREA PRINCIPAL (D-PAD ESQUERDA, ABXY DIREITA) ----
        LinearLayout mainPlayArea = new LinearLayout(this);
        mainPlayArea.setOrientation(LinearLayout.HORIZONTAL);
        mainPlayArea.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams mainParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        mainParams.setMargins(0, dp(6), 0, dp(6));
        mainPlayArea.setLayoutParams(mainParams);

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
        dpadCenterBg.setColor(Color.parseColor("#111114"));
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

        Button btnL3 = makeThumbstickBtn("L3");
        setupGamepadTouch(btnL3, "LS");
        dpadWing.addView(btnL3);

        mainPlayArea.addView(dpadWing);

        LinearLayout centerInfo = new LinearLayout(this);
        centerInfo.setOrientation(LinearLayout.VERTICAL);
        centerInfo.setGravity(Gravity.CENTER);
        centerInfo.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.35f));

        TextView tvGame = new TextView(this);
        tvGame.setText("PCcell");
        tvGame.setTextColor(Color.parseColor("#3F3F46"));
        tvGame.setTextSize(13);
        tvGame.setLetterSpacing(0.12f);
        tvGame.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        tvGame.setGravity(Gravity.CENTER);
        centerInfo.addView(tvGame);

        mainPlayArea.addView(centerInfo);

        LinearLayout abxyWing = new LinearLayout(this);
        abxyWing.setOrientation(LinearLayout.VERTICAL);
        abxyWing.setGravity(Gravity.CENTER);
        abxyWing.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        Button btnY = makeActionBtn("Y", "#F59E0B");
        setupGamepadTouch(btnY, "Y");

        LinearLayout abxyMidRow = new LinearLayout(this);
        abxyMidRow.setOrientation(LinearLayout.HORIZONTAL);
        abxyMidRow.setGravity(Gravity.CENTER);

        Button btnX = makeActionBtn("X", "#38BDF8");
        setupGamepadTouch(btnX, "X");

        View abxySpacer = new View(this);
        abxySpacer.setLayoutParams(new LinearLayout.LayoutParams(dp(28), 1));

        Button btnB = makeActionBtn("B", "#EF4444");
        setupGamepadTouch(btnB, "B");

        abxyMidRow.addView(btnX);
        abxyMidRow.addView(abxySpacer);
        abxyMidRow.addView(btnB);

        Button btnA = makeActionBtn("A", "#10B981");
        setupGamepadTouch(btnA, "A");

        abxyWing.addView(btnY);
        abxyWing.addView(abxyMidRow);
        abxyWing.addView(btnA);

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
        b.setTextColor(Color.parseColor("#FAFAFA"));
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        b.setTextSize(14);
        b.setStateListAnimator(null);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#141418"));
        bg.setCornerRadius(dp(12));
        bg.setStroke(dp(1), Color.parseColor("#27272A"));
        b.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(78), dp(44));
        lp.setMargins(dp(4), 0, dp(4), 0);
        b.setLayoutParams(lp);
        return b;
    }

    private Button makeSystemBtn(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(Color.parseColor("#A1A1AA"));
        b.setTextSize(10);
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        b.setStateListAnimator(null);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#141418"));
        bg.setCornerRadius(dp(10));
        bg.setStroke(dp(1), Color.parseColor("#27272A"));
        b.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(68), dp(36));
        lp.setMargins(dp(4), 0, dp(4), 0);
        b.setLayoutParams(lp);
        return b;
    }

    private Button makeDpadBtn(String text, int widthPx, int heightPx) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(Color.parseColor("#E4E4E7"));
        b.setTextSize(20);
        b.setPadding(0, 0, 0, 0);
        b.setStateListAnimator(null);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#141418"));
        bg.setCornerRadius(dp(12));
        bg.setStroke(dp(1), Color.parseColor("#27272A"));
        b.setBackground(bg);
        b.setLayoutParams(new LinearLayout.LayoutParams(widthPx, heightPx));
        return b;
    }

    private Button makeActionBtn(String text, String accentHex) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(Color.parseColor(accentHex));
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        b.setTextSize(21);
        b.setPadding(0, 0, 0, 0);
        b.setStateListAnimator(null);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(Color.parseColor("#141418"));
        bg.setStroke(dp(2), Color.parseColor(accentHex));
        b.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(68), dp(68));
        lp.setMargins(dp(4), dp(4), dp(4), dp(4));
        b.setLayoutParams(lp);
        return b;
    }

    private Button makeThumbstickBtn(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(Color.parseColor("#A1A1AA"));
        b.setTextSize(12);
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        b.setPadding(0, 0, 0, 0);
        b.setStateListAnimator(null);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#141418"));
        bg.setCornerRadius(dp(18));
        bg.setStroke(dp(1), Color.parseColor("#27272A"));
        b.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(56), dp(38));
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
                btn.setAlpha(0.75f);
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
    // 4. FOTO DIRETO NO CTRL+V DO PC
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
            Toast.makeText(this, "Nao foi possivel abrir a camera: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // ==========================================
    // 5. REMOTE DESKTOP (60Hz + TOUCHPAD + BOTÕES ESQUERDO/DIREITO)
    // ==========================================
    private void showRemoteDesktopDialog() {
        remoteDesktopDialog = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        remoteDesktopDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(Color.parseColor("#09090B"));
        frame.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        remoteDesktopView = new ImageView(this);
        remoteDesktopView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        remoteDesktopView.setBackgroundColor(Color.parseColor("#09090B"));
        remoteDesktopView.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        final float[] lastTouchX = {0};
        final float[] lastTouchY = {0};
        final long[] downTime = {0};
        final boolean[] hasMoved = {false};

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

        LinearLayout topBarRD = new LinearLayout(this);
        topBarRD.setOrientation(LinearLayout.HORIZONTAL);
        topBarRD.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout.LayoutParams topBarLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        topBarLp.gravity = Gravity.TOP;
        topBarLp.setMargins(dp(16), dp(16), dp(16), 0);
        topBarRD.setLayoutParams(topBarLp);

        TextView badge60Hz = new TextView(this);
        badge60Hz.setText("● 60Hz");
        badge60Hz.setTextColor(Color.parseColor("#10B981"));
        badge60Hz.setTextSize(11);
        badge60Hz.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        badge60Hz.setPadding(dp(12), dp(6), dp(12), dp(6));
        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setColor(Color.parseColor("#D9111114"));
        badgeBg.setCornerRadius(dp(999));
        badgeBg.setStroke(dp(1), Color.parseColor("#27272A"));
        badge60Hz.setBackground(badgeBg);
        topBarRD.addView(badge60Hz);

        View topSpacer = new View(this);
        topSpacer.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1f));
        topBarRD.addView(topSpacer);

        Button closeBtnRD = new Button(this);
        closeBtnRD.setText("Fechar");
        closeBtnRD.setAllCaps(false);
        closeBtnRD.setTextColor(Color.parseColor("#FAFAFA"));
        closeBtnRD.setTextSize(12);
        closeBtnRD.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        closeBtnRD.setStateListAnimator(null);
        GradientDrawable closeBg = new GradientDrawable();
        closeBg.setColor(Color.parseColor("#D9111114"));
        closeBg.setCornerRadius(dp(999));
        closeBg.setStroke(dp(1), Color.parseColor("#27272A"));
        closeBtnRD.setBackground(closeBg);
        closeBtnRD.setPadding(dp(16), dp(6), dp(16), dp(6));
        closeBtnRD.setOnClickListener(v -> remoteDesktopDialog.dismiss());
        topBarRD.addView(closeBtnRD);

        // DOCK INFERIOR MINIMALISTA DE CLIQUES
        LinearLayout dockClicks = new LinearLayout(this);
        dockClicks.setOrientation(LinearLayout.HORIZONTAL);
        dockClicks.setGravity(Gravity.CENTER);
        GradientDrawable dockBg = new GradientDrawable();
        dockBg.setColor(Color.parseColor("#E6111114"));
        dockBg.setCornerRadius(dp(999));
        dockBg.setStroke(dp(1), Color.parseColor("#27272A"));
        dockClicks.setBackground(dockBg);
        dockClicks.setPadding(dp(8), dp(6), dp(8), dp(6));
        FrameLayout.LayoutParams dockLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dockLp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        dockLp.setMargins(0, 0, 0, dp(20));
        dockClicks.setLayoutParams(dockLp);

        Button btnClickLeft = new Button(this);
        btnClickLeft.setText("Clique Esq.");
        btnClickLeft.setAllCaps(false);
        btnClickLeft.setTextColor(Color.parseColor("#FAFAFA"));
        btnClickLeft.setTextSize(12);
        btnClickLeft.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        btnClickLeft.setStateListAnimator(null);
        GradientDrawable leftBg = new GradientDrawable();
        leftBg.setColor(Color.parseColor("#18181C"));
        leftBg.setCornerRadius(dp(999));
        leftBg.setStroke(dp(1), Color.parseColor("#10B981"));
        btnClickLeft.setBackground(leftBg);
        btnClickLeft.setLayoutParams(new LinearLayout.LayoutParams(dp(132), dp(42)));
        btnClickLeft.setOnTouchListener((v, e) -> {
            int action = e.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                vibrarFeedback(35);
                btnClickLeft.setAlpha(0.7f);
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
        dockDivider.setLayoutParams(new LinearLayout.LayoutParams(dp(8), 1));

        Button btnClickRight = new Button(this);
        btnClickRight.setText("Clique Dir.");
        btnClickRight.setAllCaps(false);
        btnClickRight.setTextColor(Color.parseColor("#FAFAFA"));
        btnClickRight.setTextSize(12);
        btnClickRight.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        btnClickRight.setStateListAnimator(null);
        GradientDrawable rightBg = new GradientDrawable();
        rightBg.setColor(Color.parseColor("#18181C"));
        rightBg.setCornerRadius(dp(999));
        rightBg.setStroke(dp(1), Color.parseColor("#F59E0B"));
        btnClickRight.setBackground(rightBg);
        btnClickRight.setLayoutParams(new LinearLayout.LayoutParams(dp(132), dp(42)));
        btnClickRight.setOnTouchListener((v, e) -> {
            int action = e.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                vibrarFeedback(35);
                btnClickRight.setAlpha(0.7f);
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

    // 6. CAMERA / WEBCAM - TELA DEDICADA MINIMALISTA
    // ==========================================
    private void showCameraStudioDialog() {
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, PERM_CAMERA);
            return;
        }

        Dialog cameraDialog = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        cameraDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(Color.parseColor("#09090B"));

        SurfaceView surfaceView = new SurfaceView(this);
        surfaceView.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        frame.addView(surfaceView);

        // Top overlay bar flutuante
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setPadding(dp(16), dp(16), dp(16), dp(12));
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout.LayoutParams topBarLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        topBar.setLayoutParams(topBarLp);

        Button btnClose = makeCameraOverlayBtn("Fechar", "#D9111114", "#27272A");
        btnClose.setOnClickListener(v -> cameraDialog.dismiss());
        topBar.addView(btnClose);

        View topSpacer = new View(this);
        topSpacer.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1f));
        topBar.addView(topSpacer);

        Button btnSwitch = makeCameraOverlayBtn("Alternar camera", "#D9111114", "#27272A");
        topBar.addView(btnSwitch);

        frame.addView(topBar);

        // Bottom dock flutuante
        LinearLayout bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setPadding(dp(10), dp(8), dp(10), dp(8));
        bottomBar.setGravity(Gravity.CENTER);
        GradientDrawable camDockBg = new GradientDrawable();
        camDockBg.setColor(Color.parseColor("#E6111114"));
        camDockBg.setCornerRadius(dp(999));
        camDockBg.setStroke(dp(1), Color.parseColor("#27272A"));
        bottomBar.setBackground(camDockBg);
        FrameLayout.LayoutParams bottomBarLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bottomBarLp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        bottomBarLp.setMargins(0, 0, 0, dp(22));
        bottomBar.setLayoutParams(bottomBarLp);

        Button[] btnStreamRef = new Button[1];
        Button btnStream = makeCameraOverlayBtn("Transmitir Webcam", "#18181C", "#27272A");
        btnStreamRef[0] = btnStream;
        boolean[] streamingRef = {false};

        btnStream.setOnClickListener(v -> {
            streamingRef[0] = !streamingRef[0];
            isStreamingCamera = streamingRef[0];
            GradientDrawable stBg = new GradientDrawable();
            stBg.setCornerRadius(dp(999));
            if (streamingRef[0]) {
                stBg.setColor(Color.parseColor("#0B261C"));
                stBg.setStroke(dp(1), Color.parseColor("#10B981"));
                btnStreamRef[0].setTextColor(Color.parseColor("#34D399"));
                btnStreamRef[0].setText("Transmitindo");
            } else {
                stBg.setColor(Color.parseColor("#18181C"));
                stBg.setStroke(dp(1), Color.parseColor("#27272A"));
                btnStreamRef[0].setTextColor(Color.parseColor("#FAFAFA"));
                btnStreamRef[0].setText("Transmitir Webcam");
            }
            btnStreamRef[0].setBackground(stBg);
        });

        Button btnPhoto = makeCameraOverlayBtn("Foto -> Ctrl+V", "#2563EB", "#2563EB");
        btnPhoto.setOnClickListener(v -> captureAndSendCameraFrame());

        bottomBar.addView(btnStream);
        View bottomSpacer = new View(this);
        bottomSpacer.setLayoutParams(new LinearLayout.LayoutParams(dp(8), 1));
        bottomBar.addView(bottomSpacer);
        bottomBar.addView(btnPhoto);

        frame.addView(bottomBar);

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
            Toast.makeText(MainActivity.this, "Erro ao iniciar camera: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void captureAndSendCameraFrame() {
        if (camera == null) return;
        camera.takePicture(null, null, (data, cam) -> {
            if (data != null) {
                client.sendClipboardImage(data);
                runOnUiThread(() -> Toast.makeText(this, "Foto enviada para o Ctrl+V do PC.", Toast.LENGTH_SHORT).show());
            }
            cam.startPreview();
        });
    }

    private Button makeCameraOverlayBtn(String text, String fillHex, String strokeHex) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextColor(Color.parseColor("#FAFAFA"));
        b.setTextSize(12);
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        b.setStateListAnimator(null);
        b.setPadding(dp(16), dp(8), dp(16), dp(8));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor(fillHex));
        bg.setCornerRadius(dp(999));
        bg.setStroke(dp(1), Color.parseColor(strokeHex));
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
    // 7. MICROFONE SEM FIO - INTERFACE MINIMALISTA
    // ==========================================
    private void showMicDialog() {
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, PERM_AUDIO);
            return;
        }

        LinearLayout root = createDarkModalRoot("Microfone Sem Fio", "Transmissao de audio PCM em tempo real");
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        micDialog = createDarkModal(root);

        // Anel minimalista com icone vetorial de microfone
        FrameLayout micRing = new FrameLayout(this);
        micRing.setBackgroundResource(R.drawable.bg_mic_ring);
        LinearLayout.LayoutParams ringParams = new LinearLayout.LayoutParams(dp(96), dp(96));
        ringParams.setMargins(0, dp(10), 0, dp(16));
        ringParams.gravity = Gravity.CENTER_HORIZONTAL;
        micRing.setLayoutParams(ringParams);

        micIcon = new ImageView(this);
        micIcon.setImageResource(R.drawable.ic_mic_large);
        FrameLayout.LayoutParams iconLp = new FrameLayout.LayoutParams(dp(44), dp(44));
        iconLp.gravity = Gravity.CENTER;
        micIcon.setLayoutParams(iconLp);
        micRing.addView(micIcon);
        root.addView(micRing);

        // Indicador de nivel de audio
        micLevelBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        micLevelBar.setMax(100);
        micLevelBar.setProgress(0);
        micLevelBar.setProgressTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#10B981")));
        micLevelBar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#27272A")));
        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(6));
        barParams.setMargins(0, dp(4), 0, dp(14));
        micLevelBar.setLayoutParams(barParams);
        root.addView(micLevelBar);

        micStatusText = new TextView(this);
        micStatusText.setText("Pronto para transmitir");
        micStatusText.setTextColor(Color.parseColor("#A1A1AA"));
        micStatusText.setTextSize(13);
        micStatusText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        statusParams.setMargins(0, 0, 0, dp(18));
        micStatusText.setLayoutParams(statusParams);
        root.addView(micStatusText);

        micToggleButton = makeStyledModalBtn("Iniciar gravacao", "#0B261C", "#10B981", "#34D399");
        LinearLayout.LayoutParams micBtnParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        micToggleButton.setLayoutParams(micBtnParams);

        micToggleButton.setOnClickListener(v -> {
            if (isRecordingMic) {
                stopMicRecording();
            } else {
                startMicRecording();
            }
        });
        root.addView(micToggleButton);
        root.addView(makeModalCloseButton(micDialog));

        micDialog.setOnDismissListener(d -> {
            if (isRecordingMic) stopMicRecording();
            stopMicPulse();
        });

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
        micButton.setText("Microfone");
        micButton.setTextColor(Color.parseColor("#E4E4E7"));
    }

    private void updateMicDialogRecordingState(boolean recording) {
        if (recording) {
            micButton.setText("Gravando");
            micButton.setTextColor(Color.parseColor("#EF4444"));

            if (micToggleButton != null) {
                micToggleButton.setText("Parar gravacao");
                micToggleButton.setTextColor(Color.parseColor("#FCA5A5"));
                GradientDrawable bg = new GradientDrawable();
                bg.setColor(Color.parseColor("#241115"));
                bg.setCornerRadius(dp(12));
                bg.setStroke(dp(1), Color.parseColor("#EF4444"));
                micToggleButton.setBackground(bg);
            }
            if (micStatusText != null) {
                micStatusText.setText("Transmitindo audio para o PC...");
                micStatusText.setTextColor(Color.parseColor("#34D399"));
            }
            startMicPulse();
        } else {
            if (micToggleButton != null) {
                micToggleButton.setText("Iniciar gravacao");
                micToggleButton.setTextColor(Color.parseColor("#34D399"));
                GradientDrawable bg = new GradientDrawable();
                bg.setColor(Color.parseColor("#0B261C"));
                bg.setCornerRadius(dp(12));
                bg.setStroke(dp(1), Color.parseColor("#10B981"));
                micToggleButton.setBackground(bg);
            }
            if (micStatusText != null) {
                micStatusText.setText("Pronto para transmitir");
                micStatusText.setTextColor(Color.parseColor("#A1A1AA"));
            }
            if (micLevelBar != null) micLevelBar.setProgress(0);
            stopMicPulse();
        }
    }

    private void startMicPulse() {
        if (micIcon == null) return;
        stopMicPulse();
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(micIcon, "scaleX", 1f, 1.15f, 1f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(micIcon, "scaleY", 1f, 1.15f, 1f);
        micPulseAnimator = new AnimatorSet();
        micPulseAnimator.playTogether(scaleX, scaleY);
        micPulseAnimator.setDuration(600);
        Handler pulseHandler = new Handler(Looper.getMainLooper());
        Runnable pulseRunnable = new Runnable() {
            @Override
            public void run() {
                if (isRecordingMic && micIcon != null) {
                    micIcon.animate().scaleX(1.18f).scaleY(1.18f).setDuration(300)
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
        showMicDialog();
    }

    private void abrirWebcamStream() {
        showCameraStudioDialog();
    }

    // ==========================================
    // TOUCHPAD E EXPLORADOR REMOTO
    // ==========================================
    private void exibirTouchpadDialog() {
        LinearLayout root = createDarkModalRoot("Touchpad", "Arraste para mover • Toque para clicar • 2 dedos para rolar");
        Dialog dialog = createDarkModal(root);

        FrameLayout touchpadBox = new FrameLayout(this);
        touchpadBox.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(220)));
        touchpadBox.setBackgroundResource(R.drawable.bg_feed_container);

        TextView padHint = new TextView(this);
        padHint.setText("AREA DE TOQUE");
        padHint.setTextColor(Color.parseColor("#3F3F46"));
        padHint.setTextSize(11);
        padHint.setLetterSpacing(0.12f);
        FrameLayout.LayoutParams hintLp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hintLp.gravity = Gravity.CENTER;
        padHint.setLayoutParams(hintLp);
        touchpadBox.addView(padHint);

        final float[] lastPos = new float[2];
        final long[] downTime = new long[1];
        final boolean[] moved = new boolean[1];

        touchpadBox.setOnTouchListener((v, event) -> {
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
        root.addView(touchpadBox);

        LinearLayout clickRow = new LinearLayout(this);
        clickRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams crLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        crLp.setMargins(0, dp(10), 0, dp(8));
        clickRow.setLayoutParams(crLp);

        Button btnLeft = makeStyledModalBtn("Clique Esq.", "#18181C", "#27272A", "#FAFAFA");
        btnLeft.setOnClickListener(v -> {
            vibrarFeedback(30);
            client.sendCommand("MOUSE_CLICK", "LEFT");
        });

        Button btnRight = makeStyledModalBtn("Clique Dir.", "#18181C", "#27272A", "#FAFAFA");
        btnRight.setOnClickListener(v -> {
            vibrarFeedback(30);
            client.sendCommand("MOUSE_CLICK", "RIGHT");
        });

        LinearLayout.LayoutParams p1 = new LinearLayout.LayoutParams(0, dp(42), 1.0f);
        p1.setMargins(0, 0, dp(6), 0);
        btnLeft.setLayoutParams(p1);
        btnRight.setLayoutParams(new LinearLayout.LayoutParams(0, dp(42), 1.0f));

        clickRow.addView(btnLeft);
        clickRow.addView(btnRight);
        root.addView(clickRow);

        LinearLayout extraRow = new LinearLayout(this);
        extraRow.setOrientation(LinearLayout.HORIZONTAL);

        Button btnPrevSlide = makeStyledModalBtn("Anterior", "#141418", "#27272A", "#A1A1AA");
        btnPrevSlide.setTextSize(12);
        btnPrevSlide.setOnClickListener(v -> client.sendCommand("KEY_PRESS", "PAGE_UP"));

        Button btnNextSlide = makeStyledModalBtn("Proximo", "#141418", "#27272A", "#A1A1AA");
        btnNextSlide.setTextSize(12);
        btnNextSlide.setOnClickListener(v -> client.sendCommand("KEY_PRESS", "PAGE_DOWN"));

        Button btnKeyboard = makeStyledModalBtn("Digitar", "#141418", "#27272A", "#60A5FA");
        btnKeyboard.setTextSize(12);
        btnKeyboard.setOnClickListener(v -> exibirDialogDigitarTexto());

        LinearLayout.LayoutParams p2 = new LinearLayout.LayoutParams(0, dp(40), 1.0f);
        p2.setMargins(0, 0, dp(6), 0);
        btnPrevSlide.setLayoutParams(p2);
        btnNextSlide.setLayoutParams(p2);
        btnKeyboard.setLayoutParams(new LinearLayout.LayoutParams(0, dp(40), 1.0f));

        extraRow.addView(btnPrevSlide);
        extraRow.addView(btnNextSlide);
        extraRow.addView(btnKeyboard);
        root.addView(extraRow);

        root.addView(makeModalCloseButton(dialog));
        dialog.show();
    }

    private void exibirDialogDigitarTexto() {
        LinearLayout root = createDarkModalRoot("Digitar no PC", "O texto sera digitado na janela ativa");
        Dialog dialog = createDarkModal(root);

        EditText input = new EditText(this);
        input.setHint("Digite o texto...");
        input.setHintTextColor(Color.parseColor("#52525B"));
        input.setTextColor(Color.parseColor("#FAFAFA"));
        input.setTextSize(14);
        input.setBackgroundResource(R.drawable.bg_input);
        input.setPadding(dp(14), dp(12), dp(14), dp(12));
        root.addView(input);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.setMargins(0, dp(14), 0, 0);
        row.setLayoutParams(rlp);

        Button cancel = makeStyledModalBtn("Cancelar", "#18181C", "#27272A", "#A1A1AA");
        cancel.setOnClickListener(v -> dialog.dismiss());
        Button send = makeStyledModalBtn("Enviar", "#2563EB", "#2563EB", "#FFFFFF");
        send.setOnClickListener(v -> {
            String text = input.getText().toString();
            if (!text.isEmpty()) {
                client.sendCommand("KEY_TYPE", text);
                Toast.makeText(this, "Texto digitado no PC.", Toast.LENGTH_SHORT).show();
            }
            dialog.dismiss();
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(42), 1f);
        lp.setMargins(0, 0, dp(8), 0);
        cancel.setLayoutParams(lp);
        send.setLayoutParams(new LinearLayout.LayoutParams(0, dp(42), 1f));
        row.addView(cancel);
        row.addView(send);
        root.addView(row);

        dialog.show();
    }

    private void exibirDialogExplorador(String path, List<RemoteDirItem> items) {
        String displayPath = path.equals("ROOT") ? "Unidades do Computador" : path;
        LinearLayout root = createDarkModalRoot("Pastas do PC", displayPath);
        Dialog dialog = createDarkModal(root);

        if (!"ROOT".equals(path)) {
            Button btnUp = makeStyledModalBtn("Voltar pasta anterior", "#18181C", "#27272A", "#93C5FD");
            btnUp.setTextSize(12);
            LinearLayout.LayoutParams ulp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(38));
            ulp.setMargins(0, 0, 0, dp(10));
            btnUp.setLayoutParams(ulp);
            btnUp.setOnClickListener(v -> {
                File cur = new File(path);
                File parent = cur.getParentFile();
                client.sendCommand("BROWSE_DIR", parent != null ? parent.getAbsolutePath() : "ROOT");
                dialog.dismiss();
            });
            root.addView(btnUp);
        }

        ListView listView = new ListView(this);
        listView.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(320)));
        listView.setDivider(new android.graphics.drawable.ColorDrawable(Color.parseColor("#1F1F23")));
        listView.setDividerHeight(1);

        List<String> labels = new ArrayList<>();
        for (RemoteDirItem it : items) {
            String prefix = it.isDir ? "[Pasta]  " : "";
            String suffix = it.isDir ? "" : "  (" + formatFileSize(it.size) + ")";
            labels.add(prefix + it.name + suffix);
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, labels) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView tv = (TextView) super.getView(position, convertView, parent);
                RemoteDirItem item = items.get(position);
                tv.setTextColor(item.isDir ? Color.parseColor("#93C5FD") : Color.parseColor("#FAFAFA"));
                tv.setTextSize(13);
                tv.setPadding(dp(8), dp(12), dp(8), dp(12));
                return tv;
            }
        };
        listView.setAdapter(adapter);

        listView.setOnItemClickListener((parent, view, position, id) -> {
            RemoteDirItem selected = items.get(position);
            if (selected.isDir) {
                client.sendCommand("BROWSE_DIR", selected.fullPath);
                dialog.dismiss();
            } else {
                LinearLayout confirmRoot = createDarkModalRoot("Baixar arquivo", selected.name + " (" + formatFileSize(selected.size) + ")");
                Dialog confirmDlg = createDarkModal(confirmRoot);
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                Button cBtn = makeStyledModalBtn("Cancelar", "#18181C", "#27272A", "#A1A1AA");
                cBtn.setOnClickListener(v -> confirmDlg.dismiss());
                Button dBtn = makeStyledModalBtn("Baixar", "#2563EB", "#2563EB", "#FFFFFF");
                dBtn.setOnClickListener(v -> {
                    client.sendCommand("REQUEST_FILE", selected.fullPath);
                    Toast.makeText(MainActivity.this, "Baixando " + selected.name + "...", Toast.LENGTH_SHORT).show();
                    confirmDlg.dismiss();
                });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(42), 1f);
                lp.setMargins(0, 0, dp(8), 0);
                cBtn.setLayoutParams(lp);
                dBtn.setLayoutParams(new LinearLayout.LayoutParams(0, dp(42), 1f));
                row.addView(cBtn);
                row.addView(dBtn);
                confirmRoot.addView(row);
                confirmDlg.show();
            }
        });

        root.addView(listView);
        root.addView(makeModalCloseButton(dialog));

        if (exploreDialog != null && exploreDialog.isShowing()) {
            exploreDialog.dismiss();
        }
        exploreDialog = dialog;
        dialog.show();
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
        card.setPadding(dp(14), dp(12), dp(14), dp(12));

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        if (isSent) {
            cardParams.setMargins(dp(44), 0, 0, 0);
        } else {
            cardParams.setMargins(0, 0, dp(44), 0);
        }
        card.setLayoutParams(cardParams);

        TextView header = new TextView(this);
        header.setText((isSent ? "Enviado" : "Recebido") + " • " + sender + " • " + getCurrentTime());
        header.setTextColor(isSent ? Color.parseColor("#BFDBFE") : Color.parseColor("#A1A1AA"));
        header.setTextSize(10);
        header.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
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
                    LinearLayout.LayoutParams ivParams = new LinearLayout.LayoutParams(dp(190), dp(140));
                    ivParams.setMargins(0, dp(8), 0, dp(6));
                    iv.setLayoutParams(ivParams);
                    card.addView(iv);
                }
            } catch (Exception ignored) {}
        }

        String tag = getFileTag(fileName);
        TextView fileInfo = new TextView(this);
        fileInfo.setText((tag.isEmpty() ? "" : "[" + tag + "] ") + fileName + "\n" + formatFileSize(size));
        fileInfo.setTextColor(Color.parseColor("#FAFAFA"));
        fileInfo.setTextSize(13);
        fileInfo.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        fileInfo.setPadding(0, dp(6), 0, dp(4));
        card.addView(fileInfo);

        if (fileUri != null) {
            TextView pathHint = new TextView(this);
            pathHint.setText("Salvo em Downloads/TransferApp");
            pathHint.setTextColor(Color.parseColor("#34D399"));
            pathHint.setTextSize(11);
            card.addView(pathHint);

            LinearLayout actions = new LinearLayout(this);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            actions.setPadding(0, dp(10), 0, 0);

            Button btnOpen = new Button(this);
            btnOpen.setText("Abrir");
            btnOpen.setAllCaps(false);
            btnOpen.setTextSize(12);
            btnOpen.setStateListAnimator(null);
            btnOpen.setBackgroundResource(R.drawable.bg_button_action);
            btnOpen.setTextColor(Color.WHITE);
            btnOpen.setOnClickListener(v -> abrirArquivo(fileUri, fileName));

            Button btnShare = new Button(this);
            btnShare.setText("Compartilhar");
            btnShare.setAllCaps(false);
            btnShare.setTextSize(12);
            btnShare.setStateListAnimator(null);
            btnShare.setBackgroundResource(R.drawable.bg_button_secondary);
            btnShare.setTextColor(Color.parseColor("#93C5FD"));
            btnShare.setOnClickListener(v -> compartilharArquivo(fileUri, fileName));

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(36), 1.0f);
            lp.setMargins(0, 0, dp(8), 0);
            btnOpen.setLayoutParams(lp);
            btnShare.setLayoutParams(new LinearLayout.LayoutParams(0, dp(36), 1.0f));

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
        return makeStyledModalBtn(text, "#18181C", "#27272A", "#E4E4E7");
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
        discoverButton.setText("Buscando na rede...");
        discoveredBanner.setVisibility(View.GONE);

        DiscoveryClient.discover(this, new DiscoveryClient.DiscoveryListener() {
            @Override
            public void onServerFound(String serverName, String host, int port) {
                runOnUiThread(() -> {
                    discoveredIp = host;
                    discoveredText.setText(serverName + " (" + host + ":" + port + ")");
                    discoveredBanner.setVisibility(View.VISIBLE);
                });
            }

            @Override
            public void onDiscoveryFinished() {
                runOnUiThread(() -> {
                    discoverButton.setEnabled(true);
                    discoverButton.setText("Buscar PC na rede");
                });
            }
        });
    }

    private void enviarMensagemTexto() {
        String texto = messageEditText.getText().toString().trim();
        if (texto.isEmpty()) return;

        client.sendText(texto);
        addTextMessage("Você", texto, true);
        messageEditText.setText("");
    }

    private void enviarClipboardLocal() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null && clipboard.hasPrimaryClip() && clipboard.getPrimaryClip().getItemCount() > 0) {
            CharSequence text = clipboard.getPrimaryClip().getItemAt(0).getText();
            if (text != null && text.length() > 0) {
                client.sendClipboard(text.toString());
                addClipboardCard("Você", text.toString());
                Toast.makeText(this, "Clipboard enviado ao PC.", Toast.LENGTH_SHORT).show();
                return;
            }
        }
        Toast.makeText(this, "Area de transferencia vazia.", Toast.LENGTH_SHORT).show();
    }

    private void copyToClipboard(String text) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            ClipData clip = ClipData.newPlainText("PCcell", text);
            clipboard.setPrimaryClip(clip);
        }
    }

    private void exibirDialogControleRemoto() {
        LinearLayout root = createDarkModalRoot("Midia e Navegador", "Controle de reproducao e abertura de links");
        Dialog dialog = createDarkModal(root);

        LinearLayout mediaRow = new LinearLayout(this);
        mediaRow.setOrientation(LinearLayout.HORIZONTAL);

        Button btnPlay = makeStyledModalBtn("Play", "#18181C", "#27272A", "#FAFAFA");
        Button btnVolDown = makeStyledModalBtn("Vol -", "#18181C", "#27272A", "#FAFAFA");
        Button btnVolUp = makeStyledModalBtn("Vol +", "#18181C", "#27272A", "#FAFAFA");
        Button btnMute = makeStyledModalBtn("Mudo", "#18181C", "#27272A", "#FCA5A5");

        btnPlay.setOnClickListener(v -> client.sendCommand("PLAY_PAUSE", ""));
        btnVolDown.setOnClickListener(v -> client.sendCommand("VOL_DOWN", ""));
        btnVolUp.setOnClickListener(v -> client.sendCommand("VOL_UP", ""));
        btnMute.setOnClickListener(v -> client.sendCommand("MUTE", ""));

        Button[] mediaBtns = {btnPlay, btnVolDown, btnVolUp, btnMute};
        for (int i = 0; i < mediaBtns.length; i++) {
            LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(0, dp(42), 1f);
            if (i < mediaBtns.length - 1) mp.setMargins(0, 0, dp(6), 0);
            mediaBtns[i].setLayoutParams(mp);
            mediaBtns[i].setTextSize(12);
            mediaRow.addView(mediaBtns[i]);
        }
        root.addView(mediaRow);

        TextView lblUrl = new TextView(this);
        lblUrl.setText("ABRIR LINK NO PC");
        lblUrl.setTextColor(Color.parseColor("#71717A"));
        lblUrl.setTextSize(10);
        lblUrl.setLetterSpacing(0.08f);
        lblUrl.setPadding(0, dp(16), 0, dp(8));
        root.addView(lblUrl);

        EditText urlInput = new EditText(this);
        urlInput.setHint("https://youtube.com");
        urlInput.setHintTextColor(Color.parseColor("#52525B"));
        urlInput.setTextColor(Color.parseColor("#FAFAFA"));
        urlInput.setTextSize(13);
        urlInput.setSingleLine(true);
        urlInput.setBackgroundResource(R.drawable.bg_input);
        urlInput.setPadding(dp(14), dp(10), dp(14), dp(10));
        LinearLayout.LayoutParams ulp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        ulp.setMargins(0, 0, 0, dp(10));
        urlInput.setLayoutParams(ulp);
        root.addView(urlInput);

        Button btnOpenUrl = makeStyledModalBtn("Abrir no navegador", "#2563EB", "#2563EB", "#FFFFFF");
        LinearLayout.LayoutParams obLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        btnOpenUrl.setLayoutParams(obLp);
        btnOpenUrl.setOnClickListener(v -> {
            String url = urlInput.getText().toString().trim();
            if (!url.isEmpty()) {
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    url = "https://" + url;
                }
                client.sendCommand("OPEN_URL", url);
                Toast.makeText(this, "Abrindo link no PC...", Toast.LENGTH_SHORT).show();
            }
        });
        root.addView(btnOpenUrl);
        root.addView(makeModalCloseButton(dialog));

        dialog.show();
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
                    Toast.makeText(this, "Foto enviada para o Ctrl+V do PC.", Toast.LENGTH_LONG).show();
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
            if (in == null) throw new IOException("Arquivo indisponivel.");
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
                    Toast.makeText(this, "Texto pronto para envio.", Toast.LENGTH_SHORT).show();
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
            if (uri == null) throw new IOException("Nao foi possivel criar entrada em Downloads.");
            lastSavedUri = uri;
            OutputStream out = getContentResolver().openOutputStream(uri);
            if (out == null) throw new IOException("Nao foi possivel gravar no arquivo.");
            return out;
        }

        File folder = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "TransferApp");
        if (!folder.exists() && !folder.mkdirs()) {
            folder = new File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "TransferApp");
            if (!folder.exists() && !folder.mkdirs()) {
                throw new IOException("Nao foi possivel criar a pasta de destino.");
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
        row.setPadding(0, dp(4), 0, dp(4));

        LinearLayout bubble = new LinearLayout(this);
        bubble.setOrientation(LinearLayout.VERTICAL);
        bubble.setBackgroundResource(isSent ? R.drawable.bg_bubble_sent : R.drawable.bg_bubble_received);
        bubble.setPadding(dp(14), dp(10), dp(14), dp(10));

        LinearLayout.LayoutParams bubbleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        if (isSent) {
            bubbleParams.setMargins(dp(56), 0, 0, 0);
        } else {
            bubbleParams.setMargins(0, 0, dp(56), 0);
        }
        bubble.setLayoutParams(bubbleParams);

        TextView senderView = new TextView(this);
        senderView.setText(sender + " • " + getCurrentTime());
        senderView.setTextColor(isSent ? Color.parseColor("#BFDBFE") : Color.parseColor("#A1A1AA"));
        senderView.setTextSize(10);
        bubble.addView(senderView);

        TextView contentView = new TextView(this);
        contentView.setText(text);
        contentView.setTextColor(Color.parseColor("#FAFAFA"));
        contentView.setTextSize(14);
        contentView.setPadding(0, dp(2), 0, 0);
        contentView.setTextIsSelectable(true);
        bubble.addView(contentView);

        if (text.startsWith("http://") || text.startsWith("https://")) {
            Button linkBtn = new Button(this);
            linkBtn.setText("Abrir link");
            linkBtn.setAllCaps(false);
            linkBtn.setTextSize(11);
            linkBtn.setStateListAnimator(null);
            linkBtn.setBackgroundResource(R.drawable.bg_button_secondary);
            linkBtn.setTextColor(Color.parseColor("#93C5FD"));
            LinearLayout.LayoutParams lbp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(32));
            lbp.setMargins(0, dp(6), 0, 0);
            linkBtn.setLayoutParams(lbp);
            linkBtn.setPadding(dp(12), 0, dp(12), 0);
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
        row.setPadding(0, dp(4), 0, dp(4));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(isSent ? R.drawable.bg_bubble_sent : R.drawable.bg_card);
        card.setPadding(dp(14), dp(10), dp(14), dp(10));

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        if (isSent) cardParams.setMargins(dp(44), 0, 0, 0);
        else cardParams.setMargins(0, 0, dp(44), 0);
        card.setLayoutParams(cardParams);

        TextView header = new TextView(this);
        header.setText("Clipboard • " + sender + " • " + getCurrentTime());
        header.setTextColor(isSent ? Color.parseColor("#BFDBFE") : Color.parseColor("#93C5FD"));
        header.setTextSize(10);
        card.addView(header);

        String preview = text.length() > 100 ? text.substring(0, 100) + "..." : text;
        TextView content = new TextView(this);
        content.setText(preview);
        content.setTextColor(Color.parseColor("#FAFAFA"));
        content.setTextSize(13);
        content.setPadding(0, dp(4), 0, dp(6));
        card.addView(content);

        Button copyBtn = new Button(this);
        copyBtn.setText("Copiar");
        copyBtn.setAllCaps(false);
        copyBtn.setTextSize(11);
        copyBtn.setStateListAnimator(null);
        copyBtn.setBackgroundResource(R.drawable.bg_button_secondary);
        copyBtn.setTextColor(Color.parseColor("#93C5FD"));
        copyBtn.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(32)));
        copyBtn.setPadding(dp(12), 0, dp(12), 0);
        copyBtn.setOnClickListener(v -> {
            copyToClipboard(text);
            Toast.makeText(this, "Copiado.", Toast.LENGTH_SHORT).show();
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

    private String getFileTag(String name) {
        String lower = name.toLowerCase();
        if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".webp") || lower.endsWith(".gif")) return "IMG";
        if (lower.endsWith(".mp4") || lower.endsWith(".mkv") || lower.endsWith(".avi") || lower.endsWith(".mov")) return "VID";
        if (lower.endsWith(".mp3") || lower.endsWith(".wav") || lower.endsWith(".aac") || lower.endsWith(".flac")) return "AUD";
        if (lower.endsWith(".pdf") || lower.endsWith(".doc") || lower.endsWith(".docx") || lower.endsWith(".txt")) return "DOC";
        if (lower.endsWith(".zip") || lower.endsWith(".rar") || lower.endsWith(".7z") || lower.endsWith(".tar")) return "ZIP";
        if (lower.endsWith(".apk")) return "APK";
        return "ARQ";
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
