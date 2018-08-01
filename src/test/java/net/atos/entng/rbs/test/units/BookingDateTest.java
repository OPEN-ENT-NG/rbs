package net.atos.entng.rbs.test.units;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.zone.ZoneOffsetTransition;
import java.time.zone.ZoneRules;
import java.util.Date;

import org.junit.Assert;
import org.junit.Test;

import net.atos.entng.rbs.models.BookingDateUtils;

public class BookingDateTest {
	static final String IANA_PARIS = "Europe/Paris";

	@Test
	public void test() {
		ZoneId zoneId = ZoneId.of(IANA_PARIS);
		ZoneRules rules = zoneId.getRules();
		ZoneOffsetTransition nextTransition = rules.nextTransition(Instant.now());
		Instant transition = nextTransition.getInstant();
		ZonedDateTime before = nextTransition.getInstant().atZone(ZoneId.of(IANA_PARIS)).plusDays(-1);
		ZonedDateTime after = nextTransition.getInstant().atZone(ZoneId.of(IANA_PARIS)).plusDays(1);
		System.out.println("Before transition : " + before);
		System.out.println("Before transition no zoned : " + Date.from(before.toOffsetDateTime().toInstant()));
		System.out.println("Before UTC: " + before.toEpochSecond());
		// moment.utc(...).tz("Europe/paris").format("DD/MM/YYYY HH:mm")
		System.out.println("Before UTC 2:00: " + before.toLocalDateTime().withHour(2).withMinute(0));
		System.out.println("Before UTC 2:00 at offset utc: "
				+ before.toLocalDateTime().withHour(2).withMinute(0).toEpochSecond(ZoneOffset.UTC));
		// => moment.utc(...).format("DD/MM/YYYY HH:mm")
		long beforeUTCTimestamp = before.toEpochSecond();
		System.out.println("Before transition reverse UTC: "
				+ ZonedDateTime.ofInstant(Instant.ofEpochSecond(beforeUTCTimestamp), ZoneId.of(IANA_PARIS)));
		System.out.println("Transition : " + transition);
		System.out.println("After transition : " + after);
		System.out.println("After UTC : " + after.toEpochSecond());
		// moment.utc(...).tz("Europe/paris").format("DD/MM/YYYY HH:mm")
		System.out.println("After UTC 2:00: " + after.toLocalDateTime().withHour(2).withMinute(0));
		System.out.println("After UTC 2:00 at offset utc: "
				+ after.toLocalDateTime().withHour(2).withMinute(0).toEpochSecond(ZoneOffset.UTC));
		// => moment.utc(...).format("DD/MM/YYYY HH:mm")
	}

	@Test
	public void shouldAddDayIgnoreDST() {
		ZoneId zoneId = ZoneId.of(IANA_PARIS);
		ZoneRules rules = zoneId.getRules();
		ZoneOffsetTransition nextTransition = rules.nextTransition(Instant.now());
		Instant transition = nextTransition.getInstant();
		ZonedDateTime before = nextTransition.getInstant().atZone(ZoneId.of(IANA_PARIS)).plusDays(-1);
		before = before.withHour(10).withMinute(0);
		ZonedDateTime after = BookingDateUtils.addDaysIgnoreDST(before, 2);
		Assert.assertTrue(before.toInstant().isBefore(transition));
		Assert.assertTrue(after.toInstant().isAfter(transition));
		Assert.assertEquals(10, after.getHour());
		Assert.assertEquals(0, after.getMinute());
	}
}
