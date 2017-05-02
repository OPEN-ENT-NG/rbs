package net.atos.entng.rbs.test.units;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.zone.ZoneOffsetTransition;
import java.time.zone.ZoneRules;

import org.junit.Assert;
import org.junit.Test;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import net.atos.entng.rbs.models.Booking;
import net.atos.entng.rbs.models.BookingDateUtils;
import net.atos.entng.rbs.models.Resource;
import net.atos.entng.rbs.models.Slot;
import net.atos.entng.rbs.models.Slots;
import net.atos.entng.rbs.models.Slot.SlotIterable;

public class BookingTest {
	static DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
	static final String IANA_PARIS = "Europe/Paris";

	static long nowToTimestampSeconds(String zoneID) {
		ZonedDateTime dateTime = ZonedDateTime.now((ZoneId.of(zoneID)));
		return dateTime.toEpochSecond();
	}

	static long toTimestampSeconds(ZonedDateTime dateTime, String zoneID) {
		return dateTime.toEpochSecond();
	}

	static long toTimestampSeconds(String str, String zoneID) {
		ZonedDateTime dateTime = ZonedDateTime.parse(str, formatter.withZone(ZoneId.of(zoneID)));
		return dateTime.toEpochSecond();
	}

	static String fromTimestampSeconds(long str, String zoneID) {
		Instant i = Instant.ofEpochSecond(str);
		ZonedDateTime z = ZonedDateTime.ofInstant(i, ZoneId.of(zoneID));
		return formatter.format(z);
	}

	static ZonedDateTime fromTimestampSecondsAsZoned(long str, String zoneID) {
		Instant i = Instant.ofEpochSecond(str);
		ZonedDateTime z = ZonedDateTime.ofInstant(i, ZoneId.of(zoneID));
		return z;
	}

	@Test
	public void shouldComputeLastSlotForPeriodicBookingWithEndDate() {
		JsonObject jsonR = new JsonObject();
		JsonObject jsonB = new JsonObject();
		JsonArray jsonS = new JsonArray();
		jsonS.add(new JsonObject().put("start_date","01/07/2018 15:00").put("end_date","01/07/2018 16:00").put("iana",IANA_PARIS));
		jsonB.put("periodicity", 1);// every 1 weeks
		jsonB.put("iana", IANA_PARIS);
		jsonB.put("periodic_end_date", toTimestampSeconds("31/07/2018 16:00", IANA_PARIS));
		jsonB.put("days", new JsonArray("[true,false,false,false,false,false,true]"));// sunday and saturday
		Booking booking = new Booking(jsonB, new Resource(jsonR));
		Slots slots = new Slots(jsonS);
		booking.setSlots(slots);
		//

		Assert.assertFalse(slots.areNotStartingAndEndingSameDay());
		Assert.assertFalse(booking.isNotPeriodic());
		Assert.assertFalse(booking.hasPeriodicEndAsLastDay());// 31-07 is not last
		Assert.assertFalse(booking.hasNotSelectedDays());
		Assert.assertFalse(booking.hasNotSelectedStartDayOfWeek());
		Assert.assertTrue(booking.hasPeriodicEndDate());

		Assert.assertEquals("1000001", booking.getSelectedDaysBitString());
		Assert.assertEquals(30, booking.daysBetweenFirstSlotEndAndPeriodicEndDate(slots.getSlotWithLatestEndDate()));
		Assert.assertEquals(9, booking.countOccurrences(slots.getSlotWithLatestEndDate()));
		long lastSlot = booking.getLastSlotDate(booking.countOccurrences(slots.getSlotWithLatestEndDate()));
		Assert.assertEquals("29/07/2018 16:00", fromTimestampSeconds(lastSlot, IANA_PARIS));
		lastSlot = booking.computeAndSetLastEndDateAsUTCSedonds();
		Assert.assertEquals("29/07/2018 16:00", fromTimestampSeconds(lastSlot, IANA_PARIS));
		Assert.assertEquals(9, booking.getOccurrences(0));
		Assert.assertEquals("29/07/2018 16:00",
				fromTimestampSeconds(booking.getPeriodicEndDateAsUTCSeconds(), IANA_PARIS));
		//
		SlotIterable it = new SlotIterable(booking, booking.getSlots().get(0));
		int count = 0;
		for (Slot s : it) {
			Assert.assertTrue(s.getSelectedDay() == 0 || s.getSelectedDay() == 6);
			count++;
		}
		Assert.assertEquals(8, count);
		Assert.assertEquals(28, it.getNbDaysFromBeginning());
	}

