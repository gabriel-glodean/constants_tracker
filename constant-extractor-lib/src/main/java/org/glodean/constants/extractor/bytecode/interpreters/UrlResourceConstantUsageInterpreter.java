package org.glodean.constants.extractor.bytecode.interpreters;

import org.glodean.constants.interpreter.ConstantUsageInterpreter;
import org.glodean.constants.interpreter.MethodCallContext;
import org.glodean.constants.model.ClassConstant.ConstantUsage;
import org.glodean.constants.model.ClassConstant.CoreSemanticType;
import org.glodean.constants.model.ClassConstant.UsageLocation;
import org.glodean.constants.model.ClassConstant.UsageType;

import java.util.Map;
import java.util.Set;

/**
 * Interprets constants passed to URL / URI / HTTP-client methods as
 * {@link CoreSemanticType#URL_RESOURCE}.
 *
 * <p>Detects usage with {@code java.net.URL}, {@code java.net.URI},
 * {@code java.net.http.HttpRequest}, and common HTTP client libraries.
 */
public class UrlResourceConstantUsageInterpreter implements ConstantUsageInterpreter {

    private static final Set<String> URL_CLASSES = Set.of(
            "java/net/URL",
            "java/net/URI",
            "java/net/http/HttpRequest",
            "java/net/http/HttpClient"
    );

    private static final Set<String> URL_METHODS = Set.of(
            "<init>", "create", "of", "newBuilder",
            "uri", "GET", "POST", "PUT", "DELETE"
    );

    private static final Set<String> HTTP_CLIENT_CLASSES = Set.of(
            "org/apache/http/client/methods/HttpGet",
            "org/apache/http/client/methods/HttpPost",
            "org/apache/http/client/methods/HttpPut",
            "org/apache/http/client/methods/HttpDelete",
            "okhttp3/Request$Builder",
            "org/springframework/web/reactive/function/client/WebClient",
            "org/springframework/web/client/RestTemplate"
    );

    private static final Set<String> HTTP_CLIENT_METHODS = Set.of(
            "<init>", "uri", "url",
            "getForObject", "getForEntity",
            "postForObject", "postForEntity",
            "exchange", "delete", "put"
    );

    @Override
    public ConstantUsage interpret(UsageLocation location, InterpretationContext context) {
        if (!(context instanceof MethodCallContext(String targetClass, String targetMethod, String methodDescriptor, _))) {
            return unknown(location);
        }

        if (isUrlMethod(targetClass, targetMethod)) {
            double confidence = calculateConfidence(targetClass);
            return new ConstantUsage(
                    UsageType.METHOD_INVOCATION_PARAMETER,
                    CoreSemanticType.URL_RESOURCE,
                    location,
                    confidence,
                    Map.of(
                            "urlClass", targetClass,
                            "urlMethod", targetMethod,
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

    private boolean isUrlMethod(String targetClass, String targetMethod) {
        if (URL_CLASSES.contains(targetClass) && URL_METHODS.contains(targetMethod)) {
            return true;
        }
        return HTTP_CLIENT_CLASSES.contains(targetClass) && HTTP_CLIENT_METHODS.contains(targetMethod);
    }

    private double calculateConfidence(String targetClass) {
        if (URL_CLASSES.contains(targetClass)) {
            return 0.95;
        }
        return 0.85;
    }

    private static ConstantUsage unknown(UsageLocation location) {
        return new ConstantUsage(UsageType.METHOD_INVOCATION_PARAMETER, CoreSemanticType.UNKNOWN, location, 0.0);
    }
}

