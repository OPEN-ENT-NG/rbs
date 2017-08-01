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

import java.util.List;

import org.entcore.common.service.CrudService;
import org.entcore.common.user.UserInfos;
import org.vertx.java.core.Handler;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;

import fr.wseduc.webutils.Either;

public interface ResourceService extends CrudService {

	public void createResource(JsonObject resource, UserInfos user, Handler<Either<String, JsonObject>> handler);

	public void listResources(List<String> groupsAndUserIds, UserInfos user, Handler<Either<String, JsonArray>> handler);

	public void updateResource(String id, JsonObject data, Handler<Either<String, JsonObject>> handler);

	public void getBookingOwnersIds(long resourceId, Handler<Either<String, JsonArray>> handler);

	/**
	 * Get max_delay and min_delay of resource, owner, school_id and managers (userIds and groupIds) of resourceType
	 */
	public void getDelaysAndTypeProperties(long resourceId, Handler<Either<String, JsonObject>> handler);

	public void addNotification(String id, UserInfos user, Handler<Either<String, JsonObject>> handler);

	public void removeNotification(String id, UserInfos user, Handler<Either<String, JsonObject>> handler);

    public void getNotifications(UserInfos user, Handler<Either<String, JsonArray>> handler);

    public void getUserNotification (long resourceId, UserInfos user, Handler<Either<String, JsonArray>> handler);
}
