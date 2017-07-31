package net.atos.entng.rbs.service.pdf;

import net.atos.entng.rbs.model.ExportBooking;
import net.atos.entng.rbs.model.ExportRequest;
import org.joda.time.DateTime;
import org.joda.time.Days;
import org.joda.time.Seconds;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;

import java.util.ArrayList;

public class JsonDayFormatter extends JsonFormatter {

	private final int CALENDAR_HEIGHT = 900;
	private final int CALENDAR_WIDTH = 680;

	public JsonDayFormatter(JsonObject jsonFileObject) {
		super(jsonFileObject);
	}

	public JsonObject format() {
		DateTime firstSlotOfADay = automaticGetFirstSlotOfADay();
		DateTime lastSlotOfADay = automaticGetLastSlotOfADay();

		JsonObject convertedObject = new JsonObject();
		convertedObject.putString(EDITION_DATE_FIELD_NAME, DateTime.now().toString("dd/MM/YYYY"));
		int slotNumber = lastSlotOfADay.getHourOfDay() - firstSlotOfADay.getHourOfDay() + 1;
		double slotHeight = CALENDAR_HEIGHT / slotNumber;
		double slotWidth = round(CALENDAR_WIDTH / 8, DECIMAL_PRECISION);

		// General calendar settings
		convertedObject.putNumber(CALENDAR_HEIGHT_FIELD_NAME, round(round(slotHeight, DECIMAL_PRECISION) * slotNumber, DECIMAL_PRECISION));
		convertedObject.putString(CALENDAR_HEIGHT_UNIT_FIELD_NAME, DEFAULT_SIZE_UNIT);
		convertedObject.putNumber(CALENDAR_WIDTH_FIELD_NAME, CALENDAR_WIDTH);
		convertedObject.putString(CALENDAR_WIDTH_UNIT_FIELD_NAME, DEFAULT_SIZE_UNIT);
		convertedObject.putNumber(BOOKING_WIDTH_FIELD_NAME, CALENDAR_WIDTH - 135);
		convertedObject.putString(BOOKING_WIDTH_UNIT_FIELD_NAME, DEFAULT_SIZE_UNIT);
		convertedObject.putNumber(SLOT_HEIGHT_FIELD_NAME, round(slotHeight, DECIMAL_PRECISION));
		convertedObject.putString(SLOT_HEIGHT_UNIT_FIELD_NAME, DEFAULT_SIZE_UNIT);
		convertedObject.putNumber(SLOT_WIDTH_FIELD_NAME, slotWidth);
		convertedObject.putString(SLOT_WIDTH_UNIT_FIELD_NAME, DEFAULT_SIZE_UNIT);

		DateTime exportStart = new DateTime(exportObject.getString(ExportRequest.START_DATE));
		DateTime exportEnd = new DateTime(exportObject.getString(ExportRequest.END_DATE)).plusDays(1);
		JsonArray exportBookingList = exportObject.getArray(BOOKING_LIST_FIELD_NAME);

		DateTime dayIterator = exportStart;
		JsonArray dayList = new JsonArray();

		while (dayIterator.getDayOfYear() != exportEnd.getDayOfYear() ||
				dayIterator.getYear() != exportEnd.getYear() ||
				dayIterator.getEra() != exportEnd.getEra()) {

			JsonObject dayObject = new JsonObject();
			JsonArray slotRawTitle = buildSlotRawTitle(firstSlotOfADay, lastSlotOfADay);

			// Building resource list
			ArrayList<Long> performedResourceId = new ArrayList<Long>();
			JsonArray resourceList = new JsonArray();

			for (int i = 0; i < exportBookingList.size(); i++) {
				JsonObject bookingIterator = exportBookingList.get(i);
				Long currentResourceId = bookingIterator.getLong(ExportBooking.RESOURCE_ID);

				if (!performedResourceId.contains(currentResourceId)) { // New resource found
					performedResourceId.add(bookingIterator.getLong(ExportBooking.RESOURCE_ID));
					JsonObject resource = new JsonObject();
					resource.putNumber(ExportBooking.RESOURCE_ID, currentResourceId);
					resource.putString(ExportBooking.RESOURCE_NAME, bookingIterator.getString(ExportBooking.RESOURCE_NAME));
					resource.putString(ExportBooking.RESOURCE_COLOR, bookingIterator.getString(ExportBooking.RESOURCE_COLOR));
					resource.putString(ExportBooking.SCHOOL_NAME, bookingIterator.getString(ExportBooking.SCHOOL_NAME));
					resource.putArray(SLOT_RAW_TITLE_FIELD_NAME, slotRawTitle);

					// Adding resource bookings
					JsonArray bookingList = new JsonArray();
					for (int j = 0; j < exportBookingList.size(); j++) {
						JsonObject exportBooking = exportBookingList.get(j);

						if (exportBooking.getLong(ExportBooking.RESOURCE_ID).equals(currentResourceId)) { // This booking belongs to the current resource
							// Split booking into several bookings if booked on several days
							DateTime exportBookingStartDate = new DateTime(exportBooking.getString(ExportBooking.BOOKING_START_DATE));
							DateTime exportBookingEndDate = new DateTime(exportBooking.getString(ExportBooking.BOOKING_END_DATE));
							int daysBetweenStartAndEnd = Days.daysBetween(exportBookingStartDate, exportBookingEndDate).getDays();

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

									String classId = "booking-" + String.valueOf(exportBooking.getNumber(ExportBooking.BOOKING_ID)) + "-" + String.valueOf(k + 1);
									double topOffset = slotHeight + slotHeight * (slotNumber - 1) * bookingOffsetFromStart / dayDurationInSecond;
									double bookingHeight = slotHeight * (slotNumber - 1) * bookingDuration / dayDurationInSecond - 2;

									booking.putString(ExportBooking.BOOKING_OWNER_NAME, exportBooking.getString(ExportBooking.BOOKING_OWNER_NAME));
									booking.putString(BOOKING_CLASS_ID_FIELD_NAME, classId);
									booking.putNumber(BOOKING_TOP_FIELD_NAME, round(topOffset, DECIMAL_PRECISION));
									booking.putString(BOOKING_TOP_UNIT_FIELD_NAME, DEFAULT_SIZE_UNIT);
									booking.putNumber(BOOKING_HEIGHT_FIELD_NAME, round(bookingHeight, DECIMAL_PRECISION));
									booking.putString(BOOKING_HEIGHT_UNIT_FIELD_NAME, DEFAULT_SIZE_UNIT);

									bookingList.addObject(booking);
								}
							}
						}
					}

					resource.putArray(BOOKING_LIST_FIELD_NAME, bookingList);
					resourceList.addObject(resource);
				}
			}
			dayObject.putArray(RESOURCES_FIELD_NAME, resourceList);

			String dayName = dayIterator.toString("EEEE");
			dayName = dayName.substring(0, 1).toUpperCase() + dayName.substring(1);
			dayObject.putString("name", dayName);
			dayObject.putString("date", dayIterator.toString("dd/MM/YYYY"));

			dayList.addObject(dayObject);
			dayIterator = dayIterator.plusDays(1);
		}

		convertedObject.putArray(DAY_LIST_FIELD_NAME, dayList);

		return convertedObject;
	}


	private DateTime automaticGetFirstSlotOfADay() {
		return new DateTime(1, 1, 1, 7, 0);
	}

	private DateTime automaticGetLastSlotOfADay() {
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
		JsonArray slotRawTitle = new JsonArray();
		for (int i = firstSlotOfADay.getHourOfDay(); i < lastSlotOfADay.getHourOfDay(); i++)
			slotRawTitle.addObject(new JsonObject().putString("value", String.valueOf(i) + ":00 - " + String.valueOf(i + 1) + ":00"));
		return slotRawTitle;
	}
}
