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

import static net.atos.entng.rbs.BookingUtils.*;
import static org.entcore.common.neo4j.Neo4jResult.validResultHandler;
import static org.entcore.common.sql.Sql.parseId;
import static org.entcore.common.sql.SqlResult.*;

import java.util.List;

import fr.wseduc.webutils.http.Renders;
import org.entcore.common.sql.Sql;
import org.entcore.common.user.UserInfos;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;

import fr.wseduc.webutils.Either;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class ResourceTypeServiceSqlImpl implements ResourceTypeService {

	protected static final Logger log = LoggerFactory.getLogger(Renders.class);

	@Override
	public void list(final List<String> groupsAndUserIds, final UserInfos user,
			final Handler<Either<String, JsonArray>> handler) {
		list(groupsAndUserIds, user, null, handler);
	}

	@Override
	public void list(final List<String> groupsAndUserIds, final UserInfos user, final String structureId,
					 final Handler<Either<String, JsonArray>> handler) {

		StringBuilder query = new StringBuilder();
		JsonArray values = new fr.wseduc.webutils.collections.JsonArray();

		query.append("SELECT t.*,")
				.append(" json_agg(row_to_json(row(ts.member_id,ts.action)::rbs.share_tuple)) as shared,")
				.append(" array_to_json(array_agg(m.group_id)) as groups ")
				.append(" FROM rbs.resource_type AS t")
				.append(" LEFT JOIN rbs.resource_type_shares AS ts ON t.id = ts.resource_id")
				.append(" LEFT JOIN rbs.members AS m ON (ts.member_id = m.id AND m.group_id IS NOT NULL)");

		boolean isLocalAdmin = isLocalAdmin(user);

		// Local admin targeting one structure
		// A local administrator of a given school can see its types, even if he is not owner or manager of these types
		if(Boolean.TRUE.equals(isLocalAdmin) && (structureId != null)) {
			query.append(" WHERE t.school_id = ?");
			values.add(structureId);
		} else {
			//Not local admin targeting all structures
			query.append(" WHERE ts.member_id IN ").append(Sql.listPrepared(groupsAndUserIds.toArray()));
			for (String groupOruser : groupsAndUserIds) {
				values.add(groupOruser);
			}
			query.append(" OR t.owner = ?");
			values.add(user.getUserId());

			//Local admin targeting all structures
			// && (structureId == null)
			// A local administrator of a given school can see its types, even if he is not owner or manager of these types
			if(Boolean.TRUE.equals(isLocalAdmin)) {
				List<String> scope = getLocalAdminScope(user);
				if (scope!=null && !scope.isEmpty()) {
					query.append(" OR t.school_id IN ").append(Sql.listPrepared(scope.toArray()));
					for (String schoolId : scope) {
						values.add(schoolId);
					}
				}
			}

			//Not local admin targeting one structure
			//Boolean.FALSE.equals(isLocalAdmin) &&
			if (structureId != null) {
				query.append(" AND t.school_id = ?");
				values.add(structureId);
			}

		}


//		query.append(" WHERE ts.member_id IN ").append(Sql.listPrepared(groupsAndUserIds.toArray()));
//		for (String groupOruser : groupsAndUserIds) {
//			values.add(groupOruser);
//		}
//
//		query.append(" OR t.owner = ?");
//		values.add(user.getUserId());
//
//		// A local administrator of a given school can see its types, even if he is not owner or manager of these types
//		List<String> scope = getLocalAdminScope(user);
//		if (scope!=null && !scope.isEmpty()) {
//			query.append(" OR t.school_id IN ").append(Sql.listPrepared(scope.toArray()));
//			for (String schoolId : scope) {
//				values.add(schoolId);
//			}
//		}

		query.append(" GROUP BY t.id")
				.append(" ORDER BY t.name");

		Sql.getInstance().prepared(query.toString(), values, parseShared(handler));
	}



	@Override
	public void getModeratorsIds(final String typeId, final Handler<Either<String, JsonArray>> handler) {

		StringBuilder query = new StringBuilder();
		JsonArray values = new fr.wseduc.webutils.collections.JsonArray();

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

		Sql.getInstance().prepared(query.toString(), values, new Handler<Message<JsonObject>>() {
			@Override
			public void handle(Message<JsonObject> event) {
				final Either<String, JsonArray> res = validResult(event);
				handler.handle(res);
			}
		});
	}

	@Override
	public void overrideColorChild(String typeId, String color, Handler<Either<String,JsonObject>> handler) {
		StringBuilder query = new StringBuilder();
		JsonArray values = new fr.wseduc.webutils.collections.JsonArray();
		query.append ("UPDATE rbs.resource")
				.append(" SET color = ?")
				.append (" WHERE type_id = ?");
		values.add(color);
		values.add(parseId(typeId));
		Sql.getInstance().prepared(query.toString(), values, validRowsResultHandler(handler));
		log.trace("Children's color for resource type " + typeId + " updated");
	}

	@Override
	public void overrideValidationChild(String typeId, Boolean validation, Handler<Either<String, JsonObject>> handler) {
		StringBuilder query = new StringBuilder();
		JsonArray values = new fr.wseduc.webutils.collections.JsonArray();
		query.append ("UPDATE rbs.resource")
				.append(" SET validation = ?")
				.append (" WHERE type_id = ?");
		values.add(validation);
		values.add(parseId(typeId));
		Sql.getInstance().prepared(query.toString(), values, validRowsResultHandler(handler));
	}

	@Override
	public void addNotifications(String id, UserInfos user, Handler<Either<String, JsonObject>> handler) {
		StringBuilder query = new StringBuilder("INSERT INTO rbs.notifications (resource_id, user_id)" +
				" SELECT r.id, ? FROM rbs.resource AS r WHERE type_id = ? AND NOT EXISTS (SELECT resource_id, user_id FROM rbs.notifications WHERE user_id = ? AND resource_id = r.id)");
		JsonArray values = new fr.wseduc.webutils.collections.JsonArray();
		values.add(user.getUserId());
		values.add(parseId(id));
		values.add(user.getUserId());
		Sql.getInstance().prepared(query.toString(), values, validUniqueResultHandler(handler));
	}

	@Override
	public void removeNotifications(String id, UserInfos user, Handler<Either<String, JsonObject>> handler) {
		StringBuilder query = new StringBuilder("DELETE FROM rbs.notifications WHERE resource_id IN (SELECT id FROM rbs.resource WHERE type_id = ?) AND user_id = ?");
		JsonArray values = new fr.wseduc.webutils.collections.JsonArray();
		values.add(parseId(id));
		values.add(user.getUserId());
		Sql.getInstance().prepared(query.toString(), values, validRowsResultHandler(handler));
	}
}
