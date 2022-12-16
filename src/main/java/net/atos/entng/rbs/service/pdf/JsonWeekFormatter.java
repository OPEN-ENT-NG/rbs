package net.atos.entng.rbs.service.pdf;

import fr.wseduc.webutils.I18n;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.joda.time.*;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.joda.time.format.DateTimeFormat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static net.atos.entng.rbs.core.constants.Field.*;
import static net.atos.entng.rbs.core.constants.Field.RESOURCE_ID;
import static net.atos.entng.rbs.model.ExportBooking.*;
import static net.atos.entng.rbs.model.ExportRequest.END_DATE;
import static net.atos.entng.rbs.model.ExportRequest.START_DATE;
import static net.atos.entng.rbs.model.ExportResponse.BOOKINGS;
import static org.joda.time.DateTimeConstants.MONDAY;
import static org.joda.time.DateTimeConstants.SUNDAY;

public class JsonWeekFormatter extends JsonFormatter {
	private static final Logger log = LoggerFactory.getLogger(JsonWeekFormatter.class);

	private final int CALENDAR_HEIGHT = 560;
	private final int CALENDAR_WIDTH = 1000;
	DateTime firstSlotOfADay;
	DateTime lastSlotOfADay;
	int slotNumber;
	double slotHeight;
	double slotWidth;

	public JsonWeekFormatter(JsonObject jsonFileObject, String host, Locale locale, String userTimeZone) {
		super(jsonFileObject, host, locale, userTimeZone);
		firstSlotOfADay = automaticGetFirstSlotOfADay();
		lastSlotOfADay = automaticGetLastSlotOfADay();
		slotNumber = lastSlotOfADay.getHourOfDay() - firstSlotOfADay.getHourOfDay() + 1;
		slotHeight = CALENDAR_HEIGHT / slotNumber;
		slotWidth = round(CALENDAR_WIDTH / 8, DECIMAL_PRECISION);
	}
	
	public JsonObject format() {
		JsonObject convertedObject = new JsonObject();
		convertedObject.put(EDITION_DATE_FIELD_NAME, DateTime.now().toString(DD_MM_YYYY));

		// General calendar settings
		convertedObject.put(CALENDAR_HEIGHT_FIELD_NAME, round(round(slotHeight, DECIMAL_PRECISION) * slotNumber, DECIMAL_PRECISION));
		convertedObject.put(CALENDAR_HEIGHT_UNIT_FIELD_NAME, DEFAULT_SIZE_UNIT);
		convertedObject.put(CALENDAR_WIDTH_FIELD_NAME, CALENDAR_WIDTH);
		convertedObject.put(CALENDAR_WIDTH_UNIT_FIELD_NAME, DEFAULT_SIZE_UNIT);
		convertedObject.put(SLOT_HEIGHT_FIELD_NAME, round(slotHeight, DECIMAL_PRECISION));
		convertedObject.put(SLOT_HEIGHT_UNIT_FIELD_NAME, DEFAULT_SIZE_UNIT);
		convertedObject.put(SLOT_WIDTH_FIELD_NAME, slotWidth);
		convertedObject.put(SLOT_WIDTH_UNIT_FIELD_NAME, DEFAULT_SIZE_UNIT);
		convertedObject.put(I18N_TITLE, I18n.getInstance().translate(MAP_I18N.get(I18N_TITLE), this.host, this.locale));

		DateTime exportStart = toUserTimeZone(exportObject.getString(START_DATE)).withDayOfWeek(MONDAY);
		DateTime exportEnd = toUserTimeZone(exportObject.getString(END_DATE)).withDayOfWeek(SUNDAY).plusDays(1);
		JsonArray exportBookings = exportObject.getJsonArray(BOOKINGS);

		DateTime weekIterator = exportStart;
		JsonArray weekList = new fr.wseduc.webutils.collections.JsonArray();

		while (Weeks.weeksBetween(weekIterator, exportEnd).getWeeks() != 0) {

			JsonObject weekObject = new JsonObject();
			JsonArray slotRawTitle = buildSlotRawTitle(firstSlotOfADay, lastSlotOfADay);
			JsonArray dayList = buildDayList(weekIterator);

			// Building resource list
			ArrayList<Long> performedResourceId = new ArrayList<>();
			JsonArray resourceList = new fr.wseduc.webutils.collections.JsonArray();

			for (int i = 0; i < exportBookings.size(); i++) {
				JsonObject bookingIterator = exportBookings.getJsonObject(i);
				Long currentResourceId = bookingIterator.getLong(RESOURCE_ID);

				if (!performedResourceId.contains(currentResourceId)) { // New resource found
					performedResourceId.add(bookingIterator.getLong(RESOURCE_ID));
					JsonObject resource = new JsonObject();
					resource.put(RESOURCE_ID, currentResourceId);
					resource.put(RESOURCE_NAME, bookingIterator.getString(RESOURCE_NAME));
					resource.put(RESOURCE_COLOR, bookingIterator.getString(RESOURCE_COLOR));
					resource.put(SCHOOL_NAME, bookingIterator.getString(SCHOOL_NAME));
					resource.put(I18N_FOOTER, I18n.getInstance().translate(MAP_I18N.get(I18N_FOOTER), this.host, this.locale));
					resource.put(SLOT_RAW_TITLE_FIELD_NAME, slotRawTitle);
					resource.put(DAYS_FIELD_NAME, dayList);

					// Adding resource bookings
					JsonArray bookingList = new JsonArray();
					for (int j = 0; j < exportBookings.size(); j++) {
						JsonObject exportBooking = exportBookings.getJsonObject(j);
						if (exportBooking.getLong(RESOURCE_ID).equals(currentResourceId)) { // This booking belongs to the current resource
							treatBooking(exportBooking, firstSlotOfADay, lastSlotOfADay, weekIterator, exportBookings, bookingList);
						}
					}

					resource.put(BOOKING_LIST_FIELD_NAME, bookingList);
					resourceList.add(resource);
				}
			}
			weekObject.put(RESOURCES_FIELD_NAME, resourceList);
			weekList.add(weekObject);
			weekIterator = weekIterator.plusWeeks(1);
		}

		convertedObject.put(WEEK_LIST_FIELD_NAME, weekList);

		return convertedObject;
	}


