🗨️ Java Socket-Based Multi-Client Chat Server
This project is a multithreaded TCP socket-based chat server built in Java 1.8 that supports multiple clients, concurrent messaging, and persistent chat history using file I/O. It features colored messages in the terminal using ANSI escape codes or Jansi support.

📌 Features
✅ Supports multiple concurrent clients via threading

✅ Uses CopyOnWriteArrayList for safe concurrent access to client connections

✅ Maintains chat history using a shared history.txt file

✅ Sends the full chat history to newly connected users

✅ Broadcasts messages to all users except the sender

✅ Basic color-coded output for readability

✅ Clean disconnection and automatic user list cleanup

🛠️ Technologies Used
Java 1.8

Socket Programming (java.net)

File I/O (BufferedReader, BufferedWriter)

Threading (Runnable, Thread)

Concurrent Collections (CopyOnWriteArrayList)

Optional: Jansi for Windows-safe terminal colors

📂 File Structure
arduino
Copy code
├── Server.java         // The multithreaded server
├── Client.java         // Client-side application
├── history.txt         // Persistent chat log
🚀 How to Run
🔹 Server
bash
Copy code
javac Server.java
java Server
🔹 Client (Run in multiple terminals)
bash
Copy code
javac Client.java
java Client
Ensure all clients connect to the correct IP and port (e.g., localhost:4545 or LAN IP)

💬 How It Works
Connection: Each client connection is handled in a new thread.

Broadcasting: Messages are broadcast to all clients except the sender.

Chat History: Maintained in history.txt, and sent to each client on connect.

Thread Safety: Critical sections (like file writing and output stream writing) are synchronized to avoid race conditions.

Client Identification: Based on IP address mapping (manually configured).

⚠️ Known Limitations
Messages sent from multiple clients at the exact same time may still cause slight race issues if not properly synchronized.

IP-based user identification fails if multiple users are behind the same NAT (e.g., same Wi-Fi router).

Color output may not work as expected on Windows unless Jansi is used.

No GUI support or media/file sending (text-only chat).

🔒 Future Improvements
Add GUI using Swing or JavaFX

Implement image/media sharing

Improve user authentication

Support emojis or markdown-style formatting

Use a better protocol (like JSON over sockets) for structured messages

👨‍💻 Author
Created by Kushal Das
Feel free to fork and improve!

