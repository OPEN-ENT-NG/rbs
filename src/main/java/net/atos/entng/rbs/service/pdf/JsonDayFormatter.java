package net.atos.entng.rbs.service.pdf;

import fr.wseduc.webutils.I18n;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.joda.time.DateTime;
import org.joda.time.Days;
import org.joda.time.Seconds;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.joda.time.format.DateTimeFormat;

import java.util.*;

import static net.atos.entng.rbs.core.constants.Field.*;
import static net.atos.entng.rbs.core.constants.Field.RESOURCE_ID;
import static net.atos.entng.rbs.model.ExportBooking.*;
import static net.atos.entng.rbs.model.ExportRequest.END_DATE;
import static net.atos.entng.rbs.model.ExportRequest.START_DATE;

public class JsonDayFormatter extends JsonFormatter {
	private static final Logger log = LoggerFactory.getLogger(JsonDayFormatter.class);

	private final int CALENDAR_HEIGHT = 900;
	private final int CALENDAR_WIDTH = 680;
	private final DateTime firstSlotOfADay;
	private final DateTime lastSlotOfADay;
	private final int slotNumber;
	private final double slotHeight;
	private final double slotWidth;

	public JsonDayFormatter(JsonObject jsonFileObject, String host, Locale locale, String userTimeZone) {
		super(jsonFileObject, host, locale, userTimeZone);
		firstSlotOfADay = automaticGetFirstSlotOfADay();
		lastSlotOfADay = automaticGetLastSlotOfADay();
		slotNumber = lastSlotOfADay.getHourOfDay() - firstSlotOfADay.getHourOfDay() + 1;
		slotHeight = CALENDAR_HEIGHT / slotNumber;
		slotWidth = round(CALENDAR_WIDTH - 125, DECIMAL_PRECISION);
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

		DateTime exportStart = toUserTimeZone(exportObject.getString(START_DATE));
		DateTime exportEnd = toUserTimeZone(exportObject.getString(END_DATE)).plusDays(1);
		JsonArray exportBookings = exportObject.getJsonArray(BOOKING_LIST_FIELD_NAME);

		DateTime dayIterator = exportStart;
		JsonArray dayList = new JsonArray();

		// Loop on each day of the export
		while (dayIterator.getDayOfYear() != exportEnd.getDayOfYear() ||
				dayIterator.getYear() != exportEnd.getYear() ||
				dayIterator.getEra() != exportEnd.getEra()) {

			JsonObject dayObject = new JsonObject();
			JsonArray slotRawTitles = buildSlotRawTitles(firstSlotOfADay, lastSlotOfADay);

			// Building resource list
			ArrayList<Long> performedResourceId = new ArrayList<>();
			JsonArray resourceList = new JsonArray();

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
					resource.put(SLOT_RAW_TITLE_FIELD_NAME, slotRawTitles);

					// Adding resource bookings
					JsonArray bookingList = new JsonArray();
					for (int j = 0; j < exportBookings.size(); j++) {
						JsonObject exportBooking = exportBookings.getJsonObject(j);
						if (exportBooking.getLong(RESOURCE_ID).equals(currentResourceId)) { // This booking belongs to the current resource
							treatBooking(exportBooking, firstSlotOfADay, lastSlotOfADay, dayIterator, exportBookings, bookingList);
						}
					}

					resource.put(BOOKING_LIST_FIELD_NAME, bookingList);
					resourceList.add(resource);
				}
			}
			dayObject.put(RESOURCES_FIELD_NAME, resourceList);

			String dayName = DateTimeFormat.forPattern("EEEE").withLocale(this.locale).print(dayIterator);
			dayName = dayName.substring(0, 1).toUpperCase() + dayName.substring(1);
			dayObject.put(NAME, dayName);
			dayObject.put(DATE, dayIterator.toString(DD_MM_YYYY));

			dayList.add(dayObject);
			dayIterator = dayIterator.plusDays(1);
		}

		convertedObject.put(DAY_LIST_FIELD_NAME, dayList);

		return convertedObject;
	}


	private DateTime automaticGetFirstSlotOfADay() {
		return new DateTime(1, 1, 1, 7, 0);
	}

	private DateTime automaticGetLastSlotOfADay() {
		return new DateTime(1, 1, 1, 20, 0);
	}

	private void treatBooking(JsonObject exportBooking, DateTime firstSlotOfADay, DateTime lastSlotOfADay,
							  DateTime dayIterator, JsonArray exportBookings, JsonArray bookingList) {
		// Split booking into several bookings if booked on several days
		int bookingId = exportBooking.getInteger(BOOKING_ID);
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

			if (dayIterator.getDayOfYear() == bookingStartDate.getDayOfYear() &&
					dayIterator.getYear() == bookingStartDate.getYear() &&
					dayIterator.getEra() == bookingStartDate.getEra()) {

				JsonObject booking = new JsonObject();

				int dayDurationInSecond = Seconds.secondsBetween(dayStartDate, dayEndDate).getSeconds();
				int bookingOffsetFromStart = Seconds.secondsBetween(dayStartDate, bookingStartDate).getSeconds();
				int bookingDuration = Seconds.secondsBetween(bookingStartDate, bookingEndDate).getSeconds();

				String classId = "booking-" + bookingId + "-" + (k + 1);
				int slotIndex = getSlotIndex(bookingStartDate, bookingEndDate, siblings);
				double topOffset = slotHeight + slotHeight * (slotNumber - 1) * bookingOffsetFromStart / dayDurationInSecond;
				double bookingHeight = slotHeight * (slotNumber - 1) * bookingDuration / dayDurationInSecond - 2;
				double bookingWidth = (slotWidth - 2) / nbMaxBookingPerSlot;
				double leftOffset = (slotIndex * bookingWidth) + 125;

				exportBooking.put(SLOT_INDEX, slotIndex);
				booking.put(BOOKING_OWNER_NAME, exportBooking.getString(BOOKING_OWNER_NAME));
				booking.put(QUANTITY, exportBooking.getLong(QUANTITY).toString());
				booking.put(BOOKING_CLASS_ID_FIELD_NAME, classId);
				booking.put(BOOKING_TOP_FIELD_NAME, round(topOffset, DECIMAL_PRECISION));
				booking.put(BOOKING_TOP_UNIT_FIELD_NAME, DEFAULT_SIZE_UNIT);
				booking.put(BOOKING_HEIGHT_FIELD_NAME, round(bookingHeight, DECIMAL_PRECISION));
				booking.put(BOOKING_HEIGHT_UNIT_FIELD_NAME, DEFAULT_SIZE_UNIT);
				booking.put(BOOKING_LEFT_FIELD_NAME, round(leftOffset, DECIMAL_PRECISION));
				booking.put(BOOKING_LEFT_UNIT_FIELD_NAME, DEFAULT_SIZE_UNIT);
				booking.put(BOOKING_WIDTH_FIELD_NAME, round(bookingWidth, DECIMAL_PRECISION));
				booking.put(BOOKING_WIDTH_UNIT_FIELD_NAME, DEFAULT_SIZE_UNIT);
				booking.put(SLOT_INDEX, slotIndex);

				bookingList.add(booking);
			}
		}
	}
}
