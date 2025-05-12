import java.io.*;
import java.net.*;

class Client {

    public static final String RESET = "\u001B[0m";
    public static final String BLUE = "\u001B[34m";
    public static final String GREEN = "\u001B[32m";
    public static final String CYAN = "\u001B[36m";

    public static void main(String[] args) throws Exception {
        Socket client = new Socket("192.168.0.106", 4545);

        BufferedReader userInput = new BufferedReader(new InputStreamReader(System.in));
        BufferedReader inFromServer = new BufferedReader(new InputStreamReader(client.getInputStream()));
        DataOutputStream toServer = new DataOutputStream(client.getOutputStream());

        new Thread(() -> {
            try {
		boolean isLoad = false;
                String line;
		while ((line = inFromServer.readLine()) != null) {
    			if (line.trim().equals("End of History")) {
        			isLoad = true;
        			System.out.print(GREEN + "you: " + RESET);
        			continue;
    			}
    			System.out.println(line);
    			if (isLoad) {
       				System.out.print(GREEN + "you: " + RESET);
    			}
		}
            } catch (Exception e) {
                System.out.println("Server closed connection.");
            }
        }).start();

        while (true) { 
            String msg = userInput.readLine();
            if (msg != null && !msg.trim().isEmpty()) {
                toServer.writeBytes(msg + "\n");
                toServer.flush();
            }
        }
    }
}
