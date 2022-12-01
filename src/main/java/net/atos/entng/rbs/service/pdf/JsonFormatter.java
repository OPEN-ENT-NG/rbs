package net.atos.entng.rbs.service.pdf;

import net.atos.entng.rbs.model.ExportRequest;
import net.atos.entng.rbs.model.ExportResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.*;
import java.util.stream.Collectors;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import static net.atos.entng.rbs.core.constants.Field.EXPORT;
import static net.atos.entng.rbs.core.constants.Field.VALUE;
import static net.atos.entng.rbs.model.ExportBooking.*;
import static net.atos.entng.rbs.model.ExportBooking.BOOKING_END_DATE;

public abstract class JsonFormatter {
	protected static final String EDITION_DATE_FIELD_NAME = "edition_date";
	protected static final String CALENDAR_HEIGHT_FIELD_NAME = "calendar_height";
	protected static final String CALENDAR_HEIGHT_UNIT_FIELD_NAME = "calendar_height_unit";
	protected static final String CALENDAR_WIDTH_FIELD_NAME = "calendar_width";
	protected static final String CALENDAR_WIDTH_UNIT_FIELD_NAME = "calendar_width_unit";
	protected static final String SLOT_HEIGHT_FIELD_NAME = "slot_height";
	protected static final String SLOT_HEIGHT_UNIT_FIELD_NAME = "slot_height_unit";
	protected static final String SLOT_WIDTH_FIELD_NAME = "slot_width";
	protected static final String SLOT_WIDTH_UNIT_FIELD_NAME = "slot_width_unit";
	protected static final String SLOT_RAW_TITLE_FIELD_NAME = "slot_raw_title";
	protected static final String DAYS_FIELD_NAME = "days";
	protected static final String RESOURCES_FIELD_NAME = "resources";
	protected static final String BOOKING_LIST_FIELD_NAME = "bookings";
	protected static final String BOOKING_CLASS_ID_FIELD_NAME = "booking_class_id";
	protected static final String BOOKING_TOP_FIELD_NAME = "booking_top";
	protected static final String BOOKING_TOP_UNIT_FIELD_NAME = "booking_top_unit";
	protected static final String BOOKING_LEFT_FIELD_NAME = "booking_left";
	protected static final String BOOKING_LEFT_UNIT_FIELD_NAME = "booking_left_unit";
	protected static final String BOOKING_HEIGHT_FIELD_NAME = "booking_height";
	protected static final String BOOKING_HEIGHT_UNIT_FIELD_NAME = "booking_height_unit";
	protected static final String DEFAULT_SIZE_UNIT = "px";
	protected static final String WEEK_LIST_FIELD_NAME = "weeks";
	protected static final String DAY_LIST_FIELD_NAME = "days";
	protected static final String BOOKING_WIDTH_FIELD_NAME = "booking_width";
	protected static final String BOOKING_WIDTH_UNIT_FIELD_NAME = "booking_width_unit";
	protected static final String SLOT_INDEX = "slot_index";
	protected static final int DECIMAL_PRECISION = 2;
	protected static final String I18N_TITLE = "i18n_title";
	protected static final String I18N_HEADER_OWNER = "i18n_owner";
	protected static final String I18N_HEADER_START = "i18n_start";
	protected static final String I18N_HEADER_END = "i18n_end";
	protected static final String I18N_QUANTITY = "i18n_quantity";
	protected static final String I18N_TO = "i18n_to";
	protected static final String I18N_FOOTER = "i18n_footer";


	protected static final Map<String, String> MAP_I18N  = new HashMap<>();

	static {
		MAP_I18N.put(I18N_TITLE, "rbs.pdf.title");
		MAP_I18N.put(I18N_HEADER_OWNER, "rbs.booking.headers.owner");
		MAP_I18N.put(I18N_HEADER_START, "rbs.booking.headers.start_date");
		MAP_I18N.put(I18N_HEADER_END, "rbs.booking.headers.end_date");
		MAP_I18N.put(I18N_QUANTITY, "rbs.booking.headers.quantity");
		MAP_I18N.put(I18N_TO, "rbs.search.date.to");
		MAP_I18N.put(I18N_FOOTER, "rbs.booking.edit.period.end.on");
	}


	protected JsonObject jsonExport = null;
	protected JsonObject exportObject = null;
	protected  Locale locale;
	protected  String host;
	protected  String userTimeZone;
	

	protected JsonFormatter(JsonObject jsonFileObject, String host, Locale locale, String userTimeZone) {
		JsonArray jsonFileArray = new JsonArray();
		jsonFileArray.add(jsonFileObject);

		this.jsonExport = new JsonObject().put(EXPORT, jsonFileArray);
		JsonArray exportArrayElement = jsonExport.getJsonArray(EXPORT);
		exportObject = exportArrayElement.getJsonObject(0);

		this.locale = locale;
		this.host = host;
		this.userTimeZone = userTimeZone;
	}

