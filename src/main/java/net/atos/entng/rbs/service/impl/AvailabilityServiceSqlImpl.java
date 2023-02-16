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

package net.atos.entng.rbs.service.impl;

import fr.wseduc.webutils.Either;
import net.atos.entng.rbs.Rbs;
import net.atos.entng.rbs.models.Availability;
import net.atos.entng.rbs.service.AvailabilityService;
import org.entcore.common.service.impl.SqlCrudService;
import org.entcore.common.sql.Sql;
import org.entcore.common.sql.SqlResult;
import org.entcore.common.user.UserInfos;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;


public class AvailabilityServiceSqlImpl extends SqlCrudService implements AvailabilityService {
	public AvailabilityServiceSqlImpl() {
		super("rbs", "availability");
	}

	@Override
	public void listAvailabilities(JsonArray resourceIds, Handler<Either<String, JsonArray>> handler) {
		String query =  "SELECT * FROM " + Rbs.AVAILABILITY_TABLE;
		JsonArray params = new JsonArray();
		if (resourceIds != null && !resourceIds.isEmpty()) {
			query += " WHERE resource_id IN " + Sql.listPrepared(resourceIds);
			params.addAll(resourceIds);
		}
		query += " ORDER BY start_date ASC";
		Sql.getInstance().prepared(query, params, SqlResult.validResultHandler(handler));
	}

	@Override
	public void listResourceAvailability(Integer resourceId, boolean is_unavailability, UserInfos user, Handler<Either<String, JsonArray>> handler) {
		String query =  "SELECT * FROM " + Rbs.AVAILABILITY_TABLE + " WHERE resource_id = ? AND is_unavailability = ? " +
				"ORDER BY start_date ASC";
		JsonArray params = new JsonArray().add(resourceId).add(is_unavailability);
		Sql.getInstance().prepared(query, params, SqlResult.validResultHandler(handler));
	}

	@Override
	public void createAvailability(Availability availability, UserInfos user, Handler<Either<String, JsonObject>> handler) {
		String query = "INSERT INTO " + Rbs.AVAILABILITY_TABLE + " (resource_id, quantity, is_unavailability, start_date, " +
				"end_date, start_time, end_time, days) " +
				"VALUES (?, ?, ?, ?, ?, ?, ?, B'" + availability.getSelectedDaysBitString() + "') RETURNING *;";

		JsonArray params = new JsonArray()
				.add(availability.getResourceId())
				.add(availability.getQuantity())
				.add(availability.isUnavailability())
				.add(availability.getStartDate())
				.add(availability.getEndDate())
				.add(availability.getStartTime())
				.add(availability.getEndTime());

		Sql.getInstance().prepared(query, params, SqlResult.validUniqueResultHandler(handler));
	}

	@Override
	public void updateAvailability(Availability availability, UserInfos user, Handler<Either<String, JsonObject>> handler) {
		String query = "UPDATE " + Rbs.AVAILABILITY_TABLE + " SET quantity = ?, is_unavailability = ?, start_date = ?, end_date = ?, " +
				"start_time = ?, end_time = ?, days = B'" + availability.getSelectedDaysBitString() + "' " +
				"WHERE id = ? RETURNING *;";

		JsonArray params = new JsonArray()
				.add(availability.getQuantity())
				.add(availability.isUnavailability())
				.add(availability.getStartDate())
				.add(availability.getEndDate())
				.add(availability.getStartTime())
				.add(availability.getEndTime())
				.add(availability.getId());

		Sql.getInstance().prepared(query, params, SqlResult.validUniqueResultHandler(handler));
	}

	@Override
	public void deleteAvailability(Integer availabilityId, Handler<Either<String, JsonObject>> handler) {
		String query = "DELETE FROM " + Rbs.AVAILABILITY_TABLE + " WHERE id = ? RETURNING id;";
		JsonArray params = new JsonArray().add(availabilityId);
		Sql.getInstance().prepared(query, params, SqlResult.validUniqueResultHandler(handler));
	}

	@Override
	public void deleteAllAvailability(Integer resourceId, String deleteUnavailability, Handler<Either<String, JsonArray>> handler) {
		String query = "DELETE FROM " + Rbs.AVAILABILITY_TABLE + " WHERE resource_id = ? ";
		JsonArray params = new JsonArray().add(resourceId);

		if (deleteUnavailability.equals("true") || deleteUnavailability.equals("false")) {
			query += "AND is_unavailability = ?";
			params.add(Boolean.parseBoolean(deleteUnavailability));
		}

		query += "RETURNING id;";
		Sql.getInstance().prepared(query, params, SqlResult.validResultHandler(handler));
	}
}
