package com.example.mainapplication;

public class User {
    private String uid;
    private String username;
    private String email;
    private String lastMessage;
    private long lastMessageTime;
    private boolean online;
    private String typingTo;

    public User() {}

    public User(String uid, String username, String email) {
        this.uid = uid;
        this.username = username;
        this.email = email;
        this.lastMessage = "";
        this.lastMessageTime = 0L;
        this.online = true;
        this.typingTo = "";
    }

    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getLastMessage() { return lastMessage; }
    public void setLastMessage(String lastMessage) { this.lastMessage = lastMessage; }

    public long getLastMessageTime() { return lastMessageTime; }
    public void setLastMessageTime(long lastMessageTime) { this.lastMessageTime = lastMessageTime; }

    public boolean isOnline() { return online; }
    public void setOnline(boolean online) { this.online = online; }

    public String getTypingTo() { return typingTo; }
    public void setTypingTo(String typingTo) { this.typingTo = typingTo; }
}
