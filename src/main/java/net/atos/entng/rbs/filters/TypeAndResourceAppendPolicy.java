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

package net.atos.entng.rbs.filters;

import static net.atos.entng.rbs.BookingStatus.SUSPENDED;
import static net.atos.entng.rbs.BookingUtils.getLocalAdminScope;
import static org.entcore.common.sql.Sql.parseId;

import java.util.ArrayList;
import java.util.List;

import net.atos.entng.rbs.controllers.ResourceController;

import org.entcore.common.http.filter.ResourcesProvider;
import org.entcore.common.sql.Sql;
import org.entcore.common.sql.SqlConf;
import org.entcore.common.sql.SqlConfs;
import org.entcore.common.sql.SqlResult;
import org.entcore.common.user.UserInfos;
import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;

import fr.wseduc.webutils.http.Binding;
import fr.wseduc.webutils.http.HttpMethod;

/* Authorize if user has rights (owner or shared) on resourceType or on resource,
 * or if he is a local administrator for the resourceType's school_id
 */
public class TypeAndResourceAppendPolicy implements ResourcesProvider {

	@Override
	public void authorize(final HttpServerRequest request, final Binding binding, final UserInfos user, final Handler<Boolean> handler) {
		SqlConf conf = SqlConfs.getConf(ResourceController.class.getName());
		String resourceId = request.params().get(conf.getResourceIdLabel());
		String bookingId = request.params().get("bookingId");
		boolean hasBooking = (bookingId != null && !bookingId.trim().isEmpty());

		if (resourceId != null && !resourceId.trim().isEmpty() && (parseId(resourceId) instanceof Integer)) {
			request.pause();
			// Replace character '.' by '-' in the called method's name
			String sharedMethod = binding.getServiceMethod().replaceAll("\\.", "-");

			// Groups and users
			final List<String> groupsAndUserIds = new ArrayList<>();
			groupsAndUserIds.add(user.getUserId());
			if (user.getGroupsIds() != null) {
				groupsAndUserIds.addAll(user.getGroupsIds());
			}

			// Query
			StringBuilder query = new StringBuilder();
			JsonArray values = new JsonArray();
			query.append("SELECT count(*)")
				.append(" FROM rbs.resource AS r")
				.append(" INNER JOIN rbs.resource_type AS t ON r.type_id = t.id");
			if (hasBooking) {
				// Additional join when parameter bookingId is used
				query.append(" INNER JOIN rbs.booking AS b ON b.resource_id = r.id");
			}

			query.append(" LEFT JOIN rbs.resource_type_shares AS ts ON t.id = ts.resource_id")
					.append(" LEFT JOIN rbs.resource_shares AS rs ON r.id = rs.resource_id")
					.append(" WHERE (");

			if (isUpdateBooking(binding) || isUpdatePeriodicBooking(binding) || isDeleteBooking(binding)) {
				query.append("(ts.member_id IN ").append(Sql.listPrepared(groupsAndUserIds.toArray())).append(" AND ts.action = 'net-atos-entng-rbs-controllers-BookingController|processBooking')");
				for (String groupOruser : groupsAndUserIds) {
					values.add(groupOruser);
				}

				query.append(" OR (rs.member_id IN ").append(Sql.listPrepared(groupsAndUserIds.toArray())).append(" AND rs.action = 'net-atos-entng-rbs-controllers-BookingController|processBooking')");
				for (String groupOruser : groupsAndUserIds) {
					values.add(groupOruser);
				}
			} else {
				query.append("(ts.member_id IN ").append(Sql.listPrepared(groupsAndUserIds.toArray())).append(" AND ts.action = ?)");
				for (String groupOruser : groupsAndUserIds) {
					values.add(groupOruser);
				}
				values.add(sharedMethod);

				query.append(" OR (rs.member_id IN ").append(Sql.listPrepared(groupsAndUserIds.toArray())).append(" AND rs.action = ?)");
				for (String groupOruser : groupsAndUserIds) {
					values.add(groupOruser);
				}
				values.add(sharedMethod);
			}

			// Authorize user if he is a local administrator for the resourceType's school_id
			List<String> scope = getLocalAdminScope(user);
			if (scope!=null && !scope.isEmpty()) {
				query.append(" OR t.school_id IN ").append(Sql.listPrepared(scope.toArray()));
				for (String schoolId : scope) {
					values.addString(schoolId);
				}
			}

			query.append(" OR t.owner = ? OR r.owner = ?");
			values.add(user.getUserId()).add(user.getUserId());
			if(isDeleteBooking(binding)) {
				query.append(" OR b.owner = ?"); // Owner can delete his booking
				values.add(user.getUserId());
			} else if(isUpdateBooking(binding) || isUpdatePeriodicBooking(binding)) {
				// Check that the user is the booking's owner and that the resource is available
				query.append(" OR b.owner = ?"); // Owner can delete his booking
				values.add(user.getUserId());
				query.append(" AND r.is_available = ?");
				values.add(true);
			}

			query.append(") AND r.id = ?");
			values.add(Sql.parseId(resourceId));
			if (hasBooking) {
				query.append(" AND b.id = ?");
				values.add(Sql.parseId(bookingId));
			}

			if (isCreatePeriodicBooking(binding)) {
				// Check that the resource allows periodic_booking and that it is available
				query.append(" AND r.periodic_booking = ?")
					.append(" AND r.is_available = ?");
				values.add(true).add(true);
			}
			else if(isCreateBooking(binding)) {
				// Check that the resource is available
				query.append(" AND r.is_available = ?");
				values.add(true);
			}
			else if(isProcessBooking(binding)) {
				// A booking can be validated or refused, only if its status is not "suspended" and the resource is available
				query.append(" AND b.status != ?")
					.append(" AND r.is_available = true");
				values.add(SUSPENDED.status());
			}

			// Execute
			Sql.getInstance().prepared(query.toString(), values, new Handler<Message<JsonObject>>() {
				@Override
				public void handle(Message<JsonObject> message) {
					request.resume();
					Long count = SqlResult.countResult(message);
					handler.handle(count != null && count > 0);
				}
			});
		} else {
			handler.handle(false);
		}
	}


	private boolean isCreateBooking(final Binding binding) {
		return bindingIsThatMethod(binding, HttpMethod.POST, "net.atos.entng.rbs.controllers.BookingController|createBooking");
	}

	private boolean isCreatePeriodicBooking(final Binding binding) {
		return bindingIsThatMethod(binding, HttpMethod.POST, "net.atos.entng.rbs.controllers.BookingController|createPeriodicBooking");
	}

	private boolean isUpdateBooking(final Binding binding) {
		return bindingIsThatMethod(binding, HttpMethod.PUT, "net.atos.entng.rbs.controllers.BookingController|updateBooking");
	}

	private boolean isUpdatePeriodicBooking(final Binding binding) {
		return bindingIsThatMethod(binding, HttpMethod.PUT, "net.atos.entng.rbs.controllers.BookingController|updatePeriodicBooking");
	}

	private boolean isProcessBooking(final Binding binding) {
		return bindingIsThatMethod(binding, HttpMethod.PUT, "net.atos.entng.rbs.controllers.BookingController|processBooking");
	}

	private boolean isDeleteBooking(final Binding binding) {
		return bindingIsThatMethod(binding, HttpMethod.DELETE, "net.atos.entng.rbs.controllers.BookingController|deleteBooking");
	}

	private boolean bindingIsThatMethod(final Binding binding, final HttpMethod thatHttpMethod, final String thatServiceMethod) {
		return (thatHttpMethod.equals(binding.getMethod()) && thatServiceMethod.equals(binding.getServiceMethod()));
	}


}
