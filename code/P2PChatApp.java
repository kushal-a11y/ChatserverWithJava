package code;
// P2PChatApp.java
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;

public class P2PChatApp extends JFrame {
    private JTextPane chatArea = new JTextPane();
    private JTextField inputField = new JTextField();
    private JButton sendButton = new JButton();  // Fixed here
    private DefaultStyledDocument doc = new DefaultStyledDocument();

    private final Set<Socket> connections = ConcurrentHashMap.newKeySet();
    private final Map<Socket, String> userNames = new ConcurrentHashMap<>();
    private final Map<String, Socket> nameToSocket = new ConcurrentHashMap<>();

    private ServerSocket serverSocket;
    private String username;
    private int listenPort;
    private final List<String> chatHistory = Collections.synchronizedList(new ArrayList<>());

    private String roomCode;
    private boolean isInitiator;
    private DatagramSocket udpSocket;
    private static final int UDP_PORT = 9999;

    public P2PChatApp(String username, int listenPort, boolean isInitiator, String roomCode) {
        this.username = username;
        this.listenPort = listenPort;
        this.isInitiator = isInitiator;
        this.roomCode = roomCode;

        setTitle("Room: " + roomCode + " | User: " + username);
        setSize(600, 450);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        initTopBar();
        initUI();

        startServer(listenPort);

        try {
            udpSocket = new DatagramSocket(UDP_PORT);
            udpSocket.setBroadcast(true);
        } catch (Exception e) {
            appendMessage("Failed to start UDP socket for discovery", Color.RED);
        }

        if (isInitiator) startRoomBroadcast();
        else discoverAndJoinRoom();

        setVisible(true);
    }

