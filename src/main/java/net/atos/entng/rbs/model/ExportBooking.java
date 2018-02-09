/*
 * Copyright © Région Nord Pas de Calais-Picardie,  Département 91, Région Aquitaine-Limousin-Poitou-Charentes, 2016.
 *
 * This file is part of OPEN ENT NG. OPEN ENT NG is a versatile ENT Project based on the JVM and ENT Core Project.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation (version 3 of the License).
 *
 * For the sake of explanation, any module that communicate over native
 * Web protocols, such as HTTP, with OPEN ENT NG is outside the scope of this
 * license and could be license under its own terms. This is merely considered
 * normal use of OPEN ENT NG, and does not fall under the heading of "covered work".
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package net.atos.entng.rbs.model;

import org.vertx.java.core.json.JsonObject;

/**
 * Contains data used to generate export
 */
public class ExportBooking {

	public static final String BOOKING_ID = "id";
	public static final String BOOKING_OWNER_ID = "owner"; // "ff2238cd-5110-431b-a6fa-bbc4fa51411d",
	public static final String BOOKING_START_DATE = "start_date"; //:"2017-06-26T09:00:00.000",
	public static final String BOOKING_END_DATE = "end_date"; // "2017-06-26T10:00:00.000",
	public static final String BOOKING_REASON = "booking_reason"; // free text
	public static final String BOOKING_OWNER_NAME = "owner_name"; // "Catherine BAILLY",
	public static final String BOOKING_MODERATOR_ID = "moderator_id"; // null,
	public static final String BOOKING_MODERATOR_NAME = "moderator_name"; // null
	public static final String SCHOOL_ID = "school_id"; // "a4d06ecc-e3cd-428b-b239-0a82a208f715",
	public static final String RESOURCE_TYPE_ID = "type_id"; // 1,
	public static final String RESOURCE_TYPE_COLOR = "type_color"; // "#02ed09",
	public static final String RESOURCE_ID = "resource_id"; //2,
	public static final String RESOURCE_NAME = "resource_name"; // "PC Windaube",
	public static final String RESOURCE_COLOR = "resource_color"; // "#02ed19",
	public static final String SCHOOL_NAME = "school_name"; // added by post treatment (stored in MongoDB, not PostgreSQL)

	private JsonObject data;

	public ExportBooking(JsonObject data) {
		this.data = data;
	}

	public JsonObject toJson() {
		return data;
	}

	public String getSchoolId() {
		return data.getString(SCHOOL_ID);
	}

	public void setSchoolName(String schoolName) {
		data.putString(SCHOOL_NAME, schoolName);
	}

	public String getColor() {
		String resourceColor = data.getString(RESOURCE_COLOR);
		if (resourceColor == null || resourceColor.isEmpty()) {
			return data.getString(RESOURCE_TYPE_COLOR);
		}
		return resourceColor;
	}

	public String getStart() {
		return data.getString(BOOKING_START_DATE);
	}

	public String getEnd() {
		return data.getString(BOOKING_END_DATE);
	}


	@Override
	public String toString() {
		final StringBuffer sb = new StringBuffer("ExportBooking{");
		sb.append("resource=").append(getResource());
		sb.append(", owner=").append(getOwner());
		sb.append(", start=").append(getStart());
		sb.append(", end=").append(getEnd());
		sb.append('}');
		return sb.toString();
	}

	public String getOwner() {
		return data.getString(BOOKING_OWNER_NAME);
	}

	public String getResource() {
		return data.getString(RESOURCE_NAME);
	}

	public String getSchoolName() {
		return data.getString(SCHOOL_NAME);
	}

	public String getBookingReason() {
		return data.getString(BOOKING_REASON);
	}

	public Number getId() {
		return data.getNumber(BOOKING_ID);
	}

	public String getOwnerId() {
		return data.getString(BOOKING_OWNER_ID);
	}
}

