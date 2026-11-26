import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

// Fixture de verifyPmdTestRules: no se compila. Ver build.gradle.kts.
// Cada línea que la regla debe marcar termina con "expect: <regla>".
class PinnedContainerImageFixture {

    KafkaContainer withoutTag = new KafkaContainer("apache/kafka"); // expect: PinnedContainerImage
    String latestLiteral = "apache/kafka:latest"; // expect: PinnedContainerImage
    DockerImageName parsedWithoutTag = DockerImageName.parse("apache/kafka"); // expect: PinnedContainerImage

    KafkaContainer pinned = new KafkaContainer("apache/kafka:4.4.0");
    DockerImageName parsedPinned = DockerImageName.parse("apache/kafka:4.4.0");
}
