package com.applymate.model;

/** Pipeline stages, in order. The last three are closed outcomes. */
public enum ApplicationStatus {
    APPLIED,
    ONLINE_ASSESSMENT,
    INTERVIEWING,
    OFFER,
    REJECTED,
    WITHDRAWN;

    /** Still waiting on the employer or on the candidate's next step. */
    public boolean isOpen() {
        return this == APPLIED || this == ONLINE_ASSESSMENT || this == INTERVIEWING;
    }

    /** The employer has replied in some way beyond the initial application. */
    public boolean isResponse() {
        return this == ONLINE_ASSESSMENT || this == INTERVIEWING || this == OFFER || this == REJECTED;
    }
}
