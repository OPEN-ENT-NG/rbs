package net.atos.entng.rbs.models;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;

public class BookingDateUtils {
	public static long secondToDays(long seconds) {
		return TimeUnit.DAYS.convert(seconds, TimeUnit.SECONDS);
	}

	public static ZonedDateTime addDaysIgnoreDST(ZonedDateTime base, int plusNbDays) {
		ZonedDateTime result = base.plusDays(plusNbDays);
		if (result.getHour() != base.getHour()) {
			result.withHour(base.getHour());
		}
		return result;
	}

	public static long daysToSecond(long days) {
		return TimeUnit.SECONDS.convert(days, TimeUnit.DAYS);
	}

	public static long currentTimestampSecondsForIana(String iana) {
		return ZonedDateTime.now(ZoneId.of(iana)).toEpochSecond();
	}

	public static int nextDayOfWeek(final int currentDayOfWeek, final int plusDays) {
		return (currentDayOfWeek + plusDays) % 7;
	}

	public static long tomorrowTimestampSecondsForIana(String iana) {
		// Get tomorrow by adding one day to current time
		ZonedDateTime today = ZonedDateTime.now(ZoneId.of(iana));
		today = today.truncatedTo(ChronoUnit.DAYS);
		ZonedDateTime tomorrowMidnight = today.plusDays(1);
		return tomorrowMidnight.toEpochSecond();
	}

	/**
	 *
	 * @param unixTimestamp in seconds
	 * @return 0 for sunday, 1 for monday, etc
	 */
	public static int dayOfWeekForTimestampSecondsAndIana(final long unixTimestamp, String iana) {
		Instant i = Instant.ofEpochSecond(unixTimestamp);// UTC
		ZonedDateTime z = ZonedDateTime.ofInstant(i, ZoneId.of(iana));// LOCAL
		// %7 so that sunday is 0, monday is 1, etc
		return z.getDayOfWeek().getValue() % 7;
	}

	public static ZonedDateTime localDateTimeForTimestampSecondsAndIana(long timestampSeconds, String iana) {
		Instant i = Instant.ofEpochSecond(timestampSeconds);// UTC
		ZonedDateTime z = ZonedDateTime.ofInstant(i, ZoneId.of(iana));// LOCAL
		return z;
	}

}