	@Test
	public void shouldComputeLastSlotForPeriodicBookingWithEndDateAsLastDay() {
		JsonObject jsonR = new JsonObject();
		JsonObject jsonB = new JsonObject();
		JsonArray jsonS = new JsonArray();
		jsonS.add(new JsonObject().put("start_date","01/07/2018 15:00").put("end_date","01/07/2018 16:00").put("iana",IANA_PARIS));
		jsonB.put("periodicity", 1);// every 1 weeks
		jsonB.put("iana", IANA_PARIS);
		jsonB.put("periodic_end_date", toTimestampSeconds("29/07/2018 16:00", IANA_PARIS));
		jsonB.put("days", new JsonArray("[true,false,false,false,false,true,false]"));// sunday and saturday
		Booking booking = new Booking(jsonB, new Resource(jsonR));
		Slots slots = new Slots(jsonS);
		booking.setSlots(slots);
		//
		Assert.assertFalse(slots.areNotStartingAndEndingSameDay());
		Assert.assertFalse(booking.isNotPeriodic());
		Assert.assertTrue(booking.hasPeriodicEndAsLastDay());// 29-07 is last
		Assert.assertFalse(booking.hasNotSelectedDays());
		Assert.assertFalse(booking.hasNotSelectedStartDayOfWeek());
		Assert.assertTrue(booking.hasPeriodicEndDate());

		Assert.assertEquals("1000010", booking.getSelectedDaysBitString());
		Assert.assertEquals(28, booking.daysBetweenFirstSlotEndAndPeriodicEndDate(slots.getSlotWithLatestEndDate()));
		Assert.assertEquals(9, booking.countOccurrences(slots.getSlotWithLatestEndDate()));
		long lastSlot = booking.getLastSlotDate(9);
		Assert.assertEquals("29/07/2018 16:00", fromTimestampSeconds(lastSlot, IANA_PARIS));
		lastSlot = booking.computeAndSetLastEndDateAsUTCSedonds();
		Assert.assertEquals("29/07/2018 16:00", fromTimestampSeconds(lastSlot, IANA_PARIS));
		Assert.assertEquals(0, booking.getOccurrences(0));// occurences not setted
		Assert.assertEquals("29/07/2018 16:00",
				fromTimestampSeconds(booking.getPeriodicEndDateAsUTCSeconds(), IANA_PARIS));
		//
		SlotIterable it = new SlotIterable(booking, booking.getSlots().get(0));
		int count = 0;
		for (Slot s : it) {
			Assert.assertTrue(s.getSelectedDay() == 0 || s.getSelectedDay() == 5);
			count++;
		}
		Assert.assertEquals(8, count);
		Assert.assertEquals(28, it.getNbDaysFromBeginning());
	}

