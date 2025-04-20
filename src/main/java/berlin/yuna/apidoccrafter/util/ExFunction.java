package berlin.yuna.apidoccrafter.util;

@FunctionalInterface
public interface ExFunction<T, R> {

    @SuppressWarnings({"java:S112", "RedundantThrows"})
    R apply(T t) throws Exception;
}
