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

import static net.atos.entng.rbs.BookingUtils.getLocalAdminScope;
import static org.entcore.common.sql.Sql.parseId;
import static org.entcore.common.sql.SqlResult.*;

import java.util.List;

import fr.wseduc.webutils.http.Renders;
import org.entcore.common.sql.Sql;
import org.entcore.common.user.UserInfos;
import org.vertx.java.core.Handler;
import org.vertx.java.core.json.JsonArray;

import fr.wseduc.webutils.Either;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;

public class ResourceTypeServiceSqlImpl implements ResourceTypeService {

	protected static final Logger log = LoggerFactory.getLogger(Renders.class);
	@Override
	public void list(final List<String> groupsAndUserIds, final UserInfos user,
			final Handler<Either<String, JsonArray>> handler) {

		StringBuilder query = new StringBuilder();
		JsonArray values = new JsonArray();

		query.append("SELECT t.*,")
			.append(" json_agg(row_to_json(row(ts.member_id,ts.action)::rbs.share_tuple)) as shared,")
			.append(" array_to_json(array_agg(m.group_id)) as groups ")
			.append(" FROM rbs.resource_type AS t")
			.append(" LEFT JOIN rbs.resource_type_shares AS ts ON t.id = ts.resource_id")
			.append(" LEFT JOIN rbs.members AS m ON (ts.member_id = m.id AND m.group_id IS NOT NULL)");

		query.append(" WHERE ts.member_id IN ").append(Sql.listPrepared(groupsAndUserIds.toArray()));
		for (String groupOruser : groupsAndUserIds) {
			values.add(groupOruser);
		}

		query.append(" OR t.owner = ?");
		values.add(user.getUserId());

		// A local administrator of a given school can see its types, even if he is not owner or manager of these types
		List<String> scope = getLocalAdminScope(user);
		if (scope!=null && !scope.isEmpty()) {
			query.append(" OR t.school_id IN ").append(Sql.listPrepared(scope.toArray()));
			for (String schoolId : scope) {
				values.addString(schoolId);
			}
		}

		query.append(" GROUP BY t.id")
			.append(" ORDER BY t.name");

		Sql.getInstance().prepared(query.toString(), values, parseShared(handler));
	}

	@Override
	public void getModeratorsIds(final String typeId, final Handler<Either<String, JsonArray>> handler) {

		StringBuilder query = new StringBuilder();
		JsonArray values = new JsonArray();

		query.append("SELECT DISTINCT m.*")
			.append(" FROM rbs.resource_type AS t")
			.append(" INNER JOIN rbs.resource_type_shares AS ts ON t.id = ts.resource_id")
			.append(" INNER JOIN rbs.members AS m ON (ts.member_id = m.id)")
			.append(" WHERE ts.action = 'net-atos-entng-rbs-controllers-BookingController|processBooking'")
			.append(" AND t.id = ?")
			.append(" GROUP BY m.id");
		values.add(parseId(typeId));

		query.append(" UNION")
			.append(" SELECT t.owner as id, t.owner as user_id, null as group_id")
			.append(" FROM rbs.resource_type AS t")
			.append(" WHERE t.id = ?");
		values.add(parseId(typeId));

		Sql.getInstance().prepared(query.toString(), values, validResultHandler(handler));
	}

	@Override
	public void overrideColorChild(String typeId, String color, Handler<Either<String,JsonObject>> eitherHandler) {
		StringBuilder query = new StringBuilder();
		JsonArray values = new JsonArray();
		query.append ("UPDATE rbs.resource")
				.append(" SET color = ?")
				.append (" WHERE type_id = ?");
		values.add(color);
		values.add(parseId(typeId));
		Sql.getInstance().prepared(query.toString(), values, validRowsResultHandler(eitherHandler));
		log.trace("Children's color for resource type " + typeId + " updated");
	}

	@Override
	public void overrideValidationChild(String typeId, Boolean validation, Handler<Either<String, JsonObject>> handler) {
		StringBuilder query = new StringBuilder();
		JsonArray values = new JsonArray();
		query.append ("UPDATE rbs.resource")
				.append(" SET validation = ?")
				.append (" WHERE type_id = ?");
		values.add(validation);
		values.add(parseId(typeId));
		Sql.getInstance().prepared(query.toString(), values, validRowsResultHandler(handler));
	}

}
