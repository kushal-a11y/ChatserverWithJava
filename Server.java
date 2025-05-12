import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

class Server {

    public static final String RESET = "\u001B[0m";
    public static final String BLUE = "\u001B[34m";
    public static final String GREEN = "\u001B[32m";
    public static final String CYAN = "\u001B[36m";

    static final String CHAT_HISTORY = "history.txt";
    static HashMap<String, String> users = new HashMap<>();
    static CopyOnWriteArrayList<ConcurrentUser> list = new CopyOnWriteArrayList<>();

    public static void main(String[] args) throws Exception {
        ServerSocket welcome = new ServerSocket(4545);
        users.put("192.168.0.106", "Kushal");
        users.put("192.168.0.105", "Kousik");

        System.out.println("Server Running on port 4545...");

        while (true) {
            Socket client = welcome.accept();
            new Thread(new ClientHandler(client)).start();
        }
    }

    static class ConcurrentUser {
        String username;
        DataOutputStream out;

        ConcurrentUser(String username, DataOutputStream out) {
            this.username = username;
            this.out = out;
        }
    }

    static class ClientHandler implements Runnable {
        private Socket client;

        public ClientHandler(Socket client) {
            this.client = client;
        }

        public void run() {
            String clientIP = client.getInetAddress().getHostAddress();
            String username = users.getOrDefault(clientIP, "Unknown User");

            try {
                System.out.println(username + " connected.");

                BufferedReader inFromClient = new BufferedReader(new InputStreamReader(client.getInputStream()));
                DataOutputStream toClient = new DataOutputStream(client.getOutputStream());

                list.add(new ConcurrentUser(username, toClient));

                File historyFile = new File(CHAT_HISTORY);
                if (historyFile.exists()) {
                    BufferedReader history = new BufferedReader(new FileReader(historyFile));
                    String line;
                    while ((line = history.readLine()) != null) {
                        toClient.writeBytes(line + '\n');
                    }
                    history.close();
                    toClient.writeBytes("End of History\n");
                }

                BufferedWriter writer = new BufferedWriter(new FileWriter(CHAT_HISTORY, true));

                String line;
                while ((line = inFromClient.readLine()) != null) {
                    String formatted = BLUE + username + " : " + RESET + GREEN + line + RESET;

                    // Write to history.txt safely
                    synchronized (writer) {
                        writer.write(formatted + '\n');
                        writer.flush();
                    }

                    // Broadcast to all other users
                    for (ConcurrentUser con : list) {
                        if (!con.username.equals(username)) {
                            try {
                                synchronized (con.out) {
                                    con.out.writeBytes(formatted + "\n");
                                    con.out.flush();
                                }
                            } catch (IOException e) {
                                System.out.println("Failed to send to " + con.username + ". Removing...");
                                list.remove(con);
                                try {
                                    con.out.close();
                                } catch (IOException ex) {
                                    // ignore
                                }
                            }
                        }
                    }
                }

                writer.close();
                client.close();
                list.removeIf(c -> c.username.equals(username));
                System.out.println(username + " disconnected.");

            } catch (Exception e) {
                System.out.println("Error handling client " + username + ": " + e.getMessage());
                list.removeIf(c -> c.username.equals(username));
            }
        }
    }
}
