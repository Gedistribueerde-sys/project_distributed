package org.example;

public class BulletinBoardImpl implements BulletinBoard {
    @Override
    public String sendMessage(String message) {
        // Implementation here
        System.out.println("Message sent: " + message);
        return message;
    }
}