	@Test
	public void shouldComputeLastSlotForPeriodicBookingEvery3Weeks() {
		JsonObject jsonR = new JsonObject();
		JsonObject jsonB = new JsonObject();
		JsonArray jsonS = new JsonArray();
		jsonS.add(new JsonObject().put("start_date","01/07/2018 15:00").put("end_date","01/07/2018 16:00").put("iana",IANA_PARIS));
		jsonB.put("periodicity", 3);// every 3 weeks
		jsonB.put("iana", IANA_PARIS);
		jsonB.put("start_date", toTimestampSeconds("01/07/2018 15:00", IANA_PARIS));
		jsonB.put("end_date", toTimestampSeconds("01/07/2018 16:00", IANA_PARIS));
		jsonB.put("periodic_end_date", toTimestampSeconds("31/07/2018 16:00", IANA_PARIS));
		jsonB.put("days", new JsonArray("[true,false,false,false,false,false,true]"));// sunday and saturday
		Booking booking = new Booking(jsonB, new Resource(jsonR));
		Slots slots = new Slots(jsonS);
		booking.setSlots(slots);
		//
		Assert.assertFalse(slots.areNotStartingAndEndingSameDay());
		Assert.assertFalse(booking.isNotPeriodic());
		Assert.assertFalse(booking.hasPeriodicEndAsLastDay());// 31-07 is not last
		Assert.assertFalse(booking.hasNotSelectedDays());
		Assert.assertFalse(booking.hasNotSelectedStartDayOfWeek());
		Assert.assertTrue(booking.hasPeriodicEndDate());

		Assert.assertEquals("1000001", booking.getSelectedDaysBitString());
		Assert.assertEquals(30, booking.daysBetweenFirstSlotEndAndPeriodicEndDate(slots.getSlotWithLatestEndDate()));
		Assert.assertEquals(3, booking.countOccurrences(slots.getSlotWithLatestEndDate()));
		long lastSlot = booking.getLastSlotDate(booking.countOccurrences(slots.getSlotWithLatestEndDate()));
		Assert.assertEquals("22/07/2018 16:00", fromTimestampSeconds(lastSlot, IANA_PARIS));
		lastSlot = booking.computeAndSetLastEndDateAsUTCSedonds();
		Assert.assertEquals("22/07/2018 16:00", fromTimestampSeconds(lastSlot, IANA_PARIS));
		Assert.assertEquals(3, booking.getOccurrences(0));
		Assert.assertEquals("22/07/2018 16:00",
				fromTimestampSeconds(booking.getPeriodicEndDateAsUTCSeconds(), IANA_PARIS));
		//
		SlotIterable it = new SlotIterable(booking,booking.getSlots().get(0));
		int count = 0;
		for (Slot s : it) {
			Assert.assertTrue(s.getSelectedDay() == 0 || s.getSelectedDay() == 6);
			count++;
		}
		Assert.assertEquals(2, count);
		Assert.assertEquals(21, it.getNbDaysFromBeginning());
	}

	@Test
	public void shouldComputeLastSlotForPeriodicBookingWithOccurrencesCount() {
		JsonObject jsonR = new JsonObject();
		JsonObject jsonB = new JsonObject();
		JsonArray jsonS = new JsonArray();
		jsonS.add(new JsonObject().put("start_date","01/07/2018 15:00").put("end_date","01/07/2018 16:00").put("iana",IANA_PARIS));
		jsonB.put("periodicity", 1);// every 1 weeks
		jsonB.put("iana", IANA_PARIS);
		jsonB.put("start_date", toTimestampSeconds("01/07/2018 15:00", IANA_PARIS));
		jsonB.put("end_date", toTimestampSeconds("01/07/2018 16:00", IANA_PARIS));
		jsonB.put("occurrences", 10);
		jsonB.put("days", new JsonArray("[true,false,false,false,false,false,true]"));// sunday and saturday
		Booking booking = new Booking(jsonB, new Resource(jsonR));
		Slots slots = new Slots(jsonS);
		booking.setSlots(slots);
		//
		Assert.assertFalse(slots.areNotStartingAndEndingSameDay());
		Assert.assertFalse(booking.isNotPeriodic());
		Assert.assertFalse(booking.hasPeriodicEndAsLastDay());// 31-07 is not last
		Assert.assertFalse(booking.hasNotSelectedDays());
		Assert.assertFalse(booking.hasNotSelectedStartDayOfWeek());
		Assert.assertFalse(booking.hasPeriodicEndDate());

		Assert.assertEquals("1000001", booking.getSelectedDaysBitString());
		Assert.assertEquals(10, booking.getOccurrences(0));
		long lastSlot = booking.getLastSlotDate(booking.getOccurrences(0));
		Assert.assertEquals("04/08/2018 16:00", fromTimestampSeconds(lastSlot, IANA_PARIS));
		lastSlot = booking.computeAndSetLastEndDateAsUTCSedonds();
		Assert.assertEquals("04/08/2018 16:00", fromTimestampSeconds(lastSlot, IANA_PARIS));
		Assert.assertEquals(0, (booking.getPeriodicEndDateAsUTCSeconds()));
	}

