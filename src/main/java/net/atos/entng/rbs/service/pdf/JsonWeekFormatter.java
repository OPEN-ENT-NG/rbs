package net.atos.entng.rbs.service.pdf;

import fr.wseduc.webutils.I18n;
import net.atos.entng.rbs.model.ExportBooking;
import net.atos.entng.rbs.model.ExportRequest;
import net.atos.entng.rbs.model.ExportResponse;
import org.joda.time.*;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.joda.time.format.DateTimeFormat;

import java.util.ArrayList;
import java.util.Locale;
import java.util.TimeZone;

public class JsonWeekFormatter extends JsonFormatter {

	private final int CALENDAR_HEIGHT = 560;
	private final int CALENDAR_WIDTH = 1000;

	public JsonWeekFormatter(JsonObject jsonFileObject, String host, Locale locale, String userTimeZone) {
		super(jsonFileObject, host, locale, userTimeZone);
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
		convertedObject.put(SLOT_HEIGHT_FIELD_NAME, round(slotHeight, DECIMAL_PRECISION));
		convertedObject.put(SLOT_HEIGHT_UNIT_FIELD_NAME, DEFAULT_SIZE_UNIT);
		convertedObject.put(SLOT_WIDTH_FIELD_NAME, slotWidth);
		convertedObject.put(SLOT_WIDTH_UNIT_FIELD_NAME, DEFAULT_SIZE_UNIT);
		convertedObject.put(I18N_TITLE, I18n.getInstance().translate(MAP_I18N.get(I18N_TITLE), this.host, this.locale));

		DateTime exportStart = toUserTimeZone(exportObject.getString(ExportRequest.START_DATE)).withDayOfWeek(DateTimeConstants.MONDAY);
		DateTime exportEnd = toUserTimeZone(exportObject.getString(ExportRequest.END_DATE)).withDayOfWeek(DateTimeConstants.SUNDAY).plusDays(1);
		JsonArray exportBookingList = exportObject.getJsonArray(ExportResponse.BOOKINGS);

		DateTime weekIterator = exportStart;
		JsonArray weekList = new fr.wseduc.webutils.collections.JsonArray();

		while (Weeks.weeksBetween(weekIterator, exportEnd).getWeeks() != 0) {

			JsonObject weekObject = new JsonObject();
			JsonArray slotRawTitle = buildSlotRawTitle(firstSlotOfADay, lastSlotOfADay);
			JsonArray dayList = builDayList(weekIterator);

			// Building resource list
			ArrayList<Long> performedResourceId = new ArrayList<>();
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
					resource.put(I18N_FOOTER, I18n.getInstance().translate(MAP_I18N.get(I18N_FOOTER), this.host, this.locale));
					resource.put(SLOT_RAW_TITLE_FIELD_NAME, slotRawTitle);
					resource.put(DAYS_FIELD_NAME, dayList);

					// Adding resource bookings
					JsonArray bookingList = new fr.wseduc.webutils.collections.JsonArray();
					for (int j = 0; j < exportBookingList.size(); j++) {
						JsonObject exportBooking = exportBookingList.getJsonObject(j);

						if (exportBooking.getLong(ExportBooking.RESOURCE_ID).equals(currentResourceId)) { // This booking belongs to the current resource
							// Split booking into several bookings if booked on several days
							DateTime exportBookingStartDate = toUserTimeZone(exportBooking.getString(ExportBooking.BOOKING_START_DATE));
							DateTime exportBookingEndDate = toUserTimeZone(exportBooking.getString(ExportBooking.BOOKING_END_DATE));
							int daysBetweenStartAndEnd = Days.daysBetween(exportBookingStartDate, exportBookingEndDate).getDays();

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

									String classId = "booking-" + String.valueOf(exportBooking.getInteger(ExportBooking.BOOKING_ID)) + "-" + String.valueOf(k + 1);
									double leftOffest = slotWidth * bookingStartDate.getDayOfWeek() + 5;
									double topOffset = 48 + slotHeight * (slotNumber - 1) * bookingOffsetFromStart / dayDurationInSecond;
									double bookingHeight = slotHeight * (slotNumber - 1) * bookingDuration / dayDurationInSecond - 2;

									booking.put(ExportBooking.BOOKING_OWNER_NAME, exportBooking.getString(ExportBooking.BOOKING_OWNER_NAME));
									booking.put(ExportBooking.QUANTITY, exportBooking.getLong(ExportBooking.QUANTITY).toString());
									booking.put(BOOKING_CLASS_ID_FIELD_NAME, classId);
									booking.put(BOOKING_TOP_FIELD_NAME, round(topOffset, DECIMAL_PRECISION));
									booking.put(BOOKING_TOP_UNIT_FIELD_NAME, DEFAULT_SIZE_UNIT);
									booking.put(BOOKING_LEFT_FIELD_NAME, leftOffest);
									booking.put(BOOKING_LEFT_UNIT_FIELD_NAME, DEFAULT_SIZE_UNIT);
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
			slotRawTitle.add(new JsonObject().put("value", String.valueOf(i) + ":00 - " + String.valueOf(i + 1) + ":00"));
		return slotRawTitle;
	}

	/**
	 * Build the week day list
	 *
	 * @param startDate: Date in the given week
	 * @return The week day list in which the start date is contained in
	 */
	private JsonArray builDayList(DateTime startDate) {
		JsonArray dayList = new fr.wseduc.webutils.collections.JsonArray();
		DateTime firstDayOfWeek = startDate.withDayOfWeek(DateTimeConstants.MONDAY);

		for (int i = 0; i < 7; i++) {
			JsonObject oneDay = new JsonObject();
			String dayName = DateTimeFormat.forPattern("EEEE").withLocale(this.locale).print(firstDayOfWeek.plusDays(i));
			dayName = dayName.substring(0, 1).toUpperCase() + dayName.substring(1);
			oneDay.put("name", dayName);
			oneDay.put("date", firstDayOfWeek.plusDays(i).toString("dd/MM/YYYY"));
			dayList.add(oneDay);
		}
		return dayList;
	}
}
