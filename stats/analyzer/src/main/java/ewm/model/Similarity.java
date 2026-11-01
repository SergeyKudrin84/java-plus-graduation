package ewm.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(
        name = "similarities",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"event1", "event2"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Similarity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long event1;

    @Column(nullable = false)
    private Long event2;

    @Column(nullable = false)
    private Float similarity;

    @Column(nullable = false)
    private OffsetDateTime ts;
}
