package net.atos.entng.rbs.models;

import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.time.ZonedDateTime;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class Availability {
	private final Integer availability_id;
	private final JsonObject json;
	private String selectedDaysBitString;
	private final ZonedDateTime start_date;
	private final ZonedDateTime end_date;
	private final ZonedDateTime start_time;
	private final ZonedDateTime end_time;
	DateTimeFormatter sqlDateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
	DateTimeFormatter sqlTimeFormatter = DateTimeFormatter.ofPattern("HH:mm");

	public Availability(JsonObject json) {
		this(json, json.getString("iana"), null);
	}

	public Availability(JsonObject json, Integer id) {
		this(json, json.getString("iana"), id);
	}

	public Availability(JsonObject json, String iana, Integer id) {
		super();
		this.json = json;
		this.availability_id = id > 0 ? id : null;
		this.start_date = BookingDateUtils.localDateTimeForTimestampSecondsAndIana(json.getLong("start_date", 0l), iana);
		this.end_date = BookingDateUtils.localDateTimeForTimestampSecondsAndIana(json.getLong("end_date", 0l), iana);
		this.start_time = BookingDateUtils.localDateTimeForTimestampSecondsAndIana(json.getLong("start_time", 0l), iana);
		this.end_time = BookingDateUtils.localDateTimeForTimestampSecondsAndIana(json.getLong("end_time", 0l), iana);
	}



	public Integer getId() {
		return availability_id;
	}

	public Integer getResourceId() {
		return json.getInteger("resource_id");
	}

	public Integer getQuantity() {
		return json.getInteger("quantity");
	}

	public Boolean isUnavailability() {
		return json.getBoolean("is_unavailability");
	}

	public String getStartDate() {
		return sqlDateFormatter.format(this.start_date);
	}

	public String getEndDate() {
		return sqlDateFormatter.format(this.end_date);
	}

	public String getStartTime() {
		return sqlTimeFormatter.format(this.start_time);
	}

	public String getEndTime() {
		return sqlTimeFormatter.format(this.end_time);
	}

	public Optional<JsonArray> getDays() {
		return Optional.ofNullable(json.getJsonArray("days", null));
	}

	public String getSelectedDaysBitString() {
		if (selectedDaysBitString == null) {
			computeSelectedDaysAsBitString();
		}
		return selectedDaysBitString;
	}

	public void computeSelectedDaysAsBitString() {
		JsonArray selectedDaysArray = getDays().get();
		StringBuilder selectedDays = new StringBuilder();
		for (Object day : selectedDaysArray) {
			int isSelectedDay = ((Boolean) day) ? 1 : 0;
			selectedDays.append(isSelectedDay);
		}
		selectedDaysBitString = selectedDays.toString();
	}
}
