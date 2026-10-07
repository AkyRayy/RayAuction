package ray.labs.rayauction.text;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

public final class TextFormatter {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static final List<String> SECOND_FORMS = List.of("секунда", "секунды", "секунд");
    private static final List<String> MINUTE_FORMS = List.of("минута", "минуты", "минут");
    private static final List<String> HOUR_FORMS = List.of("час", "часа", "часов");
    private static final List<String> DAY_FORMS = List.of("день", "дня", "дней");

    private final Locale locale;
    private final ZoneId zone;

    public TextFormatter(Locale locale, ZoneId zone) {
        this.locale = locale == null ? Locale.ROOT : locale;
        this.zone = zone == null ? ZoneId.systemDefault() : zone;
    }

    public String money(BigDecimal amount, int scale) {
        BigDecimal value = amount.setScale(scale, RoundingMode.HALF_UP);
        if (scale == 0) {
            return String.format(locale, "%,d", value.longValueExact());
        }
        return String.format(locale, "%,.2f", value).replaceAll("0+$", "").replaceAll("[.,]$", "");
    }

    public String amount(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    public String count(int value) {
        return String.format(locale, "%,d", value);
    }

    public String dateTime(Instant instant) {
        return DATE_TIME.withZone(zone).format(instant);
    }

    public String remaining(Instant until, Instant now) {
        Duration duration = Duration.between(now, until);
        if (duration.isNegative() || duration.isZero()) {
            return duration(duration.abs());
        }
        return duration(duration);
    }

    public String duration(Duration duration) {
        long totalSeconds = duration.toSeconds();
        long days = totalSeconds / 86_400L;
        long hours = (totalSeconds % 86_400L) / 3_600L;
        long minutes = (totalSeconds % 3_600L) / 60L;
        long seconds = totalSeconds % 60L;
        StringBuilder builder = new StringBuilder();
        if (days > 0) {
            builder.append(days).append(' ').append(plural(days, DAY_FORMS));
        }
        if (hours > 0) {
            append(builder).append(hours).append(' ').append(plural(hours, HOUR_FORMS));
        }
        if (minutes > 0 && days == 0) {
            append(builder).append(minutes).append(' ').append(plural(minutes, MINUTE_FORMS));
        }
        if (builder.isEmpty()) {
            append(builder).append(seconds).append(' ').append(plural(seconds, SECOND_FORMS));
        }
        return builder.toString();
    }

    public String plural(long value, List<String> forms) {
        if (forms.size() != 3) {
            return forms.isEmpty() ? "" : forms.get(0);
        }
        long absolute = Math.abs(value) % 100;
        long lastDigit = absolute % 10;
        if (absolute > 10 && absolute < 20) {
            return forms.get(2);
        }
        if (lastDigit > 1 && lastDigit < 5) {
            return forms.get(1);
        }
        if (lastDigit == 1) {
            return forms.get(0);
        }
        return forms.get(2);
    }

    private static StringBuilder append(StringBuilder builder) {
        if (!builder.isEmpty()) {
            builder.append(' ');
        }
        return builder;
    }
}
