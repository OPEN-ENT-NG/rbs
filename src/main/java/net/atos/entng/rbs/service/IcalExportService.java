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


import fr.wseduc.webutils.Either;
import net.atos.entng.rbs.BookingUtils;
import net.atos.entng.rbs.model.ExportBooking;
import net.atos.entng.rbs.model.ExportResponse;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.ComponentList;
import net.fortuna.ical4j.model.DateTime;
import net.fortuna.ical4j.model.component.CalendarComponent;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.parameter.Cn;
import net.fortuna.ical4j.model.parameter.Role;
import net.fortuna.ical4j.model.property.*;
import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.platform.Verticle;

import java.net.URISyntaxException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

public class IcalExportService extends Verticle implements Handler<Message<JsonObject>> {

	private static final Logger LOG = LoggerFactory.getLogger(IcalExportService.class);

	public static final String ICAL_HANDLER_ADDRESS = "rbs.ical.handler";
	/**
	 * Actions handled by worker
	 */
	public static final String ACTION_CONVERT = "convert";

	private TimeZone timeZone = TimeZone.getDefault();
	private UserService userService;

	@Override
	public void start() {
		super.start();
		userService = new UserServiceDirectoryImpl(vertx.eventBus());
		vertx.eventBus().registerHandler(ICAL_HANDLER_ADDRESS, this);

	}

	@Override
	public void handle(Message<JsonObject> message) {
		String action = message.body().getString("action", "");
		JsonObject exportResponse = message.body().getObject("data", new JsonObject());
		switch (action) {
			case ACTION_CONVERT:
				generateIcsFile(exportResponse, message);
				break;
			default:
				JsonObject results = new JsonObject();
				results.putString("message", "Unknown action");
				results.putNumber("status", 400);
				message.reply(results);
		}
	}


	public Date parseTimestampForIcal(String strDate) throws ParseException {
		String format = BookingUtils.TIMESTAMP_FORMAT;
		SimpleDateFormat formatter = new SimpleDateFormat(format);
		formatter.setTimeZone(timeZone);
		return formatter.parse(strDate);
	}

	VEvent convert(Map<String, String> userMailById, ExportBooking booking) throws ParseException, URISyntaxException {

		Date start = parseTimestampForIcal(booking.getStart());
		DateTime icalStart = new DateTime(start);
		Date end = parseTimestampForIcal(booking.getEnd());
		DateTime icalEnd = new DateTime(end);

		VEvent meeting = new VEvent(icalStart, icalEnd, booking.getResource());

		meeting.getProperties().add(new TzId(TimeZone.getDefault().getID()));

		Number bookingId = booking.getId();
		Uid uid = new Uid("RBS-Booking-" + bookingId);
		meeting.getProperties().add(uid);


		Location location = new Location(booking.getSchoolName());
		meeting.getProperties().add(location);

		Description description = new Description((booking.getBookingReason()));
		meeting.getProperties().add(description);

		Organizer organizer = getOrganizer(userMailById, booking);
		meeting.getProperties().add(organizer);
		return meeting;
	}

	private Organizer getOrganizer(Map<String, String> userMailById, ExportBooking booking) throws URISyntaxException {
		String email = userMailById.get(booking.getOwnerId());
		Organizer organizer;
		try {
			organizer = new Organizer("mailto:" + email);
		} catch (URISyntaxException e) {
			organizer = new Organizer("");
		}
		organizer.getParameters().add(Role.REQ_PARTICIPANT);
		organizer.getParameters().add(new Cn(booking.getOwner()));
		return organizer;
	}


	private void generateIcsFile(final JsonObject data, final Message<JsonObject> message) {
		final ExportResponse exportResponse = ExportResponse.fromJson(data);
		Set<String> ownerIds = new HashSet<>();
		for (ExportBooking exportBooking : exportResponse.getBookings()) {
			String ownerId = exportBooking.getOwnerId();
			ownerIds.add(ownerId);
		}
		userService.getUserMails(ownerIds, new Handler<Map<String, String>>() {
			@Override
			public void handle(Map<String, String> userEmails) {
				try {
					Calendar icsCalendar = new Calendar();
					icsCalendar.getProperties().add(new ProdId("-//RBS Calendar//iCal4j 2.0//FR"));
					icsCalendar.getProperties().add(CalScale.GREGORIAN);
					ComponentList<CalendarComponent> components = icsCalendar.getComponents();

					for (ExportBooking booking : exportResponse.getBookings()) {
						VEvent vEvent = convert(userEmails, booking);
						components.add(vEvent);
					}
					JsonObject results = new JsonObject();
					results.putNumber("status", 200);
					results.putString("content", icsCalendar.toString());
					message.reply(results);
				} catch (Exception e) {
					LOG.error("Conversion error", e);
					JsonObject results = new JsonObject();
					results.putString("message", "Unknown action");
					results.putNumber("status", 400);
					message.reply(results);
				}
			}
		});

	}

}
