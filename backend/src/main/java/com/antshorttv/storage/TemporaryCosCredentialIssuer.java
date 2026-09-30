package com.antshorttv.storage;

public abstract class TemporaryCosCredentialIssuer {
    public abstract TemporaryCosCredentials issue(String name, String policy, long durationSeconds);
}
