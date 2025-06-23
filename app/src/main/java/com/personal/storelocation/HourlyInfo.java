package com.personal.storelocation;

public class HourlyInfo {
    public int hour;
    public float distance;
    public int points;
    public boolean hasCheckpoint;
    public String checkpointNote;

    public HourlyInfo(int hour, float distance, int points, boolean hasCheckpoint, String checkpointNote) {
        this.hour = hour;
        this.distance = distance;
        this.points = points;
        this.hasCheckpoint = hasCheckpoint;
        this.checkpointNote = checkpointNote;
    }

    HourlyInfo(){

    }

    public String getCheckpointNote() {
        return checkpointNote;
    }

    public void setCheckpointNote(String checkpointNote) {
        this.checkpointNote = checkpointNote;
    }

    public boolean isHasCheckpoint() {
        return hasCheckpoint;
    }

    public void setHasCheckpoint(boolean hasCheckpoint) {
        this.hasCheckpoint = hasCheckpoint;
    }

    public int getHour() {
        return hour;
    }

    public void setHour(int hour) {
        this.hour = hour;
    }

    public float getDistance() {
        return distance;
    }

    public void setDistance(float distance) {
        this.distance = distance;
    }

    public int getPoints() {
        return points;
    }

    public void setPoints(int points) {
        this.points = points;
    }

    @Override
    public String toString() {
        return "HourlyInfo{" +
                "hour=" + hour +
                ", distance=" + distance +
                ", points=" + points +
                ", hasCheckpoint=" + hasCheckpoint +
                ", checkpointNote='" + checkpointNote + '\'' +
                '}';
    }
}
