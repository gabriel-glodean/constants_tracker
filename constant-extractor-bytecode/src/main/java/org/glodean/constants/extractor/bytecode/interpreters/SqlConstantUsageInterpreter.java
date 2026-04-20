package org.glodean.constants.extractor.bytecode.interpreters;

import org.glodean.constants.interpreter.ConstantUsageInterpreter;
import org.glodean.constants.interpreter.MethodCallContext;
import org.glodean.constants.model.UnitConstant.ConstantUsage;
import org.glodean.constants.model.UnitConstant.CoreSemanticType;
import org.glodean.constants.model.UnitConstant.UsageLocation;
import org.glodean.constants.model.UnitConstant.UsageType;

import java.util.Map;
import java.util.Set;

/**
 * Interprets constants passed to JDBC / JPA methods as {@link CoreSemanticType#SQL_FRAGMENT}.
 *
 * <p>Recognizes common database API classes such as {@code java.sql.Statement},
 * {@code java.sql.Connection}, {@code java.sql.PreparedStatement},
 * {@code javax.persistence.EntityManager}, and Spring {@code JdbcTemplate}.
 */
public class SqlConstantUsageInterpreter implements ConstantUsageInterpreter {

    private static final Set<String> JDBC_CLASSES = Set.of(
            "java/sql/Statement",
            "java/sql/PreparedStatement",
            "java/sql/CallableStatement",
            "java/sql/Connection"
    );

    private static final Set<String> JDBC_METHODS = Set.of(
            "executeQuery", "executeUpdate", "execute",
            "prepareStatement", "prepareCall", "nativeSQL"
    );

    private static final Set<String> JPA_CLASSES = Set.of(
            "javax/persistence/EntityManager",
            "jakarta/persistence/EntityManager"
    );

    private static final Set<String> JPA_METHODS = Set.of(
            "createQuery", "createNativeQuery", "createNamedQuery"
    );

    private static final Set<String> SPRING_JDBC_CLASSES = Set.of(
            "org/springframework/jdbc/core/JdbcTemplate",
            "org/springframework/jdbc/core/namedparam/NamedParameterJdbcTemplate"
    );

    private static final Set<String> SPRING_JDBC_METHODS = Set.of(
            "query", "queryForObject", "queryForList", "queryForMap",
            "queryForRowSet", "update", "batchUpdate", "execute"
    );

    @Override
    public ConstantUsage interpret(UsageLocation location, InterpretationContext context) {
        if (!(context instanceof MethodCallContext(String targetClass, String targetMethod, String methodDescriptor, _))) {
            return unknown(location);
        }

        if (isSqlMethod(targetClass, targetMethod)) {
            double confidence = calculateConfidence(targetClass);
            return new ConstantUsage(
                    UsageType.METHOD_INVOCATION_PARAMETER,
                    CoreSemanticType.SQL_FRAGMENT,
                    location,
                    confidence,
                    Map.of(
                            "dbClass", targetClass,
                            "dbMethod", targetMethod,
                            "methodDescriptor", methodDescriptor
                    )
            );
        }

        return unknown(location);
    }

    @Override
    public boolean canInterpret(UsageType type) {
        return type == UsageType.METHOD_INVOCATION_PARAMETER;
    }

    private boolean isSqlMethod(String targetClass, String targetMethod) {
        if (JDBC_CLASSES.contains(targetClass) && JDBC_METHODS.contains(targetMethod)) {
            return true;
        }
        if (JPA_CLASSES.contains(targetClass) && JPA_METHODS.contains(targetMethod)) {
            return true;
        }
        return SPRING_JDBC_CLASSES.contains(targetClass) && SPRING_JDBC_METHODS.contains(targetMethod);
    }

    private double calculateConfidence(String targetClass) {
        if (JDBC_CLASSES.contains(targetClass)) {
            return 0.95;
        }
        if (JPA_CLASSES.contains(targetClass)) {
            return 0.90;
        }
        return 0.85;
    }

    private static ConstantUsage unknown(UsageLocation location) {
        return new ConstantUsage(UsageType.METHOD_INVOCATION_PARAMETER, CoreSemanticType.UNKNOWN, location, 0.0);
    }
}

