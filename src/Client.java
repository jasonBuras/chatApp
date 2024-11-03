import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;
import java.util.HashSet;

public class Client {
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private String username;

    private JFrame frame;
    private JTextArea chatArea;
    private JTextField messageField;
    private JButton sendButton;
    private JTextArea usersArea;
    private JLabel currentlyConnected;

    //NEW
    private GameWindow gameWindow;

    //Time: Debated on whether to handle this client or server side
    //Ultimately decided it was best to leave it on the client side so the time would be local.
    private static LocalDateTime now;
    private static DateTimeFormatter dtf;
    //private HashSet<String> usernames = new HashSet();

    public Client(String serverAddress, int serverPort) {
        try {
            socket = new Socket(serverAddress, serverPort);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            setupGUI();
            /*
            The server asks the user for input. The user types the username in the following format "username = ComNet"
            message on the terminal. If the user doesn’t provide a username, the server doesn’t accept the user’s messages.
             */
            boolean validName = false;
            while(!validName) {
                username = JOptionPane.showInputDialog(frame, "Enter your username (1-16 characters):");
                if(username.length() > 0 && username.length() <= 16){
                    validName = true;
                    out.println(username);
                    JOptionPane.showMessageDialog(null, "Welcome, " + username + "!");
                }else if (username.length() == 0){
                    JOptionPane.showMessageDialog(null, "Username cannot be blank.");
                }else if (username.length() > 16) {
                    JOptionPane.showMessageDialog(null, "Username cannot exceed 16 characters");
                }
            }
            frame.setTitle("ChatApp - " + username);
            // Read messages from the server in a separate thread
            new Thread(new ServerListener()).start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Setup the GUI for the client
    private void setupGUI() {
        frame = new JFrame("ChatApp");
        chatArea = new JTextArea(20, 50);
        chatArea.setEditable(false);
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);

        messageField = new JTextField(40);
        sendButton = new JButton("Send");
        currentlyConnected = new JLabel();
        currentlyConnected.setText("Currently Connected");
        currentlyConnected.setHorizontalAlignment(SwingConstants.RIGHT);

        usersArea = new JTextArea(20,15);
        usersArea.setEditable(false);

        //https://docs.oracle.com/javase/8/docs/api/?java/awt/BorderLayout.html
        frame.setLayout(new BorderLayout());
        frame.add(new JScrollPane(chatArea), BorderLayout.CENTER);
        frame.add(currentlyConnected, BorderLayout.NORTH);

        frame.add(new JScrollPane(usersArea), BorderLayout.EAST);

        JPanel panel = new JPanel();
        panel.add(messageField);
        panel.add(sendButton);
        frame.add(panel, BorderLayout.SOUTH);

        frame.pack();
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        // Sends the message when "Send" button is clicked
        sendButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                sendMessage();
            }
        });

        // Sends the message when the Enter key is pressed
        messageField.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                sendMessage();
            }
        });
    }

    // Send message to the server
    private void sendMessage() {
        String message = messageField.getText();
        if (!message.trim().isEmpty()) {
            if(message.startsWith("/challenge") || message.equals("/y") || message.equals("/n")){
                out.println(message);
            }else {
                now = LocalDateTime.now();
                out.println(message);
                chatArea.append("[" + dtf.format(now) + "] You: " + message + "\n");
            }
            messageField.setText("");
        }
    }

    public void sendMessage(String message) {
        if (!message.trim().isEmpty()) {
            if(message.startsWith("/challenge") || message.equals("/y") || message.equals("/n")){
                out.println(message);
            }else {
                now = LocalDateTime.now();
                out.println(message);
                chatArea.append("[" + dtf.format(now) + "] You: " + message + "\n");
            }
            messageField.setText("");
        }
    }

    public void sendGuessToServer(String guess){
        out.println("GUESS:" + guess);
    }

    // Listen to messages from the server
    private class ServerListener implements Runnable {
        @Override
        public void run() {
            try {
                String message;
                while ((message = in.readLine()) != null) {
                    if (message.startsWith("GAME_START")) {
                        openGameWindow();
                    }else if(message.startsWith("ANSWER:")){
                        String answer = message.substring(7);
                        if (gameWindow != null){
                            gameWindow.setAnswer(answer);
                        }
                    }else if (message.startsWith("GUESS_FEEDBACK:")) {
                        String[] parts = message.substring(15).split(":"); // Feedback string like "GYYRR"
                        String guess = parts[0];
                        String feedback = parts[1];
                        if (gameWindow != null) {
                            gameWindow.displayFeedback(guess, feedback);
                        }
                    }else if (message.equals("INVALID_WORD")) {
                        if (gameWindow != null) {
                            gameWindow.showPopup("Invalid word. Try again.");
                        }
                    }else if (message.startsWith("WIN")) {
                        gameWindow.endGame(true);
                    }else if (message.startsWith("LOSE")) {
                        gameWindow.endGame(false);
                    }else if (message.startsWith("STALEMATE")) {
                        gameWindow.endGame(false); // End the game with stalemate handling
                        JOptionPane.showMessageDialog(frame, "Game ended in a stalemate. " + message.substring(9), "Stalemate", JOptionPane.INFORMATION_MESSAGE);
                    }else if (message.startsWith("TURN")) {
                        JOptionPane.showMessageDialog(frame, "Your Turn!");
                    }else if (message.startsWith("WAITING")) {
                        JOptionPane.showMessageDialog(frame, "Waiting for opponent's turn...");
                    }else if (message.startsWith("GAME_OVER")) {
                        JOptionPane.showMessageDialog(frame, "Game Over");
                        closeGameWindow();
                    } else if (message.startsWith("USERS:")) {
                        updateUserList(message.substring(6));
                    } else {
                        now = LocalDateTime.now();
                        chatArea.append("[" + dtf.format(now) + "] " + message + "\n");
                    }
                }
            } catch (IOException e) {
                JOptionPane.showMessageDialog(frame, "Disconnected from server.", "Connection Error", JOptionPane.ERROR_MESSAGE);
                System.exit(0);
            }
        }
    }

    private void openGameWindow(){
        gameWindow = new GameWindow(username, this);
    }

    private void closeGameWindow() {
        if(gameWindow != null){
            gameWindow.dispose();
            gameWindow = null;
        }
    }
    private void updateUserList(String userList) {
        System.out.println("Updating user list: " + userList);
        usersArea.setText("");  // Clear the current list
        String[] users = userList.split(",");
        for (String user : users) {
            if (user.equals(username)) {
                usersArea.append(user + " (You)\n");
            } else {
                usersArea.append(user + "\n");
            }
        }
    }

    public static void main(String[] args) {
        //The client program is started (server IP and port are provided on the command line).
        System.out.println("Server Address: " + args[0]);
        String serverAddress = args[0];
        System.out.println("Port: " + args[1]);
        int serverPort = Integer.parseInt(args[1]);
        new Client(serverAddress, serverPort); // The client connects to the server.
        dtf = DateTimeFormatter.ofPattern("HH:mm:ss");
        now = LocalDateTime.now();
        System.out.println("Current time: " + dtf.format(now));
    }

}
