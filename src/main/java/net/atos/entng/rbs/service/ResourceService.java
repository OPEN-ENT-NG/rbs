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
import org.entcore.common.service.CrudService;
import org.entcore.common.user.UserInfos;
import org.vertx.java.core.Handler;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;

import java.util.List;

public interface ResourceService extends CrudService {

	/**
	 * Create a resource
	 *
	 * @param resource : id of current resource
	 * @param user     : information of current user logged
	 * @param handler  : handler which contains the response
	 */
	void createResource(JsonObject resource, UserInfos user, Handler<Either<String, JsonObject>> handler);

	/**
	 * Get all resources
	 *
	 * @param groupsAndUserIds : list of groups and id of users who are authorized
	 * @param user             : information of current user logged
	 * @param handler          : handler which contains the response
	 */
	void listResources(List<String> groupsAndUserIds, UserInfos user, Handler<Either<String, JsonArray>> handler);

	/**
	 * Update a resource
	 *
	 * @param id      : id of current resource
	 * @param data    : object which contains information of resource
	 * @param handler : handler which contains the response
	 */
	void updateResource(String id, JsonObject data, Handler<Either<String, JsonObject>> handler);

	/**
	 * Get the booking owners list of current resource
	 *
	 * @param resourceId : id of current resource
	 * @param handler    : handler which contains the response
	 */
	void getBookingOwnersIds(long resourceId, Handler<Either<String, JsonArray>> handler);

	/**
	 * Get max_delay and min_delay of resource, owner, school_id and managers (userIds and groupIds) of resourceType
	 *
	 * @param resourceId : id of current resource
	 * @param handler    : handler which contains the response
	 */
	void getDelaysAndTypeProperties(long resourceId, Handler<Either<String, JsonObject>> handler);

	/**
	 * Add the notification for current user on current resource
	 *
	 * @param id      : id of current resource
	 * @param user    : information of current user logged
	 * @param handler : handler which contains the response
	 */
	void addNotification(String id, UserInfos user, Handler<Either<String, JsonObject>> handler);

	/**
	 * Remove the notification for current user on current resource
	 *
	 * @param id      : id of current resource
	 * @param user    : information of current user logged
	 * @param handler : handler which contains the response
	 */
	void removeNotification(String id, UserInfos user, Handler<Either<String, JsonObject>> handler);

	/**
	 * Get notification list for current user
	 *
	 * @param user    : information of current user logged
	 * @param handler : handler which contains the response
	 */
	void getNotifications(UserInfos user, Handler<Either<String, JsonArray>> handler);

	/**
	 * Get the user's id for current resource
	 *
	 * @param resourceId : id of current resource
	 * @param user       : information of current user logged
	 * @param handler    : handler which contains the response
	 */
	void getUserNotification(long resourceId, UserInfos user, Handler<Either<String, JsonArray>> handler);
}