    private void initTopBar() {
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        topPanel.setBackground(new Color(50, 50, 50));

        JLabel titleLabel = new JLabel("Room: " + roomCode + " | User: " + username);
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));

        JButton leaveButton = new JButton("Leave Chat");
        leaveButton.setForeground(Color.WHITE);
        leaveButton.setBackground(Color.RED);
        leaveButton.setFocusPainted(false);
        leaveButton.setFont(new Font("Segoe UI", Font.BOLD, 12));
        leaveButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        leaveButton.setPreferredSize(new Dimension(120, 30));

        leaveButton.addActionListener(e -> {
            try {
                for (Socket s : connections) s.close();
                if (serverSocket != null && !serverSocket.isClosed()) serverSocket.close();
                if (udpSocket != null && !udpSocket.isClosed()) udpSocket.close();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
            dispose();
            System.exit(0); // Clean exit
        });

        topPanel.add(titleLabel, BorderLayout.WEST);
        topPanel.add(leaveButton, BorderLayout.EAST);

        getContentPane().add(topPanel, BorderLayout.NORTH);
    }

    private void initUI() {
        chatArea.setEditable(false);
        chatArea.setDocument(doc);
        chatArea.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        chatArea.setBackground(new Color(245, 245, 245));
        JScrollPane scrollPane = new JScrollPane(chatArea);

        JPanel inputPanel = new JPanel(new BorderLayout(5, 5));
        inputPanel.setBorder(new EmptyBorder(8, 8, 8, 8));
        inputPanel.setBackground(Color.WHITE);

        inputField.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        inputField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(200, 200, 200), 2, true),
            BorderFactory.createEmptyBorder(6, 10, 6, 10)
        ));

        sendButton.setPreferredSize(new Dimension(50, 38));
        sendButton.setBackground(new Color(37, 211, 102));
        sendButton.setFont(new Font("Segoe UI Symbol", Font.BOLD, 18)); // Fix here
        sendButton.setForeground(Color.WHITE);
        sendButton.setToolTipText("➤");

        inputPanel.add(inputField, BorderLayout.CENTER);
        inputPanel.add(sendButton, BorderLayout.EAST);

        getContentPane().add(scrollPane, BorderLayout.CENTER);
        getContentPane().add(inputPanel, BorderLayout.SOUTH);

        ActionListener sendAction = e -> {
            String msg = inputField.getText().trim();
            if (!msg.isEmpty()) {
                String formatted = username + ": " + msg;
                appendMessage(formatted, new Color(37, 211, 102));
                chatHistory.add(formatted);
                broadcast("MSG:" + formatted);
                inputField.setText("");
            }
        };
        sendButton.addActionListener(sendAction);
        inputField.addActionListener(sendAction);
    }

    private void startServer(int port) {
        new Thread(() -> {
            try {
                serverSocket = new ServerSocket(port);
                appendMessage("Listening on TCP port " + port, Color.GRAY);
                while (!serverSocket.isClosed()) {
                    Socket client = serverSocket.accept();
                    connections.add(client);
                    userNames.put(client, "Unknown");
                    nameToSocket.put("Unknown", client);
                    new Thread(new Receiver(client)).start();
                }
            } catch (IOException e) {
                if (!serverSocket.isClosed())
                    appendMessage("Server error: " + e.getMessage(), Color.RED);
            }
        }).start();
    }

    private void startRoomBroadcast() {
        new Thread(() -> {
            try {
                InetAddress broadcastAddr = InetAddress.getByName("255.255.255.255");
                while (true) {
                    String msg = "ROOM:" + roomCode + ":" + InetAddress.getLocalHost().getHostAddress() + ":" + listenPort;
                    DatagramPacket packet = new DatagramPacket(msg.getBytes(), msg.length(), broadcastAddr, UDP_PORT);
                    udpSocket.send(packet);
                    Thread.sleep(2000);
                }
            } catch (Exception e) {
                appendMessage("Error broadcasting room: " + e.getMessage(), Color.RED);
            }
        }).start();

        new Thread(() -> {
            byte[] buffer = new byte[256];
            while (true) {
                try {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    udpSocket.receive(packet);
                    String received = new String(packet.getData(), 0, packet.getLength());
                    if (received.startsWith("DISCOVER:") && received.substring(9).equals(roomCode)) {
                        String reply = "HERE:" + roomCode + ":" + InetAddress.getLocalHost().getHostAddress() + ":" + listenPort;
                        DatagramPacket response = new DatagramPacket(reply.getBytes(), reply.length(), packet.getAddress(), packet.getPort());
                        udpSocket.send(response);
                    }
                } catch (IOException e) {
                    break;
                }
            }
        }).start();
    }

    private void discoverAndJoinRoom() {
        new Thread(() -> {
            try {
                String msg = "DISCOVER:" + roomCode;
                DatagramSocket socket = new DatagramSocket();
                socket.setBroadcast(true);
                DatagramPacket packet = new DatagramPacket(msg.getBytes(), msg.length(), InetAddress.getByName("255.255.255.255"), UDP_PORT);
                socket.send(packet);

                socket.setSoTimeout(5000);
                byte[] buffer = new byte[256];
                DatagramPacket response = new DatagramPacket(buffer, buffer.length);
                socket.receive(response);

                String reply = new String(response.getData(), 0, response.getLength());
                if (reply.startsWith("HERE:")) {
                    String[] parts = reply.split(":");
                    String ip = parts[2];
                    int port = Integer.parseInt(parts[3]);

                    Socket tcpSocket = new Socket(ip, port);
                    connections.add(tcpSocket);
                    userNames.put(tcpSocket, "Unknown");
                    nameToSocket.put("Unknown", tcpSocket);
                    new Thread(new Receiver(tcpSocket)).start();

                    sendMessage(tcpSocket, "JOIN:" + username);
                    sendMessage(tcpSocket, "REQHIST:");
                }
                socket.close();
            } catch (Exception e) {
                appendMessage("Room discovery failed: " + e.getMessage(), Color.RED);
            }
        }).start();
    }

    private void broadcast(String msg) {
        synchronized (connections) {
            for (Socket s : connections) sendMessage(s, msg);
        }
    }

    private void sendMessage(Socket s, String msg) {
        try {
            PrintWriter out = new PrintWriter(s.getOutputStream(), true);
            out.println(msg);
        } catch (IOException e) {
            connections.remove(s);
            String name = userNames.get(s);
            nameToSocket.remove(name);
            userNames.remove(s);
        }
    }

    private void appendMessage(String message, Color color) {
        SwingUtilities.invokeLater(() -> {
            try {
                StyleContext sc = StyleContext.getDefaultStyleContext();
                AttributeSet aset = sc.addAttribute(SimpleAttributeSet.EMPTY, StyleConstants.Foreground, color);
                aset = sc.addAttribute(aset, StyleConstants.FontFamily, "Segoe UI");
                aset = sc.addAttribute(aset, StyleConstants.FontSize, 15);
                aset = sc.addAttribute(aset, StyleConstants.Bold, true);
                doc.insertString(doc.getLength(), message + "\n", aset);
                chatArea.setCaretPosition(doc.getLength());
            } catch (BadLocationException ex) {
                ex.printStackTrace();
            }
        });
    }

    class Receiver implements Runnable {
        private final Socket socket;

        Receiver(Socket socket) {
            this.socket = socket;
        }

        public void run() {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
                String line;
                while ((line = in.readLine()) != null) {
                    if (line.startsWith("JOIN:")) {
                        String name = line.substring(5);
                        if (!nameToSocket.containsKey(name)) {
                            userNames.put(socket, name);
                            nameToSocket.put(name, socket);
                            appendMessage(">> " + name + " joined", Color.MAGENTA);
                        } else {
                            sendMessage(socket, "ERROR:Name taken. Disconnecting.");
                            socket.close();
                        }
                    } else if (line.startsWith("MSG:")) {
                        String msg = line.substring(4);
                        chatHistory.add(msg);
                        appendMessage(msg, Color.BLACK);
                        for (Socket s : connections)
                            if (!s.equals(socket)) sendMessage(s, line);
                    } else if (line.startsWith("REQHIST:")) {
                        for (String msg : chatHistory)
                            sendMessage(socket, "MSG:" + msg);
                    }
                }
            } catch (IOException e) {
                String name = userNames.get(socket);
                nameToSocket.remove(name);
                userNames.remove(socket);
                connections.remove(socket);
                appendMessage("Connection closed with " + (name != null ? name : "Unknown"), Color.RED);
            }
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            String[] options = {"Create Room", "Join Room"};
            int choice = JOptionPane.showOptionDialog(null, "Choose Mode", "P2P Chat Room",
                    JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE, null, options, options[0]);

            String username = JOptionPane.showInputDialog("Enter your username:");
            if (username == null || username.trim().isEmpty()) {
                JOptionPane.showMessageDialog(null, "Username required!");
                System.exit(0);
            }

            int port;
            try {
                port = Integer.parseInt(JOptionPane.showInputDialog("Enter TCP port to listen on:"));
            } catch (Exception e) {
                JOptionPane.showMessageDialog(null, "Invalid port!");
                return;
            }

            String roomCode = JOptionPane.showInputDialog("Enter 6-digit Room Code:");
            if (roomCode == null || roomCode.length() != 6) {
                JOptionPane.showMessageDialog(null, "Invalid room code! Must be 6 characters.");
                System.exit(0);
            }

            new P2PChatApp(username.trim(), port, choice == 0, roomCode.toUpperCase());
        });
    }
}