	@Test
	public void shouldNotComputeSlotsWithOneOccurrence() {
		JsonObject jsonR = new JsonObject();
		JsonObject jsonB = new JsonObject();
		JsonArray jsonS = new JsonArray();
		jsonS.add(new JsonObject().put("start_date","01/07/2018 15:00").put("end_date","01/07/2018 16:00").put("iana",IANA_PARIS));
		jsonB.put("periodicity", 1);// every 1 weeks
		jsonB.put("iana", IANA_PARIS);
		jsonB.put("start_date", toTimestampSeconds("01/07/2018 15:00", IANA_PARIS));
		jsonB.put("end_date", toTimestampSeconds("01/07/2018 16:00", IANA_PARIS));
		jsonB.put("occurrences", 1);
		jsonB.put("days", new JsonArray("[true,false,false,false,false,false,true]"));// sunday and saturday
		Booking booking = new Booking(jsonB, new Resource(jsonR));
		booking.setSlots(new Slots(jsonS));
		//
		//
		SlotIterable it = new SlotIterable(booking, booking.getSlots().get(0));
		int count = 0;
		for (@SuppressWarnings("unused")
		Slot s : it) {
			count++;
		}
		Assert.assertEquals(0, count);
		Assert.assertEquals(0, it.getNbDaysFromBeginning());
	}

	@Test
	public void shouldNotComputeSlotsWithZeroOccurrence() {
		JsonObject jsonR = new JsonObject();
		JsonObject jsonB = new JsonObject();
		JsonArray jsonS = new JsonArray();
		jsonS.add(new JsonObject().put("start_date","01/07/2018 15:00").put("end_date","01/07/2018 16:00").put("iana",IANA_PARIS));
		jsonB.put("periodicity", 1);// every 1 weeks
		jsonB.put("iana", IANA_PARIS);
		jsonB.put("start_date", toTimestampSeconds("01/07/2018 15:00", IANA_PARIS));
		jsonB.put("end_date", toTimestampSeconds("01/07/2018 16:00", IANA_PARIS));
		jsonB.put("occurrences", 0);
		jsonB.put("days", new JsonArray("[true,false,false,false,false,false,true]"));// sunday and saturday
		Booking booking = new Booking(jsonB, new Resource(jsonR));
		booking.setSlots(new Slots(jsonS));
		//
		//
		SlotIterable it = new SlotIterable(booking, booking.getSlots().get(0));
		int count = 0;
		for (@SuppressWarnings("unused")
		Slot s : it) {
			count++;
		}
		Assert.assertEquals(0, count);
		Assert.assertEquals(0, it.getNbDaysFromBeginning());
	}

	@Test
	public void shouldComputeLastSlotForNonPeriodicBooking() {
		JsonObject jsonR = new JsonObject();
		JsonObject jsonB = new JsonObject();
		JsonArray jsonS = new JsonArray();
		jsonS.add(new JsonObject().put("start_date","01/07/2018 15:00").put("end_date","01/07/2018 16:00").put("iana",IANA_PARIS));
		jsonB.put("iana", IANA_PARIS);
		Booking booking = new Booking(jsonB, new Resource(jsonR));
		Slots slots = new Slots(jsonS);
		booking.setSlots(slots);
		//
		Assert.assertFalse(slots.areNotStartingAndEndingSameDay());
		Assert.assertTrue(booking.isNotPeriodic());
		Assert.assertTrue(booking.hasNotSelectedDays());
		Assert.assertTrue(booking.hasNotSelectedStartDayOfWeek());
		Assert.assertFalse(booking.hasPeriodicEndDate());
		Assert.assertEquals(0, booking.getOccurrences(0));
		long lastSlot = booking.getLastSlotDate(booking.getOccurrences(0));
		Assert.assertEquals("01/07/2018 16:00", fromTimestampSeconds(lastSlot, IANA_PARIS));
		lastSlot = booking.computeAndSetLastEndDateAsUTCSedonds();
		Assert.assertEquals("01/07/2018 16:00", fromTimestampSeconds(lastSlot, IANA_PARIS));
		Assert.assertEquals(0, (booking.getPeriodicEndDateAsUTCSeconds()));
	}

