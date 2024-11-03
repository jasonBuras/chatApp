import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Server {
    private static Set<ClientHandler> clientHandlers = new HashSet<>();
    private static HashSet<String> usernames = new HashSet<>();
    private static Set<String> answerWords = new HashSet<>();
    private static Set<String> allowedWords = new HashSet<>();
    private static Map<String,String> activeGames = new HashMap<>();
    private static Map<String, Integer> playerGuesses = new ConcurrentHashMap<>();
    private static final int MAX_GUESSES = 5;

    static {
        loadWordLists("wordlist.txt", "allowed.txt");
    }

    public static void main(String[] args) throws IOException {
        //The server is first started on a known port.
        int portNumber = Integer.parseInt(args[0]);
        ServerSocket serverSocket = new ServerSocket(portNumber);
        System.out.println("Port: " + portNumber);
        System.out.println("Server started. Waiting for clients...");

        while (true) {
            Socket clientSocket = serverSocket.accept(); //The client connects to the server.
            System.out.println("New client connected: " + clientSocket);
            ClientHandler clientHandler = new ClientHandler(clientSocket);
            clientHandlers.add(clientHandler);
            Thread clientThread = new Thread(clientHandler);
            clientThread.start();
        }
    }

    private static void loadWordLists(String answerFile, String allowedFile){
        try (BufferedReader answerReader = new BufferedReader(new InputStreamReader(Server.class.getResourceAsStream(answerFile)));
             BufferedReader allowedReader = new BufferedReader(new InputStreamReader(Server.class.getResourceAsStream(allowedFile)))) {

            String line;
            while((line = answerReader.readLine()) != null){
                answerWords.add(line.toUpperCase());
            }
            while((line = allowedReader.readLine()) != null){
                allowedWords.add(line.toUpperCase());
            }
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static String selectRandomWord() {
        List<String> answers = new ArrayList<>(answerWords);
        return answers.get(new Random().nextInt(answers.size()));
    }

    public static void startGame(ClientHandler player1, ClientHandler player2) {
        String answer = selectRandomWord();
        activeGames.put(player1.getUsername(), answer);
        activeGames.put(player2.getUsername(), answer);
        playerGuesses.put(player1.getUsername(), 0);
        playerGuesses.put(player2.getUsername(), 0);


        player1.sendMessage("GAME_START");
        player2.sendMessage("GAME_START");
        player1.sendMessage("ANSWER:" + answer); // Send the answer to initialize client UI
        player2.sendMessage("ANSWER:" + answer);

        broadcastMessage(player1.getUsername() + " and " + player2.getUsername() + " have started a game of WordWhiz against each other. To challenge a user, type \"/challenge (username)\".", null);
    }

    public static String validateGuess(String guess) {
        if (answerWords.contains(guess) || allowedWords.contains(guess)) {
            return "VALID";
        } else {
            return "INVALID";
        }
    }
    public static String generateFeedback(String answer, String guess) {
        StringBuilder feedback = new StringBuilder();
        for (int i = 0; i < guess.length(); i++) {
            char guessedLetter = guess.charAt(i);
            if (guessedLetter == answer.charAt(i)) {
                feedback.append("G");
            } else if (answer.contains(Character.toString(guessedLetter))) {
                feedback.append("Y");
            } else {
                feedback.append("R");
            }
        }
        return feedback.toString();
    }

    public static void handleGuess(ClientHandler player, String guessedWord) {
        String answer = activeGames.get(player.getUsername());
        if (answer == null) {
            player.sendMessage("SERVER: No active game.");
            return;
        }

        String validation = validateGuess(guessedWord);
        if (validation.equals("INVALID")) {
            player.sendMessage("INVALID_WORD");
            return;
        }

        int currentGuesses = playerGuesses.getOrDefault(player.getUsername(), 0) + 1;
        playerGuesses.put(player.getUsername(), currentGuesses);

        String feedback = generateFeedback(answer, guessedWord);
        player.sendMessage("GUESS_FEEDBACK:" + guessedWord + ":"  + feedback);

        if (guessedWord.equals(answer) && activeGames.containsKey(player.getUsername())) {
            player.sendMessage("WIN");
            endGameForBothPlayers(player, true);
        }

        ClientHandler opponent = findOpponent(player);
        if (currentGuesses >= MAX_GUESSES && opponent != null && playerGuesses.get(opponent.getUsername()) >= MAX_GUESSES) {
            endGameForBothPlayers(player, false); // Call stalemate logic
        }
    }

    private static void endGameForBothPlayers(ClientHandler winner, boolean guessedCorrectly){
        ClientHandler otherPlayer = findOpponent(winner);
        String answer = activeGames.get(winner.getUsername());

        if (answer == null) {
            // The game has already been marked as ended, no further action needed
            return;
        }

        if (guessedCorrectly) {
            // Standard win condition
            if (otherPlayer != null) {
                winner.sendMessage("WINNER: You won by guessing the word first! The word was: " + answer);
                otherPlayer.sendMessage("LOSER: " + winner.getUsername() + " guessed the word first! The word was: " + answer);
                broadcastMessage("SERVER: " + winner.getUsername() + " has defeated " + otherPlayer.getUsername() +
                        " in WordWhiz by guessing the word '" + answer + "'.", null);
            } else {
                winner.sendMessage("WINNER: You won! The word was: " + answer + ". Opponent is no longer available.");
                broadcastMessage("SERVER: " + winner.getUsername() + " won in WordWhiz by guessing the word '" + answer + "'.", null);
            }
        } else {
            // Stalemate condition: Both players ran out of guesses
            winner.sendMessage("STALEMATE: Both players ran out of guesses. The word was: " + answer);
            if (otherPlayer != null) {
                otherPlayer.sendMessage("STALEMATE: Both players ran out of guesses. The word was: " + answer);
            }
            broadcastMessage("SERVER: The game between " + winner.getUsername() + " and " + otherPlayer.getUsername() + " ended in a stalemate. The word was: '" + answer + "'.", null);
        }

        // Clean up game data for both players
        activeGames.remove(winner.getUsername());
        if (otherPlayer != null) {
            activeGames.remove(otherPlayer.getUsername());
            playerGuesses.remove(otherPlayer.getUsername());
        }
        playerGuesses.remove(winner.getUsername());
    }

    // Method to broadcast message to all clients
    public static synchronized void broadcastMessage(String message, ClientHandler sender) {
        for (ClientHandler client : clientHandlers) {
            if (client != sender) {
                client.sendMessage(message);
            } else if (client == sender && message.startsWith("SERVER:")) {
                client.sendMessage(message);
            }
        }
    }

    /*public static synchronized void broadcastGameMessage(String message, ClientHandler sender){
        for(ClientHandler client : clientHandlers){
            if (client != sender || message.startsWith("SERVER:")){
                client.sendMessage(message);
            }
        }
    }*/

    public static synchronized void broadcastUserList() {
        String userList = "USERS:" + String.join(",", usernames); // Combine all usernames into a single string
        for (ClientHandler client : clientHandlers) {
            client.sendMessage(userList);
        }
    }

    // Method to remove a client from the client handler list
    public static synchronized void removeClient(ClientHandler clientHandler) {
        clientHandlers.remove(clientHandler);
        usernames.remove(clientHandler.getUsername());
        broadcastUserList();
    }

    public static HashSet<String> getUsernames() {
        return usernames;
    }

    public static synchronized ClientHandler findClientHandler(String username){
        for (ClientHandler client : clientHandlers){
            if(client.getUsername().equals(username)){
                return client;
            }
        }
        return null;
    }

    private static ClientHandler findOpponent(ClientHandler player){
        for (Map.Entry<String, String> entry : activeGames.entrySet()){
            if (!entry.getKey().equals(player.getUsername()) && entry.getValue().equals(activeGames.get(player.getUsername()))){
                return findClientHandler(entry.getKey());
            }
        }
        return null;
    }

    public static void handleWin(ClientHandler winner){
        endGameForBothPlayers(winner,true);
    }
}

class ClientHandler implements Runnable {
    private Socket clientSocket;
    private PrintWriter out;
    private BufferedReader in;
    private String username;

    // NEW
    private static ConcurrentHashMap<String, ClientHandler> pendingChallenges = new ConcurrentHashMap<>();

    public ClientHandler(Socket socket) throws IOException {
        this.clientSocket = socket;
        this.out = new PrintWriter(clientSocket.getOutputStream(), true);
        this.in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
    }

    @Override
    public void run() {
        try {
            this.username = in.readLine();
            if(username == null || username.trim().isEmpty()){
                out.println("ERROR: Username cannot be blank.");
                clientSocket.close();
                return;
            }

            Server.getUsernames().add(username);
            Server.broadcastUserList();

            System.out.println(username + " has joined the chat.");
            Server.broadcastMessage(username + " has joined the chat.", this);

            String message;
            while ((message = in.readLine()) != null) {

                if (message.equals("WIN")) {
                    // Handle win notification from client
                    Server.handleWin(this);
                } else if (message.startsWith("GUESS:")) {
                    String guessedWord = message.substring(6).trim().toUpperCase();
                    Server.handleGuess(this, guessedWord);
                }else if(message.startsWith("/challenge ")){// NEW
                    handleChallenge(message.substring(11).trim());
                }else if(message.equals("/y")){
                    handleAcceptChallenge();
                }else if(message.equals("/n")){
                    handleDeclineChallenge();
                }else if(message.equals("/quit")){
                    Server.removeClient(this);
                    break;
                }// END NEW
                else if(message.equalsIgnoreCase("bye") || message.equalsIgnoreCase("goodbye")){
                    Server.broadcastMessage("SERVER: Goodbye, " + username, this);
                }else if(message.equals("/allUsers")){
                    Server.broadcastMessage("SERVER: " + Server.getUsernames().toString(), this);
                }else{
                    System.out.println(username + ": " + message);
                    Server.broadcastMessage(username + ": " + message, this);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if(username != null){
                    Server.broadcastMessage(username + " has left the chat.", this);
                    System.out.println(username + " has disconnected from the server.");
                    Server.removeClient(this);
                }
                clientSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1); //https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ScheduledExecutorService.html
    private void handleChallenge(String challengedUsername){
        ClientHandler challengedPlayer = Server.findClientHandler(challengedUsername);

        if(challengedPlayer != null && !challengedPlayer.equals(this)){
            pendingChallenges.put(challengedUsername, this);
            challengedPlayer.sendMessage("SERVER: " + username + " has challenged you to a game! Type /y to accept or /n to decline.");
            sendMessage("SERVER: Challenge sent to " + challengedUsername);

            // Schedule challenge timeout
            scheduler.schedule(() -> {
                if (pendingChallenges.get(challengedUsername) == this) {
                    pendingChallenges.remove(challengedUsername);
                    sendMessage("SERVER: Your challenge to " + challengedUsername + " has expired.");
                    challengedPlayer.sendMessage("SERVER: Challenge from " + username + " expired.");
                }
            }, 30, TimeUnit.SECONDS); // 30-second timeout
        } else {
            sendMessage("SERVER: User not found or cannot challenge yourself");
        }
    }

    /*public void setGame(Game game){
        this.game = game;
    }

    public Game getGame(){
        return this.game;
    }*/

    private void handleAcceptChallenge(){// NEW
        ClientHandler challenger = pendingChallenges.get(username);

        if(challenger != null){
            sendMessage("SERVER: You accepted the challenge from " + challenger.getUsername());
            challenger.sendMessage("SERVER: " + username + " has accepted your challenge! Starting game...");

            Server.startGame(this,challenger);
            pendingChallenges.remove(username);
        }else{
            sendMessage("SERVER: No challenge to accept.");
        }
    }

    private void handleDeclineChallenge(){// NEW
        ClientHandler challenger = pendingChallenges.get(username);

        if(challenger != null){
            sendMessage("SERVER: You declined the challenge from " + challenger.getUsername());
            challenger.sendMessage("SERVER: " + username + " declined your challenge.");
            pendingChallenges.remove(username);
        }else{
            sendMessage("SERVER: No challenge to decline.");
        }
    }

    public void sendMessage(String message) {
        out.println(message);
    }

    public String getUsername(){
        return username;
    }
}
