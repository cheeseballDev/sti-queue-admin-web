package model;

public class QueueModel {
    private String queueType;
    private long counter;
    private long currentNumber;
    private long currentServing;
    private boolean isOnBreak;

    // getters

    public QueueModel() {}

    public String getQueueType() {
        return this.queueType;
    }

    public long getCounter() {
        return this.counter;
    }

    public long getCurrentNumber() {
        return this.currentNumber;
    }

    public long getCurrentServing() {
        return this.currentServing;
    }

    public boolean getIsOnBreak() {
        return this.isOnBreak;
    }

    public void setQueueType(String newQueueType) {
        this.queueType = newQueueType;
    }

    public void setCounter(long newCounter) {
        this.counter = newCounter;
    }

    public void setCurrentNumber(long newCurrentNumber) {
        this.currentNumber = newCurrentNumber;
    }

    public void setCurrentServing(long newCurrentServing) {
        this.currentServing = newCurrentServing;
    }

    
}