	@Test
	public void shouldReserveAccordingMinDelay() {
		JsonObject jsonR = new JsonObject();
		jsonR.put("min_delay", BookingDateUtils.daysToSecond(2));// should reserve at least 2 days before
		JsonObject jsonB = new JsonObject();
		jsonB.put("iana", IANA_PARIS);
		Booking booking = new Booking(jsonB, new Resource(jsonR));
		long now = BookingDateUtils.currentTimestampSecondsForIana(IANA_PARIS);
		ZonedDateTime nowZoned = ZonedDateTime.ofInstant(Instant.ofEpochSecond(now), ZoneId.of(IANA_PARIS));
		// 3 days before
		ZonedDateTime nowMinus3 = nowZoned.plusDays(3);
		jsonB.put("start_date", nowMinus3.toInstant().getEpochSecond());
		Assert.assertFalse(booking.slotsNotRespectingMinDelay());
		// 2 days before
		ZonedDateTime nowMinus2 = nowZoned.plusDays(2);
		jsonB.put("start_date", nowMinus2.toInstant().getEpochSecond());
		Assert.assertFalse(booking.slotsNotRespectingMinDelay());
		// 1 days before
		ZonedDateTime nowMinus1 = nowZoned.plusDays(1);
		jsonB.put("start_date", nowMinus1.toInstant().getEpochSecond());
		Assert.assertTrue(booking.slotsNotRespectingMinDelay());
	}

	@Test
	public void shouldReserveAccordingMaxDelayForNonPeriodicBooking() {
		JsonObject jsonR = new JsonObject();
		jsonR.put("max_delay", BookingDateUtils.daysToSecond(2));// should reserve at max 2 days before
		JsonObject jsonB = new JsonObject();
		jsonB.put("iana", IANA_PARIS);
		Booking booking = new Booking(jsonB, new Resource(jsonR));
		long now = BookingDateUtils.currentTimestampSecondsForIana(IANA_PARIS);
		ZonedDateTime nowZoned = ZonedDateTime.ofInstant(Instant.ofEpochSecond(now), ZoneId.of(IANA_PARIS));
		// 3 days before
		ZonedDateTime nowMinus3 = nowZoned.plusDays(3);
		jsonB.put("end_date", nowMinus3.toInstant().getEpochSecond());
		Assert.assertTrue(booking.slotsNotRespectingMaxDelay());
		// 2 days before
		ZonedDateTime nowMinus2 = nowZoned.plusDays(2);
		jsonB.put("end_date", nowMinus2.toInstant().getEpochSecond());
		Assert.assertFalse(booking.slotsNotRespectingMaxDelay());
		// 1 days before
		ZonedDateTime nowMinus1 = nowZoned.plusDays(1);
		jsonB.put("end_date", nowMinus1.toInstant().getEpochSecond());
		Assert.assertFalse(booking.slotsNotRespectingMaxDelay());
	}

	@Test
	public void shouldReserveAccordingMaxDelayForPeriodicBooking() {
		JsonObject jsonR = new JsonObject();
		JsonArray jsonS = new JsonArray();
		jsonS.add(new JsonObject().put("start_date","01/07/2018 15:00").put("end_date","01/07/2018 16:00").put("iana",IANA_PARIS));
		jsonR.put("max_delay", BookingDateUtils.daysToSecond(2));// should reserve at max 2 days before
		JsonObject jsonB = new JsonObject();
		jsonB.put("periodicity", 1);// every 1 weeks
		jsonB.put("iana", IANA_PARIS);
		jsonB.put("start_date", nowToTimestampSeconds(IANA_PARIS));
		jsonB.put("end_date", nowToTimestampSeconds(IANA_PARIS));
		jsonB.put("occurrences", 4);
		jsonB.put("days", new JsonArray("[true,true,true,true,true,true,true]"));// everyday
		Booking booking = new Booking(jsonB, new Resource(jsonR));
		Slots slots = new Slots(jsonS);
		booking.setSlots(slots);
		System.out.println(booking.daysBetweenFirstSlotEndAndPeriodicEndDate(slots.getSlotWithLatestEndDate()));
		// more or less 4 days later
		Assert.assertTrue(booking.slotsNotRespectingMaxDelay());
		// Augment max delay
		jsonR.put("max_delay", BookingDateUtils.daysToSecond(4));// should reserve at max 4 days before
		Assert.assertFalse(booking.slotsNotRespectingMaxDelay());
	}

