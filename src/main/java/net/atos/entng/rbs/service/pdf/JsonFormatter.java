package net.atos.entng.rbs.service.pdf;

import net.atos.entng.rbs.model.ExportRequest;
import net.atos.entng.rbs.model.ExportResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

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
	protected static final int DECIMAL_PRECISION = 2;
	protected static final String I18N_TITLE = "i18n_title";
	protected static final String I18N_HEADER_OWNER = "i18n_owner";
	protected static final String I18N_HEADER_START = "i18n_start";
	protected static final String I18N_HEADER_END = "i18n_end";
	protected static final String I18N_TO = "i18n_to";
	protected static final String I18N_FOOTER = "i18n_footer";


	protected static final Map<String, String> MAP_I18N  = new HashMap<>();

	static {
		MAP_I18N.put(I18N_TITLE, "rbs.pdf.title");
		MAP_I18N.put(I18N_HEADER_OWNER, "rbs.booking.headers.owner");
		MAP_I18N.put(I18N_HEADER_START, "rbs.booking.headers.start_date");
		MAP_I18N.put(I18N_HEADER_END, "rbs.booking.headers.end_date");
		MAP_I18N.put(I18N_TO, "rbs.search.date.to");
		MAP_I18N.put(I18N_FOOTER, "rbs.booking.edit.period.end.on");
	}


	protected JsonObject jsonExport = null;
	protected JsonObject exportObject = null;
	protected  Locale locale;
	protected  String host;

	protected JsonFormatter(JsonObject jsonFileObject, String host, Locale locale) {
		JsonArray jsonFileArray = new fr.wseduc.webutils.collections.JsonArray();
		jsonFileArray.add(jsonFileObject);

		this.jsonExport = new JsonObject().put("export", jsonFileArray);
		JsonArray exportArrayElement = jsonExport.getJsonArray("export");
		exportObject = exportArrayElement.getJsonObject(0);

		this.locale = locale;
		this.host = host;
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

	public static JsonFormatter buildFormater(JsonObject jsonExportResponse, String host, Locale locale) {
		ExportRequest.View view = ExportResponse.getView(jsonExportResponse);
		JsonFormatter formatter;
		switch (view) {
			case DAY:
				formatter = new JsonDayFormatter(jsonExportResponse, host, locale);
				break;
			case LIST:
				formatter = new JsonListFormatter(jsonExportResponse, host, locale);
				break;
			case WEEK:
				formatter = new JsonWeekFormatter(jsonExportResponse, host, locale);
				break;
			default:
				formatter = new JsonWeekFormatter(jsonExportResponse, host, locale);
				break;
		}
		return formatter;
	}
}
