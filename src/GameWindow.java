import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class GameWindow extends JFrame {
    private JPanel gameBoard;
    private JButton[] letterButtons;
    private JTextField guessField;
    private JPanel guessHistoryPanel;
    private int maxGuesses = 5;
    private int currentGuess = 0;
    private JButton submitButton;
    private static String answer;
    private Client client;

    public GameWindow(String username, Client client) {
        super("Game - " + username);
        this.client = client;
        setupGameUI();
    }


    // Set up the game-specific UI
    private void setupGameUI() {
        setLayout(new BorderLayout());

        /*String[] qwertyRows = {
                "QWERTYUIOP",
                "ASDFGHJKL",
                "ZXCVBNM"
        };*/

        gameBoard = new JPanel(new GridLayout(4, 7));
        letterButtons = new JButton[26];

        // Initialize each letter button A-Z
        for (int i = 0; i < 26; i++) {
            letterButtons[i] = new JButton(Character.toString((char) ('A' + i)));
            letterButtons[i].setEnabled(true);
            letterButtons[i].setBackground(Color.LIGHT_GRAY);
            letterButtons[i].addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    JButton button = (JButton) e.getSource();
                    String letter = button.getText();
                    if(guessField.getText().length() < 5){
                        guessField.setText(guessField.getText() + letter);
                    }
                }
            });

            gameBoard.add(letterButtons[i]);
        }

        guessHistoryPanel = new JPanel(new GridLayout(maxGuesses, 1, 5, 5));
        for (int i = 0; i < maxGuesses; i++) {
            JPanel row = new JPanel(new GridLayout(1, 5, 5, 5));
            for (int j = 0; j < 5; j++) {
                JLabel letterSlot = new JLabel(" ", SwingConstants.CENTER);
                letterSlot.setOpaque(true);
                letterSlot.setBorder(BorderFactory.createLineBorder(Color.BLACK));
                row.add(letterSlot);
            }
            guessHistoryPanel.add(row);
        }

        guessField = new JTextField(10);
        submitButton = new JButton("Submit Guess");

        JPanel guessPanel = new JPanel(new BorderLayout());
        guessField = new JTextField(5);
        JButton submitGuessButton = new JButton("Submit Guess");

        submitGuessButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                submitGuess();
            }
        });

        guessField.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                submitGuess();
            }
        });

        JButton clearButton = new JButton("Clear");
        clearButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                guessField.setText("");
            }
        });

        guessPanel.add(new JLabel("Your Guess:"), BorderLayout.WEST);
        guessPanel.add(guessField, BorderLayout.CENTER);
        guessPanel.add(submitGuessButton, BorderLayout.EAST);
        guessPanel.add(clearButton,BorderLayout.SOUTH);

        add(gameBoard,BorderLayout.NORTH);
        add(guessHistoryPanel, BorderLayout.CENTER);
        add(guessPanel,BorderLayout.SOUTH);

        setSize(400, 400);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE); // Closes only this window
        setVisible(true);
    }

    // Update the state of a letter button based on game feedback
    public void updateLetterState(char letter, LetterState state) {
        JButton button = letterButtons[letter - 'A'];
        Color newColor;
        switch (state) {
            case PRESENT_CORRECT_LOCATION:
                newColor = Color.GREEN;
                break;
            case PRESENT_WRONG_LOCATION:
                // Only update to yellow if it's not already green
                newColor = button.getBackground() != Color.GREEN ? Color.YELLOW : button.getBackground();
                break;
            case UNAVAILABLE:
                // Only update to red if it's not already green or yellow
                newColor = (button.getBackground() != Color.GREEN && button.getBackground() != Color.YELLOW) ? Color.RED : button.getBackground();
                break;
            default: //Available
                newColor = Color.LIGHT_GRAY;
                break;
        }
        button.setBackground(newColor);
    }

    private void submitGuess(){
        String guess = guessField.getText().trim().toUpperCase();
        if (guess.length() == 5) {
            // Send guess to client, which will forward it to the server
            client.sendGuessToServer(guess);
            guessField.setText("");
        } else {
            JOptionPane.showMessageDialog(this, "Please enter a 5-letter word.", "Invalid Input", JOptionPane.WARNING_MESSAGE);
        }
    }

    public void displayFeedback(String guess, String feedback) {
        if ((currentGuess + 1) >= maxGuesses) {
            JOptionPane.showMessageDialog(this, "No more guesses left!");
            return;
        }

        JPanel guessRow = (JPanel) guessHistoryPanel.getComponent(currentGuess);

        for (int i = 0; i < feedback.length(); i++) {
            JLabel letterSlot = (JLabel) guessRow.getComponent(i);
            char feedbackChar = feedback.charAt(i);
            char guessLetter = guess.charAt(i);

            letterSlot.setText(Character.toString(guessLetter));

            switch (feedbackChar) {
                case 'G':
                    letterSlot.setBackground(Color.GREEN);  // Correct letter and position
                    updateLetterState(guessLetter, LetterState.PRESENT_CORRECT_LOCATION);
                    break;
                case 'Y':
                    letterSlot.setBackground(Color.YELLOW); // Correct letter, wrong position
                    updateLetterState(guessLetter, LetterState.PRESENT_WRONG_LOCATION);
                    break;
                case 'R':
                    letterSlot.setBackground(Color.RED);    // Incorrect letter
                    updateLetterState(guessLetter, LetterState.UNAVAILABLE);
                    break;
            }
        }
        currentGuess++;
        /*if (guess.equals(answer)) {  // Winning condition
            client.sendMessage("WIN"); // Notify the server of the win
            endGame(true);
        } else if (currentGuess >= maxGuesses) {
            JOptionPane.showMessageDialog(this, "You've used your last guess!");
            endGame(false);
        }*/
    }

    private boolean isGameEnded = false;
    public void endGame(boolean won){
        if (isGameEnded){
            return;
        }
        isGameEnded = true;
        String resultMessage = won ? "Congrats! You guessed " + answer + "!" : "Game Over! The correct word was: " + answer + ". Better luck next time!";

        JOptionPane.showMessageDialog(this,resultMessage,"Game Over", JOptionPane.INFORMATION_MESSAGE);
        guessField.setEnabled(false);
        submitButton.setEnabled(false);
        dispose();

    }

    /*private LetterState getLetterState(char letter, int position){
        if(answer.charAt(position) == letter){
            return LetterState.PRESENT_CORRECT_LOCATION;
        } else if (answer.indexOf(letter) >= 0) {
            return LetterState.PRESENT_WRONG_LOCATION;
        }else{
            return LetterState.UNAVAILABLE;
        }
    }*/

    public void setAnswer(String answer){
        GameWindow.answer = answer;
        System.out.println(answer);
    }

    public void showPopup(String message) {
        JOptionPane.showMessageDialog(this, message, "Notification", JOptionPane.INFORMATION_MESSAGE);
    }

    public enum LetterState{
        AVAILABLE, //default state
        PRESENT_WRONG_LOCATION,
        PRESENT_CORRECT_LOCATION,
        UNAVAILABLE
    }
}


