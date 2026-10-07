package ray.labs.rayauction.domain;

import java.util.Objects;

public sealed interface Result<T> {

    record Success<T>(T value, Outcome outcome) implements Result<T> {

        public Success {
            Objects.requireNonNull(value, "value");
        }

        public static <T> Success<T> of(T value) {
            return new Success<>(value, null);
        }

        public static <T> Success<T> of(T value, Outcome outcome) {
            return new Success<>(value, outcome);
        }
    }

    record Failure<T>(Outcome outcome) implements Result<T> {

        public Failure {
            Objects.requireNonNull(outcome, "outcome");
        }

        public static <T> Failure<T> of(String messageKey) {
            return new Failure<>(Outcome.of(messageKey));
        }

        public static <T> Failure<T> of(Outcome outcome) {
            return new Failure<>(outcome);
        }
    }

    default boolean isSuccess() {
        return this instanceof Success<T>;
    }

    default T valueOrThrow() {
        if (this instanceof Success<T> success) {
            return success.value();
        }
        throw new IllegalStateException("result is a failure: " + ((Failure<T>) this).outcome().messageKey());
    }

    default Outcome outcome() {
        return switch (this) {
            case Success<T> success -> success.outcome();
            case Failure<T> failure -> failure.outcome();
        };
    }
}
