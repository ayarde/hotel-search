package com.hotelsearch.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariDataSource;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.task.TaskExecutor;

/**
 * Context test de la configuración operativa de la Fase 6.
 */
class OperationalConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withInitializer(new ConfigDataApplicationContextInitializer())
        .withConfiguration(AutoConfigurations.of(
            TaskExecutionAutoConfiguration.class,
            DataSourceAutoConfiguration.class));

    /**
     * Verifica que la configuración operativa habilite Virtual Threads,
     * acote HikariCP y externalice los timeouts críticos.
     *
     * Condiciones:
     * - Se cargan application.yml y overrides de variables de entorno
     *
     * Se espera:
     * - El executor de la aplicación use Virtual Threads
     * - HikariCP use el máximo configurado
     * - Los timeouts y el formato JSON reflejen la configuración externa
     */
    @Test
    void context_OperationalProperties_ConfigureVirtualThreadsHikariAndTimeouts() throws Exception {
        // given
        contextRunner
            .withPropertyValues(
                "DB_POOL_MAX_SIZE=7",
                "KAFKA_DELIVERY_TIMEOUT_MS=4321",
                "KAFKA_REQUEST_TIMEOUT_MS=2345",
                "SHUTDOWN_TIMEOUT=17s")
            // when
            .run(context -> {
                // then
                assertThat(context.getStartupFailure()).isNull();
                assertThat(context.getEnvironment().getProperty(
                    "spring.threads.virtual.enabled", Boolean.class)).isTrue();
                assertThat(context.getEnvironment().getProperty(
                    "logging.structured.format.console")).isEqualTo("logstash");
                Map<String, String> renamedFields = Binder.get(context.getEnvironment())
                    .bind("logging.structured.json.rename", Map.class)
                    .get();
                assertThat(renamedFields)
                    .containsEntry("@timestamp", "timestamp")
                    .containsEntry("logger_name", "logger");
                assertThat(context.getEnvironment().getProperty(
                    "server.shutdown")).isEqualTo("graceful");
                assertThat(context.getEnvironment().getProperty(
                    "spring.lifecycle.timeout-per-shutdown-phase"))
                    .isEqualTo("17s");
                assertThat(context.getEnvironment().getProperty(
                    "spring.kafka.producer.delivery-timeout-ms", Integer.class)).isEqualTo(4321);
                assertThat(context.getEnvironment().getProperty(
                    "spring.kafka.producer.request-timeout-ms", Integer.class)).isEqualTo(2345);
                assertThat(context.getEnvironment().getProperty(
                    "spring.kafka.template.observation-enabled", Boolean.class)).isTrue();
                assertThat(context.getEnvironment().getProperty(
                    "spring.kafka.listener.observation-enabled", Boolean.class)).isTrue();

                var dataSource = context.getBean(HikariDataSource.class);
                assertThat(dataSource.getMaximumPoolSize())
                    .as("El pool JDBC debe respetar la configuración externa")
                    .isEqualTo(7);

                CountDownLatch executed = new CountDownLatch(1);
                AtomicReference<Thread> worker = new AtomicReference<>();
                TaskExecutor executor = context.getBean(TaskExecutor.class);
                executor.execute(() -> {
                    worker.set(Thread.currentThread());
                    executed.countDown();
                });

                assertThat(executed.await(5, TimeUnit.SECONDS))
                    .as("El executor debe iniciar la tarea")
                    .isTrue();
                assertThat(worker.get().isVirtual())
                    .as("La tarea de la aplicación debe ejecutarse en un Virtual Thread")
                    .isTrue();
            });
    }

    /**
     * Verifica que el pool JDBC continúe acotado por defecto aunque no se
     * proporcionen overrides.
     *
     * Condiciones:
     * - Se carga únicamente la configuración predeterminada
     *
     * Se espera:
     * - HikariCP use un máximo de diez conexiones
     */
    @Test
    void context_DefaultHikariProperties_UseMaximumPoolSizeTen() {
        // given
        contextRunner
            // when
            .run(context -> {
                // then
                assertThat(context.getStartupFailure()).isNull();
                var dataSource = context.getBean(HikariDataSource.class);
                assertThat(dataSource.getMaximumPoolSize())
                    .as("El valor inicial del pool JDBC debe ser diez")
                    .isEqualTo(10);
            });
    }

}
