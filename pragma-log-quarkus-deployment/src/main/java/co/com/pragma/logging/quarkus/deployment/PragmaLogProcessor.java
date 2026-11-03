package co.com.pragma.logging.quarkus.deployment;

import co.com.pragma.logging.core.alert.LogAlertListener;
import co.com.pragma.logging.core.masking.LogMasker;
import co.com.pragma.logging.core.masking.MaskedLogField;
import co.com.pragma.logging.core.masking.MaskingStrategy;
import co.com.pragma.logging.quarkus.PragmaLogHttpFilter;
import co.com.pragma.logging.quarkus.PragmaLogRecorder;
import co.com.pragma.logging.quarkus.slf4j.PragmaSlf4jServiceProvider;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.Capabilities;
import io.quarkus.deployment.Capability;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.LogConsoleFormatBuildItem;
import io.quarkus.deployment.builditem.LogFileFormatBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveMethodBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ServiceProviderBuildItem;
import java.lang.reflect.Modifier;
import java.util.Set;
import java.util.TreeSet;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.slf4j.spi.SLF4JServiceProvider;

/** Build steps of the pragma-log Quarkus extension (ADR-11). */
class PragmaLogProcessor {

    private static final String FEATURE = "pragma-log";
    private static final DotName MASKED_LOG_FIELD = DotName.createSimple(MaskedLogField.class);
    private static final DotName ALERT_LISTENER = DotName.createSimple(LogAlertListener.class);
    private static final DotName MASKING_STRATEGY = DotName.createSimple(MaskingStrategy.class);

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    /** Runs before application code, so SLF4J binds to pragma-log even when other bindings are visible. */
    @BuildStep
    @Record(ExecutionTime.STATIC_INIT)
    void slf4jBinding(PragmaLogRecorder recorder) {
        recorder.preferPragmaSlf4j();
    }

    /** JSON or text with masking for console and file logs; Quarkus keeps its formatter when pragma-log is off. */
    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void formatters(PragmaLogRecorder recorder, BuildProducer<LogConsoleFormatBuildItem> console,
            BuildProducer<LogFileFormatBuildItem> file) {
        console.produce(new LogConsoleFormatBuildItem(recorder.formatter(true)));
        file.produce(new LogFileFormatBuildItem(recorder.formatter(false)));
    }

    /** Request context and completion log, only for applications with an HTTP server. */
    @BuildStep
    void httpContext(Capabilities capabilities, BuildProducer<AdditionalBeanBuildItem> beans) {
        if (capabilities.isPresent(Capability.VERTX_HTTP)) {
            beans.produce(AdditionalBeanBuildItem.unremovableOf(PragmaLogHttpFilter.class));
        }
    }

    /** The SLF4J binding is found through ServiceLoader, also in native mode. */
    @BuildStep
    ServiceProviderBuildItem slf4jProvider() {
        return new ServiceProviderBuildItem(SLF4JServiceProvider.class.getName(),
                PragmaSlf4jServiceProvider.class.getName());
    }

    /**
     * Native mode: masking reads fields through Jackson, which needs reflection. Classes with
     * {@code @MaskedLogField} are registered automatically; other logged classes need
     * {@code @RegisterForReflection}, or are written as {@code <ClassName>}.
     */
    @BuildStep
    ReflectiveClassBuildItem maskedClasses(CombinedIndexBuildItem index) {
        Set<String> classes = new TreeSet<>();
        classes.add(LogMasker.class.getName() + "$SelfCheck"); // serialized at startup by LogMasker.verify()
        for (AnnotationInstance annotation : index.getIndex().getAnnotations(MASKED_LOG_FIELD)) {
            AnnotationTarget target = annotation.target();
            switch (target.kind()) {
                case FIELD -> classes.add(target.asField().declaringClass().name().toString());
                case METHOD -> classes.add(target.asMethod().declaringClass().name().toString());
                case RECORD_COMPONENT -> classes.add(target.asRecordComponent().declaringClass().name().toString());
                default -> { }
            }
        }
        return ReflectiveClassBuildItem.builder(classes.toArray(String[]::new))
                .constructors().methods().fields().build();
    }

    /**
     * Native mode: alert listeners and mask strategies are created from their class names in the configuration.
     * Implementations in the application index are registered so that works without {@code @RegisterForReflection}.
     */
    @BuildStep
    ReflectiveClassBuildItem pluggableClasses(CombinedIndexBuildItem index) {
        Set<String> classes = new TreeSet<>();
        for (DotName type : Set.of(ALERT_LISTENER, MASKING_STRATEGY)) {
            index.getIndex().getAllKnownImplementations(type).stream()
                    .filter(info -> !Modifier.isAbstract(info.flags()))
                    .map(ClassInfo::name)
                    .map(DotName::toString)
                    .forEach(classes::add);
        }
        return ReflectiveClassBuildItem.builder(classes.toArray(String[]::new)).constructors().build();
    }

    /** Native mode: colors in AUTO mode ask {@code Console.isTerminal()} (Java 22+) through reflection. */
    @BuildStep
    ReflectiveMethodBuildItem consoleIsTerminal() {
        return new ReflectiveMethodBuildItem("pragma-log AUTO colors", "java.io.Console", "isTerminal",
                new String[0]);
    }
}
