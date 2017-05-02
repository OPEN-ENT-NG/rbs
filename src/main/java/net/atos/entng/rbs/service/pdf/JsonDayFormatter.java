package net.atos.entng.rbs.service.pdf;

import net.atos.entng.rbs.model.ExportBooking;
import net.atos.entng.rbs.model.ExportRequest;
import org.joda.time.DateTime;
import org.joda.time.Days;
import org.joda.time.Seconds;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

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
		convertedObject.put(EDITION_DATE_FIELD_NAME, DateTime.now().toString("dd/MM/YYYY"));
		int slotNumber = lastSlotOfADay.getHourOfDay() - firstSlotOfADay.getHourOfDay() + 1;
		double slotHeight = CALENDAR_HEIGHT / slotNumber;
		double slotWidth = round(CALENDAR_WIDTH / 8, DECIMAL_PRECISION);

		// General calendar settings
		convertedObject.put(CALENDAR_HEIGHT_FIELD_NAME, round(round(slotHeight, DECIMAL_PRECISION) * slotNumber, DECIMAL_PRECISION));
		convertedObject.put(CALENDAR_HEIGHT_UNIT_FIELD_NAME, DEFAULT_SIZE_UNIT);
		convertedObject.put(CALENDAR_WIDTH_FIELD_NAME, CALENDAR_WIDTH);
		convertedObject.put(CALENDAR_WIDTH_UNIT_FIELD_NAME, DEFAULT_SIZE_UNIT);
		convertedObject.put(BOOKING_WIDTH_FIELD_NAME, CALENDAR_WIDTH - 135);
		convertedObject.put(BOOKING_WIDTH_UNIT_FIELD_NAME, DEFAULT_SIZE_UNIT);
		convertedObject.put(SLOT_HEIGHT_FIELD_NAME, round(slotHeight, DECIMAL_PRECISION));
		convertedObject.put(SLOT_HEIGHT_UNIT_FIELD_NAME, DEFAULT_SIZE_UNIT);
		convertedObject.put(SLOT_WIDTH_FIELD_NAME, slotWidth);
		convertedObject.put(SLOT_WIDTH_UNIT_FIELD_NAME, DEFAULT_SIZE_UNIT);

		DateTime exportStart = new DateTime(exportObject.getString(ExportRequest.START_DATE));
		DateTime exportEnd = new DateTime(exportObject.getString(ExportRequest.END_DATE)).plusDays(1);
		JsonArray exportBookingList = exportObject.getJsonArray(BOOKING_LIST_FIELD_NAME);

		DateTime dayIterator = exportStart;
		JsonArray dayList = new fr.wseduc.webutils.collections.JsonArray();

		while (dayIterator.getDayOfYear() != exportEnd.getDayOfYear() ||
				dayIterator.getYear() != exportEnd.getYear() ||
				dayIterator.getEra() != exportEnd.getEra()) {

			JsonObject dayObject = new JsonObject();
			JsonArray slotRawTitle = buildSlotRawTitle(firstSlotOfADay, lastSlotOfADay);

			// Building resource list
			ArrayList<Long> performedResourceId = new ArrayList<Long>();
			JsonArray resourceList = new fr.wseduc.webutils.collections.JsonArray();

			for (int i = 0; i < exportBookingList.size(); i++) {
				JsonObject bookingIterator = exportBookingList.getJsonObject(i);
				Long currentResourceId = bookingIterator.getLong(ExportBooking.RESOURCE_ID);

				if (!performedResourceId.contains(currentResourceId)) { // New resource found
					performedResourceId.add(bookingIterator.getLong(ExportBooking.RESOURCE_ID));
					JsonObject resource = new JsonObject();
					resource.put(ExportBooking.RESOURCE_ID, currentResourceId);
					resource.put(ExportBooking.RESOURCE_NAME, bookingIterator.getString(ExportBooking.RESOURCE_NAME));
					resource.put(ExportBooking.RESOURCE_COLOR, bookingIterator.getString(ExportBooking.RESOURCE_COLOR));
					resource.put(ExportBooking.SCHOOL_NAME, bookingIterator.getString(ExportBooking.SCHOOL_NAME));
					resource.put(SLOT_RAW_TITLE_FIELD_NAME, slotRawTitle);

					// Adding resource bookings
					JsonArray bookingList = new fr.wseduc.webutils.collections.JsonArray();
					for (int j = 0; j < exportBookingList.size(); j++) {
						JsonObject exportBooking = exportBookingList.getJsonObject(j);

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

									String classId = "booking-" + String.valueOf(exportBooking.getInteger(ExportBooking.BOOKING_ID)) + "-" + String.valueOf(k + 1);
									double topOffset = slotHeight + slotHeight * (slotNumber - 1) * bookingOffsetFromStart / dayDurationInSecond;
									double bookingHeight = slotHeight * (slotNumber - 1) * bookingDuration / dayDurationInSecond - 2;

									booking.put(ExportBooking.BOOKING_OWNER_NAME, exportBooking.getString(ExportBooking.BOOKING_OWNER_NAME));
									booking.put(BOOKING_CLASS_ID_FIELD_NAME, classId);
									booking.put(BOOKING_TOP_FIELD_NAME, round(topOffset, DECIMAL_PRECISION));
									booking.put(BOOKING_TOP_UNIT_FIELD_NAME, DEFAULT_SIZE_UNIT);
									booking.put(BOOKING_HEIGHT_FIELD_NAME, round(bookingHeight, DECIMAL_PRECISION));
									booking.put(BOOKING_HEIGHT_UNIT_FIELD_NAME, DEFAULT_SIZE_UNIT);

									bookingList.add(booking);
								}
							}
						}
					}

					resource.put(BOOKING_LIST_FIELD_NAME, bookingList);
					resourceList.add(resource);
				}
			}
			dayObject.put(RESOURCES_FIELD_NAME, resourceList);

			String dayName = dayIterator.toString("EEEE");
			dayName = dayName.substring(0, 1).toUpperCase() + dayName.substring(1);
			dayObject.put("name", dayName);
			dayObject.put("date", dayIterator.toString("dd/MM/YYYY"));

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
			slotRawTitle.add(new JsonObject().put("value", String.valueOf(i) + ":00 - " + String.valueOf(i + 1) + ":00"));
		return slotRawTitle;
	}
}
