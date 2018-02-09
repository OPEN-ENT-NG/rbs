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

import org.entcore.common.user.UserInfos;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Input provided by user in order to generate an export.
 */
public class ExportRequest {

	private static final SimpleDateFormat DATE_FORMATTER = new SimpleDateFormat("yyyy-MM-dd");
	public static final String START_DATE = "startdate";
	public static final String END_DATE = "enddate";
	public static final String FORMAT = "format";
	public static final String VIEW = "view";
	public static final String RESOURCE_IDS = "resourceIds";

	public enum Format {ICAL, PDF}

	public enum View {DAY, WEEK, LIST, NA}

	private UserInfos userInfos;
	private View view = View.WEEK;
	private Format format = Format.ICAL;
	private List<Long> resourceIds = new ArrayList<>();
	private String startDate;
	private String endDate;

	public ExportRequest(JsonObject data) {
		this(data, null);
	}

	public ExportRequest(JsonObject userExportRequest, UserInfos userInfos) {
		this.userInfos = userInfos;
		this.format = Format.valueOf(userExportRequest.getString(FORMAT));
		this.view = View.valueOf(userExportRequest.getString(VIEW));

		this.startDate = userExportRequest.getString(START_DATE);
		this.endDate = userExportRequest.getString(END_DATE);
		checkPeriodValid(startDate, endDate);

		try {
			JsonArray userExportResourceArray = userExportRequest.getArray(RESOURCE_IDS, new JsonArray());
			for (Object resourceId : userExportResourceArray) {
				this.resourceIds.add(new Long((Integer) resourceId));
			}
		} catch (NumberFormatException | NullPointerException | ClassCastException e) {
			throw new IllegalArgumentException("params resources must be defined with an array of Integer");
		}

	}

	private void checkPeriodValid(String startInput, String endInput) {
		Date start;
		Date end;
		try {
			start = DATE_FORMATTER.parse(startInput);
			end = DATE_FORMATTER.parse(endInput);
		} catch (NullPointerException | ParseException e) {
			throw new IllegalArgumentException("params start and end date must be defined with YYYY-MM-DD format !");
		}
		if (start.after(end)) {
			throw new IllegalArgumentException("Param startdate must be before enddate");
		}
	}

	public UserInfos getUserInfos() {
		return userInfos;
	}

	public View getView() {
		return view;
	}

	public Format getFormat() {
		return format;
	}

	public List<Long> getResourceIds() {
		return resourceIds;
	}

	public String getStartDate() {
		return startDate;
	}

	public String getEndDate() {
		return endDate;
	}


	public void setFormat(Format format) {
		this.format = format;
	}

	public void setView(View view) {
		this.view = view;
	}

	public void setResourceIds(List<Long> resourceIds) {
		this.resourceIds = resourceIds;
	}

	public void setStartDate(String startDate) {
		this.startDate = startDate;
	}

	public void setEndDate(String endDate) {
		this.endDate = endDate;
	}
}
