# PCcell

Integracao e controle remoto entre computador (Windows) e smartphone (Android) via rede local ou cabo USB.

Projeto desenvolvido com assistencia de Inteligencia Artificial.

---

## Funcionalidades

- Transmissao de tela em tempo real (60 FPS) com controle de mouse por toque e botoes de clique esquerdo e direito.
- Gamepad virtual para uso na horizontal com D-Pad, botoes ABXY, L1/R1/L2/R2 e L3/R3.
- Transferencia de arquivos bidirecional e explorador de pastas do PC no celular.
- Sincronizacao de area de transferencia (texto e fotos direto para Ctrl+V).
- Transmissao de camera do celular como webcam para o computador.
- Microfone sem fio transmitindo audio para o PC em tempo real.
- Atalhos de sistema (minimizar tudo, mutar som, abrir gerenciador de tarefas, navegador, etc.).
- Monitoramento de hardware (CPU, RAM e Disco) e comandos de desligamento/reinicio.

---

## Arquitetura

- PC (Java 21): Servidor TCP para comunicacao e controle via Robot, alem de servidor UDP para descoberta automatica na rede.
- Celular (Android Java): Cliente TCP com interface adaptada para rotacao de tela sem queda de conexao.

Porta padrao de comunicacao: 5050 (TCP/UDP).

---

## Como Executar

### Servidor PC
```bash
javac --release 21 -d pccell-PC/out/production/project pccell-PC/project/src/*.java
java -cp pccell-PC/out/production/project Main
```

### Aplicativo Android
```bash
cd pccell-cell
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Para conexao via cabo USB (opcional):
adb reverse tcp:5050 tcp:5050
```

---

## Licenca

MIT
