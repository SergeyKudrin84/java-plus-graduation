package ewm.participationRequest.model;

public enum ParticipationStatus {
    PENDING,
    REJECTED,
    CONFIRMED,
    CANCELED;

    public enum State {
        PENDING,
        PUBLISHED,
        CANCELED
    }
}