	abstract JsonObject format();

	/**
	 * Round a number to the given precision decimal
	 *
	 * @param number:    The number to round
	 * @param precision: The precision to apply
	 * @return The rounded number
	 */
	protected double round(double number, int precision) {
		return Math.round(number * Math.pow(10, precision)) / Math.pow(10, precision);
	}

	/**
	 * When exported if PDF, dates must be in the user's time zone... 
	 * @param utcInstant
	 * @return DateTime of the UTC instant, expressed in the user's time zone
	 */
	protected DateTime toUserTimeZone( final String utcInstant ) {
		DateTime dt = new DateTime( utcInstant, DateTimeZone.UTC );
		if( userTimeZone!=null && !userTimeZone.isEmpty() ) {
			try {
				dt = dt.withZone( DateTimeZone.forID(userTimeZone) );
			} catch (IllegalArgumentException e) {
				// Unknown time zone
			}
		}
		return dt;
	}
	
	public static JsonFormatter buildFormater(JsonObject jsonExportResponse, String host, Locale locale, String userTimeZone) {
		ExportRequest.View view = ExportResponse.getView(jsonExportResponse);
		JsonFormatter formatter;
		switch (view) {
			case DAY:
				formatter = new JsonDayFormatter(jsonExportResponse, host, locale, userTimeZone);
				break;
			case LIST:
				formatter = new JsonListFormatter(jsonExportResponse, host, locale, userTimeZone);
				break;
			case WEEK:
				formatter = new JsonWeekFormatter(jsonExportResponse, host, locale, userTimeZone);
				break;
			default:
				formatter = new JsonWeekFormatter(jsonExportResponse, host, locale, userTimeZone);
				break;
		}
		return formatter;
	}

	// Utils functions for Formatters

	/**
	 * Build the slot raw title list
	 *
	 * @param firstSlotOfADay: First time slot begin time
	 * @param lastSlotOfADay:  Last time slot end time
	 * @return The slot raw title list
	 */
	protected static JsonArray buildSlotRawTitles(DateTime firstSlotOfADay, DateTime lastSlotOfADay) {
		JsonArray slotRawTitles = new JsonArray();
		for (int i = firstSlotOfADay.getHourOfDay(); i < lastSlotOfADay.getHourOfDay(); i++) {
			slotRawTitles.add(new JsonObject().put(VALUE, i + ":00 - " + (i + 1) + ":00"));
		}
		return slotRawTitles;
	}

	protected List<JsonObject> getSiblings(JsonObject booking, List<JsonObject> bookingsList) {
		DateTime startDate = toUserTimeZone(booking.getString(BOOKING_START_DATE));
		DateTime endDate = toUserTimeZone(booking.getString(BOOKING_END_DATE));
		Long resourceId = booking.getLong(RESOURCE_ID);
		int bookingId = booking.getInteger(BOOKING_ID);

		return bookingsList.stream()
				.filter(b -> b.getLong(RESOURCE_ID).equals(resourceId)
						&& !b.getInteger(BOOKING_ID).equals(bookingId)
						&& isOverlapping(startDate, endDate, b))
				.collect(Collectors.toList());
	}

	protected List<JsonObject> getSiblings(JsonObject booking, JsonArray bookings) {
		List<JsonObject> bookingsList = new ArrayList<>();
		bookingsList.addAll(bookings.getList());
		return getSiblings(booking, bookingsList);
	}

	protected int getSlotIndex(DateTime startDate, DateTime endDate, List<JsonObject> siblings) {
		int maxSlotIndex = siblings.stream()
				.filter(b -> isOverlapping(startDate, endDate, b) && b.getInteger(SLOT_INDEX) != null)
				.mapToInt(b -> b.getInteger(SLOT_INDEX))
				.max()
				.orElse(-1);
		return maxSlotIndex + 1;
	}

	protected int getNbMaxSiblings(List<JsonObject> siblings) {
		int nbMaxSiblings = 0;
		for (JsonObject sibling : siblings) {
			int localNbMaxSibling = getSiblings(sibling, siblings).size() + 1;
			if (localNbMaxSibling > nbMaxSiblings) nbMaxSiblings = localNbMaxSibling;
		}

		return nbMaxSiblings;
	}

	private boolean isOverlapping(DateTime startDate, DateTime endDate, JsonObject booking) {
		DateTime bookingStartDate = toUserTimeZone(booking.getString(BOOKING_START_DATE));
		DateTime bookingEndDate = toUserTimeZone(booking.getString(BOOKING_END_DATE));
		return startDate.isBefore(bookingEndDate) && endDate.isAfter(bookingStartDate);
	}
}
