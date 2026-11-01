package ewm.model;

public record EventPair(long eventA, long eventB) {

    public EventPair {
        if (eventA > eventB) {
            long temp = eventA;
            eventA = eventB;
            eventB = temp;
        }

        if (eventA == eventB) {
            throw new IllegalArgumentException(
                    "Нельзя создать пару из одного мероприятия"
            );
        }
    }
}
