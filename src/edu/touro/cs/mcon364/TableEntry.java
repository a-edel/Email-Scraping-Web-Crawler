package edu.touro.cs.mcon364;

import java.sql.Timestamp;

public class TableEntry
{
    private String emailAddress;
    private String source;
    private Timestamp timestamp;

    public TableEntry(String emailAddress, String source, Timestamp timestamp)
    {
        this.emailAddress = emailAddress;
        this.source = source;
        this.timestamp = timestamp;
    }

    public String getEmailAddress() {
        return emailAddress;
    }

    public String getSource() {
        return source;
    }

    public Timestamp getTimestamp() {
        return timestamp;
    }
}
