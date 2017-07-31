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
package net.atos.entng.rbs.service;

import net.atos.entng.rbs.BookingUtils;
import net.atos.entng.rbs.model.ExportBooking;
import net.fortuna.ical4j.model.Parameter;
import net.fortuna.ical4j.model.Property;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.parameter.Cn;
import net.fortuna.ical4j.model.property.*;
import org.entcore.common.user.UserInfos;
import org.junit.Test;
import org.vertx.java.core.json.JsonObject;

import java.net.SocketException;
import java.net.URISyntaxException;
import java.text.ParseException;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class IcalExportServiceTest {
	private static final String resourceName = "Salle de Chimie";
	private static final String schoolName = "Ecole du ruisseau";
	private static final String startDate = "2017-07-20T09:00:00.000";
	private static final String endDate = "2017-07-20T10:00:00.000";
	private static final String owner = "Catherine Bailly";
	private static final String reason = "Cours de soutien exceptionnel";
	private static final String email = "test@rbs-ent.org";

	@Test
	public void should_convert_booking_without_mail() throws ParseException, SocketException, URISyntaxException {

		JsonObject jsonBooking = getJsonBooking(1, resourceName, reason, schoolName, startDate, endDate, owner);
		ExportBooking booking = new ExportBooking(jsonBooking);

		UserInfos userInfos = new UserInfos();

		IcalExportService icalExportService = new IcalExportService();
		VEvent meeting = icalExportService.convert(userInfos, booking);

		Property organizer = meeting.getProperties().getProperty(Organizer.ORGANIZER);
		assertThat(organizer, notNullValue());
		assertThat(organizer.getValue(), equalTo(""));
		Parameter organizerCommonName = organizer.getParameter(Cn.CN);
		assertThat(organizerCommonName, notNullValue());
		assertThat(organizerCommonName.getValue(), equalTo(owner));
	}

	/**
	 * http://redmine.docdoku.net/issues/3930#note-7 :
	 * SUMMARY : Nom de la ressource
	 * DESCRIPTION : motif de la réservation
	 * LOCATION : Nom de l'établissement
	 * DTSTART : horaire de début de la réservation
	 * DTEND : horaire de fin de la réservation
	 * ORGANIZER;ROLE=REQ-PARTICIPANT : Nom de la personne ayant effectué la réservation
	 */
	@Test
	public void should_convert_booking_to_ical_event() throws ParseException, SocketException, URISyntaxException {

		JsonObject jsonBooking = getJsonBooking(1, resourceName, reason, schoolName, startDate, endDate, owner);
		ExportBooking booking = new ExportBooking(jsonBooking);

		UserInfos userInfos = getUserInfos(email);

		IcalExportService icalExportService = new IcalExportService();
		VEvent meeting = icalExportService.convert(userInfos, booking);

		Summary summary = meeting.getSummary();
		assertThat(summary, notNullValue());
		assertThat(summary.getValue(), equalTo(resourceName));

		Location location = meeting.getLocation();
		assertThat(location, notNullValue());
		assertThat(location.getValue(), equalTo(schoolName));

		Description description = meeting.getDescription();
		assertThat(description.getValue(), equalTo(reason));

		DtStart startDate1 = meeting.getStartDate();
		assertThat(startDate1, notNullValue());
		assertThat(startDate1.getDate(), equalTo(icalExportService.parseTimestampForIcal(startDate)));


		DtEnd endDate1 = meeting.getEndDate();
		assertThat(endDate1, notNullValue());
		assertThat(endDate1.getDate(), equalTo(icalExportService.parseTimestampForIcal(endDate)));


		Property organizer = meeting.getProperties().getProperty(Organizer.ORGANIZER);
		assertThat(organizer, notNullValue());
		assertThat(organizer.getValue(), equalTo("mailto:" + email));
		Parameter organizerCommonName = organizer.getParameter(Cn.CN);
		assertThat(organizerCommonName, notNullValue());
		assertThat(organizerCommonName.getValue(), equalTo(owner));
	}

	private UserInfos getUserInfos(String email) {
		UserInfos userInfos = new UserInfos();
		userInfos.setUsername("User name");
		userInfos.getOtherProperties().put("email", email);
		return userInfos;
	}


	private JsonObject getJsonBooking(Number bookingId, String resourceName, String reason, String schoolName, String startDate, String endDate, String owner) {
		JsonObject jsonObject = new JsonObject();
		jsonObject.putNumber(ExportBooking.BOOKING_ID, bookingId);
		jsonObject.putString(ExportBooking.RESOURCE_NAME, resourceName);
		jsonObject.putString(ExportBooking.BOOKING_REASON, reason);
		jsonObject.putString(ExportBooking.SCHOOL_NAME, schoolName);
		jsonObject.putString(ExportBooking.BOOKING_START_DATE, startDate);
		jsonObject.putString(ExportBooking.BOOKING_END_DATE, endDate);
		jsonObject.putString(ExportBooking.BOOKING_OWNER_NAME, owner);
		return jsonObject;
	}

}