	@Test
	public void shouldBookPeriodicEventWithNextDSTTransition() {
		ZoneId zoneId = ZoneId.of(IANA_PARIS);
		ZoneRules rules = zoneId.getRules();
		ZoneOffsetTransition nextTransition = rules.nextTransition(Instant.now());
		ZonedDateTime nextTransitionDate = nextTransition.getInstant().atZone(zoneId);
		System.out.println("Next transition at: " + nextTransitionDate);
		//
		ZonedDateTime startDate = nextTransitionDate.plusDays(-2).withHour(10);
		//
		JsonObject jsonR = new JsonObject();
		JsonObject jsonB = new JsonObject();
		jsonB.put("periodicity", 1);// every 1 weeks
		jsonB.put("iana", IANA_PARIS);
		jsonB.put("start_date", toTimestampSeconds(startDate, IANA_PARIS));
		jsonB.put("end_date", toTimestampSeconds(startDate.plusHours(1), IANA_PARIS));
		jsonB.put("periodic_end_date", toTimestampSeconds(startDate.plusDays(4), IANA_PARIS));
		jsonB.put("days", new JsonArray("[true,true,true,true,true,true,true]"));// everyday
		Booking booking = new Booking(jsonB, new Resource(jsonR));
		//
		SlotIterable it = new SlotIterable(booking,booking.getSlots().get(0));
		int nbBefore = 0;
		int nbAfter = 0;
		for (Slot slot : it) {
			Assert.assertEquals(10, slot.getStart().getHour());
			Assert.assertEquals(11, slot.getEnd().getHour());
			if (slot.getStart().isBefore(nextTransitionDate)) {
				nbBefore++;
			}
			if (slot.getStart().isAfter(nextTransitionDate)) {
				nbAfter++;
			}
		}
		System.out.println("Nb before/after : " + nbBefore + "/" + nbAfter);
		Assert.assertNotEquals(0, nbBefore);
		Assert.assertNotEquals(0, nbAfter);
	}

	// Test both transition
	@Test
	public void shouldBookPeriodicEventWithNextNextDSTTransition() {
		JsonArray jsonS = new JsonArray();
		jsonS.add(new JsonObject().put("start_date","01/07/2018 15:00").put("end_date","01/07/2018 16:00").put("iana",IANA_PARIS));
		ZoneId zoneId = ZoneId.of(IANA_PARIS);
		ZoneRules rules = zoneId.getRules();
		ZoneOffsetTransition nextTransition = rules.nextTransition(Instant.now());
		ZoneOffsetTransition nextNextTransition = rules.nextTransition(nextTransition.getInstant());
		ZonedDateTime nextNextTransitionDate = nextNextTransition.getInstant().atZone(zoneId);
		System.out.println("Next next transition at: " + nextNextTransitionDate);
		System.out.println("Next transition at: " + nextNextTransitionDate);
		//
		ZonedDateTime startDate = nextNextTransitionDate.plusDays(-2).withHour(10);
		//
		JsonObject jsonR = new JsonObject();
		JsonObject jsonB = new JsonObject();
		jsonB.put("periodicity", 1);// every 1 weeks
		jsonB.put("iana", IANA_PARIS);
		jsonB.put("start_date", toTimestampSeconds(startDate, IANA_PARIS));
		jsonB.put("end_date", toTimestampSeconds(startDate.plusHours(1), IANA_PARIS));
		jsonB.put("periodic_end_date", toTimestampSeconds(startDate.plusDays(4), IANA_PARIS));
		jsonB.put("days", new JsonArray("[true,true,true,true,true,true,true]"));// everyday
		Booking booking = new Booking(jsonB, new Resource(jsonR));
		booking.setSlots(new Slots(jsonS));
		//
		SlotIterable it = new SlotIterable(booking, booking.getSlots().get(0));
		int nbBefore = 0;
		int nbAfter = 0;
		for (Slot slot : it) {
			Assert.assertEquals(10, slot.getStart().getHour());
			Assert.assertEquals(11, slot.getEnd().getHour());
			if (slot.getStart().isBefore(nextNextTransitionDate)) {
				nbBefore++;
			}
			if (slot.getStart().isAfter(nextNextTransitionDate)) {
				nbAfter++;
			}
		}
		System.out.println("Nb before/after : " + nbBefore + "/" + nbAfter);
		Assert.assertNotEquals(0, nbBefore);
		Assert.assertNotEquals(0, nbAfter);
	}
}