	private DateTime automaticGetFirstSlotOfADay() {
		// HARDCODED : First slot begins at 7h
		return new DateTime(1, 1, 1, 7, 0);
	}

	private DateTime automaticGetLastSlotOfADay() {
		// HARDCODED : Last slot ends at 20h
		return new DateTime(1, 1, 1, 20, 0);
	}

	/**
	 * Build the slot raw title list
	 *
	 * @param firstSlotOfADay: First time slot begin time
	 * @param lastSlotOfADay:  Last time slot end time
	 * @return The slot raw title list
	 */
	private JsonArray buildSlotRawTitle(DateTime firstSlotOfADay, DateTime lastSlotOfADay) {
		JsonArray slotRawTitle = new fr.wseduc.webutils.collections.JsonArray();
		for (int i = firstSlotOfADay.getHourOfDay(); i < lastSlotOfADay.getHourOfDay(); i++)
			slotRawTitle.add(new JsonObject().put(VALUE, i + ":00 - " + (i + 1) + ":00"));
		return slotRawTitle;
	}

	/**
	 * Build the week day list
	 *
	 * @param startDate: Date in the given week
	 * @return The week day list in which the start date is contained in
	 */
	private JsonArray buildDayList(DateTime startDate) {
		JsonArray dayList = new fr.wseduc.webutils.collections.JsonArray();
		DateTime firstDayOfWeek = startDate.withDayOfWeek(MONDAY);

		for (int i = 0; i < 7; i++) {
			JsonObject oneDay = new JsonObject();
			String dayName = DateTimeFormat.forPattern("EEEE").withLocale(this.locale).print(firstDayOfWeek.plusDays(i));
			dayName = dayName.substring(0, 1).toUpperCase() + dayName.substring(1);
			oneDay.put(NAME, dayName);
			oneDay.put(DATE, firstDayOfWeek.plusDays(i).toString(DD_MM_YYYY));
			dayList.add(oneDay);
		}
		return dayList;
	}


