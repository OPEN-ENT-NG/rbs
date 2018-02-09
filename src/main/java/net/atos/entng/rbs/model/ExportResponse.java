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

import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;


/**
 * A wrapper object containing data to export
 */
public class ExportResponse {
	public static final String BOOKINGS = "bookings";

	private ExportRequest request;
	private List<ExportBooking> bookings;

	public ExportResponse(ExportRequest request) {
		this.request = request;
	}

	public ExportRequest getRequest() {
		return request;
	}

	public List<ExportBooking> getBookings() {
		return bookings;
	}

	public void setBookings(List<ExportBooking> bookings) {
		this.bookings = bookings;
	}

	public void setSchoolNames(Map<String, String> schoolNamesById) {
		for (ExportBooking exportBooking : bookings) {
			String schoolName = null;
			String schoolId = exportBooking.getSchoolId();
			if (schoolId != null) {
				schoolName = schoolNamesById.get(schoolId);
			}
			if (schoolName == null || schoolName.isEmpty()) {
				schoolName = "Inconnue";
			}
			exportBooking.setSchoolName(schoolName);
		}
	}

	public JsonObject toJson() {
		JsonObject response = new JsonObject();
		response.putString(ExportRequest.START_DATE, request.getStartDate());
		response.putString(ExportRequest.END_DATE, request.getEndDate());
		response.putString(ExportRequest.FORMAT, request.getFormat().name());
		response.putString(ExportRequest.VIEW, request.getView().name());
		JsonArray jsonBookings = new JsonArray();
		for (ExportBooking booking : bookings) {
			jsonBookings.addObject(booking.toJson());
		}
		response.putArray(BOOKINGS, jsonBookings);
		return response;
	}

	public static ExportRequest.View getView(JsonObject exportResponse) {
		String viewAsString = exportResponse.getString(ExportRequest.VIEW, ExportRequest.View.WEEK.name());
		return ExportRequest.View.valueOf(viewAsString);
	}

	public static ExportResponse fromJson(JsonObject data) {
		ExportRequest exportRequest = new ExportRequest(data);
		ExportResponse exportResponse = new ExportResponse(exportRequest);
		JsonArray array = data.getArray(BOOKINGS);
		List<ExportBooking> bookings = new ArrayList<>();
		for (Object o : array) {
			ExportBooking booking = new ExportBooking((JsonObject) o);
			bookings.add(booking);
		}
		exportResponse.setBookings(bookings);
		return exportResponse;
	}
}
