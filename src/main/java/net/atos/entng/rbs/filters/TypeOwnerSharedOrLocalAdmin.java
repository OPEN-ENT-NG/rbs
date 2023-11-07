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

import static net.atos.entng.rbs.BookingUtils.getLocalAdminScope;

import java.util.ArrayList;
import java.util.List;

import net.atos.entng.rbs.controllers.ResourceTypeController;

import org.entcore.common.http.filter.ResourcesProvider;
import org.entcore.common.sql.Sql;
import org.entcore.common.sql.SqlConf;
import org.entcore.common.sql.SqlConfs;
import org.entcore.common.sql.SqlResult;
import org.entcore.common.user.UserInfos;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.Message;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import fr.wseduc.webutils.http.Binding;

/* Authorize if
 * user is the resourceType's owner,
 * or he has been shared rights,
 * or he is a local administrator for the school_id of the resourceType
 */
public class TypeOwnerSharedOrLocalAdmin implements ResourcesProvider {

	@Override
	public void authorize(final HttpServerRequest request, Binding binding, final UserInfos user,
			final Handler<Boolean> handler) {

		SqlConf conf = SqlConfs.getConf(ResourceTypeController.class.getName());
		String resourceTypeId = request.params().get(conf.getResourceIdLabel());

		if(resourceTypeId == null || resourceTypeId.trim().isEmpty()) {
			handler.handle(false);
		}
		else {
			request.pause();
			String sharedMethod = binding.getServiceMethod().replaceAll("\\.", "-");

			final List<String> groupsAndUserIds = new ArrayList<>();
			groupsAndUserIds.add(user.getUserId());
			if (user.getGroupsIds() != null && !user.getGroupsIds().isEmpty()) {
				groupsAndUserIds.addAll(user.getGroupsIds());
			}

			StringBuilder query = new StringBuilder();
			JsonArray values = new JsonArray();

			query.append("SELECT count(*)")
				.append(" FROM rbs.resource_type AS t")
				.append(" LEFT JOIN rbs.resource_type_shares AS ts ON t.id = ts.resource_id")
				.append(" WHERE ((ts.member_id IN ").append(Sql.listPrepared(groupsAndUserIds.toArray()))
				.append(" AND ts.action = ?)");
			for (String groupOrUserId : groupsAndUserIds) {
				values.add(groupOrUserId);
			}
			values.add(sharedMethod);

			// Authorize user if he is a local administrator for the resourceType's school_id
			List<String> scope = getLocalAdminScope(user);
			if (scope!=null && !scope.isEmpty()) {
				query.append(" OR t.school_id IN ").append(Sql.listPrepared(scope.toArray()));
				for (String schoolId : scope) {
					values.add(schoolId);
				}
			}

			query.append(" OR t.owner = ?)")
				.append(" AND t.id = ?");
			values.add(user.getUserId())
				.add(Sql.parseId(resourceTypeId));

			Sql.getInstance().prepared(query.toString(), values, new Handler<Message<JsonObject>>() {
				@Override
				public void handle(Message<JsonObject> message) {
					request.resume();
					Long count = SqlResult.countResult(message);
					handler.handle(count != null && count > 0);
				}
			});
		}

	}

}
