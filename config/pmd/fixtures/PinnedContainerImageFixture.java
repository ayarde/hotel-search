import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

// Fixture de verifyPmdTestRules: no se compila. Ver build.gradle.kts.
class PinnedContainerImageFixture {

    KafkaContainer withoutTag = new KafkaContainer("apache/kafka");
    String latestLiteral = "apache/kafka:latest";
    DockerImageName parsedWithoutTag = DockerImageName.parse("apache/kafka");

    KafkaContainer pinned = new KafkaContainer("apache/kafka:4.4.0");
    DockerImageName parsedPinned = DockerImageName.parse("apache/kafka:4.4.0");
}