	private void treatBooking(JsonObject exportBooking, DateTime firstSlotOfADay, DateTime lastSlotOfADay,
							  DateTime weekIterator, JsonArray exportBookings, JsonArray bookingList) {
		// Split booking into several bookings if booked on several days
		DateTime exportBookingStartDate = toUserTimeZone(exportBooking.getString(BOOKING_START_DATE));
		DateTime exportBookingEndDate = toUserTimeZone(exportBooking.getString(BOOKING_END_DATE));
		int daysBetweenStartAndEnd = Days.daysBetween(exportBookingStartDate, exportBookingEndDate).getDays();

		List<JsonObject> siblings = getSiblings(exportBooking, exportBookings);
		int nbMaxBookingPerSlot = getNbMaxSiblings(siblings) + 1;

		for (int k = 0; k != Math.abs(daysBetweenStartAndEnd) + 1; k++) {

			DateTime dayStartDate = exportBookingStartDate.plusDays(k).hourOfDay().setCopy(firstSlotOfADay.getHourOfDay());
			dayStartDate = dayStartDate.minuteOfHour().setCopy(0);
			DateTime dayEndDate = exportBookingStartDate.plusDays(k).hourOfDay().setCopy(lastSlotOfADay.getHourOfDay());
			dayEndDate = dayEndDate.minuteOfHour().setCopy(0);

			DateTime bookingStartDate = (exportBookingStartDate.isBefore(dayStartDate)) ? dayStartDate : exportBookingStartDate;
			DateTime bookingEndDate = (exportBookingEndDate.isAfter(dayEndDate)) ? dayEndDate : exportBookingEndDate;

			if (bookingStartDate.getWeekyear() == weekIterator.getWeekyear() &&
					bookingStartDate.getWeekOfWeekyear() == weekIterator.getWeekOfWeekyear() &&
					bookingStartDate.getEra() == weekIterator.getEra()) {

				JsonObject booking = new JsonObject();

				int dayDurationInSecond = Seconds.secondsBetween(dayStartDate, dayEndDate).getSeconds();
				int bookingOffsetFromStart = Seconds.secondsBetween(dayStartDate, bookingStartDate).getSeconds();
				int bookingDuration = Seconds.secondsBetween(bookingStartDate, bookingEndDate).getSeconds();

				String classId = "booking-" + exportBooking.getInteger(BOOKING_ID) + "-" + (k + 1);
				int slotIndex = getSlotIndex(bookingStartDate, bookingEndDate, siblings);
				double topOffset = 48 + slotHeight * (slotNumber - 1) * bookingOffsetFromStart / dayDurationInSecond;
				double bookingHeight = slotHeight * (slotNumber - 1) * bookingDuration / dayDurationInSecond - 2;
				double bookingWidth = (slotWidth - 2) / nbMaxBookingPerSlot;
				double leftOffest = slotWidth * bookingStartDate.getDayOfWeek() + (slotIndex * bookingWidth);

				exportBooking.put(SLOT_INDEX, slotIndex);
				booking.put(BOOKING_OWNER_NAME, exportBooking.getString(BOOKING_OWNER_NAME));
				booking.put(QUANTITY, exportBooking.getLong(QUANTITY).toString());
				booking.put(BOOKING_CLASS_ID_FIELD_NAME, classId);
				booking.put(BOOKING_TOP_FIELD_NAME, round(topOffset, DECIMAL_PRECISION));
				booking.put(BOOKING_TOP_UNIT_FIELD_NAME, DEFAULT_SIZE_UNIT);
				booking.put(BOOKING_LEFT_FIELD_NAME, leftOffest);
				booking.put(BOOKING_LEFT_UNIT_FIELD_NAME, DEFAULT_SIZE_UNIT);
				booking.put(BOOKING_HEIGHT_FIELD_NAME, round(bookingHeight, DECIMAL_PRECISION));
				booking.put(BOOKING_HEIGHT_UNIT_FIELD_NAME, DEFAULT_SIZE_UNIT);
				booking.put(BOOKING_WIDTH_FIELD_NAME, round(bookingWidth, DECIMAL_PRECISION));
				booking.put(BOOKING_WIDTH_UNIT_FIELD_NAME, DEFAULT_SIZE_UNIT);
				booking.put(SLOT_INDEX, slotIndex);

				bookingList.add(booking);
			}
		}
	}
}
