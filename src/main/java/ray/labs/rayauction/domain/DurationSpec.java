package ray.labs.rayauction.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public record DurationSpec(String raw, Duration duration) {

    private static final BigDecimal MINUTE = BigDecimal.valueOf(60L);
    private static final BigDecimal HOUR = BigDecimal.valueOf(3_600L);
    private static final BigDecimal DAY = BigDecimal.valueOf(86_400L);
    private static final BigDecimal WEEK = BigDecimal.valueOf(604_800L);

    public DurationSpec {
        if (duration == null || duration.isNegative() || duration.isZero()) {
            throw new IllegalArgumentException("duration must be positive: " + raw);
        }
    }

    public static Optional<DurationSpec> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String normalized = collapseSeparators(raw.trim().toLowerCase(Locale.ROOT));
        List<String> tokens = joinBareNumbers(normalized.split(" "));
        Duration total = Duration.ZERO;
        boolean matched = false;
        for (String token : tokens) {
            if (token.isEmpty()) {
                continue;
            }
            Optional<Duration> parsed = parseUnit(token);
            if (parsed.isEmpty()) {
                return Optional.empty();
            }
            total = total.plus(parsed.get());
            matched = true;
        }
        if (!matched || total.isZero() || total.isNegative()) {
            return Optional.empty();
        }
        return Optional.of(new DurationSpec(raw.trim(), total));
    }

    public static List<DurationSpec> parseAll(List<String> values) {
        List<DurationSpec> specs = new ArrayList<>();
        for (String value : values) {
            parse(value).ifPresent(specs::add);
        }
        return List.copyOf(specs);
    }

    public String label() {
        return raw;
    }

    public long seconds() {
        return duration.toSeconds();
    }

    private static String collapseSeparators(String value) {
        StringBuilder collapsed = new StringBuilder(value.length());
        boolean previousSeparator = false;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (Character.isWhitespace(character) || character == ',') {
                if (!previousSeparator) {
                    collapsed.append(' ');
                }
                previousSeparator = true;
                continue;
            }
            collapsed.append(character);
            previousSeparator = false;
        }
        return collapsed.toString().trim();
    }

    private static List<String> joinBareNumbers(String[] parts) {
        List<String> tokens = new ArrayList<>();
        for (String part : parts) {
            if (!part.isEmpty()) {
                tokens.add(part);
            }
        }
        List<String> merged = new ArrayList<>();
        for (int index = 0; index < tokens.size(); index++) {
            String token = tokens.get(index);
            if (isNumeric(token) && index + 1 < tokens.size()) {
                merged.add(token + tokens.get(index + 1));
                index++;
                continue;
            }
            merged.add(token);
        }
        return merged;
    }

    private static boolean isNumeric(String value) {
        boolean digits = false;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (Character.isDigit(character)) {
                digits = true;
                continue;
            }
            if (character != '.') {
                return false;
            }
        }
        return digits;
    }

    private static Optional<Duration> parseUnit(String token) {
        int splitAt = 0;
        while (splitAt < token.length()
                && (Character.isDigit(token.charAt(splitAt)) || token.charAt(splitAt) == '.')) {
            splitAt++;
        }
        String digits = token.substring(0, splitAt);
        String unit = token.substring(splitAt);
        if (digits.isEmpty() || unit.isEmpty()) {
            return Optional.empty();
        }
        BigDecimal amount;
        try {
            amount = new BigDecimal(digits);
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
        if (amount.signum() <= 0) {
            return Optional.empty();
        }
        try {
            return switch (unit) {
                case "s", "sec", "secs", "second", "seconds" -> Optional.of(seconds(amount));
                case "m", "min", "mins", "minute", "minutes" -> Optional.of(seconds(amount.multiply(MINUTE)));
                case "h", "hr", "hour", "hours" -> Optional.of(seconds(amount.multiply(HOUR)));
                case "d", "day", "days" -> Optional.of(seconds(amount.multiply(DAY)));
                case "w", "week", "weeks" -> Optional.of(seconds(amount.multiply(WEEK)));
                default -> Optional.empty();
            };
        } catch (ArithmeticException ex) {
            return Optional.empty();
        }
    }

    private static Duration seconds(BigDecimal value) {
        return Duration.ofSeconds(value.setScale(0, RoundingMode.DOWN).longValueExact());
    }
}
